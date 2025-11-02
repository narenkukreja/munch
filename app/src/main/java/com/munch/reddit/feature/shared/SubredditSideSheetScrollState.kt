package com.munch.reddit.feature.shared

/**
 * In-memory store so the subreddit picker remembers its last scroll position
 * even when navigating between screens.
 */
object SubredditSideSheetScrollState {
    private var savedScroll: Int = 0

    fun update(position: Int) {
        savedScroll = position.coerceAtLeast(0)
    }

    fun current(): Int = savedScroll
}
