package com.munch.reddit.feature.shared

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeableState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import kotlin.math.roundToInt

/**
 * Custom swipe-back wrapper that enables edge-to-edge swipe gesture
 * to navigate back with a reveal animation showing the previous activity.
 *
 * This wrapper ONLY handles the swipe gesture and translates the content.
 * The content itself should have its own background.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SwipeBackWrapper(
    onSwipeBackFinished: () -> Unit,
    modifier: Modifier = Modifier,
    swipeThreshold: Float = 0.4f, // Fraction of screen width to trigger back
    edgeWidth: Float = 50f, // Width in dp for edge detection
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var isExiting by remember { mutableStateOf(false) }

    val swipeState: SwipeableState<SwipeBackValue> = rememberSwipeableState(
        initialValue = SwipeBackValue.Closed,
        animationSpec = exitAnimationSpec()
    )

    val anchors = remember(containerWidth) {
        if (containerWidth > 0f) {
            mapOf(
                0f to SwipeBackValue.Closed,
                containerWidth to SwipeBackValue.Dismissed
            )
        } else {
            emptyMap()
        }
    }

    // Finish when the swipeable settles at the dismissed anchor.
    LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue == SwipeBackValue.Dismissed && !isExiting) {
            isExiting = true
            onSwipeBackFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .let { base ->
                if (anchors.isEmpty()) {
                    base
                } else {
                    base.swipeable(
                        state = swipeState,
                        anchors = anchors,
                        thresholds = { _, _ -> FractionalThreshold(swipeThreshold) },
                        orientation = Orientation.Horizontal,
                        enabled = !isExiting,
                        resistance = null,
                        velocityThreshold = 300.dp
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(swipeState.offset.value.roundToInt(), 0) }
                .shadow(
                    elevation = if (swipeState.offset.value > 0) 16.dp else 0.dp,
                    clip = false
                )
        ) {
            content()
        }
    }
}

private enum class SwipeBackValue { Closed, Dismissed }

private fun exitAnimationSpec(): AnimationSpec<Float> =
    tween(durationMillis = 260, easing = FastOutSlowInEasing)
