package com.regman.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFF22C55E),
    surface = Color(0xFF0B0F14),
    background = Color(0xFF0B0F14),
    surfaceVariant = Color(0xFF151B23),
    onSurface = Color(0xFFE6E9EF),
)

@Composable
fun RegManTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Scheme, content = content)
