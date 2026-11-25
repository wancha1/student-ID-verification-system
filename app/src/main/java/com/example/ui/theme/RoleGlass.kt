package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Clean, calm neutral canvas background that respects dark and light theme
 * so cards, badges, and bottom action buttons stand out cleanly.
 */
@Composable
fun Modifier.roleGlassBackground(accent: Color): Modifier =
    background(MaterialTheme.colorScheme.background)
