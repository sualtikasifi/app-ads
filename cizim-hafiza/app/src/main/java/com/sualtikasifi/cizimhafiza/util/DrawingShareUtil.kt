package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a finished drawing (its vector strokes) into a shareable branded
 * PNG card and hands it off to the system share sheet, so a funny drawing
 * can be sent straight to a friend without leaving the app.
 */
object DrawingShareUtil {

    private const val CARD_SIZE = 1000
    private const val CANVAS_INSET = 60f
    private const val CANVAS_PADDING = 40f

    private val backgroundColor = Color.rgb(0xFB, 0xF3, 0xE7)
    private val cardWhite = Color.WHITE
    private val outline = Color.rgb(0xE8, 0xDC, 0xC9)
    private val penColor = Color.rgb(0x1E, 0x1B, 0x18)
    private val textDark = Color.rgb(0x2B, 0x21, 0x18)
    private val brandOrange = Color.rgb(0xF9, 0x73, 0x16)

    fun shareDrawing(context: Context, word: String, strokes: List<DrawingStroke>) {
        val bitmap = renderCard(word, strokes)
        val file = writeToCache(context, bitmap)
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    private fun renderCard(word: String, strokes: List<DrawingStroke>): Bitmap {
        val height = CARD_SIZE + 260
        val bitmap = Bitmap.createBitmap(CARD_SIZE, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val canvasRect = RectF(CANVAS_INSET, CANVAS_INSET, CARD_SIZE - CANVAS_INSET, CARD_SIZE - CANVAS_INSET)
        val cornerRadius = 32f
        canvas.drawRoundRect(canvasRect, cornerRadius, cornerRadius, Paint().apply {
            color = cardWhite
            isAntiAlias = true
        })
        canvas.drawRoundRect(canvasRect, cornerRadius, cornerRadius, Paint().apply {
            color = outline
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        })

        drawStrokes(canvas, strokes, canvasRect)

        val wordPaint = Paint().apply {
            color = textDark
            textSize = 56f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(word.capitalizeTr(), CARD_SIZE / 2f, CARD_SIZE + 90f, wordPaint)

        val brandPaint = Paint().apply {
            color = brandOrange
            textSize = 42f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("Karalak", CARD_SIZE / 2f, CARD_SIZE + 190f, brandPaint)

        return bitmap
    }

    private fun drawStrokes(canvas: Canvas, strokes: List<DrawingStroke>, targetRect: RectF) {
        val allPoints = strokes.asSequence().flatten()
        val minX = allPoints.minOfOrNull { it.x } ?: return
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOfOrNull { it.y } ?: return
        val maxY = allPoints.maxOf { it.y }
        val contentWidth = (maxX - minX).coerceAtLeast(1f)
        val contentHeight = (maxY - minY).coerceAtLeast(1f)

        val availableWidth = (targetRect.width() - CANVAS_PADDING * 2).coerceAtLeast(1f)
        val availableHeight = (targetRect.height() - CANVAS_PADDING * 2).coerceAtLeast(1f)
        val scale = minOf(availableWidth / contentWidth, availableHeight / contentHeight)
        val offsetX = targetRect.left + (targetRect.width() - contentWidth * scale) / 2f
        val offsetY = targetRect.top + (targetRect.height() - contentHeight * scale) / 2f

        val paint = Paint().apply {
            color = penColor
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        strokes.forEach { stroke ->
            if (stroke.size < 2) return@forEach
            val path = Path()
            val first = stroke.first()
            path.moveTo(offsetX + (first.x - minX) * scale, offsetY + (first.y - minY) * scale)
            stroke.drop(1).forEach {
                path.lineTo(offsetX + (it.x - minX) * scale, offsetY + (it.y - minY) * scale)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun writeToCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "shared_drawings").apply { mkdirs() }
        val file = File(dir, "karalak_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
