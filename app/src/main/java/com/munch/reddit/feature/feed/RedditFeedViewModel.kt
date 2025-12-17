package com.munch.reddit.feature.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.munch.reddit.data.repository.RedditRepository
import com.munch.reddit.data.repository.SubredditRepository
import com.munch.reddit.domain.SubredditCatalog
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.feature.shared.SubredditSideSheetScrollState
import java.io.Serializable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RedditFeedViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: RedditRepository,
    private val subredditRepository: SubredditRepository,
    private val appPreferences: com.munch.reddit.data.AppPreferences
) : ViewModel() {

    enum class FeedSortOption(val displayLabel: String, val apiValue: String) {
        HOT(displayLabel = "Hot", apiValue = "hot"),
        TOP(displayLabel = "Top", apiValue = "top"),
        NEW(displayLabel = "New", apiValue = "new")
    }

    enum class TopTimeRange(val displayLabel: String, val apiValue: String) {
        HOUR(displayLabel = "This hour", apiValue = "hour"),
        DAY(displayLabel = "Today", apiValue = "day"),
        WEEK(displayLabel = "This week", apiValue = "week"),
        MONTH(displayLabel = "This month", apiValue = "month"),
        YEAR(displayLabel = "This year", apiValue = "year"),
        ALL(displayLabel = "All time", apiValue = "all")
    }

    private fun loadFavoriteSubreddits(): MutableList<String> {
        val stored = appPreferences.getFavoriteSubreddits()
            .map { normalizeSubreddit(it) }
            .filter { it.isNotBlank() && !it.equals("all", ignoreCase = true) }
            .distinctBy { it.lowercase() }
        val fallback = SubredditCatalog.defaultSubreddits
            .filterNot { it.equals("all", ignoreCase = true) }
        return (if (stored.isNotEmpty()) stored else fallback).toMutableList()
    }

    private var favoriteSubreddits: MutableList<String> = loadFavoriteSubreddits()
    private var availableSubreddits: MutableList<String> = mutableListOf<String>().apply {
        add("all")
        addAll(favoriteSubreddits)
    }

    private val _uiState = MutableStateFlow(
        RedditFeedUiState(
            selectedSubreddit = availableSubreddits.first(),
            selectedSort = FeedSortOption.HOT,
            selectedTopTimeRange = TopTimeRange.DAY,
            favoriteSubreddits = favoriteSubreddits.toList()
        )
    )
    val uiState: StateFlow<RedditFeedUiState> = _uiState.asStateFlow()

    var subredditOptions: List<String> = availableSubreddits
    val sortOptions: List<FeedSortOption> = FeedSortOption.values().toList()
    val topTimeRangeOptions: List<TopTimeRange> = TopTimeRange.values().toList()

    private var currentSubreddit: String = availableSubreddits.first()
    private var currentSortOption: FeedSortOption = FeedSortOption.HOT
    private var currentTopTimeRange: TopTimeRange = TopTimeRange.DAY
    private var nextPageToken: String? = null
    private var isLoadingNextPage: Boolean = false
    private var refreshJob: Job? = null
    // In-memory cache to avoid reloading when switching tabs back and forth
    private val feedCache = mutableMapOf<String, CachedFeed>()
    // Navigation stack for subreddit history (r/all is always at the bottom)
    private val subredditStack = mutableListOf<String>(availableSubreddits.first())
    // Scroll position cache per subreddit
    private val scrollPositionCache = mutableMapOf<String, ScrollPosition>()
    private val subredditSortState = mutableMapOf(
        availableSubreddits.first().lowercase() to SortState(
            sort = FeedSortOption.HOT,
            topTimeRange = TopTimeRange.DAY
        )
    )

    data class ScrollPosition(
        val firstVisibleItemIndex: Int = 0,
        val firstVisibleItemScrollOffset: Int = 0
    ) : Serializable

    private data class CachedFeed(
        val posts: List<RedditPost>,
        val nextPageToken: String?,
        val hasMore: Boolean,
        val sort: FeedSortOption,
        val topTimeRange: TopTimeRange
    )

    private data class SortState(
        val sort: FeedSortOption,
        val topTimeRange: TopTimeRange
    ) : Serializable

    private fun restoreFromSavedState() {
        val savedSubreddit = savedStateHandle.get<String>(KEY_CURRENT_SUBREDDIT)?.takeIf { it.isNotBlank() }
        if (!savedSubreddit.isNullOrBlank()) {
            currentSubreddit = savedSubreddit
        }

        if (availableSubreddits.none { it.equals(currentSubreddit, ignoreCase = true) }) {
            currentSubreddit = availableSubreddits.firstOrNull() ?: "all"
        }

        savedStateHandle.get<String>(KEY_CURRENT_SORT)?.let { sortName ->
            runCatching { FeedSortOption.valueOf(sortName) }
                .getOrNull()
                ?.let { restored -> currentSortOption = restored }
        }

        savedStateHandle.get<String>(KEY_CURRENT_TOP_TIME_RANGE)?.let { rangeName ->
            runCatching { TopTimeRange.valueOf(rangeName) }
                .getOrNull()
                ?.let { restored -> currentTopTimeRange = restored }
        }

        savedStateHandle.get<ArrayList<String>>(KEY_SUBREDDIT_STACK)
            ?.takeIf { it.isNotEmpty() }
            ?.let { restoredStack ->
                val filtered = restoredStack.filter { restored ->
                    availableSubreddits.any { it.equals(restored, ignoreCase = true) }
                }
                subredditStack.clear()
                if (filtered.isNotEmpty()) {
                    subredditStack.addAll(filtered)
                } else {
                    subredditStack.add(availableSubreddits.first())
                }
            }

        if (!subredditStack.contains(currentSubreddit)) {
            subredditStack.add(currentSubreddit)
        }

        savedStateHandle.get<HashMap<String, SortState>>(KEY_SUBREDDIT_SORT_STATE)
            ?.takeIf { it.isNotEmpty() }
            ?.let { restoredMap ->
                subredditSortState.clear()
                subredditSortState.putAll(restoredMap)
            }

        val currentKey = currentSubreddit.lowercase()
        if (!subredditSortState.containsKey(currentKey)) {
            subredditSortState[currentKey] =
                SortState(sort = currentSortOption, topTimeRange = currentTopTimeRange)
        }

        savedStateHandle.get<HashMap<String, ScrollPosition>>(KEY_SCROLL_POSITION_CACHE)
            ?.takeIf { it.isNotEmpty() }
            ?.let { restoredPositions ->
                scrollPositionCache.clear()
                scrollPositionCache.putAll(restoredPositions)
            }

        savedStateHandle.get<Boolean>(KEY_HIDE_READ_POSTS)?.let { hideRead ->
            _uiState.update { state -> state.copy(hideReadPosts = hideRead) }
        }

        savedStateHandle.get<Int>(KEY_SIDE_SHEET_SCROLL)?.let { savedScroll ->
            SubredditSideSheetScrollState.update(savedScroll)
        }

        val restoredScrollPosition = scrollPositionCache[currentSubreddit.lowercase()]
        _uiState.update { state ->
            state.copy(
                selectedSubreddit = currentSubreddit,
                selectedSort = currentSortOption,
                selectedTopTimeRange = currentTopTimeRange,
                scrollPosition = restoredScrollPosition,
                favoriteSubreddits = favoriteSubreddits.toList()
            )
        }
    }

    private fun persistSelectionState() {
        savedStateHandle[KEY_CURRENT_SUBREDDIT] = currentSubreddit
        savedStateHandle[KEY_CURRENT_SORT] = currentSortOption.name
        savedStateHandle[KEY_CURRENT_TOP_TIME_RANGE] = currentTopTimeRange.name
        savedStateHandle[KEY_SUBREDDIT_STACK] = ArrayList(subredditStack)
        savedStateHandle[KEY_SUBREDDIT_SORT_STATE] = HashMap(subredditSortState)
    }

    private fun persistScrollPositions() {
        savedStateHandle[KEY_SCROLL_POSITION_CACHE] = HashMap(scrollPositionCache)
    }

    private fun persistHideRead() {
        savedStateHandle[KEY_HIDE_READ_POSTS] = _uiState.value.hideReadPosts
    }

    private fun cacheKey(
        subreddit: String = currentSubreddit,
        sort: FeedSortOption = currentSortOption,
        top: TopTimeRange = currentTopTimeRange
    ): String {
        val sub = subreddit.trim().lowercase()
        // Only TOP uses time range; others share a single bucket
        val time = if (sort == FeedSortOption.TOP) top.apiValue else "_"
        return listOf(sub, sort.apiValue, time).joinToString(":")
    }

    private fun normalizeSubreddit(subreddit: String): String =
        subreddit.removePrefix("r/").removePrefix("R/").trim().lowercase()

    private fun prefetchSubredditIcons() {
        viewModelScope.launch {
            val allSubreddits = availableSubreddits + SubredditCatalog.exploreSubreddits
            subredditRepository.prefetchSubredditIcons(allSubreddits.distinct())
        }
    }

    fun syncFavoritesFromPreferences() {
        val stored = loadFavoriteSubreddits()
        val current = favoriteSubreddits.map { it.lowercase() }
        val incoming = stored.map { it.lowercase() }
        if (current == incoming) return
        applyFavoriteUpdate(stored, persist = false)
    }

    fun addFavoriteSubreddit(subreddit: String) {
        val normalized = normalizeSubreddit(subreddit)
        if (normalized.isBlank() || normalized.equals("all", ignoreCase = true)) return
        if (favoriteSubreddits.any { it.equals(normalized, ignoreCase = true) }) return
        val updated = favoriteSubreddits.toMutableList().apply { add(normalized) }
        applyFavoriteUpdate(updated, persist = true)
    }

    fun saveFavoriteOrder(newOrder: List<String>) {
        applyFavoriteUpdate(newOrder, persist = true)
    }

    private fun applyFavoriteUpdate(newFavorites: List<String>, persist: Boolean) {
        val sanitized = newFavorites
            .map { normalizeSubreddit(it) }
            .filter { it.isNotBlank() && !it.equals("all", ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .ifEmpty {
                SubredditCatalog.defaultSubreddits.filterNot { it.equals("all", ignoreCase = true) }
            }

        favoriteSubreddits = sanitized.toMutableList()
        availableSubreddits = mutableListOf<String>().apply {
            add("all")
            addAll(favoriteSubreddits)
        }
        subredditOptions = availableSubreddits

        favoriteSubreddits.forEach { sub ->
            subredditSortState.putIfAbsent(
                sub.lowercase(),
                SortState(sort = FeedSortOption.HOT, topTimeRange = TopTimeRange.DAY)
            )
        }

        if (availableSubreddits.none { it.equals(currentSubreddit, ignoreCase = true) }) {
            currentSubreddit = availableSubreddits.firstOrNull() ?: "all"
        }

        if (!subredditStack.contains(currentSubreddit)) {
            subredditStack.clear()
            subredditStack.add(currentSubreddit)
        }

        if (persist) {
            appPreferences.setFavoriteSubreddits(favoriteSubreddits)
        }

        persistSelectionState()

        _uiState.update { state ->
            state.copy(
                favoriteSubreddits = favoriteSubreddits.toList(),
                selectedSubreddit = currentSubreddit
            )
        }

        prefetchSubredditIcons()
    }

    fun updateSideSheetScroll(position: Int) {
        val normalized = position.coerceAtLeast(0)
        SubredditSideSheetScrollState.update(normalized)
        savedStateHandle[KEY_SIDE_SHEET_SCROLL] = normalized
    }

    fun getSideSheetScroll(): Int =
        savedStateHandle.get<Int>(KEY_SIDE_SHEET_SCROLL) ?: SubredditSideSheetScrollState.current()

    private fun enqueueIconFetch(subreddit: String) {
        viewModelScope.launch {
            subredditRepository.fetchSubredditIcon(subreddit)
        }
    }

    init {
        restoreFromSavedState()
        refresh()
        prefetchSubredditIcons()
        // Load read posts from preferences
        _uiState.update { state ->
            state.copy(
                readPostIds = appPreferences.getReadPostIds()
            )
        }
        // Collect subreddit icons from shared repository
        viewModelScope.launch {
            subredditRepository.subredditIcons.collect { icons ->
                _uiState.update { state -> state.copy(subredditIcons = icons) }
            }
        }
    }

    fun refresh(limit: Int = DEFAULT_LIMIT, clearExisting: Boolean = false) {
        nextPageToken = null
        isLoadingNextPage = false
        // On refresh, always show read posts again
        _uiState.update { it.copy(hideReadPosts = false) }
        persistHideRead()
        subredditSortState[currentSubreddit.lowercase()] =
            SortState(sort = currentSortOption, topTimeRange = currentTopTimeRange)
        persistSelectionState()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { state ->
                val initialPosts = if (clearExisting) emptyList() else state.posts
                state.copy(
                    isLoading = true,
                    isAppending = false,
                    errorMessage = null,
                    selectedSubreddit = currentSubreddit,
                    selectedSort = currentSortOption,
                    selectedTopTimeRange = currentTopTimeRange,
                    hasMore = true,
                    posts = initialPosts
                )
            }
            val timeRangeParameter = if (currentSortOption == FeedSortOption.TOP) {
                currentTopTimeRange.apiValue
            } else {
                null
            }
            val result = runCatching {
                repository.fetchSubreddit(
                    subreddit = currentSubreddit,
                    limit = limit,
                    sort = currentSortOption.apiValue,
                    timeRange = timeRangeParameter,
                    after = null
                )
            }
            result
                .onSuccess { page ->
                    nextPageToken = page.nextPageToken
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            posts = page.posts,
                            errorMessage = null,
                            selectedSort = currentSortOption,
                            selectedTopTimeRange = currentTopTimeRange,
                            isAppending = false,
                            hasMore = page.nextPageToken != null
                        )
                    }
                    // Update cache for this key
                    feedCache[cacheKey()] = CachedFeed(
                        posts = page.posts,
                        nextPageToken = page.nextPageToken,
                        hasMore = page.nextPageToken != null,
                        sort = currentSortOption,
                        topTimeRange = currentTopTimeRange
                    )
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Failed to load feed",
                            selectedSort = currentSortOption,
                            selectedTopTimeRange = currentTopTimeRange,
                            isAppending = false
                        )
                    }
                }
        }
    }

    fun loadMore(limit: Int = DEFAULT_LIMIT) {
        val token = nextPageToken
        if (!_uiState.value.hasMore || token.isNullOrBlank()) return
        if (_uiState.value.isLoading || isLoadingNextPage) return
        isLoadingNextPage = true
        val timeRangeParameter = if (currentSortOption == FeedSortOption.TOP) {
            currentTopTimeRange.apiValue
        } else {
            null
        }
        viewModelScope.launch {
            try {
                _uiState.update { state -> state.copy(isAppending = true) }
                val result = runCatching {
                    repository.fetchSubreddit(
                        subreddit = currentSubreddit,
                        limit = limit,
                        sort = currentSortOption.apiValue,
                        timeRange = timeRangeParameter,
                        after = token
                    )
                }
                result
                    .onSuccess { page ->
                        nextPageToken = page.nextPageToken
                        _uiState.update { state ->
                            val merged = (state.posts + page.posts).distinctBy { it.id }
                            state.copy(
                                posts = merged,
                                isAppending = false,
                                errorMessage = null,
                                hasMore = page.nextPageToken != null
                            )
                        }
                        // Update cache with the appended result
                        val key = cacheKey()
                        val existing = feedCache[key]
                        val merged = ((existing?.posts ?: emptyList()) + page.posts).distinctBy { it.id }
                        feedCache[key] = CachedFeed(
                            posts = merged,
                            nextPageToken = page.nextPageToken,
                            hasMore = page.nextPageToken != null,
                            sort = currentSortOption,
                            topTimeRange = currentTopTimeRange
                        )
                    }
                    .onFailure { throwable ->
                        _uiState.update { state ->
                            state.copy(
                                isAppending = false,
                                errorMessage = throwable.message ?: state.errorMessage
                            )
                        }
                    }
            } finally {
                isLoadingNextPage = false
            }
        }
    }

    fun selectSubreddit(subreddit: String) {
        val target = availableSubreddits.firstOrNull { it.equals(subreddit, ignoreCase = true) }
            ?: subreddit.lowercase()
        if (target.equals(currentSubreddit, ignoreCase = true)) return
        enqueueIconFetch(target)
        subredditSortState[currentSubreddit.lowercase()] =
            SortState(sort = currentSortOption, topTimeRange = currentTopTimeRange)
        currentSubreddit = target
        currentSortOption = FeedSortOption.HOT
        currentTopTimeRange = TopTimeRange.DAY
        nextPageToken = null
        isLoadingNextPage = false
        subredditSortState[target.lowercase()] =
            SortState(sort = FeedSortOption.HOT, topTimeRange = TopTimeRange.DAY)
        // Push to navigation stack
        subredditStack.add(target)
        // When going forward, clear saved scroll position for fresh start
        scrollPositionCache.remove(target.lowercase())
        persistScrollPositions()
        persistSelectionState()
        // If we have a cached feed for this subreddit with current sort/time, use it
        val cached = feedCache[cacheKey(subreddit = currentSubreddit)]
        if (cached != null && cached.sort == FeedSortOption.HOT && cached.topTimeRange == TopTimeRange.DAY) {
            nextPageToken = cached.nextPageToken
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isAppending = false,
                    errorMessage = null,
                    posts = cached.posts,
                    selectedSubreddit = currentSubreddit,
                    selectedSort = FeedSortOption.HOT,
                    selectedTopTimeRange = TopTimeRange.DAY,
                    hasMore = cached.hasMore,
                    scrollPosition = null,  // Clear scroll position when going forward
                    hideReadPosts = false
                )
            }
        } else {
            refresh(clearExisting = true)
        }
    }

    fun navigateBack(): Boolean {
        // Can't go back if we're at the base (r/all)
        if (subredditStack.size <= 1) return false
        subredditSortState[currentSubreddit.lowercase()] =
            SortState(sort = currentSortOption, topTimeRange = currentTopTimeRange)
        // Pop current subreddit
        subredditStack.removeLastOrNull()
        // Get the previous subreddit
        val previousSubreddit = subredditStack.lastOrNull() ?: return false
        enqueueIconFetch(previousSubreddit)
        currentSubreddit = previousSubreddit
        val restoredSortState = subredditSortState[previousSubreddit.lowercase()]
            ?: SortState(sort = FeedSortOption.HOT, topTimeRange = TopTimeRange.DAY)
        currentSortOption = restoredSortState.sort
        currentTopTimeRange = restoredSortState.topTimeRange
        persistSelectionState()
        // Get saved scroll position for the previous subreddit
        val savedScrollPosition = getScrollPosition(currentSubreddit)
        // Set navigating back flag for animation
        _uiState.update { it.copy(isNavigatingBack = true) }
        // Load the previous subreddit from cache or fetch
        val cached = feedCache[cacheKey(
            subreddit = currentSubreddit,
            sort = currentSortOption,
            top = currentTopTimeRange
        )]
        if (cached != null) {
            nextPageToken = cached.nextPageToken
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isAppending = false,
                    errorMessage = null,
                    posts = cached.posts,
                    selectedSubreddit = currentSubreddit,
                    selectedSort = currentSortOption,
                    selectedTopTimeRange = currentTopTimeRange,
                    hasMore = cached.hasMore,
                    scrollPosition = savedScrollPosition,
                    isNavigatingBack = false,
                    hideReadPosts = false
                )
            }
        } else {
            refresh(clearExisting = true)
            _uiState.update { it.copy(isNavigatingBack = false) }
        }
        return true
    }

    fun selectSort(sortOption: FeedSortOption) {
        if (sortOption == currentSortOption) {
            return
        }
        currentSortOption = sortOption
        subredditSortState[currentSubreddit.lowercase()] =
            SortState(sort = currentSortOption, topTimeRange = currentTopTimeRange)
        persistSelectionState()
        refresh()
    }

    fun selectTopTimeRange(timeRange: TopTimeRange) {
        if (timeRange == currentTopTimeRange) {
            return
        }
        currentTopTimeRange = timeRange
        subredditSortState[currentSubreddit.lowercase()] =
            SortState(sort = currentSortOption, topTimeRange = currentTopTimeRange)
        persistSelectionState()
        if (currentSortOption == FeedSortOption.TOP) {
            refresh()
        } else {
            _uiState.update { state ->
                state.copy(selectedTopTimeRange = currentTopTimeRange)
            }
        }
    }

    fun search(query: String, limit: Int = DEFAULT_LIMIT) {
        nextPageToken = null
        isLoadingNextPage = false
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    isAppending = false,
                    errorMessage = null,
                    selectedSubreddit = query,
                    hasMore = true,
                    posts = emptyList()
                )
            }
            val result = runCatching {
                repository.searchPosts(
                    query = query,
                    limit = limit,
                    sort = "relevance",
                    timeRange = null,
                    after = null
                )
            }
            result
                .onSuccess { page ->
                    nextPageToken = page.nextPageToken
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            posts = page.posts,
                            errorMessage = null,
                            isAppending = false,
                            hasMore = page.nextPageToken != null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Failed to search",
                            isAppending = false
                        )
                    }
                }
        }
    }

    data class RedditFeedUiState(
        val isLoading: Boolean = false,
        val isAppending: Boolean = false,
        val posts: List<RedditPost> = emptyList(),
        val errorMessage: String? = null,
        val selectedSubreddit: String = "all",
        val selectedSort: FeedSortOption = FeedSortOption.HOT,
        val selectedTopTimeRange: TopTimeRange = TopTimeRange.DAY,
        val hasMore: Boolean = true,
        val subredditIcons: Map<String, String?> = emptyMap(),
        val scrollPosition: ScrollPosition? = null,
        val isNavigatingBack: Boolean = false,
        val readPostIds: Set<String> = emptySet(),
        val hideReadPosts: Boolean = false,
        val favoriteSubreddits: List<String> = SubredditCatalog.defaultSubreddits.filterNot { it.equals("all", ignoreCase = true) }
    )

    fun saveScrollPosition(subreddit: String, firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        scrollPositionCache[subreddit.lowercase()] = ScrollPosition(
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset
        )
        persistScrollPositions()
    }

    fun getScrollPosition(subreddit: String): ScrollPosition? {
        return scrollPositionCache[subreddit.lowercase()]
    }

    fun consumeScrollPosition() {
        _uiState.update { state ->
            if (state.scrollPosition == null) {
                state
            } else {
                state.copy(scrollPosition = null)
            }
        }
    }

    fun getPreviousSubreddit(): String? {
        return if (subredditStack.size >= 2) {
            subredditStack[subredditStack.size - 2]
        } else {
            null
        }
    }

    fun canNavigateBack(): Boolean = subredditStack.size > 1

    fun getPreviousSubredditFeed(): List<RedditPost>? {
        val prevSubreddit = getPreviousSubreddit() ?: return null
        val sortState = subredditSortState[prevSubreddit.lowercase()]
            ?: SortState(sort = FeedSortOption.HOT, topTimeRange = TopTimeRange.DAY)
        val cached = feedCache[cacheKey(
            subreddit = prevSubreddit,
            sort = sortState.sort,
            top = sortState.topTimeRange
        )]
        return cached?.posts
    }

    companion object {
        private const val DEFAULT_LIMIT = 50
        private const val KEY_CURRENT_SUBREDDIT = "reddit_feed.current_subreddit"
        private const val KEY_CURRENT_SORT = "reddit_feed.current_sort"
        private const val KEY_CURRENT_TOP_TIME_RANGE = "reddit_feed.current_top_time_range"
        private const val KEY_SUBREDDIT_STACK = "reddit_feed.subreddit_stack"
        private const val KEY_SUBREDDIT_SORT_STATE = "reddit_feed.subreddit_sort_state"
        private const val KEY_SCROLL_POSITION_CACHE = "reddit_feed.scroll_positions"
        private const val KEY_HIDE_READ_POSTS = "reddit_feed.hide_read_posts"
        private const val KEY_SIDE_SHEET_SCROLL = "reddit_feed.side_sheet_scroll"
    }

    fun dismissPost(id: String) {
        appPreferences.markPostRead(id)
        val cacheKey = cacheKey()
        feedCache[cacheKey]?.let { cached ->
            feedCache[cacheKey] = cached.copy(
                posts = cached.posts.filterNot { it.id == id }
            )
        }
        _uiState.update { state ->
            state.copy(
                readPostIds = state.readPostIds + id,
                posts = state.posts.filterNot { it.id == id }
            )
        }
    }

    fun markPostRead(id: String) {
        appPreferences.markPostRead(id)
        _uiState.update { state ->
            state.copy(readPostIds = state.readPostIds + id)
        }
    }

    fun toggleHideReadPosts() {
        val newValue = !_uiState.value.hideReadPosts
        _uiState.update { it.copy(hideReadPosts = newValue) }
        persistHideRead()
    }
}
