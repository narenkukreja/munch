package com.munch.reddit.feature.shared

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.munch.reddit.feature.feed.SubredditColor

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        color = SubredditColor,
        trackColor = SubredditColor.copy(alpha = 0.25f),
        strokeWidth = 3.dp,
        modifier = modifier
    )
}

