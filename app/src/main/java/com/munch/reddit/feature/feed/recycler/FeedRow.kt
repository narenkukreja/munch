package com.munch.reddit.feature.feed.recycler

import com.munch.reddit.domain.model.RedditPost

sealed interface FeedRow {
    data class Post(
        val post: RedditPost,
        val isRead: Boolean,
        val selectedSubreddit: String
    ) : FeedRow

    data object LoadingFooter : FeedRow

    data object EndSpacer : FeedRow
}

