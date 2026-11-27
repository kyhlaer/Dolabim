package com.dresskit.wardrobe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.media.FaceDetector
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.localizedText
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow

enum class SkinUndertone {
    WARM,
    COOL,
    NEUTRAL;

    fun label(lang: AppLanguage): String = when (this) {
        WARM -> localizedText(lang, "Sıcak Alt Ton", "Warm Undertone", "Warmer Unterton", "Sous-ton chaud", "Subtono cálido", "暖色调肤色")
        COOL -> localizedText(lang, "Soğuk Alt Ton", "Cool Undertone", "Kühler Unterton", "Sous-ton froid", "Subtono frío", "冷色调肤色")
        NEUTRAL -> localizedText(lang, "Nötr Alt Ton", "Neutral Undertone", "Neutraler Unterton", "Sous-ton neutre", "Subtono neutro", "自然中性肤色")
    }
}

enum class SkinDepth {
    FAIR,
    MEDIUM,
    DEEP
}

enum class FacialContrast {
    SOFT,
    MEDIUM,
    HIGH;

    fun label(lang: AppLanguage): String = when (this) {
        SOFT -> localizedText(lang, "Yumuşak Kontrast", "Soft Contrast", "Weicher Kontrast", "Contraste doux", "Contraste suave", "柔和对比度")
        MEDIUM -> localizedText(lang, "Orta Kontrast", "Medium Contrast", "Mittlerer Kontrast", "Contraste moyen", "Contraste medio", "中等对比度")
        HIGH -> localizedText(lang, "Yüksek Kontrast", "High Contrast", "Starker Kontrast", "Contraste élevé", "Contraste alto", "高对比度")
    }
}

enum class ColorSeason(
    val undertone: SkinUndertone,
    val depth: SkinDepth
) {
    LIGHT_SPRING(SkinUndertone.WARM, SkinDepth.FAIR),
    TRUE_SPRING(SkinUndertone.WARM, SkinDepth.FAIR),
    BRIGHT_SPRING(SkinUndertone.WARM, SkinDepth.MEDIUM),
    LIGHT_SUMMER(SkinUndertone.COOL, SkinDepth.FAIR),
    TRUE_SUMMER(SkinUndertone.COOL, SkinDepth.MEDIUM),
    SOFT_SUMMER(SkinUndertone.COOL, SkinDepth.MEDIUM),
    SOFT_AUTUMN(SkinUndertone.NEUTRAL, SkinDepth.MEDIUM),
    TRUE_AUTUMN(SkinUndertone.WARM, SkinDepth.MEDIUM),
    DEEP_AUTUMN(SkinUndertone.WARM, SkinDepth.DEEP),
    DEEP_WINTER(SkinUndertone.COOL, SkinDepth.DEEP),
    TRUE_WINTER(SkinUndertone.COOL, SkinDepth.DEEP),
    BRIGHT_WINTER(SkinUndertone.NEUTRAL, SkinDepth.FAIR);

    fun shortLabel(lang: AppLanguage): String = when (this) {
        LIGHT_SPRING -> localizedText(lang, "Açık İlkbahar", "Light Spring", "Heller Frühling", "Printemps Clair", "Primavera Clara", "浅春型")
        TRUE_SPRING -> localizedText(lang, "Sıcak İlkbahar", "True Spring", "Warmer Frühling", "Printemps Vrai", "Primavera Pura", "暖春型")
        BRIGHT_SPRING -> localizedText(lang, "Canlı İlkbahar", "Bright Spring", "Klarer Frühling", "Printemps Lumineux", "Primavera Brillante", "净春型")
        LIGHT_SUMMER -> localizedText(lang, "Açık Yaz", "Light Summer", "Heller Sommer", "Été Clair", "Verano Claro", "浅夏型")
        TRUE_SUMMER -> localizedText(lang, "Soğuk Yaz", "True Summer", "Kühler Sommer", "Été Vrai", "Verano Puro", "冷夏型")
        SOFT_SUMMER -> localizedText(lang, "Yumuşak Yaz", "Soft Summer", "Weicher Sommer", "Été Doux", "Verano Suave", "柔夏型")
        SOFT_AUTUMN -> localizedText(lang, "Yumuşak Sonbahar", "Soft Autumn", "Weicher Herbst", "Automne Doux", "Otoño Suave", "柔秋型")
        TRUE_AUTUMN -> localizedText(lang, "Sıcak Sonbahar", "True Autumn", "Warmer Herbst", "Automne Vrai", "Otoño Puro", "暖秋型")
        DEEP_AUTUMN -> localizedText(lang, "Derin Sonbahar", "Deep Autumn", "Tiefer Herbst", "Automne Profond", "Otoño Profundo", "深秋型")
        DEEP_WINTER -> localizedText(lang, "Derin Kış", "Deep Winter", "Tiefer Winter", "Hiver Profond", "Invierno Profundo", "深冬型")
        TRUE_WINTER -> localizedText(lang, "Soğuk Kış", "True Winter", "Kühler Winter", "Hiver Vrai", "Invierno Puro", "冷冬型")
        BRIGHT_WINTER -> localizedText(lang, "Canlı Kış", "Bright Winter", "Klarer Winter", "Hiver Lumineux", "Invierno Brillante", "净冬型")
    }
}

data class PersonalColorProfile(
    val averageSkinHex: Long,
    val undertone: SkinUndertone,
    val depth: SkinDepth,
    val faceDetected: Boolean,
    val seasonTitle: String,
    val summaryText: String,
    val flatteringColors: List<GarmentColor>,
    val avoidColors: List<GarmentColor>,
    val season: ColorSeason = ColorSeason.SOFT_AUTUMN,
    val contrast: FacialContrast = FacialContrast.MEDIUM,
    val labHueDeg: Float = 54f
)

private data class LabColor(val l: Float, val a: Float, val b: Float)

/**
 * 12-Season Personal Color Analyzer using CIE L*a*b* (D65) skin chrominance,
 * clamped Gray-World ambient illuminant compensation (±14%), cheek/forehead ROI extraction,
 * interquartile trimmed medians, and hair/brow-to-skin facial contrast.
 */
object PersonalColorAnalyzer {

    fun analyzeUserPhoto(context: Context, lang: AppLanguage): PersonalColorProfile? {
        val file = ImageStorage.getUserFacePhotoFile(context)
        if (!file.exists() || file.length() == 0L) return null

        var orientedBmp: Bitmap? = null
        var evenBmp: Bitmap? = null
        var rgb565: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth < 2 || bounds.outHeight < 2) return null

            var sample = 1
            while (bounds.outWidth / sample > 480 || bounds.outHeight / sample > 640) {
                sample *= 2
            }
            var decoded: Bitmap? = null
            repeat(4) {
                if (decoded == null) {
                    try {
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                    } catch (_: OutOfMemoryError) {
                        sample *= 2
                    }
                }
            }
            val rawBmp = decoded ?: return null
            val orientation = ImageStorage.readExifOrientation(file)
            orientedBmp = ImageStorage.rotateIfNeeded(rawBmp, orientation)
            if (orientedBmp !== rawBmp && !rawBmp.isRecycled) rawBmp.recycle()

            val src = orientedBmp ?: return null
            if (src.width < 2 || src.height < 2) return null
            val evenW = (src.width and -2).coerceAtLeast(2)
            val bmp = if (src.width != evenW) {
                Bitmap.createBitmap(src, 0, 0, evenW, src.height)
            } else {
                src
            }
            evenBmp = bmp

            rgb565 = bmp.copy(Bitmap.Config.RGB_565, false)
            var faceFound = false
            var minX = (bmp.width * 0.25f).toInt()
            var maxX = (bmp.width * 0.75f).toInt()
            var minY = (bmp.height * 0.08f).toInt()
            var maxY = (bmp.height * 0.52f).toInt()
            var eyeBandTop = -1
            var eyeBandBottom = -1

            if (rgb565 != null) {
                val detector = FaceDetector(rgb565.width, rgb565.height, 1)
                val faces = arrayOfNulls<FaceDetector.Face>(1)
                val count = detector.findFaces(rgb565, faces)
                rgb565.recycle()
                rgb565 = null
                val face = if (count > 0) faces[0] else null
                if (face != null && face.eyesDistance() > 4f) {
                    val mid = PointF()
                    face.getMidPoint(mid)
                    val eyeDist = face.eyesDistance()
                    val radius = eyeDist * 0.72f
                    faceFound = true
                    minX = (mid.x - radius).toInt().coerceIn(0, bmp.width - 1)
                    maxX = (mid.x + radius).toInt().coerceIn(minX + 1, bmp.width)
                    minY = (mid.y - eyeDist * 0.65f).toInt().coerceIn(0, bmp.height - 1)
                    maxY = (mid.y + eyeDist * 0.88f).toInt().coerceIn(minY + 1, bmp.height)
                    eyeBandTop = (mid.y - eyeDist * 0.16f).toInt()
                    eyeBandBottom = (mid.y + eyeDist * 0.14f).toInt()
                }
            }

            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)

            // Sample perimeter / background ambient light for clamped Gray-World white balance
            var ambR = 0L
            var ambG = 0L
            var ambB = 0L
            var ambCnt = 0
            val edgeStep = maxOf(1, minOf(bmp.width, bmp.height) / 40)
            for (y in 0 until bmp.height step edgeStep) {
                val rowOff = y * bmp.width
                for (x in 0 until bmp.width step edgeStep) {
                    if (x in minX until maxX && y in minY until maxY) continue
                    val px = pixels[rowOff + x]
                    if (((px ushr 24) and 0xFF) < 128) continue
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    val lum = 0.299f * r + 0.587f * g + 0.114f * b
                    if (lum in 55f..242f && !isLikelySkinPixel(r, g, b)) {
                        ambR += r
                        ambG += g
                        ambB += b
                        ambCnt++
                    }
                }
            }
            val ambientTriple = if (ambCnt >= 12) {
                Triple((ambR / ambCnt).toInt(), (ambG / ambCnt).toInt(), (ambB / ambCnt).toInt())
            } else null

            // Sample hair/brow dark pixels near upper & outer face region for contrast estimation
            val darkLs = ArrayList<Float>(128)
            val hairTop = (minY - ((maxY - minY) * 0.28f).toInt()).coerceAtLeast(0)
            val hairBottom = (minY + ((maxY - minY) * 0.45f).toInt()).coerceAtMost(bmp.height)
            for (y in hairTop until hairBottom step 2) {
                val rowOff = y * bmp.width
                for (x in minX until maxX step 2) {
                    val px = pixels[rowOff + x]
                    if (((px ushr 24) and 0xFF) < 128) continue
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    if (!isLikelySkinPixel(r, g, b)) {
                        val lum = 0.299f * r + 0.587f * g + 0.114f * b
                        if (lum in 8f..125f) {
                            darkLs.add(rgbToLab(r, g, b).l)
                        }
                    }
                }
            }
            val darkFeatureL = if (darkLs.size >= 8) {
                darkLs.sort()
                darkLs[(darkLs.size * 0.25f).toInt().coerceIn(0, darkLs.lastIndex)]
            } else null

            val skinPixels = extractSkinSamplesFromPixels(
                width = bmp.width,
                height = bmp.height,
                pixels = pixels,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                eyeBandTop = eyeBandTop,
                eyeBandBottom = eyeBandBottom,
                ambientRgb = ambientTriple
            )

            if (skinPixels.isEmpty()) return null
            analyzeSkinSamples(skinPixels, ambientTriple, darkFeatureL, faceFound, lang)
        } catch (_: Throwable) {
            null
        } finally {
            if (rgb565 != null && !rgb565.isRecycled) rgb565.recycle()
            if (evenBmp != null && evenBmp !== orientedBmp && !evenBmp.isRecycled) evenBmp.recycle()
            if (orientedBmp != null && !orientedBmp.isRecycled) orientedBmp.recycle()
        }
    }

    internal fun extractSkinSamplesFromPixels(
        width: Int,
        height: Int,
        pixels: IntArray,
        minX: Int = (width * 0.25f).toInt(),
        maxX: Int = (width * 0.75f).toInt(),
        minY: Int = (height * 0.08f).toInt(),
        maxY: Int = (height * 0.52f).toInt(),
        eyeBandTop: Int = -1,
        eyeBandBottom: Int = -1,
        ambientRgb: Triple<Int, Int, Int>? = null
    ): List<Int> {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return emptyList()
        val x0 = minX.coerceIn(0, width - 1)
        val x1 = maxX.coerceIn(x0 + 1, width)
        val y0 = minY.coerceIn(0, height - 1)
        val y1 = maxY.coerceIn(y0 + 1, height)

        var gainR = 1f
        var gainG = 1f
        var gainB = 1f
        if (ambientRgb != null && ambientRgb.first > 30 && ambientRgb.second > 30 && ambientRgb.third > 30) {
            val meanAmb = (ambientRgb.first + ambientRgb.second + ambientRgb.third) / 3f
            gainR = (meanAmb / ambientRgb.first).coerceIn(0.86f, 1.14f)
            gainG = (meanAmb / ambientRgb.second).coerceIn(0.86f, 1.14f)
            gainB = (meanAmb / ambientRgb.third).coerceIn(0.86f, 1.14f)
        }

        val skinPixels = ArrayList<Int>(1024)
        // Stage 1: strict YCbCr skin detector inside ROI (preserves daylight calibration)
        for (y in y0 until y1) {
            if (eyeBandTop in 0 until eyeBandBottom && y in eyeBandTop..eyeBandBottom) continue
            val rowOff = y * width
            for (x in x0 until x1) {
                val px = pixels[rowOff + x]
                if (((px ushr 24) and 0xFF) < 128) continue
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                if (isLikelySkinPixel(r, g, b)) skinPixels.add(px)
            }
        }

        // Stage 2: ambient-compensated + relaxed chrominance for deep skin / shadow / cast light
        if (skinPixels.size < 25) {
            for (y in y0 until y1) {
                if (eyeBandTop in 0 until eyeBandBottom && y in eyeBandTop..eyeBandBottom) continue
                val rowOff = y * width
                for (x in x0 until x1) {
                    val px = pixels[rowOff + x]
                    if (((px ushr 24) and 0xFF) < 128) continue
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    val cr = (r * gainR).toInt().coerceIn(0, 255)
                    val cg = (g * gainG).toInt().coerceIn(0, 255)
                    val cb = (b * gainB).toInt().coerceIn(0, 255)
                    if (!isLikelySkinPixel(r, g, b) && isRelaxedSkinPixel(cr, cg, cb)) {
                        skinPixels.add(px)
                    }
                }
            }
        }

        // Stage 3: focused inner face ROI (excluding eye band) filtering extreme black/white
        val fx0 = maxOf(x0, (width * 0.28f).toInt()).coerceIn(0, width - 1)
        val fx1 = minOf(x1, (width * 0.72f).toInt()).coerceIn(fx0 + 1, width)
        val fy0 = maxOf(y0, (height * 0.12f).toInt()).coerceIn(0, height - 1)
        val fy1 = minOf(y1, (height * 0.56f).toInt()).coerceIn(fy0 + 1, height)
        if (skinPixels.size < 16) {
            for (y in fy0 until fy1 step 2) {
                if (eyeBandTop in 0 until eyeBandBottom && y in eyeBandTop..eyeBandBottom) continue
                val rowOff = y * width
                for (x in fx0 until fx1 step 2) {
                    val px = pixels[rowOff + x]
                    if (((px ushr 24) and 0xFF) < 128) continue
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    val lum = 0.299f * r + 0.587f * g + 0.114f * b
                    if (lum in 18f..246f) skinPixels.add(px)
                }
            }
        }

        // Stage 4: ultimate focused inner ROI fallback so any valid decoded bitmap succeeds
        if (skinPixels.isEmpty()) {
            for (y in fy0 until fy1 step 2) {
                val rowOff = y * width
                for (x in fx0 until fx1 step 2) {
                    val px = pixels[rowOff + x]
                    if (((px ushr 24) and 0xFF) >= 128) skinPixels.add(px)
                }
            }
        }
        if (skinPixels.isEmpty()) {
            for (i in pixels.indices step 4) {
                if (((pixels[i] ushr 24) and 0xFF) > 0) skinPixels.add(pixels[i])
            }
        }
        return skinPixels
    }

    private fun isRelaxedSkinPixel(r: Int, g: Int, b: Int): Boolean {
        if (r <= 32 || g <= 18 || b <= 10 || r < g * 0.88f || r < b * 0.84f) return false
        val cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
        val cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
        return cb in 68.0..142.0 && cr in 122.0..188.0
    }

    fun analyzeSkinSamples(
        skinRgb: List<Int>,
        ambientRgb: Triple<Int, Int, Int>? = null,
        darkFeatureL: Float? = null,
        faceDetected: Boolean = true,
        lang: AppLanguage = AppLanguage.TR
    ): PersonalColorProfile {
        if (skinRgb.isEmpty()) {
            return profileForSeason(ColorSeason.SOFT_AUTUMN, 0xFFCDB99C, faceDetected, FacialContrast.MEDIUM, lang)
        }

        // Clamped Gray-World white balance (±14% gain cap prevents lighting washout)
        var gainR = 1f
        var gainG = 1f
        var gainB = 1f
        if (ambientRgb != null && ambientRgb.first > 30 && ambientRgb.second > 30 && ambientRgb.third > 30) {
            val meanAmb = (ambientRgb.first + ambientRgb.second + ambientRgb.third) / 3f
            gainR = (meanAmb / ambientRgb.first).coerceIn(0.86f, 1.14f)
            gainG = (meanAmb / ambientRgb.second).coerceIn(0.86f, 1.14f)
            gainB = (meanAmb / ambientRgb.third).coerceIn(0.86f, 1.14f)
        }

        val labs = ArrayList<LabColor>(skinRgb.size)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        for (px in skinRgb) {
            val r = (((px shr 16) and 0xFF) * gainR).toInt().coerceIn(0, 255)
            val g = (((px shr 8) and 0xFF) * gainG).toInt().coerceIn(0, 255)
            val b = ((px and 0xFF) * gainB).toInt().coerceIn(0, 255)
            val lab = rgbToLab(r, g, b)
            if (lab.l in 16f..92f) {
                labs.add(lab)
                sumR += r
                sumG += g
                sumB += b
            }
        }
        val safeLabs = if (labs.isNotEmpty()) labs else skinRgb.map {
            rgbToLab((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF)
        }

        val cnt = maxOf(1, labs.size)
        val avgR = if (labs.isNotEmpty()) (sumR / cnt).toInt().coerceIn(0, 255) else 200
        val avgG = if (labs.isNotEmpty()) (sumG / cnt).toInt().coerceIn(0, 255) else 170
        val avgB = if (labs.isNotEmpty()) (sumB / cnt).toInt().coerceIn(0, 255) else 150
        val avgHex = (0xFFL shl 24) or (avgR.toLong() shl 16) or (avgG.toLong() shl 8) or avgB.toLong()

        val lMed = trimmedMean(safeLabs.map { it.l })
        val aMed = trimmedMean(safeLabs.map { it.a })
        val bMed = trimmedMean(safeLabs.map { it.b })

        val chroma = hypot(aMed.toDouble(), bMed.toDouble()).toFloat()
        var hueDeg = (atan2(bMed.toDouble(), aMed.toDouble()) * 180.0 / PI).toFloat()
        if (hueDeg < 0f) hueDeg += 360f

        val depth = when {
            lMed >= 68f -> SkinDepth.FAIR
            lMed >= 52f -> SkinDepth.MEDIUM
            else -> SkinDepth.DEEP
        }

        val bMinusA = bMed - aMed
        val undertone = when {
            hueDeg < 45f || bMinusA < -0.5f -> SkinUndertone.COOL
            hueDeg > 59.5f && hueDeg <= 80f && bMinusA >= 7.5f && aMed >= 5.0f -> SkinUndertone.WARM
            else -> SkinUndertone.NEUTRAL
        }

        val facialDelta = if (darkFeatureL != null) {
            (lMed - darkFeatureL).coerceIn(0f, 80f)
        } else {
            when (depth) {
                SkinDepth.DEEP -> 27f
                SkinDepth.MEDIUM -> 24f
                SkinDepth.FAIR -> 22f
            }
        }
        val contrast = when {
            facialDelta >= 36f || chroma >= 25f -> FacialContrast.HIGH
            facialDelta < 22f && chroma < 19f -> FacialContrast.SOFT
            else -> FacialContrast.MEDIUM
        }

        val season = when (undertone) {
            SkinUndertone.WARM -> when (depth) {
                SkinDepth.FAIR -> when {
                    contrast == FacialContrast.HIGH -> ColorSeason.BRIGHT_SPRING
                    contrast == FacialContrast.SOFT || chroma < 20f -> ColorSeason.LIGHT_SPRING
                    else -> ColorSeason.TRUE_SPRING
                }
                SkinDepth.MEDIUM -> when (contrast) {
                    FacialContrast.SOFT -> ColorSeason.SOFT_AUTUMN
                    FacialContrast.HIGH -> ColorSeason.TRUE_SPRING
                    FacialContrast.MEDIUM -> ColorSeason.TRUE_AUTUMN
                }
                SkinDepth.DEEP -> ColorSeason.DEEP_AUTUMN
            }
            SkinUndertone.COOL -> when (depth) {
                SkinDepth.FAIR -> when (contrast) {
                    FacialContrast.HIGH -> ColorSeason.BRIGHT_WINTER
                    FacialContrast.SOFT -> ColorSeason.LIGHT_SUMMER
                    FacialContrast.MEDIUM -> ColorSeason.TRUE_SUMMER
                }
                SkinDepth.MEDIUM -> when (contrast) {
                    FacialContrast.HIGH -> ColorSeason.TRUE_WINTER
                    FacialContrast.SOFT -> ColorSeason.SOFT_SUMMER
                    FacialContrast.MEDIUM -> ColorSeason.TRUE_SUMMER
                }
                SkinDepth.DEEP -> if (contrast == FacialContrast.HIGH) ColorSeason.TRUE_WINTER else ColorSeason.DEEP_WINTER
            }
            SkinUndertone.NEUTRAL -> when (depth) {
                SkinDepth.FAIR -> when {
                    contrast == FacialContrast.HIGH -> ColorSeason.BRIGHT_SPRING
                    hueDeg >= 53f -> ColorSeason.LIGHT_SPRING
                    else -> ColorSeason.LIGHT_SUMMER
                }
                SkinDepth.MEDIUM -> when {
                    contrast == FacialContrast.HIGH -> ColorSeason.BRIGHT_WINTER
                    hueDeg >= 53f -> ColorSeason.SOFT_AUTUMN
                    else -> ColorSeason.SOFT_SUMMER
                }
                SkinDepth.DEEP -> if (hueDeg >= 54f) ColorSeason.DEEP_AUTUMN else ColorSeason.DEEP_WINTER
            }
        }

        return profileForSeason(season, avgHex, faceDetected, contrast, lang, undertone, depth, hueDeg)
    }

    fun profileForSeason(
        season: ColorSeason,
        averageSkinHex: Long,
        faceDetected: Boolean,
        contrast: FacialContrast,
        lang: AppLanguage,
        overrideUndertone: SkinUndertone? = null,
        overrideDepth: SkinDepth? = null,
        labHueDeg: Float = 54f
    ): PersonalColorProfile {
        val (flattering, avoid) = seasonPalettes(season)
        val undertone = overrideUndertone ?: season.undertone
        val depth = overrideDepth ?: season.depth
        return PersonalColorProfile(
            averageSkinHex = averageSkinHex,
            undertone = undertone,
            depth = depth,
            faceDetected = faceDetected,
            seasonTitle = season.shortLabel(lang),
            summaryText = seasonSummary(season, lang),
            flatteringColors = flattering,
            avoidColors = avoid,
            season = season,
            contrast = contrast,
            labHueDeg = labHueDeg
        )
    }

    private fun seasonPalettes(season: ColorSeason): Pair<List<GarmentColor>, List<GarmentColor>> = when (season) {
        ColorSeason.LIGHT_SPRING -> listOf(
            GarmentColor.CHAMPAGNE, GarmentColor.ECRU, GarmentColor.SAND, GarmentColor.CORAL,
            GarmentColor.MINT, GarmentColor.SKY, GarmentColor.TURQUOISE, GarmentColor.BEIGE,
            GarmentColor.CAMEL, GarmentColor.SAGE, GarmentColor.BLUSH, GarmentColor.YELLOW
        ) to listOf(GarmentColor.BLACK, GarmentColor.PLUM, GarmentColor.BURGUNDY)

        ColorSeason.TRUE_SPRING -> listOf(
            GarmentColor.CORAL, GarmentColor.ORANGE, GarmentColor.YELLOW, GarmentColor.TERRACOTTA,
            GarmentColor.TURQUOISE, GarmentColor.GREEN, GarmentColor.CAMEL, GarmentColor.ECRU,
            GarmentColor.MINT, GarmentColor.DENIM, GarmentColor.OCHRE, GarmentColor.CHOCOLATE
        ) to listOf(GarmentColor.BLACK, GarmentColor.LIGHT_GRAY, GarmentColor.PLUM)

        ColorSeason.BRIGHT_SPRING -> listOf(
            GarmentColor.RED, GarmentColor.CORAL, GarmentColor.COBALT, GarmentColor.TURQUOISE,
            GarmentColor.EMERALD, GarmentColor.YELLOW, GarmentColor.ECRU, GarmentColor.BLACK,
            GarmentColor.PINK, GarmentColor.TEAL, GarmentColor.TERRACOTTA, GarmentColor.WHITE
        ) to listOf(GarmentColor.STONE, GarmentColor.GRAY, GarmentColor.ROSEWOOD)

        ColorSeason.LIGHT_SUMMER -> listOf(
            GarmentColor.ICE_BLUE, GarmentColor.LAVENDER, GarmentColor.BLUSH, GarmentColor.SKY,
            GarmentColor.MINT, GarmentColor.LIGHT_GRAY, GarmentColor.ROSEWOOD, GarmentColor.WHITE,
            GarmentColor.DENIM, GarmentColor.SLATE_BLUE, GarmentColor.PINK, GarmentColor.SAGE
        ) to listOf(GarmentColor.ORANGE, GarmentColor.MUSTARD, GarmentColor.RUST)

        ColorSeason.TRUE_SUMMER -> listOf(
            GarmentColor.NAVY, GarmentColor.SLATE_BLUE, GarmentColor.ICE_BLUE, GarmentColor.LAVENDER,
            GarmentColor.BURGUNDY, GarmentColor.ROSEWOOD, GarmentColor.TEAL, GarmentColor.WHITE,
            GarmentColor.GRAY, GarmentColor.BLUSH, GarmentColor.PLUM, GarmentColor.DENIM
        ) to listOf(GarmentColor.ORANGE, GarmentColor.OCHRE, GarmentColor.TERRACOTTA)

        ColorSeason.SOFT_SUMMER -> listOf(
            GarmentColor.SAGE, GarmentColor.SLATE_BLUE, GarmentColor.ROSEWOOD, GarmentColor.LAVENDER,
            GarmentColor.GRAY, GarmentColor.STONE, GarmentColor.DENIM, GarmentColor.PLUM,
            GarmentColor.TEAL, GarmentColor.ECRU, GarmentColor.CHARCOAL, GarmentColor.BLUSH
        ) to listOf(GarmentColor.ORANGE, GarmentColor.YELLOW, GarmentColor.BLACK)

        ColorSeason.SOFT_AUTUMN -> listOf(
            GarmentColor.OLIVE, GarmentColor.SAGE, GarmentColor.BEIGE, GarmentColor.SAND,
            GarmentColor.STONE, GarmentColor.CAMEL, GarmentColor.TERRACOTTA, GarmentColor.ROSEWOOD,
            GarmentColor.TEAL, GarmentColor.BROWN, GarmentColor.ECRU, GarmentColor.DENIM
        ) to listOf(GarmentColor.COBALT, GarmentColor.LIGHT_GRAY, GarmentColor.WHITE)

        ColorSeason.TRUE_AUTUMN -> listOf(
            GarmentColor.RUST, GarmentColor.TERRACOTTA, GarmentColor.OLIVE, GarmentColor.MUSTARD,
            GarmentColor.CAMEL, GarmentColor.FOREST, GarmentColor.OCHRE, GarmentColor.CHOCOLATE,
            GarmentColor.BROWN, GarmentColor.ECRU, GarmentColor.TEAL, GarmentColor.WINE
        ) to listOf(GarmentColor.ICE_BLUE, GarmentColor.LIGHT_GRAY, GarmentColor.PINK)

        ColorSeason.DEEP_AUTUMN -> listOf(
            GarmentColor.CHOCOLATE, GarmentColor.RUST, GarmentColor.TERRACOTTA, GarmentColor.FOREST,
            GarmentColor.OLIVE, GarmentColor.OCHRE, GarmentColor.WINE, GarmentColor.BURGUNDY,
            GarmentColor.PETROL, GarmentColor.CAMEL, GarmentColor.ECRU, GarmentColor.BLACK
        ) to listOf(GarmentColor.ICE_BLUE, GarmentColor.BLUSH, GarmentColor.LIGHT_GRAY)

        ColorSeason.DEEP_WINTER -> listOf(
            GarmentColor.BLACK, GarmentColor.WHITE, GarmentColor.BURGUNDY, GarmentColor.WINE,
            GarmentColor.EMERALD, GarmentColor.COBALT, GarmentColor.PLUM, GarmentColor.NAVY,
            GarmentColor.PETROL, GarmentColor.RED, GarmentColor.CHARCOAL, GarmentColor.ICE_BLUE
        ) to listOf(GarmentColor.ORANGE, GarmentColor.CAMEL, GarmentColor.SAND)

        ColorSeason.TRUE_WINTER -> listOf(
            GarmentColor.BLACK, GarmentColor.WHITE, GarmentColor.COBALT, GarmentColor.RED,
            GarmentColor.EMERALD, GarmentColor.ICE_BLUE, GarmentColor.NAVY, GarmentColor.BURGUNDY,
            GarmentColor.PURPLE, GarmentColor.CHARCOAL, GarmentColor.PINK, GarmentColor.TEAL
        ) to listOf(GarmentColor.OCHRE, GarmentColor.CAMEL, GarmentColor.RUST)

        ColorSeason.BRIGHT_WINTER -> listOf(
            GarmentColor.BLACK, GarmentColor.WHITE, GarmentColor.COBALT, GarmentColor.EMERALD,
            GarmentColor.RED, GarmentColor.PINK, GarmentColor.PURPLE, GarmentColor.TURQUOISE,
            GarmentColor.ICE_BLUE, GarmentColor.NAVY, GarmentColor.WINE, GarmentColor.YELLOW
        ) to listOf(GarmentColor.BEIGE, GarmentColor.CAMEL, GarmentColor.STONE)
    }

    private fun seasonSummary(season: ColorSeason, lang: AppLanguage): String = when (season) {
        ColorSeason.LIGHT_SPRING -> localizedText(lang, "Açık, sıcak ve taze tonlar yüzünü canlandırır; şampanya, mercan, mint ve açık camel idealdir.", "Light, warm, airy shades brighten your complexion; champagne, coral, mint, and light camel shine.")
        ColorSeason.TRUE_SPRING -> localizedText(lang, "Sıcak ve canlı tonlar enerjini öne çıkarır; kiremit, turkuaz, hardal, yeşil ve camel çok yakışır.", "Warm golden hues flatter your natural glow; terracotta, turquoise, coral, and camel stand out.")
        ColorSeason.BRIGHT_SPRING -> localizedText(lang, "Yüksek kontrastlı sıcak ve parlak renkler seni ön plana çıkarır; saks mavi, mercan ve net ekru dengelidir.", "Crisp high-clarity warm colors suit your contrast; cobalt, coral, emerald, and sharp ecru pop.")
        ColorSeason.LIGHT_SUMMER -> localizedText(lang, "Soğuk, pastel ve yumuşak tonlar cildini duru gösterir; buz mavisi, lavanta, pudra ve gül kurusu öne çıkar.", "Cool pastel tones soften your features; ice blue, lavender, blush, and misty rose look effortless.")
        ColorSeason.TRUE_SUMMER -> localizedText(lang, "Serin ve dengeli tonlar tenine berraklık katar; lacivert, arduvaz mavi, bordo ve net beyaz idealdir.", "Cool, refined shades clarify your skin; navy, slate blue, burgundy, and cool rosewood excel.")
        ColorSeason.SOFT_SUMMER -> localizedText(lang, "Yumuşak, puslu ve nötr-soğuk renkler yüzünle bütünleşir; adaçayı, arduvaz mavi, gül kurusu ve taş tonları harikadır.", "Muted cool-neutral tones blend harmoniously with your skin; sage, slate, taupe, and plum work best.")
        ColorSeason.SOFT_AUTUMN -> localizedText(lang, "Zeytin, adaçayı, bej, kiremit ve sıcak taş tonları nötr-sıcak dengeni mükemmel vurgular.", "Muted earthy shades like olive, sage, sand, terracotta, and teal bring out your balanced warmth.")
        ColorSeason.TRUE_AUTUMN -> localizedText(lang, "Zengin toprak ve baharat tonları yüzünü ısıtır; pas turuncu, haki, çikolata ve safran çok güçlü durur.", "Rich spiced earth tones harmonize effortlessly; rust, forest green, mustard, and chocolate radiate.")
        ColorSeason.DEEP_AUTUMN -> localizedText(lang, "Derin, yoğun ve sıcak tonlar yüksek kontrastını taşır; şarap bordo, petrol, çikolata ve orman yeşili idealdir.", "Deep warm pigments anchor your rich complexion; wine, chocolate, forest, and petrol look regal.")
        ColorSeason.DEEP_WINTER -> localizedText(lang, "Soğuk, derin ve net renkler cildini parlatır; siyah, net beyaz, bordo, saks mavi ve zümrüt çok şıktır.", "Rich jewel tones and crisp neutrals suit you best; black, crisp white, emerald, and wine shine.")
        ColorSeason.TRUE_WINTER -> localizedText(lang, "Keskin, soğuk ve yüksek kontrastlı tonlar senin içindir; net siyah-beyaz, kobalt mavi, kırmızı ve buz mavisi öne çıkar.", "Pure icy cool shades amplify your clarity; cobalt, true red, black, crisp white, and ice blue win.")
        ColorSeason.BRIGHT_WINTER -> localizedText(lang, "Net, parlak ve soğuk/nötr tonlar doğal ışıltını öne çıkarır; kobalt, zümrüt, turkuaz ve siyah kontrastı parlatır.", "Electric jewel tones balanced with sharp blacks and whites showcase your crisp facial contrast.")
    }

    private fun trimmedMean(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val n = sorted.size
        if (n < 8) return sorted[n / 2]
        val start = n / 4
        val end = ((n * 3) / 4).coerceAtLeast(start + 1)
        var acc = 0f
        for (i in start until end) acc += sorted[i]
        return acc / (end - start)
    }

    private fun rgbToLab(r: Int, g: Int, b: Int): LabColor {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val lr = lin(r)
        val lg = lin(g)
        val lb = lin(b)
        val x = (lr * 0.4124564 + lg * 0.3575761 + lb * 0.1804375) / 0.95047
        val y = (lr * 0.2126729 + lg * 0.7151522 + lb * 0.0721750) / 1.00000
        val z = (lr * 0.0193339 + lg * 0.1191920 + lb * 0.9503041) / 1.08883
        fun f(t: Double): Double = if (t > 0.008856) t.pow(1.0 / 3.0) else 7.787 * t + 16.0 / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return LabColor(
            l = (116.0 * fy - 16.0).toFloat(),
            a = (500.0 * (fx - fy)).toFloat(),
            b = (200.0 * (fy - fz)).toFloat()
        )
    }

    private fun isLikelySkinPixel(r: Int, g: Int, b: Int): Boolean {
        if (r <= 65 || g <= 32 || b <= 18 || r <= g || r <= b) return false
        val cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
        val cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
        return cb in 77.0..129.0 && cr in 132.0..178.0
    }
}
