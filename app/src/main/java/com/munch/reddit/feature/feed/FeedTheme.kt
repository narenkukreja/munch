package com.munch.reddit.feature.feed

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.munch.reddit.theme.FeedColorPalette
import com.munch.reddit.theme.FeedThemePreset

private val LocalFeedColorPalette = staticCompositionLocalOf { FeedThemePreset.Wormi.palette }

@Composable
fun FeedTheme(
    preset: FeedThemePreset,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalFeedColorPalette provides preset.palette) {
        content()
    }
}

@Composable
fun FeedTheme(
    palette: FeedColorPalette,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalFeedColorPalette provides palette) {
        content()
    }
}

val PostBackgroundColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedColorPalette.current.postBackground

val SpacerBackgroundColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedColorPalette.current.spacerBackground

val TitleColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedColorPalette.current.title

val SubredditColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedColorPalette.current.subreddit

val MetaInfoColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedColorPalette.current.metaInfo

val PinnedLabelColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedColorPalette.current.pinnedLabel

val ModLabelColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedColorPalette.current.modLabel

val OpLabelColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedColorPalette.current.opLabel

val VisualModColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedColorPalette.current.visualModLabel

val PostBorderColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedColorPalette.current.postBorder ?: MaterialTheme.colorScheme.outlineVariant
