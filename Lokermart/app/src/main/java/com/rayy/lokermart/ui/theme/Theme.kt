package com.rayy.lokermart.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Palet: teal (tenang & tepercaya) + amber sebagai aksen untuk hal penting seperti kode loker.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F4F2),
    onPrimaryContainer = Color(0xFF064E48),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF3B2A00),
    secondaryContainer = Color(0xFFFFF4DB),
    onSecondaryContainer = Color(0xFF5C4100),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF0F3),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFDC2626),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FD1C5),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF0B3B37),
    onPrimaryContainer = Color(0xFFB7F0EA),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF3B2A00),
    secondaryContainer = Color(0xFF4A3600),
    onSecondaryContainer = Color(0xFFFFE9B0),
    background = Color(0xFF0E1213),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF171C1D),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF232A2B),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF3B4446),
    outlineVariant = Color(0xFF2A3234),
    error = Color(0xFFF87171),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun LokermartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Warna dinamis dimatikan agar identitas visual konsisten di semua perangkat
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
