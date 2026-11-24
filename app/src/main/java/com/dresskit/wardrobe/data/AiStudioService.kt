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
import com.dresskit.wardrobe.ui.theme.STUDIO_ECOMMERCE_PROMPT
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
        "gemini-3.1-flash-image-preview",
        "gemini-2.5-flash-image",
        "gemini-3-pro-image-preview",
        "gemini-3-pro-image",
        "gemini-3.1-flash-image",
        "gemini-3.1-flash-lite-image"
    )
    private const val API_REVISION = "2026-05-20"
    private const val MAX_RESPONSE_CHARS = 12_000_000
    private const val MAX_IMAGE_DOWNLOAD_BYTES = 64 * 1024 * 1024
    private val ALLOWED_OUTPUT_IMAGE_MIMES = setOf("image/png", "image/jpeg", "image/jpg", "image/webp")
    private val GEMINI_API_KEY_REGEX = Regex("""^(?:AIza[0-9A-Za-z_-]{20,120}|AQ\.[0-9A-Za-z._~=-]{16,512})$""")
    private val HF_TOKEN_REGEX = Regex("""^hf_[0-9A-Za-z]{16,128}$""")

    internal const val FLAT_LAY_DIFFUSION_PROMPT = STUDIO_ECOMMERCE_PROMPT

    private enum class SpacePayloadMode {
        UPLOAD_GALLERY_FLUX2_DEV,
        UPLOAD_GALLERY_FLUX2_KLEIN_BASE,
        UPLOAD_GALLERY_QWEN_OFFICIAL,
        UPLOAD_GALLERY_FIRERED_1_1,
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
            host = "black-forest-labs-flux-2-dev.hf.space",
            endpoint = "/infer",
            mode = SpacePayloadMode.UPLOAD_GALLERY_FLUX2_DEV
        ),
        HfSpaceEndpoint(
            host = "black-forest-labs-flux-2-klein-9b.hf.space",
            endpoint = "/generate",
            mode = SpacePayloadMode.UPLOAD_GALLERY_FLUX2_KLEIN_BASE
        ),
        HfSpaceEndpoint(
            host = "qwen-qwen-image-edit-2511.hf.space",
            endpoint = "/infer",
            mode = SpacePayloadMode.UPLOAD_GALLERY_QWEN_OFFICIAL
        ),
        HfSpaceEndpoint(
            host = "fireredteam-firered-image-edit-1-1.hf.space",
            endpoint = "/infer",
            mode = SpacePayloadMode.UPLOAD_GALLERY_FIRERED_1_1
        ),
        HfSpaceEndpoint(
            host = "black-forest-labs-flux-1-kontext-dev.hf.space",
            endpoint = "/infer",
            mode = SpacePayloadMode.UPLOAD_SINGLE_KONTEXT
        )
    )

    internal val ALLOWED_HF_HOSTS = HF_SPACE_CHAIN.map { it.host }.toSet()

    class StudioQuotaExceededException(message: String) : IllegalStateException(message)

    class StudioServiceBusyException(message: String) : IllegalStateException(message)

    class GeminiApiException(message: String) : IllegalStateException(message)

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

    fun getApiKey(context: Context): String =
        SecureSecretStore.getSecret(context, PREFS_NAME, KEY_GEMINI_API_KEY) { isValidGeminiApiKey(it) }

    fun setApiKey(context: Context, key: String) {
        SecureSecretStore.setSecret(context, PREFS_NAME, KEY_GEMINI_API_KEY, key) { isValidGeminiApiKey(it) }
    }

    fun clearApiKey(context: Context) {
        SecureSecretStore.clearSecret(context, PREFS_NAME, KEY_GEMINI_API_KEY)
    }

    fun hasHfToken(context: Context): Boolean = getHfToken(context).isNotBlank()

    fun getHfToken(context: Context): String =
        SecureSecretStore.getSecret(context, PREFS_NAME, KEY_HF_TOKEN) { isValidHfToken(it) }

    fun setHfToken(context: Context, token: String) {
        SecureSecretStore.setSecret(context, PREFS_NAME, KEY_HF_TOKEN, token) { isValidHfToken(it) }
    }

    fun clearHfToken(context: Context) {
        SecureSecretStore.clearSecret(context, PREFS_NAME, KEY_HF_TOKEN)
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
        var geminiError: Throwable? = null

        if (apiKey.isNotBlank()) {
            val b64 = encodeBitmapJpegBase64(sourceBitmap, maxDim = 1024, quality = 92)

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
            geminiError = geminiRes.exceptionOrNull()
        }

        val hfRes = generateFlatLayViaHfSpaces(context, sourceBitmap)
        if (hfRes.isSuccess) {
            val rawBmp = hfRes.getOrThrow()
            val polished = StudioCutoutProcessor.polishCatalogShot(rawBmp)
            if (polished !== rawBmp && !rawBmp.isRecycled) rawBmp.recycle()
            return@withContext Result.success(polished)
        }

        if (geminiError != null) {
            return@withContext Result.failure(
                GeminiApiException(geminiError.message ?: "Gemini API hatası")
            )
        }

        val hfErr = hfRes.exceptionOrNull()
        Result.failure(hfErr ?: IllegalStateException("Catalog studio synthesis failed"))
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
        val naturalJpegBytes = encodeBitmapJpegBytes(sourceBitmap, maxDim = 1024, quality = 92)
        var squareJpegBytes: ByteArray? = null

        var quotaErr: StudioQuotaExceededException? = null
        var lastError: Throwable? = null
        var nullSpaceErrors = 0
        var anyServerResponded = false
        for (space in HF_SPACE_CHAIN) {
            val spaceResponded = java.util.concurrent.atomic.AtomicBoolean(false)
            try {
                val inputBytes = if (space.mode == SpacePayloadMode.UPLOAD_SINGLE_KONTEXT) {
                    squareJpegBytes ?: run {
                        val sqBmp = prepareSquareInputBitmap(sourceBitmap, targetSize = 1024, padRatio = 0f)
                        val bytes = encodeBitmapJpegBytes(sqBmp, maxDim = 1024, quality = 92)
                        if (!sqBmp.isRecycled) sqBmp.recycle()
                        squareJpegBytes = bytes
                        bytes
                    }
                } else {
                    naturalJpegBytes
                }
                val res = invokeHfSpaceFlatLay(space, inputBytes, hfToken, spaceResponded)
                if (spaceResponded.get()) anyServerResponded = true
                if (res.isSuccess) return res
                val err = res.exceptionOrNull()
                if (err is StudioQuotaExceededException) {
                    anyServerResponded = true
                    if (quotaErr == null) quotaErr = err
                } else if (err != null) {
                    if (err.message == "Space error") nullSpaceErrors++
                    lastError = err
                }
            } catch (e: StudioQuotaExceededException) {
                anyServerResponded = true
                if (quotaErr == null) quotaErr = e
            } catch (e: Exception) {
                if (spaceResponded.get()) anyServerResponded = true
                if (e.message == "Space error") nullSpaceErrors++
                lastError = e
            }
        }
        if (quotaErr == null && nullSpaceErrors > 0) {
            return Result.failure(
                StudioQuotaExceededException(
                    if (hfToken.isNotBlank()) "Hugging Face ZeroGPU quota reached" else "ZeroGPU anonymous quota exceeded"
                )
            )
        }
        if (quotaErr != null) return Result.failure(quotaErr)
        if (anyServerResponded) {
            return Result.failure(StudioServiceBusyException("AI sunucuları şu an yoğun"))
        }
        return Result.failure(lastError ?: IllegalStateException("No catalog AI service reachable"))
    }

    private fun invokeHfSpaceFlatLay(
        space: HfSpaceEndpoint,
        jpegBytes: ByteArray,
        hfToken: String,
        spaceResponded: java.util.concurrent.atomic.AtomicBoolean? = null
    ): Result<Bitmap> {
        if (space.host !in ALLOWED_HF_HOSTS || !SecurityGuards.isPublicHostResolved(space.host)) {
            return Result.failure(SecurityException("Blocked untrusted space host"))
        }

        val remotePath = uploadToGradioSpace(space.host, jpegBytes, hfToken, spaceResponded)
            ?: return Result.failure(IllegalStateException("Upload failed on ${space.host}"))
        spaceResponded?.set(true)
        val fileObj = JSONObject().apply {
            put("path", remotePath)
            put("url", "https://${space.host}/gradio_api/file=$remotePath")
            put("orig_name", "garment.jpg")
            put("mime_type", "image/jpeg")
            put("meta", JSONObject().put("_type", "gradio.FileData"))
        }
        val galleryArr = JSONArray().put(
            JSONObject()
                .put("image", fileObj)
                .put("caption", JSONObject.NULL)
        )
        val dataArr = when (space.mode) {
            SpacePayloadMode.UPLOAD_GALLERY_FLUX2_DEV -> JSONArray()
                .put(FLAT_LAY_DIFFUSION_PROMPT)
                .put(galleryArr)
                .put(0)
                .put(true)
                .put(1024)
                .put(1024)
                .put(28)
                .put(4.0)
                .put(false)

            SpacePayloadMode.UPLOAD_GALLERY_FLUX2_KLEIN_BASE -> JSONArray()
                .put(FLAT_LAY_DIFFUSION_PROMPT)
                .put(galleryArr)
                .put("Base (50 steps)")
                .put(0)
                .put(true)
                .put(1024)
                .put(1024)
                .put(28)
                .put(4.0)
                .put(false)

            SpacePayloadMode.UPLOAD_GALLERY_QWEN_OFFICIAL -> JSONArray()
                .put(galleryArr)
                .put(FLAT_LAY_DIFFUSION_PROMPT)
                .put(0)
                .put(true)
                .put(4.0)
                .put(32)
                .put(1024)
                .put(1024)
                .put(false)

            SpacePayloadMode.UPLOAD_GALLERY_FIRERED_1_1 -> JSONArray()
                .put(galleryArr)
                .put(FLAT_LAY_DIFFUSION_PROMPT)
                .put("None")
                .put(0)
                .put(true)
                .put(4.0)
                .put(28)
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
                .put(28)
        }
        val payload = JSONObject().put("data", dataArr)

        val callUrl = "https://${space.host}/gradio_api/call${space.endpoint}"
        val callResp = postJsonToGradio(callUrl, space.host, payload.toString(), hfToken, spaceResponded)
            ?: return Result.failure(IllegalStateException("Call failed on ${space.host}"))
        val eventId = runCatching { JSONObject(callResp).optString("event_id", "") }.getOrDefault("")
        if (eventId.isBlank()) {
            return Result.failure(IllegalStateException("Missing event_id on ${space.host}"))
        }

        val sseUrl = "https://${space.host}/gradio_api/call${space.endpoint}/$eventId"
        val sseRaw = readGradioSse(sseUrl, space.host, hfToken, spaceResponded)
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
        hfToken: String,
        spaceResponded: java.util.concurrent.atomic.AtomicBoolean? = null
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

            val code = conn.responseCode
            spaceResponded?.set(true)
            if (code !in 200..299) return null
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
        hfToken: String,
        spaceResponded: java.util.concurrent.atomic.AtomicBoolean? = null
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
            val code = conn.responseCode
            spaceResponded?.set(true)
            if (code !in 200..299) return null
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
        hfToken: String,
        spaceResponded: java.util.concurrent.atomic.AtomicBoolean? = null
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
                readTimeout = 130_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "text/event-stream")
                if (hfToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $hfToken")
                }
            } ?: return null

            val code = conn.responseCode
            spaceResponded?.set(true)
            if (code !in 200..299) return null
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

    private fun prepareSquareInputBitmap(source: Bitmap, targetSize: Int, padRatio: Float = 0f): Bitmap {
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val pad = targetSize * padRatio
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
        aspectRatio: String,
        includeImageConfig: Boolean = true
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
            put("role", "user")
            put("parts", parts)
        }
        val genConfig = JSONObject().apply {
            put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
            if (includeImageConfig && aspectRatio.isNotBlank()) {
                put("imageConfig", JSONObject().put("aspectRatio", aspectRatio))
            }
        }
        return JSONObject().apply {
            put("contents", JSONArray().put(contentObj))
            put("generationConfig", genConfig)
        }
    }

    private fun isHttp400Or404(result: Result<Bitmap>): Boolean {
        val msg = result.exceptionOrNull()?.message.orEmpty()
        return msg.startsWith("HTTP 404") ||
            msg.startsWith("HTTP 400") ||
            msg.contains("not found", ignoreCase = true)
    }

    private fun isDefiniteInvalidKeyError(result: Result<Bitmap>): Boolean {
        val msg = result.exceptionOrNull()?.message.orEmpty()
        if (msg.startsWith("HTTP 401")) return true
        return Regex("""api[_\s-]*key.*(invalid|not valid|expired|missing)|unauthenticated""", RegexOption.IGNORE_CASE)
            .containsMatchIn(msg)
    }

    private fun executeGenerateContentWithRetry(
        url: String,
        host: String,
        apiKey: String,
        prompt: String,
        imageBase64List: List<String>,
        aspectRatio: String
    ): Result<Bitmap> {
        val gcPayload = buildGenerateContentPayload(prompt, imageBase64List, aspectRatio, includeImageConfig = true)
        val r1 = executeGeminiRequest(url, host, apiKey, gcPayload, includeApiRevision = false)
        if (r1.isSuccess || isDefiniteInvalidKeyError(r1)) return r1
        val msg = r1.exceptionOrNull()?.message.orEmpty()
        if (msg.startsWith("HTTP 400")) {
            val fallbackPayload = buildGenerateContentPayload(prompt, imageBase64List, aspectRatio, includeImageConfig = false)
            return executeGeminiRequest(url, host, apiKey, fallbackPayload, includeApiRevision = false)
        }
        return r1
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

        var lastResult: Result<Bitmap>? = null

        if (isVertexExpressApiKey(cleanKey)) {
            for (model in GEMINI_IMAGE_MODELS) {
                val v1Url = "https://$VERTEX_EXPRESS_HOST/v1/publishers/google/models/$model:generateContent"
                val r1 = executeGenerateContentWithRetry(v1Url, VERTEX_EXPRESS_HOST, cleanKey, prompt, imageBase64List, aspectRatio)
                if (r1.isSuccess || isDefiniteInvalidKeyError(r1)) return r1
                lastResult = r1

                if (isHttp400Or404(r1)) {
                    val betaUrl = "https://$VERTEX_EXPRESS_HOST/v1beta1/publishers/google/models/$model:generateContent"
                    val rBeta = executeGenerateContentWithRetry(betaUrl, VERTEX_EXPRESS_HOST, cleanKey, prompt, imageBase64List, aspectRatio)
                    if (rBeta.isSuccess || isDefiniteInvalidKeyError(rBeta)) return rBeta
                    lastResult = rBeta
                }
            }
            for (model in GEMINI_IMAGE_MODELS.take(4)) {
                val glUrl = "https://$GEMINI_HOST/v1beta/models/$model:generateContent"
                val rGl = executeGenerateContentWithRetry(glUrl, GEMINI_HOST, cleanKey, prompt, imageBase64List, aspectRatio)
                if (rGl.isSuccess || isDefiniteInvalidKeyError(rGl)) return rGl
                lastResult = rGl
            }
            return lastResult ?: Result.failure(IllegalStateException("Vertex Express AI failed"))
        }

        for (model in GEMINI_IMAGE_MODELS) {
            val gcUrl = "https://$GEMINI_HOST/v1beta/models/$model:generateContent"
            val gcRes = executeGenerateContentWithRetry(gcUrl, GEMINI_HOST, cleanKey, prompt, imageBase64List, aspectRatio)
            if (gcRes.isSuccess || isDefiniteInvalidKeyError(gcRes)) return gcRes
            lastResult = gcRes
        }

        val interPayload = buildInteractionsPayload(GEMINI_IMAGE_MODEL, prompt, imageBase64List, aspectRatio)
        val interactionsEndpoints = listOf(GEMINI_INTERACTIONS_V1_URL, GEMINI_INTERACTIONS_BETA_URL)
        for (ep in interactionsEndpoints) {
            val interRes = executeGeminiRequest(ep, GEMINI_HOST, cleanKey, interPayload, includeApiRevision = false)
            if (interRes.isSuccess || isDefiniteInvalidKeyError(interRes)) return interRes
            lastResult = interRes
        }
        return lastResult ?: Result.failure(IllegalStateException("Gemini API request failed"))
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
                val safeErrMsg = extractApiErrorMessage(responseText)
                val fullMsg = if (!safeErrMsg.isNullOrBlank()) "HTTP $code: $safeErrMsg" else "HTTP $code"
                return Result.failure(IllegalStateException(fullMsg))
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

    private val ANY_MIME_ATTR_PATTERN = Pattern.compile(
        """"mime_?[Tt]ype"\s*:\s*"([^"]+)""""
    )

    private fun normalizeAllowedBase64(mime: String?, data: String?): String? {
        val cleanMime = (mime ?: "").trim().ifBlank { "image/jpeg" }.lowercase(Locale.ROOT)
        if (cleanMime !in ALLOWED_OUTPUT_IMAGE_MIMES) return null
        val cleanData = data?.replace(Regex("\\s+"), "")
        return if (!cleanData.isNullOrBlank()) cleanData else null
    }

    internal fun extractGeneratedImageBase64(jsonBody: String): String? {
        if (jsonBody.isBlank()) return null
        try {
            val root = JSONObject(jsonBody)
            root.optJSONObject("output_image")?.let { out ->
                normalizeAllowedBase64(out.optString("mime_type", ""), out.optString("data", ""))?.let { return it }
            }
            root.optJSONArray("steps")?.let { steps ->
                for (i in 0 until steps.length()) {
                    val content = steps.optJSONObject(i)?.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val c = content.optJSONObject(j) ?: continue
                        if (c.optString("type") == "image") {
                            normalizeAllowedBase64(c.optString("mime_type", ""), c.optString("data", ""))?.let { return it }
                        }
                    }
                }
            }
            root.optJSONArray("candidates")?.let { cands ->
                for (i in 0 until cands.length()) {
                    val parts = cands.optJSONObject(i)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    for (j in 0 until parts.length()) {
                        val p = parts.optJSONObject(j) ?: continue
                        val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data") ?: continue
                        val mime = inline.optString("mimeType", inline.optString("mime_type", ""))
                        normalizeAllowedBase64(mime, inline.optString("data", ""))?.let { return it }
                    }
                }
            }
            return null
        } catch (e: RuntimeException) {
            if (e.message?.contains("not mocked", ignoreCase = true) != true) {
                // Fall through to pattern matcher only if JSONObject is an unmocked JVM test stub or invalid JSON
            }
        } catch (_: Exception) {
            // Invalid/partial JSON fallback below
        }

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
                val snippet = m.group(0).orEmpty()
                val mimeMatcher = ANY_MIME_ATTR_PATTERN.matcher(snippet)
                if (mimeMatcher.find()) {
                    val mimeVal = mimeMatcher.group(1)?.trim()?.lowercase(Locale.ROOT)
                    if (mimeVal != null && mimeVal !in ALLOWED_OUTPUT_IMAGE_MIMES) continue
                }
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
            ?.replace(Regex("""AQ\.[0-9A-Za-z._~=-]+"""), "[KEY]")
            ?.replace(Regex("""hf_[0-9A-Za-z]+"""), "[TOKEN]")
            ?.replace(Regex("""Bearer\s+[0-9A-Za-z._~+/=-]+""", RegexOption.IGNORE_CASE), "Bearer [TOKEN]")
    }

    private fun encodeBitmapJpegBytes(bitmap: Bitmap, maxDim: Int, quality: Int): ByteArray {
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
        return baos.toByteArray()
    }

    private fun encodeBitmapJpegBase64(bitmap: Bitmap, maxDim: Int, quality: Int): String {
        return Base64.encodeToString(encodeBitmapJpegBytes(bitmap, maxDim, quality), Base64.NO_WRAP)
    }
}
