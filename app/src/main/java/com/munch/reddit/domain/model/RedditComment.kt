package com.munch.reddit.domain.model

data class FlairRichText(
    val type: String,
    val text: String? = null,
    val alias: String? = null,
    val url: String? = null
)

data class RedditComment(
    val id: String,
    val parentId: String?,
    val author: String,
    val body: String,
    val bodyHtml: String? = null,
    val score: Int,
    val createdUtc: Long,
    val authorFlairText: String? = null,
    val authorFlairRichtext: List<FlairRichText>? = null,
    val isStickied: Boolean = false,
    val pendingRemoteReplyCount: Int = 0,
    val children: List<RedditComment> = emptyList()
)
