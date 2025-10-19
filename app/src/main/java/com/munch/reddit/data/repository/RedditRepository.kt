package com.munch.reddit.data.repository

import android.util.Log
import com.google.gson.Gson
import com.munch.reddit.data.remote.RedditApiService
import com.munch.reddit.data.remote.dto.ParsedCommentListing
import com.munch.reddit.data.remote.dto.RedditCommentListingResponse
import com.munch.reddit.data.remote.dto.RedditListingResponse
import com.munch.reddit.data.remote.dto.RedditMoreChildrenResponse
import com.munch.reddit.data.remote.dto.collectPlaceholderIds
import com.munch.reddit.data.remote.dto.parse
import com.munch.reddit.data.remote.dto.toDomain
import com.munch.reddit.data.subreddit.CachedSubredditIcon
import com.munch.reddit.data.subreddit.SubredditIconStorage
import com.munch.reddit.domain.model.RedditComment
import com.munch.reddit.domain.model.RedditCommentCursor
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.domain.model.RedditPostDetail
import java.util.LinkedHashSet
import java.util.concurrent.TimeUnit

interface RedditRepository {
    data class PostPage(
        val posts: List<RedditPost>,
        val nextPageToken: String?
    )

    data class PostCommentsPage(
        val comments: List<RedditComment>,
        val nextCursor: RedditCommentCursor?,
        val pendingCountsByParent: Map<String, Int> = emptyMap()
    )

    suspend fun fetchSubreddit(
        subreddit: String,
        limit: Int = 50,
        sort: String = "hot",
        timeRange: String? = null,
        after: String? = null
    ): PostPage

    suspend fun fetchPostDetail(
        permalink: String,
        limit: Int = 100,
        sort: String = "top"
    ): RedditPostDetail

    suspend fun fetchMoreComments(
        cursor: RedditCommentCursor,
        limit: Int = 100,
        sort: String = "top"
    ): PostCommentsPage

    suspend fun fetchSubredditIcon(subreddit: String): String?

    suspend fun searchPosts(
        query: String,
        limit: Int = 50,
        sort: String = "relevance",
        timeRange: String? = null,
        after: String? = null
    ): PostPage
}

class RedditRepositoryImpl(
    private val api: RedditApiService,
    private val iconStorage: SubredditIconStorage
) : RedditRepository {

    private val gson = Gson()
    private val defaultCommentDepth = 3
    private val maxMoreChildrenBatch = 100
    private val defaultCommentSort = "top"
    private val subredditIconCache = mutableMapOf<String, CachedSubredditIcon>()
    private var isIconCacheLoaded = false
    private val iconCacheTtlMillis = TimeUnit.HOURS.toMillis(3)

    override suspend fun fetchSubreddit(
        subreddit: String,
        limit: Int,
        sort: String,
        timeRange: String?,
        after: String?
    ): RedditRepository.PostPage {
        val normalized = subreddit.trim().ifBlank { "all" }
        val basePath = if (normalized.equals("all", ignoreCase = true)) {
            "r/all"
        } else {
            "r/${normalized.lowercase()}"
        }
        val normalizedSort = sort.trim().lowercase().ifBlank { "hot" }
        val normalizedTimeRange = timeRange?.trim()?.lowercase()?.ifBlank { null }
        val path = "$basePath/${normalizedSort}"
        val response = api.getListing(
            path = path,
            limit = limit,
            sort = normalizedSort,
            timeRange = normalizedTimeRange,
            after = after,
            rawJson = 1
        )
        val posts = response.data.children
            .asSequence()
            .filter { it.kind.equals("t3", ignoreCase = true) }
            .map { it.data.toDomain() }
            .toList()
        return RedditRepository.PostPage(
            posts = posts,
            nextPageToken = response.data.after
        )
    }

    override suspend fun fetchPostDetail(permalink: String, limit: Int, sort: String): RedditPostDetail {
        val permalinkPaths = normalizePermalink(permalink)
        val normalizedSort = sort.normalizeSort()

        val responses = api.getComments(
            path = permalinkPaths.full,
            limit = limit,
            depth = defaultCommentDepth,
            rawJson = 1,
            sort = normalizedSort,
            after = null
        )

        val postListing = responses.firstOrNull()?.let { element ->
            gson.fromJson(element, RedditListingResponse::class.java)
        }
        val postDto = postListing?.data?.children?.firstOrNull()?.data
        val post = postDto?.toDomain()
            ?: throw IllegalStateException("Unable to load post details")

        val commentsListing = responses.getOrNull(1)?.let { element ->
            gson.fromJson(element, RedditCommentListingResponse::class.java)
        }
        val parsedComments = commentsListing?.parse() ?: ParsedCommentListing()
        val comments = parsedComments.comments
        val nextCursor = buildCursorForPost(
            post = post,
            moreChildrenIds = parsedComments.moreChildrenIds,
            continueThreadIds = parsedComments.continueThreadTargetIds,
            placeholderParents = parsedComments.placeholderParentByChild,
            permalinkPath = permalinkPaths.compact
        )

        return RedditPostDetail(
            post = post,
            comments = comments,
            nextCommentCursor = nextCursor
        )
    }

    override suspend fun fetchMoreComments(
        cursor: RedditCommentCursor,
        limit: Int,
        sort: String
    ): RedditRepository.PostCommentsPage {
        if (!cursor.hasWork()) {
            return RedditRepository.PostCommentsPage(emptyList(), null, emptyMap())
        }

        val normalizedSort = sort.normalizeSort()
        val batchSize = limit.coerceIn(1, maxMoreChildrenBatch)

        val forcedContinueParent = cursor.consumeForcedContinueParent()
        val moreBatch = if (forcedContinueParent == null) cursor.takeMoreBatch(batchSize) else emptyList()
        if (moreBatch.isNotEmpty()) {
            val consumedParents = cursor.consumePlaceholderParents(moreBatch)
            val response = api.getMoreChildren(
                linkId = cursor.linkId,
                childrenCsv = moreBatch.joinToString(","),
                sort = normalizedSort,
                depth = defaultCommentDepth,
                rawJson = 1
            )

            val parsed = parseMoreChildrenResponse(response)
            cursor.enqueueMore(
                ids = parsed.moreChildrenIds.mapNotNull { sanitizeCommentId(it) },
                parentMap = parsed.placeholderParentByChild
            )
            parsed.continueThreadTargetIds
                .mapNotNull { sanitizeCommentId(it) }
                .forEach { cursor.enqueueContinueThread(it) }

            val orderedComments = orderComments(moreBatch, parsed.comments)
            val nextCursor = if (cursor.hasWork()) cursor.snapshot() else null
            val pendingCounts = parsed.pendingCountsByParent.toMutableMap().apply {
                consumedParents.forEach { (parentId, count) ->
                    this[parentId] = (this[parentId] ?: 0) + count
                }
            }

            Log.d(TAG, "MoreChildren batch=${moreBatch.size} comments=${orderedComments.size} pending=$pendingCounts next=${nextCursor != null}")
            return RedditRepository.PostCommentsPage(
                comments = orderedComments,
                nextCursor = nextCursor,
                pendingCountsByParent = pendingCounts
            )
        }

        val continueParent = forcedContinueParent ?: cursor.pollContinueThreadParent()
            ?: return RedditRepository.PostCommentsPage(emptyList(), null, emptyMap())

        // ✅ Use short-form path for nested comment threads
        val postId = cursor.linkId.removePrefix("t3_")
        val focusTarget = continueParent.removePrefix("t1_")
        val focusedPath = "comments/$postId/comment/$focusTarget"
        Log.d(TAG, "Focused path used for continue thread: $focusedPath")
        if (forcedContinueParent != null) {
            Log.d(TAG, "Forced continue branch served for parent=$focusTarget (raw=$continueParent)")
        }

        val responses = api.getComments(
            path = focusedPath,
            limit = batchSize,
            depth = defaultCommentDepth,
            rawJson = 1,
            sort = normalizedSort,
            after = null,
            comment = null
        )

        val commentsListing = responses.getOrNull(1)?.let { element ->
            gson.fromJson(element, RedditCommentListingResponse::class.java)
        }
        val parsed = commentsListing?.parse() ?: ParsedCommentListing()

        val ordered = orderComments(listOf(continueParent), parsed.comments)
        val nextCursor = if (cursor.hasWork()) cursor.snapshot() else null
        cursor.enqueueMore(
            ids = parsed.moreChildrenIds.mapNotNull { sanitizeCommentId(it) },
            parentMap = parsed.placeholderParentByChild
        )
        parsed.continueThreadTargetIds
            .mapNotNull { sanitizeCommentId(it) }
            .forEach { cursor.enqueueContinueThread(it) }
        val pendingCounts = parsed.pendingCountsByParent.toMutableMap().apply {
            putIfAbsent(continueParent, getOrDefault(continueParent, 0))
        }

        Log.d(TAG, "ContinueThread parent=$continueParent comments=${ordered.size} pending=$pendingCounts next=${nextCursor != null}")
        return RedditRepository.PostCommentsPage(
            comments = ordered,
            nextCursor = nextCursor,
            pendingCountsByParent = pendingCounts
        )
    }

    override suspend fun fetchSubredditIcon(subreddit: String): String? {
        ensureIconCacheLoaded()
        val normalized = subreddit.removePrefix("r/").removePrefix("R/").trim().lowercase()
        if (normalized.isBlank() || normalized == "all") {
            return null
        }
        val now = System.currentTimeMillis()
        val cached = subredditIconCache[normalized]
        if (cached != null && now - cached.fetchedAtEpochMillis <= iconCacheTtlMillis) {
            return cached.url
        }
        val fetchResult = runCatching {
            api.getSubredditAbout(normalized, rawJson = 1).data.iconImg.sanitizeIconUrl()
        }
        if (fetchResult.isSuccess) {
            val sanitized = fetchResult.getOrNull()
            subredditIconCache[normalized] = CachedSubredditIcon(
                url = sanitized,
                fetchedAtEpochMillis = now
            )
            persistIconCache()
            return sanitized
        }
        return cached?.url
    }

    override suspend fun searchPosts(
        query: String,
        limit: Int,
        sort: String,
        timeRange: String?,
        after: String?
    ): RedditRepository.PostPage {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotBlank()) { "Search query cannot be blank" }

        val response = api.searchPosts(
            query = normalizedQuery,
            limit = limit,
            sort = sort,
            timeRange = timeRange,
            after = after,
            rawJson = 1
        )

        val posts = response.data.children
            .asSequence()
            .filter { it.kind.equals("t3", ignoreCase = true) }
            .map { it.data.toDomain() }
            .toList()

        return RedditRepository.PostPage(
            posts = posts,
            nextPageToken = response.data.after
        )
    }

    private suspend fun ensureIconCacheLoaded() {
        if (isIconCacheLoaded) return
        val stored = iconStorage.read()
        subredditIconCache.clear()
        subredditIconCache.putAll(stored)
        isIconCacheLoaded = true
    }

    private suspend fun persistIconCache() {
        iconStorage.write(subredditIconCache)
    }

    private data class PermalinkPaths(
        val full: String,
        val compact: String
    )

    private fun normalizePermalink(permalink: String): PermalinkPaths {
        val normalized = permalink.trim()
            .removePrefix("https://www.reddit.com")
            .removePrefix("http://www.reddit.com")
            .removePrefix("https://reddit.com")
            .removePrefix("http://reddit.com")
            .trimStart('/')
            .trimEnd('/')
        require(normalized.isNotBlank()) { "Invalid permalink" }
        val withoutJson = normalized.removeSuffix(".json")
        val compact = buildCompactPermalink(withoutJson)
        return PermalinkPaths(full = withoutJson, compact = compact)
    }

    private fun buildCompactPermalink(path: String): String {
        val segments = path.split('/')
            .mapNotNull { segment -> segment.takeIf { it.isNotBlank() } }
        val commentsIndex = segments.indexOfFirst { it.equals("comments", ignoreCase = true) }
        if (commentsIndex >= 0 && commentsIndex + 1 < segments.size) {
            val postId = segments[commentsIndex + 1]
            if (postId.isNotBlank()) {
                return "comments/$postId"
            }
        }
        return path
    }

    private fun buildCursorForPost(
        post: RedditPost,
        moreChildrenIds: List<String>,
        continueThreadIds: List<String>,
        placeholderParents: Map<String, String>,
        permalinkPath: String
    ): RedditCommentCursor? {
        val sanitizedIds = moreChildrenIds.mapNotNull { sanitizeCommentId(it) }.distinct()
        val postId = post.id.takeUnless { it.isBlank() } ?: return null
        val linkId = if (postId.startsWith("t3_")) postId else "t3_$postId"
        val continueIds = continueThreadIds.mapNotNull { sanitizeCommentId(it) }.distinct()
        if (sanitizedIds.isEmpty() && continueIds.isEmpty()) return null
        val sanitizedPlaceholderParents = placeholderParents.mapNotNull { (childId, parentId) ->
            val sanitizedChild = sanitizeCommentId(childId) ?: return@mapNotNull null
            val sanitizedParent = sanitizeCommentId(parentId) ?: parentId
            sanitizedChild to sanitizedParent
        }.toMap()
        return RedditCommentCursor(
            linkId = linkId,
            initialMoreChildren = sanitizedIds,
            initialContinueThreadTargets = continueIds,
            permalinkPath = permalinkPath.removeSuffix(".json"),
            initialPlaceholderParents = sanitizedPlaceholderParents
        )
    }

    private fun parseMoreChildrenResponse(response: RedditMoreChildrenResponse): ParsedCommentListing {
        val things = response.json?.data?.things.orEmpty()
        val moreIds = LinkedHashSet<String>()
        val continueThreadIds = LinkedHashSet<String>()
        val pendingCounts = mutableMapOf<String, Int>()
        val placeholderParents = mutableMapOf<String, String>()
        val comments = mutableListOf<RedditComment>()
        var lastCommentId: String? = null
        var lastRealParent: String? = null
        for (container in things) {
            when {
                container.kind.equals("t1", ignoreCase = true) -> {
                    val comment = container.data.toDomain(
                        moreIdsCollector = moreIds,
                        continueThreadCollector = continueThreadIds,
                        pendingCountCollector = pendingCounts,
                        placeholderParentCollector = placeholderParents
                    )
                    if (comment != null) {
                        comments += comment
                        lastCommentId = comment.id
                        lastRealParent = comment.parentId
                    }
                }
                container.kind.equals("more", ignoreCase = true) -> {
                    container.data.collectPlaceholderIds(
                        moreIdsCollector = moreIds,
                        continueThreadCollector = continueThreadIds,
                        pendingCountCollector = pendingCounts,
                        placeholderParentCollector = placeholderParents,
                        parentIdFallback = lastRealParent,
                        lastVisibleCommentId = lastCommentId
                    )
                }
            }
        }
        return ParsedCommentListing(
            comments = comments,
            moreChildrenIds = moreIds.toList(),
            continueThreadTargetIds = continueThreadIds.toList(),
            pendingCountsByParent = pendingCounts,
            placeholderParentByChild = placeholderParents
        )
    }

    private fun orderComments(requestedIds: List<String>, comments: List<RedditComment>): List<RedditComment> {
        if (requestedIds.isEmpty() || comments.isEmpty()) return comments
        val byId = comments.associateBy { it.id }
        val ordered = requestedIds.mapNotNull { byId[it] }
        if (ordered.size == comments.size) {
            return ordered
        }
        val extras = comments.filterNot { ordered.contains(it) }
        return ordered + extras
    }

    private fun sanitizeCommentId(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withoutPrefix = trimmed.removePrefix("t1_").removePrefix("t3_")
        return withoutPrefix.takeIf { it.isNotEmpty() }
    }

    private fun String?.normalizeSort(): String {
        val normalized = this?.trim()?.lowercase().orEmpty()
        return normalized.ifBlank { defaultCommentSort }
    }

    private fun String?.sanitizeIconUrl(): String? =
        this?.replace("&amp;", "&")?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        private const val TAG = "RedditRepository"
    }
}
