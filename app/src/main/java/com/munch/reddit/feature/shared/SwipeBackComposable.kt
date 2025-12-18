package com.munch.reddit.feature.shared

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChangeConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    val velocityThresholdPx = remember(density) { with(density) { 300.dp.toPx() } }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var isExiting by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                containerWidth = it.width.toFloat()
                offsetX = offsetX.coerceIn(0f, containerWidth)
            }
            .pointerInput(containerWidth, swipeThreshold, isExiting) {
                awaitEachGesture {
                    if (isExiting || containerWidth <= 0f) return@awaitEachGesture

                    val down = awaitFirstDown(requireUnconsumed = false)

                    settleJob?.cancel()
                    settleJob = null

                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    val touchSlop = viewConfiguration.touchSlop
                    var dragStarted = false
                    var totalDx = 0f
                    var totalDy = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue

                        if (!change.pressed) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            break
                        }

                        val delta = change.position - change.previousPosition
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        if (!dragStarted && change.positionChangeConsumed()) return@awaitEachGesture

                        if (!dragStarted) {
                            totalDx += delta.x
                            totalDy += delta.y

                            val absDx = abs(totalDx)
                            val absDy = abs(totalDy)
                            if (absDx < touchSlop && absDy < touchSlop) continue

                            val isHorizontalDrag = absDx > absDy
                            val isRightSwipe = totalDx > 0f
                            if (!isHorizontalDrag || !isRightSwipe) return@awaitEachGesture

                            dragStarted = true
                            val initialDelta = (totalDx - touchSlop).coerceAtLeast(0f)
                            offsetX = (offsetX + initialDelta).coerceIn(0f, containerWidth)
                            change.consume()
                            continue
                        }

                        offsetX = (offsetX + delta.x).coerceIn(0f, containerWidth)
                        change.consume()
                    }

                    if (!dragStarted) return@awaitEachGesture

                    val velocityX = velocityTracker.calculateVelocity().x
                    val dismissThresholdPx = containerWidth * swipeThreshold
                    val shouldDismiss = velocityX > velocityThresholdPx || offsetX > dismissThresholdPx
                    val targetOffset = if (shouldDismiss) containerWidth else 0f

                    settleJob = scope.launch {
                        animate(
                            initialValue = offsetX,
                            targetValue = targetOffset,
                            animationSpec = exitAnimationSpec()
                        ) { value, _ ->
                            offsetX = value
                        }

                        if (targetOffset == containerWidth && !isExiting) {
                            isExiting = true
                            onSwipeBackFinished()
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .shadow(
                    elevation = if (offsetX > 0f) 16.dp else 0.dp,
                    clip = false
                )
        ) {
            content()
        }
    }
}

private fun exitAnimationSpec(): AnimationSpec<Float> =
    tween(durationMillis = 260, easing = FastOutSlowInEasing)
