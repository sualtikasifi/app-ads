package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import androidx.core.content.FileProvider
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReplay
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns a stored drawing into a short, vertical promo-video clip of it being
 * drawn — the review panel's "kaydet" next to approve/reject, for a drawing
 * worth taking out of the app and onto Instagram/TikTok before the review
 * decision that deletes it. There is no in-app share flow for this: it is a
 * tool for whoever runs the account to pick good drawings and post them
 * themselves, not something a player ever sees.
 *
 * Reuses [DrawingReplay] for its timing rather than picking its own, so the
 * file is a recording of the replay the reviewer actually watched and not a
 * second, differently-paced rendering of the same strokes.
 *
 * **Why frames are fed to the encoder as raw YUV buffers** rather than drawn
 * onto `MediaCodec.createInputSurface()`: an encoder input surface expects to
 * be rendered into with OpenGL, and `Surface.lockCanvas` on one is not
 * supported — it works on some devices and silently produces nothing on
 * others. Converting each frame costs a little CPU for a whole video, which
 * is a fair price for a path that behaves the same everywhere. Nothing here
 * touches the main thread.
 */
object DrawingVideoExporter {

    /**
     * 9:16 — a Reels/Shorts/Stories frame, not the old square export. A
     * multiple of 16 on both sides keeps every hardware encoder happy — some
     * reject dimensions they cannot tile.
     */
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val FRAME_RATE = 30
    private const val BIT_RATE = 8_000_000
    private const val I_FRAME_INTERVAL_SECONDS = 1

    /**
     * Frames of the finished drawing held at the end. Without them the video
     * ends on the very frame the last point lands and the thing being shown
     * is never actually seen whole.
     */
    private const val TAIL_FRAMES = FRAME_RATE

    /** How long the "tap to play" watermark stays over the drawing before fading out. */
    private const val PLAY_ICON_FADE_FRAMES = FRAME_RATE / 2

    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /** Wall-clock bound on the whole encode — see the loop in [encode]. */
    private const val ENCODE_TIMEOUT_MS = 60_000L
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

    // The app's own palette (see presentation/theme/Color.kt) rather than a
    // one-off promo palette — a clip posted next to the app's real icon and
    // screenshots should visibly be the same product.
    private val creamBg = Color.rgb(0xFC, 0xF5, 0xEA)
    private val textDark = Color.rgb(0x2A, 0x1F, 0x16)
    private val textMuted = Color.rgb(0x6B, 0x5B, 0x49)
    private val teal = Color.rgb(0x0E, 0x94, 0x90)
    private val tealDeep = Color.rgb(0x07, 0x6E, 0x6B)
    private val orange = Color.rgb(0xF9, 0x73, 0x16)
    private val gold = Color.rgb(0xE0, 0xA3, 0x2B)
    private val penColor = Color.rgb(0x1E, 0x1B, 0x18)

    /**
     * The one thing to edit before this becomes someone else's promo tool:
     * swap in the account this is actually posted from.
     */
    private const val INSTAGRAM_HANDLE = "@KaralakUygulama"

    /**
     * Renders and encodes the whole clip. Suspends on [Dispatchers.Default]
     * — expect a few seconds for a dense drawing.
     *
     * Failure is returned rather than thrown: a device with no usable AVC
     * encoder is a thing that exists, and the caller's job is to say so
     * rather than to crash.
     */
    suspend fun export(
        context: Context,
        word: String,
        strokes: List<DrawingStroke>
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            require(strokes.any { it.isNotEmpty() }) { "Boş çizim" }

            val totalUnits = DrawingReplay.timelineUnits(strokes)
            val drawnFrames = (DrawingReplay.durationMillis(totalUnits) * FRAME_RATE / 1000)
                .coerceAtLeast(1)
            val totalFrames = drawnFrames + TAIL_FRAMES

            // Decoded once and reused for every frame — decoding a launcher
            // icon-sized PNG thirty times a second would be pure waste.
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.karalak_logo_mark)
            val masked = maskedWord(word)

            val file = File(
                File(context.cacheDir, "shared_drawings").apply { mkdirs() },
                "karalak_${sanitize(word)}_${System.currentTimeMillis()}.mp4"
            )

            // A failed encode leaves a file the muxer had already created
            // and half-written; nothing downstream could tell it from a
            // real clip.
            onFailureDelete(file) {
                encode(file, totalFrames) { canvas, frame ->
                    // frame + 1, so the opening frame already carries the
                    // first mark rather than being a blank sheet of paper. Past
                    // drawnFrames the progress stays pinned at 1, which is what
                    // makes the tail a held final image rather than a
                    // continuation.
                    val progress = ((frame + 1).toFloat() / drawnFrames).coerceAtMost(1f)
                    drawFrame(canvas, strokes, totalUnits, progress, masked, logo, frame)
                }
            }
            logo.recycle()
            file
        }.onFailure { Log.w(TAG, "Video export failed", it) }
    }

    /** Hands the finished file to the system share sheet. */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    /**
     * "KEDİ" -> "K _ _ _" — the first letter stays, everything else is a
     * blank for the caption to invite a guess instead of spoiling it. Only
     * letters/digits get a blank; a space in the word (two-word answers)
     * stays a space rather than turning into its own confusing blank.
     */
    private fun maskedWord(word: String): String {
        val upper = word.uppercase()
        val firstLetterIndex = upper.indexOfFirst { it.isLetterOrDigit() }
        if (firstLetterIndex < 0) return upper
        return upper.mapIndexed { index, c ->
            when {
                index == firstLetterIndex -> c.toString()
                c.isWhitespace() -> " "
                c.isLetterOrDigit() -> "_"
                else -> c.toString()
            }
        }.joinToString(" ")
    }

    // ---- frame rendering ----

    private fun drawFrame(
        canvas: Canvas,
        strokes: List<DrawingStroke>,
        totalUnits: Int,
        progress: Float,
        maskedWord: String,
        logo: Bitmap,
        frame: Int
    ) {
        canvas.drawColor(creamBg)

        drawHeader(canvas, logo)
        // Bottom stops at .665H, not further down — everything below it
        // (word caption, CTA, store badges, handle) is laid out cumulatively
        // from here, each consuming the previous one's actual measured
        // height, so this is the one number that has to leave enough room
        // for all four of them before HEIGHT runs out (see the tail end of
        // drawFooter).
        val frameRect = RectF(WIDTH * 0.08f, HEIGHT * 0.19f, WIDTH * 0.92f, HEIGHT * 0.665f)
        drawGlowFrame(canvas, frameRect)
        drawDrawing(canvas, strokes, totalUnits, progress, frameRect)
        if (frame < PLAY_ICON_FADE_FRAMES) {
            drawPlayIcon(canvas, frameRect, alpha = 255 - (255 * frame / PLAY_ICON_FADE_FRAMES))
        }
        val wordPillBottom = drawWordCaption(canvas, maskedWord, frameRect.bottom + HEIGHT * 0.035f)
        drawFooter(canvas, wordPillBottom)
    }

    /** App mark + "Karalak" wordmark, and the "Günün Çizimi" pill underneath. */
    private fun drawHeader(canvas: Canvas, logo: Bitmap) {
        val logoSize = WIDTH * 0.11f
        val wordmarkPaint = Paint().apply {
            color = tealDeep
            textSize = WIDTH * 0.09f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val wordmarkWidth = wordmarkPaint.measureText(WORDMARK)
        val groupWidth = logoSize + WIDTH * 0.03f + wordmarkWidth
        val groupLeft = (WIDTH - groupWidth) / 2f
        val logoTop = HEIGHT * 0.045f

        canvas.drawBitmap(
            logo,
            null,
            RectF(groupLeft, logoTop, groupLeft + logoSize, logoTop + logoSize),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        canvas.drawText(
            WORDMARK,
            groupLeft + logoSize + WIDTH * 0.03f,
            logoTop + logoSize / 2f - (wordmarkPaint.ascent() + wordmarkPaint.descent()) / 2f,
            wordmarkPaint
        )

        drawPill(
            canvas,
            text = "🌟 GÜNÜN ÇİZİMİ",
            centerX = WIDTH / 2f,
            centerY = HEIGHT * 0.145f,
            fill = orange,
            textColor = Color.WHITE,
            textSize = WIDTH * 0.042f
        )
    }

    /** A rounded rectangle with a soft multi-pass teal/gold glow behind a crisp border. */
    private fun drawGlowFrame(canvas: Canvas, rect: RectF) {
        val corner = WIDTH * 0.06f
        // Cheap glow: the same stroke drawn several times, wider and fainter
        // each pass, instead of a real blur filter — a BlurMaskFilter needs a
        // software layer per frame, which is exactly the cost this format
        // (30 frames/sec, encoded thirty times a second) can't afford.
        for (i in 4 downTo 1) {
            val glowPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = WIDTH * 0.004f * i
                color = teal
                alpha = 40
                isAntiAlias = true
            }
            canvas.drawRoundRect(rect, corner, corner, glowPaint)
        }
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = WIDTH * 0.01f
            isAntiAlias = true
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(teal, gold, teal),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, corner, corner, borderPaint)

        val fillPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        val inset = borderPaint.strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
            corner, corner, fillPaint
        )
    }

    private fun drawDrawing(
        canvas: Canvas,
        strokes: List<DrawingStroke>,
        totalUnits: Int,
        progress: Float,
        frameRect: RectF
    ) {
        // The fit is computed from ALL the strokes even though only some are
        // drawn — see DrawingReplay. A frame fitted to what has been drawn so
        // far would rescale the picture every frame.
        val allPoints = strokes.asSequence().flatten()
        val minX = allPoints.minOfOrNull { it.x } ?: return
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOfOrNull { it.y } ?: return
        val maxY = allPoints.maxOf { it.y }
        val contentWidth = (maxX - minX).coerceAtLeast(1f)
        val contentHeight = (maxY - minY).coerceAtLeast(1f)

        val padding = frameRect.width() * 0.08f
        val availableWidth = frameRect.width() - padding * 2
        val availableHeight = frameRect.height() - padding * 2
        val scale = minOf(availableWidth / contentWidth, availableHeight / contentHeight)
        val offsetX = frameRect.left + padding + (availableWidth - contentWidth * scale) / 2f
        val offsetY = frameRect.top + padding + (availableHeight - contentHeight * scale) / 2f

        val strokePaint = Paint().apply {
            color = penColor
            style = Paint.Style.STROKE
            strokeWidth = WIDTH * 0.008f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        val dotPaint = Paint().apply {
            color = penColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        DrawingReplay.forEachVisible(strokes, totalUnits, progress) { stroke, visiblePoints ->
            if (visiblePoints == 1) {
                val p = stroke.first()
                canvas.drawCircle(
                    offsetX + (p.x - minX) * scale,
                    offsetY + (p.y - minY) * scale,
                    strokePaint.strokeWidth / 2f,
                    dotPaint
                )
            } else {
                val path = Path()
                val first = stroke.first()
                path.moveTo(offsetX + (first.x - minX) * scale, offsetY + (first.y - minY) * scale)
                for (i in 1 until visiblePoints) {
                    val p = stroke[i]
                    path.lineTo(offsetX + (p.x - minX) * scale, offsetY + (p.y - minY) * scale)
                }
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    /** A translucent "reel" play button, faded out over the opening frames once drawing starts. */
    private fun drawPlayIcon(canvas: Canvas, frameRect: RectF, alpha: Int) {
        if (alpha <= 0) return
        val cx = frameRect.centerX()
        val cy = frameRect.centerY()
        val radius = frameRect.width() * 0.09f
        val circlePaint = Paint().apply {
            color = Color.BLACK
            this.alpha = (alpha * 0.35f).toInt()
            isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, radius, circlePaint)

        val trianglePaint = Paint().apply {
            color = Color.WHITE
            this.alpha = alpha
            isAntiAlias = true
        }
        val triangleSize = radius * 0.7f
        val path = Path().apply {
            moveTo(cx - triangleSize * 0.5f, cy - triangleSize * 0.75f)
            lineTo(cx - triangleSize * 0.5f, cy + triangleSize * 0.75f)
            lineTo(cx + triangleSize * 0.75f, cy)
            close()
        }
        canvas.drawPath(path, trianglePaint)
    }

    /** "Çizilen Kelime" label + the masked-word pill, e.g. "K _ _ _". Returns the pill's bottom edge. */
    private fun drawWordCaption(canvas: Canvas, maskedWord: String, top: Float): Float {
        val labelPaint = Paint().apply {
            color = textMuted
            textSize = WIDTH * 0.04f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("ÇİZİLEN KELİME", WIDTH / 2f, top, labelPaint)

        val pillRect = drawPill(
            canvas,
            text = maskedWord,
            centerX = WIDTH / 2f,
            centerY = top + WIDTH * 0.09f,
            fill = teal,
            textColor = Color.WHITE,
            textSize = WIDTH * 0.065f,
            horizontalPadding = WIDTH * 0.08f
        )
        return pillRect.bottom
    }

    /**
     * App-store badges + handle, the reason anyone watching would bother
     * opening the app. [top] is [drawWordCaption]'s own returned bottom edge
     * rather than a fixed fraction of HEIGHT — the two are laid out as one
     * continuous stack precisely so a change to the word pill's size can
     * never quietly start overlapping this one.
     */
    private fun drawFooter(canvas: Canvas, top: Float) {
        val ctaY = top + HEIGHT * 0.045f
        val ctaPaint = Paint().apply {
            color = textDark
            textSize = WIDTH * 0.05f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Karalak Uygulamasını Keşfet!", WIDTH / 2f, ctaY, ctaPaint)

        val badgeY = ctaY + HEIGHT * 0.045f
        val badgeGap = WIDTH * 0.04f
        val badgeTextSize = WIDTH * 0.032f
        val storeBadge = Paint().apply {
            textSize = badgeTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val leftText = "📱 App Store"
        val rightText = "▶ Google Play"
        val leftWidth = storeBadge.measureText(leftText)
        val rightWidth = storeBadge.measureText(rightText)
        val pad = WIDTH * 0.03f
        val totalWidth = leftWidth + rightWidth + pad * 4 + badgeGap
        var x = (WIDTH - totalWidth) / 2f
        x += drawBadge(canvas, leftText, x, badgeY, pad, storeBadge) + badgeGap
        drawBadge(canvas, rightText, x, badgeY, pad, storeBadge)

        val handlePaint = Paint().apply {
            color = textMuted
            textSize = WIDTH * 0.034f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(INSTAGRAM_HANDLE, WIDTH / 2f, badgeY + HEIGHT * 0.04f, handlePaint)
    }

    /** Draws one rounded-outline store badge at [left]; returns its width so callers can lay out the next one. */
    private fun drawBadge(canvas: Canvas, text: String, left: Float, centerY: Float, pad: Float, textPaint: Paint): Float {
        val textWidth = textPaint.measureText(text)
        val height = textPaint.textSize + pad * 1.4f
        val width = textWidth + pad * 2
        val rect = RectF(left, centerY - height / 2f, left + width, centerY + height / 2f)
        val outline = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = WIDTH * 0.0025f
            color = textMuted
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, height / 2f, height / 2f, outline)
        val fill = Paint(textPaint).apply { color = textDark; textAlign = Paint.Align.CENTER }
        canvas.drawText(text, rect.centerX(), centerY - (fill.ascent() + fill.descent()) / 2f, fill)
        return width
    }

    /**
     * A rounded, filled pill with centered text — the shape "Günün Çizimi"
     * and the masked word both share. Returns its bounds so a caller that
     * needs to stack something below it (see [drawWordCaption]) never has to
     * duplicate this sizing math to know where it actually ended.
     */
    private fun drawPill(
        canvas: Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        fill: Int,
        textColor: Int,
        textSize: Float,
        horizontalPadding: Float = WIDTH * 0.06f
    ): RectF {
        val textPaint = Paint().apply {
            color = textColor
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.02f
        }
        val textWidth = textPaint.measureText(text)
        val verticalPadding = textSize * 0.55f
        val rect = RectF(
            centerX - textWidth / 2f - horizontalPadding,
            centerY - textSize / 2f - verticalPadding,
            centerX + textWidth / 2f + horizontalPadding,
            centerY + textSize / 2f + verticalPadding
        )
        val fillPaint = Paint().apply { color = fill; isAntiAlias = true }
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
        canvas.drawText(text, centerX, centerY - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        return rect
    }

    // ---- encoding ----

    private inline fun encode(file: File, totalFrames: Int, renderFrame: (Canvas, Int) -> Unit) {
        val (codecName, colorFormat) = selectEncoder()
            ?: error("Bu cihazda kullanılabilir bir video kodlayıcı yok")

        val format = MediaFormat.createVideoFormat(MIME, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
        }

        val codec = MediaCodec.createByCodecName(codecName)
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pixels = IntArray(WIDTH * HEIGHT)
        val yuv = ByteArray(WIDTH * HEIGHT * 3 / 2)
        val semiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar

        var muxerStarted = false
        var trackIndex = -1

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var frame = 0
            var inputDone = false
            var outputDone = false

            // A codec that stops answering would otherwise spin this loop
            // for the life of the process. The bound is generous — this is
            // an escape hatch, not a performance budget.
            val deadline = System.currentTimeMillis() + ENCODE_TIMEOUT_MS

            while (!outputDone) {
                check(System.currentTimeMillis() < deadline) { "Video kodlaması zaman aşımına uğradı" }
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val presentationTimeUs = frame * 1_000_000L / FRAME_RATE
                        if (frame >= totalFrames) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            renderFrame(canvas, frame)
                            bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                            toYuv420(pixels, yuv, semiPlanar)
                            codec.getInputBuffer(inputIndex)?.apply {
                                clear()
                                put(yuv)
                            }
                            codec.queueInputBuffer(inputIndex, 0, yuv.size, presentationTimeUs, 0)
                            frame++
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // The only point at which the real output format is
                        // known, and the only legal moment to add the track.
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val encoded = codec.getOutputBuffer(outputIndex)
                        // Codec config bytes travel in the track format, not
                        // as a sample; writing them would corrupt the file.
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted && encoded != null) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            // Stopping a muxer that was never started throws, and that
            // exception would replace whatever actually went wrong.
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
            bitmap.recycle()
        }
    }

    /**
     * The first AVC encoder that can take one of the two YUV layouts we can
     * produce. Both are common; which one a device offers is not something
     * to assume, and guessing wrong produces a green-and-magenta video
     * rather than an error.
     */
    private fun selectEncoder(): Pair<String, Int>? {
        val preferred = listOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        )
        for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
            if (!info.isEncoder || MIME !in info.supportedTypes.map { it.lowercase() }) continue
            val supported = runCatching { info.getCapabilitiesForType(MIME).colorFormats.toSet() }
                .getOrNull() ?: continue
            preferred.firstOrNull { it in supported }?.let { return info.name to it }
        }
        return null
    }

    /**
     * ARGB_8888 to YUV 4:2:0, the only thing a hardware AVC encoder will
     * accept from a ByteBuffer. Chroma is point-sampled from the top-left
     * pixel of each 2x2 block — a real loss for the colored branding baked
     * into this frame (unlike the old square export's flat line art), but a
     * short promo clip re-compressed again by Instagram/TikTok on upload
     * never needed pixel-perfect chroma to begin with.
     */
    private fun toYuv420(pixels: IntArray, out: ByteArray, semiPlanar: Boolean) {
        val frameSize = WIDTH * HEIGHT
        val chromaPlaneSize = frameSize / 4
        var uIndex = frameSize
        var vIndex = if (semiPlanar) frameSize + 1 else frameSize + chromaPlaneSize

        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val argb = pixels[y * WIDTH + x]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                out[y * WIDTH + x] = ((((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
                    .coerceIn(0, 255)).toByte()

                if (y % 2 == 0 && x % 2 == 0) {
                    val u = ((((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)).toByte()
                    val v = ((((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)).toByte()
                    out[uIndex] = u
                    out[vIndex] = v
                    // Semi-planar interleaves U and V in one plane, planar
                    // keeps them in two — hence the different strides.
                    uIndex += if (semiPlanar) 2 else 1
                    vIndex += if (semiPlanar) 2 else 1
                }
            }
        }
    }

    /** Runs [block], removing [file] if it throws. */
    private inline fun onFailureDelete(file: File, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
    }

    private fun sanitize(word: String): String =
        word.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(24)

    private const val WORDMARK = "Karalak"
    private const val TAG = "DrawingVideoExporter"
}
