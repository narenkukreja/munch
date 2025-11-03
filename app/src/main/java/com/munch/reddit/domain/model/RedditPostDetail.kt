package com.munch.reddit.domain.model

data class RedditPostDetail(
    val post: RedditPost,
    val comments: List<RedditComment>,
    val nextCommentCursor: RedditCommentCursor?
)
