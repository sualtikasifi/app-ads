package com.sualtikasifi.cizimhafiza.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Karalak's "warm paper studio" palette.
 *
 * The look is built on three ideas, and every token below serves one of them:
 *
 *  1. **Paper, not screen.** The background is a warm cream that darkens
 *     slightly toward the bottom ([CreamBackground] → [CreamDeep]); cards are
 *     true white "paper" sitting on top of it.
 *  2. **Chunky depth, not soft blur.** Instead of Material's diffuse
 *     elevation shadows, raised surfaces get a HARD bottom edge in a darker
 *     shade of themselves ([PaperEdge], [OrangeDeep], [TealDeep]) — the
 *     toy-like, pressable look. Every raised color therefore comes in pairs:
 *     a face and an edge.
 *  3. **Orange leads, teal answers.** Orange is the game's action color;
 *     teal is reserved for social/online surfaces so multiplayer reads as a
 *     different place without introducing a second brand.
 *
 * [WordCategoryColors] is a separate, deliberately muted family used ONLY as
 * small category accents — never as UI chrome.
 */

// --- Paper & ink ------------------------------------------------------------

/** Page background, top of the vertical gradient. */
val CreamBackground = Color(0xFFFCF5EA)

/** Page background, bottom of the gradient — a touch deeper so screens have a light source. */
val CreamDeep = Color(0xFFF3E6D2)

/** Recessed/inset areas (progress tracks, empty slots) on a cream page. */
val CreamBackgroundVariant = Color(0xFFF1E3CD)

/** Raised card face. */
val CardWhite = Color(0xFFFFFFFF)

/** Hard bottom edge under a white card — the "thickness" of the paper. */
val PaperEdge = Color(0xFFE3D3B9)

/** Hairline border on cards and inputs. */
val Outline = Color(0xFFE9DCC6)

/** Warm-tinted shadow; a neutral black shadow reads grey and dirty on cream. */
val ShadowWarm = Color(0x2E4A3520)

// --- Text -------------------------------------------------------------------

/** Primary text — a deep espresso, not black, so it sits on cream without vibrating. */
val TextDark = Color(0xFF2A1F16)

/** Secondary text: labels, captions, inactive states. */
val TextMuted = Color(0xFF8B7B67)

/** Tertiary text: hints and disabled content. */
val TextFaint = Color(0xFFB0A18C)

// --- Primary: orange --------------------------------------------------------

val Orange = Color(0xFFF97316)

/** Pressed/hover face and small dark accents. */
val OrangeDark = Color(0xFFDC5F0B)

/** Hard bottom edge under an orange button. */
val OrangeDeep = Color(0xFFB94B06)

/** Tinted container for selected chips, badges, icon wells. */
val OrangeContainer = Color(0xFFFDE7CE)

/** Text/icons drawn on [OrangeContainer]. */
val OrangeInk = Color(0xFF9E4207)

// --- Secondary: teal (online / social surfaces) -----------------------------

val Teal = Color(0xFF0E9490)
val TealDeep = Color(0xFF076E6B)
val TealContainer = Color(0xFFD3EDEB)
val TealInk = Color(0xFF06605D)

// --- Feedback ---------------------------------------------------------------

val CorrectGreen = Color(0xFF3E9E56)
val CorrectContainer = Color(0xFFDCF0DF)
val WrongRed = Color(0xFFDF4E3C)
val WrongContainer = Color(0xFFFBE0DC)

/** Countdown color for the final seconds of a turn. */
val TimerWarning = Color(0xFFE23A2C)

/** Gold used for first place / streak badges. */
val GoldAccent = Color(0xFFE0A32B)

// --- Canvas -----------------------------------------------------------------

/**
 * Pen color for the drawing canvas — fixed and theme-independent, since the
 * canvas paper is always white. (This used to derive from
 * `colorScheme.onSurface`, which turned pale in system dark mode and made
 * strokes nearly invisible against the white paper.)
 */
val PenColor = Color(0xFF221E1A)

/** Dot-grid texture on canvas paper — must stay faint enough to draw over. */
val CanvasGrid = Color(0xFFEDE2D0)

// --- Category accents -------------------------------------------------------

/**
 * One muted hue per word category, held at roughly equal lightness/chroma so
 * no single category shouts. Keyed by the Turkish category name stored in the
 * word pool; [wordCategoryColor] resolves a name (or null = "all") to a color.
 */
val WordCategoryColors: Map<String, Color> = mapOf(
    "Hayvanlar" to Color(0xFF4F9D69),
    "Eşyalar" to Color(0xFFC08A2E),
    "Meslekler" to Color(0xFF4E7FC1),
    "Spor" to Color(0xFFD2593F),
    "Doğa" to Color(0xFF3E9C8F),
    "Yiyecekler" to Color(0xFFD5566E),
    "Taşıtlar" to Color(0xFF6A6FC4),
    "Duygular" to Color(0xFFC77BB0),
    "Giyim" to Color(0xFF8C7BC4)
)

/** Falls back to the brand orange for "all categories" and any unmapped name. */
fun wordCategoryColor(category: String?): Color =
    category?.let { WordCategoryColors[it] } ?: Orange
