package com.munch.reddit.feature.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Custom swipe-back wrapper that enables edge-to-edge swipe gesture
 * to navigate back with a reveal animation showing the previous activity.
 *
 * This wrapper ONLY handles the swipe gesture and translates the content.
 * The content itself should have its own background.
 */
@Composable
fun SwipeBackWrapper(
    onSwipeBackFinished: () -> Unit,
    modifier: Modifier = Modifier,
    swipeThreshold: Float = 0.4f, // Fraction of screen width to trigger back
    edgeWidth: Float = 50f, // Width in dp for edge detection
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { edgeWidth.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var isSwipeActive by remember { mutableStateOf(false) }
    var containerWidth by remember { mutableFloatStateOf(0f) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        label = "swipeOffset"
    )

    // Root container that does not move; keeps edge overlay anchored to screen
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerWidth = it.width.toFloat() }
    ) {
        // Moving content with swipe offset and shadow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .shadow(
                    elevation = if (animatedOffsetX > 0) 16.dp else 0.dp,
                    clip = false
                )
        ) {
            content()
        }

        // Edge overlay to capture swipe gestures before children consume them
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(with(density) { edgeWidthPx.toDp() })
                .zIndex(1f)
                .pointerInput(edgeWidthPx, swipeThreshold) {
                    detectHorizontalDragGestures(
                        onDragStart = { _ ->
                            isSwipeActive = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!isSwipeActive || dragAmount <= 0f) return@detectHorizontalDragGestures
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            val threshold = (containerWidth.takeIf { it > 0f } ?: 1f) * swipeThreshold
                            if (offsetX > threshold) {
                                onSwipeBackFinished()
                            } else {
                                offsetX = 0f
                            }
                            isSwipeActive = false
                        },
                        onDragCancel = {
                            offsetX = 0f
                            isSwipeActive = false
                        }
                    )
                }
        )
    }
}
