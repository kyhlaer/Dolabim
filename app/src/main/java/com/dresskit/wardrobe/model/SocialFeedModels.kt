package com.dresskit.wardrobe.model

enum class SocialPostKind(val key: String, val labelTr: String, val labelEn: String) {
    OUTFIT("outfit", "Kombin", "Outfit"),
    NEW_PIECE("new_piece", "Yeni Parça", "New Piece"),
    LOOK("look", "Look / Stil", "Look"),
    PHOTO("photo", "Fotoğraf", "Photo"),
    VIDEO("video", "Video", "Video"),
    QUESTION("question", "Fikir / Soru", "Ask");

    fun display(lang: AppLanguage): String = localizedText(lang, labelTr, labelEn)

    companion object {
        fun fromKey(raw: String): SocialPostKind =
            entries.firstOrNull { it.key == raw } ?: OUTFIT
    }
}

data class SocialPostData(
    val postId: String,
    val ownerUid: String,
    val ownerUsername: String,
    val ownerDisplayName: String,
    val ownerAvatar: String,
    val postKind: String,
    val caption: String,
    val mediaType: String, // "photo" | "video" | "text"
    val photos: List<String>,
    val posterBase64: String,
    val chunkCount: Int,
    val videoMime: String,
    val videoDurationSec: Int,
    val attachedItemName: String,
    val attachedCategory: String,
    val attachedPriceText: String,
    val attachedProductUrl: String,
    val createdAtMs: Long,
    val editedAtMs: Long,
    val isPrivatePost: Boolean = false
) {
    val shareUrl: String
        get() = buildString {
            append("https://kyhlaer.github.io/Dolabim/?post=")
            append(postId)
            if (isPrivatePost && ownerUid.isNotBlank()) {
                append("&owner=")
                append(ownerUid)
            }
        }

    fun canEditCaption(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs <= createdAtMs + EDIT_WINDOW_MS

    fun editRemainingMinutes(nowMs: Long = System.currentTimeMillis()): Int {
        val rem = (createdAtMs + EDIT_WINDOW_MS) - nowMs
        if (rem <= 0L) return 0
        return ((rem + 59_999L) / 60_000L).toInt().coerceIn(1, 15)
    }

    companion object {
        const val EDIT_WINDOW_MS = 900_000L // 15 minutes
    }
}

data class PostLiker(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarBase64: String
)

data class PostCommentData(
    val commentId: String,
    val postId: String,
    val authorUid: String,
    val authorUsername: String,
    val authorDisplayName: String,
    val authorAvatar: String,
    val text: String,
    val mediaType: String, // "none" | "image" | "gif"
    val mediaBase64: String,
    val mediaUrl: String,
    val createdAtMs: Long
)

data class SocialNotificationData(
    val notifId: String,
    val targetUid: String,
    val type: String, // "follow_request" | "follow_accepted" | "new_follower" | "post_like" | "post_comment"
    val fromUid: String,
    val fromUsername: String,
    val fromDisplayName: String,
    val fromAvatar: String,
    val postId: String,
    val postOwnerUid: String,
    val previewText: String,
    val read: Boolean,
    val createdAtMs: Long
)

data class GifPreset(
    val id: String,
    val labelTr: String,
    val labelEn: String,
    val dataUrl: String
) {
    fun display(lang: AppLanguage): String = localizedText(lang, labelTr, labelEn)
}
