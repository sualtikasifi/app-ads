package com.sualtikasifi.cizimhafiza.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.sualtikasifi.cizimhafiza.R

/**
 * Two bundled families (both cover the full Turkish alphabet, verified
 * against their cmap tables before bundling):
 *
 *  - [DisplayFont] — Baloo 2. Rounded, heavy-set, high x-height. Carries the
 *    game's voice: screen titles, the word being drawn, scores, timers.
 *  - [BodyFont] — Quicksand. Geometric and open at small sizes, where Baloo's
 *    tight apertures start to close up. Carries everything readable: body
 *    copy, labels, buttons, inputs.
 *
 * They are bundled as static TTFs rather than pulled through
 * `GoogleFont`/downloadable fonts on purpose — the game must look identical
 * offline and on first launch, with no async font swap mid-screen.
 */
val DisplayFont = FontFamily(
    Font(R.font.baloo2_medium, FontWeight.Medium),
    Font(R.font.baloo2_semibold, FontWeight.SemiBold),
    Font(R.font.baloo2_bold, FontWeight.Bold),
    Font(R.font.baloo2_extrabold, FontWeight.ExtraBold)
)

val BodyFont = FontFamily(
    Font(R.font.quicksand_regular, FontWeight.Normal),
    Font(R.font.quicksand_medium, FontWeight.Medium),
    Font(R.font.quicksand_semibold, FontWeight.SemiBold),
    Font(R.font.quicksand_bold, FontWeight.Bold)
)

// Custom fonts carry their own vertical metrics, which Compose pads on top of
// by default — that padding is what makes bundled display type sit visibly
// off-center inside buttons and circular timers. Turning it off and centering
// each line within its lineHeight instead makes optical centering match
// geometric centering everywhere.
private val Tight = PlatformTextStyle(includeFontPadding = false)
private val CenteredLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun display(size: Int, height: Int, weight: FontWeight = FontWeight.ExtraBold, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = DisplayFont,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = height.sp,
        letterSpacing = tracking.sp,
        platformStyle = Tight,
        lineHeightStyle = CenteredLines
    )

// SemiBold, not Medium, as the body default: Quicksand is a geometric face
// with thin, even strokes, and at 12-14sp its Medium weight visibly
// disappeared once the page behind it became a textured collage rather than
// flat cream. SemiBold keeps the same open, rounded shapes while giving the
// stroke enough weight to hold up against the artwork.
private fun body(size: Int, height: Int, weight: FontWeight = FontWeight.SemiBold, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = BodyFont,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = height.sp,
        letterSpacing = tracking.sp,
        platformStyle = Tight,
        lineHeightStyle = CenteredLines
    )

/**
 * The full Material 3 scale — every one of the fifteen roles is defined.
 * (Previously only six were, so any screen reaching for e.g.
 * `headlineMedium` or `labelSmall` silently fell back to stock Roboto at
 * stock weights, which is why type looked inconsistent from screen to screen.)
 */
val Typography = Typography(
    displayLarge = display(52, 58, tracking = -0.5),
    displayMedium = display(42, 48, tracking = -0.4),
    displaySmall = display(34, 40, tracking = -0.3),

    headlineLarge = display(32, 38, tracking = -0.2),
    headlineMedium = display(26, 32),
    headlineSmall = display(22, 28),

    titleLarge = display(20, 26, weight = FontWeight.Bold),
    titleMedium = body(17, 24, weight = FontWeight.Bold),
    titleSmall = body(15, 20, weight = FontWeight.Bold),

    bodyLarge = body(16, 24, tracking = 0.1),
    bodyMedium = body(15, 22, tracking = 0.1),
    // 13sp rather than 12: this is the app's caption size (progress lines,
    // card subtitles, empty states), and 12sp Quicksand was the single most
    // common piece of hard-to-read text on the textured page.
    bodySmall = body(13, 19, tracking = 0.1),

    labelLarge = body(15, 20, weight = FontWeight.Bold, tracking = 0.2),
    labelMedium = body(13, 18, weight = FontWeight.Bold, tracking = 0.2),
    labelSmall = body(12, 17, weight = FontWeight.Bold, tracking = 0.4)
)
