package com.munch.reddit.theme

import kotlin.math.roundToInt

object TextSizeDefaults {
    const val MinSizeSp = 12f
    const val MaxSizeSp = 32f
    const val StepSizeSp = 2f
    const val DefaultSizeSp = 18f
    private const val LegacyBaseSizeSp = 16f

    val sliderSteps: Int =
        ((MaxSizeSp - MinSizeSp) / StepSizeSp).toInt().coerceAtLeast(1) - 1

    fun clamp(sizeSp: Float): Float {
        return roundToStep(sizeSp).coerceIn(MinSizeSp, MaxSizeSp)
    }

    fun roundToStep(sizeSp: Float): Float {
        val steps = ((sizeSp - MinSizeSp) / StepSizeSp).roundToInt()
        return MinSizeSp + steps * StepSizeSp
    }

    fun fromLegacyScale(scale: Float): Float {
        return clamp(scale * LegacyBaseSizeSp)
    }
}
