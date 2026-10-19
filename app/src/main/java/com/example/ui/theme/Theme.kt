package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ObsidianTelemetryColorScheme = darkColorScheme(
    primary = RadarCyan,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004D59),
    onPrimaryContainer = Color(0xFF80F2FF),
    secondary = RadarEmerald,
    onSecondary = Color(0xFF00391A),
    secondaryContainer = Color(0xFF005228),
    onSecondaryContainer = Color(0xFF70FF9F),
    tertiary = WarningAmber,
    onTertiary = Color(0xFF452B00),
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorderGlow,
    error = DangerCoral,
    onError = Color.White
)

@Composable
fun CallPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ObsidianTelemetryColorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CallPulseTheme(content = content)
}
