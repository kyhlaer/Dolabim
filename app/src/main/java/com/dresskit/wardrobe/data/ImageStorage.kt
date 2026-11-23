package com.dresskit.wardrobe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.GarmentColor
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

data class SavedPhotoPair(
    val studioFileName: String,
    val originalFileName: String
)

data class GarmentAnalysisResult(
    val extractedHexes: List<Long>,
    val detectedColors: List<GarmentColor>,
    val predictedSubCategory: ClothingSubCategory,
    val suggestedName: String
)

object ImageStorage {
    private const val PHOTOS_DIR = "photos"
    private const val CAMERA_TEMP_DIR = "camera_temp"
    private const val MAX_DIMENSION = 1440
    private const val JPEG_QUALITY = 90

    private fun photosDir(context: Context): File {
        val dir = File(context.filesDir, PHOTOS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cameraTempDir(context: Context): File {
        val dir = File(context.cacheDir, CAMERA_TEMP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun sanitizeFileName(fileName: String): String? {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty() ||
            trimmed == "." ||
            trimmed == ".." ||
            trimmed.contains("..") ||
            trimmed.contains('/') ||
            trimmed.contains('\\')
        ) {
            return null
        }
        if (!trimmed.matches(Regex("[A-Za-z0-9._-]+"))) return null
        return trimmed
    }

    fun getPhotoFile(context: Context, fileName: String): File {
        val dir = photosDir(context)
        val safe = sanitizeFileName(fileName) ?: return File(dir, "__invalid__")
        val target = File(dir, safe)
        return try {
            val dirCanon = dir.canonicalPath + File.separator
            if (target.canonicalPath.startsWith(dirCanon)) target else File(dir, "__invalid__")
        } catch (_: Exception) {
            File(dir, "__invalid__")
        }
    }

    fun getUserBodyPhotoFile(context: Context): File {
        return File(context.filesDir, "user_body_photo.jpg")
    }

    fun getUserFacePhotoFile(context: Context): File {
        return File(context.filesDir, "user_face_photo.jpg")
    }

    fun cleanStaleCameraTempFiles(context: Context, maxAgeMs: Long = 3_600_000L) {
        try {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            cameraTempDir(context).listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // Non-critical cleanup
        }
    }

    private fun enforceOwnerOnlyPermissions(file: File) {
        runCatching {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
    }

    private fun writeUserPhotoBitmap(bmp: Bitmap, outFile: File): Boolean {
        val tmpFile = File(outFile.parentFile, "${outFile.name}.tmp")
        return try {
            FileOutputStream(tmpFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            }
            enforceOwnerOnlyPermissions(tmpFile)
            if (!tmpFile.renameTo(outFile)) {
                tmpFile.copyTo(outFile, overwrite = true)
                enforceOwnerOnlyPermissions(outFile)
                tmpFile.delete()
            }
            bmp.recycle()
            true
        } catch (_: Exception) {
            tmpFile.delete()
            bmp.recycle()
            false
        }
    }

    fun saveUserBodyPhotoBitmap(context: Context, bmp: Bitmap): Boolean {
        val outFile = getUserBodyPhotoFile(context)
        val tmpFile = File(outFile.parentFile, "${outFile.name}.tmp")
        return try {
            FileOutputStream(tmpFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            }
            enforceOwnerOnlyPermissions(tmpFile)
            if (!tmpFile.renameTo(outFile)) {
                tmpFile.copyTo(outFile, overwrite = true)
                enforceOwnerOnlyPermissions(outFile)
                tmpFile.delete()
            }
            true
        } catch (_: Exception) {
            tmpFile.delete()
            false
        }
    }

    fun saveUserBodyPhotoFromFile(context: Context, tempFile: File): Boolean {
        return try {
            val bmp = decodeOrientedBitmapFromFile(tempFile) ?: return false
            writeUserPhotoBitmap(bmp, getUserBodyPhotoFile(context))
        } finally {
            tempFile.delete()
        }
    }

    fun saveUserBodyPhotoFromUri(context: Context, uri: Uri): Boolean {
        val bmp = decodeOrientedBitmapFromUri(context, uri) ?: return false
        return writeUserPhotoBitmap(bmp, getUserBodyPhotoFile(context))
    }

    fun saveUserFacePhotoFromFile(context: Context, tempFile: File): Boolean {
        return try {
            val bmp = decodeOrientedBitmapFromFile(tempFile) ?: return false
            writeUserPhotoBitmap(bmp, getUserFacePhotoFile(context))
        } finally {
            tempFile.delete()
        }
    }

    fun saveUserFacePhotoFromUri(context: Context, uri: Uri): Boolean {
        val bmp = decodeOrientedBitmapFromUri(context, uri) ?: return false
        return writeUserPhotoBitmap(bmp, getUserFacePhotoFile(context))
    }

    fun decodeOrientedBitmapFromFile(tempFile: File): Bitmap? {
        if (!tempFile.exists() || tempFile.length() == 0L) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decodedModern = runCatching {
                val source = ImageDecoder.createSource(tempFile)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val srcW = info.size.width
                    val srcH = info.size.height
                    val maxSide = max(srcW, srcH)
                    if (maxSide > MAX_DIMENSION && maxSide > 0) {
                        val ratio = MAX_DIMENSION.toFloat() / maxSide.toFloat()
                        decoder.setTargetSize(
                            (srcW * ratio).toInt().coerceAtLeast(1),
                            (srcH * ratio).toInt().coerceAtLeast(1)
                        )
                    }
                }
            }.getOrNull()
            if (decodedModern != null) {
                val scaled = scaleDownIfNeeded(decodedModern)
                if (scaled !== decodedModern) decodedModern.recycle()
                return scaled
            }
        }
        return try {
            val orientation = readExifOrientation(tempFile)
            val bitmap = decodeSampledBitmapFromFile(tempFile.absolutePath) ?: return null
            val rotated = rotateIfNeeded(bitmap, orientation)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeOrientedBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decodedModern = runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val srcW = info.size.width
                    val srcH = info.size.height
                    val maxSide = max(srcW, srcH)
                    if (maxSide > MAX_DIMENSION && maxSide > 0) {
                        val ratio = MAX_DIMENSION.toFloat() / maxSide.toFloat()
                        decoder.setTargetSize(
                            (srcW * ratio).toInt().coerceAtLeast(1),
                            (srcH * ratio).toInt().coerceAtLeast(1)
                        )
                    }
                }
            }.getOrNull()
            if (decodedModern != null) {
                val scaled = scaleDownIfNeeded(decodedModern)
                if (scaled !== decodedModern) decodedModern.recycle()
                return scaled
            }
        }

        // Single-pass temp copy avoids ContentResolver providers that forbid multiple openInputStream calls
        val tempCopy = File(cameraTempDir(context), "pick_${UUID.randomUUID()}.tmp")
        return try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempCopy).use { out ->
                    input.copyTo(out)
                }
            } ?: 0L
            if (copied > 0L) decodeOrientedBitmapFromFile(tempCopy) else null
        } catch (_: Throwable) {
            null
        } finally {
            tempCopy.delete()
        }
    }

    fun saveFromBitmap(context: Context, orientedBitmap: Bitmap): SavedPhotoPair {
        val scaled = scaleDownIfNeeded(orientedBitmap)
        val pair = saveBothModes(context, scaled)
        if (scaled !== orientedBitmap) scaled.recycle()
        return pair
    }

    fun createCameraTempFile(context: Context): Pair<File, Uri> {
        val tempFile = File(cameraTempDir(context), "cam_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return tempFile to uri
    }

    fun saveFromCameraFile(context: Context, tempFile: File): SavedPhotoPair? {
        if (!tempFile.exists() || tempFile.length() == 0L) return null
        return try {
            val orientation = readExifOrientation(tempFile)
            val bitmap = decodeSampledBitmapFromFile(tempFile.absolutePath) ?: return null
            val rotated = rotateIfNeeded(bitmap, orientation)
            if (rotated !== bitmap) bitmap.recycle()

            val pair = saveBothModes(context, rotated)
            rotated.recycle()
            tempFile.delete()
            pair
        } catch (_: Exception) {
            null
        }
    }

    fun saveFromGalleryUri(context: Context, uri: Uri): SavedPhotoPair? {
        val decoded = decodeOrientedBitmapFromUri(context, uri) ?: return null
        return try {
            saveBothModes(context, decoded)
        } finally {
            decoded.recycle()
        }
    }

    fun isOfflineCutoutFileName(fileName: String?): Boolean =
        fileName?.startsWith("bgrm_") == true

    private fun saveBothModes(context: Context, orientedOriginal: Bitmap): SavedPhotoPair {
        val uuid = UUID.randomUUID().toString()
        val origName = "orig_$uuid.jpg"
        val studioName = "bgrm_$uuid.jpg"

        writeCompressedJpeg(context, orientedOriginal, origName)
        val studioBmp = StudioCutoutProcessor.toEcommerceFlatLayShot(context, orientedOriginal)
        writeCompressedJpeg(context, studioBmp, studioName)
        if (studioBmp !== orientedOriginal) studioBmp.recycle()

        return SavedPhotoPair(studioFileName = studioName, originalFileName = origName)
    }

    fun saveNewCutoutVariant(context: Context, cutoutBitmap: Bitmap): String {
        val newCutoutName = "bgrm_${UUID.randomUUID()}.jpg"
        writeCompressedJpeg(context, cutoutBitmap, newCutoutName)
        return newCutoutName
    }

    fun saveNewCatalogVariant(context: Context, catalogBitmap: Bitmap): String {
        val newCatalogName = "catalog_${UUID.randomUUID()}.jpg"
        writeCompressedJpeg(context, catalogBitmap, newCatalogName)
        return newCatalogName
    }

    fun saveNewStudioVariant(context: Context, studioBitmap: Bitmap): String =
        saveNewCatalogVariant(context, studioBitmap)

    fun decodePhotoBitmap(context: Context, fileName: String): Bitmap? {
        val file = getPhotoFile(context, fileName)
        if (!file.exists() || file.length() == 0L) return null
        return decodeOrientedBitmapFromFile(file)
    }

    fun deletePhoto(context: Context, fileName: String) {
        val file = getPhotoFile(context, fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Analyzes the studio-cutout image to predict garment color(s), sub-category, and clean Turkish name.
     */
    fun analyzeGarment(context: Context, fileName: String): GarmentAnalysisResult {
        val file = getPhotoFile(context, fileName)
        if (!file.exists()) {
            return GarmentAnalysisResult(
                extractedHexes = emptyList(),
                detectedColors = emptyList(),
                predictedSubCategory = ClothingSubCategory.TSHIRT,
                suggestedName = "Tişört"
            )
        }
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 6 }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                ?: return GarmentAnalysisResult(emptyList(), emptyList(), ClothingSubCategory.TSHIRT, "Tişört")
            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            bmp.recycle()

            // Identify non-white garment foreground pixels
            val isFg = BooleanArray(w * h)
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            var fgCount = 0

            val bucketCounts = HashMap<Int, Int>()
            val bucketR = HashMap<Int, Long>()
            val bucketG = HashMap<Int, Long>()
            val bucketB = HashMap<Int, Long>()

            for (y in 0 until h) {
                val rowOff = y * w
                for (x in 0 until w) {
                    val idx = rowOff + x
                    val px = pixels[idx]
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    // Foreground = not pure studio white (#FFFFFF)
                    val foreground = !(r >= 246 && g >= 246 && b >= 246)
                    if (foreground) {
                        isFg[idx] = true
                        fgCount++
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y

                        val qr = (r / 20)
                        val qg = (g / 20)
                        val qb = (b / 20)
                        val key = (qr shl 10) or (qg shl 5) or qb
                        bucketCounts[key] = (bucketCounts[key] ?: 0) + 1
                        bucketR[key] = (bucketR[key] ?: 0L) + r
                        bucketG[key] = (bucketG[key] ?: 0L) + g
                        bucketB[key] = (bucketB[key] ?: 0L) + b
                    }
                }
            }

            if (fgCount < 25 || maxX <= minX || maxY <= minY) {
                return GarmentAnalysisResult(
                    extractedHexes = listOf(0xFFFAFAFAL),
                    detectedColors = listOf(GarmentColor.WHITE),
                    predictedSubCategory = ClothingSubCategory.TSHIRT,
                    suggestedName = "Beyaz Tişört"
                )
            }

            // Compute top distinct representative colors
            val sortedBuckets = bucketCounts.entries.sortedByDescending { it.value }
            val pickedHexes = mutableListOf<Long>()
            val detectedColors = mutableListOf<GarmentColor>()

            for (entry in sortedBuckets) {
                val cnt = entry.value.toFloat()
                val avgR = ((bucketR[entry.key] ?: 0L) / cnt).toInt().coerceIn(0, 255)
                val avgG = ((bucketG[entry.key] ?: 0L) / cnt).toInt().coerceIn(0, 255)
                val avgB = ((bucketB[entry.key] ?: 0L) / cnt).toInt().coerceIn(0, 255)
                val rgb = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB

                val tooClose = pickedHexes.any { existing ->
                    colorDistance(existing.toInt(), rgb) < 42.0
                }
                if (!tooClose) {
                    pickedHexes.add(rgb.toLong() and 0xFFFFFFFFL)
                    val matched = matchGarmentColorHsv(avgR, avgG, avgB)
                    if (!detectedColors.contains(matched)) {
                        detectedColors.add(matched)
                    }
                    if (pickedHexes.size == 3) break
                }
            }

            val primaryColor = detectedColors.firstOrNull()
            val predictedSub = predictSubCategory(
                isFg = isFg,
                w = w,
                minX = minX,
                minY = minY,
                maxX = maxX,
                maxY = maxY,
                fgCount = fgCount,
                primaryColor = primaryColor
            )

            val suggestedName = buildAutoName(primaryColor, predictedSub)

            GarmentAnalysisResult(
                extractedHexes = pickedHexes,
                detectedColors = detectedColors.take(2),
                predictedSubCategory = predictedSub,
                suggestedName = suggestedName
            )
        } catch (_: Exception) {
            GarmentAnalysisResult(emptyList(), emptyList(), ClothingSubCategory.TSHIRT, "Tişört")
        }
    }

    fun buildAutoName(color: GarmentColor?, subCategory: ClothingSubCategory): String {
        val colorWord = color?.label?.substringBefore(" /") ?: ""
        val typeWord = when (subCategory) {
            ClothingSubCategory.JEANS -> "Jean Pantolon"
            ClothingSubCategory.CHINO -> "Chino Pantolon"
            ClothingSubCategory.SWEATER -> "Kazak"
            ClothingSubCategory.COAT -> "Mont"
            ClothingSubCategory.LOAFER -> "Klasik Ayakkabı"
            ClothingSubCategory.SLIPPER -> "Terlik"
            ClothingSubCategory.CAP -> "Şapka"
            else -> subCategory.label
        }
        return if (colorWord.isNotBlank()) "$colorWord $typeWord" else typeWord
    }

    private fun predictSubCategory(
        isFg: BooleanArray,
        w: Int,
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
        fgCount: Int,
        primaryColor: GarmentColor?
    ): ClothingSubCategory {
        val boxW = (maxX - minX + 1).toFloat()
        val boxH = (maxY - minY + 1).toFloat()
        val aspectRatio = boxH / boxW // >1 taller, <1 wider
        val fillDensity = fgCount / (boxW * boxH)

        // 1. Wide horizontal profile -> Shoes / Glasses
        if (aspectRatio < 0.78f) {
            return if (fillDensity < 0.35f) ClothingSubCategory.GLASSES else ClothingSubCategory.SNEAKER
        }

        // 2. Check lower-center vertical gap (trousers / jeans / sweatpants / shorts have a leg split!)
        val midXStart = minX + (boxW * 0.43f).toInt()
        val midXEnd = minX + (boxW * 0.57f).toInt()
        val lowerYStart = minY + (boxH * 0.62f).toInt()
        val lowerYEnd = maxY - (boxH * 0.04f).toInt()
        var lowerCenterTotal = 0
        var lowerCenterEmpty = 0
        for (y in lowerYStart..lowerYEnd) {
            val rowOff = y * w
            for (x in midXStart..midXEnd) {
                lowerCenterTotal++
                if (!isFg[rowOff + x]) lowerCenterEmpty++
            }
        }
        val legSplitRatio = if (lowerCenterTotal > 0) lowerCenterEmpty.toFloat() / lowerCenterTotal else 0f

        // Compare top half width vs bottom half width
        val topY = minY + (boxH * 0.25f).toInt()
        val botY = minY + (boxH * 0.80f).toInt()
        var topFgWidth = 0
        var botFgWidth = 0
        for (x in minX..maxX) {
            if (isFg[topY * w + x]) topFgWidth++
            if (isFg[botY * w + x]) botFgWidth++
        }

        // Trousers / Shorts detection
        if (legSplitRatio > 0.46f && topFgWidth >= botFgWidth * 0.7f) {
            return when {
                aspectRatio < 1.15f -> ClothingSubCategory.SHORTS
                primaryColor == GarmentColor.DENIM || primaryColor == GarmentColor.NAVY ||
                    primaryColor == GarmentColor.SKY -> ClothingSubCategory.JEANS
                primaryColor == GarmentColor.LIGHT_GRAY || primaryColor == GarmentColor.GRAY ->
                    ClothingSubCategory.SWEATPANTS
                else -> ClothingSubCategory.PANTS
            }
        }

        // 3. Upper garments: check sleeve span vs torso & hood bump in top 15%
        val hoodEndY = minY + (boxH * 0.14f).toInt()
        var hoodCenterFg = 0
        var hoodTotal = 0
        val hoodXStart = minX + (boxW * 0.38f).toInt()
        val hoodXEnd = minX + (boxW * 0.62f).toInt()
        for (y in minY..hoodEndY) {
            val rowOff = y * w
            for (x in hoodXStart..hoodXEnd) {
                hoodTotal++
                if (isFg[rowOff + x]) hoodCenterFg++
            }
        }
        val hoodFill = if (hoodTotal > 0) hoodCenterFg.toFloat() / hoodTotal else 0f

        // Long sleeves reach far down on sides -> higher fill in middle/bottom-sides
        val sleeveCheckY = minY + (boxH * 0.56f).toInt()
        var sleeveRowFg = 0
        for (x in minX..maxX) {
            if (isFg[sleeveCheckY * w + x]) sleeveRowFg++
        }
        val midWidthRatio = sleeveRowFg / boxW

        return when {
            aspectRatio > 1.28f && fillDensity > 0.56f -> ClothingSubCategory.COAT
            hoodFill > 0.72f && midWidthRatio > 0.62f -> ClothingSubCategory.HOODIE
            midWidthRatio > 0.64f && fillDensity > 0.52f -> ClothingSubCategory.SWEATSHIRT
            aspectRatio < 1.05f -> ClothingSubCategory.TSHIRT
            else -> ClothingSubCategory.SHIRT
        }
    }

    private fun matchGarmentColorHsv(r: Int, g: Int, b: Int): GarmentColor {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0] // 0..360
        val sat = hsv[1] // 0..1
        val valB = hsv[2] // 0..1

        // 1. Low saturation -> achromatic / neutral family
        if (sat < 0.14f) {
            return when {
                valB < 0.20f -> GarmentColor.BLACK
                valB < 0.36f -> GarmentColor.CHARCOAL
                valB < 0.62f -> GarmentColor.GRAY
                valB < 0.84f -> GarmentColor.LIGHT_GRAY
                else -> if (r - b > 8) GarmentColor.ECRU else GarmentColor.WHITE
            }
        }

        // 2. Very dark tones with slight hue (e.g. dark navy vs black vs brown)
        if (valB < 0.22f) {
            return when {
                hue in 195f..255f -> GarmentColor.NAVY
                hue in 15f..45f -> GarmentColor.BROWN
                hue in 325f..360f || hue < 15f -> GarmentColor.BURGUNDY
                else -> GarmentColor.BLACK
            }
        }

        // 3. Soft warm neutrals (Beige / Ecru / Stone / Camel)
        if (sat < 0.32f && hue in 20f..55f) {
            return when {
                valB > 0.86f -> GarmentColor.ECRU
                valB > 0.72f -> GarmentColor.BEIGE
                valB > 0.52f -> GarmentColor.STONE
                else -> GarmentColor.BROWN
            }
        }

        // 4. Chromatic hue buckets for accurate Yellow / Pink / Blush / Blue / Green / Red / Olive detection
        return when {
            hue < 14f || hue >= 348f -> when {
                sat < 0.35f && valB > 0.72f -> GarmentColor.BLUSH
                valB < 0.45f -> GarmentColor.BURGUNDY
                else -> GarmentColor.RED
            }
            hue in 14f..32f -> when {
                valB < 0.46f -> GarmentColor.BROWN
                sat > 0.58f && valB > 0.68f -> GarmentColor.ORANGE
                else -> GarmentColor.TERRACOTTA
            }
            hue in 32f..44f -> when {
                valB < 0.48f -> GarmentColor.BROWN
                sat > 0.55f -> GarmentColor.MUSTARD
                else -> GarmentColor.CAMEL
            }
            hue in 44f..68f -> when {
                valB > 0.66f -> GarmentColor.YELLOW
                sat < 0.42f -> GarmentColor.OLIVE
                else -> GarmentColor.MUSTARD
            }
            hue in 68f..150f -> when {
                sat < 0.32f && valB > 0.55f -> GarmentColor.SAGE
                valB < 0.45f -> GarmentColor.OLIVE
                else -> GarmentColor.GREEN
            }
            hue in 150f..185f -> if (valB > 0.68f && sat < 0.45f) GarmentColor.MINT else GarmentColor.TURQUOISE
            hue in 185f..212f -> if (valB > 0.68f) GarmentColor.SKY else GarmentColor.DENIM
            hue in 212f..255f -> when {
                valB < 0.38f -> GarmentColor.NAVY
                sat > 0.55f -> GarmentColor.COBALT
                else -> GarmentColor.DENIM
            }
            hue in 255f..312f -> GarmentColor.PURPLE
            hue in 312f..348f -> when {
                valB < 0.45f -> GarmentColor.BURGUNDY
                sat < 0.36f && valB > 0.72f -> GarmentColor.BLUSH
                else -> GarmentColor.PINK
            }
            else -> GarmentColor.BURGUNDY
        }
    }

    private fun colorDistance(c1: Int, c2: Int): Double {
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        return sqrt(
            (r1 - r2).toDouble().pow(2.0) +
                (g1 - g2).toDouble().pow(2.0) +
                (b1 - b2).toDouble().pow(2.0)
        )
    }

    private fun readExifOrientation(file: File): Int {
        return try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun decodeSampledBitmapFromFile(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        repeat(4) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
                val scaled = scaleDownIfNeeded(decoded)
                if (scaled !== decoded) decoded.recycle()
                return scaled
            } catch (_: OutOfMemoryError) {
                sample *= 2
            }
        }
        return null
    }

    internal fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0 || maxDim <= 0) return 1
        var inSampleSize = 1L
        val limit = maxDim.toLong() * 2L
        while (width.toLong() / inSampleSize > limit || height.toLong() / inSampleSize > limit) {
            inSampleSize *= 2L
        }
        return inSampleSize.coerceAtMost(256L).toInt().coerceAtLeast(1)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / maxSide.toFloat()
        val newW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun writeCompressedJpeg(context: Context, bitmap: Bitmap, fileName: String) {
        val outFile = getPhotoFile(context, fileName)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
        }
        enforceOwnerOnlyPermissions(outFile)
    }
}
