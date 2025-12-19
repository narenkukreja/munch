package com.munch.reddit.feature.shared

import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.munch.reddit.R
import com.munch.reddit.feature.feed.PostBackgroundColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import com.munch.reddit.feature.shared.sidesheet.SubredditSideSheetAdapter
import com.munch.reddit.feature.shared.sidesheet.SubredditSideSheetColors
import com.munch.reddit.feature.shared.sidesheet.buildSubredditSideSheetRows
import com.munch.reddit.ui.theme.MaterialSpacing
import com.munch.reddit.ui.theme.MunchForRedditTheme
import kotlinx.coroutines.delay
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
    onSettingsClick: () -> Unit = {},
    onEditFavoritesClick: () -> Unit = {},
    initialScrollOffset: Int = 0,
    onScrollOffsetChange: (Int) -> Unit = {}
) {
    val dragOffsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val dragThreshold = 100f
    val spacing = MaterialSpacing

    val rows = remember(selectedSubreddit, subreddits, subredditIcons, exploreSubreddits) {
        buildSubredditSideSheetRows(
            selectedSubreddit = selectedSubreddit,
            favorites = subreddits,
            subredditIcons = subredditIcons,
            exploreSubreddits = exploreSubreddits
        )
    }

    val onSelectSubredditState = rememberUpdatedState(onSelectSubreddit)
    val onDismissRequestState = rememberUpdatedState(onDismissRequest)
    val onSearchClickState = rememberUpdatedState(onSearchClick)
    val onSettingsClickState = rememberUpdatedState(onSettingsClick)
    val onEditFavoritesClickState = rememberUpdatedState(onEditFavoritesClick)
    val onScrollOffsetChangeState = rememberUpdatedState(onScrollOffsetChange)

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
                    ) { onDismissRequestState.value() }
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(150)),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(150))
        ) {
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
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (dragOffsetX.value > dragThreshold) {
                                        onDismissRequestState.value()
                                        delay(200)
                                        dragOffsetX.snapTo(0f)
                                    } else {
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
                val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)

                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(width),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = sheetColor,
                    border = BorderStroke(1.dp, borderColor),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp
                ) {
                    val titleColor = TitleColor.toArgb()
                    val subredditColor = SubredditColor.toArgb()
                    val searchContentColor = TitleColor.copy(alpha = 0.6f).toArgb()
                    val searchBackgroundColor = PostBackgroundColor.copy(alpha = 0.6f).toArgb()
                    val borderColorArgb = borderColor.toArgb()

                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            val root = LayoutInflater.from(context)
                                .inflate(R.layout.view_subreddit_side_sheet, null, false)
                            val recyclerView = root.findViewById<RecyclerView>(R.id.side_sheet_recycler)
                            recyclerView.layoutManager = LinearLayoutManager(context)
                            recyclerView.adapter = SubredditSideSheetAdapter()
                            recyclerView.itemAnimator = null

                            val spacingPx = root.resources.getDimensionPixelSize(R.dimen.spacing_sm)
                            recyclerView.addItemDecoration(SideSheetSpacingItemDecoration(spacingPx))

                            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                                    onScrollOffsetChangeState.value(recyclerView.computeVerticalScrollOffset())
                                }
                            })

                            root
                        },
                        update = { root ->
                            val search = root.findViewById<LinearLayout>(R.id.side_sheet_search)
                            val searchIcon = root.findViewById<ImageView>(R.id.side_sheet_search_icon)
                            val searchText = root.findViewById<TextView>(R.id.side_sheet_search_text)
                            val recyclerView = root.findViewById<RecyclerView>(R.id.side_sheet_recycler)
                            val adapter = recyclerView.adapter as? SubredditSideSheetAdapter ?: return@AndroidView

                            search.setOnClickListener {
                                onSearchClickState.value()
                                onDismissRequestState.value()
                            }

                            val searchBackground = roundedRectBackground(
                                view = search,
                                fillColor = searchBackgroundColor,
                                strokeColor = borderColorArgb
                            )
                            search.background = searchBackground
                            ImageViewCompat.setImageTintList(searchIcon, ColorStateList.valueOf(searchContentColor))
                            searchText.setTextColor(searchContentColor)

                            val currentColors = SubredditSideSheetColors(
                                title = titleColor,
                                subreddit = subredditColor
                            )
                            if (adapter.colors != currentColors) {
                                adapter.colors = currentColors
                            }

                            adapter.onSubredditClick = { subreddit ->
                                onSelectSubredditState.value(subreddit)
                                onDismissRequestState.value()
                            }
                            adapter.onSettingsClick = {
                                onSettingsClickState.value()
                                onDismissRequestState.value()
                            }
                            adapter.onEditFavoritesClick = {
                                onEditFavoritesClickState.value()
                                onDismissRequestState.value()
                            }
                            adapter.submitList(rows)

                            val normalizedScroll = initialScrollOffset.coerceAtLeast(0)
                            val applied = root.getTag(R.id.tag_side_sheet_applied_scroll_offset) as? Int
                            if (applied != normalizedScroll) {
                                root.setTag(R.id.tag_side_sheet_applied_scroll_offset, normalizedScroll)
                                recyclerView.post {
                                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return@post
                                    lm.scrollToPositionWithOffset(0, -normalizedScroll)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun roundedRectBackground(view: View, fillColor: Int, strokeColor: Int): GradientDrawable {
    val density = view.resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 16f * density
        setColor(fillColor)
        setStroke((1f * density).toInt().coerceAtLeast(1), strokeColor)
    }
}

private class SideSheetSpacingItemDecoration(
    private val verticalSpacingPx: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val lastPosition = (parent.adapter?.itemCount ?: 0) - 1
        if (position < lastPosition) {
            outRect.bottom = verticalSpacingPx
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SubredditSideSheetPreview() {
    MunchForRedditTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            SubredditSideSheet(
                visible = true,
                selectedSubreddit = "androiddev",
                subreddits = listOf("androiddev", "gaming", "movies", "all"),
                onSelectSubreddit = {},
                onDismissRequest = {},
                onSearchClick = {},
                subredditIcons = emptyMap(),
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

