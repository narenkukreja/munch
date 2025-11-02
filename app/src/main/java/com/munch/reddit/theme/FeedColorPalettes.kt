package com.munch.reddit.theme

import androidx.compose.ui.graphics.Color

data class FeedColorPalette(
    val themeId: String,
    val displayName: String,
    val postBackground: Color,
    val spacerBackground: Color,
    val title: Color,
    val subreddit: Color,
    val metaInfo: Color,
    val pinnedLabel: Color,
    val modLabel: Color,
    val opLabel: Color,
    val visualModLabel: Color,
    val postBorder: Color? = null
)

private val wormiPalette = FeedColorPalette(
    themeId = "wormi",
    displayName = "Wormi",
    postBackground = Color(0xFF232531),
    spacerBackground = Color(0xFF181A26),
    title = Color(0xFFE6E8F4),
    subreddit = Color(0xFFA7B6DD),
    metaInfo = Color(0xFFBFC2D5),
    pinnedLabel = Color(0xFF2ECC71),
    modLabel = Color(0xFF2ECC71),
    opLabel = Color(0xFFFF5252),
    visualModLabel = Color(0xFF2ECC71),
    postBorder = null
)

private val narwhalPalette = FeedColorPalette(
    themeId = "narwhal",
    displayName = "Narwhal",
    postBackground = Color(0xFF202020),
    spacerBackground = Color(0xFF141414),
    title = Color(0xFFFFFFFF),
    subreddit = Color(0xFF3F5C7C),
    metaInfo = Color(0xFF747474),
    pinnedLabel = Color(0xFF2ECC71),
    modLabel = Color(0xFF2ECC71),
    opLabel = Color(0xFFFF5252),
    visualModLabel = Color(0xFF2ECC71),
    postBorder = Color(0xFF747474)
)

private val redditPalette = FeedColorPalette(
    themeId = "reddit",
    displayName = "Reddit",
    postBackground = Color(0xFF1C1E2A),
    spacerBackground = Color(0xFF0F131C),
    title = Color(0xFFDFE3EF),
    subreddit = Color(0xFF414465),
    metaInfo = Color(0xFFBFC2D5),
    pinnedLabel = Color(0xFF2ECC71),
    modLabel = Color(0xFF2ECC71),
    opLabel = Color(0xFFFF5252),
    visualModLabel = Color(0xFF2ECC71),
    postBorder = null
)

enum class FeedThemePreset(val palette: FeedColorPalette) {
    Wormi(wormiPalette),
    Narwhal(narwhalPalette),
    Reddit(redditPalette);

    val id: String get() = palette.themeId
    val displayName: String get() = palette.displayName

    companion object {
        fun fromId(themeId: String): FeedThemePreset = when (themeId.lowercase()) {
            Wormi.id -> Wormi
            Narwhal.id -> Narwhal
            Reddit.id -> Reddit
            else -> Wormi
        }

        val allPalettes: List<FeedColorPalette> by lazy {
            listOf(Wormi.palette, Narwhal.palette, Reddit.palette)
        }
    }
}
