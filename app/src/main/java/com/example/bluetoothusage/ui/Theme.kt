package com.example.bluetoothusage.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF006CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFFFF7A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1C2),
    tertiary = Color(0xFFE91E63),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7E5),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF8FAFF),
    onBackground = Color(0xFF121826),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121826),
    surfaceVariant = Color(0xFFE7EDFF),
    onSurfaceVariant = Color(0xFF4A5878),
    outline = Color(0xFF6D7DA8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DB5FF),
    onPrimary = Color(0xFF002B67),
    primaryContainer = Color(0xFF004BAF),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Color(0xFFFFB36B),
    onSecondary = Color(0xFF4A2600),
    secondaryContainer = Color(0xFF8A4300),
    tertiary = Color(0xFFFF8DB5),
    onTertiary = Color(0xFF5B0028),
    tertiaryContainer = Color(0xFF9B174B),
    background = Color(0xFF0E1320),
    onBackground = Color(0xFFE7ECFF),
    surface = Color(0xFF171D2B),
    onSurface = Color(0xFFE7ECFF),
    surfaceVariant = Color(0xFF303A55),
    onSurfaceVariant = Color(0xFFC5D0F6),
    outline = Color(0xFF8D9BC7)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BluetoothUsageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
