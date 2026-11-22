package com.dresskit.wardrobe.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.media.FaceDetector
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class BodySegmentationResult(
    val personMask: Bitmap,
    val bodyBounds: RectF,
    val headCenterX: Float,
    val neckY: Float,
    val shoulderY: Float,
    val waistY: Float,
    val ankleY: Float,
    val shoulderSpanNorm: Float,
    val ambientColor: Int
)

/**
 * On-device U2-NetP segmentation & studio image enhancement engine:
 * 1) Clean studio cutout + E-Commerce Flat-Lay product transformation on pure #FFFFFF
 * 2) Person silhouette segmentation + body landmark estimation for 3D Virtual Fitting Room
 */
object StudioCutoutProcessor {
    private const val MODEL_ASSET_NAME = "u2netp.onnx"
    private const val MODEL_INPUT_SIZE = 320
    private const val STUDIO_CANVAS_SIZE = 1080
    private const val STUDIO_PADDING_RATIO = 0.095f

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
        return toEcommerceFlatLayShot(context, source)
    }

    /**
     * Transforms a garment photo into a balanced e-commerce flat-lay product photograph on pure #FFFFFF:
     * strips background & detached clutter, trims top hanger hooks, softens harsh crease shadows
     * while preserving exact fabric colors/prints/logos, subtly levels tilt, and centers on 1:1 square.
     */
    fun toEcommerceFlatLayShot(context: Context, source: Bitmap): Bitmap {
        return try {
            val mask320 = inferMask(context, source, applyScreenshotCutoff = true)
            compositeFlatLayOnWhite(source, mask320)
        } catch (_: Exception) {
            centerOnWhiteCanvas(source)
        }
    }

    /**
     * Generates person silhouette mask + normalized body anchor coordinates for 3D Kabin drape.
     */
    fun analyzeUserBodyFor3D(context: Context, bodyBitmap: Bitmap): BodySegmentationResult {
        val w = bodyBitmap.width.coerceAtLeast(1)
        val h = bodyBitmap.height.coerceAtLeast(1)
        val mask320 = runCatching {
            inferMask(context, bodyBitmap, applyScreenshotCutoff = false)
        }.getOrElse { FloatArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE) { 1f } }

        val targetMax = 520
        val scale = min(1f, targetMax.toFloat() / max(w, h).toFloat())
        val maskW = (w * scale).roundToInt().coerceIn(96, targetMax)
        val maskH = (h * scale).roundToInt().coerceIn(96, targetMax)

        val alpha320 = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE) { i ->
            val a = (mask320[i] * 255f).toInt().coerceIn(0, 255)
            Color.argb(a, 255, 255, 255)
        }
        val smallMask = Bitmap.createBitmap(
            alpha320,
            MODEL_INPUT_SIZE,
            MODEL_INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val upscaledMask = Bitmap.createScaledBitmap(smallMask, maskW, maskH, true)
        smallMask.recycle()

        // Dilate slightly horizontally so slightly looser garments blend naturally into person contour
        val maskPixels = IntArray(maskW * maskH)
        upscaledMask.getPixels(maskPixels, 0, maskW, 0, 0, maskW, maskH)

        var minX = maskW
        var minY = maskH
        var maxX = -1
        var maxY = -1
        var fgCount = 0
        for (y in 0 until maskH) {
            val rowOff = y * maskW
            for (x in 0 until maskW) {
                val a = (maskPixels[rowOff + x] ushr 24) and 0xFF
                if (a >= 95) {
                    fgCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val bounds = if (fgCount > maskW * maskH * 0.03f && maxX > minX && maxY > minY) {
            RectF(
                minX.toFloat() / maskW,
                minY.toFloat() / maskH,
                (maxX + 1).toFloat() / maskW,
                (maxY + 1).toFloat() / maskH
            )
        } else {
            RectF(0.24f, 0.08f, 0.76f, 0.94f)
        }

        // Ambient light tint sampling from person body photo
        val sampleBmp = Bitmap.createScaledBitmap(bodyBitmap, maskW, maskH, true)
        val samplePixels = IntArray(maskW * maskH)
        sampleBmp.getPixels(samplePixels, 0, maskW, 0, 0, maskW, maskH)
        sampleBmp.recycle()

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var lumCount = 0L
        for (i in samplePixels.indices) {
            val px = samplePixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val lum = (r + g + b) / 3
            if (lum in 35..230) {
                sumR += r
                sumG += g
                sumB += b
                lumCount++
            }
        }
        val ambientColor = if (lumCount > 0L) {
            Color.rgb(
                (sumR / lumCount).toInt().coerceIn(0, 255),
                (sumG / lumCount).toInt().coerceIn(0, 255),
                (sumB / lumCount).toInt().coerceIn(0, 255)
            )
        } else {
            Color.rgb(190, 190, 190)
        }

        // Optional FaceDetector refinement for head center & neck/shoulder anchors
        var headCenterX = bounds.centerX().coerceIn(0.2f, 0.8f)
        var detectedNeckY: Float? = null
        runCatching {
            val detW = 240
            val detH = ((240f * h / w).roundToInt().coerceIn(120, 480) / 2) * 2
            val faceBmp = Bitmap.createBitmap(detW, detH, Bitmap.Config.RGB_565)
            val c = Canvas(faceBmp)
            c.drawBitmap(bodyBitmap, null, RectF(0f, 0f, detW.toFloat(), detH.toFloat()), null)
            @Suppress("DEPRECATION")
            val detector = FaceDetector(detW, detH, 1)
            @Suppress("DEPRECATION")
            val faces = arrayOfNulls<FaceDetector.Face>(1)
            val count = detector.findFaces(faceBmp, faces)
            faceBmp.recycle()
            if (count > 0 && faces[0] != null) {
                val f = faces[0]!!
                val mid = PointF()
                f.getMidPoint(mid)
                val eyeDist = f.eyesDistance().coerceAtLeast(detW * 0.04f)
                headCenterX = (mid.x / detW).coerceIn(0.2f, 0.8f)
                detectedNeckY = ((mid.y + eyeDist * 1.35f) / detH).coerceIn(0.08f, 0.45f)
            }
        }

        val bodyH = bounds.height().coerceAtLeast(0.35f)
        val neckY = detectedNeckY ?: (bounds.top + bodyH * 0.17f).coerceIn(0.10f, 0.40f)
        val shoulderY = (neckY + bodyH * 0.04f).coerceIn(neckY, 0.50f)
        val waistY = (shoulderY + bodyH * 0.34f).coerceIn(shoulderY + 0.12f, 0.78f)
        val ankleY = (bounds.bottom - bodyH * 0.04f).coerceIn(waistY + 0.12f, 0.96f)

        // Estimate shoulder horizontal span from torso slice
        val sliceRow = (shoulderY * maskH + maskH * 0.07f).roundToInt().coerceIn(0, maskH - 1)
        var sMinX = maskW
        var sMaxX = 0
        for (x in 0 until maskW) {
            if (((maskPixels[sliceRow * maskW + x] ushr 24) and 0xFF) >= 95) {
                if (x < sMinX) sMinX = x
                if (x > sMaxX) sMaxX = x
            }
        }
        val measuredSpan = if (sMaxX > sMinX) (sMaxX - sMinX).toFloat() / maskW else 0f
        val shoulderSpanNorm = if (measuredSpan > 0.18f) {
            measuredSpan.coerceIn(0.26f, 0.82f)
        } else {
            (bounds.width() * 0.72f).coerceIn(0.28f, 0.78f)
        }

        return BodySegmentationResult(
            personMask = upscaledMask,
            bodyBounds = bounds,
            headCenterX = headCenterX,
            neckY = neckY,
            shoulderY = shoulderY,
            waistY = waistY,
            ankleY = ankleY,
            shoulderSpanNorm = shoulderSpanNorm,
            ambientColor = ambientColor
        )
    }

    private fun inferMask(
        context: Context,
        source: Bitmap,
        applyScreenshotCutoff: Boolean
    ): FloatArray {
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

        val isTallScreenshot = applyScreenshotCutoff &&
            source.height.toFloat() / source.width.toFloat().coerceAtLeast(1f) > 1.65f
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

    private fun compositeFlatLayOnWhite(source: Bitmap, mask320: FloatArray): Bitmap {
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

        // 1. Clean uniform outer borders
        clearUniformBorderHalo(srcPixels, alphaArray, w, h)

        // 2. Keep only the main connected garment region (strips stray hands/table clutter)
        keepLargestForegroundComponent(alphaArray, w, h)

        // 3. Measure bounding box and trim narrow hanger hook centered above neckline
        var bbox = findForegroundBounds(alphaArray, w, h) ?: return centerOnWhiteCanvas(source)
        stripTopHangerHook(alphaArray, w, h, bbox)
        bbox = findForegroundBounds(alphaArray, w, h) ?: return centerOnWhiteCanvas(source)

        val fgCount = countForegroundPixels(alphaArray)
        val cropW = bbox.right - bbox.left + 1
        val cropH = bbox.bottom - bbox.top + 1
        if (fgCount < (w * h) * 0.015 || cropW < 16 || cropH < 16) {
            return centerOnWhiteCanvas(source)
        }

        // 4. Low-frequency studio illumination equalization (lifts harsh crease shadows, preserves prints & hues)
        applyStudioFlatLayLighting(srcPixels, alphaArray, w, h, bbox)

        // 5. Matte cleanly onto pure white (#FFFFFF)
        val blended = IntArray(w * h)
        for (i in 0 until w * h) {
            val a = alphaArray[i] / 255f
            val px = srcPixels[i]
            val sr = (px shr 16) and 0xFF
            val sg = (px shr 8) and 0xFF
            val sb = px and 0xFF
            val outR = (sr * a + 255f * (1f - a)).toInt().coerceIn(0, 255)
            val outG = (sg * a + 255f * (1f - a)).toInt().coerceIn(0, 255)
            val outB = (sb * a + 255f * (1f - a)).toInt().coerceIn(0, 255)
            blended[i] = Color.rgb(outR, outG, outB)
        }

        val cutoutFull = Bitmap.createBitmap(blended, w, h, Bitmap.Config.ARGB_8888)
        val cropped = Bitmap.createBitmap(cutoutFull, bbox.left, bbox.top, cropW, cropH)
        cutoutFull.recycle()

        // 6. Auto-straighten slight hanger/camera tilt while preserving shoes/wide accessories
        val leveled = autoLevelIfNeeded(cropped, alphaArray, w, bbox)
        if (leveled !== cropped) cropped.recycle()

        val result = centerOnWhiteCanvas(leveled)
        leveled.recycle()
        return result
    }

    private fun findForegroundBounds(alphaArray: IntArray, w: Int, h: Int): Rect? {
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                if (alphaArray[rowOff + x] >= 115) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX > minX && maxY > minY) Rect(minX, minY, maxX, maxY) else null
    }

    private fun countForegroundPixels(alphaArray: IntArray): Int {
        var count = 0
        for (a in alphaArray) {
            if (a >= 115) count++
        }
        return count
    }

    private fun keepLargestForegroundComponent(alphaArray: IntArray, w: Int, h: Int) {
        val total = w * h
        if (total == 0) return
        val labels = IntArray(total)
        val queue = IntArray(total)
        var bestLabel = 0
        var bestCount = 0
        var currentLabel = 0

        for (i in 0 until total) {
            if (alphaArray[i] < 115 || labels[i] != 0) continue
            currentLabel++
            var head = 0
            var tail = 0
            labels[i] = currentLabel
            queue[tail++] = i

            while (head < tail) {
                val idx = queue[head++]
                val x = idx % w
                val y = idx / w
                if (x > 0) {
                    val n = idx - 1
                    if (alphaArray[n] >= 115 && labels[n] == 0) {
                        labels[n] = currentLabel
                        queue[tail++] = n
                    }
                }
                if (x + 1 < w) {
                    val n = idx + 1
                    if (alphaArray[n] >= 115 && labels[n] == 0) {
                        labels[n] = currentLabel
                        queue[tail++] = n
                    }
                }
                if (y > 0) {
                    val n = idx - w
                    if (alphaArray[n] >= 115 && labels[n] == 0) {
                        labels[n] = currentLabel
                        queue[tail++] = n
                    }
                }
                if (y + 1 < h) {
                    val n = idx + w
                    if (alphaArray[n] >= 115 && labels[n] == 0) {
                        labels[n] = currentLabel
                        queue[tail++] = n
                    }
                }
            }

            if (tail > bestCount) {
                bestCount = tail
                bestLabel = currentLabel
            }
        }

        if (bestLabel == 0 || bestCount < 32) return
        for (i in 0 until total) {
            if (alphaArray[i] >= 115 && labels[i] != bestLabel) {
                alphaArray[i] = 0
            }
        }
    }

    private fun stripTopHangerHook(alphaArray: IntArray, w: Int, h: Int, bbox: Rect) {
        val boxW = (bbox.right - bbox.left + 1).coerceAtLeast(1)
        val boxH = (bbox.bottom - bbox.top + 1).coerceAtLeast(1)
        val scanEndY = (bbox.top + boxH * 0.16f).roundToInt().coerceAtMost(bbox.bottom)
        val narrowMaxW = (boxW * 0.14f).coerceAtLeast(4f)
        val boxCenterX = (bbox.left + bbox.right) * 0.5f

        for (y in bbox.top..scanEndY) {
            val rowOff = y * w
            var rowMinX = bbox.right + 1
            var rowMaxX = bbox.left - 1
            for (x in bbox.left..bbox.right) {
                if (alphaArray[rowOff + x] >= 115) {
                    if (x < rowMinX) rowMinX = x
                    if (x > rowMaxX) rowMaxX = x
                }
            }
            if (rowMaxX < rowMinX) continue
            val spanW = (rowMaxX - rowMinX + 1).toFloat()
            val spanCenter = (rowMinX + rowMaxX) * 0.5f
            if (spanW < narrowMaxW && abs(spanCenter - boxCenterX) < boxW * 0.25f) {
                for (x in rowMinX..rowMaxX) {
                    alphaArray[rowOff + x] = 0
                }
            } else {
                break
            }
        }
    }

    private fun applyStudioFlatLayLighting(
        pixels: IntArray,
        alphaArray: IntArray,
        w: Int,
        h: Int,
        bbox: Rect
    ) {
        val gridSize = 40
        val gridSum = FloatArray(gridSize * gridSize)
        val gridCnt = IntArray(gridSize * gridSize)
        val boxW = (bbox.right - bbox.left + 1).coerceAtLeast(1)
        val boxH = (bbox.bottom - bbox.top + 1).coerceAtLeast(1)

        val lumaHist = IntArray(256)
        var fgTotal = 0

        for (y in bbox.top..bbox.bottom) {
            val rowOff = y * w
            val gy = (((y - bbox.top).toFloat() / boxH) * gridSize).toInt().coerceIn(0, gridSize - 1)
            for (x in bbox.left..bbox.right) {
                val idx = rowOff + x
                if (alphaArray[idx] < 130) continue
                val px = pixels[idx]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val luma = (0.299f * r + 0.587f * g + 0.114f * b)
                val gx = (((x - bbox.left).toFloat() / boxW) * gridSize).toInt().coerceIn(0, gridSize - 1)
                val gIdx = gy * gridSize + gx
                gridSum[gIdx] += luma
                gridCnt[gIdx]++
                lumaHist[luma.toInt().coerceIn(0, 255)]++
                fgTotal++
            }
        }
        if (fgTotal < 64) return

        // Find 72nd percentile luminance so dark fold shadows lift softly toward well-lit fabric tone
        val targetRank = (fgTotal * 0.72f).toInt()
        var running = 0
        var targetLuma = 180f
        for (bin in 0..255) {
            running += lumaHist[bin]
            if (running >= targetRank) {
                targetLuma = bin.toFloat()
                break
            }
        }
        targetLuma = targetLuma.coerceIn(40f, 240f)

        val field = FloatArray(gridSize * gridSize)
        for (i in field.indices) {
            field[i] = if (gridCnt[i] > 0) gridSum[i] / gridCnt[i] else -1f
        }

        // Fill empty cells from neighbors then blur heavily so ONLY smooth lighting changes apply
        repeat(3) {
            val next = field.copyOf()
            for (gy in 0 until gridSize) {
                for (gx in 0 until gridSize) {
                    val cur = field[gy * gridSize + gx]
                    if (cur >= 0f && it == 0) continue
                    var sum = 0f
                    var cnt = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val ny = gy + dy
                            val nx = gx + dx
                            if (ny in 0 until gridSize && nx in 0 until gridSize) {
                                val v = field[ny * gridSize + nx]
                                if (v >= 0f) {
                                    sum += v
                                    cnt++
                                }
                            }
                        }
                    }
                    if (cnt > 0) next[gy * gridSize + gx] = sum / cnt
                }
            }
            next.copyInto(field)
        }

        for (y in bbox.top..bbox.bottom) {
            val rowOff = y * w
            val fy = (((y - bbox.top).toFloat() / boxH) * (gridSize - 1)).coerceIn(0f, (gridSize - 1).toFloat())
            val y0 = fy.toInt().coerceIn(0, gridSize - 1)
            val y1 = (y0 + 1).coerceIn(0, gridSize - 1)
            val wy = fy - y0
            for (x in bbox.left..bbox.right) {
                val idx = rowOff + x
                if (alphaArray[idx] < 90) continue
                val fx = (((x - bbox.left).toFloat() / boxW) * (gridSize - 1)).coerceIn(0f, (gridSize - 1).toFloat())
                val x0 = fx.toInt().coerceIn(0, gridSize - 1)
                val x1 = (x0 + 1).coerceIn(0, gridSize - 1)
                val wx = fx - x0

                val l00 = field[y0 * gridSize + x0].let { if (it < 0f) targetLuma else it }
                val l10 = field[y0 * gridSize + x1].let { if (it < 0f) targetLuma else it }
                val l01 = field[y1 * gridSize + x0].let { if (it < 0f) targetLuma else it }
                val l11 = field[y1 * gridSize + x1].let { if (it < 0f) targetLuma else it }
                val localLuma = ((l00 * (1f - wx) + l10 * wx) * (1f - wy) +
                    (l01 * (1f - wx) + l11 * wx) * wy).coerceAtLeast(16f)

                val px = pixels[idx]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val pxLuma = 0.299f * r + 0.587f * g + 0.114f * b

                // Preserve deep blacks without graying out; soften fold shadows elsewhere
                val blackProtect = smoothstep(18f, 48f, pxLuma)
                val rawGain = (targetLuma / localLuma).coerceIn(0.84f, 1.34f)
                val studioGain = (1f + (rawGain - 1f) * 0.56f * blackProtect) * (1f + 0.03f * blackProtect)

                val nr = (r * studioGain).roundToInt().coerceIn(0, 255)
                val ng = (g * studioGain).roundToInt().coerceIn(0, 255)
                val nb = (b * studioGain).roundToInt().coerceIn(0, 255)
                pixels[idx] = Color.rgb(nr, ng, nb)
            }
        }
    }

    private fun autoLevelIfNeeded(
        cropped: Bitmap,
        alphaArray: IntArray,
        fullW: Int,
        bbox: Rect
    ): Bitmap {
        val boxW = bbox.right - bbox.left + 1
        val boxH = bbox.bottom - bbox.top + 1
        val aspect = boxH.toFloat() / boxW.toFloat().coerceAtLeast(1f)
        if (aspect < 0.85f || boxW < 40 || boxH < 40) return cropped

        fun findTopYInBand(xStartNorm: Float, xEndNorm: Float): Float? {
            val xs = (bbox.left + boxW * xStartNorm).roundToInt().coerceIn(bbox.left, bbox.right)
            val xe = (bbox.left + boxW * xEndNorm).roundToInt().coerceIn(xs, bbox.right)
            val ysMax = (bbox.top + boxH * 0.45f).roundToInt().coerceAtMost(bbox.bottom)
            var sumY = 0f
            var cnt = 0
            for (x in xs..xe step max(1, (xe - xs) / 8)) {
                for (y in bbox.top..ysMax) {
                    if (alphaArray[y * fullW + x] >= 120) {
                        sumY += y
                        cnt++
                        break
                    }
                }
            }
            return if (cnt >= 3) sumY / cnt else null
        }

        val leftY = findTopYInBand(0.22f, 0.34f) ?: return cropped
        val rightY = findTopYInBand(0.66f, 0.78f) ?: return cropped
        val dx = boxW * 0.44f
        val dy = rightY - leftY
        val deg = (atan2(dy, dx) * 180.0 / PI).toFloat()
        if (abs(deg) < 1.5f || abs(deg) > 9.5f) return cropped

        val matrix = Matrix().apply { postRotate(-deg) }
        val rotated = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        // Re-fill transparent rotation triangles with pure white
        val rw = rotated.width
        val rh = rotated.height
        val rPixels = IntArray(rw * rh)
        rotated.getPixels(rPixels, 0, rw, 0, 0, rw, rh)
        var minX = rw
        var minY = rh
        var maxX = -1
        var maxY = -1
        for (y in 0 until rh) {
            val rowOff = y * rw
            for (x in 0 until rw) {
                val px = rPixels[rowOff + x]
                val a = (px ushr 24) and 0xFF
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                if (a < 250) {
                    val alphaF = a / 255f
                    rPixels[rowOff + x] = Color.rgb(
                        (r * alphaF + 255f * (1f - alphaF)).toInt().coerceIn(0, 255),
                        (g * alphaF + 255f * (1f - alphaF)).toInt().coerceIn(0, 255),
                        (b * alphaF + 255f * (1f - alphaF)).toInt().coerceIn(0, 255)
                    )
                } else if (!(r >= 248 && g >= 248 && b >= 248)) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        val cleanWhiteBmp = Bitmap.createBitmap(rPixels, rw, rh, Bitmap.Config.ARGB_8888)
        rotated.recycle()
        return if (maxX > minX && maxY > minY) {
            val tight = Bitmap.createBitmap(cleanWhiteBmp, minX, minY, maxX - minX + 1, maxY - minY + 1)
            cleanWhiteBmp.recycle()
            tight
        } else {
            cleanWhiteBmp
        }
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

        val isBrightNeutralBg = bgR > 218 && bgG > 218 && bgB > 218 &&
            abs(bgR - bgG) < 18 && abs(bgG - bgB) < 18
        if (!isBrightNeutralBg) return

        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        for (c in cornerIndices) {
            visited[c] = true
            queue[tail++] = c
        }

        val tolerance = 24
        while (head < tail) {
            val idx = queue[head++]
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
                    queue[tail++] = idx - 1
                }
                if (x + 1 < w && !visited[idx + 1]) {
                    visited[idx + 1] = true
                    queue[tail++] = idx + 1
                }
                if (y > 0 && !visited[idx - w]) {
                    visited[idx - w] = true
                    queue[tail++] = idx - w
                }
                if (y + 1 < h && !visited[idx + w]) {
                    visited[idx + w] = true
                    queue[tail++] = idx + w
                }
            }
        }
    }

    private fun centerOnWhiteCanvas(subject: Bitmap, paddingRatio: Float = STUDIO_PADDING_RATIO): Bitmap {
        val canvasBmp = Bitmap.createBitmap(
            STUDIO_CANVAS_SIZE,
            STUDIO_CANVAS_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(canvasBmp)
        canvas.drawColor(Color.WHITE)

        val pad = STUDIO_CANVAS_SIZE * paddingRatio
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

    /**
     * Polishes a generative catalog flat-lay output: snaps perimeter near-white studio walls
     * cleanly to pure #FFFFFF without eating white fabric inside seams, trims excess margins,
     * and centers symmetrically onto a 1080x1080 pure #FFFFFF canvas with balanced margins.
     */
    fun polishCatalogShot(aiBitmap: Bitmap): Bitmap {
        return try {
            val w = aiBitmap.width.coerceAtLeast(1)
            val h = aiBitmap.height.coerceAtLeast(1)
            val work = if (aiBitmap.config == Bitmap.Config.ARGB_8888) {
                aiBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                aiBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } ?: return centerOnWhiteCanvas(aiBitmap, 0.065f)

            val pixels = IntArray(w * h)
            work.getPixels(pixels, 0, w, 0, 0, w, h)
            work.recycle()

            fun isNearWhiteBg(px: Int): Boolean {
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                return minC >= 242 && (maxC - minC) <= 9
            }

            val total = w * h
            val isBg = BooleanArray(total)
            val visited = BooleanArray(total)
            val queue = IntArray(total)
            var head = 0
            var tail = 0

            fun trySeed(idx: Int) {
                if (!visited[idx] && isNearWhiteBg(pixels[idx])) {
                    visited[idx] = true
                    isBg[idx] = true
                    queue[tail++] = idx
                }
            }

            for (x in 0 until w) {
                trySeed(x)
                trySeed((h - 1) * w + x)
            }
            for (y in 0 until h) {
                trySeed(y * w)
                trySeed(y * w + (w - 1))
            }

            while (head < tail) {
                val idx = queue[head++]
                val x = idx % w
                val y = idx / w
                if (x > 0) trySeed(idx - 1)
                if (x + 1 < w) trySeed(idx + 1)
                if (y > 0) trySeed(idx - w)
                if (y + 1 < h) trySeed(idx + w)
            }

            val rowFg = IntArray(h)
            val colFg = IntArray(w)
            var rawMinX = w
            var rawMinY = h
            var rawMaxX = -1
            var rawMaxY = -1
            var fgCount = 0

            for (y in 0 until h) {
                val rowOff = y * w
                for (x in 0 until w) {
                    val idx = rowOff + x
                    if (isBg[idx]) {
                        pixels[idx] = Color.WHITE
                    } else {
                        val px = pixels[idx]
                        val r = (px shr 16) and 0xFF
                        val g = (px shr 8) and 0xFF
                        val b = px and 0xFF
                        if (!(r >= 247 && g >= 247 && b >= 247)) {
                            fgCount++
                            rowFg[y]++
                            colFg[x]++
                            if (x < rawMinX) rawMinX = x
                            if (x > rawMaxX) rawMaxX = x
                            if (y < rawMinY) rawMinY = y
                            if (y > rawMaxY) rawMaxY = y
                        }
                        pixels[idx] = Color.rgb(r, g, b)
                    }
                }
            }

            if (fgCount <= (total * 0.04f)) {
                return centerOnWhiteCanvas(aiBitmap, 0.065f)
            }

            val minRowPx = max(2, (w * 0.005f).toInt())
            val minColPx = max(2, (h * 0.005f).toInt())
            var minX = 0
            while (minX < w && colFg[minX] < minColPx) minX++
            var maxX = w - 1
            while (maxX > minX && colFg[maxX] < minColPx) maxX--
            var minY = 0
            while (minY < h && rowFg[minY] < minRowPx) minY++
            var maxY = h - 1
            while (maxY > minY && rowFg[maxY] < minRowPx) maxY--

            if (maxX <= minX + 16 || maxY <= minY + 16) {
                minX = rawMinX
                maxX = rawMaxX
                minY = rawMinY
                maxY = rawMaxY
            }

            val cleanBmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
            val validBox = maxX > minX + 16 && maxY > minY + 16
            if (!validBox) {
                val out = centerOnWhiteCanvas(cleanBmp, 0.065f)
                cleanBmp.recycle()
                return out
            }

            val cropped = Bitmap.createBitmap(cleanBmp, minX, minY, maxX - minX + 1, maxY - minY + 1)
            cleanBmp.recycle()
            val centered = centerOnWhiteCanvas(cropped, 0.065f)
            cropped.recycle()
            centered
        } catch (_: Exception) {
            centerOnWhiteCanvas(aiBitmap, 0.065f)
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
