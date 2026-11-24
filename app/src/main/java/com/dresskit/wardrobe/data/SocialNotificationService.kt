package com.dresskit.wardrobe.data

import android.Manifest
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dresskit.wardrobe.MainActivity
import com.dresskit.wardrobe.R
import com.dresskit.wardrobe.model.SocialNotificationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Polls per-user social notifications (follow requests, accepts, new followers, likes, comments)
 * and displays distinct OS notifications on the recipient device using channel "dolabim_social_notifications".
 */
object SocialNotificationService {

    const val CHANNEL_ID = "dolabim_social_notifications"
    const val JOB_ID = 74209
    const val EXTRA_OPEN_ACTIVITY_HEART = "open_activity_heart"
    private const val PREFS_NAME = "dolabim_social_notif_prefs"
    private const val KEY_NOTIFIED_IDS = "notified_ids_v1"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Dolabım Sosyal Bildirimler",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Takip istekleri, yeni takipçiler ve gönderi etkileşimleri"
        }
        nm.createNotificationChannel(ch)
    }

    fun schedulePeriodicChecks(context: Context) {
        ensureNotificationChannel(context)
        val appCtx = context.applicationContext
        val js = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val pending = js.allPendingJobs.any { it.id == JOB_ID }
        if (pending) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(appCtx, SocialNotificationJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(15L * 60_000L)
            .setPersisted(true)
            .build()
        runCatching { js.schedule(info) }
    }

    suspend fun pollAndDispatchNotifications(context: Context): Int {
        ensureNotificationChannel(context)
        val appCtx = context.applicationContext
        val list = SocialFeedService.fetchMyNotifications(appCtx).getOrNull() ?: return 0
        val unread = list.filter { !it.read }
        if (unread.isEmpty()) return 0

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        var changed = false

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appCtx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        unread.sortedBy { it.createdAtMs }.forEach { n ->
            if (n.notifId !in seen) {
                seen.add(n.notifId)
                changed = true
                if (canNotify) {
                    showOsNotification(appCtx, n)
                }
            }
        }
        if (changed) {
            val trimmed = seen.toList().takeLast(200).toSet()
            prefs.edit().putStringSet(KEY_NOTIFIED_IDS, trimmed).apply()
        }
        return unread.size
    }

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
            else -> "Dolabım Sosyal" to "$actor ${n.previewText}"
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ACTIVITY_HEART, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            n.notifId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(n.notifId.hashCode(), notif)
        }
    }
}

class SocialNotificationJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        serviceScope.launch {
            runCatching {
                SocialNotificationService.pollAndDispatchNotifications(applicationContext)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        serviceScope.cancel()
        return true
    }
}
