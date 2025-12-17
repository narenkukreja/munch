package com.munch.reddit.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

fun TextStyle.withCommentTextSize(sizeSp: Float): TextStyle {
    if (sizeSp <= 0f) return this
    val targetSize: TextUnit = sizeSp.sp
    val newLineHeight = when {
        lineHeight.isUnspecified || fontSize.isUnspecified || fontSize.value == 0f -> lineHeight
        else -> (sizeSp * (lineHeight.value / fontSize.value)).sp
    }
    return copy(
        fontSize = targetSize,
        lineHeight = newLineHeight
    )
}
