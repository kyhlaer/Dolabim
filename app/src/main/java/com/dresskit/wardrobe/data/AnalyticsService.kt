package com.dresskit.wardrobe.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

/**
 * Anonymous, strict-allowlist Firestore REST usage telemetry client.
 * Collects session counts, active status, tab/feature durations, coarse IP region,
 * device metadata, and sanitized errors without ever reading photos, garment names, or GPS.
 */
object AnalyticsService {

    private const val FIREBASE_PROJECT_ID = "dolabim-stats"
    private const val FIREBASE_API_KEY = "AIzaSyDcAnCMY6oXLShrR36brMLr6_d7Dhu1DGQ"
    private const val APP_VERSION = "1.43.282"
    private const val PREFS_NAME = "dolabim_telemetry"
    private const val KEY_ENABLED = "analytics_enabled"
    private const val KEY_UID = "auth_uid"
    private const val KEY_ID_TOKEN = "auth_id_token"
    private const val KEY_REFRESH_TOKEN = "auth_refresh_token"
    private const val KEY_EXPIRES_AT = "auth_expires_ms"
    private const val KEY_FIRST_SEEN = "first_seen_ms"
    private const val KEY_LAST_SENT_SEEN = "last_sent_seen_ms"
    private const val KEY_SESSION_COUNT = "session_count"
    private const val KEY_TOTAL_DURATION = "total_duration_sec"
    private const val KEY_ERROR_COUNT = "error_count"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_LIFE_FEAT_COUNTS = "lifetime_feature_counts_json"
    private const val KEY_LIFE_TAB_SECS = "lifetime_tab_seconds_json"
    private const val KEY_LIFE_FEAT_SECS = "lifetime_feature_seconds_json"
    private const val KEY_GEO_COUNTRY = "geo_country"
    private const val KEY_GEO_CITY = "geo_city"
    private const val KEY_GEO_REGION = "geo_region"
    private const val KEY_GEO_TS = "geo_fetched_ms"
    private const val KEY_PROF_USER = "profile_username"
    private const val KEY_PROF_DISPLAY = "profile_display_name"
    private const val KEY_PROF_AVATAR = "profile_avatar_thumb"

    private const val MIN_VALID_EPOCH_MS = 1700000000001L
    private const val MAX_VALID_EPOCH_MS = 4102444800000L
    private val UID_REGEX = Regex("^[A-Za-z0-9_-]{1,128}$")

    private val FIREBASE_ALLOWED_HOSTS = setOf(
        "identitytoolkit.googleapis.com",
        "securetoken.googleapis.com",
        "firestore.googleapis.com"
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val networkLock = Any()

    private var appContext: Context? = null
    private var heartbeatJob: Job? = null

    private var activeSessionId: String = ""
    private var sessionStartedAtMs: Long = 0L
    private var sessionLastActiveAtMs: Long = 0L
    private var baseLifetimeDurationSec: Long = 0L
    private var entrySource: String = "cold_start"
    private var currentTab: String = "wardrobe"
    private var lastTabSwitchAtMs: Long = 0L
    private var isForeground: Boolean = false

    private var currentTheme: String = "MODERN"
    private var currentLanguage: String = "TR"
    private var wardrobeItemCount: Int = 0
    private var wishlistCount: Int = 0
    private var outfitCount: Int = 0
    private var profileUsername: String = ""
    private var profileDisplayName: String = ""
    private var profileAvatarThumb: String = ""

    private val sessionFeatureCounts = ConcurrentHashMap<String, Int>()
    private val sessionTabSeconds = ConcurrentHashMap<String, Int>()
    private val sessionFeatureSeconds = ConcurrentHashMap<String, Int>()
    private val featureStartTimes = ConcurrentHashMap<String, Long>()
    private val recentEvents = ArrayList<String>()

    fun isEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        appContext = app
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (!enabled) {
            onAppBackground(app)
        } else {
            onAppForeground(app)
        }
    }

    fun initContext(
        context: Context,
        theme: String,
        language: String,
        itemsCount: Int,
        wishlistItemsCount: Int,
        outfitsCount: Int
    ) {
        val app = context.applicationContext
        appContext = app
        currentTheme = theme.take(32).ifBlank { "MODERN" }
        currentLanguage = language.take(12).ifBlank { "TR" }
        wardrobeItemCount = itemsCount.coerceIn(0, 100000)
        wishlistCount = wishlistItemsCount.coerceIn(0, 100000)
        outfitCount = outfitsCount.coerceIn(0, 100000)
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (profileUsername.isBlank()) {
            profileUsername = prefs.getString(KEY_PROF_USER, "")?.take(32).orEmpty()
        }
        if (profileDisplayName.isBlank()) {
            profileDisplayName = prefs.getString(KEY_PROF_DISPLAY, "")?.take(60).orEmpty()
        }
        if (profileAvatarThumb.isBlank()) {
            profileAvatarThumb = prefs.getString(KEY_PROF_AVATAR, "")?.take(35000).orEmpty()
        }
    }

    fun getCurrentUid(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString(KEY_UID, "") ?: ""
        return if (UID_REGEX.matches(uid)) uid else ""
    }

    fun getOrCreateAuthCredentials(context: Context): Pair<String, String>? {
        val app = context.applicationContext
        appContext = app
        return synchronized(networkLock) {
            ensureAnonymousAuth(app)
        }
    }

    fun updateProfileIdentity(
        context: Context,
        username: String,
        displayName: String,
        avatarThumbBase64: String
    ) {
        val app = context.applicationContext
        appContext = app
        val cleanUser = username.trim().take(32)
        val cleanDisplay = displayName.trim().take(60)
        val cleanAvatar = if (avatarThumbBase64.startsWith("data:image/") && avatarThumbBase64.length <= 35000) {
            avatarThumbBase64
        } else {
            ""
        }
        synchronized(lock) {
            profileUsername = cleanUser
            profileDisplayName = cleanDisplay
            profileAvatarThumb = cleanAvatar
        }
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROF_USER, cleanUser)
            .putString(KEY_PROF_DISPLAY, cleanDisplay)
            .putString(KEY_PROF_AVATAR, cleanAvatar)
            .apply()
        if (isEnabled(app) && isForeground) {
            ioScope.launch {
                flushNow(app, online = true)
            }
        }
    }

    fun onAppForeground(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!isEnabled(app)) return
        synchronized(lock) {
            if (isForeground && activeSessionId.isNotEmpty()) return
            isForeground = true
            startNewSessionLocked(app, if (sessionStartedAtMs == 0L) "cold_start" else "foreground")
        }
        startHeartbeatLoop(app)
        ioScope.launch {
            fetchGeoIfStale(app)
            flushNow(app, online = true)
        }
    }

    fun onAppBackground(context: Context) {
        val app = context.applicationContext
        appContext = app
        heartbeatJob?.cancel()
        heartbeatJob = null
        synchronized(lock) {
            if (!isForeground && activeSessionId.isEmpty()) return
            isForeground = false
            accumulateCurrentTabSecondsLocked(System.currentTimeMillis())
        }
        ioScope.launch {
            flushNow(app, online = false)
            synchronized(lock) {
                activeSessionId = ""
            }
        }
    }

    fun onTabChanged(tabKey: String) {
        val app = appContext ?: return
        if (!isEnabled(app)) return
        val clean = tabKey.take(32).ifBlank { "wardrobe" }
        synchronized(lock) {
            if (currentTab == clean) return
            accumulateCurrentTabSecondsLocked(System.currentTimeMillis())
            currentTab = clean
            appendEventLocked("tab", clean, "")
        }
    }

    fun trackFeature(name: String, detail: String = "") {
        val app = appContext ?: return
        if (!isEnabled(app)) return
        val cleanName = name.take(40).replace('|', '_').ifBlank { return }
        val cleanDetail = detail.take(48).replace('|', '_')
        synchronized(lock) {
            sessionFeatureCounts[cleanName] = (sessionFeatureCounts[cleanName] ?: 0) + 1
            incrementPrefsMap(app, KEY_LIFE_FEAT_COUNTS, cleanName, 1, 50)
            appendEventLocked("feature", cleanName, cleanDetail)
        }
    }

    fun startFeatureTimer(name: String) {
        val cleanName = name.take(40).ifBlank { return }
        featureStartTimes[cleanName] = System.currentTimeMillis()
    }

    fun endFeatureTimer(name: String) {
        val app = appContext ?: return
        if (!isEnabled(app)) return
        val cleanName = name.take(40).ifBlank { return }
        val started = featureStartTimes.remove(cleanName) ?: return
        val elapsedSec = ((System.currentTimeMillis() - started) / 1000L).toInt().coerceIn(0, 7200)
        if (elapsedSec <= 0) return
        synchronized(lock) {
            sessionFeatureSeconds[cleanName] = (sessionFeatureSeconds[cleanName] ?: 0) + elapsedSec
            incrementPrefsMap(app, KEY_LIFE_FEAT_SECS, cleanName, elapsedSec, 30)
        }
    }

    fun trackError(tag: String, message: String) {
        val app = appContext ?: return
        if (!isEnabled(app)) return
        val cleanMsg = "${tag.take(30)}: ${message.replace(Regex("[\\r\\n\\t]+"), " ").take(140)}".take(180)
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        synchronized(lock) {
            val nextErrCnt = (prefs.getInt(KEY_ERROR_COUNT, 0) + 1).coerceAtMost(100000)
            prefs.edit()
                .putInt(KEY_ERROR_COUNT, nextErrCnt)
                .putString(KEY_LAST_ERROR, cleanMsg)
                .apply()
            sessionFeatureCounts["error"] = (sessionFeatureCounts["error"] ?: 0) + 1
            incrementPrefsMap(app, KEY_LIFE_FEAT_COUNTS, "error", 1, 50)
            appendEventLocked("error", tag.take(32), message.take(64).replace('|', '_'))
        }
    }

    private fun startNewSessionLocked(app: Context, source: String) {
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis().coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS - 10000L)
        sessionStartedAtMs = now
        sessionLastActiveAtMs = now
        lastTabSwitchAtMs = now
        entrySource = source.take(32)
        val existingUid = prefs.getString(KEY_UID, "") ?: ""
        activeSessionId = if (UID_REGEX.matches(existingUid)) "${existingUid}_$now" else ""

        val nextCount = (prefs.getInt(KEY_SESSION_COUNT, 0) + 1).coerceIn(1, 1000000)
        baseLifetimeDurationSec = prefs.getLong(KEY_TOTAL_DURATION, 0L).coerceAtLeast(0L)
        val firstSeen = prefs.getLong(KEY_FIRST_SEEN, 0L)
        val editor = prefs.edit().putInt(KEY_SESSION_COUNT, nextCount)
        if (firstSeen !in MIN_VALID_EPOCH_MS..MAX_VALID_EPOCH_MS) {
            editor.putLong(KEY_FIRST_SEEN, now)
        }
        editor.apply()

        sessionFeatureCounts.clear()
        sessionTabSeconds.clear()
        sessionFeatureSeconds.clear()
        recentEvents.clear()
        appendEventLocked("session", source, currentTab)
    }

    private fun accumulateCurrentTabSecondsLocked(nowMs: Long) {
        if (lastTabSwitchAtMs <= 0L) {
            lastTabSwitchAtMs = nowMs
            return
        }
        val elapsedSec = ((nowMs - lastTabSwitchAtMs) / 1000L).toInt().coerceIn(0, 7200)
        lastTabSwitchAtMs = nowMs
        if (elapsedSec <= 0) return
        val app = appContext
        sessionTabSeconds[currentTab] = (sessionTabSeconds[currentTab] ?: 0) + elapsedSec
        if (app != null) {
            incrementPrefsMap(app, KEY_LIFE_TAB_SECS, currentTab, elapsedSec, 10)
        }
    }

    private fun appendEventLocked(kind: String, name: String, detail: String) {
        val now = System.currentTimeMillis()
        val entry = "$now|$kind|$name|$detail".take(160)
        if (recentEvents.size >= 40) {
            recentEvents.removeAt(0)
        }
        recentEvents.add(entry)
    }

    private fun startHeartbeatLoop(app: Context) {
        heartbeatJob?.cancel()
        heartbeatJob = ioScope.launch {
            while (isActive && isForeground && isEnabled(app)) {
                delay(60_000L)
                if (!isForeground || !isEnabled(app)) break
                synchronized(lock) {
                    accumulateCurrentTabSecondsLocked(System.currentTimeMillis())
                }
                flushNow(app, online = true)
            }
        }
    }

    private fun flushNow(app: Context, online: Boolean) {
        if (!isEnabled(app)) return
        synchronized(networkLock) {
            val creds = ensureAnonymousAuth(app) ?: return
            val uid = creds.first
            val idToken = creds.second
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val snapshot: Pair<JSONObject, JSONObject>
            val curSessionId: String
            synchronized(lock) {
                if (sessionStartedAtMs !in MIN_VALID_EPOCH_MS..MAX_VALID_EPOCH_MS) {
                    startNewSessionLocked(app, "recover")
                }
                if (!activeSessionId.startsWith("${uid}_")) {
                    activeSessionId = "${uid}_$sessionStartedAtMs"
                }
                curSessionId = activeSessionId

                val rawNow = System.currentTimeMillis().coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
                val storedFirst = prefs.getLong(KEY_FIRST_SEEN, sessionStartedAtMs)
                    .coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
                val prevSentSeen = prefs.getLong(KEY_LAST_SENT_SEEN, storedFirst)
                val effectiveNow = maxOf(rawNow, maxOf(storedFirst, maxOf(prevSentSeen, sessionStartedAtMs)))
                    .coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
                sessionLastActiveAtMs = maxOf(sessionLastActiveAtMs, effectiveNow)

                val sessionDurationSec = ((sessionLastActiveAtMs - sessionStartedAtMs) / 1000L)
                    .toInt()
                    .coerceIn(0, 172800)
                val totalDurationSec = (baseLifetimeDurationSec + sessionDurationSec)
                    .coerceIn(0L, 1000000000L)

                prefs.edit()
                    .putLong(KEY_FIRST_SEEN, storedFirst)
                    .putLong(KEY_LAST_SENT_SEEN, sessionLastActiveAtMs)
                    .putLong(KEY_TOTAL_DURATION, totalDurationSec)
                    .apply()

                val dm = app.resources.displayMetrics
                val screenSize = "${dm.widthPixels}x${dm.heightPixels}".take(32)
                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(120)
                val osInfo = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})".take(100)
                val tz = (TimeZone.getDefault().id ?: "").take(64)
                val country = (prefs.getString(KEY_GEO_COUNTRY, "") ?: "").take(64)
                val city = (prefs.getString(KEY_GEO_CITY, "") ?: "").take(64)
                val region = (prefs.getString(KEY_GEO_REGION, "") ?: "").take(64)
                val anonTag = ("A-" + uid.take(6).uppercase(Locale.ROOT)).take(24)
                val curProfUser = profileUsername.ifBlank { prefs.getString(KEY_PROF_USER, "") ?: "" }.take(32)
                val curProfDisplay = profileDisplayName.ifBlank { prefs.getString(KEY_PROF_DISPLAY, "") ?: "" }.take(60)
                val curProfAvatar = profileAvatarThumb.ifBlank { prefs.getString(KEY_PROF_AVATAR, "") ?: "" }.take(35000)

                val deviceJson = buildDeviceDocumentJson(
                    uid = uid,
                    anonTag = anonTag,
                    platform = "android",
                    appVersion = APP_VERSION,
                    deviceModel = deviceModel,
                    osInfo = osInfo,
                    screenSize = screenSize,
                    language = currentLanguage,
                    theme = currentTheme,
                    timezone = tz,
                    country = country,
                    city = city,
                    region = region,
                    firstSeenAtMs = storedFirst,
                    lastSeenAtMs = sessionLastActiveAtMs,
                    sessionCount = prefs.getInt(KEY_SESSION_COUNT, 1).coerceIn(1, 1000000),
                    totalDurationSec = totalDurationSec,
                    activeSessionId = if (online) curSessionId else "",
                    lastTab = currentTab,
                    isOnline = online,
                    wardrobeItemCount = wardrobeItemCount,
                    wishlistCount = wishlistCount,
                    outfitCount = outfitCount,
                    lifetimeFeatureCounts = readPrefsMap(app, KEY_LIFE_FEAT_COUNTS, 50),
                    lifetimeTabSeconds = readPrefsMap(app, KEY_LIFE_TAB_SECS, 10),
                    lifetimeFeatureSeconds = readPrefsMap(app, KEY_LIFE_FEAT_SECS, 30),
                    errorCount = prefs.getInt(KEY_ERROR_COUNT, 0).coerceIn(0, 100000),
                    lastError = (prefs.getString(KEY_LAST_ERROR, "") ?: "").take(180),
                    profileUsername = curProfUser,
                    profileDisplayName = curProfDisplay,
                    profileAvatar = curProfAvatar
                )

                val sessionJson = buildSessionDocumentJson(
                    uid = uid,
                    sessionId = curSessionId,
                    anonTag = anonTag,
                    platform = "android",
                    appVersion = APP_VERSION,
                    deviceModel = deviceModel,
                    osInfo = osInfo,
                    screenSize = screenSize,
                    language = currentLanguage,
                    theme = currentTheme,
                    timezone = tz,
                    country = country,
                    city = city,
                    region = region,
                    entrySource = entrySource,
                    networkType = detectNetworkType(app),
                    startedAtMs = sessionStartedAtMs,
                    lastActiveAtMs = sessionLastActiveAtMs,
                    durationSec = sessionDurationSec,
                    activeTab = currentTab,
                    isOnline = online,
                    tabSeconds = sessionTabSeconds.toMap(),
                    featureCounts = sessionFeatureCounts.toMap(),
                    featureSeconds = sessionFeatureSeconds.toMap(),
                    recentEvents = recentEvents.toList(),
                    wardrobeStats = mapOf(
                        "items" to wardrobeItemCount,
                        "wishlist" to wishlistCount,
                        "outfits" to outfitCount
                    ),
                    profileUsername = curProfUser,
                    profileDisplayName = curProfDisplay
                )
                snapshot = deviceJson to sessionJson
            }

            val devUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/(default)/documents/telemetry_devices/${URLEncoder.encode(uid, "UTF-8")}"
            val sessUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/(default)/documents/telemetry_sessions/${URLEncoder.encode(curSessionId, "UTF-8")}"
            val devCode = patchFirestoreDoc(devUrl, idToken, snapshot.first)
            if (devCode == 403) {
                val coreFields = JSONObject(snapshot.first.toString())
                coreFields.optJSONObject("fields")?.apply {
                    remove("profileUsername")
                    remove("profileDisplayName")
                    remove("profileAvatar")
                }
                patchFirestoreDoc(devUrl, idToken, coreFields)
            }
            val sessCode = patchFirestoreDoc(sessUrl, idToken, snapshot.second)
            if (sessCode == 403) {
                val coreSess = JSONObject(snapshot.second.toString())
                coreSess.optJSONObject("fields")?.apply {
                    remove("profileUsername")
                    remove("profileDisplayName")
                }
                patchFirestoreDoc(sessUrl, idToken, coreSess)
            }
        }
    }

    private fun ensureAnonymousAuth(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString(KEY_UID, "") ?: ""
        val token = prefs.getString(KEY_ID_TOKEN, "") ?: ""
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        if (UID_REGEX.matches(uid) && token.isNotBlank() && System.currentTimeMillis() < expiresAt) {
            return uid to token
        }

        if (UID_REGEX.matches(uid) && refresh.isNotBlank()) {
            val refreshed = postFormJson(
                "https://securetoken.googleapis.com/v1/token?key=${URLEncoder.encode(FIREBASE_API_KEY, "UTF-8")}",
                "grant_type=refresh_token&refresh_token=${URLEncoder.encode(refresh, "UTF-8")}"
            )
            val nextId = refreshed?.optString("id_token") ?: ""
            val nextRef = refreshed?.optString("refresh_token") ?: refresh
            val nextUid = refreshed?.optString("user_id") ?: uid
            val expSec = refreshed?.optString("expires_in")?.toLongOrNull() ?: 3600L
            if (nextId.isNotBlank() && nextUid == uid) {
                prefs.edit()
                    .putString(KEY_UID, nextUid)
                    .putString(KEY_ID_TOKEN, nextId)
                    .putString(KEY_REFRESH_TOKEN, nextRef)
                    .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (expSec - 90).coerceAtLeast(60) * 1000L)
                    .apply()
                return nextUid to nextId
            }
        }

        // A temporary refresh failure must never replace the account's social identity.
        if (uid.isNotBlank() || refresh.isNotBlank()) return null
        val signUp = postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${URLEncoder.encode(FIREBASE_API_KEY, "UTF-8")}",
            JSONObject().put("returnSecureToken", true).toString()
        ) ?: return null
        val newUid = signUp.optString("localId")
        val newToken = signUp.optString("idToken")
        val newRefresh = signUp.optString("refreshToken")
        val expSec = signUp.optString("expiresIn").toLongOrNull() ?: 3600L
        if (!UID_REGEX.matches(newUid) || newToken.isBlank()) return null

        val now = System.currentTimeMillis().coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS - 10000L)
        prefs.edit()
            .putString(KEY_UID, newUid)
            .putString(KEY_ID_TOKEN, newToken)
            .putString(KEY_REFRESH_TOKEN, newRefresh)
            .putLong(KEY_EXPIRES_AT, now + (expSec - 90).coerceAtLeast(60) * 1000L)
            .putLong(KEY_FIRST_SEEN, now)
            .putLong(KEY_LAST_SENT_SEEN, now)
            .apply()
        return newUid to newToken
    }

    private fun fetchGeoIfStale(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_GEO_TS, 0L)
        if (System.currentTimeMillis() - last < 24 * 3600_000L) return
        val conn = SecurityGuards.openSafeHttpsGet(
            initialUrl = "https://ipwho.is/",
            allowedHosts = setOf("ipwho.is"),
            maxRedirects = 1,
            connectTimeoutMs = 4000,
            readTimeoutMs = 4000
        ) ?: return
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optBoolean("success", false)) {
                prefs.edit()
                    .putString(KEY_GEO_COUNTRY, json.optString("country", "").take(64))
                    .putString(KEY_GEO_CITY, json.optString("city", "").take(64))
                    .putString(KEY_GEO_REGION, json.optString("region", "").take(64))
                    .putLong(KEY_GEO_TS, System.currentTimeMillis())
                    .apply()
            }
        } catch (_: Exception) {
            // Best effort only
        } finally {
            conn.disconnect()
        }
    }

    private fun detectNetworkType(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "unknown"
            val net = cm.activeNetwork ?: return "offline"
            val caps = cm.getNetworkCapabilities(net) ?: return "unknown"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun patchFirestoreDoc(url: String, idToken: String, fieldsDoc: JSONObject): Int {
        if (!SecurityGuards.isSafePublicHttpsUrl(url, 2048)) return 0
        val uri = java.net.URI(url)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return 0
        if (host !in FIREBASE_ALLOWED_HOSTS || !SecurityGuards.isPublicHostResolved(host)) return 0

        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as? HttpsURLConnection)?.apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = false
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
            } ?: return 0
            BufferedWriter(OutputStreamWriter(conn.outputStream, Charsets.UTF_8)).use {
                it.write(fieldsDoc.toString())
            }
            conn.responseCode
        } catch (_: Exception) {
            0
        } finally {
            conn?.disconnect()
        }
    }

    private fun postJson(url: String, jsonBody: String): JSONObject? {
        return postRaw(url, "application/json; charset=UTF-8", jsonBody)
    }

    private fun postFormJson(url: String, formBody: String): JSONObject? {
        return postRaw(url, "application/x-www-form-urlencoded", formBody)
    }

    private fun postRaw(url: String, contentType: String, body: String): JSONObject? {
        if (!SecurityGuards.isSafePublicHttpsUrl(url, 2048)) return null
        val host = runCatching { java.net.URI(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return null
        if (host !in FIREBASE_ALLOWED_HOSTS || !SecurityGuards.isPublicHostResolved(host)) return null

        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as? HttpsURLConnection)?.apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = false
                requestMethod = "POST"
                setRequestProperty("Content-Type", contentType)
                doOutput = true
            } ?: return null
            BufferedWriter(OutputStreamWriter(conn.outputStream, Charsets.UTF_8)).use {
                it.write(body)
            }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun readPrefsMap(context: Context, key: String, maxKeys: Int): Map<String, Int> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, null)
            ?: return emptyMap()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, Int>()
        val keys = obj.keys()
        while (keys.hasNext() && out.size < maxKeys) {
            val k = keys.next()
            out[k] = obj.optInt(k, 0).coerceAtLeast(0)
        }
        return out
    }

    private fun incrementPrefsMap(context: Context, prefKey: String, entryKey: String, delta: Int, maxKeys: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = runCatching { JSONObject(prefs.getString(prefKey, "{}") ?: "{}") }.getOrNull() ?: JSONObject()
        if (!obj.has(entryKey) && obj.length() >= maxKeys) return
        val nextVal = (obj.optInt(entryKey, 0) + delta).coerceIn(0, 1_000_000_000)
        obj.put(entryKey, nextVal)
        prefs.edit().putString(prefKey, obj.toString()).apply()
    }

    private fun strVal(v: String) = JSONObject().put("stringValue", v)
    private fun intVal(v: Long) = JSONObject().put("integerValue", v.toString())
    private fun boolVal(v: Boolean) = JSONObject().put("booleanValue", v)
    private fun intMapVal(map: Map<String, Int>, maxEntries: Int): JSONObject {
        val fields = JSONObject()
        var count = 0
        for ((k, v) in map) {
            if (count >= maxEntries) break
            val cleanKey = k.take(40)
            if (cleanKey.isNotEmpty()) {
                fields.put(cleanKey, intVal(v.toLong().coerceAtLeast(0L)))
                count++
            }
        }
        return JSONObject().put("mapValue", JSONObject().put("fields", fields))
    }

    private fun strListVal(list: List<String>, maxEntries: Int): JSONObject {
        val arr = JSONArray()
        for (item in list.takeLast(maxEntries)) {
            arr.put(strVal(item.take(180)))
        }
        return JSONObject().put("arrayValue", JSONObject().put("values", arr))
    }

    fun buildDeviceSchemaMap(
        uid: String,
        anonTag: String,
        platform: String,
        appVersion: String,
        deviceModel: String,
        osInfo: String,
        screenSize: String,
        language: String,
        theme: String,
        timezone: String,
        country: String,
        city: String,
        region: String,
        firstSeenAtMs: Long,
        lastSeenAtMs: Long,
        sessionCount: Int,
        totalDurationSec: Long,
        activeSessionId: String,
        lastTab: String,
        isOnline: Boolean,
        wardrobeItemCount: Int,
        wishlistCount: Int,
        outfitCount: Int,
        lifetimeFeatureCounts: Map<String, Int>,
        lifetimeTabSeconds: Map<String, Int>,
        lifetimeFeatureSeconds: Map<String, Int>,
        errorCount: Int,
        lastError: String,
        profileUsername: String = "",
        profileDisplayName: String = "",
        profileAvatar: String = ""
    ): LinkedHashMap<String, Any> {
        val first = firstSeenAtMs.coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
        val last = maxOf(first, lastSeenAtMs).coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
        val safePlatform = if (platform in setOf("android", "web_pwa", "web_browser")) platform else "android"
        val map = linkedMapOf<String, Any>(
            "uid" to uid.take(128),
            "anonTag" to anonTag.take(24).ifBlank { "A-USER" },
            "platform" to safePlatform,
            "appVersion" to appVersion.take(32).ifBlank { APP_VERSION },
            "deviceModel" to deviceModel.take(120),
            "osInfo" to osInfo.take(100),
            "screenSize" to screenSize.take(32),
            "language" to language.take(12).ifBlank { "TR" },
            "theme" to theme.take(32).ifBlank { "MODERN" },
            "timezone" to timezone.take(64),
            "country" to country.take(64),
            "city" to city.take(64),
            "region" to region.take(64),
            "firstSeenAtMs" to first,
            "lastSeenAtMs" to last,
            "sessionCount" to sessionCount.coerceIn(1, 1000000).toLong(),
            "totalDurationSec" to totalDurationSec.coerceIn(0L, 1000000000L),
            "activeSessionId" to activeSessionId.take(160),
            "lastTab" to lastTab.take(32),
            "isOnline" to isOnline,
            "wardrobeItemCount" to wardrobeItemCount.coerceIn(0, 100000).toLong(),
            "wishlistCount" to wishlistCount.coerceIn(0, 100000).toLong(),
            "outfitCount" to outfitCount.coerceIn(0, 100000).toLong(),
            "lifetimeFeatureCounts" to lifetimeFeatureCounts.entries.take(50).associate { it.key.take(40) to it.value.coerceAtLeast(0) },
            "lifetimeTabSeconds" to lifetimeTabSeconds.entries.take(10).associate { it.key.take(40) to it.value.coerceAtLeast(0) },
            "lifetimeFeatureSeconds" to lifetimeFeatureSeconds.entries.take(30).associate { it.key.take(40) to it.value.coerceAtLeast(0) },
            "errorCount" to errorCount.coerceIn(0, 100000).toLong(),
            "lastError" to lastError.take(180)
        )
        if (profileUsername.isNotBlank()) map["profileUsername"] = profileUsername.take(32)
        if (profileDisplayName.isNotBlank()) map["profileDisplayName"] = profileDisplayName.take(60)
        if (profileAvatar.isNotBlank()) map["profileAvatar"] = profileAvatar.take(35000)
        return map
    }

    fun buildSessionSchemaMap(
        uid: String,
        sessionId: String,
        anonTag: String,
        platform: String,
        appVersion: String,
        deviceModel: String,
        osInfo: String,
        screenSize: String,
        language: String,
        theme: String,
        timezone: String,
        country: String,
        city: String,
        region: String,
        entrySource: String,
        networkType: String,
        startedAtMs: Long,
        lastActiveAtMs: Long,
        durationSec: Int,
        activeTab: String,
        isOnline: Boolean,
        tabSeconds: Map<String, Int>,
        featureCounts: Map<String, Int>,
        featureSeconds: Map<String, Int>,
        recentEvents: List<String>,
        wardrobeStats: Map<String, Int>,
        profileUsername: String = "",
        profileDisplayName: String = ""
    ): LinkedHashMap<String, Any> {
        val start = startedAtMs.coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
        val last = maxOf(start, lastActiveAtMs).coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
        val safePlatform = if (platform in setOf("android", "web_pwa", "web_browser")) platform else "android"
        return linkedMapOf(
            "uid" to uid.take(128),
            "sessionId" to sessionId.take(160),
            "anonTag" to anonTag.take(24).ifBlank { "A-USER" },
            "platform" to safePlatform,
            "appVersion" to appVersion.take(32).ifBlank { APP_VERSION },
            "deviceModel" to deviceModel.take(120),
            "osInfo" to osInfo.take(100),
            "screenSize" to screenSize.take(32),
            "language" to language.take(12).ifBlank { "TR" },
            "theme" to theme.take(32).ifBlank { "MODERN" },
            "timezone" to timezone.take(64),
            "country" to country.take(64),
            "city" to city.take(64),
            "region" to region.take(64),
            "entrySource" to entrySource.take(32).ifBlank { "cold_start" },
            "networkType" to networkType.take(32),
            "startedAtMs" to start,
            "lastActiveAtMs" to last,
            "durationSec" to durationSec.coerceIn(0, 172800).toLong(),
            "activeTab" to activeTab.take(32),
            "isOnline" to isOnline,
            "tabSeconds" to tabSeconds.entries.take(10).associate { it.key.take(40) to it.value.coerceAtLeast(0) },
            "featureCounts" to featureCounts.entries.take(50).associate { it.key.take(40) to it.value.coerceAtLeast(0) },
            "featureSeconds" to featureSeconds.entries.take(30).associate { it.key.take(40) to it.value.coerceAtLeast(0) },
            "recentEvents" to recentEvents.takeLast(50).map { it.take(180) },
            "wardrobeStats" to wardrobeStats.entries.take(10).associate { it.key.take(40) to it.value.coerceAtLeast(0) }
        ).apply {
            if (profileUsername.isNotBlank()) put("profileUsername", profileUsername.take(32))
            if (profileDisplayName.isNotBlank()) put("profileDisplayName", profileDisplayName.take(60))
        }
    }

    fun isValidSessionDocId(sessionId: String, uid: String): Boolean {
        return UID_REGEX.matches(uid) &&
            sessionId.length <= 160 &&
            Regex("^${Regex.escape(uid)}_[0-9]{10,16}$").matches(sessionId)
    }

    fun buildDeviceDocumentJson(
        uid: String,
        anonTag: String,
        platform: String,
        appVersion: String,
        deviceModel: String,
        osInfo: String,
        screenSize: String,
        language: String,
        theme: String,
        timezone: String,
        country: String,
        city: String,
        region: String,
        firstSeenAtMs: Long,
        lastSeenAtMs: Long,
        sessionCount: Int,
        totalDurationSec: Long,
        activeSessionId: String,
        lastTab: String,
        isOnline: Boolean,
        wardrobeItemCount: Int,
        wishlistCount: Int,
        outfitCount: Int,
        lifetimeFeatureCounts: Map<String, Int>,
        lifetimeTabSeconds: Map<String, Int>,
        lifetimeFeatureSeconds: Map<String, Int>,
        errorCount: Int,
        lastError: String,
        profileUsername: String = "",
        profileDisplayName: String = "",
        profileAvatar: String = ""
    ): JSONObject {
        val first = firstSeenAtMs.coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
        val last = maxOf(first, lastSeenAtMs).coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
        val safePlatform = if (platform in setOf("android", "web_pwa", "web_browser")) platform else "android"
        val fields = JSONObject()
            .put("uid", strVal(uid.take(128)))
            .put("anonTag", strVal(anonTag.take(24).ifBlank { "A-USER" }))
            .put("platform", strVal(safePlatform))
            .put("appVersion", strVal(appVersion.take(32).ifBlank { APP_VERSION }))
            .put("deviceModel", strVal(deviceModel.take(120)))
            .put("osInfo", strVal(osInfo.take(100)))
            .put("screenSize", strVal(screenSize.take(32)))
            .put("language", strVal(language.take(12).ifBlank { "TR" }))
            .put("theme", strVal(theme.take(32).ifBlank { "MODERN" }))
            .put("timezone", strVal(timezone.take(64)))
            .put("country", strVal(country.take(64)))
            .put("city", strVal(city.take(64)))
            .put("region", strVal(region.take(64)))
            .put("firstSeenAtMs", intVal(first))
            .put("lastSeenAtMs", intVal(last))
            .put("sessionCount", intVal(sessionCount.toLong().coerceIn(1L, 1000000L)))
            .put("totalDurationSec", intVal(totalDurationSec.coerceIn(0L, 1000000000L)))
            .put("activeSessionId", strVal(activeSessionId.take(160)))
            .put("lastTab", strVal(lastTab.take(32)))
            .put("isOnline", boolVal(isOnline))
            .put("wardrobeItemCount", intVal(wardrobeItemCount.toLong().coerceIn(0L, 100000L)))
            .put("wishlistCount", intVal(wishlistCount.toLong().coerceIn(0L, 100000L)))
            .put("outfitCount", intVal(outfitCount.toLong().coerceIn(0L, 100000L)))
            .put("lifetimeFeatureCounts", intMapVal(lifetimeFeatureCounts, 50))
            .put("lifetimeTabSeconds", intMapVal(lifetimeTabSeconds, 10))
            .put("lifetimeFeatureSeconds", intMapVal(lifetimeFeatureSeconds, 30))
            .put("errorCount", intVal(errorCount.toLong().coerceIn(0L, 100000L)))
            .put("lastError", strVal(lastError.take(180)))
        if (profileUsername.isNotBlank()) fields.put("profileUsername", strVal(profileUsername.take(32)))
        if (profileDisplayName.isNotBlank()) fields.put("profileDisplayName", strVal(profileDisplayName.take(60)))
        if (profileAvatar.isNotBlank()) fields.put("profileAvatar", strVal(profileAvatar.take(35000)))
        return JSONObject().put("fields", fields)
    }

    fun buildSessionDocumentJson(
        uid: String,
        sessionId: String,
        anonTag: String,
        platform: String,
        appVersion: String,
        deviceModel: String,
        osInfo: String,
        screenSize: String,
        language: String,
        theme: String,
        timezone: String,
        country: String,
        city: String,
        region: String,
        entrySource: String,
        networkType: String,
        startedAtMs: Long,
        lastActiveAtMs: Long,
        durationSec: Int,
        activeTab: String,
        isOnline: Boolean,
        tabSeconds: Map<String, Int>,
        featureCounts: Map<String, Int>,
        featureSeconds: Map<String, Int>,
        recentEvents: List<String>,
        wardrobeStats: Map<String, Int>,
        profileUsername: String = "",
        profileDisplayName: String = ""
    ): JSONObject {
        val start = startedAtMs.coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
        val last = maxOf(start, lastActiveAtMs).coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS)
        val safePlatform = if (platform in setOf("android", "web_pwa", "web_browser")) platform else "android"
        val fields = JSONObject()
            .put("uid", strVal(uid.take(128)))
            .put("sessionId", strVal(sessionId.take(160)))
            .put("anonTag", strVal(anonTag.take(24).ifBlank { "A-USER" }))
            .put("platform", strVal(safePlatform))
            .put("appVersion", strVal(appVersion.take(32).ifBlank { APP_VERSION }))
            .put("deviceModel", strVal(deviceModel.take(120)))
            .put("osInfo", strVal(osInfo.take(100)))
            .put("screenSize", strVal(screenSize.take(32)))
            .put("language", strVal(language.take(12).ifBlank { "TR" }))
            .put("theme", strVal(theme.take(32).ifBlank { "MODERN" }))
            .put("timezone", strVal(timezone.take(64)))
            .put("country", strVal(country.take(64)))
            .put("city", strVal(city.take(64)))
            .put("region", strVal(region.take(64)))
            .put("entrySource", strVal(entrySource.take(32).ifBlank { "cold_start" }))
            .put("networkType", strVal(networkType.take(32)))
            .put("startedAtMs", intVal(start))
            .put("lastActiveAtMs", intVal(last))
            .put("durationSec", intVal(durationSec.toLong().coerceIn(0L, 172800L)))
            .put("activeTab", strVal(activeTab.take(32)))
            .put("isOnline", boolVal(isOnline))
            .put("tabSeconds", intMapVal(tabSeconds, 10))
            .put("featureCounts", intMapVal(featureCounts, 50))
            .put("featureSeconds", intMapVal(featureSeconds, 30))
            .put("recentEvents", strListVal(recentEvents, 50))
            .put("wardrobeStats", intMapVal(wardrobeStats, 10))
        if (profileUsername.isNotBlank()) fields.put("profileUsername", strVal(profileUsername.take(32)))
        if (profileDisplayName.isNotBlank()) fields.put("profileDisplayName", strVal(profileDisplayName.take(60)))
        return JSONObject().put("fields", fields)
    }
}
