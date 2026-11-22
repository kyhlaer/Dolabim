package com.dresskit.wardrobe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max
import kotlin.math.min

/**
 * Generative AI Studio & 3D Virtual Try-On service:
 * 1) Optional user-supplied Gemini API key (`gemini-nano-banana-2.1` via Interactions API)
 * 2) Zero-key free cloud image-to-image catalog flat-lay synthesis across verified Hugging Face Gradio Spaces
 *    (with optional user-supplied free Hugging Face token for extended ZeroGPU quota).
 * Never stores or backs up user keys/tokens in backups or exports.
 */
object AiStudioService {

    private const val PREFS_NAME = "dolabim_ai_prefs"
    private const val KEY_GEMINI_API_KEY = "user_gemini_api_key"
    private const val KEY_HF_TOKEN = "user_hf_token"

    private const val GEMINI_INTERACTIONS_V1_URL =
        "https://generativelanguage.googleapis.com/v1/interactions"
    private const val GEMINI_INTERACTIONS_BETA_URL =
        "https://generativelanguage.googleapis.com/v1beta/interactions"
    private const val GEMINI_HOST = "generativelanguage.googleapis.com"
    private const val VERTEX_EXPRESS_HOST = "aiplatform.googleapis.com"
    private val ALLOWED_GEMINI_HOSTS = setOf(GEMINI_HOST, VERTEX_EXPRESS_HOST)
    private const val GEMINI_IMAGE_MODEL = "gemini-nano-banana-2.1"
    private val GEMINI_IMAGE_MODELS = listOf(
        "gemini-nano-banana-2.1",
        "gemini-3-pro-image",
        "gemini-3.1-flash-lite-image"
    )
    private const val API_REVISION = "2026-05-20"
    private const val MAX_RESPONSE_CHARS = 12_000_000
    private const val MAX_IMAGE_DOWNLOAD_BYTES = 16 * 1024 * 1024
    private val GEMINI_API_KEY_REGEX = Regex("""^(?:AIza[0-9A-Za-z_-]{20,120}|AQ\.[0-9A-Za-z._-]{16,220})$""")
    private val HF_TOKEN_REGEX = Regex("""^hf_[0-9A-Za-z]{16,128}$""")

    // Positive-only diffusion prompt (omits negative nouns like "hands" or "hanger" that trigger artifacts)
    internal const val FLAT_LAY_DIFFUSION_PROMPT =
        "Professional luxury e-commerce catalog flat-lay product photograph of only this single garment " +
            "laid completely flat on a pure solid seamless white (#FFFFFF) studio background. Freshly steamed " +
            "and smoothly ironed with smooth crisp fabric, hood or collar upright and neatly shaped, both " +
            "sleeves cleanly extended symmetrically outward at a slight natural downward angle, straight even hem. " +
            "Soft diffused commercial studio lighting with completely shadowless pure white backdrop. " +
            "Strictly preserve the exact same viewing side shown in the original photo (back view remains " +
            "back view without adding front pockets or zippers; front view remains front view), exact original " +
            "fabric color, texture, stitching, graphic print artwork, logo, and proportions. Centered cleanly " +
            "in a 1:1 square frame with balanced pure white margins on all four sides. Isolated garment only."

    private enum class SpacePayloadMode {
        UPLOAD_SINGLE_KULKAS,
        UPLOAD_GALLERY_QWEN,
        UPLOAD_GALLERY_LPX,
        DIRECT_B64_FIRERED,
        UPLOAD_SINGLE_KONTEXT
    }

    private data class HfSpaceEndpoint(
        val host: String,
        val endpoint: String,
        val mode: SpacePayloadMode,
        val usesZeroGpu: Boolean = true
    )

    private val HF_SPACE_CHAIN = listOf(
        HfSpaceEndpoint(
            host = "kulkas2pintu-qwen-edit-image.hf.space",
            endpoint = "/edit",
            mode = SpacePayloadMode.UPLOAD_SINGLE_KULKAS
        ),
        HfSpaceEndpoint(
            host = "linoyts-qwen-image-edit-2511-fast.hf.space",
            endpoint = "/infer",
            mode = SpacePayloadMode.UPLOAD_GALLERY_QWEN
        ),
        HfSpaceEndpoint(
            host = "lpx55-qwen-image-edit-2511-turbo-lightning.hf.space",
            endpoint = "/infer",
            mode = SpacePayloadMode.UPLOAD_GALLERY_LPX
        ),
        HfSpaceEndpoint(
            host = "prithivmlmods-firered-image-edit-1-0-fast.hf.space",
            endpoint = "/edit_image",
            mode = SpacePayloadMode.DIRECT_B64_FIRERED
        ),
        HfSpaceEndpoint(
            host = "black-forest-labs-flux-1-kontext-dev.hf.space",
            endpoint = "/infer",
            mode = SpacePayloadMode.UPLOAD_SINGLE_KONTEXT
        )
    )

    internal val ALLOWED_HF_HOSTS = HF_SPACE_CHAIN.map { it.host }.toSet()

    class StudioQuotaExceededException(message: String) : IllegalStateException(message)

    internal sealed class GradioSseOutcome {
        data class ImageUrl(val url: String) : GradioSseOutcome()
        data class InlineBase64(val base64: String) : GradioSseOutcome()
        data class QuotaExceeded(val message: String) : GradioSseOutcome()
        data class SpaceError(val message: String) : GradioSseOutcome()
    }

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
    private val INLINE_MIME_THEN_DATA_PATTERN = Pattern.compile(
        """"mime_?[Tt]ype"\s*:\s*"image/(?:png|jpeg|jpg|webp)"[^{}]{0,200}?"data"\s*:\s*"([A-Za-z0-9+/=\r\n\s]{32,})"""",
        Pattern.DOTALL
    )
    private val DATA_THEN_INLINE_MIME_PATTERN = Pattern.compile(
        """"data"\s*:\s*"([A-Za-z0-9+/=\r\n\s]{32,})"[^{}]{0,200}?"mime_?[Tt]ype"\s*:\s*"image/(?:png|jpeg|jpg|webp)"""",
        Pattern.DOTALL
    )
    private val GRADIO_INLINE_B64_PATTERN = Pattern.compile(
        """"data:image/(?:png|jpeg|jpg|webp);base64,([A-Za-z0-9+/=\s]{32,})""""
    )
    private val GRADIO_URL_PATTERN = Pattern.compile(
        """"url"\s*:\s*"([^"]+)""""
    )
    private val GRADIO_PATH_PATTERN = Pattern.compile(
        """"path"\s*:\s*"(/[^"]+)""""
    )
    private val GRADIO_ERR_MSG_PATTERN = Pattern.compile(
        """"(?:error|message|title)"\s*:\s*"([^"]{1,240})""""
    )

    fun isVertexExpressApiKey(key: String): Boolean = key.trim().startsWith("AQ.")

    fun isValidGeminiApiKey(key: String): Boolean = GEMINI_API_KEY_REGEX.matches(key.trim())

    fun isValidHfToken(token: String): Boolean = HF_TOKEN_REGEX.matches(token.trim())

    fun hasApiKey(context: Context): Boolean = isValidGeminiApiKey(getApiKey(context))

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_GEMINI_API_KEY, "")?.trim() ?: ""
        return if (isValidGeminiApiKey(raw)) raw else ""
    }

    fun setApiKey(context: Context, key: String) {
        val clean = key.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (clean.isEmpty()) {
            prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
        } else if (isValidGeminiApiKey(clean)) {
            prefs.edit().putString(KEY_GEMINI_API_KEY, clean).apply()
        }
    }

    fun clearApiKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    fun hasHfToken(context: Context): Boolean = getHfToken(context).isNotBlank()

    fun getHfToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_HF_TOKEN, "")?.trim() ?: ""
    }

    fun setHfToken(context: Context, token: String) {
        val clean = token.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (clean.isEmpty()) {
            prefs.edit().remove(KEY_HF_TOKEN).apply()
        } else {
            prefs.edit().putString(KEY_HF_TOKEN, clean).apply()
        }
    }

    fun clearHfToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HF_TOKEN).apply()
    }

    /**
     * Generates a true reconstructed e-commerce studio catalog photo (#FFFFFF 1:1 flat-lay):
     * tries user's Gemini key if configured, otherwise runs free zero-key generative failover
     * across Hugging Face Gradio image-editing Spaces, and finishes with clean border framing.
     * Never silently replaces failed generative synthesis with plain background removal.
     */
    suspend fun generateEcommerceFlatLay(
        context: Context,
        sourceBitmap: Bitmap
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        var lastError: Throwable? = null

        if (apiKey.isNotBlank()) {
            val sqBmp = prepareSquareInputBitmap(sourceBitmap, targetSize = 1024)
            val b64 = encodeBitmapJpegBase64(sqBmp, maxDim = 1024, quality = 90)
            if (!sqBmp.isRecycled) sqBmp.recycle()

            val geminiRes = postGeminiImageRequest(
                apiKey = apiKey,
                prompt = FLAT_LAY_DIFFUSION_PROMPT,
                imageBase64List = listOf(b64),
                aspectRatio = "1:1"
            )
            if (geminiRes.isSuccess) {
                val rawBmp = geminiRes.getOrThrow()
                val polished = StudioCutoutProcessor.polishCatalogShot(rawBmp)
                if (polished !== rawBmp && !rawBmp.isRecycled) rawBmp.recycle()
                return@withContext Result.success(polished)
            }
            lastError = geminiRes.exceptionOrNull()
        }

        val hfRes = generateFlatLayViaHfSpaces(context, sourceBitmap)
        if (hfRes.isSuccess) {
            val rawBmp = hfRes.getOrThrow()
            val polished = StudioCutoutProcessor.polishCatalogShot(rawBmp)
            if (polished !== rawBmp && !rawBmp.isRecycled) rawBmp.recycle()
            return@withContext Result.success(polished)
        }

        val hfErr = hfRes.exceptionOrNull()
        Result.failure(hfErr ?: lastError ?: IllegalStateException("Catalog studio synthesis failed"))
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
                "Dress the person in the first image wearing these exact garments seamlessly integrated onto " +
                "their body in 3D with natural fabric drape, realistic folds, shadows, sleeves, collar fit, " +
                "and body contour occlusion. Strictly preserve the person's exact face, facial identity, " +
                "hair, skin tone, body proportions, pose, hands, camera perspective, ambient lighting, and " +
                "background without altering them. Preserve the exact garment colors, prints, logos, graphics, and textures."

        val b64List = mutableListOf<String>().apply {
            add(encodeBitmapJpegBase64(bodyBitmap, maxDim = 960, quality = 86))
            limitedGarments.forEach { (_, garmentBmp) ->
                add(encodeBitmapJpegBase64(garmentBmp, maxDim = 768, quality = 85))
            }
        }
        postGeminiImageRequest(
            apiKey = apiKey,
            prompt = tryOnPrompt,
            imageBase64List = b64List,
            aspectRatio = "3:4"
        )
    }

    private fun generateFlatLayViaHfSpaces(
        context: Context,
        sourceBitmap: Bitmap
    ): Result<Bitmap> {
        val hfToken = getHfToken(context)
        val sqBmp = prepareSquareInputBitmap(sourceBitmap, targetSize = 768)
        val baos = ByteArrayOutputStream()
        sqBmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        sqBmp.recycle()
        val sqJpegBytes = baos.toByteArray()
        val sqBase64 = Base64.encodeToString(sqJpegBytes, Base64.NO_WRAP)

        var lastError: Throwable? = null
        for (space in HF_SPACE_CHAIN) {
            try {
                val res = invokeHfSpaceFlatLay(space, sqJpegBytes, sqBase64, hfToken)
                if (res.isSuccess) return res
                val err = res.exceptionOrNull()
                if (err is StudioQuotaExceededException) {
                    return Result.failure(err)
                }
                if (err != null) lastError = err
            } catch (e: StudioQuotaExceededException) {
                return Result.failure(e)
            } catch (e: Exception) {
                lastError = e
            }
        }
        return Result.failure(lastError ?: IllegalStateException("No catalog AI service reachable"))
    }

    private fun invokeHfSpaceFlatLay(
        space: HfSpaceEndpoint,
        sqJpegBytes: ByteArray,
        sqBase64: String,
        hfToken: String
    ): Result<Bitmap> {
        if (space.host !in ALLOWED_HF_HOSTS || !SecurityGuards.isPublicHostResolved(space.host)) {
            return Result.failure(SecurityException("Blocked untrusted space host"))
        }

        val payload = when (space.mode) {
            SpacePayloadMode.DIRECT_B64_FIRERED -> {
                val arrJson = JSONArray().put("data:image/jpeg;base64,$sqBase64").toString()
                JSONObject().put(
                    "data",
                    JSONArray()
                        .put(arrJson)
                        .put(FLAT_LAY_DIFFUSION_PROMPT)
                        .put(0)
                        .put(true)
                        .put(1.0)
                        .put(4)
                )
            }

            else -> {
                val remotePath = uploadToGradioSpace(space.host, sqJpegBytes, hfToken)
                    ?: return Result.failure(IllegalStateException("Upload failed on ${space.host}"))
                val fileObj = JSONObject().apply {
                    put("path", remotePath)
                    put("url", "https://${space.host}/gradio_api/file=$remotePath")
                    put("orig_name", "garment.jpg")
                    put("mime_type", "image/jpeg")
                    put("meta", JSONObject().put("_type", "gradio.FileData"))
                }
                val dataArr = when (space.mode) {
                    SpacePayloadMode.UPLOAD_SINGLE_KULKAS -> JSONArray()
                        .put(fileObj)
                        .put(FLAT_LAY_DIFFUSION_PROMPT)
                        .put("")
                        .put(JSONObject.NULL)
                        .put(0)
                        .put(true)
                        .put(1.0)
                        .put(4)
                        // Crucial: preserve_identity=false & identity_strength=0 suppresses ANATOMY_POS hand injection
                        .put(false)
                        .put(0)
                        .put(1024)
                        .put(false)
                        .put(false)

                    SpacePayloadMode.UPLOAD_GALLERY_QWEN -> JSONArray()
                        .put(JSONArray().put(JSONObject().put("image", fileObj).put("caption", JSONObject.NULL)))
                        .put(FLAT_LAY_DIFFUSION_PROMPT)
                        .put(0)
                        .put(true)
                        .put(1.0)
                        .put(4)
                        .put(1024)
                        .put(1024)
                        .put(false)

                    SpacePayloadMode.UPLOAD_GALLERY_LPX -> JSONArray()
                        .put(JSONArray().put(JSONObject().put("image", fileObj).put("caption", JSONObject.NULL)))
                        .put(FLAT_LAY_DIFFUSION_PROMPT)
                        .put(0)
                        .put(true)
                        .put(1.0)
                        .put(4)
                        .put(1024)
                        .put(1024)
                        .put(false)
                        .put(1)

                    SpacePayloadMode.UPLOAD_SINGLE_KONTEXT -> JSONArray()
                        .put(fileObj)
                        .put(FLAT_LAY_DIFFUSION_PROMPT)
                        .put(0)
                        .put(true)
                        .put(2.5)
                        .put(24)

                    SpacePayloadMode.DIRECT_B64_FIRERED -> JSONArray()
                }
                JSONObject().put("data", dataArr)
            }
        }

        val callUrl = "https://${space.host}/gradio_api/call${space.endpoint}"
        val callResp = postJsonToGradio(callUrl, space.host, payload.toString(), hfToken)
            ?: return Result.failure(IllegalStateException("Call failed on ${space.host}"))
        val eventId = runCatching { JSONObject(callResp).optString("event_id", "") }.getOrDefault("")
        if (eventId.isBlank()) {
            return Result.failure(IllegalStateException("Missing event_id on ${space.host}"))
        }

        val sseUrl = "https://${space.host}/gradio_api/call${space.endpoint}/$eventId"
        val sseRaw = readGradioSse(sseUrl, space.host, hfToken)
            ?: return Result.failure(IllegalStateException("SSE stream failed on ${space.host}"))

        return when (val parsed = parseGradioSseOutput(sseRaw, space.host)) {
            is GradioSseOutcome.InlineBase64 -> {
                val bytes = Base64.decode(parsed.base64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return Result.failure(IllegalStateException("Decode failed"))
                Result.success(bmp)
            }

            is GradioSseOutcome.ImageUrl -> {
                downloadAndDecodeImage(parsed.url, space.host, hfToken)
            }

            is GradioSseOutcome.QuotaExceeded -> {
                Result.failure(StudioQuotaExceededException(parsed.message))
            }

            is GradioSseOutcome.SpaceError -> {
                Result.failure(IllegalStateException(parsed.message))
            }
        }
    }

    private fun uploadToGradioSpace(
        host: String,
        jpegBytes: ByteArray,
        hfToken: String
    ): String? {
        val uploadUrl = "https://$host/gradio_api/upload"
        if (!SecurityGuards.isSafePublicHttpsUrl(uploadUrl, maxLen = 512) ||
            host !in ALLOWED_HF_HOSTS ||
            !SecurityGuards.isPublicHostResolved(host)
        ) {
            return null
        }

        val boundary = "----DolabimBoundary${System.currentTimeMillis()}"
        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(uploadUrl).openConnection() as? HttpsURLConnection)?.apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                if (hfToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $hfToken")
                }
            } ?: return null

            conn.outputStream.use { os ->
                val head = ("--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"files\"; filename=\"garment.jpg\"\r\n" +
                    "Content-Type: image/jpeg\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
                val tail = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
                os.write(head)
                os.write(jpegBytes)
                os.write(tail)
            }

            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val arr = JSONArray(body)
            val path = arr.optString(0, "").trim()
            if (path.startsWith("/")) path else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun postJsonToGradio(
        targetUrl: String,
        expectedHost: String,
        bodyJson: String,
        hfToken: String
    ): String? {
        if (!SecurityGuards.isSafePublicHttpsUrl(targetUrl, maxLen = 1024) ||
            expectedHost !in ALLOWED_HF_HOSTS ||
            !SecurityGuards.isPublicHostResolved(expectedHost)
        ) {
            return null
        }
        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(targetUrl).openConnection() as? HttpsURLConnection)?.apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (hfToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $hfToken")
                }
            } ?: return null

            conn.outputStream.use { os ->
                os.write(bodyJson.toByteArray(StandardCharsets.UTF_8))
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun readGradioSse(
        sseUrl: String,
        expectedHost: String,
        hfToken: String
    ): String? {
        if (!SecurityGuards.isSafePublicHttpsUrl(sseUrl, maxLen = 1024) ||
            expectedHost !in ALLOWED_HF_HOSTS ||
            !SecurityGuards.isPublicHostResolved(expectedHost)
        ) {
            return null
        }
        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(sseUrl).openConnection() as? HttpsURLConnection)?.apply {
                connectTimeout = 15_000
                readTimeout = 95_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "text/event-stream")
                if (hfToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $hfToken")
                }
            } ?: return null

            if (conn.responseCode !in 200..299) return null
            val sb = StringBuilder()
            conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                while (sb.length < MAX_RESPONSE_CHARS) {
                    val line = reader.readLine() ?: break
                    sb.append(line).append('\n')
                    if (line.startsWith("data:") &&
                        (sb.contains("event: complete") || sb.contains("event: error"))
                    ) {
                        break
                    }
                }
            }
            sb.toString()
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadAndDecodeImage(
        imageUrl: String,
        expectedHost: String,
        hfToken: String
    ): Result<Bitmap> {
        if (!SecurityGuards.isSafePublicHttpsUrl(imageUrl, maxLen = 2048)) {
            return Result.failure(SecurityException("Blocked unsafe output URL"))
        }
        val uriHost = runCatching { URI(imageUrl).host?.lowercase(Locale.ROOT)?.trimEnd('.') }.getOrNull()
        if (uriHost != expectedHost.lowercase(Locale.ROOT) || expectedHost !in ALLOWED_HF_HOSTS) {
            return Result.failure(SecurityException("Mismatched output host"))
        }
        if (!SecurityGuards.isPublicHostResolved(expectedHost)) {
            return Result.failure(SecurityException("Untrusted resolved IP"))
        }

        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(imageUrl).openConnection() as? HttpsURLConnection)?.apply {
                connectTimeout = 15_000
                readTimeout = 45_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                if (hfToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $hfToken")
                }
            } ?: return Result.failure(IllegalStateException("Download connection unavailable"))

            if (conn.responseCode !in 200..299) {
                return Result.failure(IllegalStateException("Image HTTP ${conn.responseCode}"))
            }

            val baos = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16_384)
                while (baos.size() < MAX_IMAGE_DOWNLOAD_BYTES) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    baos.write(buf, 0, read)
                }
            }
            val bytes = baos.toByteArray()
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(IllegalStateException("Generated image decode failed"))
            Result.success(bmp)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseGradioSseOutput(rawSse: String, spaceHost: String): GradioSseOutcome {
        val cleanHost = spaceHost.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (cleanHost !in ALLOWED_HF_HOSTS) {
            return GradioSseOutcome.SpaceError("Untrusted space host")
        }

        var lastEvent = ""
        for (rawLine in rawSse.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("event:")) {
                lastEvent = line.substringAfter("event:").trim().lowercase(Locale.ROOT)
                continue
            }
            if (!line.startsWith("data:")) continue
            val payload = line.substringAfter("data:").trim()

            if (lastEvent == "error") {
                val lower = payload.lowercase(Locale.ROOT)
                val msg = GRADIO_ERR_MSG_PATTERN.matcher(payload).let { m ->
                    if (m.find()) m.group(1) else null
                } ?: "Space error"
                return if (lower.contains("zerogpu") || lower.contains("quota") || lower.contains("runs limit")) {
                    GradioSseOutcome.QuotaExceeded(msg)
                } else {
                    GradioSseOutcome.SpaceError(msg)
                }
            }

            if (lastEvent == "complete") {
                if (payload.contains("\"status\":\"blocked\"") || payload.contains("\"status\": \"blocked\"")) {
                    return GradioSseOutcome.SpaceError("Content blocked by remote safety filter")
                }

                val b64Matcher = GRADIO_INLINE_B64_PATTERN.matcher(payload)
                if (b64Matcher.find()) {
                    val rawB64 = b64Matcher.group(1)?.replace(Regex("\\s+"), "")
                    if (!rawB64.isNullOrBlank()) {
                        return GradioSseOutcome.InlineBase64(rawB64)
                    }
                }

                val urlMatcher = GRADIO_URL_PATTERN.matcher(payload)
                if (urlMatcher.find()) {
                    val outUrl = urlMatcher.group(1)?.trim() ?: ""
                    val urlHost = runCatching {
                        URI(outUrl).host?.lowercase(Locale.ROOT)?.trimEnd('.')
                    }.getOrNull()
                    if (!SecurityGuards.isSafePublicHttpsUrl(outUrl, maxLen = 2048) || urlHost != cleanHost) {
                        return GradioSseOutcome.SpaceError("Rejected untrusted output URL host")
                    }
                    return GradioSseOutcome.ImageUrl(outUrl)
                }

                val pathMatcher = GRADIO_PATH_PATTERN.matcher(payload)
                if (pathMatcher.find()) {
                    val remotePath = pathMatcher.group(1)?.trim() ?: ""
                    val resolved = "https://$cleanHost/gradio_api/file=$remotePath"
                    if (!SecurityGuards.isSafePublicHttpsUrl(resolved, maxLen = 2048)) {
                        return GradioSseOutcome.SpaceError("Unsafe file path")
                    }
                    return GradioSseOutcome.ImageUrl(resolved)
                }
                return GradioSseOutcome.SpaceError("No image returned in Gradio payload")
            }
        }
        return GradioSseOutcome.SpaceError("Incomplete Gradio SSE response")
    }

    private fun prepareSquareInputBitmap(source: Bitmap, targetSize: Int): Bitmap {
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val pad = targetSize * 0.06f
        val avail = targetSize - 2f * pad
        val scale = min(avail / source.width.coerceAtLeast(1).toFloat(), avail / source.height.coerceAtLeast(1).toFloat())
        val drawW = source.width * scale
        val drawH = source.height * scale
        val left = (targetSize - drawW) * 0.5f
        val top = (targetSize - drawH) * 0.5f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            RectF(left, top, left + drawW, top + drawH),
            paint
        )
        return out
    }

    private fun buildInteractionsPayload(
        model: String,
        prompt: String,
        imageBase64List: List<String>,
        aspectRatio: String
    ): JSONObject {
        val inputArray = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", prompt))
            imageBase64List.forEach { b64 ->
                put(
                    JSONObject()
                        .put("type", "image")
                        .put("mime_type", "image/jpeg")
                        .put("data", b64)
                )
            }
        }
        return JSONObject().apply {
            put("model", model)
            put("input", inputArray)
            put(
                "response_format",
                JSONObject()
                    .put("type", "image")
                    .put("mime_type", "image/jpeg")
                    .put("aspect_ratio", aspectRatio)
            )
        }
    }

    private fun buildGenerateContentPayload(
        prompt: String,
        imageBase64List: List<String>,
        aspectRatio: String
    ): JSONObject {
        val parts = JSONArray().apply {
            put(JSONObject().put("text", prompt))
            imageBase64List.forEach { b64 ->
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", b64)
                    )
                )
            }
        }
        val contentObj = JSONObject().apply {
            put("role", "USER")
            put("parts", parts)
        }
        val genConfig = JSONObject().apply {
            put("responseModalities", JSONArray().put("IMAGE").put("TEXT"))
            put("imageConfig", JSONObject().put("aspectRatio", aspectRatio))
        }
        return JSONObject().apply {
            put("contents", JSONArray().put(contentObj))
            put("generationConfig", genConfig)
        }
    }

    private fun isNotFoundError(result: Result<Bitmap>): Boolean {
        val msg = result.exceptionOrNull()?.message.orEmpty()
        return msg == "HTTP 404" ||
            msg.contains("404", ignoreCase = true) ||
            msg.contains("not found", ignoreCase = true)
    }

    private fun isAuthError(result: Result<Bitmap>): Boolean {
        val msg = result.exceptionOrNull()?.message.orEmpty()
        return msg.contains("HTTP 401") ||
            msg.contains("HTTP 403") ||
            msg.contains("API key", ignoreCase = true) ||
            msg.contains("UNAUTHENTICATED", ignoreCase = true) ||
            msg.contains("PERMISSION_DENIED", ignoreCase = true)
    }

    private fun postGeminiImageRequest(
        apiKey: String,
        prompt: String,
        imageBase64List: List<String>,
        aspectRatio: String
    ): Result<Bitmap> {
        val cleanKey = apiKey.trim()
        if (!isValidGeminiApiKey(cleanKey)) {
            return Result.failure(IllegalArgumentException("Invalid Gemini API key format"))
        }

        val gcPayload = buildGenerateContentPayload(prompt, imageBase64List, aspectRatio)
        var lastResult: Result<Bitmap>? = null

        if (isVertexExpressApiKey(cleanKey)) {
            for (model in GEMINI_IMAGE_MODELS) {
                val v1Url = "https://$VERTEX_EXPRESS_HOST/v1/publishers/google/models/$model:generateContent"
                val r1 = executeGeminiRequest(v1Url, VERTEX_EXPRESS_HOST, cleanKey, gcPayload, includeApiRevision = false)
                if (r1.isSuccess || isAuthError(r1)) return r1
                lastResult = r1

                if (isNotFoundError(r1)) {
                    val betaUrl = "https://$VERTEX_EXPRESS_HOST/v1beta1/publishers/google/models/$model:generateContent"
                    val rBeta = executeGeminiRequest(betaUrl, VERTEX_EXPRESS_HOST, cleanKey, gcPayload, includeApiRevision = false)
                    if (rBeta.isSuccess || isAuthError(rBeta)) return rBeta
                    lastResult = rBeta
                }
            }
            if (lastResult != null && isNotFoundError(lastResult)) {
                val interPayload = buildInteractionsPayload(GEMINI_IMAGE_MODEL, prompt, imageBase64List, aspectRatio)
                val rInter = executeGeminiRequest(GEMINI_INTERACTIONS_V1_URL, GEMINI_HOST, cleanKey, interPayload, includeApiRevision = true)
                if (rInter.isSuccess) return rInter
                lastResult = rInter
            }
            return lastResult ?: Result.failure(IllegalStateException("Vertex Express AI failed"))
        }

        val interPayload = buildInteractionsPayload(GEMINI_IMAGE_MODEL, prompt, imageBase64List, aspectRatio)
        val primary = executeGeminiRequest(GEMINI_INTERACTIONS_V1_URL, GEMINI_HOST, cleanKey, interPayload, includeApiRevision = true)
        if (primary.isSuccess || isAuthError(primary)) return primary
        lastResult = primary

        if (isNotFoundError(primary)) {
            val betaInter = executeGeminiRequest(GEMINI_INTERACTIONS_BETA_URL, GEMINI_HOST, cleanKey, interPayload, includeApiRevision = true)
            if (betaInter.isSuccess || isAuthError(betaInter)) return betaInter
            lastResult = betaInter

            if (isNotFoundError(betaInter)) {
                val gcUrl = "https://$GEMINI_HOST/v1beta/models/$GEMINI_IMAGE_MODEL:generateContent"
                val gcRes = executeGeminiRequest(gcUrl, GEMINI_HOST, cleanKey, gcPayload, includeApiRevision = false)
                if (gcRes.isSuccess) return gcRes
                lastResult = gcRes
            }
        }
        return lastResult
    }

    private fun executeGeminiRequest(
        targetUrl: String,
        expectedHost: String,
        apiKey: String,
        payload: JSONObject,
        includeApiRevision: Boolean
    ): Result<Bitmap> {
        if (expectedHost !in ALLOWED_GEMINI_HOSTS ||
            !SecurityGuards.isSafePublicHttpsUrl(targetUrl, maxLen = 500) ||
            !SecurityGuards.isPublicHostResolved(expectedHost)
        ) {
            return Result.failure(SecurityException("Blocked unsafe host"))
        }
        val uriHost = runCatching { URI(targetUrl).host?.lowercase(Locale.ROOT) }.getOrNull()
        if (uriHost != expectedHost) {
            return Result.failure(SecurityException("Host mismatch"))
        }

        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(targetUrl).openConnection() as? HttpsURLConnection)?.apply {
                connectTimeout = 15_000
                readTimeout = 90_000
                instanceFollowRedirects = false
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("x-goog-api-key", apiKey)
                setRequestProperty("Content-Type", "application/json")
                if (includeApiRevision) {
                    setRequestProperty("Api-Revision", API_REVISION)
                }
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
            DATA_THEN_TYPE_IMAGE_PATTERN,
            INLINE_MIME_THEN_DATA_PATTERN,
            DATA_THEN_INLINE_MIME_PATTERN
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
        if (!m.find()) return null
        return m.group(1)
            ?.replace(Regex("""AIza[0-9A-Za-z_-]+"""), "[KEY]")
            ?.replace(Regex("""AQ\.[0-9A-Za-z._-]+"""), "[KEY]")
            ?.replace(Regex("""hf_[0-9A-Za-z]+"""), "[TOKEN]")
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
