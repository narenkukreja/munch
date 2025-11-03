package com.munch.reddit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

// Core surfaces and backgrounds (kept identical to the legacy dark palette).
val ExpressiveBackground = Color(0xFF181A26)
val ExpressiveSurface = Color(0xFF232531)

// Brand and accent colors (preserve existing UI accent values).
val ExpressivePrimary = Color(0xFF3C638C)
val ExpressiveSecondary = Color(0xFF625B71)
val ExpressiveTertiary = Color(0xFF7D5260)

// Content colors.
val ExpressiveOnPrimary = Color(0xFFFFFFFF)
val ExpressiveOnSurface = Color(0xFFFFFFFF)
val ExpressiveOnSurfaceVariant = Color(0xFF7A7A7A)

// Supporting accents.
val ExpressivePositive = Color(0xFF2ECC71)
val ExpressiveNegative = Color(0xFFFF5252)

// Utility colors derived from existing palette.
val ExpressiveOutline = ExpressiveOnSurface.copy(alpha = 0.12f).compositeOver(ExpressiveSurface)
val ExpressiveOutlineVariant = ExpressiveOnSurface.copy(alpha = 0.08f).compositeOver(ExpressiveSurface)
val ExpressiveSurfaceTint = ExpressivePrimary
