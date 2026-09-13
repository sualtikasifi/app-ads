package com.sualtikasifi.cizimhafiza.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val WarmColorScheme = lightColorScheme(
    primary = Orange,
    onPrimary = CardWhite,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OrangeInk,

    secondary = Teal,
    onSecondary = CardWhite,
    secondaryContainer = TealContainer,
    onSecondaryContainer = TealInk,

    tertiary = GoldAccent,
    onTertiary = CardWhite,
    tertiaryContainer = OrangeContainer,
    onTertiaryContainer = OrangeInk,

    background = CreamBackground,
    onBackground = TextDark,
    surface = CardWhite,
    onSurface = TextDark,
    surfaceVariant = CreamBackgroundVariant,
    onSurfaceVariant = TextMuted,
    surfaceTint = Orange,
    inverseSurface = TextDark,
    inverseOnSurface = CreamBackground,

    outline = Outline,
    outlineVariant = PaperEdge,
    scrim = ShadowWarm,

    error = WrongRed,
    onError = CardWhite,
    errorContainer = WrongContainer,
    onErrorContainer = Color(0xFF8E2216)
)

/**
 * Corner scale. Small controls stay only gently rounded so they read as
 * buttons; anything card-sized gets a generous radius, which is where the
 * app's soft, toy-like character comes from. Fully-round pills are
 * [com.sualtikasifi.cizimhafiza.presentation.common.PillShape].
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

/**
 * Design tokens Material 3's [androidx.compose.material3.ColorScheme] has no
 * slot for. Reached through [AppTheme] (e.g. `AppTheme.edge`) rather than
 * imported as top-level colors, so a screen can never accidentally pair a
 * face color with the wrong edge.
 */
@Immutable
data class AppTokens(
    /** Hard bottom edge under a raised white surface. */
    val edge: Color = PaperEdge,
    /** Hard bottom edge under a primary-colored surface. */
    val primaryEdge: Color = OrangeDeep,
    /** Hard bottom edge under a secondary-colored surface. */
    val secondaryEdge: Color = TealDeep,
    /** How far a raised surface stands off the page. */
    val raise: Dp = 4.dp,
    /** Reduced raise for small/dense controls. */
    val raiseSmall: Dp = 3.dp,
    val shadow: Color = ShadowWarm,
    val textFaint: Color = TextFaint,
    val success: Color = CorrectGreen,
    val successContainer: Color = CorrectContainer,
    val gold: Color = GoldAccent,
    val canvasGrid: Color = CanvasGrid,
    /**
     * The drawing paper — white in BOTH palettes, unlike every other
     * surface. [PenColor] is a fixed near-black, so a canvas that followed
     * colorScheme.surface into the dark palette would be black ink on a
     * near-black ground: every stroke, live and saved, invisible. The paper
     * is part of the game, not part of the theme.
     */
    val canvasPaper: Color = CardWhite,
    /** Bottom stop of the page's background gradient. */
    val backgroundDeep: Color = CreamDeep,
    /** Raised card face over the textured collage — see CardWarmWhite. */
    val cardWarm: Color = CardWarmWhite,
    /** How strongly the page gradient veils the collage artwork behind it. */
    val backgroundVeilTop: Float = 0.82f,
    val backgroundVeilBottom: Float = 0.90f
)


private val LocalAppTokens = staticCompositionLocalOf { AppTokens() }

object AppTheme {
    val tokens: AppTokens
        @Composable get() = LocalAppTokens.current
}

/**
 * One warm palette, always. Karalak is played on paper: the drawing canvas
 * is white in every screen that shows a stroke, and a dark theme meant
 * inverting the app around a surface that could never invert with it.
 * Dynamic color is unused for the same reason — a palette sampled from the
 * wallpaper would fight both the collage background and that fixed canvas.
 *
 * The design tokens below stay, and screens reach colors through them
 * rather than importing constants directly: that indirection is worth
 * keeping on its own, whether or not a second palette ever exists.
 */
@Composable
fun CizimHafizaTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppTokens provides AppTokens()) {
        MaterialTheme(
            colorScheme = WarmColorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
