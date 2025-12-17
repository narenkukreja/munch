package com.munch.reddit.feature.shared

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import com.munch.reddit.feature.feed.PostBackgroundColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import com.munch.reddit.ui.theme.MaterialSpacing

@Composable
fun EditFavoritesScreen(
    favorites: List<String>,
    onBack: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val spacing = MaterialSpacing
    val localFavorites = remember(favorites) {
        mutableStateListOf<String>().apply {
            addAll(favorites.map { sanitizeFavoriteName(it) })
        }
    }
    var isEditing by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingFavorite by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    val itemDragThresholdPx = with(LocalDensity.current) { 36.dp.toPx() }
    val systemInsets = WindowInsets.statusBars.asPaddingValues()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PostBackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.lg, vertical = spacing.md)
                .padding(top = systemInsets.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TitleColor
                    )
                }
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.titleLarge,
                    color = TitleColor,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add favorite",
                            tint = TitleColor
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isEditing) {
                                onSave(localFavorites.toList())
                            } else {
                                isEditing = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = if (isEditing) "Save favorites" else "Edit favorites",
                            tint = SubredditColor
                        )
                    }
                }
            }

            if (localFavorites.isEmpty()) {
                Text(
                    text = "No favorites yet. Add one to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TitleColor.copy(alpha = 0.8f)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    itemsIndexed(localFavorites, key = { _, item -> item }) { _, item ->
                        FavoriteRow(
                            name = item,
                            isEditing = isEditing,
                            onRemove = { localFavorites.remove(item) },
                            onMove = { direction ->
                                val fromIndex = localFavorites.indexOf(item)
                                if (fromIndex == -1 || localFavorites.isEmpty()) return@FavoriteRow
                                val target = (fromIndex + direction).coerceIn(0, localFavorites.lastIndex)
                                if (target != fromIndex) {
                                    localFavorites.removeAt(fromIndex)
                                    localFavorites.add(target, item)
                                }
                            },
                            onTapReorder = {
                                val fromIndex = localFavorites.indexOf(item)
                                if (fromIndex == -1 || localFavorites.isEmpty()) return@FavoriteRow
                                val target = when {
                                    fromIndex > 0 -> fromIndex - 1
                                    fromIndex < localFavorites.lastIndex -> fromIndex + 1
                                    else -> fromIndex
                                }
                                if (target != fromIndex) {
                                    localFavorites.removeAt(fromIndex)
                                    localFavorites.add(target, item)
                                }
                            },
                            dragThreshold = itemDragThresholdPx
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val normalized = sanitizeFavoriteName(pendingFavorite)
                        when {
                            normalized.isBlank() -> addError = "Enter a subreddit"
                            normalized.equals("all", ignoreCase = true) -> addError = "Can't add r/all"
                            localFavorites.any { it.equals(normalized, ignoreCase = true) } -> addError = "Already added"
                            else -> {
                                localFavorites.add(normalized)
                                pendingFavorite = ""
                                addError = null
                                showAddDialog = false
                                isEditing = true
                            }
                        }
                    }
                ) {
                    Text(text = "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(text = "Cancel", color = SubredditColor)
                }
            },
            title = {
                Text(
                    text = "Add favorite",
                    color = TitleColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    TextField(
                        value = pendingFavorite,
                        onValueChange = {
                            pendingFavorite = it
                            addError = null
                        },
                        placeholder = { Text(text = "subreddit name", color = TitleColor.copy(alpha = 0.6f)) },
                        singleLine = true
                    )
                    addError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun FavoriteRow(
    name: String,
    isEditing: Boolean,
    onRemove: () -> Unit,
    onMove: (direction: Int) -> Unit,
    onTapReorder: () -> Unit,
    dragThreshold: Float
) {
    val spacing = MaterialSpacing
    var accumulatedDrag by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val animatedElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 1.dp,
        label = "favorite_elevation"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        label = "favorite_scale"
    )
    val dragOffset by animateDpAsState(
        targetValue = if (isDragging) (-6).dp else 0.dp,
        label = "favorite_offset"
    )
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.95f else 0.8f,
        label = "favorite_alpha"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = dragOffset)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        shape = RoundedCornerShape(12.dp),
        color = PostBackgroundColor.copy(alpha = backgroundAlpha),
        border = if (isDragging) BorderStroke(1.dp, SubredditColor.copy(alpha = 0.6f)) else null,
        tonalElevation = animatedElevation,
        shadowElevation = animatedElevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            if (isEditing) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.RemoveCircle,
                        contentDescription = "Remove favorite",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = "r/${name.removePrefix("r/").removePrefix("R/")}",
                style = MaterialTheme.typography.titleMedium,
                color = TitleColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            if (isEditing) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Reorder",
                    tint = TitleColor.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            enabled = isEditing,
                            onClick = onTapReorder
                        )
                        .pointerInput(isEditing, name) {
                            if (!isEditing) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    isDragging = true
                                    accumulatedDrag = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDrag += dragAmount.y
                                    if (accumulatedDrag <= -dragThreshold) {
                                        onMove(-1)
                                        accumulatedDrag = 0f
                                    } else if (accumulatedDrag >= dragThreshold) {
                                        onMove(1)
                                        accumulatedDrag = 0f
                                    }
                                },
                                onDragEnd = {
                                    accumulatedDrag = 0f
                                    isDragging = false
                                },
                                onDragCancel = {
                                    accumulatedDrag = 0f
                                    isDragging = false
                                }
                            )
                        }
                )
            }
        }
    }
}

private fun sanitizeFavoriteName(name: String): String =
    name.removePrefix("r/").removePrefix("R/").trim().lowercase()
