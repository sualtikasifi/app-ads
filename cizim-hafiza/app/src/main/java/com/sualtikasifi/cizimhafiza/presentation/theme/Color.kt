package com.sualtikasifi.cizimhafiza.presentation.theme

import androidx.compose.ui.graphics.Color

// Warm cream/orange brand palette (per product-supplied mockups).
val CreamBackground = Color(0xFFFBF3E7)
val CreamBackgroundVariant = Color(0xFFF5E9D8)
val CardWhite = Color(0xFFFFFFFF)
val Orange = Color(0xFFF97316)
val OrangeDark = Color(0xFFD9600D)
val OrangeContainer = Color(0xFFFDE6CF)
val TextDark = Color(0xFF2B2118)
val TextMuted = Color(0xFF8A7F72)
val Outline = Color(0xFFE8DCC9)

// Countdown warning color for the last 2 seconds of a drawing turn.
val TimerWarning = Color(0xFFE53935)
val CorrectGreen = Color(0xFF3FA34D)
val WrongRed = Color(0xFFE0523F)

// Pen color for the drawing canvas — fixed and theme-independent, since the
// canvas paper is always white regardless of light/dark mode. Previously
// this defaulted to MaterialTheme.colorScheme.onSurface, which is fine in
// light mode but turns pale/cream in dark mode, making strokes nearly
// invisible against the white paper.
val PenColor = Color(0xFF1E1B18)
