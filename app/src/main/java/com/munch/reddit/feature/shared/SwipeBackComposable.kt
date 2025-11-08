package com.munch.reddit.feature.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
    var dragEligible by remember { mutableStateOf(false) }
    var containerWidth by remember { mutableFloatStateOf(0f) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        label = "swipeOffset"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .pointerInput(edgeWidthPx, swipeThreshold) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragEligible = offset.x <= edgeWidthPx
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!dragEligible || dragAmount <= 0f) return@detectHorizontalDragGestures
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (!dragEligible) {
                            offsetX = 0f
                            return@detectHorizontalDragGestures
                        }
                        val threshold = (containerWidth.takeIf { it > 0f } ?: 1f) * swipeThreshold
                        val shouldNavigateBack = offsetX > threshold
                        dragEligible = false
                        if (shouldNavigateBack) {
                            onSwipeBackFinished()
                        } else {
                            offsetX = 0f
                        }
                    },
                    onDragCancel = {
                        dragEligible = false
                        offsetX = 0f
                    }
                )
            }
    ) {
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
    }
}
