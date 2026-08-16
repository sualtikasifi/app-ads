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
import com.sualtikasifi.cizimhafiza.presentation.game.ResultItem
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * Renders finished drawings (their vector strokes) into shareable branded
 * PNG cards and hands them off to the system share sheet — either one
 * drawing at a time, or the whole game's results as a single collage — so a
 * funny drawing (or a whole round) can be sent straight to a friend without
 * leaving the app.
 */
object DrawingShareUtil {

    private const val CARD_WIDTH = 1000
    private const val CANVAS_INSET = 60f
    private const val STROKE_WIDTH_RATIO = 0.011f

    private val backgroundColor = Color.rgb(0xFB, 0xF3, 0xE7)
    private val cardWhite = Color.WHITE
    private val outline = Color.rgb(0xE8, 0xDC, 0xC9)
    private val penColor = Color.rgb(0x1E, 0x1B, 0x18)
    private val textDark = Color.rgb(0x2B, 0x21, 0x18)
    private val textMuted = Color.rgb(0x8A, 0x7F, 0x72)
    private val brandOrange = Color.rgb(0xF9, 0x73, 0x16)
    private val correctGreen = Color.rgb(0x3F, 0xA3, 0x4D)
    private val wrongRed = Color.rgb(0xE0, 0x52, 0x3F)

    fun shareDrawing(context: Context, word: String, strokes: List<DrawingStroke>) {
        val bitmap = renderSingleCard(word, strokes)
        shareBitmap(context, bitmap, "karalak")
    }

    fun shareAllResults(
        context: Context,
        totalScore: Int,
        correctCount: Int,
        wrongCount: Int,
        fastestCorrectSeconds: Double?,
        items: List<ResultItem>
    ) {
        val bitmap = renderResultsCard(totalScore, correctCount, wrongCount, fastestCorrectSeconds, items)
        shareBitmap(context, bitmap, "karalak_sonuc")
    }

    private fun shareBitmap(context: Context, bitmap: Bitmap, fileNamePrefix: String) {
        val file = writeToCache(context, bitmap, fileNamePrefix)
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    private fun renderSingleCard(word: String, strokes: List<DrawingStroke>): Bitmap {
        val height = CARD_WIDTH + 260
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val canvasRect = RectF(CANVAS_INSET, CANVAS_INSET, CARD_WIDTH - CANVAS_INSET, CARD_WIDTH - CANVAS_INSET)
        drawRoundedCard(canvas, canvasRect)
        drawStrokes(canvas, strokes, canvasRect)

        val wordPaint = Paint().apply {
            color = textDark
            textSize = 56f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(word.capitalizeTr(), CARD_WIDTH / 2f, CARD_WIDTH + 90f, wordPaint)

        drawBrandFooter(canvas, CARD_WIDTH / 2f, CARD_WIDTH + 190f, textSize = 42f)

        return bitmap
    }

    private fun renderResultsCard(
        totalScore: Int,
        correctCount: Int,
        wrongCount: Int,
        fastestCorrectSeconds: Double?,
        items: List<ResultItem>
    ): Bitmap {
        val columns = 3
        val outerPadding = 40f
        val cellGap = 20f
        val cellSize = (CARD_WIDTH - outerPadding * 2 - cellGap * (columns - 1)) / columns
        val labelHeight = 56f
        val rowHeight = cellSize + labelHeight + cellGap
        val rows = ceil(items.size / columns.toFloat()).toInt().coerceAtLeast(1)

        val headerHeight = 360f
        val gridHeight = rows * rowHeight
        val footerHeight = 150f
        val height = (headerHeight + gridHeight + footerHeight).toInt()

        val bitmap = Bitmap.createBitmap(CARD_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        drawResultsHeader(canvas, totalScore, correctCount, wrongCount, fastestCorrectSeconds)

        items.forEachIndexed { index, item ->
            val col = index % columns
            val row = index / columns
            val cellLeft = outerPadding + col * (cellSize + cellGap)
            val cellTop = headerHeight + row * rowHeight
            val cellRect = RectF(cellLeft, cellTop, cellLeft + cellSize, cellTop + cellSize)

            drawRoundedCard(canvas, cellRect, cornerRadius = 22f)
            drawStrokes(canvas, item.strokes, cellRect, paddingRatio = 0.1f)
            drawBadge(canvas, cellRect.right - 26f, cellRect.top + 26f, radius = 22f, isCorrect = item.isCorrect)

            val labelPaint = Paint().apply {
                color = textDark
                textSize = 32f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(
                item.word.capitalizeTr(),
                cellRect.centerX(),
                cellRect.bottom + labelHeight * 0.65f,
                labelPaint
            )
        }

        drawBrandFooter(canvas, CARD_WIDTH / 2f, headerHeight + gridHeight + footerHeight * 0.62f, textSize = 46f)

        return bitmap
    }

    private fun drawResultsHeader(
        canvas: Canvas,
        totalScore: Int,
        correctCount: Int,
        wrongCount: Int,
        fastestCorrectSeconds: Double?
    ) {
        val titlePaint = Paint().apply {
            color = textDark
            textSize = 40f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("Oyun Bitti!", CARD_WIDTH / 2f, 80f, titlePaint)

        val scorePaint = Paint().apply {
            color = brandOrange
            textSize = 96f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("$totalScore Puan", CARD_WIDTH / 2f, 200f, scorePaint)

        val stats = buildList {
            add("Doğru: $correctCount")
            add("Yanlış: $wrongCount")
            fastestCorrectSeconds?.let { add("En Hızlı: ${"%.1f".format(it)} sn") }
        }
        val pillPaint = Paint().apply {
            color = cardWhite
            isAntiAlias = true
        }
        val pillBorderPaint = Paint().apply {
            color = outline
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val pillTextPaint = Paint().apply {
            color = textDark
            textSize = 30f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val pillHeight = 64f
        val pillGap = 16f
        val pillWidths = stats.map { pillTextPaint.measureText(it) + 56f }
        val totalWidth = pillWidths.sum() + pillGap * (stats.size - 1)
        var x = CARD_WIDTH / 2f - totalWidth / 2f
        val pillTop = 250f
        stats.forEachIndexed { i, text ->
            val w = pillWidths[i]
            val rect = RectF(x, pillTop, x + w, pillTop + pillHeight)
            canvas.drawRoundRect(rect, pillHeight / 2f, pillHeight / 2f, pillPaint)
            canvas.drawRoundRect(rect, pillHeight / 2f, pillHeight / 2f, pillBorderPaint)
            canvas.drawText(text, rect.centerX(), rect.centerY() + pillTextPaint.textSize * 0.35f, pillTextPaint)
            x += w + pillGap
        }
    }

    private fun drawBrandFooter(canvas: Canvas, centerX: Float, y: Float, textSize: Float) {
        val brandPaint = Paint().apply {
            color = brandOrange
            this.textSize = textSize
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("Karalak", centerX, y, brandPaint)
    }

    private fun drawRoundedCard(canvas: Canvas, rect: RectF, cornerRadius: Float = 32f) {
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, Paint().apply {
            color = cardWhite
            isAntiAlias = true
        })
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, Paint().apply {
            color = outline
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        })
    }

    private fun drawBadge(canvas: Canvas, cx: Float, cy: Float, radius: Float, isCorrect: Boolean) {
        canvas.drawCircle(cx, cy, radius, Paint().apply {
            color = if (isCorrect) correctGreen else wrongRed
            isAntiAlias = true
        })
        val strokePaint = Paint().apply {
            color = cardWhite
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.26f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        if (isCorrect) {
            val path = Path().apply {
                moveTo(cx - radius * 0.45f, cy)
                lineTo(cx - radius * 0.1f, cy + radius * 0.35f)
                lineTo(cx + radius * 0.5f, cy - radius * 0.35f)
            }
            canvas.drawPath(path, strokePaint)
        } else {
            canvas.drawLine(cx - radius * 0.4f, cy - radius * 0.4f, cx + radius * 0.4f, cy + radius * 0.4f, strokePaint)
            canvas.drawLine(cx + radius * 0.4f, cy - radius * 0.4f, cx - radius * 0.4f, cy + radius * 0.4f, strokePaint)
        }
    }

    private fun drawStrokes(canvas: Canvas, strokes: List<DrawingStroke>, targetRect: RectF, paddingRatio: Float = 0.08f) {
        val allPoints = strokes.asSequence().flatten()
        val minX = allPoints.minOfOrNull { it.x } ?: return
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOfOrNull { it.y } ?: return
        val maxY = allPoints.maxOf { it.y }
        val contentWidth = (maxX - minX).coerceAtLeast(1f)
        val contentHeight = (maxY - minY).coerceAtLeast(1f)

        val padding = minOf(targetRect.width(), targetRect.height()) * paddingRatio
        val availableWidth = (targetRect.width() - padding * 2).coerceAtLeast(1f)
        val availableHeight = (targetRect.height() - padding * 2).coerceAtLeast(1f)
        val scale = minOf(availableWidth / contentWidth, availableHeight / contentHeight)
        val offsetX = targetRect.left + (targetRect.width() - contentWidth * scale) / 2f
        val offsetY = targetRect.top + (targetRect.height() - contentHeight * scale) / 2f

        val paint = Paint().apply {
            color = penColor
            style = Paint.Style.STROKE
            strokeWidth = minOf(targetRect.width(), targetRect.height()) * STROKE_WIDTH_RATIO
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

    private fun writeToCache(context: Context, bitmap: Bitmap, fileNamePrefix: String): File {
        val dir = File(context.cacheDir, "shared_drawings").apply { mkdirs() }
        val file = File(dir, "${fileNamePrefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
