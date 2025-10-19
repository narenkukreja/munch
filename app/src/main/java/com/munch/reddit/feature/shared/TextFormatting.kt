package com.munch.reddit.feature.shared

import android.graphics.Typeface
import android.text.style.ImageSpan
import android.text.style.QuoteSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.text.HtmlCompat

private const val LINK_TAG = "link"
private const val SUBREDDIT_TAG = "subreddit"
private const val IMAGE_TAG = "image"
private val LinkRegex = Regex("https?://[^\\s]+")
private val SubredditRegex = Regex("(?<![A-Za-z0-9_])r/[A-Za-z0-9_]+", RegexOption.IGNORE_CASE)
private val UserRegex = Regex("(?<![A-Za-z0-9_])u/[A-Za-z0-9_-]+", RegexOption.IGNORE_CASE)
private val TrailingUrlDelimiters = charArrayOf(')', ']', '}', '>', ',', '.', ';', ':', '"', '\'')

private fun sanitizeLinkValue(raw: String): String {
    var end = raw.length
    while (end > 0) {
        val lastChar = raw[end - 1]
        if (lastChar !in TrailingUrlDelimiters) break
        if (lastChar == ')') {
            val prefix = raw.substring(0, end - 1)
            val openCount = prefix.count { it == '(' }
            val closeCount = prefix.count { it == ')' }
            if (openCount > closeCount) {
                break
            }
        }
        end--
    }
    return raw.substring(0, end)
}

fun parseHtmlText(raw: String): String {
    if (raw.isBlank()) return raw
    return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
}

private data class ProcessedText(
    val text: String,
    val quoteRanges: List<Pair<Int, Int>>
)

private fun preprocessContent(raw: String): ProcessedText {
    if (raw.isBlank()) return ProcessedText(raw, emptyList())
    val lines = raw.split('\n')
    val builder = StringBuilder()
    val quoteRanges = mutableListOf<Pair<Int, Int>>()
    var cursor = 0
    lines.forEachIndexed { index, line ->
        val trimmed = line.trimStart()
        val isQuote = trimmed.startsWith(">")
        val leadingWhitespace = line.length - trimmed.length
        val processedLine = if (isQuote) {
            val withoutMarker = trimmed.drop(1).let { remaining ->
                if (remaining.startsWith(" ")) remaining.drop(1) else remaining
            }
            buildString {
                repeat(leadingWhitespace) { append(' ') }
                append(withoutMarker)
            }
        } else {
            line
        }
        builder.append(processedLine)
        if (isQuote) {
            val start = cursor
            val end = cursor + processedLine.length
            if (start < end) {
                quoteRanges += start to end
            }
        }
        cursor += processedLine.length
        if (index < lines.lastIndex) {
            builder.append('\n')
            cursor += 1
        }
    }
    return ProcessedText(builder.toString(), quoteRanges)
}

private fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
    if (text.isEmpty()) return this
    var end = length
    while (end > 0 && text[end - 1].isWhitespace()) {
        end--
    }
    return if (end == length) this else subSequence(0, end)
}

fun buildLinkAnnotatedString(
    raw: String,
    linkColor: Color,
    allowSubredditClick: Boolean,
    quoteColor: Color?
): AnnotatedString {
    val processed = preprocessContent(raw)
    val content = processed.text
    val builder = AnnotatedString.Builder()
    builder.append(content)

    if (quoteColor != null) {
        processed.quoteRanges.forEach { (start, end) ->
            if (start >= 0 && end <= content.length && start < end) {
                builder.addStyle(SpanStyle(color = quoteColor), start, end)
            }
        }
    }

    LinkRegex.findAll(content).forEach { match ->
        val sanitized = sanitizeLinkValue(match.value)
        if (sanitized.isBlank()) return@forEach
        val start = match.range.first
        val end = start + sanitized.length
        builder.addStyle(
            SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold),
            start,
            end
        )
        builder.addStringAnnotation(
            tag = LINK_TAG,
            annotation = sanitized,
            start = start,
            end = end
        )
    }

    SubredditRegex.findAll(content).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        builder.addStyle(
            SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold),
            start,
            end
        )
        if (allowSubredditClick) {
            builder.addStringAnnotation(
                tag = SUBREDDIT_TAG,
                annotation = match.value.removePrefix("r/").removePrefix("R/"),
                start = start,
                end = end
            )
        }
    }

    UserRegex.findAll(content).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        builder.addStyle(
            SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold),
            start,
            end
        )
    }

    return builder.toAnnotatedString()
}

private fun buildLinkAnnotatedStringFromHtml(
    html: String,
    fallbackText: String,
    linkColor: Color,
    allowSubredditClick: Boolean,
    quoteColor: Color?
): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val content = spanned.toString().ifBlank { fallbackText }
    if (content.isEmpty()) {
        return AnnotatedString.Builder().toAnnotatedString()
    }
    if (spanned.length == 0) {
        return buildLinkAnnotatedString(content, linkColor, allowSubredditClick, quoteColor)
    }

    val imageSpans = spanned.getSpans(0, spanned.length, ImageSpan::class.java)
        .sortedBy { span -> spanned.getSpanStart(span) }

    val builder = AnnotatedString.Builder()
    val indexMap = IntArray(spanned.length + 1)
    indexMap[0] = 0

    fun mapIndex(original: Int): Int {
        val index = original.coerceIn(0, spanned.length)
        return indexMap[index].coerceIn(0, builder.length)
    }

    var spanPointer = 0
    var originalIndex = 0
    while (originalIndex < spanned.length) {
        indexMap[originalIndex] = builder.length
        val nextImageSpan = imageSpans.getOrNull(spanPointer)
        val nextImageStart = nextImageSpan?.let { spanned.getSpanStart(it) } ?: Int.MAX_VALUE

        if (originalIndex == nextImageStart && nextImageSpan != null) {
            val spanStart = spanned.getSpanStart(nextImageSpan).coerceAtLeast(0)
            val spanEnd = spanned.getSpanEnd(nextImageSpan).coerceAtMost(spanned.length)
            val builderStart = builder.length
            val imageUrl = nextImageSpan.source.orEmpty().ifBlank {
                spanned.getSpans(spanStart, spanEnd, URLSpan::class.java).firstOrNull()?.url.orEmpty()
            }
            val isGif = imageUrl.lowercase().contains(".gif")
            val label = if (isGif) "View GIF" else "View Image"
            builder.append(label)
            val builderEnd = builder.length
            if (imageUrl.isNotBlank()) {
                val sanitized = sanitizeLinkValue(imageUrl)
                builder.addStyle(
                    SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold),
                    builderStart,
                    builderEnd
                )
                builder.addStringAnnotation(
                    tag = IMAGE_TAG,
                    annotation = sanitized,
                    start = builderStart,
                    end = builderEnd
                )
            }
            indexMap[spanStart] = builderStart
            val boundedEnd = spanEnd.coerceAtMost(spanned.length)
            for (i in (spanStart + 1) until boundedEnd) {
                indexMap[i] = builderEnd
            }
            if (boundedEnd in indexMap.indices) {
                indexMap[boundedEnd] = builderEnd
            }
            originalIndex = spanEnd
            spanPointer++
        } else {
            val segmentEnd = minOf(nextImageStart, spanned.length)
            if (segmentEnd <= originalIndex) {
                originalIndex = segmentEnd
                continue
            }
            val textSegment = spanned.subSequence(originalIndex, segmentEnd).toString()
            val builderStart = builder.length
            builder.append(textSegment)
            val builderEnd = builder.length
            val lengthDelta = segmentEnd - originalIndex
            for (i in 0 until lengthDelta) {
                indexMap[originalIndex + i] = builderStart + i
            }
            indexMap[segmentEnd] = builderEnd
            originalIndex = segmentEnd
        }
    }
    indexMap[spanned.length] = builder.length

    fun applyStyle(rangeStart: Int, rangeEnd: Int, style: SpanStyle) {
        val start = mapIndex(rangeStart)
        val end = mapIndex(rangeEnd)
        if (start < end) {
            builder.addStyle(style, start, end)
        }
    }

    if (quoteColor != null) {
        spanned.getSpans(0, spanned.length, QuoteSpan::class.java).forEach { span ->
            applyStyle(spanned.getSpanStart(span), spanned.getSpanEnd(span), SpanStyle(color = quoteColor))
        }
    }

    spanned.getSpans(0, spanned.length, StyleSpan::class.java).forEach { span ->
        val style = when (span.style) {
            Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            else -> null
        }
        if (style != null) {
            applyStyle(spanned.getSpanStart(span), spanned.getSpanEnd(span), style)
        }
    }

    spanned.getSpans(0, spanned.length, UnderlineSpan::class.java).forEach { span ->
        applyStyle(spanned.getSpanStart(span), spanned.getSpanEnd(span), SpanStyle(textDecoration = TextDecoration.Underline))
    }

    spanned.getSpans(0, spanned.length, StrikethroughSpan::class.java).forEach { span ->
        applyStyle(spanned.getSpanStart(span), spanned.getSpanEnd(span), SpanStyle(textDecoration = TextDecoration.LineThrough))
    }

    spanned.getSpans(0, spanned.length, URLSpan::class.java).forEach { span ->
        val sanitized = sanitizeLinkValue(span.url)
        val start = mapIndex(spanned.getSpanStart(span))
        val end = mapIndex(spanned.getSpanEnd(span))
        if (start < end) {
            builder.addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold), start, end)
            builder.addStringAnnotation(
                tag = LINK_TAG,
                annotation = sanitized,
                start = start,
                end = end
            )
        }
    }

    val finalContentForLinks = builder.toString()
    LinkRegex.findAll(finalContentForLinks).forEach { match ->
        val sanitized = sanitizeLinkValue(match.value)
        if (sanitized.isBlank()) return@forEach
        val start = match.range.first
        val end = start + sanitized.length
        builder.addStyle(
            SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold),
            start,
            end
        )
        builder.addStringAnnotation(
            tag = LINK_TAG,
            annotation = sanitized,
            start = start,
            end = end
        )
    }

    val finalContent = builder.toString()
    SubredditRegex.findAll(finalContent).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        builder.addStyle(
            SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold),
            start,
            end
        )
        if (allowSubredditClick) {
            builder.addStringAnnotation(
                tag = SUBREDDIT_TAG,
                annotation = match.value.removePrefix("r/").removePrefix("R/"),
                start = start,
                end = end
            )
        }
    }

    UserRegex.findAll(finalContent).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        builder.addStyle(
            SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold),
            start,
            end
        )
    }

    return builder.toAnnotatedString()
}

@Composable
fun LinkifiedText(
    text: String,
    htmlText: String? = null,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    quoteColor: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onLinkClick: ((String) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    onSubredditClick: ((String) -> Unit)? = null,
    onTextClick: (() -> Unit)? = null
) {
    val allowSubredditClick = onSubredditClick != null
    val annotated = remember(text, htmlText, linkColor, allowSubredditClick, quoteColor) {
        val sanitizedHtml = htmlText?.takeIf { it.isNotBlank() }
        val result = if (sanitizedHtml != null) {
            buildLinkAnnotatedStringFromHtml(
                html = sanitizedHtml,
                fallbackText = text,
                linkColor = linkColor,
                allowSubredditClick = allowSubredditClick,
                quoteColor = quoteColor
            )
        } else {
            buildLinkAnnotatedString(text, linkColor, allowSubredditClick, quoteColor)
        }
        result.trimTrailingWhitespace()
    }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val pointerModifier = if (onLinkClick != null || onImageClick != null || onSubredditClick != null || onTextClick != null) {
        Modifier.pointerInput(annotated, onLinkClick, onImageClick, onSubredditClick, onTextClick) {
            detectTapGestures { offsetPosition ->
                val layout = layoutResult ?: return@detectTapGestures
                val offset = layout.getOffsetForPosition(offsetPosition)
                val imageAnnotation = annotated.getStringAnnotations(IMAGE_TAG, offset, offset).firstOrNull()
                if (imageAnnotation != null) {
                    val target = imageAnnotation.item
                    if (target.isNotBlank()) {
                        onImageClick?.invoke(target)
                    }
                    return@detectTapGestures
                }
                val linkAnnotation = annotated.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()
                if (linkAnnotation != null) {
                    val target = linkAnnotation.item
                    if (target.isLikelyImageUrl() && onImageClick != null) {
                        onImageClick(target)
                    } else {
                        onLinkClick?.invoke(target)
                    }
                    return@detectTapGestures
                }
                val subredditAnnotation = annotated.getStringAnnotations(SUBREDDIT_TAG, offset, offset).firstOrNull()
                if (subredditAnnotation != null) {
                    onSubredditClick?.invoke(subredditAnnotation.item)
                    return@detectTapGestures
                }
                onTextClick?.invoke()
            }
        }
    } else {
        Modifier
    }

    BasicText(
        text = annotated,
        modifier = modifier.then(pointerModifier),
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult = it }
    )
}

fun String.isLikelyImageUrl(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".png") ||
        lower.endsWith(".gif") ||
        lower.endsWith(".webp") ||
        "preview.redd.it" in lower ||
        "i.redd.it" in lower
}
