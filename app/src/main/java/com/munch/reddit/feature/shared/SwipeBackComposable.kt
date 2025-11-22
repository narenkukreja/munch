package com.munch.reddit.feature.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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

    val offsetX = remember { Animatable(0f) }
    var dragEligible by remember { mutableStateOf(false) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var isExiting by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .pointerInput(edgeWidthPx, swipeThreshold, containerWidth) {
                coroutineScope {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (isExiting) return@detectHorizontalDragGestures
                            dragEligible = offset.x <= edgeWidthPx
                            if (dragEligible) {
                                launch { offsetX.stop() }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!dragEligible || dragAmount <= 0f || isExiting) return@detectHorizontalDragGestures
                            change.consume()
                            val maxWidth = containerWidth.takeIf { it > 0f } ?: Float.MAX_VALUE
                            launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(0f, maxWidth))
                            }
                        },
                        onDragEnd = {
                            val threshold = (containerWidth.takeIf { it > 0f } ?: 1f) * swipeThreshold
                            val shouldNavigateBack = dragEligible && offsetX.value > threshold
                            dragEligible = false

                            if (shouldNavigateBack) {
                                isExiting = true
                                launch {
                                    try {
                                        val targetWidth = containerWidth.takeIf { it > 0f } ?: threshold / swipeThreshold
                                        // Finish the motion to the screen edge before triggering the callback
                                        offsetX.animateTo(
                                            targetValue = targetWidth,
                                            animationSpec = tween(
                                                durationMillis = 220,
                                                easing = FastOutLinearInEasing
                                            )
                                        )
                                        onSwipeBackFinished()
                                    } finally {
                                        isExiting = false
                                    }
                                }
                            } else {
                                launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = 200,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            dragEligible = false
                            launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            }
                        }
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .shadow(
                    elevation = if (offsetX.value > 0) 16.dp else 0.dp,
                    clip = false
                )
        ) {
            content()
        }
    }
}
