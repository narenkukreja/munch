package com.munch.reddit.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing subreddit metadata such as icons.
 * This is shared across multiple ViewModels to avoid redundant fetching.
 */
class SubredditRepository(
    private val redditRepository: RedditRepository
) {
    private val _subredditIcons = MutableStateFlow<Map<String, String?>>(emptyMap())
    val subredditIcons: StateFlow<Map<String, String?>> = _subredditIcons.asStateFlow()

    private val iconCache = mutableMapOf<String, String?>()
    private val pendingFetches = mutableSetOf<String>()

    /**
     * Fetches the icon for a subreddit if not already cached or pending.
     * Updates the StateFlow when the icon is fetched.
     */
    suspend fun fetchSubredditIcon(subreddit: String) {
        val key = normalizeSubredditKey(subreddit)
        if (key.isEmpty()) return

        // Special case for r/all - no icon
        if (key == "all") {
            if (!iconCache.containsKey(key)) {
                iconCache[key] = null
                _subredditIcons.value = iconCache.toMap()
            }
            return
        }

        // Skip if already cached or currently being fetched
        if (iconCache.containsKey(key) || pendingFetches.contains(key)) {
            return
        }

        // Mark as pending
        pendingFetches.add(key)

        try {
            val iconUrl = runCatching {
                redditRepository.fetchSubredditIcon(key)
            }.getOrNull()

            iconCache[key] = iconUrl
            _subredditIcons.value = iconCache.toMap()
        } finally {
            pendingFetches.remove(key)
        }
    }

    /**
     * Prefetch icons for multiple subreddits at once.
     */
    suspend fun prefetchSubredditIcons(subreddits: List<String>) {
        subreddits.forEach { subreddit ->
            fetchSubredditIcon(subreddit)
        }
    }

    /**
     * Get the cached icon URL for a subreddit, or null if not cached.
     */
    fun getCachedIcon(subreddit: String): String? {
        val key = normalizeSubredditKey(subreddit)
        return iconCache[key]
    }

    /**
     * Clear all cached icons.
     */
    fun clearCache() {
        iconCache.clear()
        pendingFetches.clear()
        _subredditIcons.value = emptyMap()
    }

    private fun normalizeSubredditKey(subreddit: String): String {
        return subreddit
            .removePrefix("r/")
            .removePrefix("R/")
            .trim()
            .lowercase()
    }
}
