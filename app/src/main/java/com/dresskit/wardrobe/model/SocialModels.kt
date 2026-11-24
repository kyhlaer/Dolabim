package com.dresskit.wardrobe.model

import org.json.JSONArray
import org.json.JSONObject

val STYLE_VIBE_PRESETS = listOf(
    "Minimalist",
    "Streetwear",
    "Smart Casual",
    "Vintage",
    "Old Money",
    "Sporty",
    "Monokrom",
    "Casual",
    "Klasik",
    "Bohem"
)

data class StatsVisibilityConfig(
    val showTotalItems: Boolean = true,
    val showCategoryBreakdown: Boolean = true,
    val showFavoriteCount: Boolean = true,
    val showWishlistCount: Boolean = true,
    val showOutfitCount: Boolean = true,
    val showTopColors: Boolean = true,
    val showTopSubCategories: Boolean = true,
    val showEstimatedValue: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("showTotalItems", showTotalItems)
        .put("showCategoryBreakdown", showCategoryBreakdown)
        .put("showFavoriteCount", showFavoriteCount)
        .put("showWishlistCount", showWishlistCount)
        .put("showOutfitCount", showOutfitCount)
        .put("showTopColors", showTopColors)
        .put("showTopSubCategories", showTopSubCategories)
        .put("showEstimatedValue", showEstimatedValue)

    companion object {
        fun fromJson(obj: JSONObject?): StatsVisibilityConfig {
            if (obj == null) return StatsVisibilityConfig()
            return StatsVisibilityConfig(
                showTotalItems = obj.optBoolean("showTotalItems", true),
                showCategoryBreakdown = obj.optBoolean("showCategoryBreakdown", true),
                showFavoriteCount = obj.optBoolean("showFavoriteCount", true),
                showWishlistCount = obj.optBoolean("showWishlistCount", true),
                showOutfitCount = obj.optBoolean("showOutfitCount", true),
                showTopColors = obj.optBoolean("showTopColors", true),
                showTopSubCategories = obj.optBoolean("showTopSubCategories", true),
                showEstimatedValue = obj.optBoolean("showEstimatedValue", false)
            )
        }
    }
}

data class CustomShowcaseItem(
    val id: String,
    val name: String,
    val category: String = ClothingCategory.TOP.name,
    val notes: String = "",
    val priceText: String = "",
    val productUrl: String = "",
    val photoBase64: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("category", category)
        .put("notes", notes)
        .put("priceText", priceText)
        .put("productUrl", productUrl)
        .put("photoBase64", photoBase64)
        .put("createdAtMs", createdAtMs)

    companion object {
        fun fromJson(obj: JSONObject?): CustomShowcaseItem? {
            obj ?: return null
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) return null
            return CustomShowcaseItem(
                id = id,
                name = name,
                category = obj.optString("category", ClothingCategory.TOP.name),
                notes = obj.optString("notes", ""),
                priceText = obj.optString("priceText", ""),
                productUrl = obj.optString("productUrl", ""),
                photoBase64 = obj.optString("photoBase64", ""),
                createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
            )
        }
    }
}

data class LocalProfileConfig(
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarBase64: String = "",
    val avatarThumbBase64: String = "",
    val isPrivate: Boolean = false,
    val styleTags: List<String> = emptyList(),
    val statsVisibility: StatsVisibilityConfig = StatsVisibilityConfig(),
    val showcaseSections: List<String> = listOf("favorites", "selected"),
    val selectedShowcaseItemIds: List<String> = emptyList(),
    val customShowcaseItems: List<CustomShowcaseItem> = emptyList(),
    val lastSyncedAtMs: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("username", username)
        .put("displayName", displayName)
        .put("bio", bio)
        .put("avatarBase64", avatarBase64)
        .put("avatarThumbBase64", avatarThumbBase64)
        .put("isPrivate", isPrivate)
        .put("styleTags", JSONArray(styleTags))
        .put("statsVisibility", statsVisibility.toJson())
        .put("showcaseSections", JSONArray(showcaseSections))
        .put("selectedShowcaseItemIds", JSONArray(selectedShowcaseItemIds))
        .put("customShowcaseItems", JSONArray().apply {
            customShowcaseItems.forEach { put(it.toJson()) }
        })
        .put("lastSyncedAtMs", lastSyncedAtMs)

    companion object {
        fun fromJson(obj: JSONObject?): LocalProfileConfig {
            if (obj == null) return LocalProfileConfig()
            val tags = buildList {
                val arr = obj.optJSONArray("styleTags")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }.take(3)
            val secs = buildList {
                val arr = obj.optJSONArray("showcaseSections")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i).trim()
                        if (s in setOf("favorites", "wishlist", "selected", "custom")) add(s)
                    }
                }
            }.distinct()
            val selIds = buildList {
                val arr = obj.optJSONArray("selectedShowcaseItemIds")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }.distinct()
            val customs = buildList {
                val arr = obj.optJSONArray("customShowcaseItems")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        CustomShowcaseItem.fromJson(arr.optJSONObject(i))?.let { add(it) }
                    }
                }
            }
            return LocalProfileConfig(
                username = obj.optString("username", ""),
                displayName = obj.optString("displayName", ""),
                bio = obj.optString("bio", ""),
                avatarBase64 = obj.optString("avatarBase64", ""),
                avatarThumbBase64 = obj.optString("avatarThumbBase64", ""),
                isPrivate = obj.optBoolean("isPrivate", false),
                styleTags = tags,
                statsVisibility = StatsVisibilityConfig.fromJson(obj.optJSONObject("statsVisibility")),
                showcaseSections = secs.ifEmpty { listOf("favorites", "selected") },
                selectedShowcaseItemIds = selIds,
                customShowcaseItems = customs,
                lastSyncedAtMs = obj.optLong("lastSyncedAtMs", 0L)
            )
        }
    }
}

data class PublicProfileHeader(
    val uid: String,
    val username: String,
    val usernameLower: String,
    val displayName: String,
    val bio: String,
    val avatarBase64: String,
    val styleTags: List<String>,
    val isPrivate: Boolean,
    val updatedAtMs: Long
)

data class ProfileContentData(
    val ownerUid: String,
    val statsVisibility: StatsVisibilityConfig,
    val wardrobeSummary: Map<String, Any>,
    val showcaseSections: List<String>,
    val updatedAtMs: Long
)

data class ShowcaseItemData(
    val ownerUid: String,
    val itemId: String,
    val section: String,
    val name: String,
    val category: String,
    val subCategory: String,
    val styleTag: String,
    val seasons: List<String>,
    val colors: List<String>,
    val priceText: String,
    val productUrl: String,
    val notes: String,
    val photoBase64: String,
    val sortOrder: Int,
    val createdAtMs: Long
)

data class FollowRelation(
    val followId: String,
    val followerUid: String,
    val targetUid: String,
    val status: String, // "accepted" | "pending"
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class SharedGarmentData(
    val shareId: String,
    val ownerUid: String,
    val ownerUsername: String,
    val ownerDisplayName: String,
    val ownerAvatar: String,
    val name: String,
    val category: String,
    val subCategory: String,
    val styleTag: String,
    val seasons: List<String>,
    val colors: List<String>,
    val notes: String,
    val priceText: String,
    val productUrl: String,
    val isWishlist: Boolean,
    val photos: List<String>,
    val createdAtMs: Long
) {
    val shareUrl: String
        get() = "https://kyhlaer.github.io/Dolabim/?item=$shareId"
}

sealed class DeepLinkTarget {
    data class SharedItem(val shareId: String) : DeepLinkTarget()
    data class UserProfile(val usernameOrUid: String, val isUid: Boolean = false) : DeepLinkTarget()
    data class SocialPost(val postId: String, val ownerUid: String = "") : DeepLinkTarget()
}

data class LoadedProfileBundle(
    val header: PublicProfileHeader,
    val canViewContent: Boolean,
    val followStatus: String?, // null, "accepted", "pending"
    val followersCount: Int,
    val followingCount: Int,
    val pendingRequestUids: List<String>,
    val content: ProfileContentData?,
    val showcaseItems: List<ShowcaseItemData>
)
