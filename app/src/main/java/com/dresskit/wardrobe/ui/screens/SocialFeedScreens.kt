package com.dresskit.wardrobe.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.dresskit.wardrobe.data.AnalyticsService
import com.dresskit.wardrobe.data.AnimatedGifGenerator
import com.dresskit.wardrobe.data.SocialFeedService
import com.dresskit.wardrobe.data.SocialProfileService
import com.dresskit.wardrobe.data.WardrobeRepository
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.LocalProfileConfig
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.PostCommentData
import com.dresskit.wardrobe.model.PublicProfileHeader
import com.dresskit.wardrobe.model.SocialNotificationData
import com.dresskit.wardrobe.model.SocialPostData
import com.dresskit.wardrobe.model.SocialPostKind
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.components.FilterChipPill
import com.dresskit.wardrobe.ui.components.bouncyOverscroll
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class PostFilterMode(val labelTr: String, val labelEn: String) {
    ALL("Tümü", "All"),
    FOLLOWING("Takip", "Following"),
    OUTFIT("Kombin", "Outfits"),
    NEW_PIECE("Yeni Parça", "New Pieces"),
    LOOK("Look", "Looks"),
    VIDEO("Video", "Videos")
}

private enum class PostPhotoFilterPreset(val labelTr: String, val labelEn: String) {
    ORIGINAL("Orijinal", "Original"),
    WARM("Sıcak", "Warm"),
    COOL("Soğuk", "Cool"),
    BW("Siyah-Beyaz", "B&W"),
    VIVID("Canlı", "Vivid"),
    EDITORIAL("Editorial", "Editorial")
}

@Composable
fun ActivityHeartIconButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryInk = DressKitStyle.primaryInk()
    val accent = DressKitStyle.accentColor()
    Box(modifier = modifier.size(36.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(DressKitStyle.secondarySurface())
                .border(1.dp, DressKitStyle.borderColor(), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (unreadCount > 0) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Etkinlik & İstekler",
                tint = if (unreadCount > 0) Color(0xFFF43F5E) else primaryInk,
                modifier = Modifier.size(19.dp)
            )
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .height(17.dp)
                    .widthIn(min = 17.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .border(1.5.dp, DressKitStyle.canvasBg(), CircleShape)
                    .padding(horizontal = if (unreadCount > 9) 4.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else "$unreadCount",
                    color = DressKitStyle.buttonFg(),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

@Composable
fun ActivityHeartDialog(
    myProfile: LocalProfileConfig,
    repository: WardrobeRepository,
    allItems: List<ClothingItem> = emptyList(),
    allOutfits: List<Outfit> = emptyList(),
    onProfileUpdated: (LocalProfileConfig) -> Unit = {},
    onOpenDmUid: ((String) -> Unit)? = null,
    onOpenProfileUid: (String) -> Unit,
    onOpenPost: (String, String) -> Unit,
    onUnreadChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var currentProfile by remember(myProfile) { mutableStateOf(myProfile) }
    var usernameGateReason by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pendingRequests by remember { mutableStateOf<List<PublicProfileHeader>>(emptyList()) }
    var notifications by remember { mutableStateOf<List<SocialNotificationData>>(emptyList()) }
    val acceptedAndCanFollowBack = remember { mutableStateMapOf<String, Boolean>() }
    val busyUids = remember { mutableStateMapOf<String, Boolean>() }

    usernameGateReason?.let { reason ->
        UsernameRequiredDialog(
            myProfile = currentProfile,
            allItems = allItems,
            allOutfits = allOutfits,
            reasonText = reason,
            onSaved = { saved ->
                currentProfile = saved
                onProfileUpdated(saved)
            },
            onDismiss = { usernameGateReason = null }
        )
    }

    suspend fun loadAll() {
        loading = true
        val reqRes = SocialProfileService.fetchPendingFollowRequests(context)
        val notifRes = SocialFeedService.fetchMyNotifications(context)
        reqRes.onSuccess { pendingRequests = it }
        notifRes.onSuccess { list ->
            val filtered = list.filterNot {
                it.type == "dm_message" || it.type == "dm_request" || it.type == "dm_post_reply"
            }
            notifications = filtered
            onUnreadChanged(filtered.count { !it.read })
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        AnalyticsService.trackFeature("notif_open")
        loadAll()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .background(DressKitStyle.canvasBg()),
            color = DressKitStyle.canvasBg()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = localizedText(lang, "Hareketler & Takip İstekleri", "Activity & Follow Requests"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryInk
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (notifications.any { !it.read }) {
                            TextButton(onClick = {
                                scope.launch {
                                    SocialFeedService.markAllNotificationsRead(context, notifications)
                                    notifications = notifications.map { it.copy(read = true) }
                                    onUnreadChanged(0)
                                }
                            }) {
                                Text(localizedText(lang, "Okundu yap", "Mark read"), color = DressKitStyle.accentColor(), fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Kapat", tint = primaryInk)
                        }
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DressKitStyle.accentColor())
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = localizedText(lang, "Takip İstekleri (${pendingRequests.size})", "Follow Requests (${pendingRequests.size})"),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = primaryInk
                            )
                        }
                        if (pendingRequests.isEmpty() && acceptedAndCanFollowBack.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = localizedText(lang, "Bekleyen takip isteğin yok.", "No pending follow requests."),
                                        color = secondaryInk,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        } else {
                            items(pendingRequests, key = { "req_${it.uid}" }) { reqUser ->
                                val isAccepted = acceptedAndCanFollowBack.containsKey(reqUser.uid)
                                val alreadyFollowedBack = acceptedAndCanFollowBack[reqUser.uid] == true
                                val isBusy = busyUids[reqUser.uid] == true
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onOpenProfileUid(reqUser.uid)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        UserAvatarCircle(
                                            avatarBase64 = reqUser.avatarBase64,
                                            displayName = reqUser.displayName.ifBlank { reqUser.username },
                                            size = 42.dp
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = reqUser.displayName.ifBlank { reqUser.username.ifBlank { "Kullanıcı" } },
                                                color = primaryInk,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (reqUser.username.isNotBlank()) {
                                                Text("@${reqUser.username}", color = secondaryInk, fontSize = 12.sp)
                                            }
                                        }
                                        if (!isAccepted) {
                                            OutlinedButton(
                                                onClick = {
                                                    busyUids[reqUser.uid] = true
                                                    scope.launch {
                                                        val myUid = AnalyticsService.getCurrentUid(context)
                                                        SocialProfileService.unfollowOrCancel(context, reqUser.uid, myUid)
                                                        pendingRequests = pendingRequests.filterNot { it.uid == reqUser.uid }
                                                        busyUids.remove(reqUser.uid)
                                                    }
                                                },
                                                enabled = !isBusy,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text(localizedText(lang, "Reddet", "Decline"), color = secondaryInk, fontSize = 11.sp)
                                            }
                                            Button(
                                                onClick = {
                                                    if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                                                        usernameGateReason = localizedText(lang, "Takip isteğini onaylamak için önce kullanıcı adı oluşturmalısın.", "Create a username before accepting follow requests.")
                                                        return@Button
                                                    }
                                                    busyUids[reqUser.uid] = true
                                                    scope.launch {
                                                        SocialProfileService.acceptFollowRequest(context, reqUser.uid).onSuccess {
                                                            val bundle = SocialProfileService.fetchProfileBundle(context, reqUser.uid, isUsername = false).getOrNull()
                                                            val already = bundle?.followStatus != null
                                                            acceptedAndCanFollowBack[reqUser.uid] = already
                                                        }.onFailure { err ->
                                                            if (err.message?.startsWith("USERNAME_REQUIRED:") == true) {
                                                                usernameGateReason = localizedText(lang, "Takip isteğini onaylamak için önce kullanıcı adı oluşturmalısın.", "Create a username before accepting follow requests.")
                                                            } else {
                                                                Toast.makeText(context, err.message ?: "Hata", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        busyUids.remove(reqUser.uid)
                                                    }
                                                },
                                                enabled = !isBusy,
                                                colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg()),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text(localizedText(lang, "Onayla", "Accept"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    if (!alreadyFollowedBack) {
                                                        if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                                                            usernameGateReason = localizedText(lang, "Geri takip etmek için önce kullanıcı adı oluşturmalısın.", "Create a username before following back.")
                                                            return@OutlinedButton
                                                        }
                                                        busyUids[reqUser.uid] = true
                                                        scope.launch {
                                                            SocialProfileService.followUser(context, reqUser).onSuccess {
                                                                acceptedAndCanFollowBack[reqUser.uid] = true
                                                            }.onFailure { err ->
                                                                if (err.message?.startsWith("USERNAME_REQUIRED:") == true) {
                                                                    usernameGateReason = localizedText(lang, "Geri takip etmek için önce kullanıcı adı oluşturmalısın.", "Create a username before following back.")
                                                                } else {
                                                                    Toast.makeText(context, err.message ?: "Hata", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                            busyUids.remove(reqUser.uid)
                                                        }
                                                    }
                                                },
                                                enabled = !isBusy && !alreadyFollowedBack,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text(
                                                    text = if (alreadyFollowedBack) localizedText(lang, "Takip Edildi", "Following")
                                                    else localizedText(lang, "Geri Takip Et", "Follow Back"),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = localizedText(lang, "Bildirimler (${notifications.size})", "Notifications (${notifications.size})"),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = primaryInk
                            )
                        }
                        if (notifications.isEmpty()) {
                            item {
                                Text(
                                    text = localizedText(lang, "Henüz yeni hareket yok.", "No recent notifications yet."),
                                    color = secondaryInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            items(notifications, key = { "n_${it.notifId}" }) { n ->
                                val actor = n.fromUsername.takeIf { it.isNotBlank() }?.let { "@$it" }
                                    ?: n.fromDisplayName.ifBlank { "Kullanıcı" }
                                val msg = when (n.type) {
                                    "follow_request" -> "$actor seni takip etmek istedi"
                                    "follow_accepted" -> "$actor takip isteğini kabul etti"
                                    "new_follower" -> "$actor seni takip etmeye başladı"
                                    "post_like" -> "$actor gönderini beğendi"
                                    "post_comment" -> "$actor gönderine yorum yaptı: ${n.previewText}"
                                    "dm_request" -> "$actor sana mesaj isteği gönderdi"
                                    "dm_post_reply" -> "$actor gönderine özel yanıt verdi: ${n.previewText}"
                                    "dm_message" -> "$actor mesaj gönderdi: ${n.previewText}"
                                    else -> "$actor ${n.previewText}"
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (!n.read) DressKitStyle.secondarySurface() else DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, if (!n.read) DressKitStyle.accentColor().copy(alpha = 0.45f) else DressKitStyle.borderColor()),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch { SocialFeedService.markNotificationRead(context, n) }
                                            onDismiss()
                                            if ((n.type == "dm_message" || n.type == "dm_request" || n.type == "dm_post_reply") && onOpenDmUid != null) {
                                                onOpenDmUid(n.fromUid)
                                            } else if (n.postId.isNotBlank()) {
                                                onOpenPost(n.postId, n.postOwnerUid)
                                            } else {
                                                onOpenProfileUid(n.fromUid)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        UserAvatarCircle(avatarBase64 = n.fromAvatar, displayName = actor, size = 38.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(msg, color = primaryInk, style = MaterialTheme.typography.bodySmall)
                                            Text(fmtSocialRel(n.createdAtMs), color = secondaryInk, fontSize = 11.sp)
                                        }
                                        if (!n.read) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(DressKitStyle.accentColor())
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SocialFeedScreen(
    items: List<ClothingItem>,
    outfits: List<Outfit> = emptyList(),
    myProfile: LocalProfileConfig,
    repository: WardrobeRepository,
    profileAvatarBase64: String,
    pendingRequestsCount: Int,
    unreadActivityCount: Int,
    unreadDmCount: Int = 0,
    onOpenActivity: () -> Unit,
    onOpenDiscover: () -> Unit = {},
    onOpenDmInbox: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onProfileUpdated: (LocalProfileConfig) -> Unit = {},
    onReplyPostInDm: (SocialPostData) -> Unit = {},
    onForwardPostInDm: (SocialPostData) -> Unit = {},
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var currentProfile by remember(myProfile) { mutableStateOf(myProfile) }
    var posts by remember { mutableStateOf<List<SocialPostData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filterMode by remember { mutableStateOf(PostFilterMode.ALL) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var activeCommentsPost by remember { mutableStateOf<SocialPostData?>(null) }
    var editingCaptionPost by remember { mutableStateOf<SocialPostData?>(null) }
    var fullscreenPhotoDataUrl by remember { mutableStateOf<String?>(null) }
    var zoomAvatarTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var usernameGateReason by remember { mutableStateOf<String?>(null) }
    val myUid = remember { AnalyticsService.getCurrentUid(context) }

    val likeCounts = remember { mutableStateMapOf<String, Int>() }
    val likedByMe = remember { mutableStateMapOf<String, Boolean>() }

    suspend fun reloadFeed() {
        loading = true
        val res = SocialFeedService.fetchFeedPosts(
            context = context,
            followingOnly = filterMode == PostFilterMode.FOLLOWING,
            myIsPrivate = currentProfile.isPrivate
        )
        res.onSuccess { fetched ->
            posts = fetched
            fetched.take(24).forEach { p ->
                SocialFeedService.fetchPostLikeState(context, p.postId).onSuccess { (cnt, mine) ->
                    likeCounts[p.postId] = cnt
                    likedByMe[p.postId] = mine
                }
            }
        }.onFailure { err ->
            Toast.makeText(context, err.message ?: "Akış yüklenemedi", Toast.LENGTH_SHORT).show()
        }
        loading = false
    }

    LaunchedEffect(filterMode == PostFilterMode.FOLLOWING) {
        reloadFeed()
    }

    val displayedPosts = remember(posts, filterMode) {
        when (filterMode) {
            PostFilterMode.ALL, PostFilterMode.FOLLOWING -> posts
            PostFilterMode.OUTFIT -> posts.filter { it.postKind == SocialPostKind.OUTFIT.key }
            PostFilterMode.NEW_PIECE -> posts.filter { it.postKind == SocialPostKind.NEW_PIECE.key }
            PostFilterMode.LOOK -> posts.filter { it.postKind == SocialPostKind.LOOK.key }
            PostFilterMode.VIDEO -> posts.filter { it.mediaType == "video" }
        }
    }

    zoomAvatarTarget?.let { (av, dn, un) ->
        AvatarZoomDialog(avatarBase64 = av, displayName = dn, username = un, onDismiss = { zoomAvatarTarget = null })
    }

    usernameGateReason?.let { reason ->
        UsernameRequiredDialog(
            myProfile = currentProfile,
            allItems = items,
            allOutfits = outfits,
            reasonText = reason,
            onSaved = { saved ->
                currentProfile = saved
                onProfileUpdated(saved)
            },
            onDismiss = { usernameGateReason = null }
        )
    }

    if (showCreateDialog) {
        CreatePostDialog(
            items = items,
            myProfile = currentProfile,
            onOpenProfileSetup = {
                showCreateDialog = false
                onOpenProfile()
            },
            onCreated = { created ->
                posts = listOf(created) + posts
                showCreateDialog = false
                Toast.makeText(context, localizedText(lang, "Gönderi paylaşıldı!", "Post published!"), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    activeCommentsPost?.let { post ->
        PostCommentsDialog(
            post = post,
            myProfile = currentProfile,
            allItems = items,
            allOutfits = outfits,
            onProfileUpdated = { saved ->
                currentProfile = saved
                onProfileUpdated(saved)
            },
            onOpenProfileUid = { uid ->
                activeCommentsPost = null
                onOpenOtherProfile(uid)
            },
            onDismiss = { activeCommentsPost = null }
        )
    }

    editingCaptionPost?.let { post ->
        var capDraft by remember(post.postId) { mutableStateOf(post.caption) }
        var savingCap by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!savingCap) editingCaptionPost = null },
            containerColor = DressKitStyle.cardSurface(),
            title = { Text(localizedText(lang, "Yazıyı Düzenle (${post.editRemainingMinutes()} dk kaldı)", "Edit Caption"), color = primaryInk) },
            text = {
                OutlinedTextField(
                    value = capDraft,
                    onValueChange = { capDraft = it.take(800) },
                    minLines = 3,
                    maxLines = 6,
                    label = { Text(localizedText(lang, "Açıklama", "Caption")) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        savingCap = true
                        scope.launch {
                            SocialFeedService.editPostCaption(context, post, capDraft).onSuccess { updated ->
                                posts = posts.map { if (it.postId == updated.postId) updated else it }
                                editingCaptionPost = null
                                Toast.makeText(context, localizedText(lang, "Yazı güncellendi", "Caption updated"), Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "Düzenlenemedi", Toast.LENGTH_SHORT).show()
                            }
                            savingCap = false
                        }
                    },
                    enabled = !savingCap,
                    colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                ) { Text(localizedText(lang, "Kaydet", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { if (!savingCap) editingCaptionPost = null }) {
                    Text(strings.close, color = primaryInk)
                }
            }
        )
    }

    fullscreenPhotoDataUrl?.let { raw ->
        val bmp = remember(raw) { SocialProfileService.decodeDataUrlToBitmap(raw)?.asImageBitmap() }
        Dialog(
            onDismissRequest = { fullscreenPhotoDataUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .clickable { fullscreenPhotoDataUrl = null },
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
                }
                IconButton(onClick = { fullscreenPhotoDataUrl = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Kapat", tint = Color.White)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.tabSocial,
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryInk
                    )
                    Text(
                        text = localizedText(lang, "Kombinler, yeni parçalar & stil akışı", "Outfits, new pieces & style stream"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = DressKitStyle.secondarySurface(),
                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenDiscover)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = primaryInk, modifier = Modifier.size(16.dp))
                            Text(localizedText(lang, "Keşfet", "Discover"), color = primaryInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    ActivityHeartIconButton(
                        unreadCount = pendingRequestsCount + unreadActivityCount,
                        onClick = onOpenActivity
                    )
                    ChatInboxIconButton(
                        unreadCount = unreadDmCount,
                        onClick = onOpenDmInbox
                    )
                    ProfileIconButton(
                        avatarBase64 = profileAvatarBase64,
                        pendingCount = pendingRequestsCount,
                        onClick = onOpenProfile
                    )
                }
            }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PostFilterMode.entries.forEach { mode ->
                FilterChipPill(
                    label = localizedText(lang, mode.labelTr, mode.labelEn),
                    selected = filterMode == mode,
                    onClick = { filterMode = mode }
                )
            }
            IconButton(onClick = { scope.launch { reloadFeed() } }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Yenile", tint = secondaryInk, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
        ) {
            if (loading && displayedPosts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DressKitStyle.accentColor())
                }
            } else if (displayedPosts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = DressKitStyle.cardSurface(),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = localizedText(lang, "Bu akışta henüz gönderi yok", "No posts in this feed yet"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = primaryInk
                        )
                        Text(
                            text = localizedText(lang, "İlk kombini, fotoğrafı veya videoyu sen paylaş!", "Share the first outfit, look, photo or video!"),
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryInk
                        )
                        Button(
                            onClick = {
                                if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                                    usernameGateReason = localizedText(lang, "Gönderi paylaşmak için önce kullanıcı adı oluşturmalısın.", "Create a username before posting.")
                                } else {
                                    showCreateDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                        ) {
                            Text(localizedText(lang, "İlk Gönderini Paylaş", "Share First Post"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .bouncyOverscroll(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(displayedPosts, key = { it.postId }) { post ->
                        SocialPostCard(
                            post = post,
                            isMine = post.ownerUid == myUid || post.ownerUid == AnalyticsService.getCurrentUid(context),
                            likeCount = likeCounts[post.postId] ?: 0,
                            isLiked = likedByMe[post.postId] == true,
                            onOpenAuthor = { onOpenOtherProfile(post.ownerUid) },
                            onZoomAvatar = { zoomAvatarTarget = Triple(post.ownerAvatar, post.ownerDisplayName, post.ownerUsername) },
                            onToggleLike = {
                                if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                                    usernameGateReason = localizedText(lang, "Beğenmek için önce kullanıcı adı oluşturmalısın.", "Create a username before liking posts.")
                                    return@SocialPostCard
                                }
                                val wasLiked = likedByMe[post.postId] == true
                                val prevCnt = likeCounts[post.postId] ?: 0
                                likedByMe[post.postId] = !wasLiked
                                likeCounts[post.postId] = (prevCnt + if (wasLiked) -1 else 1).coerceAtLeast(0)
                                scope.launch {
                                    SocialFeedService.togglePostLike(context, currentProfile, post).onFailure { err ->
                                        likedByMe[post.postId] = wasLiked
                                        likeCounts[post.postId] = prevCnt
                                        if (err.message?.startsWith("USERNAME_REQUIRED:") == true) {
                                            usernameGateReason = localizedText(lang, "Beğenmek için önce kullanıcı adı oluşturmalısın.", "Create a username before liking posts.")
                                        }
                                    }
                                }
                            },
                            onOpenComments = { activeCommentsPost = post },
                            onSharePost = {
                                clipboard.setText(AnnotatedString(post.shareUrl))
                                Toast.makeText(context, localizedText(lang, "Gönderi linki kopyalandı", "Post link copied"), Toast.LENGTH_SHORT).show()
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, post.shareUrl)
                                }
                                runCatching { context.startActivity(Intent.createChooser(sendIntent, "Paylaş")) }
                            },
                            onReplyInDm = {
                                if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                                    usernameGateReason = localizedText(lang, "Özel mesajla yanıtlamak için önce kullanıcı adı oluşturmalısın.", "Create a username before replying in chat.")
                                } else {
                                    onReplyPostInDm(post)
                                }
                            },
                            onForwardInDm = {
                                if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                                    usernameGateReason = localizedText(lang, "Sohbette göndermek için önce kullanıcı adı oluşturmalısın.", "Create a username before sharing in chat.")
                                } else {
                                    onForwardPostInDm(post)
                                }
                            },
                            onEditCaption = { editingCaptionPost = post },
                            onDeletePost = {
                                scope.launch {
                                    SocialFeedService.deletePost(context, post).onSuccess {
                                        posts = posts.filterNot { it.postId == post.postId }
                                        Toast.makeText(context, localizedText(lang, "Gönderi silindi", "Post deleted"), Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, it.message ?: "Silinemedi", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onInspectPhoto = { url -> fullscreenPhotoDataUrl = url },
                            onImportAttachedPiece = { asWishlist ->
                                val previewPhoto = post.photos.firstOrNull().orEmpty().ifBlank { post.posterBase64 }
                                scope.launch {
                                    SocialProfileService.importRemoteGarmentLocally(
                                        context = context,
                                        repository = repository,
                                        name = post.attachedItemName,
                                        categoryStr = post.attachedCategory,
                                        subCategoryStr = "",
                                        styleTagStr = "CASUAL",
                                        seasonStrs = listOf("ALL"),
                                        colorStrs = listOf("BLACK"),
                                        notes = "@${post.ownerUsername.ifBlank { post.ownerDisplayName }} gönderisinden eklendi",
                                        priceText = post.attachedPriceText,
                                        productUrl = post.attachedProductUrl,
                                        photosBase64 = listOfNotNull(previewPhoto.takeIf { it.startsWith("data:image/") }),
                                        asWishlist = asWishlist
                                    )
                                    Toast.makeText(
                                        context,
                                        if (asWishlist) localizedText(lang, "İstek listene eklendi", "Added to wishlist")
                                        else localizedText(lang, "Dolabına eklendi", "Added to closet"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 92.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable {
                    if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                        usernameGateReason = localizedText(lang, "Gönderi paylaşmak için önce kullanıcı adı oluşturmalısın.", "Create a username before posting.")
                    } else {
                        showCreateDialog = true
                    }
                },
            shape = RoundedCornerShape(999.dp),
            color = DressKitStyle.buttonBg(),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = DressKitStyle.buttonFg(), modifier = Modifier.size(19.dp))
                Text(
                    text = localizedText(lang, "Paylaş", "Post"),
                    color = DressKitStyle.buttonFg(),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SocialPostCard(
    post: SocialPostData,
    isMine: Boolean,
    likeCount: Int,
    isLiked: Boolean,
    onOpenAuthor: () -> Unit,
    onZoomAvatar: () -> Unit = {},
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onSharePost: () -> Unit,
    onReplyInDm: () -> Unit = {},
    onForwardInDm: () -> Unit = {},
    onEditCaption: () -> Unit,
    onDeletePost: () -> Unit,
    onInspectPhoto: (String) -> Unit,
    onImportAttachedPiece: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val authorLabel = post.ownerDisplayName.ifBlank { post.ownerUsername.ifBlank { "Dolabım Kullanıcısı" } }
    var menuExpanded by remember { mutableStateOf(false) }

    var playingVideoFile by remember { mutableStateOf<File?>(null) }
    var loadingVideo by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DressKitStyle.cardSurface(),
        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = onOpenAuthor,
                                onLongClick = onZoomAvatar
                            )
                    ) {
                        UserAvatarCircle(avatarBase64 = post.ownerAvatar, displayName = authorLabel, size = 38.dp)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenAuthor)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(authorLabel, color = primaryInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (post.isPrivatePost) {
                                Icon(Icons.Outlined.Lock, contentDescription = "Takipçilere Özel", tint = secondaryInk, modifier = Modifier.size(13.dp))
                            }
                        }
                        Text(
                            text = buildString {
                                if (post.ownerUsername.isNotBlank()) append("@${post.ownerUsername} · ")
                                append(fmtSocialRel(post.createdAtMs))
                                if (post.editedAtMs > 0L) append(" · düzenlendi")
                            },
                            color = secondaryInk,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = DressKitStyle.secondarySurface()
                    ) {
                        Text(
                            text = SocialPostKind.fromKey(post.postKind).display(lang),
                            color = primaryInk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "İşlemler", tint = secondaryInk, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (!isMine) {
                                DropdownMenuItem(
                                    text = { Text(localizedText(lang, "Özel Sohbetten Yanıtla", "Reply in Chat")) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onReplyInDm()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(localizedText(lang, "Sohbette Gönder", "Send in Chat")) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onForwardInDm()
                                }
                            )
                            if (isMine) {
                                val canEdit = post.canEditCaption()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (canEdit) localizedText(lang, "Yazıyı Düzenle (${post.editRemainingMinutes()} dk)", "Edit Caption")
                                            else localizedText(lang, "Düzenleme süresi doldu (15 dk)", "Edit expired (15m)")
                                        )
                                    },
                                    enabled = canEdit,
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEditCaption()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(localizedText(lang, "Gönderiyi Sil", "Delete Post"), color = Color(0xFFF43F5E)) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Color(0xFFF43F5E)) },
                                    onClick = {
                                        menuExpanded = false
                                        onDeletePost()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (post.mediaType == "photo" && post.photos.isNotEmpty()) {
                val photoPager = rememberPagerState(pageCount = { post.photos.size })
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 210.dp, max = 340.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DressKitStyle.secondarySurface())
                ) {
                    HorizontalPager(state = photoPager, modifier = Modifier.fillMaxWidth()) { page ->
                        val url = post.photos[page]
                        val bmp = remember(url) { SocialProfileService.decodeDataUrlToBitmap(url)?.asImageBitmap() }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(290.dp)
                                    .clickable { onInspectPhoto(url) }
                            )
                        }
                    }
                    if (post.photos.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.62f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "${photoPager.currentPage + 1}/${post.photos.size}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            } else if (post.mediaType == "video") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val vFile = playingVideoFile
                    if (vFile != null && vFile.exists()) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(vFile.absolutePath)
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clickable { playingVideoFile = null }
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Videoyu durdur", tint = Color.White, modifier = Modifier.padding(6.dp).size(16.dp))
                        }
                    } else {
                        val posterBmp = remember(post.posterBase64) {
                            if (post.posterBase64.isNotBlank()) SocialProfileService.decodeDataUrlToBitmap(post.posterBase64)?.asImageBitmap()
                            else null
                        }
                        if (posterBmp != null) {
                            Image(bitmap = posterBmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.34f))
                                .clickable(enabled = !loadingVideo) {
                                    loadingVideo = true
                                    videoError = ""
                                    scope.launch {
                                        SocialFeedService.loadPostVideoFile(context, post).onSuccess {
                                            playingVideoFile = it
                                        }.onFailure {
                                            videoError = it.message ?: "Video yüklenemedi"
                                        }
                                        loadingVideo = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (loadingVideo) {
                                CircularProgressIndicator(color = Color.White)
                            } else {
                                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.65f)) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Oynat", tint = Color.White, modifier = Modifier.padding(14.dp).size(32.dp))
                                }
                            }
                        }
                        if (post.videoDurationSec > 0) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.Black.copy(alpha = 0.68f),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                            ) {
                                Text("${post.videoDurationSec} sn", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
                if (videoError.isNotBlank()) {
                    Text(videoError, color = Color(0xFFF43F5E), fontSize = 11.sp)
                }
            }

            if (post.caption.isNotBlank()) {
                Text(post.caption, color = primaryInk, style = MaterialTheme.typography.bodyMedium)
            }

            if (post.attachedItemName.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DressKitStyle.secondarySurface(),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Checkroom, contentDescription = null, tint = DressKitStyle.accentColor(), modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(post.attachedItemName, color = primaryInk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (post.attachedPriceText.isNotBlank()) {
                                Text(post.attachedPriceText, color = secondaryInk, fontSize = 11.sp)
                            }
                        }
                        TextButton(onClick = { onImportAttachedPiece(true) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = DressKitStyle.accentColor(), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(localizedText(lang, "İstek", "Save"), color = DressKitStyle.accentColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.clickable(onClick = onToggleLike),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Beğen",
                            tint = if (isLiked) Color(0xFFF43F5E) else primaryInk,
                            modifier = Modifier.size(21.dp)
                        )
                        Text("$likeCount", color = primaryInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.clickable(onClick = onOpenComments),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Yorumlar", tint = primaryInk, modifier = Modifier.size(20.dp))
                        Text(localizedText(lang, "Yorum", "Comment"), color = primaryInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                IconButton(onClick = onSharePost, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Share, contentDescription = "Paylaş", tint = secondaryInk, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
fun CreatePostDialog(
    items: List<ClothingItem>,
    myProfile: LocalProfileConfig,
    onOpenProfileSetup: () -> Unit,
    onCreated: (SocialPostData) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var kind by remember { mutableStateOf(SocialPostKind.OUTFIT) }
    var caption by remember { mutableStateOf("") }
    var photoDataUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVideoFile by remember { mutableStateOf<File?>(null) }
    var attachedItem by remember { mutableStateOf<ClothingItem?>(null) }
    var showWardrobePicker by remember { mutableStateOf(false) }
    var editingPhotoIdx by remember { mutableStateOf<Int?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var videoCaptureFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = captureUri
        if (ok && uri != null) {
            scope.launch {
                val url = withContext(Dispatchers.IO) { readUriAsJpegDataUrl(context, uri) }
                if (url != null) {
                    selectedVideoFile = null
                    photoDataUrls = (photoDataUrls + url).take(4)
                }
            }
        }
    }

    val galleryPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    uris.take(4).mapNotNull { readUriAsJpegDataUrl(context, it) }
                }
                if (decoded.isNotEmpty()) {
                    selectedVideoFile = null
                    photoDataUrls = (photoDataUrls + decoded).take(4)
                }
            }
        }
    }

    val videoRecordLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val f = videoCaptureFile
        if (ok && f != null && f.exists() && f.length() > 0L) {
            photoDataUrls = emptyList()
            selectedVideoFile = f
            kind = SocialPostKind.VIDEO
        }
    }

    val videoPickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val copied = withContext(Dispatchers.IO) { copyVideoUriToTemp(context, uri) }
                if (copied == null) {
                    Toast.makeText(context, "Video çok büyük (maks 7.5 MB) veya okunamadı", Toast.LENGTH_SHORT).show()
                } else {
                    photoDataUrls = emptyList()
                    selectedVideoFile = copied
                    kind = SocialPostKind.VIDEO
                }
            }
        }
    }

    editingPhotoIdx?.let { idx ->
        val src = photoDataUrls.getOrNull(idx)
        if (src != null) {
            PostPhotoEditorDialog(
                sourceDataUrl = src,
                onSaveEdited = { edited ->
                    photoDataUrls = photoDataUrls.toMutableList().apply { set(idx, edited) }
                    editingPhotoIdx = null
                },
                onDismiss = { editingPhotoIdx = null }
            )
        }
    }

    if (showWardrobePicker) {
        AlertDialog(
            onDismissRequest = { showWardrobePicker = false },
            containerColor = DressKitStyle.cardSurface(),
            title = { Text(localizedText(lang, "Dolabından Parça Ekle", "Attach Garment"), color = primaryInk) },
            text = {
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.take(40).forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DressKitStyle.secondarySurface(),
                            modifier = Modifier.fillMaxWidth().clickable {
                                attachedItem = item
                                showWardrobePicker = false
                            }
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.name.ifBlank { item.subCategory.label }, color = primaryInk, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (item.priceText.isNotBlank()) Text(item.priceText, color = secondaryInk, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWardrobePicker = false }) { Text("Kapat", color = primaryInk) }
            }
        )
    }

    Dialog(
        onDismissRequest = { if (!publishing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = DressKitStyle.canvasBg()
        ) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(localizedText(lang, "Yeni Gönderi", "New Post"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = primaryInk)
                    IconButton(onClick = { if (!publishing) onDismiss() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Kapat", tint = primaryInk)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (myProfile.username.isBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DressKitStyle.secondarySurface(),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenProfileSetup)
                        ) {
                            Text(
                                text = localizedText(lang, "Kullanıcı adın yok — dokunup profilini & fotoğrafını ayarla.", "No username set — tap to configure profile."),
                                color = DressKitStyle.accentColor(),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Text(localizedText(lang, "Gönderi Türü", "Post Type"), color = secondaryInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SocialPostKind.entries.forEach { k ->
                            FilterChipPill(
                                label = k.display(lang),
                                selected = kind == k,
                                onClick = { kind = k }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it.take(800) },
                        minLines = 3,
                        maxLines = 6,
                        label = { Text(localizedText(lang, "Kombinin veya parçan hakkında yaz...", "Write about your outfit or piece...")) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val f = File(File(context.cacheDir, "social_captures").apply { mkdirs() }, "cam_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                captureUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(localizedText(lang, "Foto Çek", "Camera"), fontSize = 11.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { galleryPhotoLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(localizedText(lang, "Galeri", "Gallery"), fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val f = File(File(context.cacheDir, "social_captures").apply { mkdirs() }, "vid_${System.currentTimeMillis()}.mp4")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                videoCaptureFile = f
                                videoRecordLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(localizedText(lang, "Video Çek", "Record Video"), fontSize = 11.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { videoPickLauncher.launch("video/*") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(localizedText(lang, "Video Yükle", "Upload Video"), fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    if (photoDataUrls.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            photoDataUrls.forEachIndexed { idx, dataUrl ->
                                val bmp = remember(dataUrl) { SocialProfileService.decodeDataUrlToBitmap(dataUrl)?.asImageBitmap() }
                                Surface(shape = RoundedCornerShape(12.dp), color = DressKitStyle.cardSurface(), border = BorderStroke(1.dp, DressKitStyle.borderColor())) {
                                    Column(modifier = Modifier.width(120.dp).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                text = localizedText(lang, "Düzenle", "Edit"),
                                                color = DressKitStyle.accentColor(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { editingPhotoIdx = idx }
                                            )
                                            Text(
                                                text = localizedText(lang, "Kaldır", "Remove"),
                                                color = Color(0xFFF43F5E),
                                                fontSize = 11.sp,
                                                modifier = Modifier.clickable {
                                                    photoDataUrls = photoDataUrls.filterIndexed { i, _ -> i != idx }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    selectedVideoFile?.let { vf ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DressKitStyle.cardSurface(),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Video hazır (${(vf.length() / 1024L)} KB)", color = primaryInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                IconButton(onClick = { selectedVideoFile = null }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Kaldır", tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showWardrobePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Checkroom, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = attachedItem?.let { "Ekli Parça: ${it.name.ifBlank { it.subCategory.label }}" }
                                ?: localizedText(lang, "Dolabımdan Kıyafet Ekle (Opsiyonel)", "Attach Garment (Optional)"),
                            fontSize = 12.sp
                        )
                    }
                    if (attachedItem != null) {
                        TextButton(onClick = { attachedItem = null }) {
                            Text("Ekli kıyafeti kaldır", color = Color(0xFFF43F5E), fontSize = 11.sp)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DressKitStyle.secondarySurface(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (myProfile.isPrivate) localizedText(lang, "Hesabın gizli: Bu gönderiyi yalnızca onayladığın takipçilerin görebilir.", "Private account: Only accepted followers can view this post.")
                            else localizedText(lang, "Hesabın herkese açık: Tüm kullanıcılar görebilir.", "Public account: Visible to everyone."),
                            color = secondaryInk,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = {
                            val mType = when {
                                selectedVideoFile != null -> "video"
                                photoDataUrls.isNotEmpty() -> "photo"
                                else -> "text"
                            }
                            if (mType == "text" && caption.trim().isEmpty()) {
                                Toast.makeText(context, "Yazı, fotoğraf veya video eklemelisin", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            publishing = true
                            scope.launch {
                                SocialFeedService.createPost(
                                    context = context,
                                    myProfile = myProfile,
                                    postKind = kind.key,
                                    caption = caption,
                                    mediaType = mType,
                                    photoDataUrls = photoDataUrls,
                                    videoFile = selectedVideoFile,
                                    attachedItem = attachedItem
                                ).onSuccess { created ->
                                    publishing = false
                                    onCreated(created)
                                }.onFailure { err ->
                                    publishing = false
                                    Toast.makeText(context, err.message ?: "Paylaşılamadı", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !publishing,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                    ) {
                        if (publishing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DressKitStyle.buttonFg())
                        } else {
                            Text(localizedText(lang, "Paylaş", "Publish"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostPhotoEditorDialog(
    sourceDataUrl: String,
    onSaveEdited: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var rotDeg by remember { mutableIntStateOf(0) }
    var aspectMode by remember { mutableStateOf("orig") } // "orig" | "1:1" | "4:5" | "16:9"
    var filterPreset by remember { mutableStateOf(PostPhotoFilterPreset.ORIGINAL) }

    val baseBitmap = remember(sourceDataUrl) { SocialProfileService.decodeDataUrlToBitmap(sourceDataUrl) }
    val androidMatrix = remember(filterPreset) { buildFilterColorMatrix(filterPreset) }
    val composeFilter = remember(androidMatrix) { ColorFilter.colorMatrix(ComposeColorMatrix(androidMatrix.array)) }
    val transformedBmp = remember(baseBitmap, rotDeg, aspectMode) {
        if (baseBitmap == null) null else applyRotateAndAspect(baseBitmap, rotDeg, aspectMode)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = DressKitStyle.canvasBg()
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(localizedText(lang, "Fotoğraf Düzenleyici", "Photo Editor"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = primaryInk)
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Kapat", tint = primaryInk) }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    transformedBmp?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = composeFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { rotDeg = (rotDeg + 90) % 360 }) {
                        Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("90°", fontSize = 12.sp)
                    }
                    listOf("orig" to "Orijinal", "1:1" to "1:1", "4:5" to "4:5", "16:9" to "16:9").forEach { (key, lbl) ->
                        FilterChipPill(label = lbl, selected = aspectMode == key, onClick = { aspectMode = key })
                    }
                }

                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PostPhotoFilterPreset.entries.forEach { f ->
                        FilterChipPill(
                            label = localizedText(lang, f.labelTr, f.labelEn),
                            selected = filterPreset == f,
                            onClick = { filterPreset = f }
                        )
                    }
                }

                Button(
                    onClick = {
                        val bmp = transformedBmp ?: return@Button
                        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(out)
                        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(androidMatrix) }
                        canvas.drawBitmap(bmp, 0f, 0f, paint)
                        val baos = ByteArrayOutputStream()
                        out.compress(Bitmap.CompressFormat.JPEG, 86, baos)
                        out.recycle()
                        val dataUrl = "data:image/jpeg;base64,${Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)}"
                        onSaveEdited(dataUrl)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                ) {
                    Text(localizedText(lang, "Düzenlemeyi Uygula", "Apply Edits"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCommentsDialog(
    post: SocialPostData,
    myProfile: LocalProfileConfig,
    allItems: List<ClothingItem> = emptyList(),
    allOutfits: List<Outfit> = emptyList(),
    onProfileUpdated: (LocalProfileConfig) -> Unit = {},
    onOpenProfileUid: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val myUid = remember { AnalyticsService.getCurrentUid(context) }

    var currentProfile by remember(myProfile) { mutableStateOf(myProfile) }
    var zoomAvatarTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showUsernameGate by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<PostCommentData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var textInput by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf("none") } // "none" | "image" | "gif"
    var mediaBase64 by remember { mutableStateOf("") }
    var mediaUrl by remember { mutableStateOf("") }
    var showGifDrawer by remember { mutableStateOf(false) }
    var externalGifInput by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    zoomAvatarTarget?.let { (av, dn, un) ->
        AvatarZoomDialog(avatarBase64 = av, displayName = dn, username = un, onDismiss = { zoomAvatarTarget = null })
    }

    if (showUsernameGate) {
        UsernameRequiredDialog(
            myProfile = currentProfile,
            allItems = allItems,
            allOutfits = allOutfits,
            reasonText = localizedText(lang, "Yorum yapmak için önce kullanıcı adı oluşturmalısın.", "Create a username before commenting."),
            onSaved = { saved ->
                currentProfile = saved
                onProfileUpdated(saved)
            },
            onDismiss = { showUsernameGate = false }
        )
    }

    val gifPresets = remember { AnimatedGifGenerator.presets }

    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = captureUri
        if (ok && uri != null) {
            scope.launch {
                val url = withContext(Dispatchers.IO) { readUriAsJpegDataUrl(context, uri) }
                if (url != null) {
                    mediaType = "image"
                    mediaBase64 = url
                    mediaUrl = ""
                    showGifDrawer = false
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val (mType, dataUrl) = withContext(Dispatchers.IO) { readCommentGalleryMedia(context, uri) }
                if (dataUrl != null) {
                    mediaType = mType
                    mediaBase64 = dataUrl
                    mediaUrl = ""
                    showGifDrawer = false
                } else {
                    Toast.makeText(context, "Görsel/GIF boyutu çok büyük veya desteklenmiyor", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(post.postId) {
        loading = true
        SocialFeedService.fetchComments(context, post).onSuccess { comments = it }
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = DressKitStyle.canvasBg()
        ) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(localizedText(lang, "Yorumlar (${comments.size})", "Comments (${comments.size})"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = primaryInk)
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Kapat", tint = primaryInk) }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DressKitStyle.accentColor())
                    } else if (comments.isEmpty()) {
                        Text(
                            text = localizedText(lang, "İlk yorumu sen yaz, fotoğraf veya GIF gönder!", "Write the first comment or send a photo/GIF!"),
                            color = secondaryInk,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(comments, key = { it.commentId }) { c ->
                                val cAuthor = c.authorDisplayName.ifBlank { c.authorUsername.ifBlank { "Kullanıcı" } }
                                val canDel = c.authorUid == myUid || post.ownerUid == myUid
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .combinedClickable(
                                                    onClick = { onOpenProfileUid(c.authorUid) },
                                                    onLongClick = { zoomAvatarTarget = Triple(c.authorAvatar, cAuthor, c.authorUsername) }
                                                )
                                        ) {
                                            UserAvatarCircle(avatarBase64 = c.authorAvatar, displayName = cAuthor, size = 34.dp)
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (c.authorUsername.isNotBlank()) "@${c.authorUsername}" else cAuthor,
                                                    color = primaryInk,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.clickable { onOpenProfileUid(c.authorUid) }
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(fmtSocialRel(c.createdAtMs), color = secondaryInk, fontSize = 10.sp)
                                                    if (canDel) {
                                                        Icon(
                                                            Icons.Outlined.DeleteOutline,
                                                            contentDescription = "Sil",
                                                            tint = Color(0xFFF43F5E),
                                                            modifier = Modifier.size(16.dp).clickable {
                                                                scope.launch {
                                                                    SocialFeedService.deleteComment(context, post, c.commentId).onSuccess {
                                                                        comments = comments.filterNot { it.commentId == c.commentId }
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            if (c.text.isNotBlank()) {
                                                Text(c.text, color = primaryInk, fontSize = 13.sp)
                                            }
                                            if (c.mediaBase64.isNotBlank() || c.mediaUrl.isNotBlank()) {
                                                CommentMediaPreview(
                                                    dataUrl = c.mediaBase64,
                                                    remoteUrl = c.mediaUrl,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 96.dp, max = 180.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showGifDrawer) {
                    Surface(
                        color = DressKitStyle.cardSurface(),
                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = externalGifInput,
                                    onValueChange = { externalGifInput = it },
                                    singleLine = true,
                                    placeholder = { Text("Tenor / Giphy HTTPS link...", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (AnimatedGifGenerator.isSafeExternalGifUrl(externalGifInput)) {
                                            mediaType = "gif"
                                            mediaBase64 = ""
                                            mediaUrl = externalGifInput.trim()
                                            externalGifInput = ""
                                            showGifDrawer = false
                                        } else {
                                            Toast.makeText(context, "Sadece Tenor / Giphy HTTPS bağlantısı", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) { Text("Ekle", fontSize = 11.sp) }
                            }
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                gifPresets.forEach { preset ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(74.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DressKitStyle.secondarySurface())
                                            .clickable {
                                                mediaType = "gif"
                                                mediaBase64 = preset.dataUrl
                                                mediaUrl = ""
                                                showGifDrawer = false
                                            }
                                            .padding(4.dp)
                                    ) {
                                        CommentMediaPreview(
                                            dataUrl = preset.dataUrl,
                                            remoteUrl = "",
                                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                        Text(preset.display(lang), color = primaryInk, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }

                if (mediaBase64.isNotBlank() || mediaUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CommentMediaPreview(
                            dataUrl = mediaBase64,
                            remoteUrl = mediaUrl,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = if (mediaType == "gif") "GIF eklendi" else "Fotoğraf eklendi",
                            color = DressKitStyle.accentColor(),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { mediaType = "none"; mediaBase64 = ""; mediaUrl = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Kaldır", tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Surface(color = DressKitStyle.cardSurface(), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = {
                            val f = File(File(context.cacheDir, "social_captures").apply { mkdirs() }, "c_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                            captureUri = uri
                            camLauncher.launch(uri)
                        }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = "Kamera", tint = primaryInk, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Galeri", tint = primaryInk, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { showGifDrawer = !showGifDrawer }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Gif, contentDescription = "GIF", tint = DressKitStyle.accentColor(), modifier = Modifier.size(24.dp))
                        }
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it.take(500) },
                            placeholder = { Text(localizedText(lang, "Yorum yaz...", "Add comment..."), fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (sending) return@IconButton
                                if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                                    showUsernameGate = true
                                    return@IconButton
                                }
                                sending = true
                                scope.launch {
                                    SocialFeedService.addComment(
                                        context = context,
                                        myProfile = currentProfile,
                                        post = post,
                                        text = textInput,
                                        mediaType = mediaType,
                                        mediaBase64 = mediaBase64,
                                        mediaUrl = mediaUrl
                                    ).onSuccess { created ->
                                        comments = comments + created
                                        textInput = ""
                                        mediaType = "none"
                                        mediaBase64 = ""
                                        mediaUrl = ""
                                    }.onFailure { err ->
                                        if (err.message?.startsWith("USERNAME_REQUIRED:") == true) showUsernameGate = true
                                        else Toast.makeText(context, err.message ?: "Gönderilemedi", Toast.LENGTH_SHORT).show()
                                    }
                                    sending = false
                                }
                            },
                            enabled = !sending
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Gönder", tint = DressKitStyle.accentColor())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SinglePostDeepLinkDialog(
    postId: String,
    ownerUid: String,
    myProfile: LocalProfileConfig,
    items: List<ClothingItem>,
    outfits: List<Outfit> = emptyList(),
    repository: WardrobeRepository,
    onProfileUpdated: (LocalProfileConfig) -> Unit = {},
    onReplyPostInDm: (SocialPostData) -> Unit = {},
    onForwardPostInDm: (SocialPostData) -> Unit = {},
    onOpenOtherProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    var currentProfile by remember(myProfile) { mutableStateOf(myProfile) }
    var post by remember { mutableStateOf<SocialPostData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var likeCnt by remember { mutableIntStateOf(0) }
    var isLiked by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var zoomAvatarTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var usernameGateReason by remember { mutableStateOf<String?>(null) }

    zoomAvatarTarget?.let { (av, dn, un) ->
        AvatarZoomDialog(avatarBase64 = av, displayName = dn, username = un, onDismiss = { zoomAvatarTarget = null })
    }

    usernameGateReason?.let { reason ->
        UsernameRequiredDialog(
            myProfile = currentProfile,
            allItems = items,
            allOutfits = outfits,
            reasonText = reason,
            onSaved = { saved ->
                currentProfile = saved
                onProfileUpdated(saved)
            },
            onDismiss = { usernameGateReason = null }
        )
    }

    LaunchedEffect(postId, ownerUid) {
        loading = true
        post = SocialFeedService.fetchSinglePost(context, postId, ownerUid).getOrNull()
        post?.let { p ->
            SocialFeedService.fetchPostLikeState(context, p.postId).onSuccess { (cnt, m) ->
                likeCnt = cnt
                isLiked = m
            }
        }
        loading = false
    }

    val loadedPost = post
    if (showComments && loadedPost != null) {
        PostCommentsDialog(
            post = loadedPost,
            myProfile = currentProfile,
            allItems = items,
            allOutfits = outfits,
            onProfileUpdated = { saved ->
                currentProfile = saved
                onProfileUpdated(saved)
            },
            onOpenProfileUid = {
                showComments = false
                onDismiss()
                onOpenOtherProfile(it)
            },
            onDismiss = { showComments = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = DressKitStyle.canvasBg()
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Paylaşılan Gönderi", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = primaryInk)
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Kapat", tint = primaryInk) }
                }
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DressKitStyle.accentColor())
                    }
                } else if (loadedPost == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Bu gönderi bulunamadı veya gizli bir hesaba ait.", color = DressKitStyle.secondaryInk())
                    }
                } else {
                    SocialPostCard(
                        post = loadedPost,
                        isMine = loadedPost.ownerUid == AnalyticsService.getCurrentUid(context),
                        likeCount = likeCnt,
                        isLiked = isLiked,
                        onOpenAuthor = { onDismiss(); onOpenOtherProfile(loadedPost.ownerUid) },
                        onZoomAvatar = { zoomAvatarTarget = Triple(loadedPost.ownerAvatar, loadedPost.ownerDisplayName, loadedPost.ownerUsername) },
                        onToggleLike = {
                            if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                                usernameGateReason = localizedText(lang, "Beğenmek için önce kullanıcı adı oluşturmalısın.", "Create a username before liking posts.")
                                return@SocialPostCard
                            }
                            scope.launch {
                                SocialFeedService.togglePostLike(context, currentProfile, loadedPost).onSuccess { likedNow ->
                                    isLiked = likedNow
                                    likeCnt = (likeCnt + if (likedNow) 1 else -1).coerceAtLeast(0)
                                }.onFailure { err ->
                                    if (err.message?.startsWith("USERNAME_REQUIRED:") == true) {
                                        usernameGateReason = localizedText(lang, "Beğenmek için önce kullanıcı adı oluşturmalısın.", "Create a username before liking posts.")
                                    }
                                }
                            }
                        },
                        onOpenComments = { showComments = true },
                        onSharePost = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, loadedPost.shareUrl)
                            }
                            runCatching { context.startActivity(Intent.createChooser(sendIntent, "Paylaş")) }
                        },
                        onReplyInDm = {
                            onDismiss()
                            onReplyPostInDm(loadedPost)
                        },
                        onForwardInDm = {
                            onDismiss()
                            onForwardPostInDm(loadedPost)
                        },
                        onEditCaption = {},
                        onDeletePost = {
                            scope.launch {
                                SocialFeedService.deletePost(context, loadedPost).onSuccess { onDismiss() }
                            }
                        },
                        onInspectPhoto = {},
                        onImportAttachedPiece = { asWishlist ->
                            val previewPhoto = loadedPost.photos.firstOrNull().orEmpty().ifBlank { loadedPost.posterBase64 }
                            scope.launch {
                                SocialProfileService.importRemoteGarmentLocally(
                                    context = context,
                                    repository = repository,
                                    name = loadedPost.attachedItemName,
                                    categoryStr = loadedPost.attachedCategory,
                                    subCategoryStr = "",
                                    styleTagStr = "CASUAL",
                                    seasonStrs = listOf("ALL"),
                                    colorStrs = listOf("BLACK"),
                                    notes = "@${loadedPost.ownerUsername.ifBlank { loadedPost.ownerDisplayName }} gönderisinden eklendi",
                                    priceText = loadedPost.attachedPriceText,
                                    productUrl = loadedPost.attachedProductUrl,
                                    photosBase64 = listOfNotNull(previewPhoto.takeIf { it.startsWith("data:image/") }),
                                    asWishlist = asWishlist
                                )
                                Toast.makeText(
                                    context,
                                    if (asWishlist) "İstek listene eklendi" else "Dolabına eklendi",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CommentMediaPreview(
    dataUrl: String,
    remoteUrl: String,
    modifier: Modifier = Modifier
) {
    if (remoteUrl.isNotBlank()) {
        AsyncImage(model = remoteUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        return
    }
    if (dataUrl.startsWith("data:image/gif;base64,") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val rawBytes = remember(dataUrl) {
            runCatching { Base64.decode(dataUrl.substringAfter("base64,"), Base64.DEFAULT) }.getOrNull()
        }
        if (rawBytes != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                },
                update = { iv ->
                    runCatching {
                        val src = ImageDecoder.createSource(ByteBuffer.wrap(rawBytes))
                        val drw = ImageDecoder.decodeDrawable(src)
                        iv.setImageDrawable(drw)
                        (drw as? AnimatedImageDrawable)?.start()
                    }
                },
                modifier = modifier
            )
            return
        }
    }
    val bmp = remember(dataUrl) { SocialProfileService.decodeDataUrlToBitmap(dataUrl)?.asImageBitmap() }
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    }
}

private fun buildFilterColorMatrix(preset: PostPhotoFilterPreset): ColorMatrix {
    val cm = ColorMatrix()
    when (preset) {
        PostPhotoFilterPreset.ORIGINAL -> Unit
        PostPhotoFilterPreset.BW -> cm.setSaturation(0f)
        PostPhotoFilterPreset.VIVID -> {
            cm.setSaturation(1.38f)
            val contrast = ColorMatrix(floatArrayOf(
                1.08f, 0f, 0f, 0f, -8f,
                0f, 1.08f, 0f, 0f, -8f,
                0f, 0f, 1.08f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(contrast)
        }
        PostPhotoFilterPreset.WARM -> cm.postConcat(ColorMatrix(floatArrayOf(
            1.12f, 0f, 0f, 0f, 10f,
            0f, 1.03f, 0f, 0f, 4f,
            0f, 0f, 0.90f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f
        )))
        PostPhotoFilterPreset.COOL -> cm.postConcat(ColorMatrix(floatArrayOf(
            0.92f, 0f, 0f, 0f, -4f,
            0f, 1.02f, 0f, 0f, 2f,
            0f, 0f, 1.14f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )))
        PostPhotoFilterPreset.EDITORIAL -> {
            cm.setSaturation(0.75f)
            cm.postConcat(ColorMatrix(floatArrayOf(
                1.14f, 0f, 0f, 0f, -10f,
                0f, 1.14f, 0f, 0f, -10f,
                0f, 0f, 1.14f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
    }
    return cm
}

private fun applyRotateAndAspect(src: Bitmap, rotDeg: Int, aspectMode: String): Bitmap {
    val rotated = if (rotDeg % 360 != 0) {
        val m = Matrix().apply { postRotate(rotDeg.toFloat()) }
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    } else src
    val targetRatio = when (aspectMode) {
        "1:1" -> 1f
        "4:5" -> 0.8f
        "16:9" -> 16f / 9f
        else -> return rotated
    }
    val w = rotated.width
    val h = rotated.height
    val curRatio = w.toFloat() / h.coerceAtLeast(1)
    val (cropW, cropH) = if (curRatio > targetRatio) {
        (h * targetRatio).roundToInt().coerceIn(1, w) to h
    } else {
        w to (w / targetRatio).roundToInt().coerceIn(1, h)
    }
    val left = ((w - cropW) / 2).coerceAtLeast(0)
    val top = ((h - cropH) / 2).coerceAtLeast(0)
    return Bitmap.createBitmap(rotated, left, top, cropW, cropH)
}

private fun readUriAsJpegDataUrl(context: Context, uri: Uri): String? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val maxSide = 960
    val longest = maxOf(bmp.width, bmp.height).coerceAtLeast(1)
    val scaled = if (longest > maxSide) {
        val sc = maxSide.toFloat() / longest
        Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt().coerceAtLeast(64), (bmp.height * sc).toInt().coerceAtLeast(64), true).also {
            if (it !== bmp) bmp.recycle()
        }
    } else bmp
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 84, baos)
    scaled.recycle()
    return "data:image/jpeg;base64,${Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)}"
}

private fun readCommentGalleryMedia(context: Context, uri: Uri): Pair<String, String?> {
    val mime = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return "none" to null
    if (mime == "image/gif" || (raw.size >= 6 && String(raw, 0, 6, Charsets.US_ASCII).startsWith("GIF8"))) {
        val b64 = Base64.encodeToString(raw, Base64.NO_WRAP)
        val url = "data:image/gif;base64,$b64"
        return if (url.length <= 255_000) "gif" to url else "none" to null
    }
    val url = readUriAsJpegDataUrl(context, uri)
    return if (url != null) "image" to url else "none" to null
}

private fun copyVideoUriToTemp(context: Context, uri: Uri): File? {
    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (raw.isEmpty() || raw.size > 7_500_000) return null
    val dir = File(context.cacheDir, "social_captures").apply { mkdirs() }
    val out = File(dir, "vid_pick_${System.currentTimeMillis()}.mp4")
    out.writeBytes(raw)
    return out
}

private fun fmtSocialRel(ms: Long): String {
    if (ms <= 0L) return ""
    val diff = ((System.currentTimeMillis() - ms) / 1000L).coerceAtLeast(0L)
    return when {
        diff < 60L -> "az önce"
        diff < 3600L -> "${diff / 60L} dk"
        diff < 86400L -> "${diff / 3600L} sa"
        else -> SimpleDateFormat("dd MMM", Locale("tr", "TR")).format(Date(ms))
    }
}
