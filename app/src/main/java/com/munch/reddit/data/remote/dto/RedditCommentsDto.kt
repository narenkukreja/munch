package com.munch.reddit.data.remote.dto

import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.munch.reddit.domain.model.RedditComment
import java.util.LinkedHashSet

private val gson = Gson()

data class RedditCommentListingResponse(
    @SerializedName("data") val data: CommentListingDataDto = CommentListingDataDto()
)

data class CommentListingDataDto(
    @SerializedName("children") val children: List<RedditCommentContainerDto> = emptyList(),
    @SerializedName("after") val after: String? = null,
    @SerializedName("before") val before: String? = null
)

data class RedditCommentContainerDto(
    @SerializedName("kind") val kind: String = "",
    @SerializedName("data") val data: RedditCommentDataDto = RedditCommentDataDto()
)

data class RedditCommentDataDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("author") val author: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("body_html") val bodyHtml: String? = null,
    @SerializedName("author_flair_text") val authorFlairText: String? = null,
    @SerializedName("author_flair_richtext") val authorFlairRichtext: List<FlairRichTextDto>? = null,
    @SerializedName("score") val score: Int = 0,
    @SerializedName("created_utc") val createdUtc: Long = 0L,
    @SerializedName("replies") val replies: JsonElement? = null,
    @SerializedName("children") val moreChildrenIds: List<String>? = null,
    @SerializedName("count") val moreCount: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("parent_id") val parentId: String? = null
)

data class ParsedCommentListing(
    val comments: List<RedditComment> = emptyList(),
    val moreChildrenIds: List<String> = emptyList(),
    val continueThreadTargetIds: List<String> = emptyList(),
    val pendingCountsByParent: Map<String, Int> = emptyMap(),
    val placeholderParentByChild: Map<String, String> = emptyMap()
)

fun RedditCommentListingResponse.parse(): ParsedCommentListing {
    val moreIds = LinkedHashSet<String>()
    val continueThreadIds = LinkedHashSet<String>()
    val pendingCounts = mutableMapOf<String, Int>()
    val placeholderParents = mutableMapOf<String, String>()
    val comments = mutableListOf<RedditComment>()
    var lastCommentId: String? = null
    for (container in data.children) {
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
                }
            }
            container.kind.equals("more", ignoreCase = true) -> {
                container.data.collectPlaceholderIds(
                    moreIdsCollector = moreIds,
                    continueThreadCollector = continueThreadIds,
                    pendingCountCollector = pendingCounts,
                    placeholderParentCollector = placeholderParents,
                    lastVisibleCommentId = lastCommentId
                )
            }
        }
    }
    val finalizedComments = comments.applyPendingCounts(pendingCounts)
    return ParsedCommentListing(
        comments = finalizedComments,
        moreChildrenIds = moreIds.toList(),
        continueThreadTargetIds = continueThreadIds.toList(),
        pendingCountsByParent = pendingCounts,
        placeholderParentByChild = placeholderParents
    )
}

fun List<RedditCommentContainerDto>.toDomainComments(
    defaultParentId: String? = null,
    moreIdsCollector: MutableSet<String>,
    continueThreadCollector: MutableSet<String>,
    pendingCountCollector: MutableMap<String, Int>,
    placeholderParentCollector: MutableMap<String, String>
): List<RedditComment> {
    val result = mutableListOf<RedditComment>()
    var lastCommentId: String? = null
    for (container in this) {
        when {
            container.kind.equals("t1", ignoreCase = true) -> {
                val comment = container.data.toDomain(
                    parentIdFallback = defaultParentId,
                    moreIdsCollector = moreIdsCollector,
                    continueThreadCollector = continueThreadCollector,
                    pendingCountCollector = pendingCountCollector,
                    placeholderParentCollector = placeholderParentCollector
                )
                if (comment != null) {
                    result += comment
                    lastCommentId = comment.id
                }
            }
            container.kind.equals("more", ignoreCase = true) -> {
                container.data.collectPlaceholderIds(
                    moreIdsCollector = moreIdsCollector,
                    continueThreadCollector = continueThreadCollector,
                    pendingCountCollector = pendingCountCollector,
                    placeholderParentCollector = placeholderParentCollector,
                    parentIdFallback = defaultParentId,
                    lastVisibleCommentId = lastCommentId
                )
            }
        }
    }
    return result
}

fun RedditCommentDataDto.toDomain(
    parentIdFallback: String? = null,
    moreIdsCollector: MutableSet<String>,
    continueThreadCollector: MutableSet<String>,
    pendingCountCollector: MutableMap<String, Int>,
    placeholderParentCollector: MutableMap<String, String>
): RedditComment? {
    val resolvedId = id.takeIf { it.isNotBlank() } ?: name?.removePrefix("t1_")?.takeIf { it.isNotBlank() }
    if (resolvedId.isNullOrBlank()) return null
    val resolvedParent = parentId?.takeIf { it.isNotBlank() } ?: parentIdFallback
    val childParentFallback = "t1_$resolvedId"
    val childComments = parseReplies(
        element = replies,
        parentIdFallback = childParentFallback,
        moreIdsCollector = moreIdsCollector,
        continueThreadCollector = continueThreadCollector,
        pendingCountCollector = pendingCountCollector,
        placeholderParentCollector = placeholderParentCollector
    )
    pendingCountCollector.putIfAbsent(resolvedId, pendingCountCollector[resolvedId] ?: 0)
    val resolvedBodyHtml = bodyHtml?.takeIf { it.isNotBlank() }
    val parsedBody = resolvedBodyHtml
        ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() }
        ?.ifBlank { null }
        ?: body.orEmpty()
    return RedditComment(
        id = resolvedId,
        parentId = resolvedParent,
        author = author.orEmpty().ifBlank { "[deleted]" },
        body = parsedBody,
        bodyHtml = resolvedBodyHtml,
        score = score,
        createdUtc = createdUtc,
        authorFlairText = authorFlairText
            ?.trim()
            ?.replace("\u200B", "") // Zero-width space
            ?.replace("\u200C", "") // Zero-width non-joiner
            ?.replace("\u200D", "") // Zero-width joiner
            ?.replace("\uFEFF", "") // Zero-width no-break space
            ?.takeIf { it.isNotBlank() && it.length > 1 },
        authorFlairRichtext = authorFlairRichtext?.map { dto ->
            com.munch.reddit.domain.model.FlairRichText(
                type = dto.type ?: "text",
                text = dto.text,
                alias = dto.alias,
                url = dto.url
            )
        },
        pendingRemoteReplyCount = pendingCountCollector[resolvedId] ?: 0,
        children = childComments
    )
}

private fun parseReplies(
    element: JsonElement?,
    parentIdFallback: String?,
    moreIdsCollector: MutableSet<String>,
    continueThreadCollector: MutableSet<String>,
    pendingCountCollector: MutableMap<String, Int>,
    placeholderParentCollector: MutableMap<String, String>
): List<RedditComment> {
    if (element == null || element.isJsonNull || (element.isJsonPrimitive && element.asJsonPrimitive.isString && element.asString.isBlank())) {
        return emptyList()
    }
    if (!element.isJsonObject) return emptyList()

    return runCatching {
        val response = gson.fromJson(element, RedditCommentListingResponse::class.java)
        response.data.children.toDomainComments(
            defaultParentId = parentIdFallback,
            moreIdsCollector = moreIdsCollector,
            continueThreadCollector = continueThreadCollector,
            pendingCountCollector = pendingCountCollector,
            placeholderParentCollector = placeholderParentCollector
        )
    }.getOrElse { emptyList() }
}

private fun List<RedditComment>.applyPendingCounts(pendingCounts: Map<String, Int>): List<RedditComment> {
    if (isEmpty() || pendingCounts.isEmpty()) return this
    var changed = false
    val updated = ArrayList<RedditComment>(size)
    for (comment in this) {
        val refreshed = comment.applyPendingCount(pendingCounts)
        if (refreshed !== comment) {
            changed = true
        }
        updated += refreshed
    }
    return if (changed) updated else this
}

private fun RedditComment.applyPendingCount(pendingCounts: Map<String, Int>): RedditComment {
    if (pendingCounts.isEmpty()) return this
    val updatedChildren = children.applyPendingCounts(pendingCounts)
    val pending = pendingCounts[id] ?: pendingRemoteReplyCount
    return if (pending == pendingRemoteReplyCount && updatedChildren === children) {
        this
    } else {
        copy(pendingRemoteReplyCount = pending, children = updatedChildren)
    }
}

private fun String?.sanitizeCommentId(): String? {
    if (this == null) return null
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    val withoutPrefix = trimmed.removePrefix("t1_").removePrefix("t3_")
    return withoutPrefix.takeIf { it.isNotEmpty() }
}

fun RedditCommentDataDto.collectPlaceholderIds(
    moreIdsCollector: MutableSet<String>,
    continueThreadCollector: MutableSet<String>,
    pendingCountCollector: MutableMap<String, Int>,
    placeholderParentCollector: MutableMap<String, String>,
    parentIdFallback: String? = null,
    lastVisibleCommentId: String? = null
) {
    val isContinueThread = id.equals("_", ignoreCase = true) && (moreChildrenIds.isNullOrEmpty())
    val normalizedParent = parentId?.sanitizeCommentId()
        ?: parentIdFallback?.sanitizeCommentId()
        ?: lastVisibleCommentId?.sanitizeCommentId()

    val inferredCount = when {
        moreCount > 0 -> moreCount
        !moreChildrenIds.isNullOrEmpty() -> moreChildrenIds.size
        isContinueThread -> 1
        else -> 0
    }

    if (normalizedParent != null && inferredCount > 0) {
        pendingCountCollector[normalizedParent] = (pendingCountCollector[normalizedParent] ?: 0) + inferredCount
    }

    if (isContinueThread) {
        normalizedParent?.let { continueThreadCollector += it }
        return
    }

    moreChildrenIds
        .orEmpty()
        .mapNotNull { it.sanitizeCommentId() }
        .forEach { childId ->
            moreIdsCollector += childId
            if (normalizedParent != null) {
                placeholderParentCollector[childId] = normalizedParent
            }
        }
}
