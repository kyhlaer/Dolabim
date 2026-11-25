package com.dresskit.wardrobe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.CustomShowcaseItem
import com.dresskit.wardrobe.model.DeepLinkTarget
import com.dresskit.wardrobe.model.FollowRelation
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.LoadedProfileBundle
import com.dresskit.wardrobe.model.LocalProfileConfig
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.ProfileContentData
import com.dresskit.wardrobe.model.PublicProfileHeader
import com.dresskit.wardrobe.model.Season
import com.dresskit.wardrobe.model.SharedGarmentData
import com.dresskit.wardrobe.model.SharedOutfitData
import com.dresskit.wardrobe.model.SharedOutfitPiece
import com.dresskit.wardrobe.model.ShowcaseItemData
import com.dresskit.wardrobe.model.StatsVisibilityConfig
import com.dresskit.wardrobe.model.StyleTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cloud sync, profile visibility, follow workflow, garment & profile deep-link sharing,
 * and public user discovery over Firebase Auth + Cloud Firestore REST API.
 */
object SocialProfileService {

    private const val FIREBASE_PROJECT_ID = "dolabim-stats"
    private const val FS_BASE =
        "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/(default)/documents"
    private const val PROFILE_FILE = "dolabim_profile_v1.json"
    private val ALLOWED_HOSTS = setOf("firestore.googleapis.com")
    private val USERNAME_REGEX = Regex("^[a-z0-9][a-z0-9_.]{1,22}[a-z0-9]$")
    private val SHARE_ID_REGEX = Regex("^[A-Za-z0-9_-]{8,48}$")
    private const val MIN_VALID_EPOCH_MS = 1700000000001L
    private const val MAX_VALID_EPOCH_MS = 4102444800000L

    private fun safeNowMs(): Long =
        System.currentTimeMillis().coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS - 5000L)

    private val localLock = Any()

    fun normalizeUsername(raw: String): String =
        raw.trim()
            .removePrefix("@")
            .replace('ç', 'c').replace('Ç', 'c')
            .replace('ğ', 'g').replace('Ğ', 'g')
            .replace('ı', 'i').replace('İ', 'i').replace('I', 'i')
            .replace('ö', 'o').replace('Ö', 'o')
            .replace('ş', 's').replace('Ş', 's')
            .replace('ü', 'u').replace('Ü', 'u')
            .lowercase(Locale.ROOT)
            .replace("\u0307", "")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_.]"), "")

    fun isValidUsername(raw: String): Boolean {
        val norm = normalizeUsername(raw)
        if (!USERNAME_REGEX.matches(norm)) return false
        if (norm.contains("..")) return false
        return true
    }

    fun parseDeepLink(uriString: String?): DeepLinkTarget? {
        if (uriString.isNullOrBlank()) return null
        val trimmed = uriString.trim()
        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (scheme == "dolabim") {
            val host = parsed.host?.lowercase(Locale.ROOT).orEmpty()
            val seg = parsed.path?.trim('/')?.substringBefore('/')?.trim().orEmpty()
            if (host == "item" && SHARE_ID_REGEX.matches(seg)) {
                return DeepLinkTarget.SharedItem(seg)
            }
            if (host == "outfit" && SHARE_ID_REGEX.matches(seg)) {
                return DeepLinkTarget.SharedOutfit(seg)
            }
            if (host == "post" && SHARE_ID_REGEX.matches(seg)) {
                return DeepLinkTarget.SocialPost(seg)
            }
            if ((host == "u" || host == "user" || host == "profile") && seg.isNotEmpty()) {
                return DeepLinkTarget.UserProfile(normalizeUsername(seg), isUid = false)
            }
            if (host == "uid" && seg.isNotEmpty()) {
                return DeepLinkTarget.UserProfile(seg, isUid = true)
            }
        }
        val query = parsed.rawQuery ?: return null
        val params = query.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null
            else runCatching {
                java.net.URLDecoder.decode(part.substring(0, idx), "UTF-8") to
                    java.net.URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            }.getOrNull()
        }.toMap()

        val postId = params["post"]?.trim().orEmpty()
        if (SHARE_ID_REGEX.matches(postId)) {
            val ownerParam = params["owner"]?.trim().orEmpty()
            return DeepLinkTarget.SocialPost(postId, ownerParam)
        }
        val itemId = params["item"]?.trim().orEmpty()
        if (SHARE_ID_REGEX.matches(itemId)) {
            return DeepLinkTarget.SharedItem(itemId)
        }
        val outfitId = params["outfit"]?.trim().orEmpty()
        if (SHARE_ID_REGEX.matches(outfitId)) {
            return DeepLinkTarget.SharedOutfit(outfitId)
        }
        val u = params["u"]?.trim().orEmpty()
        if (u.isNotEmpty()) {
            return DeepLinkTarget.UserProfile(normalizeUsername(u), isUid = false)
        }
        val uid = params["uid"]?.trim().orEmpty()
        if (uid.matches(Regex("^[A-Za-z0-9_-]{1,128}$"))) {
            return DeepLinkTarget.UserProfile(uid, isUid = true)
        }
        return null
    }

    fun computeFilteredWardrobeSummary(
        items: List<ClothingItem>,
        outfits: List<Outfit>,
        vis: StatsVisibilityConfig
    ): Map<String, Any> {
        val closet = items.filter { !it.isWishlist }
        val wishlist = items.filter { it.isWishlist }
        val out = LinkedHashMap<String, Any>()

        if (vis.showTotalItems) {
            out["totalItems"] = closet.size
            out["totalPhotos"] = closet.sumOf { it.photoFileNames.size }
        }
        if (vis.showFavoriteCount) {
            out["favoriteCount"] = items.count { it.isFavorite }
        }
        if (vis.showWishlistCount) {
            out["wishlistCount"] = wishlist.size
        }
        if (vis.showOutfitCount) {
            out["outfitCount"] = outfits.size
        }
        if (vis.showCategoryBreakdown) {
            val catSummary = ClothingCategory.entries
                .map { cat -> cat.name to closet.count { it.category == cat } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .joinToString(",") { "${it.first}:${it.second}" }
            out["categoryCounts"] = catSummary
        }
        if (vis.showTopColors) {
            val topColors = GarmentColor.entries
                .map { col -> col.name to closet.count { it.colors.contains(col) } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(6)
                .joinToString(",") { "${it.first}:${it.second}" }
            out["topColors"] = topColors
        }
        if (vis.showColorPalette) {
            val paletteStr = GarmentColor.entries
                .map { col -> col.name to closet.count { it.colors.contains(col) } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .joinToString(",") { "${it.first}:${it.second}" }
            if (paletteStr.isNotEmpty()) {
                out["colorPalette"] = paletteStr
            }
        }
        if (vis.showTopSubCategories) {
            val topSubs = ClothingSubCategory.entries
                .map { sub -> sub.name to closet.count { it.subCategory == sub } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(6)
                .joinToString(",") { "${it.first}:${it.second}" }
            out["topSubCategories"] = topSubs
        }
        if (vis.showEstimatedValue) {
            val wishlistVal = wishlist.sumOf { PriceTrackerService.parsePrice(it.priceText) ?: 0.0 }
            val closetVal = closet.sumOf { PriceTrackerService.parsePrice(it.priceText) ?: 0.0 }
            out["estimatedWishlistValue"] = wishlistVal.roundToInt()
            out["estimatedWardrobeValue"] = closetVal.roundToInt()
        }
        return out
    }

    fun loadLocalProfile(context: Context): LocalProfileConfig {
        synchronized(localLock) {
            val f = File(context.filesDir, PROFILE_FILE)
            if (!f.exists()) return LocalProfileConfig()
            return runCatching {
                LocalProfileConfig.fromJson(JSONObject(f.readText()))
            }.getOrDefault(LocalProfileConfig())
        }
    }

    fun saveLocalProfile(context: Context, profile: LocalProfileConfig) {
        synchronized(localLock) {
            val f = File(context.filesDir, PROFILE_FILE)
            val tmp = File(context.filesDir, "$PROFILE_FILE.tmp")
            runCatching {
                tmp.writeText(profile.toJson().toString())
                runCatching {
                    tmp.setReadable(false, false)
                    tmp.setReadable(true, true)
                    tmp.setWritable(false, false)
                    tmp.setWritable(true, true)
                }
                if (!tmp.renameTo(f)) {
                    tmp.copyTo(f, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }

    fun encodeAvatarPair(bitmap: Bitmap): Pair<String, String> {
        val full = scaleSquareBitmap(bitmap, 200)
        val thumb = scaleSquareBitmap(full, 88)
        val fullUrl = bitmapToJpegDataUrl(full, maxChars = 70_000, startQuality = 82)
        val thumbUrl = bitmapToJpegDataUrl(thumb, maxChars = 32_000, startQuality = 72)
        if (full !== bitmap && !full.isRecycled) full.recycle()
        if (thumb !== full && !thumb.isRecycled) thumb.recycle()
        return fullUrl to thumbUrl
    }

    fun encodeBitmapForCloudGarment(bitmap: Bitmap, maxSide: Int = 680, maxChars: Int = 210_000): String {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val longest = max(w, h)
        val scaled = if (longest > maxSide) {
            val ratio = maxSide.toFloat() / longest.toFloat()
            Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        val out = bitmapToJpegDataUrl(scaled, maxChars = maxChars, startQuality = 78)
        if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        return out
    }

    fun encodeGarmentPhotoForCloud(context: Context, fileName: String, maxChars: Int = 210_000): String? {
        val bmp = ImageStorage.decodePhotoBitmap(context, fileName) ?: return null
        return try {
            encodeBitmapForCloudGarment(bmp, maxSide = 680, maxChars = maxChars)
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    fun decodeDataUrlToBitmap(dataUrl: String): Bitmap? {
        if (!dataUrl.startsWith("data:image/")) return null
        val comma = dataUrl.indexOf(',')
        if (comma <= 0 || comma >= dataUrl.length - 4) return null
        return runCatching {
            val b64 = dataUrl.substring(comma + 1)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun scaleSquareBitmap(src: Bitmap, size: Int): Bitmap {
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)
        val side = min(w, h)
        val x = ((w - side) / 2).coerceAtLeast(0)
        val y = ((h - side) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        val out = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (cropped !== src && cropped !== out && !cropped.isRecycled) cropped.recycle()
        return out
    }

    private fun bitmapToJpegDataUrl(bmp: Bitmap, maxChars: Int, startQuality: Int): String {
        var q = startQuality
        var cur = bmp
        repeat(5) {
            val baos = ByteArrayOutputStream()
            cur.compress(Bitmap.CompressFormat.JPEG, q, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val url = "data:image/jpeg;base64,$b64"
            if (url.length <= maxChars) {
                if (cur !== bmp && !cur.isRecycled) cur.recycle()
                return url
            }
            q = (q - 14).coerceAtLeast(36)
            if (q <= 45 && cur.width > 360) {
                val smaller = Bitmap.createScaledBitmap(
                    cur,
                    (cur.width * 0.78f).toInt().coerceAtLeast(64),
                    (cur.height * 0.78f).toInt().coerceAtLeast(64),
                    true
                )
                if (cur !== bmp && !cur.isRecycled) cur.recycle()
                cur = smaller
            }
        }
        val baos = ByteArrayOutputStream()
        cur.compress(Bitmap.CompressFormat.JPEG, 35, baos)
        val res = "data:image/jpeg;base64,${Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)}"
        if (cur !== bmp && !cur.isRecycled) cur.recycle()
        return res.take(maxChars)
    }

    private fun randomShareId(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789_-"
        val rnd = SecureRandom()
        return buildString(12) {
            repeat(12) { append(chars[rnd.nextInt(chars.length)]) }
        }
    }

    // ==================== Firestore REST Layer ====================

    internal fun formatFsError(code: Int, body: String, actionLabel: String): String {
        val parsedMsg = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")?.trim().orEmpty()
        }.getOrDefault("")
        val friendly = when {
            code == 0 -> "Sunucuya bağlanılamadı"
            code == 403 || parsedMsg.contains("insufficient permissions", ignoreCase = true) ->
                "Sunucu erişim izni reddedildi (403)"
            code == 404 -> "Kayıt bulunamadı (404)"
            code == 429 -> "Çok fazla istek gönderildi, biraz bekle"
            parsedMsg.isNotBlank() -> parsedMsg.take(120)
            else -> "Kod $code"
        }
        return "$actionLabel: $friendly"
    }

    suspend fun checkUsernameAvailable(context: Context, rawUsername: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val norm = normalizeUsername(rawUsername)
            if (!isValidUsername(norm)) return@withContext Result.success(false)
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("İnternet bağlantısı kurulamadı"))
            val (myUid, token) = creds
            val (code, body) = execFs("GET", "$FS_BASE/usernames/${enc(norm)}", token, null)
            when (code) {
                404 -> Result.success(true)
                in 200..299 -> {
                    val docUid = fsString(JSONObject(body).optJSONObject("fields"), "uid")
                    Result.success(docUid == myUid)
                }
                else -> Result.failure(IllegalStateException(formatFsError(code, body, "Kontrol edilemedi")))
            }
        }

    suspend fun saveAndSyncProfile(
        context: Context,
        draft: LocalProfileConfig,
        items: List<ClothingItem>,
        outfits: List<Outfit>
    ): Result<LocalProfileConfig> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Sunucuya bağlanılamadı"))
        val (uid, token) = creds
        val prev = loadLocalProfile(context)

        val cleanUser = normalizeUsername(draft.username)
        if (cleanUser.isNotEmpty() && !isValidUsername(cleanUser)) {
            return@withContext Result.failure(
                IllegalArgumentException("Kullanıcı adı 3-24 karakter olmalı (a-z, 0-9, _, .)")
            )
        }
        val cleanDisplay = draft.displayName.trim().take(50).ifBlank { cleanUser }
        val cleanBio = draft.bio.trim().take(220)
        val cleanTags = draft.styleTags.map { it.trim().take(24) }.filter { it.isNotEmpty() }.distinct().take(4)
        val nowMs = safeNowMs()

        // Claim username document first if provided
        if (cleanUser.isNotEmpty()) {
            val (getCode, getBody) = execFs("GET", "$FS_BASE/usernames/${enc(cleanUser)}", token, null)
            if (getCode in 200..299) {
                val owner = fsString(JSONObject(getBody).optJSONObject("fields"), "uid")
                if (owner != uid) {
                    return@withContext Result.failure(IllegalStateException("Bu kullanıcı adı (@$cleanUser) başka biri tarafından alınmış"))
                }
            }
            val userFields = JSONObject()
                .put("uid", strVal(uid))
                .put("username", strVal(cleanUser))
                .put("updatedAtMs", intVal(nowMs))
            val (uCode, uBody) = execFs(
                "PATCH",
                "$FS_BASE/usernames/${enc(cleanUser)}",
                token,
                JSONObject().put("fields", userFields).toString()
            )
            if (uCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException(formatFsError(uCode, uBody, "Kullanıcı adı kaydedilemedi")))
            }
        }
        val oldUser = normalizeUsername(prev.username)
        if (oldUser.isNotEmpty() && oldUser != cleanUser) {
            execFs("DELETE", "$FS_BASE/usernames/${enc(oldUser)}", token, null)
        }

        // Write public_profiles/{uid}
        val avatarForPublic = if (draft.avatarBase64.startsWith("data:image/") && draft.avatarBase64.length <= 75000) {
            draft.avatarBase64
        } else ""
        val avatarThumb = if (draft.avatarThumbBase64.startsWith("data:image/") && draft.avatarThumbBase64.length <= 35000) {
            draft.avatarThumbBase64
        } else if (avatarForPublic.length <= 35000) avatarForPublic else ""

        val pubFields = JSONObject()
            .put("uid", strVal(uid))
            .put("username", strVal(cleanUser))
            .put("usernameLower", strVal(cleanUser))
            .put("displayName", strVal(cleanDisplay))
            .put("displayNameLower", strVal(cleanDisplay.lowercase(Locale.ROOT).replace("\u0307", "").take(50)))
            .put("bio", strVal(cleanBio))
            .put("avatarBase64", strVal(avatarForPublic))
            .put("styleTags", strArrVal(cleanTags))
            .put("isPrivate", boolVal(draft.isPrivate))
            .put("updatedAtMs", intVal(nowMs))

        val (pCode, pBody) = execFs(
            "PATCH",
            "$FS_BASE/public_profiles/${enc(uid)}",
            token,
            JSONObject().put("fields", pubFields).toString()
        )
        if (pCode !in 200..299) {
            return@withContext Result.failure(IllegalStateException(formatFsError(pCode, pBody, "Profil kaydedilemedi")))
        }

        // Write public_profiles/{uid}/content/main
        val visMap = draft.statsVisibility
        val visFields = JSONObject()
            .put("showTotalItems", boolVal(visMap.showTotalItems))
            .put("showCategoryBreakdown", boolVal(visMap.showCategoryBreakdown))
            .put("showFavoriteCount", boolVal(visMap.showFavoriteCount))
            .put("showWishlistCount", boolVal(visMap.showWishlistCount))
            .put("showOutfitCount", boolVal(visMap.showOutfitCount))
            .put("showTopColors", boolVal(visMap.showTopColors))
            .put("showColorPalette", boolVal(visMap.showColorPalette))
            .put("showTopSubCategories", boolVal(visMap.showTopSubCategories))
            .put("showEstimatedValue", boolVal(visMap.showEstimatedValue))

        val summaryRaw = computeFilteredWardrobeSummary(items, outfits, visMap)
        val summaryFields = JSONObject()
        summaryRaw.forEach { (k, v) ->
            when (v) {
                is Number -> summaryFields.put(k, intVal(v.toLong()))
                else -> summaryFields.put(k, strVal(v.toString()))
            }
        }

        val cleanSections = draft.showcaseSections
            .filter { it in setOf("favorites", "wishlist", "selected", "custom") }
            .distinct()
        val contentFields = JSONObject()
            .put("ownerUid", strVal(uid))
            .put("statsVisibility", JSONObject().put("mapValue", JSONObject().put("fields", visFields)))
            .put("wardrobeSummary", JSONObject().put("mapValue", JSONObject().put("fields", summaryFields)))
            .put("showcaseSections", strArrVal(cleanSections))
            .put("updatedAtMs", intVal(nowMs))

        val (cCode, cBody) = execFs(
            "PATCH",
            "$FS_BASE/public_profiles/${enc(uid)}/content/main",
            token,
            JSONObject().put("fields", contentFields).toString()
        )
        if (cCode !in 200..299) {
            return@withContext Result.failure(IllegalStateException(formatFsError(cCode, cBody, "Profil içeriği kaydedilemedi")))
        }

        // Build desired showcase items (capped neatly for quota)
        val desiredShowcase = buildDesiredShowcaseItems(context, uid, items, draft.copy(
            username = cleanUser,
            displayName = cleanDisplay,
            bio = cleanBio,
            styleTags = cleanTags,
            showcaseSections = cleanSections
        ), nowMs)

        // List existing showcase items on Firestore and reconcile deletions + upserts
        val existingShowcase = listSubcollection(token, "public_profiles/${enc(uid)}/showcase")
        val desiredIds = desiredShowcase.map { it.itemId }.toSet()
        for (doc in existingShowcase) {
            val docId = doc.optString("name", "").substringAfterLast('/')
            if (docId.isNotEmpty() && docId !in desiredIds) {
                execFs("DELETE", "$FS_BASE/public_profiles/${enc(uid)}/showcase/${enc(docId)}", token, null)
            }
        }
        for (sc in desiredShowcase) {
            val scFields = JSONObject()
                .put("ownerUid", strVal(sc.ownerUid))
                .put("itemId", strVal(sc.itemId))
                .put("section", strVal(sc.section))
                .put("name", strVal(sc.name.take(120)))
                .put("category", strVal(sc.category.take(32)))
                .put("subCategory", strVal(sc.subCategory.take(32)))
                .put("styleTag", strVal(sc.styleTag.take(32)))
                .put("seasons", strArrVal(sc.seasons.take(5)))
                .put("colors", strArrVal(sc.colors.take(8)))
                .put("priceText", strVal(sc.priceText.take(40)))
                .put("productUrl", strVal(sc.productUrl.take(1000)))
                .put("notes", strVal(sc.notes.take(320)))
                .put("photoBase64", strVal(sc.photoBase64.take(240000)))
                .put("sortOrder", intVal(sc.sortOrder.toLong()))
                .put("createdAtMs", intVal(sc.createdAtMs.coerceAtLeast(1700000000001L)))
            execFs(
                "PATCH",
                "$FS_BASE/public_profiles/${enc(uid)}/showcase/${enc(sc.itemId)}",
                token,
                JSONObject().put("fields", scFields).toString()
            )
        }

        val finalProfile = draft.copy(
            username = cleanUser,
            displayName = cleanDisplay,
            bio = cleanBio,
            avatarBase64 = avatarForPublic,
            avatarThumbBase64 = avatarThumb,
            styleTags = cleanTags,
            showcaseSections = cleanSections,
            lastSyncedAtMs = nowMs
        )
        saveLocalProfile(context, finalProfile)
        if (prev.isPrivate != draft.isPrivate) {
            runCatching { SocialFeedService.migrateMyPostsForPrivacy(context, uid, draft.isPrivate) }
        }
        AnalyticsService.updateProfileIdentity(context, cleanUser, cleanDisplay, avatarThumb)
        Result.success(finalProfile)
    }

    private fun buildDesiredShowcaseItems(
        context: Context,
        uid: String,
        items: List<ClothingItem>,
        profile: LocalProfileConfig,
        nowMs: Long
    ): List<ShowcaseItemData> {
        val result = ArrayList<ShowcaseItemData>()
        val usedItemIds = HashSet<String>()
        var order = 0

        fun addWardrobeItem(item: ClothingItem, section: String) {
            if (result.size >= 24) return
            val docId = "${section}_${item.id}".take(76).replace(Regex("[^A-Za-z0-9_-]"), "_")
            if (!usedItemIds.add(docId)) return
            val photo = item.coverPhoto?.let { encodeGarmentPhotoForCloud(context, it, maxChars = 190_000) }.orEmpty()
            result.add(
                ShowcaseItemData(
                    ownerUid = uid,
                    itemId = docId,
                    section = section,
                    name = item.name.ifBlank { item.subCategory.label },
                    category = item.category.name,
                    subCategory = item.subCategory.name,
                    styleTag = item.styleTag.name,
                    seasons = item.seasons.map { it.name },
                    colors = item.colors.map { it.name },
                    priceText = item.priceText,
                    productUrl = item.productUrl,
                    notes = item.notes.take(320),
                    photoBase64 = photo,
                    sortOrder = order++,
                    createdAtMs = item.createdAt.coerceAtLeast(1700000000001L)
                )
            )
        }

        if ("selected" in profile.showcaseSections) {
            val selectedSet = profile.selectedShowcaseItemIds.toSet()
            items.filter { it.id in selectedSet }.take(12).forEach { addWardrobeItem(it, "selected") }
        }
        if ("favorites" in profile.showcaseSections) {
            items.filter { it.isFavorite }.take(12).forEach { addWardrobeItem(it, "favorites") }
        }
        if ("wishlist" in profile.showcaseSections) {
            items.filter { it.isWishlist }.take(10).forEach { addWardrobeItem(it, "wishlist") }
        }
        if ("custom" in profile.showcaseSections) {
            profile.customShowcaseItems.take(8).forEach { custom ->
                if (result.size < 28) {
                    val docId = "custom_${custom.id}".take(76).replace(Regex("[^A-Za-z0-9_-]"), "_")
                    if (usedItemIds.add(docId)) {
                        result.add(
                            ShowcaseItemData(
                                ownerUid = uid,
                                itemId = docId,
                                section = "custom",
                                name = custom.name.trim().ifBlank { "Özel Parça" }.take(120),
                                category = custom.category.ifBlank { ClothingCategory.TOP.name },
                                subCategory = "",
                                styleTag = "",
                                seasons = listOf(Season.ALL.name),
                                colors = emptyList(),
                                priceText = custom.priceText.take(40),
                                productUrl = custom.productUrl.take(1000),
                                notes = custom.notes.take(320),
                                photoBase64 = custom.photoBase64.take(230_000),
                                sortOrder = order++,
                                createdAtMs = custom.createdAtMs.coerceAtLeast(1700000000001L).takeIf { it <= nowMs } ?: nowMs
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    suspend fun shareGarment(
        context: Context,
        item: ClothingItem,
        profile: LocalProfileConfig
    ): Result<SharedGarmentData> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Sunucuya bağlanılamadı"))
        val (uid, token) = creds

        val photos = item.activePhotos.take(3).mapNotNull { fileName ->
            encodeGarmentPhotoForCloud(context, fileName, maxChars = 195_000)?.takeIf { it.isNotBlank() }
        }
        if (photos.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Paylaşılacak kıyafet fotoğrafı bulunamadı"))
        }

        val shareId = randomShareId()
        val nowMs = safeNowMs()
        val cleanUser = normalizeUsername(profile.username)
        val cleanDisplay = profile.displayName.trim().take(50)
        val cleanAvatar = if (profile.avatarThumbBase64.startsWith("data:image/") && profile.avatarThumbBase64.length <= 39000) {
            profile.avatarThumbBase64
        } else ""

        val fields = JSONObject()
            .put("shareId", strVal(shareId))
            .put("ownerUid", strVal(uid))
            .put("ownerUsername", strVal(cleanUser))
            .put("ownerDisplayName", strVal(cleanDisplay))
            .put("ownerAvatar", strVal(cleanAvatar))
            .put("name", strVal(item.name.ifBlank { item.subCategory.label }.take(120)))
            .put("category", strVal(item.category.name))
            .put("subCategory", strVal(item.subCategory.name))
            .put("styleTag", strVal(item.styleTag.name))
            .put("seasons", strArrVal(item.seasons.map { it.name }.take(5)))
            .put("colors", strArrVal(item.colors.map { it.name }.take(8)))
            .put("notes", strVal(item.notes.take(600)))
            .put("priceText", strVal(item.priceText.take(40)))
            .put("productUrl", strVal(item.productUrl.take(1000)))
            .put("isWishlist", boolVal(item.isWishlist))
            .put("photos", strArrVal(photos))
            .put("createdAtMs", intVal(nowMs))

        val (code, body) = execFs(
            "PATCH",
            "$FS_BASE/shared_items/${enc(shareId)}",
            token,
            JSONObject().put("fields", fields).toString()
        )
        if (code !in 200..299) {
            return@withContext Result.failure(IllegalStateException(formatFsError(code, body, "Paylaşım oluşturulamadı")))
        }

        AnalyticsService.trackFeature("garment_share", item.category.name)
        Result.success(
            SharedGarmentData(
                shareId = shareId,
                ownerUid = uid,
                ownerUsername = cleanUser,
                ownerDisplayName = cleanDisplay,
                ownerAvatar = cleanAvatar,
                name = item.name.ifBlank { item.subCategory.label },
                category = item.category.name,
                subCategory = item.subCategory.name,
                styleTag = item.styleTag.name,
                seasons = item.seasons.map { it.name },
                colors = item.colors.map { it.name },
                notes = item.notes,
                priceText = item.priceText,
                productUrl = item.productUrl,
                isWishlist = item.isWishlist,
                photos = photos,
                createdAtMs = nowMs
            )
        )
    }

    suspend fun updateSharedItemOwner(
        context: Context,
        shared: SharedGarmentData,
        profile: LocalProfileConfig
    ): Result<SharedGarmentData> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Sunucuya bağlanılamadı"))
        val (uid, token) = creds
        val cleanUser = normalizeUsername(profile.username)
        val cleanDisplay = profile.displayName.trim().take(50).ifBlank { cleanUser }
        val cleanAvatar = if (profile.avatarThumbBase64.startsWith("data:image/") && profile.avatarThumbBase64.length <= 39000) {
            profile.avatarThumbBase64
        } else ""

        val fields = JSONObject()
            .put("shareId", strVal(shared.shareId))
            .put("ownerUid", strVal(uid))
            .put("ownerUsername", strVal(cleanUser))
            .put("ownerDisplayName", strVal(cleanDisplay))
            .put("ownerAvatar", strVal(cleanAvatar))
            .put("name", strVal(shared.name.take(120)))
            .put("category", strVal(shared.category.take(32)))
            .put("subCategory", strVal(shared.subCategory.take(32)))
            .put("styleTag", strVal(shared.styleTag.take(32)))
            .put("seasons", strArrVal(shared.seasons.take(5)))
            .put("colors", strArrVal(shared.colors.take(8)))
            .put("notes", strVal(shared.notes.take(600)))
            .put("priceText", strVal(shared.priceText.take(40)))
            .put("productUrl", strVal(shared.productUrl.take(1000)))
            .put("isWishlist", boolVal(shared.isWishlist))
            .put("photos", strArrVal(shared.photos.take(3)))
            .put("createdAtMs", intVal(shared.createdAtMs))

        val (code, body) = execFs(
            "PATCH",
            "$FS_BASE/shared_items/${enc(shared.shareId)}",
            token,
            JSONObject().put("fields", fields).toString()
        )
        if (code !in 200..299) {
            return@withContext Result.failure(IllegalStateException(formatFsError(code, body, "Paylaşım güncellenemedi")))
        }
        Result.success(shared.copy(ownerUsername = cleanUser, ownerDisplayName = cleanDisplay, ownerAvatar = cleanAvatar))
    }

    suspend fun fetchSharedItem(context: Context, shareId: String): Result<SharedGarmentData?> =
        withContext(Dispatchers.IO) {
            if (!SHARE_ID_REGEX.matches(shareId)) return@withContext Result.success(null)
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (_, token) = creds
            val (code, body) = execFs("GET", "$FS_BASE/shared_items/${enc(shareId)}", token, null)
            if (code == 404) return@withContext Result.success(null)
            if (code !in 200..299) return@withContext Result.failure(IllegalStateException(formatFsError(code, body, "Kıyafet alınamadı")))
            val fields = JSONObject(body).optJSONObject("fields") ?: return@withContext Result.success(null)
            AnalyticsService.trackFeature("shared_item_open")
            Result.success(
                SharedGarmentData(
                    shareId = fsString(fields, "shareId").ifBlank { shareId },
                    ownerUid = fsString(fields, "ownerUid"),
                    ownerUsername = fsString(fields, "ownerUsername"),
                    ownerDisplayName = fsString(fields, "ownerDisplayName"),
                    ownerAvatar = fsString(fields, "ownerAvatar"),
                    name = fsString(fields, "name"),
                    category = fsString(fields, "category"),
                    subCategory = fsString(fields, "subCategory"),
                    styleTag = fsString(fields, "styleTag"),
                    seasons = fsStringList(fields, "seasons"),
                    colors = fsStringList(fields, "colors"),
                    notes = fsString(fields, "notes"),
                    priceText = fsString(fields, "priceText"),
                    productUrl = fsString(fields, "productUrl"),
                    isWishlist = fsBool(fields, "isWishlist"),
                    photos = fsStringList(fields, "photos"),
                    createdAtMs = fsLong(fields, "createdAtMs")
                )
            )
        }

    suspend fun searchProfiles(context: Context, query: String): Result<List<PublicProfileHeader>> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            if (query.trim().isNotEmpty()) {
                AnalyticsService.trackFeature("user_search")
            }
            val blockedUids = fetchAllBlockedUidsSync(token, myUid)
            val docs = listCollection(token, "public_profiles")
            val q = normalizeUsername(query)
            val rawQ = query.trim().lowercase(Locale.ROOT)
            val parsed = docs.mapNotNull(::parsePublicProfileDoc)
                .filter { it.uid !in blockedUids }
                .filter { it.username.isNotBlank() || it.displayName.isNotBlank() }
                .filter { p ->
                    if (rawQ.isEmpty()) p.username.isNotBlank()
                    else p.usernameLower.contains(q) || p.displayName.lowercase(Locale.ROOT).contains(rawQ)
                }
                .sortedByDescending { it.updatedAtMs }
                .take(40)
            Result.success(parsed)
        }

    suspend fun fetchProfileBundle(
        context: Context,
        targetUidOrUsername: String,
        isUsername: Boolean
    ): Result<LoadedProfileBundle?> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
        val (myUid, token) = creds

        val resolvedUid = if (isUsername) {
            val norm = normalizeUsername(targetUidOrUsername)
            val (uCode, uBody) = execFs("GET", "$FS_BASE/usernames/${enc(norm)}", token, null)
            if (uCode !in 200..299) {
                // Fallback search by public_profiles in case username doc differs
                val found = listCollection(token, "public_profiles")
                    .mapNotNull(::parsePublicProfileDoc)
                    .firstOrNull { it.usernameLower == norm }
                    ?: return@withContext Result.success(null)
                found.uid
            } else {
                fsString(JSONObject(uBody).optJSONObject("fields"), "uid")
            }
        } else {
            targetUidOrUsername
        }
        if (resolvedUid.isBlank()) return@withContext Result.success(null)

        val (pCode, pBody) = execFs("GET", "$FS_BASE/public_profiles/${enc(resolvedUid)}", token, null)
        if (pCode == 404) return@withContext Result.success(null)
        if (pCode !in 200..299) return@withContext Result.failure(IllegalStateException(formatFsError(pCode, pBody, "Profil yüklenemedi")))
        val header = parsePublicProfileDoc(JSONObject(pBody)) ?: return@withContext Result.success(null)

        val (blockedByMe, blockedMe) = fetchBlockedUidSets(token, myUid)
        val isBlockedByMe = resolvedUid != myUid && resolvedUid in blockedByMe
        val hasBlockedMe = resolvedUid != myUid && resolvedUid in blockedMe
        if (isBlockedByMe || hasBlockedMe) {
            return@withContext Result.success(
                LoadedProfileBundle(
                    header = header,
                    canViewContent = false,
                    followStatus = null,
                    followersCount = 0,
                    followingCount = 0,
                    pendingRequestUids = emptyList(),
                    content = null,
                    showcaseItems = emptyList(),
                    isBlockedByMe = isBlockedByMe,
                    hasBlockedMe = hasBlockedMe
                )
            )
        }

        val blockedAll = blockedByMe + blockedMe
        val followersList = queryFollowsByField(token, "targetUid", resolvedUid)
            .filter { it.followerUid !in blockedAll }
        val followingList = queryFollowsByField(token, "followerUid", resolvedUid)
            .filter { it.targetUid !in blockedAll }

        val acceptedFollowers = followersList.count { it.status == "accepted" }
        val acceptedFollowing = followingList.count { it.status == "accepted" }
        val pendingUids = if (resolvedUid == myUid) {
            followersList.filter { it.status == "pending" }.map { it.followerUid }
        } else emptyList()

        val myFollowRel = if (resolvedUid == myUid) null else followersList.firstOrNull { it.followerUid == myUid }
        val canView = (resolvedUid == myUid) || !header.isPrivate || (myFollowRel?.status == "accepted")

        var content: ProfileContentData? = null
        var showcase: List<ShowcaseItemData> = emptyList()
        if (canView) {
            val (cCode, cBody) = execFs("GET", "$FS_BASE/public_profiles/${enc(resolvedUid)}/content/main", token, null)
            if (cCode in 200..299) {
                content = parseProfileContentDoc(JSONObject(cBody))
            }
            val scDocs = listSubcollection(token, "public_profiles/${enc(resolvedUid)}/showcase")
            showcase = scDocs.mapNotNull(::parseShowcaseDoc).sortedBy { it.sortOrder }
        }

        Result.success(
            LoadedProfileBundle(
                header = header,
                canViewContent = canView,
                followStatus = myFollowRel?.status,
                followersCount = acceptedFollowers,
                followingCount = acceptedFollowing,
                pendingRequestUids = pendingUids,
                content = content,
                showcaseItems = showcase,
                isBlockedByMe = false,
                hasBlockedMe = false
            )
        )
    }

    suspend fun followUser(context: Context, target: PublicProfileHeader): Result<FollowRelation> =
        withContext(Dispatchers.IO) {
            val localMe = loadLocalProfile(context)
            if (!isValidUsername(localMe.username)) {
                return@withContext Result.failure(IllegalStateException("USERNAME_REQUIRED:Takip etmek için önce bir kullanıcı adı oluşturmalısın"))
            }
            if (!isValidUsername(target.username)) {
                return@withContext Result.failure(IllegalArgumentException("Bu kullanıcı henüz kullanıcı adı oluşturmamış"))
            }
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            if (myUid == target.uid) {
                return@withContext Result.failure(IllegalArgumentException("Kendini takip edemezsin"))
            }
            if (target.uid in fetchAllBlockedUidsSync(token, myUid)) {
                return@withContext Result.failure(IllegalStateException("Engellenen kullanıcı takip edilemez"))
            }
            val followId = "${myUid}_${target.uid}"
            val status = if (target.isPrivate) "pending" else "accepted"
            val nowMs = safeNowMs()
            val fields = JSONObject()
                .put("followerUid", strVal(myUid))
                .put("targetUid", strVal(target.uid))
                .put("status", strVal(status))
                .put("createdAtMs", intVal(nowMs))
                .put("updatedAtMs", intVal(nowMs))
            val (code, body) = execFs(
                "PATCH",
                "$FS_BASE/follows/${enc(followId)}",
                token,
                JSONObject().put("fields", fields).toString()
            )
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException(formatFsError(code, body, "Takip işlemi başarısız")))
            }
            AnalyticsService.trackFeature("user_follow", status)
            runCatching {
                SocialFeedService.sendNotificationBestEffort(
                    context = context,
                    targetUid = target.uid,
                    type = if (status == "pending") "follow_request" else "new_follower",
                    myProfile = localMe,
                    previewText = if (status == "pending") "seni takip etmek istedi" else "seni takip etmeye başladı"
                )
            }
            Result.success(FollowRelation(followId, myUid, target.uid, status, nowMs, nowMs))
        }

    suspend fun unfollowOrCancel(context: Context, followerUid: String, targetUid: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (_, token) = creds
            val followId = "${followerUid}_${targetUid}"
            val (code, _) = execFs("DELETE", "$FS_BASE/follows/${enc(followId)}", token, null)
            if (code in 200..299 || code == 404) Result.success(Unit)
            else Result.failure(IllegalStateException("İşlem başarısız ($code)"))
        }

    suspend fun acceptFollowRequest(context: Context, followerUid: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val localMe = loadLocalProfile(context)
            if (!isValidUsername(localMe.username)) {
                return@withContext Result.failure(IllegalStateException("USERNAME_REQUIRED:Takip isteğini onaylamak için önce bir kullanıcı adı oluşturmalısın"))
            }
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            val followId = "${followerUid}_${myUid}"
            val (gCode, gBody) = execFs("GET", "$FS_BASE/follows/${enc(followId)}", token, null)
            if (gCode !in 200..299) return@withContext Result.failure(IllegalStateException("İstek bulunamadı"))
            val existingFields = JSONObject(gBody).optJSONObject("fields")
            val createdMs = fsLong(existingFields, "createdAtMs").coerceAtLeast(1700000000001L)
            val nowMs = safeNowMs().coerceAtLeast(createdMs)
            val fields = JSONObject()
                .put("followerUid", strVal(followerUid))
                .put("targetUid", strVal(myUid))
                .put("status", strVal("accepted"))
                .put("createdAtMs", intVal(createdMs))
                .put("updatedAtMs", intVal(nowMs))
            val (pCode, _) = execFs(
                "PATCH",
                "$FS_BASE/follows/${enc(followId)}",
                token,
                JSONObject().put("fields", fields).toString()
            )
            if (pCode in 200..299) {
                AnalyticsService.trackFeature("follow_accept")
                val localMe = loadLocalProfile(context)
                runCatching {
                    SocialFeedService.sendNotificationBestEffort(
                        context = context,
                        targetUid = followerUid,
                        type = "follow_accepted",
                        myProfile = localMe,
                        previewText = "takip isteğini kabul etti"
                    )
                }
                Result.success(Unit)
            } else Result.failure(IllegalStateException("Onaylanamadı ($pCode)"))
        }

    suspend fun fetchPendingFollowRequests(context: Context): Result<List<PublicProfileHeader>> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            val blockedAll = fetchAllBlockedUidsSync(token, myUid)
            val rels = queryFollowsByField(token, "targetUid", myUid)
                .filter { it.status == "pending" && it.followerUid !in blockedAll }
            val profiles = rels.mapNotNull { rel ->
                val (c, b) = execFs("GET", "$FS_BASE/public_profiles/${enc(rel.followerUid)}", token, null)
                if (c in 200..299) parsePublicProfileDoc(JSONObject(b))
                else PublicProfileHeader(rel.followerUid, "", "", "Kullanıcı", "", "", emptyList(), false, rel.createdAtMs)
            }
            Result.success(profiles)
        }

    suspend fun fetchFollowList(
        context: Context,
        targetUid: String,
        followers: Boolean
    ): Result<List<PublicProfileHeader>> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
        val (myUid, token) = creds
        val blockedAll = fetchAllBlockedUidsSync(token, myUid)
        val rels = if (followers) {
            queryFollowsByField(token, "targetUid", targetUid)
        } else {
            queryFollowsByField(token, "followerUid", targetUid)
        }.filter { it.status == "accepted" }

        val profiles = rels.mapNotNull { rel ->
            val otherUid = if (followers) rel.followerUid else rel.targetUid
            if (otherUid in blockedAll) return@mapNotNull null
            val (c, b) = execFs("GET", "$FS_BASE/public_profiles/${enc(otherUid)}", token, null)
            if (c in 200..299) parsePublicProfileDoc(JSONObject(b)) else null
        }
        Result.success(profiles)
    }

    suspend fun fetchLikeInfo(context: Context, targetKey: String): Pair<Boolean, Int> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context) ?: return@withContext (false to 0)
            val (myUid, token) = creds
            val likes = queryBySingleField(token, "item_likes", "targetKey", targetKey)
            val uids = likes.mapNotNull { fsString(it.optJSONObject("fields"), "uid").ifBlank { null } }
            (myUid in uids) to uids.size
        }

    suspend fun toggleLike(context: Context, targetKey: String): Result<Pair<Boolean, Int>> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            val cleanKey = targetKey.trim().take(120)
            if (cleanKey.length < 4) {
                return@withContext Result.failure(IllegalArgumentException("Geçersiz beğeni anahtarı"))
            }
            val likeId = "${myUid}_${cleanKey}"
            val (gCode, _) = execFs("GET", "$FS_BASE/item_likes/${enc(likeId)}", token, null)
            if (gCode in 200..299) {
                execFs("DELETE", "$FS_BASE/item_likes/${enc(likeId)}", token, null)
            } else {
                val localMe = loadLocalProfile(context)
                if (!isValidUsername(localMe.username)) {
                    return@withContext Result.failure(IllegalStateException("USERNAME_REQUIRED:Beğenmek için önce bir kullanıcı adı oluşturmalısın"))
                }
                val fields = JSONObject()
                    .put("uid", strVal(myUid))
                    .put("targetKey", strVal(cleanKey))
                    .put("createdAtMs", intVal(safeNowMs()))
                execFs("PATCH", "$FS_BASE/item_likes/${enc(likeId)}", token, JSONObject().put("fields", fields).toString())
            }
            val updated = fetchLikeInfo(context, cleanKey)
            Result.success(updated)
        }

    suspend fun importRemoteGarmentLocally(
        context: Context,
        repository: WardrobeRepository,
        name: String,
        categoryStr: String,
        subCategoryStr: String,
        styleTagStr: String,
        seasonStrs: List<String>,
        colorStrs: List<String>,
        notes: String,
        priceText: String,
        productUrl: String,
        photosBase64: List<String>,
        asWishlist: Boolean
    ): ClothingItem? = withContext(Dispatchers.IO) {
        val pairs = photosBase64.take(3).mapNotNull { dataUrl ->
            val bmp = decodeDataUrlToBitmap(dataUrl) ?: return@mapNotNull null
            try {
                ImageStorage.saveFromBitmap(context, bmp)
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        val cat = runCatching { ClothingCategory.valueOf(categoryStr) }.getOrDefault(ClothingCategory.TOP)
        val sub = runCatching { ClothingSubCategory.valueOf(subCategoryStr) }.getOrElse {
            ClothingSubCategory.forCategory(cat).firstOrNull() ?: ClothingSubCategory.TSHIRT
        }
        val style = runCatching { StyleTag.valueOf(styleTagStr) }.getOrDefault(StyleTag.CASUAL)
        val seasons = seasonStrs.mapNotNull { runCatching { Season.valueOf(it) }.getOrNull() }.ifEmpty { listOf(Season.ALL) }
        val colors = colorStrs.mapNotNull { runCatching { GarmentColor.valueOf(it) }.getOrNull() }
        val safeUrl = if (PriceTrackerService.isSafeHttpsUrl(productUrl)) productUrl else ""
        val item = ClothingItem(
            id = repository.generateId(),
            name = name.ifBlank { sub.label }.take(120),
            category = cat,
            subCategory = sub,
            styleTag = style,
            seasons = seasons,
            colors = colors,
            photoFileNames = pairs.map { it.studioFileName },
            originalPhotoFileNames = pairs.map { it.originalFileName },
            useOriginalPhotos = pairs.isNotEmpty(),
            notes = notes.take(600),
            isWishlist = asWishlist,
            priceText = if (asWishlist) priceText.take(40) else "",
            productUrl = if (asWishlist) safeUrl else ""
        )
        repository.addItem(item)
        item
    }

    // ==================== User Blocking ====================

    private fun fetchBlockedUidSets(token: String, myUid: String): Pair<Set<String>, Set<String>> {
        val byMe = queryBySingleField(token, "blocks", "blockerUid", myUid).mapNotNull { doc ->
            fsString(doc.optJSONObject("fields"), "blockedUid").ifBlank { null }
        }.toSet()
        val againstMe = queryBySingleField(token, "blocks", "blockedUid", myUid).mapNotNull { doc ->
            fsString(doc.optJSONObject("fields"), "blockerUid").ifBlank { null }
        }.toSet()
        return byMe to againstMe
    }

    internal fun fetchAllBlockedUidsSync(token: String, myUid: String): Set<String> {
        val (byMe, againstMe) = fetchBlockedUidSets(token, myUid)
        return byMe + againstMe
    }

    suspend fun fetchBlockedAccounts(context: Context): Result<List<PublicProfileHeader>> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            val (byMe, _) = fetchBlockedUidSets(token, myUid)
            val list = byMe.map { otherUid ->
                val (c, b) = execFs("GET", "$FS_BASE/public_profiles/${enc(otherUid)}", token, null)
                if (c in 200..299) {
                    parsePublicProfileDoc(JSONObject(b))
                        ?: PublicProfileHeader(otherUid, "", "", "Kullanıcı", "", "", emptyList(), false, 0L)
                } else {
                    PublicProfileHeader(otherUid, "", "", "Kullanıcı", "", "", emptyList(), false, 0L)
                }
            }
            Result.success(list)
        }

    suspend fun blockUser(context: Context, targetUid: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            if (targetUid.isBlank() || targetUid == myUid) {
                return@withContext Result.failure(IllegalArgumentException("Kendini engelleyemezsin"))
            }
            val blockId = "${myUid}_${targetUid}"
            val (existsCode, _) = execFs("GET", "$FS_BASE/blocks/${enc(blockId)}", token, null)
            if (existsCode != 200) {
                val fields = JSONObject()
                    .put("blockerUid", strVal(myUid))
                    .put("blockedUid", strVal(targetUid))
                    .put("createdAtMs", intVal(safeNowMs()))
                val (code, body) = execFs(
                    "PATCH",
                    "$FS_BASE/blocks/${enc(blockId)}?currentDocument.exists=false",
                    token,
                    JSONObject().put("fields", fields).toString()
                )
                if (code !in 200..299 && code != 409) {
                    return@withContext Result.failure(IllegalStateException(formatFsError(code, body, "Engellenemedi")))
                }
            }
            execFs("DELETE", "$FS_BASE/follows/${enc("${myUid}_${targetUid}")}", token, null)
            execFs("DELETE", "$FS_BASE/follows/${enc("${targetUid}_${myUid}")}", token, null)
            AnalyticsService.trackFeature("user_block")
            Result.success(Unit)
        }

    suspend fun unblockUser(context: Context, targetUid: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            if (targetUid.isBlank() || targetUid == myUid) return@withContext Result.success(Unit)
            val blockId = "${myUid}_${targetUid}"
            val (code, body) = execFs("DELETE", "$FS_BASE/blocks/${enc(blockId)}", token, null)
            if (code in 200..299 || code == 404) {
                AnalyticsService.trackFeature("user_unblock")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(formatFsError(code, body, "Engel kaldırılamadı")))
            }
        }

    // ==================== Shared Outfits ====================

    private fun buildSharedOutfitPieceMeta(
        pieces: List<SharedOutfitPiece>,
        stripExtras: Boolean = false
    ): String {
        val arr = JSONArray()
        pieces.forEach { p ->
            val obj = JSONObject()
                .put("name", p.name.take(120))
                .put("category", p.category.take(32))
                .put("subCategory", p.subCategory.take(32))
                .put("styleTag", p.styleTag.take(32))
                .put("seasons", JSONArray(p.seasons.take(5)))
                .put("colors", JSONArray(p.colors.take(8)))
                .put("priceText", p.priceText.take(40))
                .put("productUrl", if (stripExtras) "" else p.productUrl.take(400))
                .put("notes", if (stripExtras) "" else p.notes.take(160))
                .put("photoIndex", p.photoIndex)
            arr.put(obj)
        }
        val raw = arr.toString()
        return if (!stripExtras && raw.length > 11_500) buildSharedOutfitPieceMeta(pieces, stripExtras = true) else raw.take(11_900)
    }

    private fun buildSharedOutfitFields(
        shareId: String,
        ownerUid: String,
        ownerUsername: String,
        ownerDisplayName: String,
        ownerAvatar: String,
        title: String,
        weatherLabel: String,
        harmonyScore: Int,
        harmonyNote: String,
        notes: String,
        pieces: List<SharedOutfitPiece>,
        createdAtMs: Long
    ): JSONObject {
        val cleanPieces = pieces.take(8)
        val pieceNames = cleanPieces.map { it.name.trim().ifBlank { "Parça" }.take(120) }
        val pieceCategories = cleanPieces.map { it.category.trim().ifBlank { "TOP" }.take(32) }
        val piecePhotos = cleanPieces.mapNotNull { p -> p.photoBase64.takeIf { it.isNotBlank() }?.take(138_000) }.take(6)
        val indexedPieces = cleanPieces.map { p ->
            val idx = if (p.photoBase64.isNotBlank()) piecePhotos.indexOf(p.photoBase64.take(138_000)) else -1
            p.copy(photoIndex = idx)
        }
        val metaJson = buildSharedOutfitPieceMeta(indexedPieces).let { if (it.length < 2) "[]" else it }
        return JSONObject()
            .put("shareId", strVal(shareId))
            .put("ownerUid", strVal(ownerUid))
            .put("ownerUsername", strVal(ownerUsername.take(24)))
            .put("ownerDisplayName", strVal(ownerDisplayName.take(50)))
            .put("ownerAvatar", strVal(ownerAvatar.take(39_000)))
            .put("title", strVal(title.trim().ifBlank { "Kombinim" }.take(120)))
            .put("weatherLabel", strVal(weatherLabel.trim().take(60)))
            .put("harmonyScore", intVal(harmonyScore.coerceIn(0, 100).toLong()))
            .put("harmonyNote", strVal(harmonyNote.trim().take(140)))
            .put("notes", strVal(notes.trim().take(600)))
            .put("pieceNames", strArrVal(pieceNames))
            .put("pieceCategories", strArrVal(pieceCategories))
            .put("pieceMetaJson", strVal(metaJson))
            .put("piecePhotos", strArrVal(piecePhotos))
            .put("createdAtMs", intVal(createdAtMs.coerceAtLeast(MIN_VALID_EPOCH_MS)))
    }

    suspend fun shareOutfit(
        context: Context,
        title: String,
        pieces: List<ClothingItem>,
        harmonyScore: Int,
        harmonyNote: String,
        weatherLabel: String,
        notes: String = "",
        profile: LocalProfileConfig
    ): Result<SharedOutfitData> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Sunucuya bağlanılamadı"))
        val (uid, token) = creds
        val cappedPieces = pieces.take(8)
        if (cappedPieces.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Paylaşılacak kombin parçası bulunamadı"))
        }

        val shareId = randomShareId()
        val nowMs = safeNowMs()
        val cleanUser = normalizeUsername(profile.username)
        val cleanDisplay = profile.displayName.trim().take(50)
        val cleanAvatar = if (profile.avatarThumbBase64.startsWith("data:image/") && profile.avatarThumbBase64.length <= 39000) {
            profile.avatarThumbBase64
        } else ""

        var encodedPhotoCount = 0
        val sharedPieces = cappedPieces.map { item ->
            var photoStr = ""
            if (encodedPhotoCount < 6) {
                val fileName = item.activePhotos.firstOrNull()
                if (!fileName.isNullOrBlank()) {
                    val encoded = encodeGarmentPhotoForCloud(context, fileName, maxChars = 130_000)?.takeIf { it.isNotBlank() }
                    if (encoded != null) {
                        photoStr = encoded
                        encodedPhotoCount++
                    }
                }
            }
            SharedOutfitPiece(
                name = item.name.ifBlank { item.subCategory.label }.take(120),
                category = item.category.name,
                subCategory = item.subCategory.name,
                styleTag = item.styleTag.name,
                seasons = item.seasons.map { it.name }.take(5),
                colors = item.colors.map { it.name }.take(8),
                priceText = item.priceText.take(40),
                productUrl = item.productUrl.take(400),
                notes = item.notes.take(160),
                photoIndex = if (photoStr.isNotEmpty()) encodedPhotoCount - 1 else -1,
                photoBase64 = photoStr
            )
        }

        val cleanTitle = title.trim().ifBlank { "Kombinim" }.take(120)
        val fields = buildSharedOutfitFields(
            shareId = shareId,
            ownerUid = uid,
            ownerUsername = cleanUser,
            ownerDisplayName = cleanDisplay,
            ownerAvatar = cleanAvatar,
            title = cleanTitle,
            weatherLabel = weatherLabel,
            harmonyScore = harmonyScore,
            harmonyNote = harmonyNote,
            notes = notes,
            pieces = sharedPieces,
            createdAtMs = nowMs
        )

        val (code, body) = execFs(
            "PATCH",
            "$FS_BASE/shared_outfits/${enc(shareId)}",
            token,
            JSONObject().put("fields", fields).toString()
        )
        if (code !in 200..299) {
            return@withContext Result.failure(IllegalStateException(formatFsError(code, body, "Kombin paylaşılamadı")))
        }

        AnalyticsService.trackFeature("outfit_share")
        Result.success(
            SharedOutfitData(
                shareId = shareId,
                ownerUid = uid,
                ownerUsername = cleanUser,
                ownerDisplayName = cleanDisplay,
                ownerAvatar = cleanAvatar,
                title = cleanTitle,
                weatherLabel = weatherLabel.trim().take(60),
                harmonyScore = harmonyScore.coerceIn(0, 100),
                harmonyNote = harmonyNote.trim().take(140),
                notes = notes.trim().take(600),
                pieces = sharedPieces,
                createdAtMs = nowMs
            )
        )
    }

    suspend fun updateSharedOutfitOwner(
        context: Context,
        shared: SharedOutfitData,
        profile: LocalProfileConfig
    ): Result<SharedOutfitData> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Sunucuya bağlanılamadı"))
        val (uid, token) = creds
        val cleanUser = normalizeUsername(profile.username)
        val cleanDisplay = profile.displayName.trim().take(50).ifBlank { cleanUser }
        val cleanAvatar = if (profile.avatarThumbBase64.startsWith("data:image/") && profile.avatarThumbBase64.length <= 39000) {
            profile.avatarThumbBase64
        } else ""

        val fields = buildSharedOutfitFields(
            shareId = shared.shareId,
            ownerUid = uid,
            ownerUsername = cleanUser,
            ownerDisplayName = cleanDisplay,
            ownerAvatar = cleanAvatar,
            title = shared.title,
            weatherLabel = shared.weatherLabel,
            harmonyScore = shared.harmonyScore,
            harmonyNote = shared.harmonyNote,
            notes = shared.notes,
            pieces = shared.pieces,
            createdAtMs = shared.createdAtMs
        )

        val (code, body) = execFs(
            "PATCH",
            "$FS_BASE/shared_outfits/${enc(shared.shareId)}",
            token,
            JSONObject().put("fields", fields).toString()
        )
        if (code !in 200..299) {
            return@withContext Result.failure(IllegalStateException(formatFsError(code, body, "Kombin paylaşımı güncellenemedi")))
        }
        Result.success(shared.copy(ownerUsername = cleanUser, ownerDisplayName = cleanDisplay, ownerAvatar = cleanAvatar))
    }

    suspend fun fetchSharedOutfit(context: Context, shareId: String): Result<SharedOutfitData?> =
        withContext(Dispatchers.IO) {
            if (!SHARE_ID_REGEX.matches(shareId)) return@withContext Result.success(null)
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (_, token) = creds
            val (code, body) = execFs("GET", "$FS_BASE/shared_outfits/${enc(shareId)}", token, null)
            if (code == 404) return@withContext Result.success(null)
            if (code !in 200..299) return@withContext Result.failure(IllegalStateException(formatFsError(code, body, "Kombin alınamadı")))
            val fields = JSONObject(body).optJSONObject("fields") ?: return@withContext Result.success(null)
            val pieceNames = fsStringList(fields, "pieceNames")
            val pieceCats = fsStringList(fields, "pieceCategories")
            val piecePhotos = fsStringList(fields, "piecePhotos")
            val metaRaw = fsString(fields, "pieceMetaJson")
            val metaArr = runCatching { JSONArray(metaRaw) }.getOrNull()
            val pieces = if (metaArr != null && metaArr.length() > 0) {
                buildList {
                    for (i in 0 until metaArr.length()) {
                        val obj = metaArr.optJSONObject(i) ?: continue
                        val pIdx = obj.optInt("photoIndex", -1)
                        val photo = piecePhotos.getOrNull(pIdx).orEmpty()
                        val seasonsArr = obj.optJSONArray("seasons")
                        val colorsArr = obj.optJSONArray("colors")
                        add(
                            SharedOutfitPiece(
                                name = obj.optString("name", pieceNames.getOrNull(i) ?: "Parça"),
                                category = obj.optString("category", pieceCats.getOrNull(i) ?: "TOP"),
                                subCategory = obj.optString("subCategory", "TSHIRT"),
                                styleTag = obj.optString("styleTag", "CASUAL"),
                                seasons = buildList { if (seasonsArr != null) for (j in 0 until seasonsArr.length()) add(seasonsArr.optString(j)) },
                                colors = buildList { if (colorsArr != null) for (j in 0 until colorsArr.length()) add(colorsArr.optString(j)) },
                                priceText = obj.optString("priceText", ""),
                                productUrl = obj.optString("productUrl", ""),
                                notes = obj.optString("notes", ""),
                                photoIndex = pIdx,
                                photoBase64 = photo
                            )
                        )
                    }
                }
            } else {
                pieceNames.mapIndexed { idx, pName ->
                    SharedOutfitPiece(
                        name = pName,
                        category = pieceCats.getOrNull(idx) ?: "TOP",
                        subCategory = "TSHIRT",
                        styleTag = "CASUAL",
                        seasons = listOf("ALL"),
                        colors = emptyList(),
                        priceText = "",
                        productUrl = "",
                        notes = "",
                        photoIndex = if (idx < piecePhotos.size) idx else -1,
                        photoBase64 = piecePhotos.getOrNull(idx).orEmpty()
                    )
                }
            }

            AnalyticsService.trackFeature("shared_outfit_open")
            Result.success(
                SharedOutfitData(
                    shareId = fsString(fields, "shareId").ifBlank { shareId },
                    ownerUid = fsString(fields, "ownerUid"),
                    ownerUsername = fsString(fields, "ownerUsername"),
                    ownerDisplayName = fsString(fields, "ownerDisplayName"),
                    ownerAvatar = fsString(fields, "ownerAvatar"),
                    title = fsString(fields, "title"),
                    weatherLabel = fsString(fields, "weatherLabel"),
                    harmonyScore = fsLong(fields, "harmonyScore").toInt().coerceIn(0, 100),
                    harmonyNote = fsString(fields, "harmonyNote"),
                    notes = fsString(fields, "notes"),
                    pieces = pieces,
                    createdAtMs = fsLong(fields, "createdAtMs")
                )
            )
        }

    suspend fun importSharedOutfitLocally(
        context: Context,
        repository: WardrobeRepository,
        shared: SharedOutfitData
    ): Result<Outfit> = withContext(Dispatchers.IO) {
        val importedIds = ArrayList<String>()
        for (piece in shared.pieces) {
            val saved = importRemoteGarmentLocally(
                context = context,
                repository = repository,
                name = piece.name,
                categoryStr = piece.category,
                subCategoryStr = piece.subCategory,
                styleTagStr = piece.styleTag,
                seasonStrs = piece.seasons,
                colorStrs = piece.colors,
                notes = piece.notes,
                priceText = piece.priceText,
                productUrl = piece.productUrl,
                photosBase64 = listOfNotNull(piece.photoBase64.takeIf { it.isNotBlank() }),
                asWishlist = false
            )
            if (saved != null) importedIds.add(saved.id)
        }
        val outfit = Outfit(
            id = repository.generateId(),
            title = shared.title.ifBlank { "Paylaşılan Kombin" }.take(120),
            itemIds = importedIds,
            harmonyScore = shared.harmonyScore.coerceIn(0, 100)
        )
        repository.addOutfit(outfit)
        AnalyticsService.trackFeature("shared_outfit_import")
        Result.success(outfit)
    }

    // ==================== Helpers & Parsing ====================

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun queryFollowsByField(token: String, field: String, value: String): List<FollowRelation> {
        return queryBySingleField(token, "follows", field, value).mapNotNull { doc ->
            val fields = doc.optJSONObject("fields") ?: return@mapNotNull null
            val docId = doc.optString("name", "").substringAfterLast('/')
            val follower = fsString(fields, "followerUid")
            val target = fsString(fields, "targetUid")
            val status = fsString(fields, "status")
            if (follower.isBlank() || target.isBlank()) null
            else FollowRelation(
                followId = docId,
                followerUid = follower,
                targetUid = target,
                status = status,
                createdAtMs = fsLong(fields, "createdAtMs"),
                updatedAtMs = fsLong(fields, "updatedAtMs")
            )
        }
    }

    private fun queryBySingleField(
        token: String,
        collectionId: String,
        fieldPath: String,
        stringValue: String
    ): List<JSONObject> {
        val queryObj = JSONObject().put(
            "structuredQuery",
            JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", collectionId)))
                .put(
                    "where",
                    JSONObject().put(
                        "fieldFilter",
                        JSONObject()
                            .put("field", JSONObject().put("fieldPath", fieldPath))
                            .put("op", "EQUAL")
                            .put("value", strVal(stringValue))
                    )
                )
                .put("limit", 200)
        )
        val (code, body) = execFs("POST", "$FS_BASE:runQuery", token, queryObj.toString())
        if (code !in 200..299) return emptyList()
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.optJSONObject("document")?.let { out.add(it) }
        }
        return out
    }

    private fun listCollection(token: String, collection: String): List<JSONObject> {
        val docs = ArrayList<JSONObject>()
        var pageToken = ""
        for (p in 0 until 5) {
            val qs = if (pageToken.isNotEmpty()) "&pageToken=${enc(pageToken)}" else ""
            val (code, body) = execFs("GET", "$FS_BASE/$collection?pageSize=200$qs", token, null)
            if (code !in 200..299) break
            val json = runCatching { JSONObject(body) }.getOrNull() ?: break
            val arr = json.optJSONArray("documents") ?: JSONArray()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { docs.add(it) }
            }
            pageToken = json.optString("nextPageToken", "")
            if (pageToken.isEmpty()) break
        }
        return docs
    }

    private fun listSubcollection(token: String, path: String): List<JSONObject> {
        val (code, body) = execFs("GET", "$FS_BASE/$path?pageSize=100", token, null)
        if (code !in 200..299) return emptyList()
        val arr = runCatching { JSONObject(body).optJSONArray("documents") }.getOrNull() ?: return emptyList()
        val out = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { out.add(it) }
        }
        return out
    }

    internal fun fetchPublicProfileHeader(token: String, uid: String): PublicProfileHeader? {
        val (code, body) = execFs("GET", "$FS_BASE/public_profiles/${enc(uid)}", token, null)
        if (code !in 200..299) return null
        return runCatching { parsePublicProfileDoc(JSONObject(body)) }.getOrNull()
    }

    private fun parsePublicProfileDoc(doc: JSONObject): PublicProfileHeader? {
        val fields = doc.optJSONObject("fields") ?: return null
        val docUid = doc.optString("name", "").substringAfterLast('/')
        val uid = fsString(fields, "uid").ifBlank { docUid }
        if (uid.isBlank()) return null
        val user = fsString(fields, "username")
        return PublicProfileHeader(
            uid = uid,
            username = user,
            usernameLower = fsString(fields, "usernameLower").ifBlank { normalizeUsername(user) },
            displayName = fsString(fields, "displayName"),
            bio = fsString(fields, "bio"),
            avatarBase64 = fsString(fields, "avatarBase64"),
            styleTags = fsStringList(fields, "styleTags"),
            isPrivate = fsBool(fields, "isPrivate"),
            updatedAtMs = fsLong(fields, "updatedAtMs")
        )
    }

    private fun parseProfileContentDoc(doc: JSONObject): ProfileContentData? {
        val fields = doc.optJSONObject("fields") ?: return null
        val visFields = fields.optJSONObject("statsVisibility")?.optJSONObject("mapValue")?.optJSONObject("fields")
        val vis = StatsVisibilityConfig(
            showTotalItems = fsBool(visFields, "showTotalItems", true),
            showCategoryBreakdown = fsBool(visFields, "showCategoryBreakdown", true),
            showFavoriteCount = fsBool(visFields, "showFavoriteCount", true),
            showWishlistCount = fsBool(visFields, "showWishlistCount", true),
            showOutfitCount = fsBool(visFields, "showOutfitCount", true),
            showTopColors = fsBool(visFields, "showTopColors", true),
            showColorPalette = fsBool(visFields, "showColorPalette", true),
            showTopSubCategories = fsBool(visFields, "showTopSubCategories", true),
            showEstimatedValue = fsBool(visFields, "showEstimatedValue", false)
        )
        val sumFields = fields.optJSONObject("wardrobeSummary")?.optJSONObject("mapValue")?.optJSONObject("fields")
        val sumMap = LinkedHashMap<String, Any>()
        if (sumFields != null) {
            val keys = sumFields.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val vObj = sumFields.optJSONObject(k) ?: continue
                when {
                    vObj.has("integerValue") -> sumMap[k] = vObj.optString("integerValue").toLongOrNull() ?: 0L
                    vObj.has("stringValue") -> sumMap[k] = vObj.optString("stringValue")
                }
            }
        }
        return ProfileContentData(
            ownerUid = fsString(fields, "ownerUid"),
            statsVisibility = vis,
            wardrobeSummary = sumMap,
            showcaseSections = fsStringList(fields, "showcaseSections"),
            updatedAtMs = fsLong(fields, "updatedAtMs")
        )
    }

    private fun parseShowcaseDoc(doc: JSONObject): ShowcaseItemData? {
        val fields = doc.optJSONObject("fields") ?: return null
        val docId = doc.optString("name", "").substringAfterLast('/')
        val itemId = fsString(fields, "itemId").ifBlank { docId }
        val name = fsString(fields, "name")
        if (itemId.isBlank() || name.isBlank()) return null
        return ShowcaseItemData(
            ownerUid = fsString(fields, "ownerUid"),
            itemId = itemId,
            section = fsString(fields, "section").ifBlank { "selected" },
            name = name,
            category = fsString(fields, "category"),
            subCategory = fsString(fields, "subCategory"),
            styleTag = fsString(fields, "styleTag"),
            seasons = fsStringList(fields, "seasons"),
            colors = fsStringList(fields, "colors"),
            priceText = fsString(fields, "priceText"),
            productUrl = fsString(fields, "productUrl"),
            notes = fsString(fields, "notes"),
            photoBase64 = fsString(fields, "photoBase64"),
            sortOrder = fsLong(fields, "sortOrder").toInt(),
            createdAtMs = fsLong(fields, "createdAtMs")
        )
    }

    private fun execFs(method: String, url: String, bearer: String, jsonBody: String?): Pair<Int, String> {
        if (!SecurityGuards.isSafePublicHttpsUrl(url, 2048)) return 0 to ""
        val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return 0 to ""
        if (host !in ALLOWED_HOSTS || !SecurityGuards.isPublicHostResolved(host)) return 0 to ""
        var conn: HttpsURLConnection? = null
        return try {
            val actualMethod = if (method == "PATCH") "POST" else method
            conn = (URL(url).openConnection() as? HttpsURLConnection)?.apply {
                connectTimeout = 8000
                readTimeout = 10000
                instanceFollowRedirects = false
                requestMethod = actualMethod
                if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("Authorization", "Bearer $bearer")
                if (jsonBody != null) {
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                }
            } ?: return 0 to ""
            if (jsonBody != null) {
                BufferedWriter(OutputStreamWriter(conn.outputStream, Charsets.UTF_8)).use {
                    it.write(jsonBody)
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            code to body
        } catch (_: Exception) {
            0 to ""
        } finally {
            conn?.disconnect()
        }
    }

    private fun strVal(v: String) = JSONObject().put("stringValue", v)
    private fun intVal(v: Long) = JSONObject().put("integerValue", v.toString())
    private fun boolVal(v: Boolean) = JSONObject().put("booleanValue", v)
    private fun strArrVal(list: List<String>): JSONObject {
        val arr = JSONArray()
        list.forEach { arr.put(strVal(it)) }
        return JSONObject().put("arrayValue", JSONObject().put("values", arr))
    }

    private fun fsString(fields: JSONObject?, key: String): String =
        fields?.optJSONObject(key)?.optString("stringValue", "").orEmpty()

    private fun fsLong(fields: JSONObject?, key: String): Long {
        val obj = fields?.optJSONObject(key) ?: return 0L
        return obj.optString("integerValue", "").toLongOrNull() ?: 0L
    }

    private fun fsBool(fields: JSONObject?, key: String, default: Boolean = false): Boolean {
        val obj = fields?.optJSONObject(key) ?: return default
        return if (obj.has("booleanValue")) obj.optBoolean("booleanValue", default) else default
    }

    private fun fsStringList(fields: JSONObject?, key: String): List<String> {
        val values = fields?.optJSONObject(key)?.optJSONObject("arrayValue")?.optJSONArray("values") ?: return emptyList()
        return buildList {
            for (i in 0 until values.length()) {
                values.optJSONObject(i)?.optString("stringValue", "")?.let { add(it) }
            }
        }
    }
}
