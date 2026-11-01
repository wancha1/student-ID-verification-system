package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Soft ambient backdrop for the role dashboards. Paired with translucent Material
 * surfaces to create a frosted-glass visual language without sacrificing legibility.
 */
fun Modifier.roleGlassBackground(accent: Color): Modifier = background(
    Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF8FAFF),
            accent.copy(alpha = 0.055f),
            Color(0xFFEAF0F8)
        )
    )
)
