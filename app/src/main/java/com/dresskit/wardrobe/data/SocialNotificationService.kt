package com.dresskit.wardrobe.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dresskit.wardrobe.MainActivity
import com.dresskit.wardrobe.R
import com.dresskit.wardrobe.model.SocialNotificationData
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/**
 * Polls per-user social notifications (follow requests, accepts, new followers, likes, comments)
 * and displays distinct OS notifications on the recipient device using channel "dolabim_social_notifications".
 */
object SocialNotificationService {

    const val CHANNEL_ID = "dolabim_social_notifications"
    const val JOB_ID = 74209
    const val EXTRA_OPEN_ACTIVITY_HEART = "open_activity_heart"
    const val EXTRA_OPEN_DM_UID = "open_dm_uid"
    private const val PREFS_NAME = "dolabim_social_notif_prefs"
    private const val CHAT_CHANNEL = "dolabim_chat"

    fun ensureNotificationChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.createNotificationChannel(NotificationChannel(CHAT_CHANNEL, "Sohbet", NotificationManager.IMPORTANCE_HIGH))
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Sosyal ve Takip",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Takip istekleri, yeni takipçiler ve gönderi etkileşimleri"
        }
        nm.createNotificationChannel(ch)
    }

    fun schedulePeriodicChecks(context: Context) {
        ensureNotificationChannel(context)
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains("installedAt")) {
            appCtx.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
            prefs.edit().putLong("installedAt", System.currentTimeMillis()).apply()
        }
        val request = PeriodicWorkRequestBuilder<SocialNotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork("social-inbox", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun dispatchNew(context: Context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (AnalyticsService.getCurrentUid(context).isEmpty()) return
        val (uid, token) = checkNotNull(AnalyticsService.getOrCreateAuthCredentials(context))
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong("time:$uid", prefs.getLong("installedAt", System.currentTimeMillis()))
        val page = SocialFeedService.notificationPage(uid, token, lastSeen, prefs.getString("name:$uid", "") ?: "")
        for ((name, n) in page) {
            acknowledge(context, name, n)
        }
    }

    @Synchronized
    private fun acknowledge(context: Context, name: String, n: SocialNotificationData) {
        dispatchUnread(context, listOf(n))
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "shown:${n.targetUid}"
        val outstanding = prefs.getStringSet(key, emptySet()).orEmpty() - n.notifId
        prefs.edit().putLong("time:${n.targetUid}", n.createdAtMs).putString("name:${n.targetUid}", name)
            .putStringSet(key, outstanding).apply()
    }

    @Synchronized
    fun dispatchUnread(context: Context, rows: List<SocialNotificationData>) {
        if (rows.isEmpty() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = rows.first().targetUid
        val baseline = prefs.getLong("time:$uid", prefs.getLong("installedAt", System.currentTimeMillis()))
        val key = "shown:$uid"
        val shown = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        val lastId = prefs.getString("name:$uid", "").orEmpty().substringAfterLast('/')
        val fresh = rows.filter {
            !it.read && (it.createdAtMs > baseline || (it.createdAtMs == baseline && it.notifId > lastId)) && it.notifId !in shown
        }
        if (fresh.isEmpty()) return
        for (n in fresh) {
            if (!n.type.startsWith("dm_") || !DirectMessageService.isConversationMuted(context, n.fromUid)) {
                showOsNotification(context, n)
            }
            shown.add(n.notifId)
        }
        prefs.edit().putStringSet(key, shown).apply()
    }

    @SuppressLint("MissingPermission")
    private fun showOsNotification(context: Context, n: SocialNotificationData) {
        val actor = when {
            n.fromUsername.isNotBlank() -> "@${n.fromUsername}"
            n.fromDisplayName.isNotBlank() -> n.fromDisplayName
            else -> "Bir kullanıcı"
        }
        val (title, body) = when (n.type) {
            "follow_request" -> "Yeni Takip İsteği" to "$actor seni takip etmek istedi"
            "follow_accepted" -> "Takip İsteği Onaylandı" to "$actor takip isteğini kabul etti"
            "new_follower" -> "Yeni Takipçi" to "$actor seni takip etmeye başladı"
            "post_like" -> "Yeni Beğeni" to "$actor gönderini beğendi"
            "post_comment" -> "Yeni Yorum" to "$actor: ${n.previewText.ifBlank { "gönderine yorum yaptı" }}"
            "dm_request" -> "Yeni Mesaj İsteği" to "$actor: ${n.previewText.ifBlank { "sana mesaj isteği gönderdi" }}"
            "dm_post_reply" -> "Gönderine Özel Yanıt" to "$actor: ${n.previewText.ifBlank { "gönderine yanıt verdi" }}"
            "dm_message" -> "Yeni Mesaj" to "$actor: ${n.previewText.ifBlank { "mesaj gönderdi" }}"
            else -> "Dolabım Sosyal" to "$actor ${n.previewText}"
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (n.type == "dm_message" || n.type == "dm_request" || n.type == "dm_post_reply") {
                putExtra(EXTRA_OPEN_DM_UID, n.fromUid)
            } else {
                putExtra(EXTRA_OPEN_ACTIVITY_HEART, true)
            }
        }
        val pi = PendingIntent.getActivity(
            context,
            n.notifId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, if (n.type.startsWith("dm_")) CHAT_CHANNEL else CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(n.notifId.hashCode(), notif)
    }
}

class SocialNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            SocialNotificationService.dispatchNew(applicationContext)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
