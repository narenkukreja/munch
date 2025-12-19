package com.munch.reddit.feature.shared.sidesheet

import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.min

sealed class SubredditSideSheetRow {
    abstract val stableId: String

    data class SectionHeader(
        val title: String,
        val extraTopSpacing: Boolean
    ) : SubredditSideSheetRow() {
        override val stableId: String = "header:$title"
    }

    data class Subreddit(
        val subreddit: String,
        val display: String,
        val iconUrl: String?,
        val fallbackColor: Int?,
        val selected: Boolean,
        val isAll: Boolean
    ) : SubredditSideSheetRow() {
        override val stableId: String = "subreddit:$subreddit"
    }

    data object Settings : SubredditSideSheetRow() {
        override val stableId: String = "settings"
    }

    data object EditFavorites : SubredditSideSheetRow() {
        override val stableId: String = "edit_favorites"
    }
}

fun buildSubredditSideSheetRows(
    selectedSubreddit: String,
    favorites: List<String>,
    subredditIcons: Map<String, String?>,
    exploreSubreddits: List<String>
): List<SubredditSideSheetRow> {
    val rows = mutableListOf<SubredditSideSheetRow>()

    rows += SubredditSideSheetRow.SectionHeader(title = "Main", extraTopSpacing = false)
    rows += SubredditSideSheetRow.Subreddit(
        subreddit = "all",
        display = "all",
        iconUrl = null,
        fallbackColor = null,
        selected = "all".equals(selectedSubreddit, ignoreCase = true),
        isAll = true
    )
    rows += SubredditSideSheetRow.Settings

    val filteredFavorites = favorites.filterNot { it.equals("all", ignoreCase = true) }
    val filteredExplore = exploreSubreddits.filter { explore ->
        favorites.none { it.equals(explore, ignoreCase = true) }
    }

    val favoritesResult = computeFallbackColors(filteredFavorites)
    val exploreResult = computeFallbackColors(filteredExplore, favoritesResult.lastHue)

    if (filteredFavorites.isNotEmpty()) {
        rows += SubredditSideSheetRow.SectionHeader(title = "Favorites", extraTopSpacing = true)
        filteredFavorites.forEach { subreddit ->
            val (display, iconKey) = normalizeForDisplayAndLookup(subreddit)
            rows += SubredditSideSheetRow.Subreddit(
                subreddit = subreddit,
                display = display,
                iconUrl = subredditIcons[iconKey],
                fallbackColor = favoritesResult.colors[subreddit],
                selected = subreddit.equals(selectedSubreddit, ignoreCase = true),
                isAll = iconKey == "all"
            )
        }
        rows += SubredditSideSheetRow.EditFavorites
    }

    if (filteredExplore.isNotEmpty()) {
        rows += SubredditSideSheetRow.SectionHeader(title = "Explore", extraTopSpacing = true)
        filteredExplore.forEach { subreddit ->
            val (display, iconKey) = normalizeForDisplayAndLookup(subreddit)
            rows += SubredditSideSheetRow.Subreddit(
                subreddit = subreddit,
                display = display,
                iconUrl = subredditIcons[iconKey],
                fallbackColor = exploreResult.colors[subreddit],
                selected = subreddit.equals(selectedSubreddit, ignoreCase = true),
                isAll = iconKey == "all"
            )
        }
    }

    return rows
}

internal fun fallbackColorForSubreddit(subreddit: String): Int {
    val hue = baseFallbackHue(subreddit).toFloat()
    return colorFromHue(hue)
}

private fun normalizeForDisplayAndLookup(subreddit: String): Pair<String, String> {
    val normalized = subreddit.removePrefix("r/").removePrefix("R/").trim()
    val lookupKey = normalized.lowercase()
    val display = if (lookupKey == "all") "all" else normalized
    return display to lookupKey
}

private fun computeFallbackColors(
    subreddits: List<String>,
    initialHue: Float? = null
): FallbackColorComputation {
    if (subreddits.isEmpty()) return FallbackColorComputation(emptyMap(), initialHue)
    val colors = mutableMapOf<String, Int>()
    var lastHue = initialHue
    for (sub in subreddits) {
        var hue = baseFallbackHue(sub).toFloat()
        if (lastHue != null && hueDistance(hue, lastHue) < 25f) {
            hue = (lastHue + 47f) % 360f
        }
        val color = colorFromHue(hue)
        colors[sub] = color
        lastHue = hue
    }
    return FallbackColorComputation(colors, lastHue)
}

private fun colorFromHue(hue: Float): Int {
    return ColorUtils.HSLToColor(floatArrayOf(hue, 0.55f, 0.45f))
}

private fun baseFallbackHue(subreddit: String): Int {
    val normalized = subreddit
        .removePrefix("r/")
        .removePrefix("R/")
        .trim()
        .lowercase()
    if (normalized.isBlank()) return 210
    val hash = normalized.hashCode()
    return ((hash % 360) + 360) % 360
}

private data class FallbackColorComputation(
    val colors: Map<String, Int>,
    val lastHue: Float?
)

private fun hueDistance(a: Float, b: Float): Float {
    val diff = abs(a - b) % 360f
    return min(diff, 360f - diff)
}

