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
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection

data class PriceCheckSummary(
    val checkedCount: Int,
    val droppedCount: Int,
    val firstDroppedName: String? = null
)

object PriceTrackerService {

    private const val CHANNEL_ID = "dolabim_price_drops"
    private const val JOB_ID = 4105
    private const val MAX_HTML_CHARS = 180_000

    fun isSafeHttpsUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true) || trimmed.length > 500) return false
        return runCatching {
            val uri = URI(trimmed)
            !uri.host.isNullOrBlank() && !trimmed.contains(" ")
        }.getOrDefault(false)
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
            val cleanUrl = urlStr.trim()
            if (!isSafeHttpsUrl(cleanUrl)) return@withContext null

            var conn: HttpsURLConnection? = null
            try {
                conn = (URL(cleanUrl).openConnection() as HttpsURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                }

                val code = conn.responseCode
                if (code !in 200..299) return@withContext null

                // Only follow HTTPS redirects
                val finalUrl = conn.url?.toString().orEmpty()
                if (!finalUrl.startsWith("https://", ignoreCase = true)) return@withContext null

                val html = readBoundedText(conn)
                val currencyHint = when {
                    html.contains("\"priceCurrency\":\"USD\"", ignoreCase = true) -> "$"
                    html.contains("\"priceCurrency\":\"EUR\"", ignoreCase = true) -> "€"
                    else -> "₺"
                }

                val extracted = extractPriceFromHtml(html) ?: return@withContext null
                extracted to formatPrice(extracted, currencyHint)
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

    private fun readBoundedText(conn: HttpsURLConnection): String {
        val buf = CharArray(MAX_HTML_CHARS)
        var total = 0
        conn.inputStream.bufferedReader().use { reader ->
            while (total < MAX_HTML_CHARS) {
                val read = reader.read(buf, total, MAX_HTML_CHARS - total)
                if (read <= 0) break
                total += read
            }
        }
        return String(buf, 0, total)
    }

    private fun extractPriceFromHtml(html: String): Double? {
        val patterns = listOf(
            // Meta tags (OpenGraph / Schema.org)
            """property=["'](?:product:price:amount|og:price:amount)["'][^>]*content=["']([0-9.,]+)["']""",
            """content=["']([0-9.,]+)["'][^>]*property=["'](?:product:price:amount|og:price:amount)["']""",
            """itemprop=["']price["'][^>]*content=["']([0-9.,]+)["']""",
            """content=["']([0-9.,]+)["'][^>]*itemprop=["']price["']""",
            // JSON-LD & store API embedded JSON
            """"(?:discountedPrice|sellingPrice|salePrice|lowPrice|price)"\s*:\s*"?([0-9]+(?:[.,][0-9]+)?)"?""",
            // Turkish storefront visible currency text fallback
            """([0-9]{1,3}(?:\.[0-9]{3})*(?:,[0-9]{1,2})?)\s*(?:TL|₺)"""
        )

        for (rawPattern in patterns) {
            val m = Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE).matcher(html)
            while (m.find()) {
                val candidate = m.group(1) ?: continue
                val parsed = parsePrice(candidate)
                if (parsed != null && parsed >= 10.0) {
                    return parsed
                }
            }
        }
        return null
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
            val live = fetchProductPrice(item.productUrl) ?: continue
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
