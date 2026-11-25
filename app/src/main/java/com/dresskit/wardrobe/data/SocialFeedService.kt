package com.dresskit.wardrobe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Base64
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.LocalProfileConfig
import com.dresskit.wardrobe.model.PostCommentData
import com.dresskit.wardrobe.model.SocialNotificationData
import com.dresskit.wardrobe.model.SocialPostData
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

/**
 * Cloud REST client for the Dolabim Social Feed:
 * - Public posts (`public_posts/{postId}`) & Follower-only private posts (`public_profiles/{uid}/private_posts/{postId}`)
 * - Chunked video upload & playback cache (`media_chunks/{0..15}`)
 * - 15-minute caption edit window & deletion
 * - Comments with photos / animated GIFs (`comments/{commentId}`)
 * - Per-user notifications (`notifications/{targetUid}/items/{notifId}`)
 * - Post privacy migration when toggling account visibility
 */
object SocialFeedService {

    private const val FIREBASE_PROJECT_ID = "dolabim-stats"
    private const val FS_BASE =
        "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/(default)/documents"
    private val ALLOWED_HOSTS = setOf("firestore.googleapis.com")
    private val POST_ID_REGEX = Regex("^[A-Za-z0-9_-]{8,48}$")
    private const val MIN_VALID_EPOCH_MS = 1700000000001L
    private const val MAX_VALID_EPOCH_MS = 4102444800000L
    private const val VIDEO_CHUNK_BYTES = 500_000
    private const val MAX_VIDEO_BYTES = 7_500_000L
    private const val MAX_VIDEO_CHUNKS = 16

    private fun safeNowMs(): Long =
        System.currentTimeMillis().coerceIn(MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS - 5000L)

    private fun randomId(prefix: String = ""): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789_-"
        val rnd = SecureRandom()
        val tail = buildString(14) { repeat(14) { append(chars[rnd.nextInt(chars.length)]) } }
        return (prefix + tail).take(44)
    }

    private fun cleanOwnerMeta(context: Context, profile: LocalProfileConfig): Triple<String, String, String> {
        val eff = if (profile.username.isBlank() && profile.displayName.isBlank()) {
            SocialProfileService.loadLocalProfile(context)
        } else profile
        val cleanUser = SocialProfileService.normalizeUsername(eff.username).take(24)
        val cleanDisplay = eff.displayName.trim().take(50).ifBlank { cleanUser }
        val cleanAvatar = if (eff.avatarThumbBase64.startsWith("data:image/") && eff.avatarThumbBase64.length <= 39000) {
            eff.avatarThumbBase64
        } else if (eff.avatarBase64.startsWith("data:image/") && eff.avatarBase64.length <= 39000) {
            eff.avatarBase64
        } else ""
        return Triple(cleanUser, cleanDisplay, cleanAvatar)
    }

    private fun postCollectionPath(ownerUid: String, isPrivatePost: Boolean): String =
        if (isPrivatePost) "public_profiles/${enc(ownerUid)}/private_posts" else "public_posts"

    private fun postDocUrl(ownerUid: String, postId: String, isPrivatePost: Boolean): String =
        "$FS_BASE/${postCollectionPath(ownerUid, isPrivatePost)}/${enc(postId)}"

    suspend fun fetchFeedPosts(
        context: Context,
        followingOnly: Boolean,
        myIsPrivate: Boolean
    ): Result<List<SocialPostData>> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("İnternet bağlantısı kurulamadı"))
        val (myUid, token) = creds
        val blockedUids = SocialProfileService.fetchAllBlockedUidsSync(token, myUid)

        val followingDocs = queryBySingleField(token, "follows", "followerUid", myUid)
        val acceptedFollowingUids = followingDocs.mapNotNull { doc ->
            val f = doc.optJSONObject("fields") ?: return@mapNotNull null
            if (fsString(f, "status") == "accepted") fsString(f, "targetUid") else null
        }.filter { it !in blockedUids }.toSet()

        val collected = LinkedHashMap<String, SocialPostData>()

        // 1. Fetch public posts
        listCollectionPath(token, "public_posts").mapNotNull {
            parsePostDoc(it, isPrivatePost = false)
        }.forEach { p ->
            if (p.ownerUid !in blockedUids) collected[p.postId] = p
        }

        // 2. Fetch own private posts + accepted private friends' private posts
        val privateOwnersToFetch = LinkedHashSet<String>().apply {
            add(myUid)
            addAll(acceptedFollowingUids)
        }
        for (ownerUid in privateOwnersToFetch.take(40)) {
            if (ownerUid in blockedUids && ownerUid != myUid) continue
            if (!ownerUid.matches(Regex("^[A-Za-z0-9_-]{1,128}$"))) continue
            val docs = listCollectionPath(token, "public_profiles/${enc(ownerUid)}/private_posts")
            docs.mapNotNull { parsePostDoc(it, isPrivatePost = true) }.forEach { p ->
                if (p.ownerUid !in blockedUids) collected[p.postId] = p
            }
        }

        val sorted = collected.values
            .filter { post ->
                if (post.ownerUid in blockedUids) false
                else if (!followingOnly) true
                else post.ownerUid == myUid || post.ownerUid in acceptedFollowingUids
            }
            .sortedByDescending { it.createdAtMs }

        Result.success(sorted)
    }

    suspend fun fetchSinglePost(
        context: Context,
        postId: String,
        ownerUid: String = ""
    ): Result<SocialPostData?> = withContext(Dispatchers.IO) {
        if (!POST_ID_REGEX.matches(postId)) return@withContext Result.success(null)
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
        val (myUid, token) = creds
        val blockedUids = SocialProfileService.fetchAllBlockedUidsSync(token, myUid)

        if (ownerUid.matches(Regex("^[A-Za-z0-9_-]{1,128}$"))) {
            if (ownerUid in blockedUids && ownerUid != myUid) return@withContext Result.success(null)
            val (pCode, pBody) = execFs("GET", "$FS_BASE/public_profiles/${enc(ownerUid)}/private_posts/${enc(postId)}", token, null)
            if (pCode in 200..299) {
                val parsed = parsePostDoc(JSONObject(pBody), isPrivatePost = true)
                return@withContext Result.success(parsed?.takeIf { it.ownerUid !in blockedUids })
            }
        }
        val (pubCode, pubBody) = execFs("GET", "$FS_BASE/public_posts/${enc(postId)}", token, null)
        if (pubCode in 200..299) {
            val parsed = parsePostDoc(JSONObject(pubBody), isPrivatePost = false)
            return@withContext Result.success(parsed?.takeIf { it.ownerUid !in blockedUids })
        }
        Result.success(null)
    }

    suspend fun createPost(
        context: Context,
        myProfile: LocalProfileConfig,
        postKind: String,
        caption: String,
        mediaType: String, // "photo" | "video" | "text"
        photoDataUrls: List<String>,
        videoFile: File?,
        attachedItem: ClothingItem?
    ): Result<SocialPostData> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Sunucuya bağlanılamadı"))
        val (myUid, token) = creds
        val effProfile = SocialProfileService.loadLocalProfile(context).let { stored ->
            if (myProfile.username.isNotBlank() || myProfile.displayName.isNotBlank()) myProfile else stored
        }
        if (!SocialProfileService.isValidUsername(effProfile.username)) {
            return@withContext Result.failure(IllegalStateException("USERNAME_REQUIRED:Gönderi paylaşmak için önce bir kullanıcı adı oluşturmalısın"))
        }
        val isPrivateAccount = effProfile.isPrivate
        val (cleanUser, cleanDisplay, cleanAvatar) = cleanOwnerMeta(context, effProfile)
        val cleanCaption = caption.trim().take(800)
        val validKind = if (postKind in setOf("outfit", "new_piece", "look", "photo", "video", "question")) {
            postKind
        } else "outfit"
        val nowMs = safeNowMs()
        val postId = randomId("p_")

        val cleanPhotos = if (mediaType == "photo") {
            photoDataUrls.take(4).mapNotNull { raw ->
                compressImageDataUrl(raw, maxBytesChars = 210_000, maxSide = 960)
            }
        } else emptyList()

        if (mediaType == "photo" && cleanPhotos.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("En az 1 geçerli fotoğraf eklemelisin"))
        }
        if (mediaType == "text" && cleanCaption.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gönderi açıklaması boş olamaz"))
        }

        var posterBase64 = ""
        var chunkCount = 0
        var videoMime = ""
        var videoDurSec = 0

        if (mediaType == "video") {
            if (videoFile == null || !videoFile.exists() || videoFile.length() <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("Geçerli bir video seçilmedi"))
            }
            if (videoFile.length() > MAX_VIDEO_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("Video boyutu en fazla 7.5 MB olabilir"))
            }
            val meta = extractVideoPosterAndDuration(videoFile)
            posterBase64 = meta.first
            videoDurSec = meta.second.coerceIn(0, 60)
            videoMime = "video/mp4"

            val rawBytes = videoFile.readBytes()
            val chunks = ArrayList<ByteArray>()
            var pos = 0
            while (pos < rawBytes.size && chunks.size < MAX_VIDEO_CHUNKS) {
                val end = minOf(rawBytes.size, pos + VIDEO_CHUNK_BYTES)
                chunks.add(rawBytes.copyOfRange(pos, end))
                pos = end
            }
            if (pos < rawBytes.size || chunks.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Video parça limiti (16 parça) aşıldı"))
            }
            val baseCol = postCollectionPath(myUid, isPrivateAccount)
            for (i in chunks.indices) {
                val b64 = Base64.encodeToString(chunks[i], Base64.NO_WRAP)
                val cFields = JSONObject()
                    .put("ownerUid", strVal(myUid))
                    .put("index", intVal(i.toLong()))
                    .put("b64", strVal(b64))
                val (cCode, cBody) = execFs(
                    "PATCH",
                    "$FS_BASE/$baseCol/${enc(postId)}/media_chunks/$i",
                    token,
                    JSONObject().put("fields", cFields).toString()
                )
                if (cCode !in 200..299) {
                    return@withContext Result.failure(IllegalStateException(SocialProfileService.formatFsError(cCode, cBody, "Video parçası yüklenemedi")))
                }
            }
            chunkCount = chunks.size
            runCatching {
                val cacheDir = File(context.cacheDir, "social_videos").apply { mkdirs() }
                videoFile.copyTo(File(cacheDir, "$postId.mp4"), overwrite = true)
            }
        }

        val itemName = attachedItem?.let {
            it.name.ifBlank { it.subCategory.label }.take(120)
        }.orEmpty()
        val itemCat = attachedItem?.category?.name?.take(32).orEmpty()
        val itemPrice = attachedItem?.priceText?.take(40).orEmpty()
        val itemUrl = attachedItem?.productUrl?.take(1000).orEmpty()

        val fields = buildPostFields(
            postId = postId,
            ownerUid = myUid,
            ownerUsername = cleanUser,
            ownerDisplayName = cleanDisplay,
            ownerAvatar = cleanAvatar,
            postKind = validKind,
            caption = cleanCaption,
            mediaType = mediaType,
            photos = cleanPhotos,
            posterBase64 = posterBase64,
            chunkCount = chunkCount,
            videoMime = videoMime,
            videoDurationSec = videoDurSec,
            attachedItemName = itemName,
            attachedCategory = itemCat,
            attachedPriceText = itemPrice,
            attachedProductUrl = itemUrl,
            createdAtMs = nowMs,
            editedAtMs = 0L
        )
        val (pCode, pBody) = execFs(
            "PATCH",
            postDocUrl(myUid, postId, isPrivateAccount),
            token,
            JSONObject().put("fields", fields).toString()
        )
        if (pCode !in 200..299) {
            return@withContext Result.failure(IllegalStateException(SocialProfileService.formatFsError(pCode, pBody, "Gönderi paylaşılamadı")))
        }

        AnalyticsService.trackFeature("post_create", "$validKind/$mediaType")
        Result.success(
            SocialPostData(
                postId = postId,
                ownerUid = myUid,
                ownerUsername = cleanUser,
                ownerDisplayName = cleanDisplay,
                ownerAvatar = cleanAvatar,
                postKind = validKind,
                caption = cleanCaption,
                mediaType = mediaType,
                photos = cleanPhotos,
                posterBase64 = posterBase64,
                chunkCount = chunkCount,
                videoMime = videoMime,
                videoDurationSec = videoDurSec,
                attachedItemName = itemName,
                attachedCategory = itemCat,
                attachedPriceText = itemPrice,
                attachedProductUrl = itemUrl,
                createdAtMs = nowMs,
                editedAtMs = 0L,
                isPrivatePost = isPrivateAccount
            )
        )
    }

    suspend fun loadPostVideoFile(context: Context, post: SocialPostData): Result<File> =
        withContext(Dispatchers.IO) {
            if (post.chunkCount <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Video parçası yok"))
            }
            val cacheDir = File(context.cacheDir, "social_videos").apply { mkdirs() }
            val outFile = File(cacheDir, "${post.postId}.mp4")
            if (outFile.exists() && outFile.length() > 0L) {
                return@withContext Result.success(outFile)
            }
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (_, token) = creds
            val baseCol = postCollectionPath(post.ownerUid, post.isPrivatePost)

            val tmpFile = File(cacheDir, "${post.postId}.tmp")
            tmpFile.delete()
            FileOutputStream(tmpFile).use { fos ->
                for (i in 0 until post.chunkCount) {
                    val (code, body) = execFs(
                        "GET",
                        "$FS_BASE/$baseCol/${enc(post.postId)}/media_chunks/$i",
                        token,
                        null
                    )
                    if (code !in 200..299) {
                        tmpFile.delete()
                        return@withContext Result.failure(IllegalStateException("Video indirilemedi ($code)"))
                    }
                    val b64 = fsString(JSONObject(body).optJSONObject("fields"), "b64")
                    if (b64.isBlank()) {
                        tmpFile.delete()
                        return@withContext Result.failure(IllegalStateException("Video parçası bozuk"))
                    }
                    fos.write(Base64.decode(b64, Base64.DEFAULT))
                }
            }
            tmpFile.renameTo(outFile)
            Result.success(outFile)
        }

    suspend fun editPostCaption(
        context: Context,
        post: SocialPostData,
        newCaption: String
    ): Result<SocialPostData> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
        val (myUid, token) = creds
        if (post.ownerUid != myUid) {
            return@withContext Result.failure(SecurityException("Sadece kendi gönderini düzenleyebilirsin"))
        }
        val nowMs = safeNowMs()
        if (!post.canEditCaption(nowMs)) {
            return@withContext Result.failure(IllegalStateException("15 dakikalık düzenleme süresi doldu"))
        }
        val cleanCap = newCaption.trim().take(800)
        if (post.mediaType == "text" && cleanCap.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Yazı gönderisi boş bırakılamaz"))
        }

        val fields = buildPostFields(
            postId = post.postId,
            ownerUid = post.ownerUid,
            ownerUsername = post.ownerUsername,
            ownerDisplayName = post.ownerDisplayName,
            ownerAvatar = post.ownerAvatar,
            postKind = post.postKind,
            caption = cleanCap,
            mediaType = post.mediaType,
            photos = post.photos,
            posterBase64 = post.posterBase64,
            chunkCount = post.chunkCount,
            videoMime = post.videoMime,
            videoDurationSec = post.videoDurationSec,
            attachedItemName = post.attachedItemName,
            attachedCategory = post.attachedCategory,
            attachedPriceText = post.attachedPriceText,
            attachedProductUrl = post.attachedProductUrl,
            createdAtMs = post.createdAtMs,
            editedAtMs = nowMs
        )
        val (code, body) = execFs(
            "PATCH",
            postDocUrl(post.ownerUid, post.postId, post.isPrivatePost),
            token,
            JSONObject().put("fields", fields).toString()
        )
        if (code !in 200..299) {
            return@withContext Result.failure(
                IllegalStateException(
                    if (code == 403) "15 dakikalık düzenleme süresi dolmuş"
                    else SocialProfileService.formatFsError(code, body, "Düzenlenemedi")
                )
            )
        }
        AnalyticsService.trackFeature("post_edit")
        Result.success(post.copy(caption = cleanCap, editedAtMs = nowMs))
    }

    suspend fun deletePost(context: Context, post: SocialPostData): Result<Unit> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            if (post.ownerUid != myUid) {
                return@withContext Result.failure(SecurityException("Yetkisiz işlem"))
            }
            val baseCol = postCollectionPath(post.ownerUid, post.isPrivatePost)
            // Delete comments & video chunks cleanly first
            val comments = listCollectionPath(token, "$baseCol/${enc(post.postId)}/comments")
            comments.forEach { doc ->
                val cid = doc.optString("name", "").substringAfterLast('/')
                if (cid.isNotBlank()) execFs("DELETE", "$FS_BASE/$baseCol/${enc(post.postId)}/comments/${enc(cid)}", token, null)
            }
            for (i in 0 until post.chunkCount) {
                execFs("DELETE", "$FS_BASE/$baseCol/${enc(post.postId)}/media_chunks/$i", token, null)
            }
            val (code, _) = execFs("DELETE", postDocUrl(post.ownerUid, post.postId, post.isPrivatePost), token, null)
            if (code in 200..299 || code == 404) {
                AnalyticsService.trackFeature("post_delete")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Silinemedi ($code)"))
            }
        }

    /**
     * Migrates user's own posts when account visibility toggles between Public and Private,
     * so private account posts are immediately hidden from non-followers at the database rule level.
     */
    suspend fun migrateMyPostsForPrivacy(
        context: Context,
        myUid: String,
        nextIsPrivate: Boolean
    ) = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context) ?: return@withContext
        val (_, token) = creds
        val srcDocs = if (nextIsPrivate) {
            queryBySingleField(token, "public_posts", "ownerUid", myUid)
        } else {
            listCollectionPath(token, "public_profiles/${enc(myUid)}/private_posts")
        }
        srcDocs.forEach { doc ->
            val post = parsePostDoc(doc, isPrivatePost = !nextIsPrivate) ?: return@forEach
            val srcCol = postCollectionPath(myUid, isPrivatePost = !nextIsPrivate)
            val dstCol = postCollectionPath(myUid, isPrivatePost = nextIsPrivate)

            // Copy video chunks first
            for (i in 0 until post.chunkCount) {
                val (gc, gb) = execFs("GET", "$FS_BASE/$srcCol/${enc(post.postId)}/media_chunks/$i", token, null)
                if (gc in 200..299) {
                    val b64 = fsString(JSONObject(gb).optJSONObject("fields"), "b64")
                    if (b64.isNotBlank()) {
                        val cFields = JSONObject()
                            .put("ownerUid", strVal(myUid))
                            .put("index", intVal(i.toLong()))
                            .put("b64", strVal(b64))
                        execFs("PATCH", "$FS_BASE/$dstCol/${enc(post.postId)}/media_chunks/$i", token, JSONObject().put("fields", cFields).toString())
                    }
                }
            }
            // Write post in destination collection
            val fields = buildPostFields(
                postId = post.postId,
                ownerUid = post.ownerUid,
                ownerUsername = post.ownerUsername,
                ownerDisplayName = post.ownerDisplayName,
                ownerAvatar = post.ownerAvatar,
                postKind = post.postKind,
                caption = post.caption,
                mediaType = post.mediaType,
                photos = post.photos,
                posterBase64 = post.posterBase64,
                chunkCount = post.chunkCount,
                videoMime = post.videoMime,
                videoDurationSec = post.videoDurationSec,
                attachedItemName = post.attachedItemName,
                attachedCategory = post.attachedCategory,
                attachedPriceText = post.attachedPriceText,
                attachedProductUrl = post.attachedProductUrl,
                createdAtMs = post.createdAtMs,
                editedAtMs = post.editedAtMs
            )
            val (wc, _) = execFs("PATCH", "$FS_BASE/$dstCol/${enc(post.postId)}", token, JSONObject().put("fields", fields).toString())
            if (wc in 200..299) {
                for (i in 0 until post.chunkCount) {
                    execFs("DELETE", "$FS_BASE/$srcCol/${enc(post.postId)}/media_chunks/$i", token, null)
                }
                val srcComments = listCollectionPath(token, "$srcCol/${enc(post.postId)}/comments")
                srcComments.forEach { cDoc ->
                    val cid = cDoc.optString("name", "").substringAfterLast('/')
                    if (cid.isNotBlank()) execFs("DELETE", "$FS_BASE/$srcCol/${enc(post.postId)}/comments/${enc(cid)}", token, null)
                }
                execFs("DELETE", "$FS_BASE/$srcCol/${enc(post.postId)}", token, null)
            }
        }
    }

    suspend fun fetchPostLikeState(
        context: Context,
        postId: String
    ): Result<Pair<Int, Boolean>> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
        val (myUid, token) = creds
        val docs = queryBySingleField(token, "item_likes", "targetKey", "post_$postId")
        val uids = docs.mapNotNull { fsString(it.optJSONObject("fields"), "uid").takeIf { s -> s.isNotEmpty() } }
        Result.success(uids.size to (myUid in uids))
    }

    suspend fun togglePostLike(
        context: Context,
        myProfile: LocalProfileConfig,
        post: SocialPostData
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
        val (myUid, token) = creds
        val targetKey = "post_${post.postId}"
        val likeId = "${myUid}_$targetKey"
        val (gCode, _) = execFs("GET", "$FS_BASE/item_likes/${enc(likeId)}", token, null)
        if (gCode in 200..299) {
            val (dCode, _) = execFs("DELETE", "$FS_BASE/item_likes/${enc(likeId)}", token, null)
            return@withContext if (dCode in 200..299 || dCode == 404) Result.success(false)
            else Result.failure(IllegalStateException("Beğeni kaldırılamadı"))
        }
        val effMe = myProfile.takeIf { SocialProfileService.isValidUsername(it.username) } ?: SocialProfileService.loadLocalProfile(context)
        if (!SocialProfileService.isValidUsername(effMe.username)) {
            return@withContext Result.failure(IllegalStateException("USERNAME_REQUIRED:Gönderi beğenmek için önce bir kullanıcı adı oluşturmalısın"))
        }
        val fields = JSONObject()
            .put("uid", strVal(myUid))
            .put("targetKey", strVal(targetKey))
            .put("createdAtMs", intVal(safeNowMs()))
        val (pCode, _) = execFs("PATCH", "$FS_BASE/item_likes/${enc(likeId)}", token, JSONObject().put("fields", fields).toString())
        if (pCode !in 200..299) return@withContext Result.failure(IllegalStateException("Beğenilemedi ($pCode)"))

        AnalyticsService.trackFeature("post_like", post.postKind)
        if (post.ownerUid != myUid) {
            sendNotificationBestEffort(
                context = context,
                targetUid = post.ownerUid,
                type = "post_like",
                myProfile = myProfile,
                postId = post.postId,
                postOwnerUid = post.ownerUid,
                previewText = post.caption.ifBlank { "Gönderini beğendi" }.take(120)
            )
        }
        Result.success(true)
    }

    suspend fun fetchComments(context: Context, post: SocialPostData): Result<List<PostCommentData>> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            val blockedUids = SocialProfileService.fetchAllBlockedUidsSync(token, myUid)
            val baseCol = postCollectionPath(post.ownerUid, post.isPrivatePost)
            val docs = listCollectionPath(token, "$baseCol/${enc(post.postId)}/comments")
            val parsed = docs.mapNotNull(::parseCommentDoc)
                .filter { it.authorUid !in blockedUids }
                .sortedBy { it.createdAtMs }
            Result.success(parsed)
        }

    suspend fun addComment(
        context: Context,
        myProfile: LocalProfileConfig,
        post: SocialPostData,
        text: String,
        mediaType: String, // "none" | "image" | "gif"
        mediaBase64: String,
        mediaUrl: String
    ): Result<PostCommentData> = withContext(Dispatchers.IO) {
        val creds = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
        val (myUid, token) = creds
        val (cleanUser, cleanDisplay, cleanAvatar) = cleanOwnerMeta(context, myProfile)
        if (!SocialProfileService.isValidUsername(cleanUser)) {
            return@withContext Result.failure(IllegalStateException("USERNAME_REQUIRED:Yorum yapmak için önce bir kullanıcı adı oluşturmalısın"))
        }
        val cleanText = text.trim().take(500)
        val validMediaType = if (mediaType in setOf("none", "image", "gif")) mediaType else "none"
        val cleanB64 = when (validMediaType) {
            "image" -> compressImageDataUrl(mediaBase64, maxBytesChars = 240_000, maxSide = 720).orEmpty()
            "gif" -> if (AnimatedGifGenerator.isSafeGifDataUrl(mediaBase64, 255_000)) mediaBase64 else ""
            else -> ""
        }
        val cleanUrl = if (validMediaType == "gif" && cleanB64.isBlank() && AnimatedGifGenerator.isSafeExternalGifUrl(mediaUrl)) {
            mediaUrl.trim().take(600)
        } else ""

        if (cleanText.isEmpty() && cleanB64.isEmpty() && cleanUrl.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Yorum yazısı, fotoğraf veya GIF seçmelisin"))
        }

        val finalType = when {
            validMediaType == "gif" && (cleanB64.isNotEmpty() || cleanUrl.isNotEmpty()) -> "gif"
            validMediaType == "image" && cleanB64.isNotEmpty() -> "image"
            else -> "none"
        }

        val commentId = randomId("c_")
        val nowMs = safeNowMs()
        val fields = JSONObject()
            .put("commentId", strVal(commentId))
            .put("postId", strVal(post.postId))
            .put("authorUid", strVal(myUid))
            .put("authorUsername", strVal(cleanUser))
            .put("authorDisplayName", strVal(cleanDisplay))
            .put("authorAvatar", strVal(cleanAvatar))
            .put("text", strVal(cleanText))
            .put("mediaType", strVal(finalType))
            .put("mediaBase64", strVal(cleanB64))
            .put("mediaUrl", strVal(cleanUrl))
            .put("createdAtMs", intVal(nowMs))

        val baseCol = postCollectionPath(post.ownerUid, post.isPrivatePost)
        val (code, body) = execFs(
            "PATCH",
            "$FS_BASE/$baseCol/${enc(post.postId)}/comments/${enc(commentId)}",
            token,
            JSONObject().put("fields", fields).toString()
        )
        if (code !in 200..299) {
            return@withContext Result.failure(IllegalStateException(SocialProfileService.formatFsError(code, body, "Yorum gönderilemedi")))
        }

        AnalyticsService.trackFeature("post_comment", finalType)
        if (post.ownerUid != myUid) {
            val preview = cleanText.ifBlank { if (finalType == "gif") "[GIF gönderdi]" else "[Fotoğraf gönderdi]" }
            sendNotificationBestEffort(
                context = context,
                targetUid = post.ownerUid,
                type = "post_comment",
                myProfile = myProfile,
                postId = post.postId,
                postOwnerUid = post.ownerUid,
                previewText = preview.take(160)
            )
        }

        Result.success(
            PostCommentData(
                commentId = commentId,
                postId = post.postId,
                authorUid = myUid,
                authorUsername = cleanUser,
                authorDisplayName = cleanDisplay,
                authorAvatar = cleanAvatar,
                text = cleanText,
                mediaType = finalType,
                mediaBase64 = cleanB64,
                mediaUrl = cleanUrl,
                createdAtMs = nowMs
            )
        )
    }

    suspend fun deleteComment(context: Context, post: SocialPostData, commentId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (_, token) = creds
            val baseCol = postCollectionPath(post.ownerUid, post.isPrivatePost)
            val (code, _) = execFs("DELETE", "$FS_BASE/$baseCol/${enc(post.postId)}/comments/${enc(commentId)}", token, null)
            if (code in 200..299 || code == 404) Result.success(Unit)
            else Result.failure(IllegalStateException("Yorum silinemedi ($code)"))
        }

    suspend fun sendNotificationBestEffort(
        context: Context,
        targetUid: String,
        type: String,
        myProfile: LocalProfileConfig,
        postId: String = "",
        postOwnerUid: String = "",
        previewText: String = ""
    ) = withContext(Dispatchers.IO) {
        if (!targetUid.matches(Regex("^[A-Za-z0-9_-]{1,128}$"))) return@withContext
        val creds = AnalyticsService.getOrCreateAuthCredentials(context) ?: return@withContext
        val (myUid, token) = creds
        if (targetUid == myUid) return@withContext
        val (cleanUser, cleanDisplay, cleanAvatar) = cleanOwnerMeta(context, myProfile)
        val notifId = if (type == "follow_request") "fr_${myUid}" else randomId("n_")
        val nowMs = safeNowMs()
        val fields = JSONObject()
            .put("notifId", strVal(notifId))
            .put("targetUid", strVal(targetUid))
            .put("type", strVal(type))
            .put("fromUid", strVal(myUid))
            .put("fromUsername", strVal(cleanUser))
            .put("fromDisplayName", strVal(cleanDisplay))
            .put("fromAvatar", strVal(cleanAvatar))
            .put("postId", strVal(postId.take(48)))
            .put("postOwnerUid", strVal(postOwnerUid.take(128)))
            .put("previewText", strVal(previewText.take(160)))
            .put("read", boolVal(false))
            .put("createdAtMs", intVal(nowMs))
        execFs(
            "PATCH",
            "$FS_BASE/notifications/${enc(targetUid)}/items/${enc(notifId)}",
            token,
            JSONObject().put("fields", fields).toString()
        )
    }

    suspend fun fetchMyNotifications(context: Context): Result<List<SocialNotificationData>> =
        withContext(Dispatchers.IO) {
            val creds = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Bağlantı kurulamadı"))
            val (myUid, token) = creds
            val docs = listCollectionPath(token, "notifications/${enc(myUid)}/items")
            val parsed = docs.mapNotNull(::parseNotificationDoc).sortedByDescending { it.createdAtMs }
            Result.success(parsed)
        }

    suspend fun markNotificationRead(context: Context, notif: SocialNotificationData) =
        withContext(Dispatchers.IO) {
            if (notif.read) return@withContext
            val creds = AnalyticsService.getOrCreateAuthCredentials(context) ?: return@withContext
            val (myUid, token) = creds
            if (notif.targetUid != myUid) return@withContext
            val fields = buildNotifFields(notif.copy(read = true))
            execFs(
                "PATCH",
                "$FS_BASE/notifications/${enc(myUid)}/items/${enc(notif.notifId)}",
                token,
                JSONObject().put("fields", fields).toString()
            )
        }

    suspend fun markAllNotificationsRead(context: Context, list: List<SocialNotificationData>) =
        withContext(Dispatchers.IO) {
            list.filter { !it.read }.take(30).forEach { markNotificationRead(context, it) }
        }

    private fun buildPostFields(
        postId: String,
        ownerUid: String,
        ownerUsername: String,
        ownerDisplayName: String,
        ownerAvatar: String,
        postKind: String,
        caption: String,
        mediaType: String,
        photos: List<String>,
        posterBase64: String,
        chunkCount: Int,
        videoMime: String,
        videoDurationSec: Int,
        attachedItemName: String,
        attachedCategory: String,
        attachedPriceText: String,
        attachedProductUrl: String,
        createdAtMs: Long,
        editedAtMs: Long
    ): JSONObject = JSONObject()
        .put("postId", strVal(postId))
        .put("ownerUid", strVal(ownerUid))
        .put("ownerUsername", strVal(ownerUsername))
        .put("ownerDisplayName", strVal(ownerDisplayName))
        .put("ownerAvatar", strVal(ownerAvatar))
        .put("postKind", strVal(postKind))
        .put("caption", strVal(caption))
        .put("mediaType", strVal(mediaType))
        .put("photos", strArrVal(photos.take(4)))
        .put("posterBase64", strVal(posterBase64))
        .put("chunkCount", intVal(chunkCount.toLong()))
        .put("videoMime", strVal(videoMime))
        .put("videoDurationSec", intVal(videoDurationSec.toLong()))
        .put("attachedItemName", strVal(attachedItemName))
        .put("attachedCategory", strVal(attachedCategory))
        .put("attachedPriceText", strVal(attachedPriceText))
        .put("attachedProductUrl", strVal(attachedProductUrl))
        .put("createdAtMs", intVal(createdAtMs))
        .put("editedAtMs", intVal(editedAtMs))

    private fun buildNotifFields(n: SocialNotificationData): JSONObject = JSONObject()
        .put("notifId", strVal(n.notifId))
        .put("targetUid", strVal(n.targetUid))
        .put("type", strVal(n.type))
        .put("fromUid", strVal(n.fromUid))
        .put("fromUsername", strVal(n.fromUsername))
        .put("fromDisplayName", strVal(n.fromDisplayName))
        .put("fromAvatar", strVal(n.fromAvatar))
        .put("postId", strVal(n.postId))
        .put("postOwnerUid", strVal(n.postOwnerUid))
        .put("previewText", strVal(n.previewText))
        .put("read", boolVal(n.read))
        .put("createdAtMs", intVal(n.createdAtMs))

    private fun parsePostDoc(doc: JSONObject, isPrivatePost: Boolean): SocialPostData? {
        val f = doc.optJSONObject("fields") ?: return null
        val postId = fsString(f, "postId").ifBlank { doc.optString("name", "").substringAfterLast('/') }
        val ownerUid = fsString(f, "ownerUid")
        if (postId.isBlank() || ownerUid.isBlank()) return null
        return SocialPostData(
            postId = postId,
            ownerUid = ownerUid,
            ownerUsername = fsString(f, "ownerUsername"),
            ownerDisplayName = fsString(f, "ownerDisplayName"),
            ownerAvatar = fsString(f, "ownerAvatar"),
            postKind = fsString(f, "postKind").ifBlank { "outfit" },
            caption = fsString(f, "caption"),
            mediaType = fsString(f, "mediaType").ifBlank { "text" },
            photos = fsStringList(f, "photos"),
            posterBase64 = fsString(f, "posterBase64"),
            chunkCount = fsLong(f, "chunkCount").toInt(),
            videoMime = fsString(f, "videoMime"),
            videoDurationSec = fsLong(f, "videoDurationSec").toInt(),
            attachedItemName = fsString(f, "attachedItemName"),
            attachedCategory = fsString(f, "attachedCategory"),
            attachedPriceText = fsString(f, "attachedPriceText"),
            attachedProductUrl = fsString(f, "attachedProductUrl"),
            createdAtMs = fsLong(f, "createdAtMs"),
            editedAtMs = fsLong(f, "editedAtMs"),
            isPrivatePost = isPrivatePost
        )
    }

    private fun parseCommentDoc(doc: JSONObject): PostCommentData? {
        val f = doc.optJSONObject("fields") ?: return null
        val cid = fsString(f, "commentId").ifBlank { doc.optString("name", "").substringAfterLast('/') }
        val pid = fsString(f, "postId")
        val auid = fsString(f, "authorUid")
        if (cid.isBlank() || pid.isBlank() || auid.isBlank()) return null
        return PostCommentData(
            commentId = cid,
            postId = pid,
            authorUid = auid,
            authorUsername = fsString(f, "authorUsername"),
            authorDisplayName = fsString(f, "authorDisplayName"),
            authorAvatar = fsString(f, "authorAvatar"),
            text = fsString(f, "text"),
            mediaType = fsString(f, "mediaType").ifBlank { "none" },
            mediaBase64 = fsString(f, "mediaBase64"),
            mediaUrl = fsString(f, "mediaUrl"),
            createdAtMs = fsLong(f, "createdAtMs")
        )
    }

    private fun parseNotificationDoc(doc: JSONObject): SocialNotificationData? {
        val f = doc.optJSONObject("fields") ?: return null
        val nid = fsString(f, "notifId").ifBlank { doc.optString("name", "").substringAfterLast('/') }
        val targetUid = fsString(f, "targetUid")
        val fromUid = fsString(f, "fromUid")
        if (nid.isBlank() || targetUid.isBlank() || fromUid.isBlank()) return null
        return SocialNotificationData(
            notifId = nid,
            targetUid = targetUid,
            type = fsString(f, "type"),
            fromUid = fromUid,
            fromUsername = fsString(f, "fromUsername"),
            fromDisplayName = fsString(f, "fromDisplayName"),
            fromAvatar = fsString(f, "fromAvatar"),
            postId = fsString(f, "postId"),
            postOwnerUid = fsString(f, "postOwnerUid"),
            previewText = fsString(f, "previewText"),
            read = fsBool(f, "read", false),
            createdAtMs = fsLong(f, "createdAtMs")
        )
    }

    private fun compressImageDataUrl(dataUrl: String, maxBytesChars: Int, maxSide: Int): String? {
        if (!dataUrl.startsWith("data:image/")) return null
        if (dataUrl.startsWith("data:image/gif;") && dataUrl.length <= maxBytesChars) return dataUrl
        if (dataUrl.length <= maxBytesChars) return dataUrl
        val comma = dataUrl.indexOf(',')
        if (comma <= 0) return null
        val bytes = runCatching { Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT) }.getOrNull() ?: return null
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val longest = maxOf(bmp.width, bmp.height).coerceAtLeast(1)
        if (longest > maxSide) {
            val sc = maxSide.toFloat() / longest
            val next = Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt().coerceAtLeast(64), (bmp.height * sc).toInt().coerceAtLeast(64), true)
            if (next !== bmp && !bmp.isRecycled) bmp.recycle()
            bmp = next
        }
        for (q in intArrayOf(82, 68, 52, 38)) {
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, baos)
            val cand = "data:image/jpeg;base64,${Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)}"
            if (cand.length <= maxBytesChars) {
                if (!bmp.isRecycled) bmp.recycle()
                return cand
            }
        }
        val smaller = Bitmap.createScaledBitmap(bmp, (bmp.width * 0.65f).toInt().coerceAtLeast(64), (bmp.height * 0.65f).toInt().coerceAtLeast(64), true)
        if (!bmp.isRecycled) bmp.recycle()
        val baos = ByteArrayOutputStream()
        smaller.compress(Bitmap.CompressFormat.JPEG, 42, baos)
        smaller.recycle()
        return "data:image/jpeg;base64,${Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)}".take(maxBytesChars)
    }

    private fun extractVideoPosterAndDuration(videoFile: File): Pair<String, Int> {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(videoFile.absolutePath)
            val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val durSec = ((durMs + 500L) / 1000L).toInt().coerceIn(0, 60)
            val frame = mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val poster = if (frame != null) {
                val maxSide = 640
                val longest = maxOf(frame.width, frame.height).coerceAtLeast(1)
                val scaled = if (longest > maxSide) {
                    val sc = maxSide.toFloat() / longest
                    Bitmap.createScaledBitmap(frame, (frame.width * sc).toInt().coerceAtLeast(64), (frame.height * sc).toInt().coerceAtLeast(64), true).also {
                        if (it !== frame && !frame.isRecycled) frame.recycle()
                    }
                } else frame
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 72, baos)
                if (!scaled.isRecycled) scaled.recycle()
                "data:image/jpeg;base64,${Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)}".take(150_000)
            } else ""
            poster to durSec
        } catch (_: Throwable) {
            "" to 0
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun listCollectionPath(token: String, path: String): List<JSONObject> {
        val (code, body) = execFs("GET", "$FS_BASE/$path?pageSize=120", token, null)
        if (code !in 200..299) return emptyList()
        val arr = runCatching { JSONObject(body).optJSONArray("documents") }.getOrNull() ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(it) }
        }
    }

    private fun queryBySingleField(token: String, collection: String, field: String, value: String): List<JSONObject> {
        val payload = JSONObject().put(
            "structuredQuery",
            JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", collection)))
                .put(
                    "where",
                    JSONObject().put(
                        "fieldFilter",
                        JSONObject()
                            .put("field", JSONObject().put("fieldPath", field))
                            .put("op", "EQUAL")
                            .put("value", strVal(value))
                    )
                )
        )
        val (code, body) = execFs("POST", "$FS_BASE:runQuery", token, payload.toString())
        if (code !in 200..299) return emptyList()
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optJSONObject("document")?.let { add(it) }
            }
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun execFs(method: String, url: String, bearer: String, jsonBody: String?): Pair<Int, String> {
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return 0 to ""
        if (!uri.scheme.equals("https", ignoreCase = true) || host !in ALLOWED_HOSTS) return 0 to ""
        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpsURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 15_000
                useCaches = false
                setRequestProperty("Authorization", "Bearer $bearer")
                if (method == "PATCH") {
                    requestMethod = "POST"
                    setRequestProperty("X-HTTP-Method-Override", "PATCH")
                } else {
                    requestMethod = method
                }
                if (jsonBody != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { it.write(jsonBody) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
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

    private fun fsLong(fields: JSONObject?, key: String): Long =
        fields?.optJSONObject(key)?.optString("integerValue", "")?.toLongOrNull() ?: 0L

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
