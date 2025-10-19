package com.munch.reddit.feature.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.munch.reddit.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import com.munch.reddit.feature.feed.PostBackgroundColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import com.munch.reddit.ui.theme.MunchForRedditTheme
import com.munch.reddit.ui.theme.MaterialSpacing
import kotlinx.coroutines.launch

@Composable
fun SubredditSideSheet(
    visible: Boolean,
    selectedSubreddit: String,
    subreddits: List<String>,
    onSelectSubreddit: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    subredditIcons: Map<String, String?> = emptyMap(),
    exploreSubreddits: List<String> = emptyList(),
    onSettingsClick: () -> Unit = {}
) {
    val sheetScrollState = rememberScrollState()
    val dragOffsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val dragThreshold = 100f

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismissRequest() },
                contentAlignment = Alignment.Center
            ) { }
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(180)),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(150))
        ) {
            val spacing = MaterialSpacing
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(
                        top = spacing.md,
                        bottom = spacing.md,
                        end = spacing.md
                    )
                    .offset(x = dragOffsetX.value.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                // Reset on drag start
                            },
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (dragOffsetX.value > dragThreshold) {
                                        // Threshold crossed - dismiss immediately and let AnimatedVisibility handle the exit
                                        onDismissRequest()
                                        // Reset offset after a short delay to avoid conflict with exit animation
                                        kotlinx.coroutines.delay(200)
                                        dragOffsetX.snapTo(0f)
                                    } else {
                                        // Snap back
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        ) { change, dragAmount ->
                            if (dragAmount > 0f) {
                                // Swipe right - dismiss gesture
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = (dragOffsetX.value + dragAmount).coerceAtLeast(0f)
                                    dragOffsetX.snapTo(newOffset)
                                }
                            }
                        }
                    }
            ) {
                val sheetColor = PostBackgroundColor.copy(alpha = 0.82f)
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(width),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = sheetColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = spacing.lg, vertical = spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(spacing.md)
                    ) {
                        // Search bar
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSearchClick()
                                    onDismissRequest()
                                },
                            shape = MaterialTheme.shapes.medium,
                            color = PostBackgroundColor.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search",
                                    tint = TitleColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Search",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TitleColor.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Subreddit list with sections
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(sheetScrollState),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            // Main section
                            Text(
                                text = "Main",
                                style = MaterialTheme.typography.titleLarge,
                                color = TitleColor.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs)
                            )

                            // r/all item
                            SubredditItem(
                                subreddit = "all",
                                selectedSubreddit = selectedSubreddit,
                                subredditIcons = subredditIcons,
                                onSelectSubreddit = onSelectSubreddit,
                                onDismissRequest = onDismissRequest,
                                spacing = spacing
                            )

                            // Settings item
                            SettingsItem(
                                onSettingsClick = {
                                    onSettingsClick()
                                    onDismissRequest()
                                },
                                spacing = spacing
                            )

                            // Favorites section - filter out "all" from subreddits
                            val filteredSubreddits = subreddits.filter {
                                !it.equals("all", ignoreCase = true)
                            }

                            if (filteredSubreddits.isNotEmpty()) {
                                Text(
                                    text = "Favorites",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TitleColor.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(horizontal = spacing.sm, vertical = spacing.xs)
                                        .padding(top = spacing.sm)
                                )

                                filteredSubreddits.forEach { subreddit ->
                                    SubredditItem(
                                        subreddit = subreddit,
                                        selectedSubreddit = selectedSubreddit,
                                        subredditIcons = subredditIcons,
                                        onSelectSubreddit = onSelectSubreddit,
                                        onDismissRequest = onDismissRequest,
                                        spacing = spacing
                                    )
                                }
                            }

                            // Filter out duplicates from explore list
                            val filteredExplore = exploreSubreddits.filter { explore ->
                                !subreddits.any { it.equals(explore, ignoreCase = true) }
                            }

                            // Explore section
                            if (filteredExplore.isNotEmpty()) {
                                Text(
                                    text = "Explore",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TitleColor.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(
                                        horizontal = spacing.sm,
                                        vertical = spacing.xs
                                    ).padding(top = spacing.sm)
                                )

                                filteredExplore.forEach { subreddit ->
                                    SubredditItem(
                                        subreddit = subreddit,
                                        selectedSubreddit = selectedSubreddit,
                                        subredditIcons = subredditIcons,
                                        onSelectSubreddit = onSelectSubreddit,
                                        onDismissRequest = onDismissRequest,
                                        spacing = spacing
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubredditItem(
    subreddit: String,
    selectedSubreddit: String,
    subredditIcons: Map<String, String?>,
    onSelectSubreddit: (String) -> Unit,
    onDismissRequest: () -> Unit,
    spacing: com.munch.reddit.ui.theme.MunchSpacing
) {
    val isSelected = subreddit.equals(selectedSubreddit, ignoreCase = true)
    val normalized = subreddit.removePrefix("r/").removePrefix("R/").trim()
    val lookupKey = normalized.lowercase()
    val display = if (lookupKey == "all") "all" else normalized
    val iconUrl = subredditIcons[lookupKey]
    val textColor = if (isSelected) SubredditColor else TitleColor
    val isAll = lookupKey == "all"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable {
                onSelectSubreddit(subreddit)
                onDismissRequest()
            }
            .padding(
                horizontal = spacing.sm,
                vertical = spacing.xs
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        val avatarModifier = Modifier
            .size(28.dp)
            .clip(CircleShape)

        if (isAll) {
            // Show ic_stack for "all" subreddit
            Box(
                modifier = avatarModifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_stack),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = avatarModifier,
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = avatarModifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_no_image),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = display,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingsItem(
    onSettingsClick: () -> Unit,
    spacing: com.munch.reddit.ui.theme.MunchSpacing
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onSettingsClick() }
            .padding(
                horizontal = spacing.sm,
                vertical = spacing.xs
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "settings",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = "settings",
            style = MaterialTheme.typography.titleMedium,
            color = TitleColor,
            fontWeight = FontWeight.Normal
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SubredditSideSheetPreview() {
    MunchForRedditTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            SubredditSideSheet(
                visible = true,
                selectedSubreddit = "r/androiddev",
                subreddits = listOf("r/androiddev", "r/gaming", "r/movies", "r/all"),
                onSelectSubreddit = {},
                onDismissRequest = {},
                onSearchClick = {},
                subredditIcons = emptyMap(),
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}
