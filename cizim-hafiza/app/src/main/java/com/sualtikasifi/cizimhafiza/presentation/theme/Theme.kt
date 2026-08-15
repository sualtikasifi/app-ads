package com.sualtikasifi.cizimhafiza.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val WarmLightColorScheme = lightColorScheme(
    primary = Orange,
    onPrimary = CardWhite,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OrangeDark,
    secondary = OrangeDark,
    onSecondary = CardWhite,
    background = CreamBackground,
    onBackground = TextDark,
    surface = CardWhite,
    onSurface = TextDark,
    surfaceVariant = CreamBackgroundVariant,
    onSurfaceVariant = TextMuted,
    outline = Outline,
    error = WrongRed,
    onError = CardWhite
)

private val WarmDarkColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = DarkText,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    error = WrongRed
)

// Rounded-everywhere shape scale to match the mockups' soft cards and pill buttons.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun CizimHafizaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // No dynamic color: the warm cream/orange brand palette is intentional
    // and shouldn't be overridden by the user's wallpaper-derived theme.
    val colorScheme = if (darkTheme) WarmDarkColorScheme else WarmLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
