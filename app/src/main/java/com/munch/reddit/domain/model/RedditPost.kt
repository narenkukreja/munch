package com.munch.reddit.domain.model

data class RedditPost(
    val id: String,
    val title: String,
    val author: String,
    val subreddit: String,
    val selfText: String,
    val selfTextHtml: String? = null,
    val thumbnailUrl: String?,
    val url: String,
    val domain: String,
    val permalink: String,
    val commentCount: Int,
    val score: Int,
    val createdUtc: Long,
    val media: RedditPostMedia,
    val isStickied: Boolean,
    val isNsfw: Boolean
)

sealed class RedditPostMedia {
    object None : RedditPostMedia()
    data class Image(
        val url: String,
        val width: Int?,
        val height: Int?
    ) : RedditPostMedia()

    data class Video(
        val url: String,
        val hasAudio: Boolean,
        val width: Int?,
        val height: Int?,
        val durationSeconds: Int?
    ) : RedditPostMedia()

    data class Link(
        val url: String,
        val previewImageUrl: String?,
        val domain: String,
        val previewWidth: Int?,
        val previewHeight: Int?
    ) : RedditPostMedia()

    data class Gallery(
        val images: List<Image>
    ) : RedditPostMedia()

    data class YouTube(
        val videoId: String,
        val watchUrl: String,
        val thumbnailUrl: String?,
        val title: String?
    ) : RedditPostMedia()

    data class RedGifs(
        val embedUrl: String,
        val thumbnailUrl: String?,
        val width: Int?,
        val height: Int?
    ) : RedditPostMedia()

    data class Streamable(
        val shortcode: String,
        val url: String,
        val thumbnailUrl: String?
    ) : RedditPostMedia()

    data class StreamFF(
        val url: String,
        val embedUrl: String,
        val thumbnailUrl: String?
    ) : RedditPostMedia()

    data class StreamIn(
        val url: String,
        val embedUrl: String,
        val thumbnailUrl: String?
    ) : RedditPostMedia()
}
