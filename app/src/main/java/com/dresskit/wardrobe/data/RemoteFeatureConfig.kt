package com.dresskit.wardrobe.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

enum class AppFeatureKey(
    val key: String,
    val trName: String,
    val enName: String
) {
    SMART_TRAVEL("smart_travel", "Akıllı Bavul Hazırlayıcı", "Smart Suitcase Planner"),
    FACE_TONE("face_tone", "Yüz & Ten Rengi Analizi", "Face & Skin Tone Analysis"),
    STYLE_DISCOVERY("style_discovery", "Stil Keşfi Kartları", "Style Discovery Cards"),
    TRY_ON_2D("try_on_2d", "2D Sanal Kabin", "2D Virtual Try-On"),
    SURPRISE_OUTFIT("surprise_outfit", "Sürpriz Kombin Üretici", "Surprise Outfit Generator");

    fun displayLabel(isEnglish: Boolean): String = if (isEnglish) enName else trName
}

data class RemoteConfigState(
    val adsEnabled: Boolean = false,
    val proEnabled: Boolean = false,
    val bannerSlots: Set<String> = emptySet(),
    val lockedFeatures: Set<String> = emptySet(),
    val adUnitIds: Map<String, String> = emptyMap(),
    val announcementTitle: String = "",
    val announcementMessage: String = "",
    val ctaUrl: String = "",
    val ctaLabel: String = "",
    val proTitle: String = "",
    val proSubtitle: String = "",
    val proPurchaseUrl: String = "",
    val proUnlockCodes: Set<String> = emptySet(),
    val minRecommendedVersion: String = "1.43.1",
    val hasProUnlocked: Boolean = false
) {
    fun isFeatureLocked(feature: AppFeatureKey): Boolean =
        proEnabled && !hasProUnlocked && feature.key in lockedFeatures

    fun isBannerVisible(slot: String): Boolean =
        adsEnabled && !hasProUnlocked && slot in bannerSlots &&
            (announcementTitle.isNotBlank() || announcementMessage.isNotBlank() || ctaUrl.isNotBlank())
}

object RemoteConfigManager {

    private const val REMOTE_CONFIG_URL =
        "https://raw.githubusercontent.com/kyhlaer/Dolabim/main/remote_config.json"
    private val ALLOWED_CONFIG_HOSTS = setOf("raw.githubusercontent.com")
    private const val PREFS_NAME = "dolabim_remote_config"
    private const val KEY_JSON = "cached_json"
    private const val KEY_FETCHED_AT = "fetched_at_ms"
    private const val KEY_PRO_UNLOCKED = "user_pro_unlocked"
    private const val KEY_FAIL_COUNT = "pro_unlock_fail_count"
    private const val KEY_LOCKOUT_UNTIL = "pro_unlock_lockout_until_ms"
    private const val MAX_UNLOCK_FAILS = 5
    private const val LOCKOUT_DURATION_MS = 60_000L
    private const val CODE_SALT = "dolabim_pro_v1:"
    private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L
    private const val MAX_CONFIG_BYTES = 32_768

    fun loadCached(context: Context): RemoteConfigState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unlocked = prefs.getBoolean(KEY_PRO_UNLOCKED, false)
        val raw = prefs.getString(KEY_JSON, null)
        return if (raw.isNullOrBlank()) {
            RemoteConfigState(hasProUnlocked = unlocked)
        } else {
            parseJson(raw, unlocked)
        }
    }

    suspend fun refreshIfNeeded(
        context: Context,
        force: Boolean = false
    ): RemoteConfigState = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unlocked = prefs.getBoolean(KEY_PRO_UNLOCKED, false)
        val lastFetched = prefs.getLong(KEY_FETCHED_AT, 0L)
        val now = System.currentTimeMillis()
        if (!force && lastFetched > 0L && now - lastFetched < CACHE_TTL_MS) {
            return@withContext loadCached(context)
        }

        val fetchedJson = fetchRemoteJson()
        if (!fetchedJson.isNullOrBlank()) {
            prefs.edit()
                .putString(KEY_JSON, fetchedJson)
                .putLong(KEY_FETCHED_AT, now)
                .apply()
            return@withContext parseJson(fetchedJson, unlocked)
        }
        return@withContext loadCached(context)
    }

    fun getRemainingLockoutSeconds(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val rem = until - System.currentTimeMillis()
        return if (rem > 0L) ((rem + 999L) / 1000L).toInt() else 0
    }

    fun verifyAndUnlockPro(
        context: Context,
        enteredCode: String,
        state: RemoteConfigState
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (now < lockoutUntil) return false

        val clean = enteredCode.trim()
        if (clean.isBlank() || clean.length > 64 || state.proUnlockCodes.isEmpty()) return false
        val upper = clean.uppercase(Locale.ROOT)
        val rawShaHex = SecurityGuards.sha256Hex(clean.toByteArray(Charsets.UTF_8))
        val upperShaHex = SecurityGuards.sha256Hex(upper.toByteArray(Charsets.UTF_8))
        val saltedShaHex = SecurityGuards.sha256Hex((CODE_SALT + upper).toByteArray(Charsets.UTF_8))

        val matched = state.proUnlockCodes.any { token ->
            val target = token.trim()
            val targetSaltedHex = SecurityGuards.sha256Hex((CODE_SALT + target.uppercase(Locale.ROOT)).toByteArray(Charsets.UTF_8))
            SecurityGuards.constantTimeEqualsHex(targetSaltedHex, saltedShaHex) ||
                SecurityGuards.constantTimeEqualsHex(target, rawShaHex) ||
                SecurityGuards.constantTimeEqualsHex(target, upperShaHex) ||
                SecurityGuards.constantTimeEqualsHex(target, saltedShaHex)
        }
        if (matched) {
            prefs.edit()
                .putBoolean(KEY_PRO_UNLOCKED, true)
                .putInt(KEY_FAIL_COUNT, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
        } else {
            val fails = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
            val editor = prefs.edit().putInt(KEY_FAIL_COUNT, fails)
            if (fails >= MAX_UNLOCK_FAILS) {
                editor.putInt(KEY_FAIL_COUNT, 0)
                    .putLong(KEY_LOCKOUT_UNTIL, now + LOCKOUT_DURATION_MS)
            }
            editor.apply()
        }
        return matched
    }

    private fun fetchRemoteJson(): String? {
        val conn = SecurityGuards.openSafeHttpsGet(
            initialUrl = REMOTE_CONFIG_URL,
            allowedHosts = ALLOWED_CONFIG_HOSTS,
            maxRedirects = 1,
            connectTimeoutMs = 3500,
            readTimeoutMs = 3500
        ) ?: return null
        return try {
            val buf = CharArray(MAX_CONFIG_BYTES)
            var total = 0
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (total < MAX_CONFIG_BYTES) {
                    val n = reader.read(buf, total, MAX_CONFIG_BYTES - total)
                    if (n <= 0) break
                    total += n
                }
            }
            if (total > 0) String(buf, 0, total) else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun parseJson(raw: String, unlocked: Boolean): RemoteConfigState {
        return try {
            val obj = JSONObject(raw)
            if (obj.optInt("schemaVersion", 1) != 1) {
                return RemoteConfigState(hasProUnlocked = unlocked)
            }
            val adsEnabled = obj.optBoolean("adsEnabled", false)
            val proEnabled = obj.optBoolean("proEnabled", false)

            val slots = mutableSetOf<String>()
            obj.optJSONArray("bannerSlots")?.let { arr ->
                for (i in 0 until minOf(arr.length(), 16)) {
                    arr.optString(i).trim().take(48).takeIf { it.isNotEmpty() }?.let(slots::add)
                }
            }

            val locked = mutableSetOf<String>()
            obj.optJSONArray("lockedFeatures")?.let { arr ->
                for (i in 0 until minOf(arr.length(), 16)) {
                    arr.optString(i).trim().take(48).takeIf { it.isNotEmpty() }?.let(locked::add)
                }
            }

            val unitMap = mutableMapOf<String, String>()
            obj.optJSONObject("adUnitIds")?.let { units ->
                val keys = units.keys()
                while (keys.hasNext() && unitMap.size < 16) {
                    val k = keys.next().trim().take(48)
                    val v = units.optString(k).trim().take(120)
                    if (k.isNotEmpty() && v.isNotEmpty()) unitMap[k] = v
                }
            }

            val codes = mutableSetOf<String>()
            obj.optJSONArray("proUnlockCodes")?.let { arr ->
                for (i in 0 until minOf(arr.length(), 64)) {
                    arr.optString(i).trim().take(128).takeIf { it.isNotEmpty() }?.let(codes::add)
                }
            }

            fun safePublicUrl(value: String): String {
                val trimmed = value.trim()
                return if (SecurityGuards.isSafePublicHttpsUrl(trimmed, maxLen = 600)) trimmed else ""
            }

            RemoteConfigState(
                adsEnabled = adsEnabled,
                proEnabled = proEnabled,
                bannerSlots = slots,
                lockedFeatures = locked,
                adUnitIds = unitMap,
                announcementTitle = obj.optString("announcementTitle", "").trim().take(80),
                announcementMessage = obj.optString("announcementMessage", "").trim().take(220),
                ctaUrl = safePublicUrl(obj.optString("ctaUrl", "")),
                ctaLabel = obj.optString("ctaLabel", "").trim().take(32),
                proTitle = obj.optString("proTitle", "").trim().take(80),
                proSubtitle = obj.optString("proSubtitle", "").trim().take(240),
                proPurchaseUrl = safePublicUrl(obj.optString("proPurchaseUrl", "")),
                proUnlockCodes = codes,
                minRecommendedVersion = obj.optString("minRecommendedVersion", "1.43.1").trim().take(24),
                hasProUnlocked = unlocked
            )
        } catch (_: Exception) {
            RemoteConfigState(hasProUnlocked = unlocked)
        }
    }
}
