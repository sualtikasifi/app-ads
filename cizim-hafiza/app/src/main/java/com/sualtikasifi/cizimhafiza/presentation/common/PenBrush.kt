package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.sualtikasifi.cizimhafiza.domain.model.PenSkin

/**
 * Turns a [PenSkin] into something the canvas can paint with.
 *
 * A single-colour pen becomes a [SolidColor], which costs exactly what a
 * plain colour did before — the multi-stop pens are the only ones that pay
 * for a real gradient. The gradient is laid out diagonally across the whole
 * drawing surface rather than per-stroke, so a Galaxy or Rainbow pen reads
 * as one continuous sweep behind the picture instead of restarting its
 * colour ramp on every separate line.
 */
@Composable
fun rememberPenBrush(skin: PenSkin, canvasWidth: Float, canvasHeight: Float): Brush =
    remember(skin, canvasWidth, canvasHeight) { penBrush(skin, canvasWidth, canvasHeight) }

fun penBrush(skin: PenSkin, canvasWidth: Float, canvasHeight: Float): Brush {
    val colors = skin.colors.map { Color(it) }
    if (colors.size == 1) return SolidColor(colors.first())
    return Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = Offset(canvasWidth.coerceAtLeast(1f), canvasHeight.coerceAtLeast(1f))
    )
}

/** The pen's representative colour — for swatches in the picker, where a full gradient sweep would be illegible at 40dp. */
fun PenSkin.previewColor(): Color = Color(colors.first())
