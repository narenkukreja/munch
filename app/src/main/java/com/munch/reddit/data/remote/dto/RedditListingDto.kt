package com.munch.reddit.data.remote.dto

import android.net.Uri
import androidx.core.text.HtmlCompat
import com.google.gson.annotations.SerializedName
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.domain.model.RedditPostMedia

/**
 * DTOs describing the Reddit listing JSON schema returned by endpoints like /r/{subreddit}.json
 */
data class RedditListingResponse(
    @SerializedName("data") val data: ListingDataDto = ListingDataDto()
)

data class ListingDataDto(
    @SerializedName("children") val children: List<RedditPostContainerDto> = emptyList(),
    @SerializedName("after") val after: String? = null,
    @SerializedName("before") val before: String? = null
)

data class RedditPostContainerDto(
    @SerializedName("kind") val kind: String = "",
    @SerializedName("data") val data: RedditPostDto = RedditPostDto()
)

data class RedditPostDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("subreddit") val subreddit: String = "",
    @SerializedName("subreddit_name_prefixed") val subredditNamePrefixed: String = "",
    @SerializedName("selftext") val selfText: String = "",
    @SerializedName("selftext_html") val selfTextHtml: String? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("url_overridden_by_dest") val overriddenUrl: String? = null,
    @SerializedName("domain") val domain: String = "",
    @SerializedName("is_self") val isSelf: Boolean = false,
    @SerializedName("is_reddit_media_domain") val isRedditMediaDomain: Boolean = false,
    @SerializedName("preview") val preview: RedditPreviewDto? = null,
    @SerializedName("secure_media") val secureMedia: SecureMediaDto? = null,
    @SerializedName("media") val media: SecureMediaDto? = null,
    @SerializedName("secure_media_embed") val secureMediaEmbed: MediaEmbedDto? = null,
    @SerializedName("media_embed") val mediaEmbed: MediaEmbedDto? = null,
    @SerializedName("is_video") val isVideo: Boolean = false,
    @SerializedName("post_hint") val postHint: String? = null,
    @SerializedName("is_gallery") val isGallery: Boolean = false,
    @SerializedName("gallery_data") val galleryData: GalleryDataDto? = null,
    @SerializedName("media_metadata") val mediaMetadata: Map<String, MediaMetadataImageDto>? = null,
    @SerializedName("stickied") val stickied: Boolean = false,
    @SerializedName("permalink") val permalink: String = "",
    @SerializedName("num_comments") val commentCount: Int = 0,
    @SerializedName("ups") val upVotes: Int = 0,
    @SerializedName("downs") val downVotes: Int = 0,
    @SerializedName("score") val score: Int = 0,
    @SerializedName("created_utc") val createdUtc: Long = 0L,
    @SerializedName("over_18") val isOver18: Boolean = false
)

data class RedditCommentDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("score") val score: Int = 0,
    @SerializedName("created_utc") val createdUtc: Long = 0L
)

data class RedditPreviewDto(
    @SerializedName("images") val images: List<PreviewImageDto> = emptyList()
)

data class GalleryDataDto(
    @SerializedName("items") val items: List<GalleryItemDto> = emptyList()
)

data class GalleryItemDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("media_id") val mediaId: String = "",
    @SerializedName("caption") val caption: String? = null
)

data class PreviewImageDto(
    @SerializedName("source") val source: PreviewResolutionDto? = null,
    @SerializedName("resolutions") val resolutions: List<PreviewResolutionDto> = emptyList()
)

data class PreviewResolutionDto(
    @SerializedName("url") val url: String = "",
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0
)

data class SecureMediaDto(
    @SerializedName("reddit_video") val redditVideo: RedditVideoDto? = null,
    @SerializedName("oembed") val oembed: OEmbedDto? = null,
    @SerializedName("type") val type: String? = null
)

data class MediaEmbedDto(
    @SerializedName("content") val content: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null
)

data class RedditVideoDto(
    @SerializedName("fallback_url") val fallbackUrl: String? = null,
    @SerializedName("has_audio") val hasAudio: Boolean = false,
    @SerializedName("hls_url") val hlsUrl: String? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("duration") val duration: Int? = null
)

data class OEmbedDto(
    @SerializedName("provider_url") val providerUrl: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("html") val html: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("thumbnail_width") val thumbnailWidth: Int? = null,
    @SerializedName("thumbnail_height") val thumbnailHeight: Int? = null,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("provider_name") val providerName: String? = null,
    @SerializedName("type") val type: String? = null
)

data class MediaMetadataImageDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("e") val type: String? = null,
    @SerializedName("m") val mimeType: String? = null,
    @SerializedName("p") val previews: List<MediaMetadataResolutionDto> = emptyList(),
    @SerializedName("s") val source: MediaMetadataResolutionDto? = null
)

data class MediaMetadataResolutionDto(
    @SerializedName("u") val url: String = "",
    @SerializedName("x") val width: Int = 0,
    @SerializedName("y") val height: Int = 0
)

fun RedditPostDto.toDomain(): RedditPost {
    val galleryImages = resolveGalleryImages()
    val previewImage = resolvePreviewImage()
    val oembedData = secureMedia?.oembed ?: media?.oembed
    val mediaEmbedData = secureMediaEmbed ?: mediaEmbed
    val normalizedOverriddenUrl = overriddenUrl.cleanUrl()
    val normalizedUrl = url.cleanUrl()
    val normalizedThumbnail = thumbnail.cleanUrl()
    val destinationUrl = normalizedOverriddenUrl ?: normalizedUrl
    val resolvedDomain = domain.ifBlank { destinationUrl?.extractDomain().orEmpty() }
    val thumbnailCandidate = previewImage?.url
        ?: oembedData?.thumbnailUrl.cleanUrl()
        ?: galleryImages.firstOrNull()?.url
        ?: normalizedThumbnail
        ?: destinationUrl
    val resolvedSelfTextHtml = selfTextHtml?.takeIf { it.isNotBlank() }
    val needsFallbackHtmlDecoding = selfText.contains("&lt;", ignoreCase = true) ||
        selfText.contains("<p", ignoreCase = true) ||
        selfText.contains("</", ignoreCase = true)
    val parsedSelfText = resolvedSelfTextHtml
        ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() }
        ?.ifBlank { null }
        ?: if (needsFallbackHtmlDecoding) {
            HtmlCompat.fromHtml(selfText, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        } else {
            selfText
        }

    return RedditPost(
        id = id.ifBlank { name },
        title = title,
        author = author,
        subreddit = subredditNamePrefixed.ifBlank { "r/$subreddit" },
        selfText = parsedSelfText,
        selfTextHtml = resolvedSelfTextHtml,
        thumbnailUrl = thumbnailCandidate,
        url = destinationUrl.orEmpty(),
        domain = resolvedDomain,
        permalink = "https://www.reddit.com$permalink",
        commentCount = commentCount,
        score = score,
        createdUtc = createdUtc,
        media = resolveMedia(
            previewImage = previewImage,
            oembed = oembedData,
            mediaEmbed = mediaEmbedData,
            galleryImages = galleryImages,
            destinationUrl = destinationUrl,
            resolvedDomain = resolvedDomain,
            normalizedThumbnail = normalizedThumbnail,
            isRedditMediaDomain = isRedditMediaDomain
        ),
        isStickied = stickied,
        isNsfw = isOver18
    )
}

private fun RedditPostDto.resolveMedia(
    previewImage: PreviewImageData?,
    oembed: OEmbedDto?,
    mediaEmbed: MediaEmbedDto?,
    galleryImages: List<RedditPostMedia.Image>,
    destinationUrl: String?,
    resolvedDomain: String,
    normalizedThumbnail: String?,
    isRedditMediaDomain: Boolean
): RedditPostMedia {
    val video = secureMedia?.redditVideo ?: media?.redditVideo
    val playbackUrl = video?.hlsUrl.cleanUrl()
        ?: video?.fallbackUrl.cleanUrl()
    return when {
        playbackUrl != null -> RedditPostMedia.Video(
            url = playbackUrl,
            hasAudio = video?.hasAudio ?: false,
            width = video?.width,
            height = video?.height,
            durationSeconds = video?.duration
        )
        galleryImages.isNotEmpty() -> RedditPostMedia.Gallery(galleryImages)
        resolvedDomain.contains("redgifs", ignoreCase = true) && mediaEmbed?.content != null -> {
            val embedUrl = extractRedGifsEmbedUrl(mediaEmbed.content) ?: destinationUrl.orEmpty()
            RedditPostMedia.RedGifs(
                embedUrl = embedUrl,
                thumbnailUrl = oembed?.thumbnailUrl.cleanUrl() ?: normalizedThumbnail,
                width = mediaEmbed.width,
                height = mediaEmbed.height
            )
        }
        oembed.isYouTube() -> RedditPostMedia.YouTube(
            videoId = oembed.extractYouTubeId(destinationUrl),
            watchUrl = destinationUrl ?: oembed.buildYouTubeWatchUrl(),
            thumbnailUrl = oembed?.thumbnailUrl.cleanUrl() ?: normalizedThumbnail,
            title = oembed?.title
        )
        oembed.isStreamable() -> RedditPostMedia.Link(
            url = destinationUrl.orEmpty().ifBlank { oembed?.providerUrl.cleanUrl().orEmpty() },
            previewImageUrl = oembed?.thumbnailUrl.cleanUrl()
                ?: previewImage?.url
                ?: normalizedThumbnail,
            domain = resolvedDomain,
            previewWidth = previewImage?.width,
            previewHeight = previewImage?.height
        )
        isDirectImagePost(destinationUrl, normalizedThumbnail, isRedditMediaDomain) -> {
            val imageUrl = previewImage?.url
                ?: destinationUrl
                ?: normalizedThumbnail
                ?: return RedditPostMedia.None
            RedditPostMedia.Image(
                url = imageUrl,
                width = previewImage?.width,
                height = previewImage?.height
            )
        }
        isLinkPost(destinationUrl, previewImage, resolvedDomain) -> RedditPostMedia.Link(
            url = destinationUrl.orEmpty(),
            previewImageUrl = previewImage?.url
                ?: normalizedThumbnail,
            domain = resolvedDomain,
            previewWidth = previewImage?.width,
            previewHeight = previewImage?.height
        )
        else -> RedditPostMedia.None
    }
}

private fun RedditPostDto.isLinkPost(
    destinationUrl: String?,
    previewImage: PreviewImageData?,
    resolvedDomain: String
): Boolean {
    if (destinationUrl.isNullOrEmpty()) return false
    if (isSelf) return false
    if (isGallery) return false
    if (postHint.equals("rich:video", ignoreCase = true)) return false
    val normalizedDomain = resolvedDomain.lowercase()
    val isRedditHost = normalizedDomain.endsWith("reddit.com") || normalizedDomain.endsWith("redd.it")
    if (postHint.equals("image", ignoreCase = true) && isRedditHost) return false
    return postHint.equals("link", ignoreCase = true)
        || (!isRedditHost && postHint.equals("image", ignoreCase = true))
        || previewImage != null
        || thumbnail.cleanUrl() != null
        || !isRedditHost
}

private fun isDirectImagePost(
    destinationUrl: String?,
    normalizedThumbnail: String?,
    isRedditMediaDomain: Boolean
): Boolean {
    if (isRedditMediaDomain) return true
    if (destinationUrl.isImageUrl()) return true
    if (normalizedThumbnail.isImageUrl()) return true
    return false
}

private fun OEmbedDto?.isYouTube(): Boolean {
    if (this == null) return false
    val provider = providerName.orEmpty().lowercase()
    val typeLower = type.orEmpty().lowercase()
    return provider.contains("youtube") || typeLower.contains("video") && (provider.contains("youtu"))
}

private fun OEmbedDto?.isStreamable(): Boolean {
    if (this == null) return false
    val provider = providerName.orEmpty().lowercase()
    val providerUrlLower = providerUrl.orEmpty().lowercase()
    val htmlLower = html.orEmpty().lowercase()
    return provider.contains("streamable") || providerUrlLower.contains("streamable.com") || htmlLower.contains("streamable.com")
}

private fun OEmbedDto?.extractYouTubeId(fallbackUrl: String?): String {
    val embedSrc = this?.html?.let { html ->
        Regex("src=\"([^\"]+)\"").find(html)?.groupValues?.getOrNull(1)
    }
    val candidate = embedSrc ?: fallbackUrl.orEmpty()
    val uri = runCatching { Uri.parse(candidate) }.getOrNull()
    val lastPath = uri?.lastPathSegment.orEmpty()
    val videoId = when {
        candidate.contains("youtube.com/embed/") -> candidate.substringAfter("/embed/").substringBefore('?')
        candidate.contains("youtube.com/watch") -> uri?.getQueryParameter("v")
        candidate.contains("youtu.be/") -> lastPath
        else -> null
    }
    return videoId ?: fallbackUrl.orEmpty().substringAfterLast('/').substringAfterLast('=')
}

private fun OEmbedDto?.buildYouTubeWatchUrl(): String {
    val videoId = extractYouTubeId(null)
    return if (videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else ""
}

private fun RedditPostDto.resolvePreviewImage(): PreviewImageData? = preview?.bestImage()

private fun RedditPostDto.resolveGalleryImages(): List<RedditPostMedia.Image> {
    if (!isGallery) return emptyList()
    val metadata = mediaMetadata ?: return emptyList()
    val items = galleryData?.items.orEmpty()
    if (items.isEmpty()) return emptyList()
    return items.mapNotNull { item ->
        val imageData = metadata[item.mediaId] ?: return@mapNotNull null
        if (!imageData.isValidImage()) return@mapNotNull null
        val best = imageData.bestImage() ?: return@mapNotNull null
        RedditPostMedia.Image(
            url = best.url,
            width = best.width,
            height = best.height
        )
    }
}

private fun MediaMetadataImageDto.isValidImage(): Boolean {
    if (!status.equals("valid", ignoreCase = true)) return false
    return type.equals("Image", ignoreCase = true) || type.equals("AnimatedImage", ignoreCase = true)
}

private fun MediaMetadataImageDto.bestImage(): PreviewImageData? {
    val candidates = buildList {
        source?.let { src ->
            src.url.cleanUrl()?.let {
                add(PreviewImageData(it, src.width.takeIfPositive(), src.height.takeIfPositive()))
            }
        }
        previews
            .sortedByDescending { it.width }
            .forEach { res ->
                res.url.cleanUrl()?.let {
                    add(PreviewImageData(it, res.width.takeIfPositive(), res.height.takeIfPositive()))
                }
            }
    }
    return candidates.firstOrNull { it.url.startsWith("http") }
}

private fun RedditPreviewDto.bestImage(): PreviewImageData? {
    val image = images.firstOrNull() ?: return null
    val candidates = buildList {
        image.source?.let { src ->
            src.url.cleanUrl()?.let { add(PreviewImageData(it, src.width.takeIfPositive(), src.height.takeIfPositive())) }
        }
        image.resolutions
            .sortedByDescending { it.width }
            .forEach { res ->
                res.url.cleanUrl()?.let {
                    add(PreviewImageData(it, res.width.takeIfPositive(), res.height.takeIfPositive()))
                }
            }
    }
    return candidates.firstOrNull { it.url.startsWith("http") }
}

private fun String?.cleanUrl(): String? = this?.replace("&amp;", "&")?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.isImageUrl(): Boolean {
    val value = this?.lowercase() ?: return false
    return value.endsWith(".jpg") || value.endsWith(".jpeg") || value.endsWith(".png") || value.endsWith(".gif") || value.endsWith(".webp")
}

private fun String.extractDomain(): String {
    return try {
        val uri = Uri.parse(this)
        uri.host.orEmpty().removePrefix("www.")
    } catch (e: Exception) {
        ""
    }
}

private fun Int?.takeIfPositive(): Int? = this?.takeIf { it > 0 }

private fun extractRedGifsEmbedUrl(htmlContent: String): String? {
    // Extract URL from iframe src attribute
    // Example: <iframe src="https://www.redgifs.com/ifr/jollyartisticstoat" ...>
    val srcPattern = Regex("""src="([^"]*redgifs\.com[^"]*)"""")
    return srcPattern.find(htmlContent)?.groupValues?.get(1)
}

private data class PreviewImageData(
    val url: String,
    val width: Int?,
    val height: Int?
)
