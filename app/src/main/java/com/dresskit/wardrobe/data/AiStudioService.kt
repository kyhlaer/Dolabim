package com.dresskit.wardrobe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.dresskit.wardrobe.ui.theme.STUDIO_ECOMMERCE_PROMPT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max
import kotlin.math.min

/**
 * Optional Gemini Image Editing client (`gemini-nano-banana-2.1` via Interactions API)
 * for photorealistic e-commerce flat-lay product photography and 3D virtual try-on.
 * Users may supply their own Gemini API key in Settings; no API keys are embedded or backed up.
 */
object AiStudioService {

    private const val PREFS_NAME = "dolabim_ai_prefs"
    private const val KEY_GEMINI_API_KEY = "user_gemini_api_key"
    private const val GEMINI_INTERACTIONS_URL =
        "https://generativelanguage.googleapis.com/v1beta/interactions"
    private const val GEMINI_HOST = "generativelanguage.googleapis.com"
    private const val GEMINI_IMAGE_MODEL = "gemini-nano-banana-2.1"
    private const val API_REVISION = "2026-05-20"
    private const val MAX_RESPONSE_CHARS = 8_000_000

    private val OUTPUT_IMAGE_DATA_PATTERN = Pattern.compile(
        """"output_image"\s*:\s*\{[^{}]{0,400}?"data"\s*:\s*"([A-Za-z0-9+/=\r\n\s]{32,})"""",
        Pattern.DOTALL
    )
    private val TYPE_IMAGE_THEN_DATA_PATTERN = Pattern.compile(
        """"type"\s*:\s*"image"[^{}]{0,300}?"data"\s*:\s*"([A-Za-z0-9+/=\r\n\s]{32,})"""",
        Pattern.DOTALL
    )
    private val DATA_THEN_TYPE_IMAGE_PATTERN = Pattern.compile(
        """"data"\s*:\s*"([A-Za-z0-9+/=\r\n\s]{32,})"[^{}]{0,300}?"type"\s*:\s*"image"""",
        Pattern.DOTALL
    )

    fun hasApiKey(context: Context): Boolean = getApiKey(context).isNotBlank()

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GEMINI_API_KEY, "")?.trim() ?: ""
    }

    fun setApiKey(context: Context, key: String) {
        val clean = key.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (clean.isEmpty()) {
            prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
        } else {
            prefs.edit().putString(KEY_GEMINI_API_KEY, clean).apply()
        }
    }

    fun clearApiKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    suspend fun generateEcommerceFlatLay(
        context: Context,
        sourceBitmap: Bitmap
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))
        }
        val b64 = encodeBitmapJpegBase64(sourceBitmap, maxDim = 1024, quality = 88)
        val inputArray = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", STUDIO_ECOMMERCE_PROMPT))
            put(
                JSONObject()
                    .put("type", "image")
                    .put("mime_type", "image/jpeg")
                    .put("data", b64)
            )
        }
        val payload = JSONObject().apply {
            put("model", GEMINI_IMAGE_MODEL)
            put("input", inputArray)
            put(
                "response_format",
                JSONObject()
                    .put("type", "image")
                    .put("mime_type", "image/jpeg")
                    .put("aspect_ratio", "1:1")
            )
        }
        postImageInteraction(apiKey, payload)
    }

    suspend fun generateVirtualTryOn3D(
        context: Context,
        bodyBitmap: Bitmap,
        garments: List<Pair<String, Bitmap>>
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))
        }
        if (garments.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No garments selected"))
        }

        val limitedGarments = garments.take(4)
        val garmentListText = limitedGarments.mapIndexed { idx, (label, _) ->
            "Reference garment ${idx + 1}: $label"
        }.joinToString("; ")

        val tryOnPrompt =
            "Virtual 3D try-on synthesis. The first attached image shows the person. " +
                "The following images show the garments ($garmentListText). " +
                "Dress the person in the first image wearing these exact garments seamlessly integrated onto their body in 3D with natural fabric drape, realistic folds, shadows, sleeves, collar fit, and body contour occlusion. " +
                "Strictly preserve the person's exact face, facial identity, hair, skin tone, body proportions, pose, hands, camera perspective, ambient lighting, and background without altering them. " +
                "Preserve the exact garment colors, prints, logos, graphics, and textures."

        val inputArray = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", tryOnPrompt))
            put(
                JSONObject()
                    .put("type", "image")
                    .put("mime_type", "image/jpeg")
                    .put("data", encodeBitmapJpegBase64(bodyBitmap, maxDim = 960, quality = 86))
            )
            limitedGarments.forEach { (_, garmentBmp) ->
                put(
                    JSONObject()
                        .put("type", "image")
                        .put("mime_type", "image/jpeg")
                        .put("data", encodeBitmapJpegBase64(garmentBmp, maxDim = 768, quality = 85))
                )
            }
        }

        val payload = JSONObject().apply {
            put("model", GEMINI_IMAGE_MODEL)
            put("input", inputArray)
            put(
                "response_format",
                JSONObject()
                    .put("type", "image")
                    .put("mime_type", "image/jpeg")
                    .put("aspect_ratio", "3:4")
            )
        }
        postImageInteraction(apiKey, payload)
    }

    private fun postImageInteraction(apiKey: String, payload: JSONObject): Result<Bitmap> {
        if (!SecurityGuards.isSafePublicHttpsUrl(GEMINI_INTERACTIONS_URL, maxLen = 500) ||
            !SecurityGuards.isPublicHostResolved(GEMINI_HOST)
        ) {
            return Result.failure(SecurityException("Blocked unsafe host"))
        }

        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(GEMINI_INTERACTIONS_URL).openConnection() as? HttpsURLConnection)?.apply {
                connectTimeout = 15_000
                readTimeout = 90_000
                instanceFollowRedirects = false
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("x-goog-api-key", apiKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Api-Revision", API_REVISION)
            } ?: return Result.failure(IllegalStateException("HTTPS connection unavailable"))

            val bodyBytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
            conn.outputStream.use { os -> os.write(bodyBytes) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < MAX_RESPONSE_CHARS) {
                    val read = reader.read(buf, 0, minOf(buf.size, MAX_RESPONSE_CHARS - sb.length))
                    if (read <= 0) break
                    sb.append(buf, 0, read)
                }
                sb.toString()
            }.orEmpty()

            if (code !in 200..299) {
                val safeErrMsg = extractApiErrorMessage(responseText) ?: "HTTP $code"
                return Result.failure(IllegalStateException(safeErrMsg))
            }

            val b64 = extractGeneratedImageBase64(responseText)
                ?: return Result.failure(IllegalStateException("Model returned no image"))
            val decoded = Base64.decode(b64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                ?: return Result.failure(IllegalStateException("Generated image decode failed"))
            Result.success(bmp)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    internal fun extractGeneratedImageBase64(jsonBody: String): String? {
        if (jsonBody.isBlank()) return null
        val patterns = listOf(
            OUTPUT_IMAGE_DATA_PATTERN,
            TYPE_IMAGE_THEN_DATA_PATTERN,
            DATA_THEN_TYPE_IMAGE_PATTERN
        )
        for (pattern in patterns) {
            val m = pattern.matcher(jsonBody)
            if (m.find()) {
                val raw = m.group(1)?.replace(Regex("\\s+"), "")
                if (!raw.isNullOrBlank()) return raw
            }
        }
        return null
    }

    private fun extractApiErrorMessage(jsonBody: String): String? {
        val m = Pattern.compile(""""message"\s*:\s*"([^"]{1,180})"""").matcher(jsonBody)
        return if (m.find()) m.group(1) else null
    }

    private fun encodeBitmapJpegBase64(bitmap: Bitmap, maxDim: Int, quality: Int): String {
        val scale = min(1f, maxDim.toFloat() / max(bitmap.width, bitmap.height).coerceAtLeast(1).toFloat())
        val scaled = if (scale < 0.99f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}
