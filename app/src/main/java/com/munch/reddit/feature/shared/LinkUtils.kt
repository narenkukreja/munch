package com.munch.reddit.feature.shared

import android.app.Activity
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
    val normalizedUrl = if (sanitized.startsWith("http://", ignoreCase = true) ||
        sanitized.startsWith("https://", ignoreCase = true)
    ) {
        sanitized
    } else {
        "https://$sanitized"
    }

    val uri = runCatching { Uri.parse(normalizedUrl) }.getOrElse { return }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        openExternalLink(context, uri)
        return
    }

    val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    runCatching {
        val chromePackage = "com.android.chrome"
        context.packageManager.getApplicationInfo(chromePackage, 0)
        customTabsIntent.intent.setPackage(chromePackage)
    }

    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (context !is Activity) {
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        customTabsIntent.launchUrl(context, uri)
    }.onFailure {
        openExternalLink(context, uri)
    }
}

private fun openExternalLink(context: Context, uri: Uri) {
    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching { context.startActivity(fallbackIntent) }
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
