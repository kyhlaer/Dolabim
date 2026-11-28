package com.dresskit.wardrobe.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupPreviewInfo(
    val appVersion: String,
    val exportedAtMs: Long,
    val includeAccount: Boolean,
    val accountUsername: String,
    val itemCount: Int,
    val outfitCount: Int,
    val photoCount: Int
)

data class BackupRestoreResult(
    val itemCount: Int,
    val outfitCount: Int,
    val photoCount: Int,
    val accountRestored: Boolean
)

/**
 * Full local backup & restore for Dolabım Android:
 * - Supports "With Account" (all items, outfits, photos, settings, profile & auth UID/refreshToken)
 * - Supports "Without Account" (all items, outfits, photos & settings, excluding account identity)
 * - Uses schema `dolabim_backup_v1` compatible with Dolabım Web/PWA.
 */
object BackupManager {

    const val BACKUP_FORMAT = "dolabim_backup_v1"
    private const val MAX_BACKUP_BYTES = 50L * 1024L * 1024L // 50 MB
    private const val PREFS_APP = "dolabim_prefs"
    private const val PREFS_DM = "dolabim_dm_prefs"
    private const val PREFS_AUTH = "dolabim_telemetry"
    private const val KEY_MUTED_UIDS = "muted_partner_uids_v1"

    suspend fun exportBackupToUri(
        context: Context,
        uri: Uri,
        includeAccount: Boolean,
        appVersion: String
    ): Result<BackupPreviewInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val root = buildBackupJson(context, includeAccount, appVersion)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: throw IllegalStateException("Yedek dosyası yazılamadı")
            parsePreview(root)
        }
    }

    fun buildBackupJson(
        context: Context,
        includeAccount: Boolean,
        appVersion: String
    ): JSONObject {
        val appCtx = context.applicationContext
        val appPrefs = appCtx.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        val dmPrefs = appCtx.getSharedPreferences(PREFS_DM, Context.MODE_PRIVATE)

        // 1. Settings
        val settingsObj = JSONObject()
            .put("themeMode", appPrefs.getString("theme_mode", "MODERN") ?: "MODERN")
            .put("language", appPrefs.getString("app_language", "TR") ?: "TR")
            .put("genderPref", appPrefs.getString("gender_pref", "UNISEX") ?: "UNISEX")
            .put("genderUserSet", appPrefs.getBoolean("gender_user_set", false))
            .put("lookGender", appPrefs.getString("inspiration_look_gender", "") ?: "")
            .put("likedLookIds", JSONArray((appPrefs.getStringSet("liked_look_ids", emptySet()) ?: emptySet()).toList()))
            .put("dislikedLookIds", JSONArray((appPrefs.getStringSet("disliked_look_ids", emptySet()) ?: emptySet()).toList()))
            .put("mutedPartnerUids", JSONArray((dmPrefs.getStringSet(KEY_MUTED_UIDS, emptySet()) ?: emptySet()).toList()))

        // 2. Wardrobe (items + outfits with every single detail)
        val dataFile = File(appCtx.filesDir, "wardrobe_data.json")
        val wardrobeRoot = if (dataFile.exists()) {
            runCatching { JSONObject(dataFile.readText()) }.getOrElse { JSONObject() }
        } else {
            JSONObject()
        }
        val itemsArr = wardrobeRoot.optJSONArray("items") ?: JSONArray()
        val outfitsArr = wardrobeRoot.optJSONArray("outfits") ?: JSONArray()
        val wardrobeObj = JSONObject()
            .put("items", itemsArr)
            .put("outfits", outfitsArr)

        // 3. Photos (all garment photos + face photo + body photo)
        val photosObj = JSONObject()
        val photosDir = ImageStorage.getPhotosDir(appCtx)
        if (photosDir.exists() && photosDir.isDirectory) {
            photosDir.listFiles()?.forEach { f ->
                if (f.isFile && f.length() in 1L..15_000_000L && !f.name.endsWith(".tmp")) {
                    val mime = if (f.name.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
                    val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                    photosObj.put(f.name, "data:$mime;base64,$b64")
                }
            }
        }
        val faceFile = ImageStorage.getUserFacePhotoFile(appCtx)
        if (faceFile.exists() && faceFile.length() in 1L..15_000_000L) {
            photosObj.put("user_face_photo", "data:image/jpeg;base64,${Base64.encodeToString(faceFile.readBytes(), Base64.NO_WRAP)}")
        }
        val bodyFile = ImageStorage.getUserBodyPhotoFile(appCtx)
        if (bodyFile.exists() && bodyFile.length() in 1L..15_000_000L) {
            photosObj.put("user_body_photo", "data:image/jpeg;base64,${Base64.encodeToString(bodyFile.readBytes(), Base64.NO_WRAP)}")
        }

        val root = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("exportedAtMs", System.currentTimeMillis())
            .put("appVersion", appVersion)
            .put("platform", "android")
            .put("includeAccount", includeAccount)
            .put("settings", settingsObj)
            .put("wardrobe", wardrobeObj)
            .put("photos", photosObj)

        // 4. Optional Account (Social Profile + Firebase Auth identity)
        if (includeAccount) {
            val profileObj = runCatching { SocialProfileService.loadLocalProfile(appCtx).toJson() }
                .getOrElse { JSONObject() }
            val authPrefs = appCtx.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            val uid = authPrefs.getString("auth_uid", "").orEmpty()
            val refreshToken = authPrefs.getString("auth_refresh_token", "").orEmpty()
            val accountObj = JSONObject()
                .put("profile", profileObj)
                .put(
                    "auth",
                    JSONObject()
                        .put("uid", uid)
                        .put("refreshToken", refreshToken)
                )
            root.put("account", accountObj)
        }

        return root
    }

    private fun readBoundedText(context: Context, uri: Uri, maxBytes: Long = MAX_BACKUP_BYTES): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Dosya okunamadı")
        return stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw SecurityException("Yedek dosyası boyutu güvenlik sınırını aştı (maks 50 MB)")
                }
                out.write(buffer, 0, read)
            }
            out.toString("UTF-8")
        }
    }

    suspend fun inspectBackupFromUri(context: Context, uri: Uri): Result<BackupPreviewInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = readBoundedText(context, uri)
            val root = JSONObject(raw)
            if (root.optString("format") != BACKUP_FORMAT) {
                throw IllegalArgumentException("Geçersiz Dolabım yedek dosyası")
            }
            parsePreview(root)
        }
    }

    private fun parsePreview(root: JSONObject): BackupPreviewInfo {
        val wardrobe = root.optJSONObject("wardrobe")
        val items = wardrobe?.optJSONArray("items")?.length() ?: 0
        val outfits = wardrobe?.optJSONArray("outfits")?.length() ?: 0
        val photos = root.optJSONObject("photos")?.length() ?: 0
        val account = root.optJSONObject("account")
        val hasAccount = root.optBoolean("includeAccount", false) && account != null
        val username = account?.optJSONObject("profile")?.optString("username", "").orEmpty()
        return BackupPreviewInfo(
            appVersion = root.optString("appVersion", ""),
            exportedAtMs = root.optLong("exportedAtMs", 0L),
            includeAccount = hasAccount,
            accountUsername = username,
            itemCount = items,
            outfitCount = outfits,
            photoCount = photos
        )
    }

    suspend fun restoreBackupFromUri(
        context: Context,
        uri: Uri,
        restoreAccount: Boolean
    ): Result<BackupRestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = readBoundedText(context, uri)
            val root = JSONObject(raw)
            restoreBackupJson(context, root, restoreAccount)
        }
    }

    fun restoreBackupJson(
        context: Context,
        root: JSONObject,
        restoreAccount: Boolean
    ): BackupRestoreResult {
        if (root.optString("format") != BACKUP_FORMAT) {
            throw IllegalArgumentException("Geçersiz Dolabım yedek dosyası")
        }
        val appCtx = context.applicationContext

        // 1. Restore Wardrobe JSON
        val wardrobe = root.optJSONObject("wardrobe") ?: JSONObject()
        val itemsArr = wardrobe.optJSONArray("items") ?: JSONArray()
        val outfitsArr = wardrobe.optJSONArray("outfits") ?: JSONArray()
        val dataFile = File(appCtx.filesDir, "wardrobe_data.json")
        val outWardrobe = JSONObject()
            .put("items", itemsArr)
            .put("outfits", outfitsArr)
        dataFile.writeText(outWardrobe.toString())

        // 2. Restore Photos
        val photosObj = root.optJSONObject("photos") ?: JSONObject()
        val photosDir = ImageStorage.getPhotosDir(appCtx)
        var photoCount = 0
        val keys = photosObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val dataUrl = photosObj.optString(key, "")
            val b64 = dataUrl.substringAfter("base64,", "")
            if (b64.isBlank()) continue
            val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: continue
            if (bytes.isEmpty() || bytes.size > 15_000_000) continue
            when (key) {
                "user_face_photo" -> {
                    ImageStorage.getUserFacePhotoFile(appCtx).writeBytes(bytes)
                    photoCount++
                }
                "user_body_photo" -> {
                    ImageStorage.getUserBodyPhotoFile(appCtx).writeBytes(bytes)
                    photoCount++
                }
                else -> {
                    val dest = resolveSafeRestorePhotoFile(photosDir, key)
                    if (dest != null) {
                        dest.writeBytes(bytes)
                        photoCount++
                    }
                }
            }
        }

        // 3. Restore Settings
        val settings = root.optJSONObject("settings")
        if (settings != null) {
            val appPrefs = appCtx.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            val editor = appPrefs.edit()
            settings.optString("themeMode", "").takeIf { it.isNotBlank() }?.let { editor.putString("theme_mode", it) }
            settings.optString("language", "").takeIf { it.isNotBlank() }?.let { editor.putString("app_language", it) }
            settings.optString("genderPref", "").takeIf { it.isNotBlank() }?.let { editor.putString("gender_pref", it) }
            if (settings.has("genderUserSet")) editor.putBoolean("gender_user_set", settings.optBoolean("genderUserSet", false))
            settings.optString("lookGender", "").takeIf { it.isNotBlank() }?.let { editor.putString("inspiration_look_gender", it) }
            settings.optJSONArray("likedLookIds")?.let { arr ->
                val set = buildSet { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
                editor.putStringSet("liked_look_ids", set)
            }
            settings.optJSONArray("dislikedLookIds")?.let { arr ->
                val set = buildSet { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
                editor.putStringSet("disliked_look_ids", set)
            }
            editor.apply()

            settings.optJSONArray("mutedPartnerUids")?.let { arr ->
                val set = buildSet { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
                appCtx.getSharedPreferences(PREFS_DM, Context.MODE_PRIVATE).edit()
                    .putStringSet(KEY_MUTED_UIDS, set)
                    .apply()
            }
        }

        // 4. Optional Account Restore
        var accountRestored = false
        val account = root.optJSONObject("account")
        if (restoreAccount && account != null) {
            val prof = account.optJSONObject("profile")
            if (prof != null && prof.length() > 0) {
                runCatching {
                    SocialProfileService.saveLocalProfile(
                        appCtx,
                        com.dresskit.wardrobe.model.LocalProfileConfig.fromJson(prof)
                    )
                    accountRestored = true
                }
            }
            val auth = account.optJSONObject("auth")
            if (auth != null) {
                val uid = auth.optString("uid", "").trim()
                val ref = auth.optString("refreshToken", "").trim()
                if (uid.isNotBlank() && ref.isNotBlank()) {
                    appCtx.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE).edit()
                        .putString("auth_uid", uid)
                        .putString("auth_refresh_token", ref)
                        .putString("auth_id_token", "")
                        .putLong("auth_expires_ms", 0L)
                        .apply()
                    accountRestored = true
                }
            }
        }

        return BackupRestoreResult(
            itemCount = itemsArr.length(),
            outfitCount = outfitsArr.length(),
            photoCount = photoCount,
            accountRestored = accountRestored
        )
    }

    internal fun resolveSafeRestorePhotoFile(photosDir: File, key: String): File? {
        val clean = key.trim()
        if (clean.isEmpty() || clean.startsWith(".") || clean.contains("..") || clean.contains('/') || clean.contains('\\')) {
            return null
        }
        if (!clean.matches(Regex("^[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9]{1,8})?$"))) return null
        val dest = File(photosDir, clean)
        val dirCanonical = runCatching { photosDir.canonicalPath }.getOrNull() ?: return null
        val destCanonical = runCatching { dest.canonicalPath }.getOrNull() ?: return null
        return if (destCanonical.startsWith("$dirCanonical${File.separator}")) dest else null
    }
}
