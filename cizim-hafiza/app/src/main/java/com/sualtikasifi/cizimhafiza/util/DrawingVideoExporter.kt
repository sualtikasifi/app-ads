package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import androidx.core.content.FileProvider
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReplay
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns a stored drawing into a short MP4 of it being drawn, for use outside
 * the game — the review panel's "kaydet" next to approve/reject, so a
 * drawing worth keeping can be taken before the decision that deletes it.
 *
 * Reuses [DrawingReplay] for its timing rather than picking its own, so the
 * file is a recording of the replay the reviewer actually watched and not a
 * second, differently-paced rendering of the same strokes.
 *
 * **Why frames are fed to the encoder as raw YUV buffers** rather than drawn
 * onto `MediaCodec.createInputSurface()`: an encoder input surface expects to
 * be rendered into with OpenGL, and `Surface.lockCanvas` on one is not
 * supported — it works on some devices and silently produces nothing on
 * others. Converting each frame costs a few hundred milliseconds of CPU for
 * a whole video, which is a fair price for a path that behaves the same
 * everywhere. Nothing here touches the main thread.
 */
object DrawingVideoExporter {

    /**
     * Square, so the file drops into a social post without being cropped by
     * whoever it is uploaded to. A multiple of 16 keeps every hardware
     * encoder happy — some reject dimensions they cannot tile.
     */
    private const val SIZE = 720
    private const val FRAME_RATE = 30
    private const val BIT_RATE = 6_000_000
    private const val I_FRAME_INTERVAL_SECONDS = 1

    /**
     * Frames of the finished drawing held at the end. Without them the video
     * ends on the very frame the last point lands and the thing being shown
     * is never actually seen whole.
     */
    private const val TAIL_FRAMES = FRAME_RATE

    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /** Wall-clock bound on the whole encode — see the loop in [encode]. */
    private const val ENCODE_TIMEOUT_MS = 60_000L
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

    // Same palette as the shared PNG cards (see DrawingShareUtil) so a clip
    // and a still of the same drawing look like they came from one app.
    private val paperColor = Color.rgb(0xFB, 0xF3, 0xE7)
    private val penColor = Color.rgb(0x1E, 0x1B, 0x18)
    private val textMuted = Color.rgb(0x8A, 0x7F, 0x72)

    /**
     * Renders and encodes the whole clip. Suspends on [Dispatchers.Default]
     * — expect a second or two for a dense drawing.
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
                    drawFrame(canvas, strokes, totalUnits, progress, word)
                }
            }
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

    // ---- frame rendering ----

    private fun drawFrame(
        canvas: Canvas,
        strokes: List<DrawingStroke>,
        totalUnits: Int,
        progress: Float,
        word: String
    ) {
        canvas.drawColor(paperColor)

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

        // Bottom inset leaves the word its own band instead of the drawing
        // running underneath the caption.
        val captionBand = SIZE * 0.13f
        val padding = SIZE * 0.09f
        val availableWidth = SIZE - padding * 2
        val availableHeight = SIZE - captionBand - padding * 2
        val scale = minOf(availableWidth / contentWidth, availableHeight / contentHeight)
        val offsetX = (SIZE - contentWidth * scale) / 2f
        val offsetY = padding + (availableHeight - contentHeight * scale) / 2f

        val strokePaint = Paint().apply {
            color = penColor
            style = Paint.Style.STROKE
            strokeWidth = SIZE * 0.011f
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

        val captionPaint = Paint().apply {
            color = textMuted
            textSize = SIZE * 0.062f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(word.uppercase(), SIZE / 2f, SIZE - captionBand * 0.35f, captionPaint)
    }

    // ---- encoding ----

    private inline fun encode(file: File, totalFrames: Int, renderFrame: (Canvas, Int) -> Unit) {
        val (codecName, colorFormat) = selectEncoder()
            ?: error("Bu cihazda kullanılabilir bir video kodlayıcı yok")

        val format = MediaFormat.createVideoFormat(MIME, SIZE, SIZE).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
        }

        val codec = MediaCodec.createByCodecName(codecName)
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pixels = IntArray(SIZE * SIZE)
        val yuv = ByteArray(SIZE * SIZE * 3 / 2)
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
                            bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
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
     * pixel of each 2x2 block: correct enough for line art on flat paper,
     * where there is no colour detail to lose.
     */
    private fun toYuv420(pixels: IntArray, out: ByteArray, semiPlanar: Boolean) {
        val frameSize = SIZE * SIZE
        val chromaPlaneSize = frameSize / 4
        var uIndex = frameSize
        var vIndex = if (semiPlanar) frameSize + 1 else frameSize + chromaPlaneSize

        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val argb = pixels[y * SIZE + x]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                out[y * SIZE + x] = ((((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
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

    private const val TAG = "DrawingVideoExporter"
}
