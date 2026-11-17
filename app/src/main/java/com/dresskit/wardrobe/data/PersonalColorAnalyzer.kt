package com.dresskit.wardrobe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.media.FaceDetector
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.localizedText

enum class SkinUndertone {
    WARM,
    COOL,
    NEUTRAL;

    fun label(lang: AppLanguage): String = when (this) {
        WARM -> localizedText(lang, "Sıcak Alt Ton", "Warm Undertone", "Warmer Unterton", "Sous-ton chaud", "Subtono cálido")
        COOL -> localizedText(lang, "Soğuk Alt Ton", "Cool Undertone", "Kühler Unterton", "Sous-ton froid", "Subtono frío")
        NEUTRAL -> localizedText(lang, "Nötr Alt Ton", "Neutral Undertone", "Neutraler Unterton", "Sous-ton neutre", "Subtono neutro")
    }
}

enum class SkinDepth {
    FAIR,
    MEDIUM,
    DEEP
}

data class PersonalColorProfile(
    val averageSkinHex: Long,
    val undertone: SkinUndertone,
    val depth: SkinDepth,
    val faceDetected: Boolean,
    val seasonTitle: String,
    val summaryText: String,
    val flatteringColors: List<GarmentColor>,
    val avoidColors: List<GarmentColor>
)

/**
 * Analyzes the user's uploaded photo (on-device face detection + skin chrominance sampling)
 * to recommend the most flattering garment colors for their face & skin undertone.
 */
object PersonalColorAnalyzer {

    fun analyzeUserPhoto(context: Context, lang: AppLanguage): PersonalColorProfile? {
        val file = ImageStorage.getUserBodyPhotoFile(context)
        if (!file.exists() || file.length() == 0L) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > 480 || bounds.outHeight / sample > 640) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val rawBmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

        val evenW = (rawBmp.width and -2).coerceAtLeast(2)
        val bmp = if (rawBmp.width != evenW) {
            Bitmap.createBitmap(rawBmp, 0, 0, evenW, rawBmp.height).also { rawBmp.recycle() }
        } else {
            rawBmp
        }

        return try {
            val rgb565 = bmp.copy(Bitmap.Config.RGB_565, false)
            var faceFound = false
            var minX = (bmp.width * 0.28f).toInt()
            var maxX = (bmp.width * 0.72f).toInt()
            var minY = (bmp.height * 0.06f).toInt()
            var maxY = (bmp.height * 0.38f).toInt()

            if (rgb565 != null) {
                val detector = FaceDetector(rgb565.width, rgb565.height, 1)
                val faces = arrayOfNulls<FaceDetector.Face>(1)
                val count = detector.findFaces(rgb565, faces)
                rgb565.recycle()
                val face = if (count > 0) faces[0] else null
                if (face != null && face.eyesDistance() > 4f) {
                    val mid = PointF()
                    face.getMidPoint(mid)
                    val radius = face.eyesDistance() * 0.65f
                    faceFound = true
                    minX = (mid.x - radius).toInt().coerceIn(0, bmp.width - 1)
                    maxX = (mid.x + radius).toInt().coerceIn(minX + 1, bmp.width)
                    minY = (mid.y - radius * 0.45f).toInt().coerceIn(0, bmp.height - 1)
                    maxY = (mid.y + radius * 1.15f).toInt().coerceIn(minY + 1, bmp.height)
                }
            }

            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var skinCount = 0

            for (y in minY until maxY) {
                for (x in minX until maxX) {
                    val px = bmp.getPixel(x, y)
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    if (isLikelySkinPixel(r, g, b)) {
                        sumR += r
                        sumG += g
                        sumB += b
                        skinCount++
                    }
                }
            }

            // Fallback to middle-upper sample if strict chrominance found too few pixels
            if (skinCount < 25) {
                for (y in minY until maxY step 2) {
                    for (x in minX until maxX step 2) {
                        val px = bmp.getPixel(x, y)
                        val r = (px shr 16) and 0xFF
                        val g = (px shr 8) and 0xFF
                        val b = px and 0xFF
                        val lum = 0.299f * r + 0.587f * g + 0.114f * b
                        if (lum in 45f..235f) {
                            sumR += r
                            sumG += g
                            sumB += b
                            skinCount++
                        }
                    }
                }
            }

            if (skinCount == 0) return null

            val avgR = (sumR / skinCount).toInt().coerceIn(0, 255)
            val avgG = (sumG / skinCount).toInt().coerceIn(0, 255)
            val avgB = (sumB / skinCount).toInt().coerceIn(0, 255)
            val avgHex = (0xFFL shl 24) or (avgR.toLong() shl 16) or (avgG.toLong() shl 8) or avgB.toLong()

            val lum = (0.299f * avgR + 0.587f * avgG + 0.114f * avgB) / 255f
            val depth = when {
                lum >= 0.64f -> SkinDepth.FAIR
                lum >= 0.42f -> SkinDepth.MEDIUM
                else -> SkinDepth.DEEP
            }

            val warmthIndex = (avgR - avgB) - (avgG - avgB) * 0.35f
            val undertone = when {
                warmthIndex >= 28f -> SkinUndertone.WARM
                warmthIndex <= 14f -> SkinUndertone.COOL
                else -> SkinUndertone.NEUTRAL
            }

            val flattering = when (undertone) {
                SkinUndertone.WARM -> listOf(
                    GarmentColor.OLIVE,
                    GarmentColor.TERRACOTTA,
                    GarmentColor.CAMEL,
                    GarmentColor.ECRU,
                    GarmentColor.BEIGE,
                    GarmentColor.MUSTARD,
                    GarmentColor.BROWN,
                    GarmentColor.NAVY
                )
                SkinUndertone.COOL -> listOf(
                    GarmentColor.NAVY,
                    GarmentColor.COBALT,
                    GarmentColor.BURGUNDY,
                    GarmentColor.WHITE,
                    GarmentColor.BLACK,
                    GarmentColor.GRAY,
                    GarmentColor.BLUSH,
                    GarmentColor.MINT
                )
                SkinUndertone.NEUTRAL -> listOf(
                    GarmentColor.ECRU,
                    GarmentColor.SAGE,
                    GarmentColor.DENIM,
                    GarmentColor.NAVY,
                    GarmentColor.CHARCOAL,
                    GarmentColor.BURGUNDY,
                    GarmentColor.BEIGE,
                    GarmentColor.TURQUOISE
                )
            }

            val avoid = when (undertone) {
                SkinUndertone.WARM -> listOf(GarmentColor.LIGHT_GRAY, GarmentColor.PINK)
                SkinUndertone.COOL -> listOf(GarmentColor.ORANGE, GarmentColor.YELLOW)
                SkinUndertone.NEUTRAL -> listOf(GarmentColor.YELLOW)
            }

            val seasonTitle = when (undertone) {
                SkinUndertone.WARM -> when (depth) {
                    SkinDepth.FAIR -> localizedText(lang, "İlkbahar · Sıcak & Aydınlık", "Spring · Warm & Clear", "Frühling · Warm & Klar", "Printemps · Chaud", "Primavera · Cálida")
                    else -> localizedText(lang, "Sonbahar · Sıcak Toprak Paleti", "Autumn · Warm Earth Palette", "Herbst · Warme Erdtöne", "Automne · Tons Chauds", "Otoño · Tonos Tierra")
                }
                SkinUndertone.COOL -> when (depth) {
                    SkinDepth.DEEP -> localizedText(lang, "Kış · Soğuk & Yüksek Kontrast", "Winter · Cool High Contrast", "Winter · Kühl & Kontrastreich", "Hiver · Froid & Contrasté", "Invierno · Frío Intenso")
                    else -> localizedText(lang, "Yaz · Soğuk & Dengeli Tonlar", "Summer · Cool & Soft Tones", "Sommer · Kühle Töne", "Été · Tons Froids", "Verano · Tonos Fríos")
                }
                SkinUndertone.NEUTRAL -> localizedText(
                    lang,
                    "Dengeli Nötr · Yumuşak Palet",
                    "Balanced Neutral · Versatile Palette",
                    "Neutral · Vielseitige Palette",
                    "Neutre · Palette Polyvalente",
                    "Neutro · Paleta Versátil"
                )
            }

            val summary = when (undertone) {
                SkinUndertone.WARM -> localizedText(
                    lang,
                    "Ten alt tonun sıcak görünüyor; haki, kiremit, ekru, camel ve zeytin tonları yüzünü daha canlı gösterir.",
                    "Your skin leans warm; olive, terracotta, ecru, camel, and earth tones bring out natural radiance.",
                    "Dein Hautunterton ist warm; Oliv, Terrakotta, Ecru und Camel lassen deinen Teint strahlen.",
                    "Votre sous-ton est chaud ; l'olive, le terracotta, l'écru et le camel subliment votre teint.",
                    "Tu subtono es cálido; el oliva, terracota, crudo y camel iluminan tu rostro."
                )
                SkinUndertone.COOL -> localizedText(
                    lang,
                    "Ten alt tonun soğuk görünüyor; lacivert, bordo, net beyaz, saks mavi ve gri tonları cildini daha parlak gösterir.",
                    "Your skin leans cool; navy, burgundy, crisp white, cobalt, and soft blush complement your complexion.",
                    "Dein Hautunterton ist kühl; Marineblau, Bordeaux, Weiß und Kobalt stehen dir besonders gut.",
                    "Votre sous-ton est froid ; le marine, le bordeaux, le blanc pur et le cobalt vous vont à ravir.",
                    "Tu subtono es frío; el marino, burdeos, blanco puro y cobalto favorecen tu piel."
                )
                SkinUndertone.NEUTRAL -> localizedText(
                    lang,
                    "Alt tonun nötr ve dengeli; adaçayı, denim mavi, ekru, antrasit ve bordo gibi çoğu tonu rahatlıkla taşırsın.",
                    "Your undertone is balanced neutral; sage, denim blue, ecru, charcoal, and burgundy look effortless on you.",
                    "Dein Unterton ist neutral; Salbei, Denim, Ecru und Anthrazit passen perfekt zu dir.",
                    "Votre sous-ton est neutre ; sauge, bleu denim, écru et anthracite s'accordent facilement.",
                    "Tu subtono es neutro; salvia, azul vaquero, crudo y antracita te sientan de maravilla."
                )
            }

            PersonalColorProfile(
                averageSkinHex = avgHex,
                undertone = undertone,
                depth = depth,
                faceDetected = faceFound,
                seasonTitle = seasonTitle,
                summaryText = summary,
                flatteringColors = flattering,
                avoidColors = avoid
            )
        } catch (_: Exception) {
            null
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    private fun isLikelySkinPixel(r: Int, g: Int, b: Int): Boolean {
        if (r <= 80 || g <= 38 || b <= 22 || r <= g || r <= b) return false
        val cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
        val cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
        return cb in 77.0..128.0 && cr in 133.0..178.0
    }
}
