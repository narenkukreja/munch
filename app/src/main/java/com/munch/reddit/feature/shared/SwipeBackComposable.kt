package com.munch.reddit.feature.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
    var isSwipeStarted by remember { mutableFloatStateOf(0f) }
    var screenWidth by remember { mutableFloatStateOf(0f) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        label = "swipeOffset"
    )

    // Content with swipe offset and shadow
    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .shadow(
                elevation = if (animatedOffsetX > 0) 16.dp else 0.dp,
                clip = false
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        screenWidth = size.width.toFloat()
                        // Only allow swipe from left edge
                        if (offset.x <= edgeWidthPx) {
                            isSwipeStarted = 1f
                        }
                    },
                    onDragEnd = {
                        if (isSwipeStarted > 0f) {
                            // Check if we've crossed the threshold
                            if (offsetX > screenWidth * swipeThreshold) {
                                onSwipeBackFinished()
                            } else {
                                // Animate back to original position
                                offsetX = 0f
                            }
                        }
                        isSwipeStarted = 0f
                    },
                    onDragCancel = {
                        offsetX = 0f
                        isSwipeStarted = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isSwipeStarted > 0f && dragAmount > 0) {
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceAtLeast(0f)
                        }
                    }
                )
            }
    ) {
        content()
    }
}
