package com.munch.reddit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.compositeOver

private fun expressiveColorScheme(isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = ExpressivePrimary,
        onPrimary = ExpressiveOnPrimary,
        primaryContainer = ExpressivePrimary.copy(alpha = 0.24f).compositeOver(ExpressiveSurface),
        onPrimaryContainer = ExpressiveOnPrimary,
        secondary = ExpressiveSecondary,
        onSecondary = ExpressiveOnPrimary,
        secondaryContainer = ExpressiveSecondary.copy(alpha = 0.20f).compositeOver(ExpressiveSurface),
        onSecondaryContainer = ExpressiveOnPrimary,
        tertiary = ExpressivePositive,
        onTertiary = ExpressiveOnPrimary,
        tertiaryContainer = ExpressivePositive.copy(alpha = 0.18f).compositeOver(ExpressiveSurface),
        onTertiaryContainer = ExpressiveOnPrimary,
        background = ExpressiveBackground,
        onBackground = ExpressiveOnSurface,
        surface = ExpressiveSurface,
        onSurface = ExpressiveOnSurface,
        surfaceVariant = ExpressiveSurface,
        onSurfaceVariant = ExpressiveOnSurfaceVariant,
        surfaceTint = ExpressiveSurfaceTint,
        inverseSurface = ExpressiveOnSurface,
        inverseOnSurface = ExpressiveSurface,
        inversePrimary = ExpressivePrimary,
        outline = ExpressiveOutline,
        outlineVariant = ExpressiveOutlineVariant,
        error = ExpressiveNegative,
        onError = ExpressiveOnSurface,
        errorContainer = ExpressiveNegative.copy(alpha = 0.18f).compositeOver(ExpressiveSurface),
        onErrorContainer = ExpressiveOnSurface
    )
} else {
    lightColorScheme(
        primary = ExpressivePrimary,
        onPrimary = ExpressiveOnPrimary,
        primaryContainer = ExpressivePrimary.copy(alpha = 0.24f).compositeOver(ExpressiveSurface),
        onPrimaryContainer = ExpressiveOnPrimary,
        secondary = ExpressiveSecondary,
        onSecondary = ExpressiveOnPrimary,
        secondaryContainer = ExpressiveSecondary.copy(alpha = 0.20f).compositeOver(ExpressiveSurface),
        onSecondaryContainer = ExpressiveOnPrimary,
        tertiary = ExpressivePositive,
        onTertiary = ExpressiveOnPrimary,
        tertiaryContainer = ExpressivePositive.copy(alpha = 0.18f).compositeOver(ExpressiveSurface),
        onTertiaryContainer = ExpressiveOnPrimary,
        background = ExpressiveBackground,
        onBackground = ExpressiveOnSurface,
        surface = ExpressiveSurface,
        onSurface = ExpressiveOnSurface,
        surfaceVariant = ExpressiveSurface,
        onSurfaceVariant = ExpressiveOnSurfaceVariant,
        surfaceTint = ExpressiveSurfaceTint,
        inverseSurface = ExpressiveOnSurface,
        inverseOnSurface = ExpressiveSurface,
        inversePrimary = ExpressivePrimary,
        outline = ExpressiveOutline,
        outlineVariant = ExpressiveOutlineVariant,
        error = ExpressiveNegative,
        onError = ExpressiveOnSurface,
        errorContainer = ExpressiveNegative.copy(alpha = 0.18f).compositeOver(ExpressiveSurface),
        onErrorContainer = ExpressiveOnSurface
    )
}

private val ExpressiveSpacing = MunchSpacing(
    xs = 4.dp,
    sm = 8.dp,
    md = 16.dp,
    lg = 20.dp,
    xl = 28.dp,
    xxl = 40.dp
)

@Composable
fun MunchForRedditTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> expressiveColorScheme(darkTheme)
    }

    CompositionLocalProvider(LocalSpacing provides ExpressiveSpacing) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
