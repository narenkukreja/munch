package com.munch.reddit.domain.model

data class RedditComment(
    val id: String,
    val parentId: String?,
    val author: String,
    val body: String,
    val bodyHtml: String? = null,
    val score: Int,
    val createdUtc: Long,
    val authorFlairText: String? = null,
    val pendingRemoteReplyCount: Int = 0,
    val children: List<RedditComment> = emptyList()
)
