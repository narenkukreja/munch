package com.munch.reddit.feature.shared

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.munch.reddit.feature.feed.PostBackgroundColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import com.munch.reddit.ui.theme.MunchForRedditTheme

/**
 * A Material 3 floating toolbar with multiple icon buttons.
 * Designed to match the SubredditSideSheet aesthetic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingToolbar(
    modifier: Modifier = Modifier,
    buttons: List<FloatingToolbarButton>
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = PostBackgroundColor.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            buttons.forEach { button ->
                FloatingToolbarIconButton(
                    onClick = button.onClick,
                    icon = button.icon,
                    contentDescription = button.contentDescription,
                    iconTint = button.iconTint,
                    iconSize = button.iconSize
                )
            }
        }
    }
}

/**
 * Individual icon button within the floating toolbar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingToolbarIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    iconTint: Color,
    iconSize: Dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "toolbar_button_scale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .scale(scale)
            .size(44.dp)
            .clip(CircleShape)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Data class representing a button in the floating toolbar
 */
data class FloatingToolbarButton(
    val icon: ImageVector,
    val contentDescription: String?,
    val onClick: () -> Unit,
    val iconTint: Color = Color.White,
    val iconSize: Dp = 20.dp
)

@Preview(showBackground = true)
@Composable
private fun FloatingToolbarPreview() {
    MunchForRedditTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.BottomCenter
        ) {
            FloatingToolbar(
                modifier = Modifier.fillMaxWidth(),
                buttons = listOf(
                    FloatingToolbarButton(
                        icon = Icons.Default.ArrowDownward,
                        contentDescription = "Next comment",
                        onClick = {},
                        iconTint = SubredditColor,
                        iconSize = 20.dp
                    ),
                    FloatingToolbarButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Share post",
                        onClick = {},
                        iconTint = SubredditColor,
                        iconSize = 20.dp
                    ),
                    FloatingToolbarButton(
                        icon = Icons.Default.Menu,
                        contentDescription = "Browse subreddits",
                        onClick = {},
                        iconTint = SubredditColor,
                        iconSize = 20.dp
                    )
                )
            )
        }
    }
}
