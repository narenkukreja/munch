package com.munch.reddit.feature.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.munch.reddit.data.repository.RedditRepository
import com.munch.reddit.data.repository.SubredditRepository
import com.munch.reddit.domain.SubredditCatalog
import com.munch.reddit.domain.model.RedditComment
import com.munch.reddit.domain.model.RedditCommentCursor
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.feature.shared.SubredditSideSheetScrollState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PostDetailViewModel(
    private val permalink: String,
    private val repository: RedditRepository,
    private val subredditRepository: SubredditRepository
) : ViewModel() {

    private val sortOptions = listOf(TOP_SORT, "best", "new")

    private val _uiState = MutableStateFlow(
        PostDetailUiState(isLoading = true, selectedSort = sortOptions.first(), sortOptions = sortOptions)
    )
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var commentTree: List<RedditComment> = emptyList()
    private val collapsedIds = mutableSetOf<String>()
    private val repliesVisibleCounts = mutableMapOf<String, Int>()
    private var postAuthorLower: String = ""
    private var nextCommentCursor: RedditCommentCursor? = null
    private var isLoadingMoreComments: Boolean = false
    private var commentPrefetchJob: Job? = null
    private var autoFetchBudget: Int = 0
    private val remoteLoadingParents = mutableSetOf<String>()
    private val remoteLoadErrors = mutableSetOf<String>()
    private val manualParentFetches = mutableSetOf<String>()
    private var hasLoadedInitialPost = false

    init {
        load(reloadPost = true)
        // Prefetch subreddit icons for all available subreddits
        viewModelScope.launch {
            subredditRepository.prefetchSubredditIcons(SubredditCatalog.defaultSubreddits)
        }
        // Collect subreddit icons from shared repository
        viewModelScope.launch {
            subredditRepository.subredditIcons.collect { icons ->
                _uiState.value = _uiState.value.copy(subredditIcons = icons)
            }
        }
    }

    fun refresh() {
        load(sort = _uiState.value.selectedSort, reloadPost = true)
    }

    fun selectSort(sort: String) {
        if (!sortOptions.any { it.equals(sort, ignoreCase = true) }) return
        val normalized = sortOptions.first { it.equals(sort, ignoreCase = true) }
        if (normalized.equals(_uiState.value.selectedSort, ignoreCase = true)) return
        load(sort = normalized, reloadPost = false)
    }

    fun toggleComment(commentId: String) {
        val validId = commentId.takeIf { it.isNotBlank() } ?: return
        if (!commentTree.containsCommentId(validId)) return
        if (!collapsedIds.add(validId)) {
            collapsedIds.remove(validId)
        }
        emitComments()
    }

    fun updateSideSheetScroll(position: Int) {
        SubredditSideSheetScrollState.update(position)
    }

    fun getSideSheetScroll(): Int = SubredditSideSheetScrollState.current()

    fun loadMoreComments() {
        requestMoreComments(userInitiated = false)
    }

    fun userLoadMoreComments() {
        requestMoreComments(userInitiated = true)
    }

    fun loadMoreRemoteReplies(parentId: String) {
        val cursor = nextCommentCursor ?: run {
            val normalized = parentId.normalizeCommentId() ?: parentId
            Log.w(TAG, "Remote load requested without cursor for parent=$normalized")
            remoteLoadErrors += normalized
            emitComments()
            return
        }
        val normalizedParent = parentId.normalizeCommentId() ?: parentId
        if (normalizedParent.isBlank()) return
        if (remoteLoadingParents.contains(normalizedParent)) {
            Log.d(TAG, "Remote load already in progress for parent=$normalizedParent")
            return
        }
        if (isLoadingMoreComments || _uiState.value.isLoading || _uiState.value.isRefreshingComments) {
            Log.d(TAG, "Remote load skipped due to global loading state for parent=$normalizedParent")
            return
        }
        if (!cursor.hasWork()) {
            Log.w(TAG, "Remote load requested but cursor has no work for parent=$normalizedParent")
            remoteLoadErrors += normalizedParent
            emitComments()
            return
        }

        val promotedPlaceholder = cursor.promotePlaceholdersForParent(normalizedParent)
        val promotedContinue = cursor.promoteContinueThreadParent(normalizedParent)
        Log.d(
            TAG,
            "Remote load promotion parent=$normalizedParent placeholder=$promotedPlaceholder continue=$promotedContinue"
        )
        if (!promotedPlaceholder && !promotedContinue) {
            Log.w(TAG, "No placeholders or continue thread available for parent=$normalizedParent; forcing continue fetch")
            cursor.markForceContinueParent(normalizedParent)
        } else if (promotedContinue && !promotedPlaceholder) {
            cursor.markForceContinueParent(normalizedParent)
        }

        remoteLoadErrors.remove(normalizedParent)
        remoteLoadingParents += normalizedParent
        manualParentFetches += normalizedParent
        emitComments()
        logRemoteParentStatus(normalizedParent, reason = "fetch_started")

        isLoadingMoreComments = true
        viewModelScope.launch {
            try {
                val success = fetchNextCommentBatch(showAppendingState = false, userInitiated = true)
                remoteLoadingParents -= normalizedParent
                if (success) {
                    remoteLoadErrors.remove(normalizedParent)
                    logRemoteParentStatus(normalizedParent, reason = "fetch_success")
                } else {
                    remoteLoadErrors += normalizedParent
                    Log.e(TAG, "Remote load failed for parent=$normalizedParent")
                    logRemoteParentStatus(normalizedParent, reason = "fetch_failed")
                }
                emitComments()
            } finally {
                isLoadingMoreComments = false
            }
        }
    }

    private fun requestMoreComments(userInitiated: Boolean) {
        val cursor = nextCommentCursor ?: return
        if (!cursor.hasWork()) {
            nextCommentCursor = null
            remoteLoadingParents.clear()
            remoteLoadErrors.clear()
            manualParentFetches.clear()
            _uiState.value = _uiState.value.copy(
                hasMoreComments = false,
                pendingRemoteReplyCount = 0,
                autoFetchRemaining = autoFetchBudget,
                comments = buildDisplayComments()
            )
            Log.d(TAG, "No cursor work remaining. Clearing pending state.")
            return
        }
        if (!userInitiated && autoFetchBudget <= 0) {
            _uiState.value = _uiState.value.copy(autoFetchRemaining = autoFetchBudget)
            Log.d(TAG, "Auto fetch budget exhausted; skipping auto request")
            return
        }
        if (isLoadingMoreComments || _uiState.value.isLoading || _uiState.value.isRefreshingComments) return
        isLoadingMoreComments = true
        viewModelScope.launch {
            try {
                fetchNextCommentBatch(
                    showAppendingState = userInitiated,
                    userInitiated = userInitiated
                )
            } finally {
                isLoadingMoreComments = false
            }
        }
    }

    fun loadMoreReplies(parentId: String) {
        val comment = commentTree.findCommentById(parentId) ?: return
        val depth = commentTree.findCommentDepth(parentId) ?: 0
        val childDepth = depth + 1
        val baseVisible = defaultVisibleRepliesForDepth(childDepth)
        val currentVisible = repliesVisibleCounts[parentId] ?: baseVisible
        if (currentVisible >= comment.children.size) return
        val increment = replyBatchIncrementForDepth(childDepth)
        val updatedVisible = (currentVisible + increment).coerceAtMost(comment.children.size)
        repliesVisibleCounts[parentId] = updatedVisible
        emitComments()
    }

    private fun load(sort: String = TOP_SORT, reloadPost: Boolean = false) {
        commentPrefetchJob?.cancel()
        commentPrefetchJob = null
        viewModelScope.launch {
            val previousSort = _uiState.value.selectedSort
            val previousCursor = nextCommentCursor
            val previousAutoFetchBudget = autoFetchBudget
            if (reloadPost) {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    isRefreshingComments = false,
                    isAppending = false,
                    errorMessage = null,
                    selectedSort = sort
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshingComments = true,
                    isAppending = false,
                    errorMessage = null,
                    selectedSort = sort
                )
            }
            runCatching { repository.fetchPostDetail(permalink, limit = COMMENT_PAGE_LIMIT, sort = sort) }
                .onSuccess { detail ->
                    commentTree = detail.comments
                    collapsedIds.clear()
                    collapsedIds.addAll(commentTree.collectAutoModeratorIds())
                    repliesVisibleCounts.clear()
                    remoteLoadingParents.clear()
                    remoteLoadErrors.clear()
                    manualParentFetches.clear()
                    postAuthorLower = detail.post.author.lowercase()
                    nextCommentCursor = detail.nextCommentCursor
                    autoFetchBudget = if (nextCommentCursor?.hasWork() == true) {
                        INITIAL_AUTO_COMMENT_BATCHES
                    } else {
                        0
                    }

                    // Determine default sort for stickied daily/weekly threads
                    val effectiveSort = if (!hasLoadedInitialPost && shouldDefaultToNewSort(detail.post)) {
                        hasLoadedInitialPost = true
                        "new"
                    } else {
                        hasLoadedInitialPost = true
                        sort
                    }

                    val pendingRemoteCount = nextCommentCursor?.totalPendingWorkCount() ?: 0
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshingComments = false,
                        post = detail.post,
                        comments = buildDisplayComments(),
                        errorMessage = null,
                        selectedSort = effectiveSort,
                        sortOptions = sortOptions,
                        hasMoreComments = nextCommentCursor?.hasWork() == true,
                        isAppending = false,
                        pendingRemoteReplyCount = pendingRemoteCount,
                        autoFetchRemaining = autoFetchBudget
                    )

                    // Fetch flair emoji lookup for the post's subreddit (e.g., r/nba team flairs)
                    viewModelScope.launch {
                        val lookup = runCatching {
                            repository.fetchSubredditUserFlairEmojis(detail.post.subreddit)
                        }.getOrElse { emptyMap() }
                        _uiState.value = _uiState.value.copy(flairEmojiLookup = lookup)
                    }

                    // If we need to change sort, reload with the correct sort
                    if (effectiveSort != sort) {
                        load(sort = effectiveSort, reloadPost = false)
                        return@onSuccess
                    }

                    prefetchInitialCommentBatches()
                }
                .onFailure { throwable ->
                    autoFetchBudget = previousAutoFetchBudget
                    remoteLoadingParents.clear()
                    remoteLoadErrors.clear()
                    manualParentFetches.clear()
                    Log.e(TAG, "Failed to load post detail", throwable)
                    _uiState.value = if (reloadPost) {
                        nextCommentCursor = null
                        _uiState.value.copy(
                            isLoading = false,
                            isRefreshingComments = false,
                            errorMessage = throwable.message ?: "Unable to load post",
                            hasMoreComments = false,
                            isAppending = false,
                            pendingRemoteReplyCount = 0,
                            autoFetchRemaining = autoFetchBudget
                        )
                    } else {
                        nextCommentCursor = previousCursor
                        _uiState.value.copy(
                            isRefreshingComments = false,
                            selectedSort = previousSort,
                            autoFetchRemaining = autoFetchBudget
                        )
                    }
                }
        }
    }

    private fun emitComments() {
        _uiState.value = _uiState.value.copy(comments = buildDisplayComments())
    }

    private fun buildDisplayComments(): List<CommentListItem> =
        commentTree.flattenWithDepth(
            collapsedIds = collapsedIds,
            postAuthorLower = postAuthorLower,
            repliesVisibleCounts = repliesVisibleCounts,
            loadingRemoteParents = remoteLoadingParents,
            remoteLoadErrors = remoteLoadErrors
        )

    private fun prefetchInitialCommentBatches() {
        val cursor = nextCommentCursor ?: return
        if (!cursor.hasWork()) {
            nextCommentCursor = null
            _uiState.value = _uiState.value.copy(
                hasMoreComments = false,
                isAppending = false,
                pendingRemoteReplyCount = 0,
                autoFetchRemaining = autoFetchBudget
            )
            Log.d(TAG, "Prefetch skipped: cursor empty")
            return
        }
        if (_uiState.value.isLoading || _uiState.value.isRefreshingComments) return
        if (isLoadingMoreComments) return
        if (autoFetchBudget <= 0) return
        if (commentPrefetchJob?.isActive == true) return

        commentPrefetchJob = viewModelScope.launch {
            isLoadingMoreComments = true
            try {
                while (nextCommentCursor?.hasWork() == true && autoFetchBudget > 0) {
                    val fetched = fetchNextCommentBatch(
                        showAppendingState = false,
                        userInitiated = false
                    )
                    if (!fetched) {
                        Log.d(TAG, "Prefetch loop broke due to fetch result")
                        break
                    }
                }
            } finally {
                isLoadingMoreComments = false
                _uiState.value = _uiState.value.copy(
                    isAppending = false,
                    hasMoreComments = nextCommentCursor?.hasWork() == true,
                    pendingRemoteReplyCount = nextCommentCursor?.totalPendingWorkCount() ?: 0,
                    autoFetchRemaining = autoFetchBudget
                )
                Log.d(TAG, "Prefetch complete: cursorHasWork=${nextCommentCursor?.hasWork()} budget=$autoFetchBudget")
            }
        }.also { job ->
            job.invokeOnCompletion {
                commentPrefetchJob = null
            }
        }
    }

    private suspend fun fetchNextCommentBatch(
        showAppendingState: Boolean,
        userInitiated: Boolean
    ): Boolean {
        val cursor = nextCommentCursor ?: return false
        if (!cursor.hasWork()) {
            nextCommentCursor = null
            _uiState.value = _uiState.value.copy(
                isAppending = false,
                hasMoreComments = false,
                pendingRemoteReplyCount = 0,
                autoFetchRemaining = autoFetchBudget
            )
            Log.d(TAG, "fetchNextCommentBatch aborted: cursor has no work")
            return false
        }
        if (!userInitiated && autoFetchBudget <= 0) {
            Log.d(TAG, "fetchNextCommentBatch skipped: autoFetchBudget exhausted")
            return false
        }

        val cursorSnapshot = cursor.snapshot()
        if (!userInitiated) {
            autoFetchBudget--
        }

        if (showAppendingState) {
            _uiState.value = _uiState.value.copy(isAppending = true)
        }

        val result = runCatching {
            repository.fetchMoreComments(
                cursor = cursor,
                limit = COMMENT_PAGE_LIMIT,
                sort = _uiState.value.selectedSort
            )
        }

        return result.fold(
            onSuccess = { page ->
                var updatedTree = commentTree
                if (page.comments.isNotEmpty()) {
                    updatedTree = updatedTree.mergeComments(page.comments)
                    collapsedIds.addAll(page.comments.collectAutoModeratorIds())
                    Log.d(TAG, "Batch success: merged ${page.comments.size} comments")
                } else {
                    Log.d(TAG, "Batch success: 0 comments returned, pendingCounts=${page.pendingCountsByParent}")
                }
                if (page.pendingCountsByParent.isNotEmpty()) {
                    updatedTree = updatedTree.updatePendingCounts(page.pendingCountsByParent)
                    Log.d(TAG, "Updated pending counts: ${page.pendingCountsByParent}")
                }
                commentTree = updatedTree
                // Capture manual fetch parents BEFORE cleaning
                val wasManualFetch = manualParentFetches.toSet()
                cleanResolvedRemoteParents(updatedTree)
                if (page.comments.isNotEmpty()) {
                    val parentIdsToExpand = page.comments.collectParentCommentIds()
                    if (parentIdsToExpand.isNotEmpty()) {
                        expandVisibilityForParents(parentIdsToExpand, wasManualFetch)
                    }
                }
                nextCommentCursor = page.nextCursor
                val pendingCount = nextCommentCursor?.totalPendingWorkCount() ?: 0
                _uiState.value = _uiState.value.copy(
                    isAppending = false,
                    comments = buildDisplayComments(),
                    hasMoreComments = nextCommentCursor?.hasWork() == true,
                    pendingRemoteReplyCount = pendingCount,
                    autoFetchRemaining = autoFetchBudget
                )
                Log.d(TAG, "Batch complete: cursorHasWork=${nextCommentCursor?.hasWork()} pending=$pendingCount budget=$autoFetchBudget")
                true
            },
            onFailure = {
                nextCommentCursor = cursorSnapshot
                if (!userInitiated) {
                    autoFetchBudget++
                }
                Log.e(TAG, "Batch fetch failed", it)
                val pendingCount = nextCommentCursor?.totalPendingWorkCount() ?: 0
                _uiState.value = _uiState.value.copy(
                    isAppending = false,
                    pendingRemoteReplyCount = pendingCount,
                    autoFetchRemaining = autoFetchBudget
                )
                false
            }
        )
    }

    private fun expandVisibilityForParents(parentIds: Set<String>, manualFetchParents: Set<String> = emptySet()) {
        if (parentIds.isEmpty()) return
        for (parentId in parentIds) {
            val parent = commentTree.findCommentById(parentId) ?: continue
            val totalChildren = parent.children.size
            if (totalChildren == 0) continue

            // Determine how many should be visible based on depth
            val depth = commentTree.findCommentDepth(parentId) ?: 0
            val childDepth = depth + 1
            val baseVisible = defaultVisibleRepliesForDepth(childDepth)

            val currentlyVisible = repliesVisibleCounts[parentId] ?: 0

            // For manual fetches, show all loaded children
            // For auto-fetches, respect depth limits but ensure at least baseVisible are shown
            val targetVisible = if (manualFetchParents.contains(parentId)) {
                totalChildren
            } else {
                baseVisible.coerceAtMost(totalChildren)
            }

            if (targetVisible > currentlyVisible) {
                repliesVisibleCounts[parentId] = targetVisible
                Log.d(TAG, "Expanded visibility for parent=$parentId to $targetVisible children (manual=${manualFetchParents.contains(parentId)}, total=$totalChildren)")
            }
        }
    }

    private fun cleanResolvedRemoteParents(tree: List<RedditComment>) {
        if (remoteLoadingParents.isEmpty() && remoteLoadErrors.isEmpty()) return
        val loadingIterator = remoteLoadingParents.iterator()
        while (loadingIterator.hasNext()) {
            val parentId = loadingIterator.next()
            val comment = tree.findCommentById(parentId)
            if (comment == null || comment.pendingRemoteReplyCount <= 0) {
                loadingIterator.remove()
                remoteLoadErrors.remove(parentId)
                manualParentFetches.remove(parentId)
                Log.d(TAG, "Cleared remote loading state for parent=$parentId")
            }
        }
        val errorIterator = remoteLoadErrors.iterator()
        while (errorIterator.hasNext()) {
            val parentId = errorIterator.next()
            val comment = tree.findCommentById(parentId)
            if (comment == null || comment.pendingRemoteReplyCount <= 0) {
                errorIterator.remove()
                manualParentFetches.remove(parentId)
                Log.d(TAG, "Cleared remote error state for parent=$parentId")
            }
        }
    }

    private fun logRemoteParentStatus(parentId: String, reason: String) {
        val comment = commentTree.findCommentById(parentId)
        val childCount = comment?.children?.size ?: -1
        val pending = comment?.pendingRemoteReplyCount ?: -1
        val cursorPending = nextCommentCursor?.totalPendingWorkCount() ?: -1
        val manual = manualParentFetches.contains(parentId)
        Log.d(
            TAG,
            "RemoteReplies[$parentId] reason=$reason childCount=$childCount pending=$pending cursorPending=$cursorPending manual=$manual"
        )
    }

    sealed class CommentListItem {
        abstract val key: String

        data class CommentNode(
            val comment: RedditComment,
            val depth: Int,
            val isCollapsed: Boolean,
            val isAutoModerator: Boolean,
            val isVisualModerator: Boolean,
            val isOp: Boolean,
            val replyCount: Int
        ) : CommentListItem() {
            override val key: String = "comment_${comment.id}"
        }

        data class LoadMoreRepliesNode(
            val parentId: String,
            val depth: Int,
            val remainingCount: Int
        ) : CommentListItem() {
            override val key: String = "load_more_${parentId}_$depth"
        }

        data class RemoteRepliesNode(
            val parentId: String,
            val depth: Int,
            val pendingCount: Int,
            val isLoading: Boolean,
            val hasError: Boolean
        ) : CommentListItem() {
            override val key: String = "remote_more_${parentId}_$depth"
        }
    }

    data class PostDetailUiState(
        val isLoading: Boolean = false,
        val isAppending: Boolean = false,
        val post: RedditPost? = null,
        val comments: List<CommentListItem> = emptyList(),
        val errorMessage: String? = null,
        val selectedSort: String = "top",
        val sortOptions: List<String> = emptyList(),
        val hasMoreComments: Boolean = false,
        val isRefreshingComments: Boolean = false,
        val pendingRemoteReplyCount: Int = 0,
        val autoFetchRemaining: Int = 0,
        val subredditIcons: Map<String, String?> = emptyMap(),
        val flairEmojiLookup: Map<String, String> = emptyMap()
    )

    companion object {
        private const val COMMENT_PAGE_LIMIT = 100
        private const val TOP_SORT = "top"
        private const val INITIAL_AUTO_COMMENT_BATCHES = 2
        const val AUTO_EXPAND_REPLY_THRESHOLD = 50
        private const val TAG = "PostDetailVM"
    }
}

private fun List<RedditComment>.flattenWithDepth(
    collapsedIds: Set<String>,
    postAuthorLower: String,
    repliesVisibleCounts: Map<String, Int>,
    loadingRemoteParents: Set<String>,
    remoteLoadErrors: Set<String>,
    depth: Int = 0
): List<PostDetailViewModel.CommentListItem> {
    val result = mutableListOf<PostDetailViewModel.CommentListItem>()
    for (comment in this) {
        val isCollapsed = collapsedIds.contains(comment.id)
        val isAutoModerator = comment.author.equals("AutoModerator", ignoreCase = true)
        val isVisualModerator = comment.author.equals("VisualMod", ignoreCase = true)
        val isOp = comment.author.equals(postAuthorLower, ignoreCase = true)
        val replyCount = comment.totalReplyCount()
        result += PostDetailViewModel.CommentListItem.CommentNode(
            comment = comment,
            depth = depth,
            isCollapsed = isCollapsed,
            isAutoModerator = isAutoModerator,
            isVisualModerator = isVisualModerator,
            isOp = isOp,
            replyCount = replyCount
        )
        if (!isCollapsed && comment.children.isNotEmpty()) {
            val nextDepth = depth + 1
            val baseVisible = defaultVisibleRepliesForDepth(nextDepth)
            val autoLimit = comment.children.size.coerceAtMost(PostDetailViewModel.AUTO_EXPAND_REPLY_THRESHOLD)
            val desiredVisible = repliesVisibleCounts[comment.id]?.coerceAtLeast(baseVisible)?.coerceAtMost(autoLimit)
                ?: minOf(autoLimit, baseVisible)
            val boundedVisible = desiredVisible.coerceAtMost(comment.children.size)
            val visibleChildren = comment.children.take(boundedVisible)
            if (visibleChildren.isNotEmpty()) {
                result += visibleChildren.flattenWithDepth(
                    collapsedIds = collapsedIds,
                    postAuthorLower = postAuthorLower,
                    repliesVisibleCounts = repliesVisibleCounts,
                    loadingRemoteParents = loadingRemoteParents,
                    remoteLoadErrors = remoteLoadErrors,
                    depth = depth + 1
                )
            }
            val remaining = comment.children.size - visibleChildren.size
            if (remaining > 0) {
                result += PostDetailViewModel.CommentListItem.LoadMoreRepliesNode(
                    parentId = comment.id,
                    depth = depth + 1,
                    remainingCount = remaining
                )
            }
        }
        val isLoadingRemote = loadingRemoteParents.contains(comment.id)
        val hasRemoteError = remoteLoadErrors.contains(comment.id)
        val shouldShowRemoteNode = !isCollapsed && (comment.pendingRemoteReplyCount > 0 || isLoadingRemote || hasRemoteError)
        if (shouldShowRemoteNode) {
            result += PostDetailViewModel.CommentListItem.RemoteRepliesNode(
                parentId = comment.id,
                depth = depth + 1,
                pendingCount = comment.pendingRemoteReplyCount,
                isLoading = isLoadingRemote,
                hasError = hasRemoteError
            )
        }
    }
    return result
}

private fun List<RedditComment>.collectAutoModeratorIds(): Set<String> {
    val result = mutableSetOf<String>()
    fun traverse(comments: List<RedditComment>) {
        for (comment in comments) {
            val isAutoModerator = comment.author.equals("AutoModerator", ignoreCase = true)
            val isVisualModerator = comment.author.equals("VisualMod", ignoreCase = true)
            val shouldCollapse = isAutoModerator || (isVisualModerator && comment.isStickied)
            if (shouldCollapse && comment.id.isNotBlank()) {
                result += comment.id
            }
            if (comment.children.isNotEmpty()) traverse(comment.children)
        }
    }
    traverse(this)
    return result
}

private fun List<RedditComment>.collectParentCommentIds(): Set<String> {
    if (isEmpty()) return emptySet()
    val result = mutableSetOf<String>()
    fun traverse(comments: List<RedditComment>) {
        for (comment in comments) {
            comment.parentId.normalizeCommentId()?.let(result::add)
            if (comment.children.isNotEmpty()) {
                traverse(comment.children)
            }
        }
    }
    traverse(this)
    return result
}

private fun List<RedditComment>.containsCommentId(targetId: String): Boolean {
    if (targetId.isBlank()) return false
    fun traverse(comments: List<RedditComment>): Boolean {
        for (comment in comments) {
            if (comment.id == targetId) return true
            if (comment.children.isNotEmpty() && traverse(comment.children)) return true
        }
        return false
    }
    return traverse(this)
}

private fun List<RedditComment>.findCommentById(targetId: String): RedditComment? {
    if (targetId.isBlank()) return null
    fun traverse(comments: List<RedditComment>): RedditComment? {
        for (comment in comments) {
            if (comment.id == targetId) return comment
            val child = traverse(comment.children)
            if (child != null) return child
        }
        return null
    }
    return traverse(this)
}

private fun List<RedditComment>.findCommentDepth(targetId: String): Int? {
    if (targetId.isBlank()) return null
    fun traverse(comments: List<RedditComment>, depth: Int): Int? {
        for (comment in comments) {
            if (comment.id == targetId) return depth
            val childDepth = traverse(comment.children, depth + 1)
            if (childDepth != null) return childDepth
        }
        return null
    }
    return traverse(this, 0)
}

private fun RedditComment.totalReplyCount(): Int {
    if (children.isEmpty()) {
        return 0
    }
    var totalDescendantCount = children.size
    for (childComment in children) {
        totalDescendantCount += childComment.totalReplyCount()
    }
    return totalDescendantCount
}

private fun List<RedditComment>.mergeComments(newComments: List<RedditComment>): List<RedditComment> {
    if (newComments.isEmpty()) return this
    var result = this
    val pending = newComments.toMutableList()
    var iterations = 0
    val maxIterations = newComments.size.coerceAtLeast(1)
    while (pending.isNotEmpty() && iterations < maxIterations) {
        val iterator = pending.listIterator()
        var insertedThisRound = false
        while (iterator.hasNext()) {
            val incoming = iterator.next()
            val (updatedTree, inserted) = result.insertOrUpdateComment(incoming)
            if (inserted) {
                result = updatedTree
                iterator.remove()
                insertedThisRound = true
            }
        }
        if (!insertedThisRound) {
            break
        }
        iterations++
    }
    if (pending.isNotEmpty()) {
        result = result.addOrReplaceAtRoot(pending)
    }
    return result
}

private fun List<RedditComment>.insertOrUpdateComment(comment: RedditComment): Pair<List<RedditComment>, Boolean> {
    val parentId = comment.parentId.normalizeCommentId()
    return when {
        parentId == null -> addOrReplaceAtRoot(comment) to true
        comment.parentId?.startsWith("t3_", ignoreCase = true) == true -> addOrReplaceAtRoot(comment) to true
        else -> {
            var inserted = false
            val updated = map { existing ->
                val (updatedComment, childInserted) = existing.insertChildRecursive(parentId, comment)
                if (childInserted) inserted = true
                updatedComment
            }
            if (inserted) updated to true else this to false
        }
    }
}

private fun List<RedditComment>.addOrReplaceAtRoot(comment: RedditComment): List<RedditComment> {
    val existingIndex = indexOfFirst { it.id == comment.id }
    if (existingIndex >= 0) {
        val merged = this[existingIndex].mergeWith(comment)
        return toMutableList().apply { set(existingIndex, merged) }
    }
    return this + comment
}

private fun List<RedditComment>.addOrReplaceAtRoot(comments: Collection<RedditComment>): List<RedditComment> {
    var result = this
    for (comment in comments) {
        result = result.addOrReplaceAtRoot(comment)
    }
    return result
}

private fun RedditComment.insertChildRecursive(parentId: String, newComment: RedditComment): Pair<RedditComment, Boolean> {
    if (id.equals(parentId, ignoreCase = true)) {
        val updatedChildren = children.addOrReplaceAtRoot(newComment)
        return copy(children = updatedChildren) to true
    }
    var inserted = false
    val updatedChildren = children.map { child ->
        val (updatedChild, childInserted) = child.insertChildRecursive(parentId, newComment)
        if (childInserted) inserted = true
        updatedChild
    }
    return if (inserted) copy(children = updatedChildren) to true else this to false
}

private fun RedditComment.mergeWith(other: RedditComment): RedditComment {
    if (id != other.id) return other
    val mergedChildren = mergeChildLists(children, other.children)
    val resolvedAuthor = if (other.author.isNotBlank() && !other.author.equals("[deleted]", ignoreCase = true)) {
        other.author
    } else {
        author
    }
    val resolvedBody = if (other.body.isNotBlank()) other.body else body
    val resolvedBodyHtml = other.bodyHtml ?: bodyHtml
    val resolvedScore = if (other.score != 0) other.score else score
    val resolvedCreated = if (other.createdUtc != 0L) other.createdUtc else createdUtc
    val resolvedParent = other.parentId ?: parentId
    val resolvedFlair = other.authorFlairText?.takeIf { it.isNotBlank() } ?: authorFlairText
    val resolvedPending = if (other.pendingRemoteReplyCount != pendingRemoteReplyCount) {
        other.pendingRemoteReplyCount
    } else {
        pendingRemoteReplyCount
    }
    return other.copy(
        parentId = resolvedParent,
        author = resolvedAuthor,
        body = resolvedBody,
        bodyHtml = resolvedBodyHtml,
        score = resolvedScore,
        createdUtc = resolvedCreated,
        authorFlairText = resolvedFlair,
        pendingRemoteReplyCount = resolvedPending,
        children = mergedChildren
    )
}

private fun mergeChildLists(existing: List<RedditComment>, incoming: List<RedditComment>): List<RedditComment> {
    if (incoming.isEmpty()) return existing
    if (existing.isEmpty()) return incoming
    val result = existing.toMutableList()
    for (child in incoming) {
        val index = result.indexOfFirst { it.id == child.id }
        if (index >= 0) {
            result[index] = result[index].mergeWith(child)
        } else {
            result += child
        }
    }
    return result
}

private fun List<RedditComment>.updatePendingCounts(pendingCounts: Map<String, Int>): List<RedditComment> {
    if (isEmpty() || pendingCounts.isEmpty()) return this
    var changed = false
    val updated = ArrayList<RedditComment>(size)
    for (comment in this) {
        val refreshed = comment.updatePendingCount(pendingCounts)
        if (refreshed !== comment) {
            changed = true
        }
        updated += refreshed
    }
    return if (changed) updated else this
}

private fun RedditComment.updatePendingCount(pendingCounts: Map<String, Int>): RedditComment {
    if (pendingCounts.isEmpty()) return this
    val updatedChildren = children.updatePendingCounts(pendingCounts)
    val pendingValue = pendingCounts[id] ?: pendingRemoteReplyCount
    return if (pendingValue == pendingRemoteReplyCount && updatedChildren === children) {
        this
    } else {
        copy(pendingRemoteReplyCount = pendingValue, children = updatedChildren)
    }
}

private fun String?.normalizeCommentId(): String? {
    if (this == null) return null
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return trimmed.removePrefix("t1_").removePrefix("t3_")
}

private fun shouldDefaultToNewSort(post: RedditPost): Boolean {
    return post.isStickied
}

private fun defaultVisibleRepliesForDepth(depth: Int): Int = when (depth) {
    0 -> 5  // Top level comments show 5 replies
    1 -> 5  // First level replies show 5 replies
    2 -> 4  // Second level replies show 4 replies
    3 -> 3  // Third level replies show 3 replies
    4 -> 2  // Fourth level replies show 2 replies
    else -> 1  // Deep nesting (5+) shows only 1 reply
}

private fun replyBatchIncrementForDepth(depth: Int): Int = defaultVisibleRepliesForDepth(depth).coerceAtLeast(1)
