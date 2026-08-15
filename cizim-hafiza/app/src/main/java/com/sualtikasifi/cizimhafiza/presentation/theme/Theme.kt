package com.sualtikasifi.cizimhafiza.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
    content: @Composable () -> Unit
) {
    // The brand is a single deliberately-designed warm cream/orange look
    // (per product mockups), not a light/dark pair — always use it,
    // regardless of the system theme or wallpaper-derived dynamic color.
    // A prior light/dark toggle here left several colors (primaryContainer,
    // canvas stroke color, card content color) undefined for dark mode,
    // so they fell back to Material3's stock purple defaults or the wrong
    // ambient text color — exactly the illegible-text/purple-card bugs
    // reported after testing with system dark mode on.
    MaterialTheme(
        colorScheme = WarmLightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
