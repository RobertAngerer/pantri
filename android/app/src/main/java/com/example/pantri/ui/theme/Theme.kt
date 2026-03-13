package com.example.pantri.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PantriDark = darkColorScheme(
    primary = Mint,
    onPrimary = Surface0,
    secondary = SkyBlue,
    tertiary = Peach,
    background = Surface0,
    surface = Surface1,
    surfaceVariant = Surface2,
    primaryContainer = Surface2,
    onBackground = Color(0xFFE8E8F0),
    onSurface = Color(0xFFE8E8F0),
    onSurfaceVariant = Color(0xFFB8B8CC),
    outline = Surface3,
)

@Composable
fun PantriTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PantriDark,
        typography = Typography,
        content = content
    )
}
