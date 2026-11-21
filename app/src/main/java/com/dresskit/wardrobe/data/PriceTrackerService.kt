package com.dresskit.wardrobe.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dresskit.wardrobe.MainActivity
import com.dresskit.wardrobe.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.nio.charset.StandardCharsets
import java.text.NumberFormat
import java.util.Locale
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

data class PriceCheckSummary(
    val checkedCount: Int,
    val droppedCount: Int,
    val firstDroppedName: String? = null
)

object PriceTrackerService {

    private const val CHANNEL_ID = "dolabim_price_drops"
    private const val JOB_ID = 4105
    private const val MAX_HTML_CHARS = 800_000

    private val SHARE_URL_REGEX = Regex("""https://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val JSON_LD_SCRIPT_PATTERN = Pattern.compile(
        """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
        Pattern.CASE_INSENSITIVE
    )
    private val INDITEX_HOST_SUFFIXES = listOf(
        "zara.com",
        "bershka.com",
        "pullandbear.com",
        "stradivarius.com",
        "massimodutti.com",
        "oysho.com"
    )

    private const val MOBILE_CHROME_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val DESKTOP_CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /**
     * Extracts an https:// URL if pasted from a store share sheet (text + link)
     * and percent-encodes non-ASCII UTF-8 characters (e.g. Turkish letters) in URLs
     * without loosening SecurityGuards whitespace/injection checks.
     */
    fun normalizeProductUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val candidate = if (trimmed.contains(' ') &&
            !trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("http://", ignoreCase = true)
        ) {
            SHARE_URL_REGEX.find(trimmed)?.value?.trimEnd('.', ',', ';', ')', ']', '>', '!', '?')
                ?: trimmed
        } else {
            trimmed
        }
        return percentEncodeNonAscii(candidate)
    }

    private fun percentEncodeNonAscii(url: String): String {
        if (url.none { it.code > 0x7F }) return url
        val sb = StringBuilder(url.length + 16)
        for (ch in url) {
            if (ch.code <= 0x7F) {
                sb.append(ch)
            } else {
                val utf8 = ch.toString().toByteArray(StandardCharsets.UTF_8)
                for (b in utf8) {
                    sb.append('%')
                    sb.append("%02X".format(b.toInt() and 0xFF))
                }
            }
        }
        return sb.toString()
    }

    fun isSafeHttpsUrl(raw: String): Boolean {
        val normalized = normalizeProductUrl(raw)
        return SecurityGuards.isSafePublicHttpsUrl(normalized, maxLen = 1000)
    }

    fun parsePrice(text: String): Double? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        val match = Regex("([0-9][0-9.,]{0,12})").find(cleaned) ?: return null
        var num = match.groupValues[1]

        // Turkish thousands/decimal convention vs standard dot decimal
        num = when {
            num.contains(",") && num.contains(".") -> {
                if (num.lastIndexOf(',') > num.lastIndexOf('.')) {
                    // e.g. 1.499,90
                    num.replace(".", "").replace(",", ".")
                } else {
                    // e.g. 1,499.90
                    num.replace(",", "")
                }
            }
            num.contains(",") -> {
                val parts = num.split(",")
                if (parts.last().length == 3 && parts.first().length <= 3) {
                    num.replace(",", "")
                } else {
                    num.replace(",", ".")
                }
            }
            num.contains(".") -> {
                val parts = num.split(".")
                if (parts.last().length == 3 && parts.size >= 2) {
                    // e.g. 1.499 or 2.250 TL
                    num.replace(".", "")
                } else {
                    num
                }
            }
            else -> num
        }
        return num.toDoubleOrNull()?.takeIf { it > 0.0 && it < 1_000_000.0 }
    }

    fun formatPrice(value: Double, template: String = ""): String {
        val nf = NumberFormat.getNumberInstance(Locale("tr", "TR")).apply {
            val hasFraction = kotlin.math.abs(value - value.toLong()) > 0.01
            maximumFractionDigits = if (hasFraction) 2 else 0
            minimumFractionDigits = if (hasFraction) 2 else 0
        }
        val formatted = nf.format(value)
        return when {
            template.contains("$") -> "$$formatted"
            template.contains("€") -> "$formatted €"
            template.contains("£") -> "£$formatted"
            else -> "$formatted ₺"
        }
    }

    suspend fun fetchProductPrice(urlStr: String): Pair<Double, String>? =
        withContext(Dispatchers.IO) {
            val normalizedUrl = normalizeProductUrl(urlStr)
            if (!SecurityGuards.isSafePublicHttpsUrl(normalizedUrl, maxLen = 1000)) {
                return@withContext null
            }

            val mobileResult = fetchAndExtractPrice(normalizedUrl, MOBILE_CHROME_UA)
            if (mobileResult != null) return@withContext mobileResult

            // Retry once with Desktop Chrome UA since some storefronts serve full SSR/JSON-LD on desktop
            fetchAndExtractPrice(normalizedUrl, DESKTOP_CHROME_UA)
        }

    private fun fetchAndExtractPrice(url: String, userAgent: String): Pair<Double, String>? {
        val conn = SecurityGuards.openSafeHttpsGet(
            initialUrl = url,
            allowedHosts = null,
            maxRedirects = 6,
            connectTimeoutMs = 10_000,
            readTimeoutMs = 12_000,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
                "Accept-Encoding" to "gzip",
                "Cache-Control" to "no-cache",
                "Sec-Fetch-Dest" to "document",
                "Sec-Fetch-Mode" to "navigate"
            )
        ) ?: return null

        return try {
            val effectiveUrl = conn.url?.toString()?.takeIf { it.isNotBlank() } ?: url
            val html = readBoundedText(conn)
            val currencyHint = detectCurrencyHint(html, effectiveUrl)
            val extracted = extractPriceFromHtml(html, effectiveUrl) ?: return null
            extracted to formatPrice(extracted, currencyHint)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun detectCurrencyHint(html: String, sourceUrl: String): String {
        return when {
            html.contains("\"priceCurrency\":\"USD\"", ignoreCase = true) ||
                html.contains("\"priceCurrency\": \"USD\"", ignoreCase = true) -> "$"
            html.contains("\"priceCurrency\":\"EUR\"", ignoreCase = true) ||
                html.contains("\"priceCurrency\": \"EUR\"", ignoreCase = true) -> "€"
            html.contains("\"priceCurrency\":\"GBP\"", ignoreCase = true) ||
                html.contains("\"priceCurrency\": \"GBP\"", ignoreCase = true) -> "£"
            sourceUrl.contains(".tr", ignoreCase = true) ||
                sourceUrl.contains("/tr/", ignoreCase = true) -> "₺"
            else -> "₺"
        }
    }

    private fun readBoundedText(conn: HttpsURLConnection): String {
        val isGzip = conn.contentEncoding?.contains("gzip", ignoreCase = true) == true
        val stream = if (isGzip) GZIPInputStream(conn.inputStream) else conn.inputStream
        val buf = CharArray(8192)
        val sb = StringBuilder()
        stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            while (sb.length < MAX_HTML_CHARS) {
                val maxRead = minOf(buf.size, MAX_HTML_CHARS - sb.length)
                val read = reader.read(buf, 0, maxRead)
                if (read <= 0) break
                sb.append(buf, 0, read)
            }
        }
        return sb.toString()
    }

    private fun isInditexUrl(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        return INDITEX_HOST_SUFFIXES.any { suffix -> host == suffix || host.endsWith(".$suffix") }
    }

    internal fun extractPriceFromHtml(html: String, sourceUrl: String = ""): Double? {
        if (html.isBlank()) return null
        val isInditex = isInditexUrl(sourceUrl)

        // 1. Parse JSON-LD structured product schema first (most accurate)
        extractFromJsonLdBlocks(html)?.let { return it }

        // 2. Meta & microdata price attributes
        val metaPatterns = listOf(
            """property=["'](?:product:price:amount|og:price:amount)["'][^>]*content=["']([0-9.,]+)["']""",
            """content=["']([0-9.,]+)["'][^>]*property=["'](?:product:price:amount|og:price:amount)["']""",
            """(?:name|property)=["']twitter:data1["'][^>]*content=["']([^"']{1,32})["']""",
            """content=["']([^"']{1,32})["'][^>]*(?:name|property)=["']twitter:data1["']""",
            """itemprop=["']price["'][^>]*content=["']([0-9.,]+)["']""",
            """content=["']([0-9.,]+)["'][^>]*itemprop=["']price["']""",
            """data-(?:product-)?(?:sale-)?price=["']([0-9.,]+)["']"""
        )
        for (rawPattern in metaPatterns) {
            val m = Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE).matcher(html)
            while (m.find()) {
                val candidate = m.group(1) ?: continue
                val parsed = parsePrice(candidate)
                if (parsed != null && parsed >= 10.0) return parsed
            }
        }

        // 3. Nested JSON price objects (Trendyol, Hepsiburada, Koton, DeFacto, LCW, H&M)
        val nestedJsonPattern = Pattern.compile(
            """"(?:discountedPrice|sellingPrice|salePrice|currentPrice|finalPrice|current|price)"\s*:\s*\{[^{}]{0,260}?"(?:value|amount|text)"\s*:\s*"?([0-9]+(?:[.,][0-9]+)*)"?""",
            Pattern.CASE_INSENSITIVE
        )
        nestedJsonPattern.matcher(html).let { m ->
            while (m.find()) {
                val rawToken = m.group(1) ?: continue
                val parsed = normalizeNumericCandidate(rawToken, isInditex)
                if (parsed != null) return parsed
            }
        }

        // 4. Flat JSON price keys (including Inditex cents-to-TL conversion)
        val flatJsonPattern = Pattern.compile(
            """"(?:discountedPrice|sellingPrice|salePrice|currentPrice|finalPrice|lowPrice|price)"\s*:\s*"?([0-9]+(?:[.,][0-9]+)*)"?""",
            Pattern.CASE_INSENSITIVE
        )
        flatJsonPattern.matcher(html).let { m ->
            while (m.find()) {
                val rawToken = m.group(1) ?: continue
                val parsed = normalizeNumericCandidate(rawToken, isInditex)
                if (parsed != null) return parsed
            }
        }

        // 5. Visible storefront currency text fallback
        val visiblePatterns = listOf(
            """([0-9]{1,3}(?:\.[0-9]{3})*(?:,[0-9]{1,2})?)\s*(?:TL|₺)""",
            """(?:₺|TL)\s*([0-9]{1,3}(?:\.[0-9]{3})*(?:,[0-9]{1,2})?)""",
            """([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:€|\$|£)"""
        )
        for (rawPattern in visiblePatterns) {
            val m = Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE).matcher(html)
            while (m.find()) {
                val candidate = m.group(1) ?: continue
                val parsed = parsePrice(candidate)
                if (parsed != null && parsed >= 10.0) return parsed
            }
        }
        return null
    }

    private fun normalizeNumericCandidate(rawToken: String, isInditex: Boolean): Double? {
        if (isInditex && !rawToken.contains('.') && !rawToken.contains(',')) {
            val intVal = rawToken.toLongOrNull()
            if (intVal != null && intVal >= 10_000L && intVal <= 9_999_999L) {
                val scaled = intVal / 100.0
                if (scaled >= 10.0 && scaled < 1_000_000.0) return scaled
            }
        }
        val parsed = parsePrice(rawToken)
        return parsed?.takeIf { it >= 10.0 }
    }

    private fun extractFromJsonLdBlocks(html: String): Double? {
        val matcher = JSON_LD_SCRIPT_PATTERN.matcher(html)
        val offerKeyPattern = Pattern.compile(
            """"(?:lowPrice|price|sellingPrice|discountedPrice)"\s*:\s*"?([0-9]+(?:[.,][0-9]+)*)"?""",
            Pattern.CASE_INSENSITIVE
        )
        val fallbackPrices = mutableListOf<Double>()
        while (matcher.find()) {
            val rawJson = matcher.group(1)?.trim() ?: continue
            if (rawJson.isEmpty()) continue
            val looksLikeProductOrOffer = rawJson.contains("Product", ignoreCase = true) ||
                rawJson.contains("Offer", ignoreCase = true) ||
                rawJson.contains("\"offers\"", ignoreCase = true) ||
                rawJson.contains("priceSpecification", ignoreCase = true)

            val m = offerKeyPattern.matcher(rawJson)
            while (m.find()) {
                val candidate = m.group(1) ?: continue
                val parsed = parsePrice(candidate)
                if (parsed != null && parsed >= 10.0) {
                    if (looksLikeProductOrOffer) return parsed
                    fallbackPrices.add(parsed)
                }
            }
        }
        return fallbackPrices.firstOrNull()
    }

    suspend fun checkAllWishlistPrices(
        context: Context,
        repository: WardrobeRepository
    ): PriceCheckSummary {
        repository.load()
        val tracked = repository.items.value.filter {
            it.isWishlist && isSafeHttpsUrl(it.productUrl)
        }
        var checked = 0
        var dropped = 0
        var firstDroppedName: String? = null

        for (item in tracked) {
            val cleanItemUrl = normalizeProductUrl(item.productUrl)
            val live = fetchProductPrice(cleanItemUrl) ?: continue
            checked++
            val (livePrice, liveFormatted) = live
            val baseline = item.lastCheckedPrice.takeIf { it > 0.0 } ?: parsePrice(item.priceText)

            if (baseline != null && livePrice < baseline - 0.5) {
                val oldText = item.priceText.ifBlank { formatPrice(baseline) }
                val updated = item.copy(
                    priceText = liveFormatted,
                    previousPrice = baseline,
                    lastCheckedPrice = livePrice,
                    priceDropped = true
                )
                repository.updateItem(updated)
                sendPriceDropNotification(
                    context = context,
                    itemName = item.name,
                    oldPriceText = oldText,
                    newPriceText = liveFormatted,
                    notificationId = item.id.hashCode()
                )
                dropped++
                if (firstDroppedName == null) firstDroppedName = item.name
            } else if (item.lastCheckedPrice <= 0.0 || item.priceText.isBlank()) {
                val updated = item.copy(
                    priceText = item.priceText.ifBlank { liveFormatted },
                    lastCheckedPrice = livePrice
                )
                repository.updateItem(updated)
            }
        }
        return PriceCheckSummary(
            checkedCount = checked,
            droppedCount = dropped,
            firstDroppedName = firstDroppedName
        )
    }

    fun sendPriceDropNotification(
        context: Context,
        itemName: String,
        oldPriceText: String,
        newPriceText: String,
        notificationId: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "İstek Listesi Fiyat Düşüşleri",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "İstek listesindeki ürünlerin fiyatı düştüğünde bildirim gönderir."
        }
        nm.createNotificationChannel(channel)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🔥 Fiyat Düştü: $itemName")
            .setContentText("$oldPriceText yerine şimdi $newPriceText! İstek listende seni bekliyor.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(notificationId, notification)
    }

    fun schedulePeriodicPriceCheck(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val existing = scheduler.getPendingJob(JOB_ID)
        if (existing != null) return

        val component = ComponentName(context, PriceCheckJobService::class.java)
        val info = JobInfo.Builder(JOB_ID, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(6L * 60L * 60L * 1000L) // every 6 hours
            .setPersisted(true)
            .build()
        scheduler.schedule(info)
    }
}

@SuppressLint("SpecifyJobSchedulerIdRange")
class PriceCheckJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        val appCtx = applicationContext
        serviceScope.launch {
            try {
                val repo = WardrobeRepository(appCtx)
                PriceTrackerService.checkAllWishlistPrices(appCtx, repo)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        serviceScope.cancel()
        return true
    }
}
