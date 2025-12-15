package com.munch.reddit.feature.feed.recycler

import android.content.Context
import android.graphics.Color
import android.util.TypedValue

fun Context.dpToPx(dp: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

fun withAlpha(color: Int, alpha: Float): Int {
    val clamped = alpha.coerceIn(0f, 1f)
    val original = Color.alpha(color)
    val newAlpha = (original * clamped).toInt().coerceIn(0, 255)
    return (color and 0x00FFFFFF) or (newAlpha shl 24)
}

