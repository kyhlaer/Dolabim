package com.dresskit.wardrobe.model

enum class DmChatActivityState(val key: String) {
    IDLE("idle"),
    TYPING("typing"),
    CAPTURING_PHOTO("capturing_photo"),
    UPLOADING_PHOTO("uploading_photo"),
    RECORDING_AUDIO("recording_audio");

    companion object {
        fun fromKey(raw: String): DmChatActivityState =
            entries.firstOrNull { it.key == raw } ?: IDLE
    }
}

data class DmGlobalPresence(
    val uid: String = "",
    val isOnline: Boolean = false,
    val lastOnlineAtMs: Long = 0L
) {
    fun isEffectivelyOnline(nowMs: Long = System.currentTimeMillis()): Boolean =
        isOnline && lastOnlineAtMs > 0L && (nowMs - lastOnlineAtMs) < 75_000L
}

data class DmConversation(
    val convId: String = "",
    val participantUids: List<String> = emptyList(),
    val uid1: String = "",
    val uid2: String = "",
    val status: String = "accepted", // "accepted" | "request" | "declined"
    val requesterUid: String = "",
    val recipientUid: String = "",
    val requestMessageCount: Int = 0,
    val lastMessageText: String = "",
    val lastMessageType: String = "none",
    val lastMessageSenderUid: String = "",
    val lastMessageAtMs: Long = 0L,
    val uid1LastReadAtMs: Long = 0L,
    val uid2LastReadAtMs: Long = 0L,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
) {
    fun partnerUid(myUid: String): String =
        if (uid1 == myUid) uid2 else uid1

    fun myLastReadAtMs(myUid: String): Long =
        if (uid1 == myUid) uid1LastReadAtMs else uid2LastReadAtMs

    fun partnerLastReadAtMs(myUid: String): Long =
        if (uid1 == myUid) uid2LastReadAtMs else uid1LastReadAtMs

    fun hasUnreadFromPartner(myUid: String): Boolean =
        lastMessageSenderUid.isNotBlank() &&
            lastMessageSenderUid != myUid &&
            lastMessageAtMs > myLastReadAtMs(myUid)

    fun isRequestLockReached(myUid: String): Boolean =
        status == "request" && requesterUid == myUid && requestMessageCount >= 1
}

data class DmThreadSummary(
    val conversation: DmConversation,
    val partner: PublicProfileHeader,
    val partnerPresence: DmGlobalPresence = DmGlobalPresence(),
    val isBlockedByMe: Boolean = false,
    val hasBlockedMe: Boolean = false,
    val isMuted: Boolean = false,
    val isUnread: Boolean = false
)

data class DmMessage(
    val msgId: String = "",
    val convId: String = "",
    val senderUid: String = "",
    val senderUsername: String = "",
    val senderDisplayName: String = "",
    val senderAvatar: String = "",
    val msgType: String = "text", // text | photo | video | audio | gif | shared_post | post_reply
    val text: String = "",
    val mediaBase64: String = "",
    val mediaUrl: String = "",
    val mediaMime: String = "",
    val mediaDurationSec: Int = 0,
    val chunkCount: Int = 0,
    val sharedPostId: String = "",
    val sharedPostOwnerUid: String = "",
    val sharedPostOwnerUsername: String = "",
    val sharedPostCaption: String = "",
    val sharedPostThumb: String = "",
    val isDeleted: Boolean = false,
    val createdAtMs: Long = 0L,
    val editedAtMs: Long = 0L
) {
    fun canEdit(nowMs: Long = System.currentTimeMillis()): Boolean =
        !isDeleted &&
            (msgType == "text" || msgType == "post_reply" || msgType == "shared_post") &&
            createdAtMs > 0L &&
            (nowMs - createdAtMs) <= DM_EDIT_WINDOW_MS

    fun editRemainingMinutes(nowMs: Long = System.currentTimeMillis()): Int {
        val rem = (DM_EDIT_WINDOW_MS - (nowMs - createdAtMs)).coerceAtLeast(0L)
        return ((rem + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    companion object {
        const val DM_EDIT_WINDOW_MS = 15L * 60_000L
        const val REQ_FIRST_MSG_ID = "req_first"
    }
}

fun pairConvId(uidA: String, uidB: String): String {
    val a = uidA.trim()
    val b = uidB.trim()
    return if (a <= b) "${a}_${b}" else "${b}_${a}"
}
