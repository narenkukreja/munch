package com.munch.reddit.feature.shared


import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Invisible swipe target on the right edge that reveals the subreddit side sheet.
 */
@Composable
fun SideSheetEdgeSwipeTarget(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    edgeWidth: Dp = 28.dp,
    triggerDistance: Dp = 32.dp
) {
    if (!enabled) return

    val triggerPx = with(LocalDensity.current) { triggerDistance.toPx() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(edgeWidth)
            .pointerInput(enabled, triggerPx) {
                var totalDrag = 0f
                var triggered = false

                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                        triggered = false
                    },
                    onDragCancel = {
                        totalDrag = 0f
                        triggered = false
                    },
                    onDragEnd = {
                        totalDrag = 0f
                        triggered = false
                    }
                ) { change, dragAmount ->
                    if (dragAmount < 0f) {
                        totalDrag += -dragAmount
                        if (!triggered && totalDrag >= triggerPx) {
                            triggered = true
                            change.consume()
                            onOpen()
                        }
                    }
                }
            }
    )
}
