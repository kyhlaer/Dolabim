package com.dresskit.wardrobe.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dresskit.wardrobe.data.AnalyticsService
import com.dresskit.wardrobe.data.AnimatedGifGenerator
import com.dresskit.wardrobe.data.DirectMessageService
import com.dresskit.wardrobe.data.SocialProfileService
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.DmChatActivityState
import com.dresskit.wardrobe.model.DmConversation
import com.dresskit.wardrobe.model.DmGlobalPresence
import com.dresskit.wardrobe.model.DmMessage
import com.dresskit.wardrobe.model.DmThreadSummary
import com.dresskit.wardrobe.model.LocalProfileConfig
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.PublicProfileHeader
import com.dresskit.wardrobe.model.SocialPostData
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatInboxIconButton(
    unreadCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(36.dp)) {
        Surface(
            shape = CircleShape,
            color = DressKitStyle.secondarySurface(),
            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = "Sohbet",
                    tint = if (unreadCount > 0) DressKitStyle.accentColor() else DressKitStyle.primaryInk(),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .height(17.dp)
                    .widthIn(min = 17.dp)
                    .clip(CircleShape)
                    .background(DressKitStyle.accentColor())
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
fun DirectMessageInboxDialog(
    myProfile: LocalProfileConfig,
    allItems: List<ClothingItem>,
    allOutfits: List<Outfit>,
    onProfileUpdated: (LocalProfileConfig) -> Unit,
    onOpenChat: (partnerUid: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val myUid = remember { AnalyticsService.getCurrentUid(context) }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Mesajlar, 1 = İstekler
    var loading by remember { mutableStateOf(true) }
    var threads by remember { mutableStateOf<List<DmThreadSummary>>(emptyList()) }
    var followingList by remember { mutableStateOf<List<PublicProfileHeader>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PublicProfileHeader>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var currentProfile by remember(myProfile) { mutableStateOf(myProfile) }
    var usernameGateReason by remember { mutableStateOf<String?>(null) }
    var startingChat by remember { mutableStateOf(false) }

    suspend fun reloadInbox() {
        loading = true
        DirectMessageService.fetchMyConversations(context).onSuccess { threads = it }
        if (myUid.isNotBlank()) {
            SocialProfileService.fetchFollowList(context, myUid, followers = false).onSuccess {
                followingList = it
            }
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        AnalyticsService.trackFeature("dm_inbox_open")
        reloadInbox()
    }

    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) {
            searchResults = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(280)
        SocialProfileService.searchProfiles(context, q).onSuccess { list ->
            searchResults = list.filter { it.uid != myUid }
        }
        searching = false
    }

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

    val messageThreads = remember(threads) { threads.filter { it.conversation.status == "accepted" } }
    val requestThreads = remember(threads) { threads.filter { it.conversation.status == "request" } }
    val incomingReqCount = remember(requestThreads, myUid) {
        requestThreads.count { it.conversation.recipientUid == myUid }
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
                .imePadding(),
            color = DressKitStyle.canvasBg()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = primaryInk)
                    }
                    Text(
                        text = localizedText(lang, "Sohbet", "Messages"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryInk
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Segmented Messages | Requests tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to localizedText(lang, "Mesajlar (${messageThreads.size})", "Messages (${messageThreads.size})"),
                        1 to localizedText(
                            lang,
                            if (incomingReqCount > 0) "İstekler ($incomingReqCount)" else "İstekler",
                            if (incomingReqCount > 0) "Requests ($incomingReqCount)" else "Requests"
                        )
                    ).forEach { (idx, lbl) ->
                        val active = activeTab == idx
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = idx },
                            shape = RoundedCornerShape(999.dp),
                            color = if (active) DressKitStyle.buttonBg() else DressKitStyle.secondarySurface(),
                            border = BorderStroke(1.dp, if (active) DressKitStyle.buttonBg() else DressKitStyle.borderColor())
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = lbl,
                                    color = if (active) DressKitStyle.buttonFg() else primaryInk,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // New Chat search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = secondaryInk, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = null, tint = secondaryInk, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            localizedText(lang, "Yeni sohbet için @kullanıcı ara...", "Search @username to start chat..."),
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                val openTargetUser = { uid: String ->
                    if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                        usernameGateReason = localizedText(
                            lang,
                            "Mesajlaşmak için önce kullanıcı adı oluşturmalısın.",
                            "Create a username before starting chats."
                        )
                    } else if (!startingChat) {
                        startingChat = true
                        onDismiss()
                        onOpenChat(uid)
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DressKitStyle.accentColor())
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (searchQuery.trim().length >= 2) {
                            item {
                                Text(
                                    text = localizedText(lang, "Kullanıcı Sonuçları", "Search Results"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = secondaryInk
                                )
                            }
                            if (searching) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DressKitStyle.accentColor())
                                    }
                                }
                            } else if (searchResults.isEmpty()) {
                                item {
                                    Text(
                                        text = localizedText(lang, "Eşleşen kullanıcı bulunamadı.", "No matching users found."),
                                        color = secondaryInk,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            } else {
                                items(searchResults, key = { "sr_${it.uid}" }) { u ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = DressKitStyle.cardSurface(),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { openTargetUser(u.uid) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            UserAvatarCircle(u.avatarBase64, u.displayName.ifBlank { u.username }, size = 40.dp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(u.displayName.ifBlank { "@${u.username}" }, color = primaryInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                if (u.username.isNotBlank()) {
                                                    Text("@${u.username}", color = DressKitStyle.accentColor(), fontSize = 12.sp)
                                                }
                                            }
                                            Text(localizedText(lang, "Mesaj", "Chat"), color = DressKitStyle.accentColor(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        } else {
                            if (activeTab == 0 && followingList.isNotEmpty()) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = localizedText(lang, "Takip Ettiklerin", "Following"),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = secondaryInk
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            followingList.forEach { fu ->
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .width(64.dp)
                                                        .clickable { openTargetUser(fu.uid) }
                                                ) {
                                                    UserAvatarCircle(fu.avatarBase64, fu.displayName.ifBlank { fu.username }, size = 48.dp)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = fu.displayName.ifBlank { fu.username.ifBlank { "Üye" } },
                                                        color = primaryInk,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val shownList = if (activeTab == 0) messageThreads else requestThreads
                            if (shownList.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                        color = DressKitStyle.cardSurface(),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (activeTab == 0) localizedText(lang, "Henüz mesajın yok", "No messages yet")
                                                else localizedText(lang, "Bekleyen mesaj isteği yok", "No message requests"),
                                                color = primaryInk,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (activeTab == 0) {
                                                    localizedText(lang, "Takip ettiklerinle sohbet başlatabilir veya @kullanıcı adı arayabilirsin.", "Tap someone you follow or search by @username to chat.")
                                                } else {
                                                    localizedText(lang, "Seni takip etmeyen kullanıcıların ilk mesaj istekleri burada görünür.", "Message requests from non-followers appear here.")
                                                },
                                                color = secondaryInk,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(shownList, key = { it.conversation.convId }) { t ->
                                    val c = t.conversation
                                    val p = t.partner
                                    val title = p.displayName.ifBlank { if (p.username.isNotBlank()) "@${p.username}" else "Kullanıcı" }
                                    val isOnline = t.partnerPresence.isEffectivelyOnline()
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (t.isUnread) DressKitStyle.secondarySurface() else DressKitStyle.cardSurface(),
                                        border = BorderStroke(1.dp, if (t.isUnread) DressKitStyle.accentColor().copy(alpha = 0.5f) else DressKitStyle.borderColor()),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { openTargetUser(p.uid) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box {
                                                UserAvatarCircle(p.avatarBase64, title, size = 46.dp)
                                                if (isOnline && !t.isBlockedByMe && !t.hasBlockedMe) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .size(12.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF22C55E))
                                                            .border(2.dp, DressKitStyle.cardSurface(), CircleShape)
                                                    )
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Text(
                                                        text = title,
                                                        color = primaryInk,
                                                        fontWeight = if (t.isUnread) FontWeight.Bold else FontWeight.SemiBold,
                                                        fontSize = 14.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    if (t.isMuted) {
                                                        Icon(
                                                            Icons.AutoMirrored.Outlined.VolumeOff,
                                                            contentDescription = "Sessiz",
                                                            tint = secondaryInk,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                val preview = when {
                                                    c.lastMessageType == "deleted" -> localizedText(lang, "Bu mesaj silindi", "This message was deleted")
                                                    c.lastMessageText.isNotBlank() -> c.lastMessageText
                                                    c.status == "request" -> localizedText(lang, "Mesaj isteği", "Message request")
                                                    else -> localizedText(lang, "Sohbeti aç", "Open chat")
                                                }
                                                Text(
                                                    text = preview,
                                                    color = if (t.isUnread) primaryInk else secondaryInk,
                                                    fontSize = 12.sp,
                                                    fontStyle = if (c.lastMessageType == "deleted") FontStyle.Italic else FontStyle.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (c.lastMessageAtMs > 0L) {
                                                    Text(fmtDmShortRel(c.lastMessageAtMs), color = secondaryInk, fontSize = 10.5.sp)
                                                }
                                                if (t.isUnread) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(9.dp)
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DirectMessageChatDialog(
    partnerUid: String,
    myProfile: LocalProfileConfig,
    allItems: List<ClothingItem>,
    allOutfits: List<Outfit>,
    onProfileUpdated: (LocalProfileConfig) -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenSinglePost: (postId: String, ownerUid: String) -> Unit,
    onUnreadChanged: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val dangerInk = DressKitStyle.dangerColor()
    val myUid = remember { AnalyticsService.getCurrentUid(context) }

    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var conv by remember { mutableStateOf<DmConversation?>(null) }
    var partner by remember { mutableStateOf<PublicProfileHeader?>(null) }
    var presence by remember { mutableStateOf(DmGlobalPresence()) }
    var partnerActivity by remember { mutableStateOf(DmChatActivityState.IDLE) }
    var isBlockedByMe by remember { mutableStateOf(false) }
    var hasBlockedMe by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(DirectMessageService.isConversationMuted(context, partnerUid)) }
    var messages by remember { mutableStateOf<List<DmMessage>>(emptyList()) }

    var textInput by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showGifDrawer by remember { mutableStateOf(false) }
    var externalGifUrl by remember { mutableStateOf("") }
    var expandedTimeMsgId by remember { mutableStateOf<String?>(null) }
    var actionMenuMsg by remember { mutableStateOf<DmMessage?>(null) }
    var editingMsg by remember { mutableStateOf<DmMessage?>(null) }
    var editMsgDraft by remember { mutableStateOf("") }
    var fullscreenPhotoDataUrl by remember { mutableStateOf<String?>(null) }
    var zoomAvatarTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var usernameGateReason by remember { mutableStateOf<String?>(null) }
    var currentProfile by remember(myProfile) { mutableStateOf(myProfile) }

    // Audio recording state
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var recorderRef by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    var capturePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var captureVideoUri by remember { mutableStateOf<Uri?>(null) }
    var captureVideoFile by remember { mutableStateOf<File?>(null) }
    val listState = rememberLazyListState()

    var reloadNonce by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorderRef?.stop() }
            runCatching { recorderRef?.release() }
            recorderRef = null
            conv?.convId?.takeIf { it.isNotBlank() }?.let { cid ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
                    runCatching {
                        DirectMessageService.setChatActivityState(context.applicationContext, cid, DmChatActivityState.IDLE)
                    }
                }
            }
        }
    }

    suspend fun syncThread(scrollToEnd: Boolean = false) {
        val c = conv ?: return
        runCatching {
            val latestConv = DirectMessageService.refreshConversation(context, c.convId) ?: c
            val msgs = DirectMessageService.fetchMessages(context, c.convId).getOrNull()
            conv = latestConv
            if (msgs != null) {
                val grew = msgs.size > messages.size
                messages = msgs
                if (scrollToEnd || grew) {
                    if (msgs.isNotEmpty()) {
                        runCatching { listState.animateScrollToItem(msgs.lastIndex) }
                    }
                }
            }
            val pPres = DirectMessageService.fetchUserPresence(context, partnerUid)
            val pAct = DirectMessageService.fetchPartnerActivityState(context, c.convId, partnerUid)
            presence = pPres
            partnerActivity = pAct
            val marked = DirectMessageService.markConversationRead(context, latestConv)
            if (marked != null) conv = marked
            onUnreadChanged()
        }
    }

    LaunchedEffect(partnerUid, reloadNonce) {
        loading = true
        errorMsg = null
        DirectMessageService.getOrCreateConversation(context, partnerUid)
            .onSuccess { summary ->
                conv = summary.conversation
                partner = summary.partner
                presence = summary.partnerPresence
                isBlockedByMe = summary.isBlockedByMe
                hasBlockedMe = summary.hasBlockedMe
                isMuted = summary.isMuted
                val initialMsgs = DirectMessageService.fetchMessages(context, summary.conversation.convId).getOrNull()
                if (initialMsgs != null) {
                    messages = initialMsgs
                }
                loading = false
                if (messages.isNotEmpty()) {
                    runCatching { listState.scrollToItem(messages.lastIndex) }
                }
                scope.launch {
                    runCatching {
                        val marked = DirectMessageService.markConversationRead(context, summary.conversation)
                        if (marked != null) conv = marked
                        partnerActivity = DirectMessageService.fetchPartnerActivityState(context, summary.conversation.convId, partnerUid)
                        onUnreadChanged()
                    }
                }
            }
            .onFailure { err ->
                if (err.message?.startsWith("USERNAME_REQUIRED:") == true) {
                    usernameGateReason = localizedText(lang, "Sohbet etmek için önce kullanıcı adı oluşturmalısın.", "Create a username to chat.")
                } else {
                    errorMsg = err.message ?: "Sohbet açılamadı"
                }
                loading = false
            }
    }

    // Active 2.5s polling loop while chat dialog is visible
    LaunchedEffect(conv?.convId) {
        val cid = conv?.convId ?: return@LaunchedEffect
        while (cid.isNotBlank()) {
            delay(2500L)
            syncThread(scrollToEnd = false)
        }
    }

    // Typing heartbeat
    LaunchedEffect(textInput, conv?.convId) {
        val cid = conv?.convId ?: return@LaunchedEffect
        if (textInput.isBlank()) {
            if (!isRecordingAudio) DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.IDLE)
            return@LaunchedEffect
        }
        DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.TYPING)
        delay(3500L)
        if (!isRecordingAudio) DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.IDLE)
    }

    // Recording timer + heartbeat
    LaunchedEffect(isRecordingAudio) {
        if (!isRecordingAudio) return@LaunchedEffect
        recordingSeconds = 0
        val cid = conv?.convId.orEmpty()
        while (isRecordingAudio) {
            if (cid.isNotBlank()) {
                DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.RECORDING_AUDIO)
            }
            delay(1000L)
            recordingSeconds += 1
            if (recordingSeconds >= 35) break
        }
    }

    fun startRecordingAudioNow() {
        val cid = conv?.convId ?: return
        val dir = File(context.cacheDir, "dm_audio").apply { mkdirs() }
        val outFile = File(dir, "rec_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        val started = runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(24_000)
            rec.setAudioSamplingRate(16_000)
            rec.setMaxDuration(35_000)
            rec.setOutputFile(outFile.absolutePath)
            rec.prepare()
            rec.start()
            true
        }.getOrElse {
            runCatching { rec.release() }
            Toast.makeText(context, localizedText(lang, "Ses kaydı başlatılamadı", "Could not start recording"), Toast.LENGTH_SHORT).show()
            false
        }
        if (started) {
            recorderRef = rec
            recordingFile = outFile
            isRecordingAudio = true
            scope.launch { DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.RECORDING_AUDIO) }
        }
    }

    fun stopRecordingAudio(sendIt: Boolean) {
        val rec = recorderRef
        val f = recordingFile
        val dur = recordingSeconds.coerceAtLeast(1)
        isRecordingAudio = false
        recorderRef = null
        recordingFile = null
        val cid = conv?.convId.orEmpty()
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        if (!sendIt || f == null || !f.exists() || conv == null) {
            runCatching { f?.delete() }
            if (cid.isNotBlank()) scope.launch { DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.IDLE) }
            return
        }
        val cNow = conv!!
        scope.launch {
            sending = true
            val raw = withContext(Dispatchers.IO) { runCatching { f.readBytes() }.getOrNull() }
            withContext(Dispatchers.IO) { runCatching { f.delete() } }
            if (raw != null && raw.isNotEmpty()) {
                val dataUrl = "data:audio/mp4;base64,${Base64.encodeToString(raw, Base64.NO_WRAP)}"
                DirectMessageService.sendMessage(
                    context = context,
                    conv = cNow,
                    myProfile = currentProfile,
                    msgType = "audio",
                    mediaBase64 = dataUrl,
                    mediaMime = "audio/mp4",
                    mediaDurationSec = dur
                ).onSuccess { (uc, m) ->
                    conv = uc
                    messages = messages.filterNot { it.msgId == m.msgId } + m
                    if (messages.isNotEmpty()) runCatching { listState.animateScrollToItem(messages.lastIndex) }
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Ses gönderilemedi", Toast.LENGTH_SHORT).show()
                }
            }
            DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.IDLE)
            sending = false
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecordingAudioNow()
        else Toast.makeText(context, localizedText(lang, "Ses kaydı için mikrofon izni gerekli", "Microphone permission required"), Toast.LENGTH_SHORT).show()
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = capturePhotoUri
        val cNow = conv
        if (cNow != null) {
            if (ok && uri != null) {
                scope.launch {
                    sending = true
                    DirectMessageService.setChatActivityState(context, cNow.convId, DmChatActivityState.UPLOADING_PHOTO)
                    val encoded = withContext(Dispatchers.IO) { DirectMessageService.encodePhotoUriForDm(context, uri) }
                    if (encoded != null) {
                        DirectMessageService.sendMessage(
                            context = context,
                            conv = cNow,
                            myProfile = currentProfile,
                            msgType = "photo",
                            mediaBase64 = encoded,
                            mediaMime = "image/jpeg"
                        ).onSuccess { (uc, m) ->
                            conv = uc
                            messages = messages.filterNot { it.msgId == m.msgId } + m
                            if (messages.isNotEmpty()) runCatching { listState.animateScrollToItem(messages.lastIndex) }
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "Hata", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, localizedText(lang, "Fotoğraf işlenemedi", "Photo failed"), Toast.LENGTH_SHORT).show()
                    }
                    DirectMessageService.setChatActivityState(context, cNow.convId, DmChatActivityState.IDLE)
                    sending = false
                }
            } else {
                scope.launch { DirectMessageService.setChatActivityState(context, cNow.convId, DmChatActivityState.IDLE) }
            }
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cNow = conv ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            sending = true
            DirectMessageService.setChatActivityState(context, cNow.convId, DmChatActivityState.UPLOADING_PHOTO)
            val encoded = withContext(Dispatchers.IO) { DirectMessageService.encodePhotoUriForDm(context, uri) }
            if (encoded != null) {
                DirectMessageService.sendMessage(
                    context = context,
                    conv = cNow,
                    myProfile = currentProfile,
                    msgType = "photo",
                    mediaBase64 = encoded,
                    mediaMime = "image/jpeg"
                ).onSuccess { (uc, m) ->
                    conv = uc
                    messages = messages.filterNot { it.msgId == m.msgId } + m
                    if (messages.isNotEmpty()) runCatching { listState.animateScrollToItem(messages.lastIndex) }
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Hata", Toast.LENGTH_SHORT).show()
                }
            }
            DirectMessageService.setChatActivityState(context, cNow.convId, DmChatActivityState.IDLE)
            sending = false
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val f = captureVideoFile
        val cNow = conv
        if (ok && f != null && f.exists() && cNow != null) {
            scope.launch {
                sending = true
                DirectMessageService.setChatActivityState(context, cNow.convId, DmChatActivityState.UPLOADING_PHOTO)
                DirectMessageService.sendMessage(
                    context = context,
                    conv = cNow,
                    myProfile = currentProfile,
                    msgType = "video",
                    videoFile = f
                ).onSuccess { (uc, m) ->
                    conv = uc
                    messages = messages.filterNot { it.msgId == m.msgId } + m
                    if (messages.isNotEmpty()) runCatching { listState.animateScrollToItem(messages.lastIndex) }
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Video gönderilemedi", Toast.LENGTH_SHORT).show()
                }
                withContext(Dispatchers.IO) { runCatching { f.delete() } }
                DirectMessageService.setChatActivityState(context, cNow.convId, DmChatActivityState.IDLE)
                sending = false
            }
        } else {
            if (f != null) runCatching { f.delete() }
            cNow?.let { scope.launch { DirectMessageService.setChatActivityState(context, it.convId, DmChatActivityState.IDLE) } }
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cNow = conv ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            sending = true
            DirectMessageService.setChatActivityState(context, cNow.convId, DmChatActivityState.UPLOADING_PHOTO)
            val tmpFile = withContext(Dispatchers.IO) {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (raw == null || raw.isEmpty() || raw.size > 7_500_000) null
                else File(File(context.cacheDir, "dm_videos").apply { mkdirs() }, "pick_${System.currentTimeMillis()}.mp4").apply { writeBytes(raw) }
            }
            if (tmpFile != null) {
                DirectMessageService.sendMessage(
                    context = context,
                    conv = cNow,
                    myProfile = currentProfile,
                    msgType = "video",
                    videoFile = tmpFile
                ).onSuccess { (uc, m) ->
                    conv = uc
                    messages = messages.filterNot { it.msgId == m.msgId } + m
                    if (messages.isNotEmpty()) runCatching { listState.animateScrollToItem(messages.lastIndex) }
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Video gönderilemedi", Toast.LENGTH_SHORT).show()
                }
                withContext(Dispatchers.IO) { runCatching { tmpFile.delete() } }
            } else {
                Toast.makeText(context, localizedText(lang, "Video max 7.5 MB olabilir", "Video max size is 7.5 MB"), Toast.LENGTH_SHORT).show()
            }
            DirectMessageService.setChatActivityState(context, cNow.convId, DmChatActivityState.IDLE)
            sending = false
        }
    }

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

    zoomAvatarTarget?.let { (av, dn, un) ->
        AvatarZoomDialog(avatarBase64 = av, displayName = dn, username = un, onDismiss = { zoomAvatarTarget = null })
    }

    fullscreenPhotoDataUrl?.let { raw ->
        val bmp = remember(raw) { SocialProfileService.decodeDataUrlToBitmap(raw)?.asImageBitmap() }
        Dialog(onDismissRequest = { fullscreenPhotoDataUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
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

    actionMenuMsg?.let { targetMsg ->
        AlertDialog(
            onDismissRequest = { actionMenuMsg = null },
            containerColor = DressKitStyle.cardSurface(),
            title = { Text(localizedText(lang, "Mesaj İşlemleri", "Message Actions"), color = primaryInk, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (targetMsg.canEdit()) {
                        OutlinedButton(
                            onClick = {
                                editMsgDraft = targetMsg.text
                                editingMsg = targetMsg
                                actionMenuMsg = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = primaryInk)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(localizedText(lang, "Düzenle (${targetMsg.editRemainingMinutes()} dk)", "Edit (${targetMsg.editRemainingMinutes()}m)"), color = primaryInk)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val cNow = conv
                            actionMenuMsg = null
                            if (cNow != null) {
                                scope.launch {
                                    DirectMessageService.softDeleteMessage(context, cNow, targetMsg)
                                        .onSuccess { deleted ->
                                            messages = messages.map { if (it.msgId == deleted.msgId) deleted else it }
                                            syncThread(scrollToEnd = false)
                                        }
                                        .onFailure { Toast.makeText(context, it.message ?: "Silinemedi", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        },
                        border = BorderStroke(1.dp, dangerInk.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = dangerInk)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(localizedText(lang, "Mesajı Sil", "Delete Message"), color = dangerInk)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionMenuMsg = null }) { Text(localizedText(lang, "Kapat", "Close"), color = primaryInk) }
            }
        )
    }

    editingMsg?.let { mToEdit ->
        AlertDialog(
            onDismissRequest = { editingMsg = null },
            containerColor = DressKitStyle.cardSurface(),
            title = { Text(localizedText(lang, "Mesajı Düzenle", "Edit Message"), color = primaryInk) },
            text = {
                OutlinedTextField(
                    value = editMsgDraft,
                    onValueChange = { editMsgDraft = it.take(1000) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cNow = conv ?: return@Button
                        val draft = editMsgDraft
                        editingMsg = null
                        scope.launch {
                            DirectMessageService.editMessage(context, cNow, mToEdit, draft)
                                .onSuccess { updated ->
                                    messages = messages.map { if (it.msgId == updated.msgId) updated else it }
                                    syncThread(scrollToEnd = false)
                                }
                                .onFailure { Toast.makeText(context, it.message ?: "Düzenlenemedi", Toast.LENGTH_SHORT).show() }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                ) { Text(localizedText(lang, "Kaydet", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { editingMsg = null }) { Text(localizedText(lang, "Vazgeç", "Cancel"), color = primaryInk) }
            }
        )
    }

    val pTitle = partner?.let { it.displayName.ifBlank { if (it.username.isNotBlank()) "@${it.username}" else "Kullanıcı" } }
        ?: localizedText(lang, "Sohbet", "Chat")
    val liveStatusLabel = when {
        isBlockedByMe || hasBlockedMe -> ""
        partnerActivity == DmChatActivityState.TYPING -> localizedText(lang, "yazıyor...", "typing...")
        partnerActivity == DmChatActivityState.CAPTURING_PHOTO -> localizedText(lang, "fotoğraf çekiyor...", "taking a photo...")
        partnerActivity == DmChatActivityState.UPLOADING_PHOTO -> localizedText(lang, "fotoğraf yüklüyor...", "uploading a photo...")
        partnerActivity == DmChatActivityState.RECORDING_AUDIO -> localizedText(lang, "ses kaydediyor...", "recording audio...")
        presence.isEffectivelyOnline() -> localizedText(lang, "Çevrimiçi", "Online")
        presence.lastOnlineAtMs > 0L -> localizedText(lang, "Son görülme: ${fmtDmShortRel(presence.lastOnlineAtMs)}", "Last seen: ${fmtDmShortRel(presence.lastOnlineAtMs)}")
        else -> if (!partner?.username.isNullOrBlank()) "@${partner!!.username}" else ""
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
                .imePadding(),
            color = DressKitStyle.canvasBg()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = primaryInk)
                        }
                        partner?.let { p ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .combinedClickable(
                                        onClick = { onDismiss(); onOpenOtherProfile(p.uid) },
                                        onLongClick = { zoomAvatarTarget = Triple(p.avatarBase64, p.displayName, p.username) }
                                    )
                            ) {
                                UserAvatarCircle(p.avatarBase64, pTitle, size = 38.dp)
                                if (presence.isEffectivelyOnline() && !isBlockedByMe && !hasBlockedMe) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(11.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22C55E))
                                            .border(1.5.dp, DressKitStyle.canvasBg(), CircleShape)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onDismiss(); onOpenOtherProfile(p.uid) }
                            ) {
                                Text(pTitle, color = primaryInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (liveStatusLabel.isNotBlank()) {
                                    Text(
                                        text = liveStatusLabel,
                                        color = if (partnerActivity != DmChatActivityState.IDLE || presence.isEffectivelyOnline()) DressKitStyle.accentColor() else secondaryInk,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val nextMute = !isMuted
                            DirectMessageService.setConversationMuted(context, partnerUid, nextMute)
                            isMuted = nextMute
                            onUnreadChanged()
                            Toast.makeText(
                                context,
                                if (nextMute) localizedText(lang, "Sohbet sessize alındı", "Conversation muted")
                                else localizedText(lang, "Sohbet bildirimleri açıldı", "Conversation unmuted"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = "Bildirimler",
                                tint = if (isMuted) DressKitStyle.accentColor() else primaryInk
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Menü", tint = primaryInk)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = DressKitStyle.cardSurface(),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, DressKitStyle.borderColor())
                            ) {
                                DropdownMenuItem(
                                    text = { Text(localizedText(lang, "Profili Gör", "View Profile"), color = primaryInk, fontWeight = FontWeight.Medium) },
                                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = primaryInk, modifier = Modifier.size(18.dp)) },
                                    onClick = { showMenu = false; onDismiss(); onOpenOtherProfile(partnerUid) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isMuted) localizedText(lang, "Sesi Aç", "Unmute") else localizedText(lang, "Sessize Al", "Mute"), color = primaryInk, fontWeight = FontWeight.Medium) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                                            contentDescription = null,
                                            tint = primaryInk,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        val nextMute = !isMuted
                                        DirectMessageService.setConversationMuted(context, partnerUid, nextMute)
                                        isMuted = nextMute
                                        onUnreadChanged()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isBlockedByMe) localizedText(lang, "Engeli Kaldır", "Unblock")
                                            else localizedText(lang, "Engelle", "Block"),
                                            color = if (isBlockedByMe) primaryInk else dangerInk,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null, tint = if (isBlockedByMe) primaryInk else dangerInk, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            if (isBlockedByMe) {
                                                SocialProfileService.unblockUser(context, partnerUid).onSuccess { isBlockedByMe = false }
                                            } else {
                                                SocialProfileService.blockUser(context, partnerUid).onSuccess { isBlockedByMe = true }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = DressKitStyle.borderColor())

                if (loading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DressKitStyle.accentColor())
                    }
                } else if (errorMsg != null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(errorMsg!!, color = dangerInk)
                            OutlinedButton(onClick = { reloadNonce++ }) {
                                Text(localizedText(lang, "Tekrar Dene", "Retry"), color = primaryInk)
                            }
                        }
                    }
                } else {
                    val activeConv = conv
                    // Recipient message request accept/decline/block banner
                    if (activeConv != null && activeConv.status == "request" && activeConv.recipientUid == myUid && !isBlockedByMe && !hasBlockedMe) {
                        Surface(
                            color = DressKitStyle.accentSoft(),
                            border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = localizedText(lang, "$pTitle sana mesaj isteği gönderdi. Yanıtlayabilmek için isteği kabul et.", "$pTitle sent a message request. Accept to reply."),
                                    color = primaryInk,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                DirectMessageService.acceptMessageRequest(context, activeConv).onSuccess { conv = it }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg()),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) { Text(localizedText(lang, "Kabul Et", "Accept"), fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                DirectMessageService.declineMessageRequest(context, activeConv).onSuccess { onDismiss() }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) { Text(localizedText(lang, "Reddet", "Decline"), color = primaryInk, fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                SocialProfileService.blockUser(context, partnerUid).onSuccess { isBlockedByMe = true }
                                            }
                                        },
                                        border = BorderStroke(1.dp, dangerInk),
                                        modifier = Modifier.height(38.dp)
                                    ) { Text(localizedText(lang, "Engelle", "Block"), color = dangerInk, fontSize = 12.sp) }
                                }
                            }
                        }
                    }

                    val lastOwnMsg = remember(messages, myUid) { messages.lastOrNull { it.senderUid == myUid } }
                    val partnerLastRead = activeConv?.partnerLastReadAtMs(myUid) ?: 0L

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.msgId }) { m ->
                            val isMine = m.senderUid == myUid
                            val showTime = expandedTimeMsgId == m.msgId
                            val clockStr = remember(m.createdAtMs) {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.createdAtMs))
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isMine && showTime) {
                                        Text(clockStr, color = secondaryInk, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = when {
                                            m.isDeleted -> DressKitStyle.secondarySurface()
                                            isMine -> DressKitStyle.accentSoft()
                                            else -> DressKitStyle.cardSurface()
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (!m.isDeleted && isMine) DressKitStyle.accentColor().copy(alpha = 0.45f)
                                            else DressKitStyle.borderColor()
                                        ),
                                        modifier = Modifier
                                            .widthIn(max = 290.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    expandedTimeMsgId = if (expandedTimeMsgId == m.msgId) null else m.msgId
                                                },
                                                onLongClick = {
                                                    if (isMine && !m.isDeleted) actionMenuMsg = m
                                                }
                                            )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (m.isDeleted) {
                                                Text(
                                                    text = localizedText(lang, "Bu mesaj silindi", "This message was deleted"),
                                                    color = secondaryInk,
                                                    fontStyle = FontStyle.Italic,
                                                    fontSize = 13.sp
                                                )
                                            } else {
                                                // Shared post / post reply preview card
                                                if ((m.msgType == "shared_post" || m.msgType == "post_reply") && m.sharedPostId.isNotBlank()) {
                                                    val thumbBmp = remember(m.sharedPostThumb) {
                                                        if (m.sharedPostThumb.isNotBlank()) SocialProfileService.decodeDataUrlToBitmap(m.sharedPostThumb)?.asImageBitmap() else null
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = DressKitStyle.secondarySurface(),
                                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                onDismiss()
                                                                onOpenSinglePost(m.sharedPostId, m.sharedPostOwnerUid)
                                                            }
                                                    ) {
                                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                            Text(
                                                                text = if (m.msgType == "post_reply") localizedText(lang, "Gönderine yanıt · @${m.sharedPostOwnerUsername}", "Post reply · @${m.sharedPostOwnerUsername}")
                                                                else localizedText(lang, "Paylaşılan Gönderi · @${m.sharedPostOwnerUsername}", "Shared Post · @${m.sharedPostOwnerUsername}"),
                                                                color = DressKitStyle.accentColor(),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            if (thumbBmp != null) {
                                                                Image(
                                                                    bitmap = thumbBmp,
                                                                    contentDescription = null,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp))
                                                                )
                                                            }
                                                            if (m.sharedPostCaption.isNotBlank()) {
                                                                Text(m.sharedPostCaption, color = primaryInk, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                            }
                                                        }
                                                    }
                                                }

                                                // Photo or reply photo
                                                if ((m.msgType == "photo" || m.msgType == "post_reply") && m.mediaBase64.startsWith("data:image/")) {
                                                    val pBmp = remember(m.mediaBase64) {
                                                        SocialProfileService.decodeDataUrlToBitmap(m.mediaBase64)?.asImageBitmap()
                                                    }
                                                    if (pBmp != null) {
                                                        Image(
                                                            bitmap = pBmp,
                                                            contentDescription = "Fotoğraf",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .heightIn(min = 120.dp, max = 220.dp)
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .clickable { fullscreenPhotoDataUrl = m.mediaBase64 }
                                                        )
                                                    }
                                                }

                                                // GIF
                                                if (m.msgType == "gif" && (m.mediaBase64.isNotBlank() || m.mediaUrl.isNotBlank())) {
                                                    CommentMediaPreview(
                                                        dataUrl = m.mediaBase64,
                                                        remoteUrl = m.mediaUrl,
                                                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 180.dp).clip(RoundedCornerShape(10.dp))
                                                    )
                                                }

                                                // Video
                                                if (m.msgType == "video" && activeConv != null) {
                                                    DmVideoBubblePlayer(
                                                        convId = activeConv.convId,
                                                        msg = m
                                                    )
                                                }

                                                // Audio with 1x / 1.5x / 2x speed selector
                                                if (m.msgType == "audio" && m.mediaBase64.isNotBlank()) {
                                                    DmVoiceNotePlayer(
                                                        dataUrl = m.mediaBase64,
                                                        durationSec = m.mediaDurationSec,
                                                        msgId = m.msgId
                                                    )
                                                }

                                                if (m.text.isNotBlank()) {
                                                    Text(m.text, color = primaryInk, fontSize = 14.sp)
                                                }
                                                if (m.editedAtMs > 0L) {
                                                    Text(
                                                        text = localizedText(lang, "değiştirildi", "edited"),
                                                        color = secondaryInk,
                                                        fontSize = 10.sp,
                                                        fontStyle = FontStyle.Italic
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!isMine && showTime) {
                                        Text(clockStr, color = secondaryInk, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    }
                                }

                                if (isMine && lastOwnMsg?.msgId == m.msgId) {
                                    val seen = partnerLastRead >= m.createdAtMs && m.createdAtMs > 0L
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (seen) Icons.Outlined.DoneAll else Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = if (seen) DressKitStyle.accentColor() else secondaryInk,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (seen) localizedText(lang, "Görüldü", "Seen") else localizedText(lang, "İletildi", "Delivered"),
                                            color = if (seen) DressKitStyle.accentColor() else secondaryInk,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom composer area / Lock banners
                    val isRequestRecipientLocked = activeConv?.status == "request" && activeConv.recipientUid == myUid
                    val isRequesterLocked = activeConv?.isRequestLockReached(myUid) == true
                    val isDeclined = activeConv?.status == "declined"
                    val isBlocked = isBlockedByMe || hasBlockedMe

                    if (isBlocked) {
                        Surface(color = DressKitStyle.cardSurface(), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isBlockedByMe) localizedText(lang, "Bu kullanıcıyı engelledin.", "You blocked this user.")
                                    else localizedText(lang, "Bu kullanıcıya şu anda mesaj gönderilemiyor.", "Messaging unavailable."),
                                    color = secondaryInk,
                                    fontSize = 12.5.sp
                                )
                                if (isBlockedByMe) {
                                    OutlinedButton(onClick = {
                                        scope.launch { SocialProfileService.unblockUser(context, partnerUid).onSuccess { isBlockedByMe = false } }
                                    }) { Text(localizedText(lang, "Engeli Kaldır", "Unblock"), color = primaryInk) }
                                }
                            }
                        }
                    } else if (isRequestRecipientLocked) {
                        Surface(color = DressKitStyle.cardSurface(), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = localizedText(lang, "Yanıt vermek için yukarıdan mesaj isteğini kabul et.", "Accept the message request above to reply."),
                                color = secondaryInk,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else if (isRequesterLocked || isDeclined) {
                        Surface(color = DressKitStyle.cardSurface(), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (isDeclined) localizedText(lang, "Mesaj isteği reddedildi.", "Message request declined.")
                                else localizedText(lang, "Mesaj isteğin gönderildi. Karşı taraf kabul edene kadar başka mesaj gönderemezsin.", "Message request sent. Wait for acceptance before sending another message."),
                                color = secondaryInk,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else if (activeConv != null) {
                        if (showGifDrawer) {
                            Surface(color = DressKitStyle.secondarySurface(), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AnimatedGifGenerator.presets.forEach { preset ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = DressKitStyle.cardSurface(),
                                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                modifier = Modifier.clickable {
                                                    showGifDrawer = false
                                                    scope.launch {
                                                        sending = true
                                                        DirectMessageService.sendMessage(
                                                            context = context,
                                                            conv = activeConv,
                                                            myProfile = currentProfile,
                                                            msgType = "gif",
                                                            mediaBase64 = preset.dataUrl
                                                        ).onSuccess { (uc, m) ->
                                                            conv = uc
                                                            messages = messages.filterNot { it.msgId == m.msgId } + m
                                                            if (messages.isNotEmpty()) runCatching { listState.animateScrollToItem(messages.lastIndex) }
                                                        }.onFailure { err ->
                                                            Toast.makeText(context, err.message ?: "Hata", Toast.LENGTH_SHORT).show()
                                                        }
                                                        sending = false
                                                    }
                                                }
                                            ) {
                                                CommentMediaPreview(
                                                    dataUrl = preset.dataUrl,
                                                    remoteUrl = "",
                                                    modifier = Modifier.size(64.dp).padding(4.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = externalGifUrl,
                                            onValueChange = { externalGifUrl = it },
                                            placeholder = { Text("Tenor / Giphy HTTPS linki", fontSize = 12.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = {
                                                val safeUrl = externalGifUrl.trim()
                                                if (!AnimatedGifGenerator.isSafeExternalGifUrl(safeUrl)) {
                                                    Toast.makeText(context, localizedText(lang, "Geçerli Tenor/Giphy HTTPS linki gir", "Enter valid Tenor/Giphy URL"), Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                showGifDrawer = false
                                                externalGifUrl = ""
                                                scope.launch {
                                                    sending = true
                                                    DirectMessageService.sendMessage(
                                                        context = context,
                                                        conv = activeConv,
                                                        myProfile = currentProfile,
                                                        msgType = "gif",
                                                        mediaUrl = safeUrl
                                                    ).onSuccess { (uc, m) ->
                                                        conv = uc
                                                        messages = messages.filterNot { it.msgId == m.msgId } + m
                                                    }
                                                    sending = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                                        ) { Text("GIF", fontSize = 12.sp) }
                                    }
                                }
                            }
                        }

                        Surface(color = DressKitStyle.cardSurface(), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (activeConv.status == "request" && activeConv.requesterUid == myUid) {
                                    Text(
                                        text = localizedText(lang, "Takip etmediğin birine onaylayana kadar 1 mesaj isteği atabilirsin.", "Non-follower chat: you can send 1 request message."),
                                        color = DressKitStyle.accentColor(),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                }
                                if (isRecordingAudio) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = dangerInk.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, dangerInk),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = localizedText(lang, "Ses kaydediliyor... 0:${recordingSeconds.toString().padStart(2, '0')} / 0:35", "Recording... 0:${recordingSeconds.toString().padStart(2, '0')}"),
                                                color = dangerInk,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                IconButton(onClick = { stopRecordingAudio(sendIt = false) }, modifier = Modifier.size(34.dp)) {
                                                    Icon(Icons.Outlined.Close, contentDescription = "İptal", tint = dangerInk)
                                                }
                                                IconButton(onClick = { stopRecordingAudio(sendIt = true) }, modifier = Modifier.size(34.dp)) {
                                                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Gönder", tint = DressKitStyle.accentColor())
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                scope.launch { DirectMessageService.setChatActivityState(context, activeConv.convId, DmChatActivityState.CAPTURING_PHOTO) }
                                                val f = File(File(context.cacheDir, "dm_photos").apply { mkdirs() }, "cam_${System.currentTimeMillis()}.jpg")
                                                val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                                capturePhotoUri = u
                                                takePhotoLauncher.launch(u)
                                            },
                                            enabled = !sending,
                                            modifier = Modifier.size(32.dp)
                                        ) { Icon(Icons.Outlined.PhotoCamera, contentDescription = "Kamera", tint = primaryInk, modifier = Modifier.size(19.dp)) }
                                        IconButton(
                                            onClick = { pickPhotoLauncher.launch("image/*") },
                                            enabled = !sending,
                                            modifier = Modifier.size(32.dp)
                                        ) { Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Galeri", tint = primaryInk, modifier = Modifier.size(19.dp)) }
                                        IconButton(
                                            onClick = {
                                                scope.launch { DirectMessageService.setChatActivityState(context, activeConv.convId, DmChatActivityState.CAPTURING_PHOTO) }
                                                val f = File(File(context.cacheDir, "dm_videos").apply { mkdirs() }, "vcam_${System.currentTimeMillis()}.mp4")
                                                val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                                captureVideoFile = f
                                                captureVideoUri = u
                                                captureVideoLauncher.launch(u)
                                            },
                                            enabled = !sending,
                                            modifier = Modifier.size(32.dp)
                                        ) { Icon(Icons.Outlined.Videocam, contentDescription = "Video Çek", tint = primaryInk, modifier = Modifier.size(19.dp)) }
                                        IconButton(
                                            onClick = { pickVideoLauncher.launch("video/*") },
                                            enabled = !sending,
                                            modifier = Modifier.size(32.dp)
                                        ) { Icon(Icons.Outlined.VideoLibrary, contentDescription = "Video Seç", tint = primaryInk, modifier = Modifier.size(19.dp)) }
                                        IconButton(
                                            onClick = { showGifDrawer = !showGifDrawer },
                                            enabled = !sending,
                                            modifier = Modifier.size(32.dp)
                                        ) { Icon(Icons.Outlined.Gif, contentDescription = "GIF", tint = DressKitStyle.accentColor(), modifier = Modifier.size(22.dp)) }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                                if (ok) startRecordingAudioNow()
                                                else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            },
                                            enabled = !sending,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Outlined.Mic, contentDescription = "Sesli Mesaj", tint = DressKitStyle.accentColor(), modifier = Modifier.size(21.dp))
                                        }
                                        OutlinedTextField(
                                            value = textInput,
                                            onValueChange = { textInput = it.take(1000) },
                                            placeholder = { Text(localizedText(lang, "Mesaj yaz...", "Message..."), fontSize = 13.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                if (sending || textInput.isBlank()) return@IconButton
                                                val msgBody = textInput
                                                textInput = ""
                                                sending = true
                                                scope.launch {
                                                    DirectMessageService.sendMessage(
                                                        context = context,
                                                        conv = activeConv,
                                                        myProfile = currentProfile,
                                                        msgType = "text",
                                                        text = msgBody
                                                    ).onSuccess { (uc, m) ->
                                                        conv = uc
                                                        messages = messages.filterNot { it.msgId == m.msgId } + m
                                                        if (messages.isNotEmpty()) runCatching { listState.animateScrollToItem(messages.lastIndex) }
                                                    }.onFailure { err ->
                                                        textInput = msgBody
                                                        Toast.makeText(context, err.message ?: "Gönderilemedi", Toast.LENGTH_SHORT).show()
                                                    }
                                                    DirectMessageService.setChatActivityState(context, activeConv.convId, DmChatActivityState.IDLE)
                                                    sending = false
                                                }
                                            },
                                            enabled = !sending && textInput.isNotBlank()
                                        ) {
                                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Gönder", tint = DressKitStyle.accentColor())
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
private fun DmVoiceNotePlayer(
    dataUrl: String,
    durationSec: Int,
    msgId: String
) {
    val context = LocalContext.current
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var playerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(msgId) {
        onDispose {
            runCatching { playerRef?.stop() }
            runCatching { playerRef?.release() }
            playerRef = null
        }
    }

    fun applySpeed(mp: MediaPlayer, nextSpeed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { mp.playbackParams = mp.playbackParams.setSpeed(nextSpeed) }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(min = 185.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = DressKitStyle.accentColor(),
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable {
                    val cur = playerRef
                    if (playing && cur != null) {
                        runCatching { cur.pause() }
                        playing = false
                    } else if (cur != null) {
                        applySpeed(cur, speed)
                        runCatching { cur.start() }
                        playing = true
                    } else {
                        val b64 = dataUrl.substringAfter("base64,", "")
                        val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val dir = File(context.cacheDir, "dm_audio_play").apply { mkdirs() }
                            val ext = if (dataUrl.contains("webm", true)) "webm" else "m4a"
                            val f = File(dir, "$msgId.$ext").apply { writeBytes(bytes) }
                            val mp = MediaPlayer()
                            runCatching {
                                mp.setDataSource(f.absolutePath)
                                mp.prepare()
                                mp.setOnCompletionListener {
                                    playing = false
                                    runCatching { mp.seekTo(0) }
                                }
                                applySpeed(mp, speed)
                                mp.start()
                                playerRef = mp
                                playing = true
                            }.onFailure {
                                runCatching { mp.release() }
                            }
                        }
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = "Sesli mesaj",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "🎤 0:${durationSec.coerceAtLeast(1).toString().padStart(2, '0')}",
                color = primaryInk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp
            )
            Text("Sesli mesaj", color = secondaryInk, fontSize = 10.sp)
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = DressKitStyle.secondarySurface(),
            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable {
                    speed = when {
                        speed < 1.25f -> 1.5f
                        speed < 1.75f -> 2.0f
                        else -> 1.0f
                    }
                    playerRef?.let { mp -> applySpeed(mp, speed) }
                }
        ) {
            Text(
                text = when {
                    speed == 1.5f -> "1.5x"
                    speed >= 2.0f -> "2x"
                    else -> "1x"
                },
                color = DressKitStyle.accentColor(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DmVideoBubblePlayer(convId: String, msg: DmMessage) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoFile by remember { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(false) }

    if (videoFile != null) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoPath(videoFile!!.absolutePath)
                    val mc = MediaController(ctx)
                    setMediaController(mc)
                    mc.setAnchorView(this)
                    start()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
        )
    } else {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    if (loading) return@clickable
                    loading = true
                    scope.launch {
                        DirectMessageService.loadDmVideoFile(context, convId, msg.msgId, msg.chunkCount, msg.mediaMime)
                            .onSuccess { videoFile = it }
                            .onFailure { Toast.makeText(context, it.message ?: "Video açılmadı", Toast.LENGTH_SHORT).show() }
                        loading = false
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Video Oynat", tint = Color.White, modifier = Modifier.size(34.dp))
                        if (msg.mediaDurationSec > 0) {
                            Text("${msg.mediaDurationSec} sn Video", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReplyPostInDmDialog(
    post: SocialPostData,
    myProfile: LocalProfileConfig,
    allItems: List<ClothingItem>,
    allOutfits: List<Outfit>,
    onProfileUpdated: (LocalProfileConfig) -> Unit,
    onSentOpenChat: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var currentProfile by remember(myProfile) { mutableStateOf(myProfile) }
    var replyText by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showUsernameGate by remember { mutableStateOf(false) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = captureUri
        if (ok && u != null) {
            scope.launch {
                val enc = withContext(Dispatchers.IO) { DirectMessageService.encodePhotoUriForDm(context, u) }
                if (enc != null) photoBase64 = enc
            }
        }
    }
    val galLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) {
            scope.launch {
                val enc = withContext(Dispatchers.IO) { DirectMessageService.encodePhotoUriForDm(context, u) }
                if (enc != null) photoBase64 = enc
            }
        }
    }

    if (showUsernameGate) {
        UsernameRequiredDialog(
            myProfile = currentProfile,
            allItems = allItems,
            allOutfits = allOutfits,
            reasonText = localizedText(lang, "Özel mesaj göndermek için önce kullanıcı adı oluşturmalısın.", "Create a username before sending private replies."),
            onSaved = { saved -> currentProfile = saved; onProfileUpdated(saved) },
            onDismiss = { showUsernameGate = false }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = DressKitStyle.cardSurface(),
        title = {
            Text(
                text = localizedText(lang, "Özel Sohbetten Yanıtla", "Reply in Private Chat"),
                color = primaryInk,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DressKitStyle.secondarySurface(),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "@${post.ownerUsername.ifBlank { post.ownerDisplayName }}",
                            color = DressKitStyle.accentColor(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (post.caption.isNotBlank()) {
                            Text(post.caption, color = primaryInk, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it.take(1000) },
                    placeholder = { Text(localizedText(lang, "Mesajını yaz (isteğe bağlı)...", "Write a message...")) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val f = File(File(context.cacheDir, "dm_photos").apply { mkdirs() }, "pr_${System.currentTimeMillis()}.jpg")
                            val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                            captureUri = u
                            camLauncher.launch(u)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp), tint = primaryInk)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(localizedText(lang, "Kamera", "Camera"), color = primaryInk, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { galLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = primaryInk)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(localizedText(lang, "Galeri", "Gallery"), color = primaryInk, fontSize = 12.sp)
                    }
                }
                if (photoBase64.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(localizedText(lang, "Fotoğraf eklendi", "Photo attached"), color = DressKitStyle.accentColor(), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { photoBase64 = "" }) { Text(localizedText(lang, "Kaldır", "Remove"), color = secondaryInk, fontSize = 11.sp) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (busy) return@Button
                    if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                        showUsernameGate = true
                        return@Button
                    }
                    if (replyText.isBlank() && photoBase64.isBlank()) {
                        Toast.makeText(context, localizedText(lang, "Yanıt metni veya fotoğraf ekle", "Add text or photo"), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        val convRes = DirectMessageService.getOrCreateConversation(context, post.ownerUid)
                        convRes.onSuccess { summary ->
                            DirectMessageService.sendMessage(
                                context = context,
                                conv = summary.conversation,
                                myProfile = currentProfile,
                                msgType = "post_reply",
                                text = replyText,
                                mediaBase64 = photoBase64,
                                mediaMime = if (photoBase64.isNotBlank()) "image/jpeg" else "",
                                sharedPost = post
                            ).onSuccess {
                                Toast.makeText(context, localizedText(lang, "Özel sohbete gönderildi!", "Sent to private chat!"), Toast.LENGTH_SHORT).show()
                                onDismiss()
                                onSentOpenChat(post.ownerUid)
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Gönderilemedi", Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "Sohbet açılamadı", Toast.LENGTH_SHORT).show()
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
            ) { Text(localizedText(lang, "Gönder", "Send")) }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) { Text(localizedText(lang, "Vazgeç", "Cancel"), color = primaryInk) }
        }
    )
}

@Composable
fun ForwardPostToDmDialog(
    post: SocialPostData,
    myProfile: LocalProfileConfig,
    allItems: List<ClothingItem>,
    allOutfits: List<Outfit>,
    onProfileUpdated: (LocalProfileConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val myUid = remember { AnalyticsService.getCurrentUid(context) }

    var currentProfile by remember(myProfile) { mutableStateOf(myProfile) }
    var showUsernameGate by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var searchQ by remember { mutableStateOf("") }
    var targets by remember { mutableStateOf<List<PublicProfileHeader>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busyUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        val convs = DirectMessageService.fetchMyConversations(context).getOrNull().orEmpty()
            .filter { !it.isBlockedByMe && !it.hasBlockedMe }
            .map { it.partner }
        val following = if (myUid.isNotBlank()) SocialProfileService.fetchFollowList(context, myUid, followers = false).getOrNull().orEmpty() else emptyList()
        val combined = LinkedHashMap<String, PublicProfileHeader>()
        convs.forEach { combined[it.uid] = it }
        following.forEach { if (!combined.containsKey(it.uid)) combined[it.uid] = it }
        targets = combined.values.toList()
        loading = false
    }

    LaunchedEffect(searchQ) {
        val q = searchQ.trim()
        if (q.length < 2) return@LaunchedEffect
        delay(250)
        SocialProfileService.searchProfiles(context, q).onSuccess { list ->
            val filtered = list.filter { it.uid != myUid }
            if (filtered.isNotEmpty()) targets = filtered
        }
    }

    if (showUsernameGate) {
        UsernameRequiredDialog(
            myProfile = currentProfile,
            allItems = allItems,
            allOutfits = allOutfits,
            reasonText = localizedText(lang, "Gönderiyi sohbette iletmek için önce kullanıcı adı oluşturmalısın.", "Create a username before forwarding posts."),
            onSaved = { saved -> currentProfile = saved; onProfileUpdated(saved) },
            onDismiss = { showUsernameGate = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DressKitStyle.cardSurface(),
        title = { Text(localizedText(lang, "Sohbette Gönder", "Send in Chat"), color = primaryInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(400) },
                    placeholder = { Text(localizedText(lang, "Not ekle (isteğe bağlı)...", "Add a note...")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = searchQ,
                    onValueChange = { searchQ = it },
                    placeholder = { Text(localizedText(lang, "@kullanıcı ara...", "Search @username...")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DressKitStyle.accentColor())
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(targets, key = { it.uid }) { u ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DressKitStyle.secondarySurface())
                                    .clickable(enabled = busyUid == null) {
                                        if (!SocialProfileService.isValidUsername(currentProfile.username)) {
                                            showUsernameGate = true
                                            return@clickable
                                        }
                                        busyUid = u.uid
                                        scope.launch {
                                            DirectMessageService.getOrCreateConversation(context, u.uid)
                                                .onSuccess { summary ->
                                                    DirectMessageService.sendMessage(
                                                        context = context,
                                                        conv = summary.conversation,
                                                        myProfile = currentProfile,
                                                        msgType = "shared_post",
                                                        text = note,
                                                        sharedPost = post
                                                    ).onSuccess {
                                                        Toast.makeText(context, localizedText(lang, "Gönderi iletildi!", "Post sent!"), Toast.LENGTH_SHORT).show()
                                                        onDismiss()
                                                    }.onFailure { err ->
                                                        Toast.makeText(context, err.message ?: "Gönderilemedi", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .onFailure { err ->
                                                    Toast.makeText(context, err.message ?: "Sohbet açılamadı", Toast.LENGTH_SHORT).show()
                                                }
                                            busyUid = null
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                UserAvatarCircle(u.avatarBase64, u.displayName.ifBlank { u.username }, size = 34.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u.displayName.ifBlank { "@${u.username}" }, color = primaryInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    if (u.username.isNotBlank()) Text("@${u.username}", color = secondaryInk, fontSize = 11.sp)
                                }
                                Text(
                                    text = if (busyUid == u.uid) "..." else localizedText(lang, "Gönder", "Send"),
                                    color = DressKitStyle.accentColor(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(localizedText(lang, "Kapat", "Close"), color = primaryInk) }
        }
    )
}

private fun fmtDmShortRel(ms: Long): String {
    if (ms <= 0L) return ""
    val diff = ((System.currentTimeMillis() - ms) / 1000L).coerceAtLeast(0L)
    return when {
        diff < 60L -> "az önce"
        diff < 3600L -> "${diff / 60L} dk"
        diff < 86400L -> "${diff / 3600L} sa"
        else -> SimpleDateFormat("dd MMM", Locale("tr", "TR")).format(Date(ms))
    }
}
