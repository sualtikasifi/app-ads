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

/**
 * A faint warm-white, for raised cards that sit directly over the textured
 * [com.sualtikasifi.cizimhafiza.presentation.common.screenBackground] collage
 * (the main menu's 2x2 destination tiles) — pure [CardWhite] read as sterile
 * against the aged-paper artwork. Distinct from [CreamBackgroundVariant],
 * which is reserved for recessed/inset areas, not raised surfaces.
 */
val CardWarmWhite = Color(0xFFFFFBF3)

/** Hard bottom edge under a white card — the "thickness" of the paper. */
val PaperEdge = Color(0xFFE3D3B9)

/** Hairline border on cards and inputs. */
val Outline = Color(0xFFE9DCC6)

/** Warm-tinted shadow; a neutral black shadow reads grey and dirty on cream. */
val ShadowWarm = Color(0x2E4A3520)

// --- Text -------------------------------------------------------------------

/** Primary text — a deep espresso, not black, so it sits on cream without vibrating. */
val TextDark = Color(0xFF2A1F16)

/**
 * Secondary text: labels, captions, inactive states.
 *
 * Deliberately darker than a "muted" tone would normally be. It sits on the
 * cream page, which is now a textured collage rather than a flat fill, and
 * the previous 0xFF8B7B67 fell under WCAG AA (~3.6:1) against it — captions
 * and field labels in that color read as faded rather than secondary.
 */
val TextMuted = Color(0xFF6B5B49)

/** Tertiary text: hints and disabled content. Same reasoning as [TextMuted]. */
val TextFaint = Color(0xFF93826C)

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

// --- Dark palette -----------------------------------------------------------

/*
 * Karalak's dark theme is the same warm paper studio after dark, not a
 * neutral grey inversion: every surface keeps a brown cast so the collage
 * background, the orange primary and the gold accents still belong together.
 *
 * Two things stay light on purpose. The drawing canvas is always white
 * paper — inverting it would mean inverting every stroke a player has
 * already drawn, and a "dark canvas" is a different product, not a theme.
 * And [PenColor]/[CanvasGrid] are therefore left exactly as they are.
 *
 * Every slot below has an explicit value. The previous attempt at a dark
 * theme defined only some of them and let Material fill the rest with its
 * stock purple, which is where the illegible text and purple cards in dark
 * mode came from — so this palette is deliberately exhaustive.
 */

val DarkBackground = Color(0xFF171310)
val DarkBackgroundDeep = Color(0xFF100D0B)
val DarkCard = Color(0xFF241D18)
val DarkCardWarm = Color(0xFF2A221B)
val DarkSurfaceVariant = Color(0xFF2E251E)
val DarkOutline = Color(0xFF473A2F)
val DarkPaperEdge = Color(0xFF0E0B09)

val DarkTextPrimary = Color(0xFFF3E8DA)
val DarkTextMuted = Color(0xFFC6B5A0)
val DarkTextFaint = Color(0xFF9C8B76)

val DarkOrange = Color(0xFFFB8B3C)
val DarkOrangeDeep = Color(0xFF8A3703)
val DarkOrangeContainer = Color(0xFF43260F)
val DarkOrangeInk = Color(0xFFFFC894)

val DarkTeal = Color(0xFF2BB8B3)
val DarkTealDeep = Color(0xFF064F4C)
val DarkTealContainer = Color(0xFF12332F)
val DarkTealInk = Color(0xFF9EE5E0)

val DarkGold = Color(0xFFE8B84B)
val DarkCorrectGreen = Color(0xFF55C06E)
val DarkCorrectContainer = Color(0xFF193021)
val DarkWrongRed = Color(0xFFFF6B5A)
val DarkWrongContainer = Color(0xFF4A1810)
val DarkWrongInk = Color(0xFFFFC9C0)

/** Neutral black shadow: on a dark ground the warm shadow reads as a smudge. */
val DarkShadow = Color(0x66000000)
