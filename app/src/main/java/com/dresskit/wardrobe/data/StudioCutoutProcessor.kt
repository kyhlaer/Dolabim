package com.dresskit.wardrobe.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.min

/**
 * Runs on-device U2-NetP salient object segmentation to strip real-world or screenshot
 * backgrounds and frame garments centered on a pure studio white (#FFFFFF) square canvas.
 */
object StudioCutoutProcessor {
    private const val MODEL_ASSET_NAME = "u2netp.onnx"
    private const val MODEL_INPUT_SIZE = 320
    private const val STUDIO_CANVAS_SIZE = 1080
    private const val STUDIO_PADDING_RATIO = 0.09f

    private val lock = Any()

    @Volatile
    private var cachedSession: OrtSession? = null

    private fun getOrCreateSession(context: Context): OrtSession {
        cachedSession?.let { return it }
        synchronized(lock) {
            cachedSession?.let { return it }
            val modelDir = File(context.filesDir, "models")
            if (!modelDir.exists()) modelDir.mkdirs()
            val modelFile = File(modelDir, MODEL_ASSET_NAME)
            if (!modelFile.exists() || modelFile.length() < 1_000_000L) {
                context.assets.open(MODEL_ASSET_NAME).use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
            }
            return env.createSession(modelFile.absolutePath, opts).also {
                cachedSession = it
            }
        }
    }

    fun toStudioProductShot(context: Context, source: Bitmap): Bitmap {
        return try {
            val mask320 = inferMask(context, source)
            compositeAndCenter(source, mask320)
        } catch (_: Exception) {
            centerOnWhiteCanvas(source)
        }
    }

    private fun inferMask(context: Context, source: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(source, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        scaled.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        if (scaled !== source) scaled.recycle()

        var maxChannel = 1f
        for (px in pixels) {
            val r = ((px shr 16) and 0xFF).toFloat()
            val g = ((px shr 8) and 0xFF).toFloat()
            val b = (px and 0xFF).toFloat()
            if (r > maxChannel) maxChannel = r
            if (g > maxChannel) maxChannel = g
            if (b > maxChannel) maxChannel = b
        }

        val planeSize = MODEL_INPUT_SIZE * MODEL_INPUT_SIZE
        val inputData = FloatArray(3 * planeSize)
        val meanR = 0.485f
        val meanG = 0.456f
        val meanB = 0.406f
        val stdR = 0.229f
        val stdG = 0.224f
        val stdB = 0.225f

        for (i in 0 until planeSize) {
            val px = pixels[i]
            val r = ((px shr 16) and 0xFF) / maxChannel
            val g = ((px shr 8) and 0xFF) / maxChannel
            val b = (px and 0xFF) / maxChannel
            inputData[i] = (r - meanR) / stdR
            inputData[planeSize + i] = (g - meanG) / stdG
            inputData[2 * planeSize + i] = (b - meanB) / stdB
        }

        val env = OrtEnvironment.getEnvironment()
        val session = getOrCreateSession(context)
        val inputName = session.inputNames.first()
        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(inputData),
            longArrayOf(1L, 3L, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong())
        )

        val isTallScreenshot = source.height.toFloat() / source.width.toFloat().coerceAtLeast(1f) > 1.65f
        val topCutoffRow = if (isTallScreenshot) (MODEL_INPUT_SIZE * 0.12f).toInt() else -1
        val bottomCutoffRow = if (isTallScreenshot) (MODEL_INPUT_SIZE * 0.86f).toInt() else MODEL_INPUT_SIZE

        val flatMask = FloatArray(planeSize)
        tensor.use { t ->
            session.run(mapOf(inputName to t)).use { results ->
                @Suppress("UNCHECKED_CAST")
                val out4d = results[0].value as Array<Array<Array<FloatArray>>>
                val out2d = out4d[0][0]
                var minV = Float.MAX_VALUE
                var maxV = -Float.MAX_VALUE
                for (y in 0 until MODEL_INPUT_SIZE) {
                    if (y < topCutoffRow || y > bottomCutoffRow) continue
                    val row = out2d[y]
                    for (x in 0 until MODEL_INPUT_SIZE) {
                        val v = row[x]
                        if (v < minV) minV = v
                        if (v > maxV) maxV = v
                    }
                }
                val span = (maxV - minV).coerceAtLeast(1e-6f)
                for (y in 0 until MODEL_INPUT_SIZE) {
                    val rowOffset = y * MODEL_INPUT_SIZE
                    if (y < topCutoffRow || y > bottomCutoffRow) {
                        for (x in 0 until MODEL_INPUT_SIZE) {
                            flatMask[rowOffset + x] = 0f
                        }
                        continue
                    }
                    val row = out2d[y]
                    for (x in 0 until MODEL_INPUT_SIZE) {
                        val norm = ((row[x] - minV) / span).coerceIn(0f, 1f)
                        flatMask[rowOffset + x] = smoothstep(0.32f, 0.70f, norm)
                    }
                }
            }
        }
        return flatMask
    }

    private fun compositeAndCenter(source: Bitmap, mask320: FloatArray): Bitmap {
        val w = source.width
        val h = source.height

        val maskPixels320 = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        for (i in maskPixels320.indices) {
            val a = (mask320[i] * 255f).toInt().coerceIn(0, 255)
            maskPixels320[i] = Color.argb(a, 255, 255, 255)
        }
        val smallMaskBmp = Bitmap.createBitmap(
            maskPixels320,
            MODEL_INPUT_SIZE,
            MODEL_INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val fullMaskBmp = Bitmap.createScaledBitmap(smallMaskBmp, w, h, true)
        smallMaskBmp.recycle()

        val srcPixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        fullMaskBmp.getPixels(maskPixels, 0, w, 0, 0, w, h)
        fullMaskBmp.recycle()

        val alphaArray = IntArray(w * h) { idx ->
            (maskPixels[idx] ushr 24) and 0xFF
        }

        // Clean uniform outer borders on e-commerce / studio photos using border flood-fill
        clearUniformBorderHalo(srcPixels, alphaArray, w, h)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        var fgCount = 0

        val blended = IntArray(w * h)
        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val idx = rowOff + x
                val alphaByte = alphaArray[idx]
                if (alphaByte >= 115) {
                    fgCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                val a = alphaByte / 255f
                val px = srcPixels[idx]
                val sr = (px shr 16) and 0xFF
                val sg = (px shr 8) and 0xFF
                val sb = px and 0xFF
                val outR = (sr * a + 255f * (1f - a)).toInt().coerceIn(0, 255)
                val outG = (sg * a + 255f * (1f - a)).toInt().coerceIn(0, 255)
                val outB = (sb * a + 255f * (1f - a)).toInt().coerceIn(0, 255)
                blended[idx] = Color.rgb(outR, outG, outB)
            }
        }

        val totalPixels = w * h
        if (fgCount < totalPixels * 0.015 || maxX - minX < 16 || maxY - minY < 16) {
            return centerOnWhiteCanvas(source)
        }

        val cutoutFull = Bitmap.createBitmap(blended, w, h, Bitmap.Config.ARGB_8888)
        val cropW = (maxX - minX + 1).coerceAtLeast(1)
        val cropH = (maxY - minY + 1).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(cutoutFull, minX, minY, cropW, cropH)
        cutoutFull.recycle()

        val result = centerOnWhiteCanvas(cropped)
        cropped.recycle()
        return result
    }

    /**
     * If the image already sits on a bright, nearly uniform studio/screenshot background,
     * flood-fill from image corners to strip any leftover outer light halo or framing edges.
     */
    private fun clearUniformBorderHalo(
        srcPixels: IntArray,
        alphaArray: IntArray,
        w: Int,
        h: Int
    ) {
        if (w < 8 || h < 8) return
        val cornerIndices = intArrayOf(0, w - 1, (h - 1) * w, h * w - 1)
        var sumR = 0
        var sumG = 0
        var sumB = 0
        for (idx in cornerIndices) {
            val px = srcPixels[idx]
            sumR += (px shr 16) and 0xFF
            sumG += (px shr 8) and 0xFF
            sumB += px and 0xFF
        }
        val bgR = sumR / 4
        val bgG = sumG / 4
        val bgB = sumB / 4

        // Only apply corner flood-fill if corners are light & relatively neutral (typical e-commerce bg)
        val isBrightNeutralBg = bgR > 218 && bgG > 218 && bgB > 218 &&
            abs(bgR - bgG) < 18 && abs(bgG - bgB) < 18
        if (!isBrightNeutralBg) return

        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>(w * 2)
        for (c in cornerIndices) {
            visited[c] = true
            queue.addLast(c)
        }

        val tolerance = 24
        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val px = srcPixels[idx]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            if (abs(r - bgR) <= tolerance && abs(g - bgG) <= tolerance && abs(b - bgB) <= tolerance) {
                alphaArray[idx] = 0
                val x = idx % w
                val y = idx / w
                if (x > 0 && !visited[idx - 1]) {
                    visited[idx - 1] = true
                    queue.addLast(idx - 1)
                }
                if (x + 1 < w && !visited[idx + 1]) {
                    visited[idx + 1] = true
                    queue.addLast(idx + 1)
                }
                if (y > 0 && !visited[idx - w]) {
                    visited[idx - w] = true
                    queue.addLast(idx - w)
                }
                if (y + 1 < h && !visited[idx + w]) {
                    visited[idx + w] = true
                    queue.addLast(idx + w)
                }
            }
        }
    }

    private fun centerOnWhiteCanvas(subject: Bitmap): Bitmap {
        val canvasBmp = Bitmap.createBitmap(
            STUDIO_CANVAS_SIZE,
            STUDIO_CANVAS_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(canvasBmp)
        canvas.drawColor(Color.WHITE)

        val pad = STUDIO_CANVAS_SIZE * STUDIO_PADDING_RATIO
        val avail = STUDIO_CANVAS_SIZE - 2f * pad
        val scale = min(avail / subject.width.toFloat(), avail / subject.height.toFloat())
        val drawW = subject.width * scale
        val drawH = subject.height * scale
        val left = (STUDIO_CANVAS_SIZE - drawW) / 2f
        val top = (STUDIO_CANVAS_SIZE - drawH) / 2f

        val srcRect = Rect(0, 0, subject.width, subject.height)
        val dstRect = RectF(left, top, left + drawW, top + drawH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(subject, srcRect, dstRect, paint)
        return canvasBmp
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
