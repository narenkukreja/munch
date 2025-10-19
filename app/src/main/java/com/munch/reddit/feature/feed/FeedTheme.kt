package com.munch.reddit.feature.feed

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

val PostBackgroundColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface

val SpacerBackgroundColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

val TitleColor: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(0xFFE6E8F4)

val SubredditColor: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(0xFFA7B6DD)

val MetaInfoColor: Color
    @Composable
    @ReadOnlyComposable
    get() = Color(0xFFBFC2D5)

val PinnedLabelColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary

val ModLabelColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary

val OpLabelColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.error

val VisualModColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary
