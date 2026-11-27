package com.dresskit.wardrobe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.dresskit.wardrobe.model.DmChatActivityState
import com.dresskit.wardrobe.model.DmConversation
import com.dresskit.wardrobe.model.DmGlobalPresence
import com.dresskit.wardrobe.model.DmMessage
import com.dresskit.wardrobe.model.DmThreadSummary
import com.dresskit.wardrobe.model.LocalProfileConfig
import com.dresskit.wardrobe.model.PublicProfileHeader
import com.dresskit.wardrobe.model.SocialPostData
import com.dresskit.wardrobe.model.pairConvId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import kotlin.math.roundToInt

/**
 * Instagram-style Direct Messaging backend over Firestore REST API:
 * - Mutual / follower-accepted conversations go directly to Messages (`accepted`)
 * - Non-followers start in Message Requests (`request`) limited to 1 message (`req_first`) until accepted
 * - Supports text, photo, chunked video (<=7.5 MB), AAC voice notes, GIFs, post forwards & post replies
 * - Live global presence (`dm_presence`), per-chat activity (`typing`, `capturing_photo`, `uploading_photo`, `recording_audio`),
 *   read receipts (`İletildi`/`Görüldü`), 15-min caption/text edit, soft-delete, local per-conversation mute, and block guard.
 */
object DirectMessageService {

    private const val FS_BASE =
        "https://firestore.googleapis.com/v1/projects/dolabim-stats/databases/(default)/documents"
    private const val DM_PREFS = "dolabim_dm_prefs"
    private const val KEY_MUTED_UIDS = "muted_partner_uids_v1"
    private const val CHUNK_SIZE_CHARS = 480_000
    private const val MAX_CHUNKS = 16
    private const val MAX_VIDEO_BYTES = 7_500_000
    private val rng = SecureRandom()

    fun isConversationMuted(context: Context, partnerUid: String): Boolean {
        if (partnerUid.isBlank()) return false
        val prefs = context.getSharedPreferences(DM_PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_MUTED_UIDS, emptySet()) ?: emptySet()
        return partnerUid in set
    }

    fun setConversationMuted(context: Context, partnerUid: String, muted: Boolean) {
        if (partnerUid.isBlank()) return
        val prefs = context.getSharedPreferences(DM_PREFS, Context.MODE_PRIVATE)
        val next = (prefs.getStringSet(KEY_MUTED_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        if (muted) next.add(partnerUid) else next.remove(partnerUid)
        prefs.edit().putStringSet(KEY_MUTED_UIDS, next).apply()
    }

    private fun randomSuffix(): String {
        val b = ByteArray(4)
        rng.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun encPath(path: String): String =
        path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun execFs(
        method: String,
        url: String,
        token: String,
        body: JSONObject? = null
    ): Pair<Int, String> {
        return try {
            val actualMethod = if (method == "PATCH") "POST" else method
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = actualMethod
                connectTimeout = 15_000
                readTimeout = 22_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                if (method == "PATCH") {
                    setRequestProperty("X-HTTP-Method-Override", "PATCH")
                }
                if (body != null) doOutput = true
            }
            try {
                if (body != null) {
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                code to text
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            0 to ""
        }
    }

    private fun fsStr(v: String) = JSONObject().put("stringValue", v)
    private fun fsLong(v: Long) = JSONObject().put("integerValue", v.toString())
    private fun fsBool(v: Boolean) = JSONObject().put("booleanValue", v)
    private fun fsStrArr(list: List<String>) = JSONObject().put(
        "arrayValue",
        JSONObject().put("values", JSONArray().apply { list.forEach { put(fsStr(it)) } })
    )

    private fun strVal(fields: JSONObject?, k: String): String =
        fields?.optJSONObject(k)?.optString("stringValue", "").orEmpty()

    private fun intVal(fields: JSONObject?, k: String): Long =
        fields?.optJSONObject(k)?.optString("integerValue", "0")?.toLongOrNull() ?: 0L

    private fun boolVal(fields: JSONObject?, k: String): Boolean =
        fields?.optJSONObject(k)?.optBoolean("booleanValue", false) ?: false

    private fun strArrVal(fields: JSONObject?, k: String): List<String> {
        val arr = fields?.optJSONObject(k)?.optJSONObject("arrayValue")?.optJSONArray("values") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i)?.optString("stringValue", "").orEmpty()
                if (s.isNotBlank()) add(s)
            }
        }
    }

    private fun queryBySingleField(
        token: String,
        collectionId: String,
        fieldName: String,
        op: String,
        valueObj: JSONObject,
        limit: Int = 60,
        strict: Boolean = false
    ): List<JSONObject> {
        val q = JSONObject().put(
            "structuredQuery",
            JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", collectionId)))
                .put(
                    "where",
                    JSONObject().put(
                        "fieldFilter",
                        JSONObject()
                            .put("field", JSONObject().put("fieldPath", fieldName))
                            .put("op", op)
                            .put("value", valueObj)
                    )
                )
                .put("limit", limit)
        )
        val (code, text) = execFs("POST", "$FS_BASE:runQuery", token, q)
        val arr = socialQueryRows(code, text, strict)
        return buildList {
            for (i in 0 until arr.length()) {
                val doc = arr.optJSONObject(i)?.optJSONObject("document") ?: continue
                add(doc)
            }
        }
    }

    private fun listSubcollection(
        token: String,
        path: String,
        pageSize: Int
    ): List<JSONObject> {
        val url = "$FS_BASE/${encPath(path)}?pageSize=$pageSize"
        val (code, text) = execFs("GET", url, token)
        check(code in 200..299) { "Sohbet verisi yüklenemedi ($code)" }
        val arr = JSONObject(text).optJSONArray("documents") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(it) }
            }
        }
    }

    private fun parseConvDoc(doc: JSONObject): DmConversation {
        val f = doc.optJSONObject("fields")
        val idFromPath = doc.optString("name", "").substringAfterLast('/')
        return DmConversation(
            convId = strVal(f, "convId").ifBlank { idFromPath },
            participantUids = strArrVal(f, "participantUids"),
            uid1 = strVal(f, "uid1"),
            uid2 = strVal(f, "uid2"),
            status = strVal(f, "status").ifBlank { "accepted" },
            requesterUid = strVal(f, "requesterUid"),
            recipientUid = strVal(f, "recipientUid"),
            requestMessageCount = intVal(f, "requestMessageCount").toInt(),
            lastMessageText = strVal(f, "lastMessageText"),
            lastMessageType = strVal(f, "lastMessageType").ifBlank { "none" },
            lastMessageSenderUid = strVal(f, "lastMessageSenderUid"),
            lastMessageAtMs = intVal(f, "lastMessageAtMs"),
            uid1LastReadAtMs = intVal(f, "uid1LastReadAtMs"),
            uid2LastReadAtMs = intVal(f, "uid2LastReadAtMs"),
            createdAtMs = intVal(f, "createdAtMs"),
            updatedAtMs = intVal(f, "updatedAtMs")
        )
    }

    private fun convToFields(c: DmConversation): JSONObject = JSONObject()
        .put("convId", fsStr(c.convId))
        .put("participantUids", fsStrArr(c.participantUids))
        .put("uid1", fsStr(c.uid1))
        .put("uid2", fsStr(c.uid2))
        .put("status", fsStr(c.status))
        .put("requesterUid", fsStr(c.requesterUid))
        .put("recipientUid", fsStr(c.recipientUid))
        .put("requestMessageCount", fsLong(c.requestMessageCount.toLong()))
        .put("lastMessageText", fsStr(c.lastMessageText.take(200)))
        .put("lastMessageType", fsStr(c.lastMessageType))
        .put("lastMessageSenderUid", fsStr(c.lastMessageSenderUid))
        .put("lastMessageAtMs", fsLong(c.lastMessageAtMs))
        .put("uid1LastReadAtMs", fsLong(c.uid1LastReadAtMs))
        .put("uid2LastReadAtMs", fsLong(c.uid2LastReadAtMs))
        .put("createdAtMs", fsLong(c.createdAtMs))
        .put("updatedAtMs", fsLong(c.updatedAtMs))

    private fun parseMessageDoc(doc: JSONObject): DmMessage {
        val f = doc.optJSONObject("fields")
        val idFromPath = doc.optString("name", "").substringAfterLast('/')
        return DmMessage(
            msgId = strVal(f, "msgId").ifBlank { idFromPath },
            convId = strVal(f, "convId"),
            senderUid = strVal(f, "senderUid"),
            senderUsername = strVal(f, "senderUsername"),
            senderDisplayName = strVal(f, "senderDisplayName"),
            senderAvatar = strVal(f, "senderAvatar"),
            msgType = strVal(f, "msgType").ifBlank { "text" },
            text = strVal(f, "text"),
            mediaBase64 = strVal(f, "mediaBase64"),
            mediaUrl = strVal(f, "mediaUrl"),
            mediaMime = strVal(f, "mediaMime"),
            mediaDurationSec = intVal(f, "mediaDurationSec").toInt(),
            chunkCount = intVal(f, "chunkCount").toInt(),
            sharedPostId = strVal(f, "sharedPostId"),
            sharedPostOwnerUid = strVal(f, "sharedPostOwnerUid"),
            sharedPostOwnerUsername = strVal(f, "sharedPostOwnerUsername"),
            sharedPostCaption = strVal(f, "sharedPostCaption"),
            sharedPostThumb = strVal(f, "sharedPostThumb"),
            isDeleted = boolVal(f, "isDeleted"),
            createdAtMs = intVal(f, "createdAtMs"),
            editedAtMs = intVal(f, "editedAtMs")
        )
    }

    private fun messageToFields(m: DmMessage): JSONObject = JSONObject()
        .put("msgId", fsStr(m.msgId))
        .put("convId", fsStr(m.convId))
        .put("senderUid", fsStr(m.senderUid))
        .put("senderUsername", fsStr(m.senderUsername.take(24)))
        .put("senderDisplayName", fsStr(m.senderDisplayName.take(50)))
        .put("senderAvatar", fsStr(m.senderAvatar.take(40_000)))
        .put("msgType", fsStr(m.msgType))
        .put("text", fsStr(m.text.take(1000)))
        .put("mediaBase64", fsStr(m.mediaBase64.take(320_000)))
        .put("mediaUrl", fsStr(m.mediaUrl.take(600)))
        .put("mediaMime", fsStr(m.mediaMime.take(64)))
        .put("mediaDurationSec", fsLong(m.mediaDurationSec.coerceIn(0, 120).toLong()))
        .put("chunkCount", fsLong(m.chunkCount.coerceIn(0, MAX_CHUNKS).toLong()))
        .put("sharedPostId", fsStr(m.sharedPostId.take(48)))
        .put("sharedPostOwnerUid", fsStr(m.sharedPostOwnerUid.take(128)))
        .put("sharedPostOwnerUsername", fsStr(m.sharedPostOwnerUsername.take(24)))
        .put("sharedPostCaption", fsStr(m.sharedPostCaption.take(220)))
        .put("sharedPostThumb", fsStr(m.sharedPostThumb.take(140_000)))
        .put("isDeleted", fsBool(m.isDeleted))
        .put("createdAtMs", fsLong(m.createdAtMs))
        .put("editedAtMs", fsLong(m.editedAtMs))

    private fun fetchProfileHeaderSync(token: String, uid: String): PublicProfileHeader? {
        val (code, text) = execFs("GET", "$FS_BASE/${encPath("public_profiles/$uid")}", token)
        if (code !in 200..299 || text.isBlank()) return null
        val f = runCatching { JSONObject(text).optJSONObject("fields") }.getOrNull() ?: return null
        val un = strVal(f, "username")
        return PublicProfileHeader(
            uid = strVal(f, "uid").ifBlank { uid },
            username = un,
            usernameLower = strVal(f, "usernameLower").ifBlank { SocialProfileService.normalizeUsername(un) },
            displayName = strVal(f, "displayName"),
            bio = strVal(f, "bio"),
            avatarBase64 = strVal(f, "avatarThumbBase64").ifBlank { strVal(f, "avatarBase64") },
            styleTags = strArrVal(f, "styleTags"),
            isPrivate = boolVal(f, "isPrivate"),
            updatedAtMs = intVal(f, "updatedAtMs")
        )
    }

    private fun fetchBlockedSetsSync(token: String, myUid: String, strict: Boolean = false): Pair<Set<String>, Set<String>> {
        val byMe = queryBySingleField(token, "blocks", "blockerUid", "EQUAL", fsStr(myUid), 80, strict)
            .mapNotNull { strVal(it.optJSONObject("fields"), "blockedUid").ifBlank { null } }
            .toSet()
        val againstMe = queryBySingleField(token, "blocks", "blockedUid", "EQUAL", fsStr(myUid), 80, strict)
            .mapNotNull { strVal(it.optJSONObject("fields"), "blockerUid").ifBlank { null } }
            .toSet()
        return byMe to againstMe
    }

    private fun fetchFollowAcceptedEitherSync(token: String, uidA: String, uidB: String): Boolean {
        val candidates = listOf("${uidA}_${uidB}", "${uidB}_${uidA}")
        candidates.forEach { fid ->
            val (code, text) = execFs("GET", "$FS_BASE/${encPath("follows/$fid")}", token)
            if (code in 200..299 && text.isNotBlank()) {
                val f = runCatching { JSONObject(text).optJSONObject("fields") }.getOrNull()
                if (strVal(f, "status") == "accepted") return true
            }
        }
        return false
    }

    private fun fetchGlobalPresenceSync(token: String, uid: String): DmGlobalPresence {
        val (code, text) = execFs("GET", "$FS_BASE/${encPath("dm_presence/$uid")}", token)
        if (code !in 200..299 || text.isBlank()) return DmGlobalPresence(uid = uid)
        val f = runCatching { JSONObject(text).optJSONObject("fields") }.getOrNull() ?: return DmGlobalPresence(uid = uid)
        return DmGlobalPresence(
            uid = strVal(f, "uid").ifBlank { uid },
            isOnline = boolVal(f, "isOnline"),
            lastOnlineAtMs = intVal(f, "lastOnlineAtMs")
        )
    }

    suspend fun setMyOnlinePresence(context: Context, isOnline: Boolean) = withContext(Dispatchers.IO) {
        runCatching {
            val (uid, token) = AnalyticsService.getOrCreateAuthCredentials(context) ?: return@runCatching
            val now = System.currentTimeMillis()
            val payload = JSONObject().put(
                "fields",
                JSONObject()
                    .put("uid", fsStr(uid))
                    .put("isOnline", fsBool(isOnline))
                    .put("lastOnlineAtMs", fsLong(now))
            )
            execFs("PATCH", "$FS_BASE/${encPath("dm_presence/$uid")}", token, payload)
        }
        Unit
    }

    suspend fun fetchUserPresence(context: Context, uid: String): DmGlobalPresence = withContext(Dispatchers.IO) {
        runCatching {
            val (_, token) = AnalyticsService.getOrCreateAuthCredentials(context) ?: return@runCatching DmGlobalPresence(uid = uid)
            fetchGlobalPresenceSync(token, uid)
        }.getOrDefault(DmGlobalPresence(uid = uid))
    }

    suspend fun setChatActivityState(context: Context, convId: String, state: DmChatActivityState) = withContext(Dispatchers.IO) {
        runCatching {
            if (convId.isBlank()) return@runCatching
            val (uid, token) = AnalyticsService.getOrCreateAuthCredentials(context) ?: return@runCatching
            val payload = JSONObject().put(
                "fields",
                JSONObject()
                    .put("uid", fsStr(uid))
                    .put("state", fsStr(state.key))
                    .put("updatedAtMs", fsLong(System.currentTimeMillis()))
            )
            execFs("PATCH", "$FS_BASE/${encPath("dm_conversations/$convId/presence/$uid")}", token, payload)
        }
        Unit
    }

    suspend fun fetchPartnerActivityState(context: Context, convId: String, partnerUid: String): DmChatActivityState = withContext(Dispatchers.IO) {
        runCatching {
            if (convId.isBlank() || partnerUid.isBlank()) return@runCatching DmChatActivityState.IDLE
            val (_, token) = AnalyticsService.getOrCreateAuthCredentials(context) ?: return@runCatching DmChatActivityState.IDLE
            val (code, text) = execFs("GET", "$FS_BASE/${encPath("dm_conversations/$convId/presence/$partnerUid")}", token)
            if (code !in 200..299 || text.isBlank()) return@runCatching DmChatActivityState.IDLE
            val f = JSONObject(text).optJSONObject("fields") ?: return@runCatching DmChatActivityState.IDLE
            val updated = intVal(f, "updatedAtMs")
            if (System.currentTimeMillis() - updated > 8_500L) return@runCatching DmChatActivityState.IDLE
            DmChatActivityState.fromKey(strVal(f, "state"))
        }.getOrDefault(DmChatActivityState.IDLE)
    }

    suspend fun fetchMyConversations(context: Context): Result<List<DmThreadSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val (myUid, token) = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: return@withContext Result.failure(IllegalStateException("Sunucuya bağlanılamadı"))
            val docs = queryBySingleField(token, "dm_conversations", "participantUids", "ARRAY_CONTAINS", fsStr(myUid), 60, strict = true)
            val (byMe, againstMe) = fetchBlockedSetsSync(token, myUid, strict = true)
            val out = docs.mapNotNull { doc ->
                val conv = parseConvDoc(doc)
                if (conv.convId.isBlank()) return@mapNotNull null
                if (conv.status == "declined" && conv.recipientUid == myUid) return@mapNotNull null
                val partnerUid = conv.partnerUid(myUid)
                if (partnerUid.isBlank()) return@mapNotNull null
                val partner = fetchProfileHeaderSync(token, partnerUid)
                    ?: PublicProfileHeader(
                        uid = partnerUid,
                        username = "",
                        usernameLower = "",
                        displayName = "Kullanıcı",
                        bio = "",
                        avatarBase64 = "",
                        styleTags = emptyList(),
                        isPrivate = false,
                        updatedAtMs = 0L
                    )
                val presence = fetchGlobalPresenceSync(token, partnerUid)
                val muted = isConversationMuted(context, partnerUid)
                val unread = conv.hasUnreadFromPartner(myUid) && !muted
                DmThreadSummary(
                    conversation = conv,
                    partner = partner,
                    partnerPresence = presence,
                    isBlockedByMe = partnerUid in byMe,
                    hasBlockedMe = partnerUid in againstMe,
                    isMuted = muted,
                    isUnread = unread
                )
            }.sortedByDescending { it.conversation.updatedAtMs }
            out
        }
    }

    suspend fun countUnreadConversationsResult(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val (myUid, token) = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: throw IllegalStateException("Bağlantı yok")
            val docs = queryBySingleField(
                token = token,
                collectionId = "dm_conversations",
                fieldName = "participantUids",
                op = "ARRAY_CONTAINS",
                valueObj = fsStr(myUid),
                limit = 50,
                strict = true
            )
            docs.count { doc ->
                val c = parseConvDoc(doc)
                val partnerUid = c.partnerUid(myUid)
                !(c.status == "declined" && c.recipientUid == myUid) &&
                    !isConversationMuted(context, partnerUid) &&
                    c.hasUnreadFromPartner(myUid)
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { err ->
                if (err is CancellationException) throw err
                Result.failure(err)
            }
        )
    }

    suspend fun countUnreadConversations(context: Context): Int =
        countUnreadConversationsResult(context).getOrDefault(0)

    suspend fun getOrCreateConversation(
        context: Context,
        partnerUid: String
    ): Result<DmThreadSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val local = SocialProfileService.loadLocalProfile(context)
            if (!SocialProfileService.isValidUsername(local.username)) {
                throw IllegalStateException("USERNAME_REQUIRED:Sohbet başlatmak için önce kullanıcı adı oluşturmalısın.")
            }
            val (myUid, token) = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: throw IllegalStateException("Sunucuya bağlanılamadı")
            if (partnerUid.isBlank() || partnerUid == myUid) {
                throw IllegalArgumentException("Geçersiz kullanıcı")
            }
            val (byMe, againstMe) = fetchBlockedSetsSync(token, myUid)
            val isBlockedByMe = partnerUid in byMe
            val hasBlockedMe = partnerUid in againstMe

            val convId = pairConvId(myUid, partnerUid)
            val (getCode, getText) = execFs("GET", "$FS_BASE/${encPath("dm_conversations/$convId")}", token)
            val existingConv = if (getCode in 200..299 && getText.isNotBlank()) {
                runCatching { parseConvDoc(JSONObject(getText)) }.getOrNull()
            } else null

            val fetchedPartner = fetchProfileHeaderSync(token, partnerUid)
            val partner = fetchedPartner ?: if (existingConv != null) {
                PublicProfileHeader(
                    uid = partnerUid,
                    username = "",
                    usernameLower = "",
                    displayName = "Kullanıcı",
                    bio = "",
                    avatarBase64 = "",
                    styleTags = emptyList(),
                    isPrivate = false,
                    updatedAtMs = 0L
                )
            } else {
                throw IllegalStateException("Kullanıcı profili bulunamadı")
            }

            if (existingConv == null && !SocialProfileService.isValidUsername(partner.username)) {
                throw IllegalStateException("Bu kullanıcının henüz kullanıcı adı yok.")
            }

            val conv: DmConversation = if (existingConv != null) {
                existingConv
            } else {
                if (isBlockedByMe || hasBlockedMe) {
                    throw IllegalStateException("Engellenen bir hesapla sohbet başlatılamaz.")
                }
                val uid1 = if (myUid < partnerUid) myUid else partnerUid
                val uid2 = if (myUid < partnerUid) partnerUid else myUid
                val isFollowerLink = fetchFollowAcceptedEitherSync(token, myUid, partnerUid)
                val now = System.currentTimeMillis()
                val next = DmConversation(
                    convId = convId,
                    participantUids = listOf(uid1, uid2),
                    uid1 = uid1,
                    uid2 = uid2,
                    status = if (isFollowerLink) "accepted" else "request",
                    requesterUid = myUid,
                    recipientUid = partnerUid,
                    requestMessageCount = 0,
                    lastMessageText = "",
                    lastMessageType = "none",
                    lastMessageSenderUid = "",
                    lastMessageAtMs = 0L,
                    uid1LastReadAtMs = if (uid1 == myUid) now else 0L,
                    uid2LastReadAtMs = if (uid2 == myUid) now else 0L,
                    createdAtMs = now,
                    updatedAtMs = now
                )
                val (cCode, cText) = execFs(
                    "PATCH",
                    "$FS_BASE/${encPath("dm_conversations/$convId")}?currentDocument.exists=false",
                    token,
                    JSONObject().put("fields", convToFields(next))
                )
                if (cCode !in 200..299) {
                    val (rCode, rText) = execFs("GET", "$FS_BASE/${encPath("dm_conversations/$convId")}", token)
                    if (rCode in 200..299 && rText.isNotBlank()) {
                        parseConvDoc(JSONObject(rText))
                    } else {
                        throw IllegalStateException("Sohbet oluşturulamadı ($cCode): ${cText.take(120)}")
                    }
                } else next
            }

            val presence = fetchGlobalPresenceSync(token, partnerUid)
            val muted = isConversationMuted(context, partnerUid)
            DmThreadSummary(
                conversation = conv,
                partner = partner,
                partnerPresence = presence,
                isBlockedByMe = isBlockedByMe,
                hasBlockedMe = hasBlockedMe,
                isMuted = muted,
                isUnread = conv.hasUnreadFromPartner(myUid) && !muted
            )
        }
    }

    suspend fun acceptMessageRequest(context: Context, conv: DmConversation): Result<DmConversation> =
        updateRequestStatus(context, conv, "accepted")

    suspend fun declineMessageRequest(context: Context, conv: DmConversation): Result<DmConversation> =
        updateRequestStatus(context, conv, "declined")

    private suspend fun updateRequestStatus(
        context: Context,
        conv: DmConversation,
        nextStatus: String
    ): Result<DmConversation> = withContext(Dispatchers.IO) {
        runCatching {
            val (myUid, token) = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: throw IllegalStateException("Bağlantı yok")
            if (conv.recipientUid != myUid) {
                throw IllegalStateException("Bu isteği yalnızca alıcı yanıtlayabilir")
            }
            val now = maxOf(System.currentTimeMillis(), conv.createdAtMs)
            val updated = conv.copy(
                status = nextStatus,
                uid1LastReadAtMs = if (conv.uid1 == myUid) maxOf(conv.uid1LastReadAtMs, now) else conv.uid1LastReadAtMs,
                uid2LastReadAtMs = if (conv.uid2 == myUid) maxOf(conv.uid2LastReadAtMs, now) else conv.uid2LastReadAtMs,
                updatedAtMs = now
            )
            val (code, text) = execFs(
                "PATCH",
                "$FS_BASE/${encPath("dm_conversations/${conv.convId}")}",
                token,
                JSONObject().put("fields", convToFields(updated))
            )
            if (code !in 200..299) throw IllegalStateException("Durum güncellenemedi ($code): ${text.take(120)}")
            updated
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { err ->
                if (err is CancellationException) throw err
                Result.failure(err)
            }
        )
    }

    suspend fun markConversationRead(context: Context, conv: DmConversation): DmConversation? = withContext(Dispatchers.IO) {
        runCatching {
            val (myUid, token) = AnalyticsService.getOrCreateAuthCredentials(context) ?: return@runCatching null
            val now = maxOf(System.currentTimeMillis(), conv.createdAtMs)
            if (conv.myLastReadAtMs(myUid) >= conv.lastMessageAtMs) return@runCatching conv
            val updated = conv.copy(
                uid1LastReadAtMs = if (conv.uid1 == myUid) maxOf(conv.uid1LastReadAtMs, now) else conv.uid1LastReadAtMs,
                uid2LastReadAtMs = if (conv.uid2 == myUid) maxOf(conv.uid2LastReadAtMs, now) else conv.uid2LastReadAtMs,
                updatedAtMs = maxOf(now, conv.updatedAtMs)
            )
            val (code, _) = execFs(
                "PATCH",
                "$FS_BASE/${encPath("dm_conversations/${conv.convId}")}",
                token,
                JSONObject().put("fields", convToFields(updated))
            )
            if (code in 200..299) updated else null
        }.getOrNull()
    }

    suspend fun refreshConversation(context: Context, convId: String): DmConversation? = withContext(Dispatchers.IO) {
        runCatching {
            val (_, token) = AnalyticsService.getOrCreateAuthCredentials(context) ?: return@runCatching null
            val (code, text) = execFs("GET", "$FS_BASE/${encPath("dm_conversations/$convId")}", token)
            if (code !in 200..299 || text.isBlank()) return@runCatching null
            parseConvDoc(JSONObject(text))
        }.getOrNull()
    }

    suspend fun fetchMessages(context: Context, convId: String): Result<List<DmMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            val (_, token) = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: throw IllegalStateException("Bağlantı yok")
            val docs = listSubcollection(token, "dm_conversations/$convId/messages", 100)
            docs.map { parseMessageDoc(it) }.sortedBy { it.createdAtMs }
        }
    }

    suspend fun loadDmVideoFile(
        context: Context,
        convId: String,
        msgId: String,
        chunkCount: Int,
        mime: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val ext = if (mime.contains("webm", ignoreCase = true)) "webm" else "mp4"
        val cacheFile = File(File(context.cacheDir, "dm_videos").apply { mkdirs() }, "${convId}_${msgId}.$ext")
        if (cacheFile.exists() && cacheFile.length() > 1024L) return@withContext Result.success(cacheFile)
        val bytes = loadDmMediaChunks(context, convId, msgId, chunkCount).getOrElse {
            return@withContext Result.failure(it)
        }
        runCatching { cacheFile.writeBytes(bytes); cacheFile }
    }

    suspend fun loadDmAudioBytes(context: Context, convId: String, message: DmMessage): ByteArray =
        withContext(Dispatchers.IO) {
            if (message.chunkCount > 0) {
                loadDmMediaChunks(context, convId, message.msgId, message.chunkCount).getOrThrow()
            } else {
                val payload = message.mediaBase64.substringAfter(";base64,", message.mediaBase64)
                Base64.decode(payload, Base64.DEFAULT).also { require(it.isNotEmpty()) }
            }
        }

    private suspend fun loadDmMediaChunks(
        context: Context,
        convId: String,
        msgId: String,
        chunkCount: Int
    ): Result<ByteArray> {
        val (_, token) = AnalyticsService.getOrCreateAuthCredentials(context)
            ?: return Result.failure(IllegalStateException("Bağlantı yok"))
        val docs = runCatching { listSubcollection(token, "dm_conversations/$convId/messages/$msgId/media_chunks", 25) }
            .getOrElse { return Result.failure(it) }
        if (docs.isEmpty()) return Result.failure(IllegalStateException("Medya yüklenemedi"))
        val pairs = docs.map { d ->
            val f = d.optJSONObject("fields")
            intVal(f, "index").toInt() to strVal(f, "b64")
        }.sortedBy { it.first }
        val count = if (chunkCount > 0) chunkCount else pairs.size
        val parts = pairs.take(count)
        if (parts.size != count || parts.withIndex().any { (index, part) -> part.first != index || part.second.isEmpty() }) {
            return Result.failure(IllegalStateException("Medya parçaları eksik"))
        }
        return runCatching { Base64.decode(parts.joinToString("") { it.second }, Base64.DEFAULT) }
    }

    fun encodePhotoUriForDm(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val maxSide = 960
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val ratio = (maxSide.toFloat() / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
                decoder.setTargetSize((info.size.width * ratio).roundToInt().coerceAtLeast(1),
                    (info.size.height * ratio).roundToInt().coerceAtLeast(1))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = ImageStorage.calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@runCatching null
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            val rotated = ImageStorage.rotateIfNeeded(decoded, orientation)
            if (rotated !== decoded) decoded.recycle()
            val ratio = (maxSide.toFloat() / maxOf(rotated.width, rotated.height)).coerceAtMost(1f)
            Bitmap.createScaledBitmap(rotated, (rotated.width * ratio).roundToInt().coerceAtLeast(1),
                (rotated.height * ratio).roundToInt().coerceAtLeast(1), true).also {
                if (it !== rotated) rotated.recycle()
            }
        }
        try {
            for (quality in 84 downTo 36 step 12) {
                val output = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) break
                if (output.size() <= 217_000) {
                    return@runCatching "data:image/jpeg;base64,${Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)}"
                }
            }
            null
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    fun decodeDmPhoto(dataUrl: String): Bitmap? = runCatching {
        if (!dataUrl.startsWith("data:image/")) return@runCatching null
        val bytes = Base64.decode(dataUrl.substringAfter(";base64,", ""), Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageStorage.calculateInSampleSize(bounds.outWidth, bounds.outHeight, 960)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching null
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        ImageStorage.rotateIfNeeded(decoded, orientation).also { if (it !== decoded) decoded.recycle() }
    }.getOrNull()

    private fun sendDmNotificationBestEffort(
        token: String,
        targetUid: String,
        fromUid: String,
        profile: LocalProfileConfig,
        type: String,
        postId: String,
        postOwnerUid: String,
        previewText: String
    ) {
        if (targetUid.isBlank() || targetUid == fromUid) return
        val now = System.currentTimeMillis()
        val notifId = "dm_${now}_${randomSuffix()}".take(64)
        val fields = JSONObject()
            .put("notifId", fsStr(notifId))
            .put("targetUid", fsStr(targetUid))
            .put("type", fsStr(type))
            .put("fromUid", fsStr(fromUid))
            .put("fromUsername", fsStr(SocialProfileService.normalizeUsername(profile.username).take(24)))
            .put("fromDisplayName", fsStr(profile.displayName.take(50)))
            .put("fromAvatar", fsStr(profile.avatarThumbBase64.take(40_000)))
            .put("postId", fsStr(postId.take(48)))
            .put("postOwnerUid", fsStr(postOwnerUid.take(128)))
            .put("previewText", fsStr(previewText.take(150)))
            .put("read", fsBool(false))
            .put("createdAtMs", fsLong(now))
        runCatching {
            execFs(
                "PATCH",
                "$FS_BASE/${encPath("notifications/$targetUid/items/$notifId")}",
                token,
                JSONObject().put("fields", fields)
            )
        }
    }

    suspend fun sendMessage(
        context: Context,
        conv: DmConversation,
        myProfile: LocalProfileConfig,
        msgType: String,
        text: String = "",
        mediaBase64: String = "",
        mediaUrl: String = "",
        mediaMime: String = "",
        mediaDurationSec: Int = 0,
        videoFile: File? = null,
        sharedPost: SocialPostData? = null
    ): Result<Pair<DmConversation, DmMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!SocialProfileService.isValidUsername(myProfile.username)) {
                throw IllegalStateException("USERNAME_REQUIRED:Mesaj göndermek için önce kullanıcı adı oluşturmalısın.")
            }
            val (myUid, token) = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: throw IllegalStateException("Bağlantı yok")

            val latestConv = refreshConversation(context, conv.convId) ?: conv
            if (latestConv.status == "declined") {
                throw IllegalStateException("Bu mesaj isteği reddedilmiş.")
            }
            if (latestConv.isRequestLockReached(myUid)) {
                throw IllegalStateException("Karşı taraf isteğini kabul edene kadar en fazla 1 mesaj gönderebilirsin.")
            }
            val partnerUid = latestConv.partnerUid(myUid)
            val (byMe, againstMe) = fetchBlockedSetsSync(token, myUid)
            if (partnerUid in byMe || partnerUid in againstMe) {
                throw IllegalStateException("Engellenmiş kullanıcıya mesaj gönderilemez.")
            }

            val now = System.currentTimeMillis()
            val msgId = if (latestConv.status == "request") DmMessage.REQ_FIRST_MSG_ID else "m_${now}_${randomSuffix()}"

            var finalChunkCount = 0
            var finalMime = mediaMime
            var finalDuration = mediaDurationSec
            var finalMediaB64 = mediaBase64

            val chunkedAudio = msgType == "audio" && finalMediaB64.length > 315_000
            if ((msgType == "video" && videoFile != null) || chunkedAudio) {
                val b64All = if (chunkedAudio) {
                    finalMediaB64.substringAfter(";base64,", "")
                } else {
                    checkNotNull(videoFile)
                    if (videoFile.length() !in 1L..MAX_VIDEO_BYTES.toLong()) {
                        throw IllegalStateException("Video boyutu en fazla 7.5 MB olabilir.")
                    }
                    val raw = videoFile.readBytes()
                    val mmr = MediaMetadataRetriever()
                    runCatching {
                        mmr.setDataSource(videoFile.absolutePath)
                        val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        finalDuration = (durMs / 1000L).toInt().coerceIn(0, 120)
                    }
                    runCatching { mmr.release() }

                    Base64.encodeToString(raw, Base64.NO_WRAP)
                }
                val chunks = b64All.chunked(CHUNK_SIZE_CHARS)
                if (chunks.isEmpty() || chunks.size > MAX_CHUNKS) {
                    throw IllegalStateException("Medya boyutu sınırı aşıldı.")
                }
                chunks.forEachIndexed { idx, part ->
                    val chunkPayload = JSONObject().put(
                        "fields",
                        JSONObject()
                            .put("ownerUid", fsStr(myUid))
                            .put("index", fsLong(idx.toLong()))
                            .put("b64", fsStr(part))
                    )
                    val (cCode, cErr) = execFs(
                        "PATCH",
                        "$FS_BASE/${encPath("dm_conversations/${latestConv.convId}/messages/$msgId/media_chunks/$idx")}",
                        token,
                        chunkPayload
                    )
                    if (cCode !in 200..299) {
                        throw IllegalStateException("Medya yüklenemedi ($cCode): ${cErr.take(100)}")
                    }
                }
                finalChunkCount = chunks.size
                if (finalMime.isBlank()) finalMime = if (chunkedAudio) "audio/mp4" else "video/mp4"
                finalMediaB64 = ""
            } else if (msgType == "photo" && finalMediaB64.length > 315_000) {
                throw IllegalStateException("Medya boyutu sınırı aşıldı (~300 KB).")
            }

            val thumb = sharedPost?.let { sp ->
                sp.photos.firstOrNull().orEmpty().ifBlank { sp.posterBase64 }.take(135_000)
            }.orEmpty()

            val msg = DmMessage(
                msgId = msgId,
                convId = latestConv.convId,
                senderUid = myUid,
                senderUsername = SocialProfileService.normalizeUsername(myProfile.username),
                senderDisplayName = myProfile.displayName.take(50),
                senderAvatar = myProfile.avatarThumbBase64.take(40_000),
                msgType = msgType,
                text = text.trim().take(1000),
                mediaBase64 = finalMediaB64,
                mediaUrl = mediaUrl.take(600),
                mediaMime = finalMime.take(64),
                mediaDurationSec = finalDuration.coerceIn(0, 120),
                chunkCount = finalChunkCount,
                sharedPostId = sharedPost?.postId.orEmpty().take(48),
                sharedPostOwnerUid = sharedPost?.ownerUid.orEmpty().take(128),
                sharedPostOwnerUsername = sharedPost?.ownerUsername.orEmpty().take(24),
                sharedPostCaption = sharedPost?.caption.orEmpty().take(220),
                sharedPostThumb = thumb,
                isDeleted = false,
                createdAtMs = now,
                editedAtMs = 0L
            )

            val (mCode, mErr) = execFs(
                "PATCH",
                "$FS_BASE/${encPath("dm_conversations/${latestConv.convId}/messages/$msgId")}?currentDocument.exists=false",
                token,
                JSONObject().put("fields", messageToFields(msg))
            )
            if (mCode !in 200..299) {
                throw IllegalStateException("Mesaj gönderilemedi ($mCode): ${mErr.take(120)}")
            }

            val preview = when (msgType) {
                "text" -> msg.text.take(120)
                "photo" -> "📷 Fotoğraf"
                "video" -> "🎬 Video"
                "audio" -> "🎤 Sesli mesaj"
                "gif" -> "GIF"
                "shared_post" -> msg.text.ifBlank { "📌 Gönderi paylaştı" }.take(120)
                "post_reply" -> msg.text.ifBlank { "↩ Gönderiye yanıt verdi" }.take(120)
                else -> "Mesaj"
            }

            val updatedConv = latestConv.copy(
                requestMessageCount = if (latestConv.status == "request") 1 else latestConv.requestMessageCount,
                lastMessageText = preview.take(200),
                lastMessageType = msgType,
                lastMessageSenderUid = myUid,
                lastMessageAtMs = now,
                uid1LastReadAtMs = if (latestConv.uid1 == myUid) maxOf(latestConv.uid1LastReadAtMs, now) else latestConv.uid1LastReadAtMs,
                uid2LastReadAtMs = if (latestConv.uid2 == myUid) maxOf(latestConv.uid2LastReadAtMs, now) else latestConv.uid2LastReadAtMs,
                updatedAtMs = maxOf(now, latestConv.createdAtMs)
            )
            execFs(
                "PATCH",
                "$FS_BASE/${encPath("dm_conversations/${latestConv.convId}")}",
                token,
                JSONObject().put("fields", convToFields(updatedConv))
            )

            val notifType = when {
                latestConv.status == "request" -> "dm_request"
                msgType == "post_reply" -> "dm_post_reply"
                else -> "dm_message"
            }
            sendDmNotificationBestEffort(
                token = token,
                targetUid = partnerUid,
                fromUid = myUid,
                profile = myProfile,
                type = notifType,
                postId = msg.sharedPostId,
                postOwnerUid = msg.sharedPostOwnerUid,
                previewText = preview
            )

            updatedConv to msg
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { err ->
                if (err is CancellationException) throw err
                Result.failure(err)
            }
        )
    }

    suspend fun editMessage(
        context: Context,
        conv: DmConversation,
        msg: DmMessage,
        newText: String
    ): Result<DmMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = newText.trim().take(1000)
            if (trimmed.isBlank()) throw IllegalArgumentException("Mesaj boş olamaz")
            if (!msg.canEdit()) throw IllegalStateException("15 dakikalık düzenleme süresi doldu")
            val (myUid, token) = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: throw IllegalStateException("Bağlantı yok")
            if (msg.senderUid != myUid) throw IllegalStateException("Yalnızca kendi mesajını düzenleyebilirsin")

            val now = maxOf(System.currentTimeMillis(), msg.createdAtMs)
            val updated = msg.copy(text = trimmed, editedAtMs = now)
            val (code, text) = execFs(
                "PATCH",
                "$FS_BASE/${encPath("dm_conversations/${conv.convId}/messages/${msg.msgId}")}",
                token,
                JSONObject().put("fields", messageToFields(updated))
            )
            if (code !in 200..299) {
                throw IllegalStateException("Düzenlenemedi ($code): ${text.take(120)}")
            }
            if (conv.lastMessageAtMs <= msg.createdAtMs) {
                val updConv = conv.copy(
                    lastMessageText = trimmed.take(200),
                    updatedAtMs = maxOf(System.currentTimeMillis(), conv.createdAtMs)
                )
                execFs(
                    "PATCH",
                    "$FS_BASE/${encPath("dm_conversations/${conv.convId}")}",
                    token,
                    JSONObject().put("fields", convToFields(updConv))
                )
            }
            updated
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { err ->
                if (err is CancellationException) throw err
                Result.failure(err)
            }
        )
    }

    suspend fun softDeleteMessage(
        context: Context,
        conv: DmConversation,
        msg: DmMessage
    ): Result<DmMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val (myUid, token) = AnalyticsService.getOrCreateAuthCredentials(context)
                ?: throw IllegalStateException("Bağlantı yok")
            if (msg.senderUid != myUid) throw IllegalStateException("Yalnızca kendi mesajını silebilirsin")

            val now = maxOf(System.currentTimeMillis(), msg.createdAtMs)
            val tombstone = msg.copy(
                isDeleted = true,
                text = "",
                mediaBase64 = "",
                mediaUrl = "",
                sharedPostThumb = "",
                editedAtMs = now
            )
            val (code, text) = execFs(
                "PATCH",
                "$FS_BASE/${encPath("dm_conversations/${conv.convId}/messages/${msg.msgId}")}",
                token,
                JSONObject().put("fields", messageToFields(tombstone))
            )
            if (code !in 200..299) {
                throw IllegalStateException("Silinemedi ($code): ${text.take(120)}")
            }
            if (conv.lastMessageAtMs <= msg.createdAtMs) {
                val updConv = conv.copy(
                    lastMessageType = "deleted",
                    lastMessageText = "Bu mesaj silindi",
                    updatedAtMs = maxOf(System.currentTimeMillis(), conv.createdAtMs)
                )
                execFs(
                    "PATCH",
                    "$FS_BASE/${encPath("dm_conversations/${conv.convId}")}",
                    token,
                    JSONObject().put("fields", convToFields(updConv))
                )
            }
            tombstone
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { err ->
                if (err is CancellationException) throw err
                Result.failure(err)
            }
        )
    }
}
