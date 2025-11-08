package com.munch.reddit.feature.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

private val TrailingUrlDelimiters = charArrayOf(')', ']', '}', '>', ',', '.', ';', ':', '"', '\'')

private fun sanitizeUrl(url: String): String {
    var end = url.length
    while (end > 0) {
        val lastChar = url[end - 1]
        if (lastChar !in TrailingUrlDelimiters) break
        if (lastChar == ')') {
            val prefix = url.substring(0, end - 1)
            val openCount = prefix.count { it == '(' }
            val closeCount = prefix.count { it == ')' }
            if (openCount > closeCount) {
                break
            }
        }
        end--
    }
    return url.substring(0, end)
}

fun openLinkInCustomTab(context: Context, url: String) {
    val sanitized = sanitizeUrl(url.trim())
    if (sanitized.isBlank()) return
    val uri = runCatching { Uri.parse(sanitized) }.getOrElse { return }
    val customTabsIntent = CustomTabsIntent.Builder()
        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        .setShowTitle(true)
        .setUrlBarHidingEnabled(true)
        .build().apply {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    runCatching {
        customTabsIntent.launchUrl(context, uri)
    }.onFailure {
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallbackIntent) }
    }
}

fun openYouTubeVideo(context: Context, videoId: String, watchUrl: String) {
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(appIntent) }
        .onFailure { runCatching { context.startActivity(webIntent) } }
}

fun isImageUrl(url: String): Boolean {
    val lowerUrl = url.lowercase()
    return lowerUrl.endsWith(".jpg") ||
           lowerUrl.endsWith(".jpeg") ||
           lowerUrl.endsWith(".png") ||
           lowerUrl.endsWith(".gif") ||
           lowerUrl.endsWith(".webp") ||
           lowerUrl.contains(".jpg?") ||
           lowerUrl.contains(".jpeg?") ||
           lowerUrl.contains(".png?") ||
           lowerUrl.contains(".gif?") ||
           lowerUrl.contains(".webp?")
}
