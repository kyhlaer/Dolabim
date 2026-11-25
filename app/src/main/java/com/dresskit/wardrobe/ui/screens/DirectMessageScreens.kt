package com.dresskit.wardrobe.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.Download
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.dresskit.wardrobe.data.AnalyticsService
import com.dresskit.wardrobe.data.AnimatedGifGenerator
import com.dresskit.wardrobe.data.DirectMessageService
import com.dresskit.wardrobe.data.ImageStorage
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class DmTakePicture : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply {
            clipData = ClipData.newRawUri("photo", input)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
}

private class DmCreatePhotoDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        ActivityResultContracts.CreateDocument(input.first).createIntent(context, input.second)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
private fun rememberDmPhotoCamera(
    ready: Boolean = true,
    onStarted: () -> Unit = {},
    onFinished: (String?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var result by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val takePicture = rememberLauncherForActivityResult(DmTakePicture()) { result = it }

    fun failure() {
        Toast.makeText(context, localizedText(lang, "Kamera açılamadı", "Could not open the camera"), Toast.LENGTH_SHORT).show()
    }

    fun launch() {
        if (pendingUri != null) return
        var file: File? = null
        try {
            val (output, uri) = ImageStorage.createCameraTempFile(context)
            file = output
            check(output.createNewFile())
            pendingUri = uri.toString()
            takePicture.launch(uri)
            onStarted()
        } catch (_: Exception) {
            pendingUri = null
            file?.delete()
            failure()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launch()
        else Toast.makeText(context, localizedText(lang, "Kamera izni gerekli", "Camera permission required"), Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(ready, pendingUri, result) {
        val uri = pendingUri?.let(Uri::parse) ?: return@LaunchedEffect
        val captured = result ?: return@LaunchedEffect
        if (captured && !ready) return@LaunchedEffect
        val encoded = withContext(Dispatchers.IO) {
            if (captured) DirectMessageService.encodePhotoUriForDm(context, uri) else null
        }
        if (captured && encoded == null) {
            Toast.makeText(context, localizedText(lang, "Fotoğraf işlenemedi", "Could not process the photo"), Toast.LENGTH_SHORT).show()
        }
        // Keep the file through decoding so a recreated activity can still consume the result.
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) { runCatching { context.contentResolver.delete(uri, null, null) } }
            onFinished(encoded)
            pendingUri = null
            result = null
        }
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launch()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }
}

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
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = primaryInk, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(DressKitStyle.accentColor()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(DressKitStyle.cardSurface(), RoundedCornerShape(12.dp))
                        .border(1.dp, DressKitStyle.borderColor(), RoundedCornerShape(12.dp))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) Text(localizedText(lang, "Yeni sohbet için @kullanıcı ara...", "Search @username to start chat..."), fontSize = 14.sp, color = secondaryInk)
                            innerTextField()
                        }
                    }
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
    var fullscreenPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadDestination by rememberSaveable { mutableStateOf<String?>(null) }
    val savePhoto = rememberLauncherForActivityResult(DmCreatePhotoDocument()) { uri ->
        downloadDestination = uri?.toString()
        if (uri == null) downloadPhotoId = null
    }
    val photoToDownload = messages.firstOrNull { it.msgId == downloadPhotoId && !it.isDeleted }?.mediaBase64
    LaunchedEffect(photoToDownload, downloadDestination) {
        val destination = downloadDestination?.let(Uri::parse) ?: return@LaunchedEffect
        val photo = photoToDownload ?: return@LaunchedEffect
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Base64.decode(photo.substringAfter(";base64,"), Base64.DEFAULT)
                checkNotNull(context.contentResolver.openOutputStream(destination)).use { it.write(bytes) }
            }.isSuccess
        }
        downloadPhotoId = null
        downloadDestination = null
        Toast.makeText(context, if (saved) localizedText(lang, "Fotoğraf kaydedildi", "Photo saved")
            else localizedText(lang, "Fotoğraf kaydedilemedi", "Could not save the photo"), Toast.LENGTH_SHORT).show()
    }
    var zoomAvatarTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var usernameGateReason by remember { mutableStateOf<String?>(null) }
    var currentProfile by remember(myProfile) { mutableStateOf(myProfile) }

    // Audio recording state
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var recorderRef by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    var captureVideoFile by remember { mutableStateOf<File?>(null) }
    val listState = rememberLazyListState()
    val voice = remember(partnerUid) { DmVoicePlayback(context.applicationContext) }
    DisposableEffect(voice) { onDispose { voice.close() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { voice.suspendPlayback() }
    LaunchedEffect(voice.playing) {
        while (voice.playing) {
            voice.updatePosition()
            delay(150L)
        }
    }
    LaunchedEffect(messages, voice.activeId) {
        val active = voice.activeId
        if (active != null && messages.any { it.msgId == active && it.isDeleted }) voice.close()
    }

    var reloadNonce by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorderRef?.stop() }
            runCatching { recorderRef?.release() }
            recorderRef = null
            recordingFile?.delete()
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
        val snapshot = messages
        runCatching {
            val latestConv = DirectMessageService.refreshConversation(context, c.convId) ?: c
            val msgs = DirectMessageService.fetchMessages(context, c.convId).getOrNull()
            if (conv === c) conv = latestConv
            if (msgs != null && messages === snapshot) {
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
            val toMark = conv ?: return
            val marked = DirectMessageService.markConversationRead(context, toMark)
            if (marked != null && conv === toMark) conv = marked
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

    fun startRecordingAudioNow() {
        val cid = conv?.convId ?: return
        if (isRecordingAudio || sending) return
        voice.suspendPlayback()
        val dir = File(context.cacheDir, "dm_audio").apply { mkdirs() }
        val outFile = runCatching { File.createTempFile("rec_", ".m4a", dir) }.getOrNull()
        var recorder: MediaRecorder? = null
        runCatching {
            checkNotNull(outFile)
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder = rec
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioChannels(1)
            rec.setAudioEncodingBitRate(24_000)
            rec.setAudioSamplingRate(16_000)
            rec.setOutputFile(outFile.absolutePath)
            rec.prepare()
            rec.start()
        }.getOrElse {
            runCatching { recorder?.release() }
            outFile?.delete()
            Toast.makeText(context, localizedText(lang, "Ses kaydı başlatılamadı", "Could not start recording"), Toast.LENGTH_SHORT).show()
            return
        }
        recorderRef = recorder
        recordingFile = outFile
        recordingStartedAt = SystemClock.elapsedRealtime()
        recordingSeconds = 0
        isRecordingAudio = true
        scope.launch { DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.RECORDING_AUDIO) }
    }

    fun stopRecordingAudio(sendIt: Boolean) {
        val rec = recorderRef
        val f = recordingFile
        val dur = ((SystemClock.elapsedRealtime() - recordingStartedAt + 500) / 1000).toInt().coerceIn(1, 35)
        isRecordingAudio = false
        recorderRef = null
        recordingFile = null
        val cid = conv?.convId.orEmpty()
        val stopped = rec != null && runCatching { rec.stop() }.isSuccess
        runCatching { rec?.release() }
        val cNow = conv
        if (!sendIt || !stopped || f == null || f.length() == 0L || cNow == null) {
            runCatching { f?.delete() }
            if (sendIt) Toast.makeText(context, localizedText(lang, "Ses kaydı okunamadı", "Could not read recording"), Toast.LENGTH_SHORT).show()
            if (cid.isNotBlank()) scope.launch { DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.IDLE) }
            return
        }
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
            } else {
                Toast.makeText(context, localizedText(lang, "Ses kaydı okunamadı", "Could not read recording"), Toast.LENGTH_SHORT).show()
            }
            DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.IDLE)
            sending = false
        }
    }

    LaunchedEffect(isRecordingAudio) {
        if (!isRecordingAudio) return@LaunchedEffect
        while (isRecordingAudio) {
            delay(250L)
            recordingSeconds = ((SystemClock.elapsedRealtime() - recordingStartedAt) / 1000).toInt().coerceAtMost(35)
            if (recordingSeconds >= 35) stopRecordingAudio(sendIt = true)
        }
    }
    LaunchedEffect(isRecordingAudio, conv?.convId) {
        val cid = conv?.convId ?: return@LaunchedEffect
        if (!isRecordingAudio) return@LaunchedEffect
        while (isRecordingAudio) {
            delay(3000L)
            DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.RECORDING_AUDIO)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (isRecordingAudio) stopRecordingAudio(sendIt = false)
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecordingAudioNow()
        else Toast.makeText(context, localizedText(lang, "Ses kaydı için mikrofon izni gerekli", "Microphone permission required"), Toast.LENGTH_SHORT).show()
    }

    suspend fun sendChatPhoto(encoded: String, target: DmConversation) {
        DirectMessageService.sendMessage(
            context = context,
            conv = target,
            myProfile = currentProfile,
            msgType = "photo",
            mediaBase64 = encoded,
            mediaMime = "image/jpeg"
        ).onSuccess { (updated, message) ->
            conv = updated
            messages = messages.filterNot { it.msgId == message.msgId } + message
            listState.animateScrollToItem(messages.lastIndex)
        }.onFailure { error ->
            Toast.makeText(context, error.message ?: "Hata", Toast.LENGTH_SHORT).show()
        }
    }

    val launchChatCamera = rememberDmPhotoCamera(
        ready = conv != null,
        onStarted = {
            conv?.convId?.let { cid ->
                scope.launch { DirectMessageService.setChatActivityState(context, cid, DmChatActivityState.CAPTURING_PHOTO) }
            }
        }
    ) { encoded ->
        val target = conv
        if (target != null && encoded != null) {
            scope.launch {
                sending = true
                try {
                    DirectMessageService.setChatActivityState(context, target.convId, DmChatActivityState.UPLOADING_PHOTO)
                    sendChatPhoto(encoded, target)
                } finally {
                    sending = false
                    DirectMessageService.setChatActivityState(context, target.convId, DmChatActivityState.IDLE)
                }
            }
        } else if (target != null) {
            scope.launch { DirectMessageService.setChatActivityState(context, target.convId, DmChatActivityState.IDLE) }
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = conv ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            sending = true
            try {
                DirectMessageService.setChatActivityState(context, target.convId, DmChatActivityState.UPLOADING_PHOTO)
                val encoded = withContext(Dispatchers.IO) { DirectMessageService.encodePhotoUriForDm(context, uri) }
                if (encoded != null) sendChatPhoto(encoded, target)
                else Toast.makeText(context, localizedText(lang, "Fotoğraf işlenemedi", "Could not process the photo"), Toast.LENGTH_SHORT).show()
            } finally {
                sending = false
                DirectMessageService.setChatActivityState(context, target.convId, DmChatActivityState.IDLE)
            }
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val f = captureVideoFile
        captureVideoFile = null
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

    fun launchVideoCamera() {
        if (captureVideoFile != null) return
        val target = conv ?: return
        var output: File? = null
        runCatching {
            val directory = File(context.cacheDir, "dm_videos").apply { mkdirs() }
            val file = File.createTempFile("vcam_", ".mp4", directory)
            output = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            captureVideoFile = file
            captureVideoLauncher.launch(uri)
            scope.launch { DirectMessageService.setChatActivityState(context, target.convId, DmChatActivityState.CAPTURING_PHOTO) }
        }.onFailure {
            captureVideoFile = null
            output?.delete()
            Toast.makeText(context, localizedText(lang, "Kamera açılamadı", "Could not open the camera"), Toast.LENGTH_SHORT).show()
        }
    }
    val videoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchVideoCamera()
        else Toast.makeText(context, localizedText(lang, "Kamera izni gerekli", "Camera permission required"), Toast.LENGTH_SHORT).show()
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

    messages.firstOrNull { it.msgId == fullscreenPhotoId && !it.isDeleted }?.let { photo ->
        DmPhotoPreview(photo.mediaBase64, onDismiss = { fullscreenPhotoId = null }, onDownload = {
            val type = photo.mediaBase64.substringAfter("data:").substringBefore(';')
            val suffix = when (type) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            downloadPhotoId = photo.msgId
            val mime = if (suffix == "jpg") "image/jpeg" else type
            runCatching { savePhoto.launch(mime to "dolabim-${photo.msgId}.$suffix") }.onFailure {
                downloadPhotoId = null
                Toast.makeText(context, localizedText(lang, "Fotoğraf kaydedilemedi", "Could not save the photo"), Toast.LENGTH_SHORT).show()
            }
        })
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
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                modifier = Modifier.widthIn(min = 210.dp)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            localizedText(lang, "Profili Gör", "View Profile"),
                                            color = primaryInk,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Person,
                                            contentDescription = null,
                                            tint = primaryInk,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                                    onClick = { showMenu = false; onDismiss(); onOpenOtherProfile(partnerUid) }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isMuted) localizedText(lang, "Sesi Aç", "Unmute") else localizedText(lang, "Sessize Al", "Mute"),
                                            color = primaryInk,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                                            contentDescription = null,
                                            tint = primaryInk,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
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
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Block,
                                            contentDescription = null,
                                            tint = if (isBlockedByMe) primaryInk else dangerInk,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
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
                            val hasPhoto = !m.isDeleted && (m.msgType == "photo" || m.msgType == "post_reply") &&
                                m.mediaBase64.startsWith("data:image/")
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
                                            .then(if (hasPhoto) Modifier.weight(1f, fill = false).width(290.dp) else Modifier)
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
                                                if (hasPhoto) {
                                                    val pBmp = remember(m.mediaBase64) {
                                                        DirectMessageService.decodeDmPhoto(m.mediaBase64)?.asImageBitmap()
                                                    }
                                                    if (pBmp != null) {
                                                        Image(
                                                            bitmap = pBmp,
                                                            contentDescription = "Fotoğraf",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .aspectRatio((pBmp.width.toFloat() / pBmp.height).coerceIn(1.21f, 2.22f))
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .combinedClickable(
                                                                    onClick = { fullscreenPhotoId = m.msgId },
                                                                    onLongClick = if (isMine) ({ actionMenuMsg = m }) else null
                                                                )
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
                                                if (m.msgType == "audio" && (m.mediaBase64.isNotBlank() || m.chunkCount > 0)) {
                                                    DmVoiceNotePlayer(
                                                        voice = voice,
                                                        message = m,
                                                        onToggle = { rate ->
                                                            scope.launch { voice.toggle(m.convId, m, rate) }
                                                        }
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

                        Surface(
                            color = DressKitStyle.cardSurface(),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            onClick = launchChatCamera,
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
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                    launchVideoCamera()
                                                } else videoPermission.launch(Manifest.permission.CAMERA)
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
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                            shape = RoundedCornerShape(999.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            onClick = {
                                                if (sending || textInput.isBlank()) return@Surface
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
                                            enabled = !sending && textInput.isNotBlank(),
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (!sending && textInput.isNotBlank()) DressKitStyle.buttonBg() else DressKitStyle.secondarySurface(),
                                            modifier = Modifier.height(40.dp).widthIn(min = 44.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Outlined.Send,
                                                    contentDescription = "Gönder",
                                                    tint = if (!sending && textInput.isNotBlank()) DressKitStyle.buttonFg() else secondaryInk,
                                                    modifier = Modifier.size(18.dp)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DmPhotoPreview(dataUrl: String, onDismiss: () -> Unit, onDownload: () -> Unit) {
    val lang = LocalAppLanguage.current
    val bitmap = remember(dataUrl) { DirectMessageService.decodeDmPhoto(dataUrl)?.asImageBitmap() }
    var zoom by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
        offset = if (zoom > 1f) offset + panChange else Offset.Zero
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))
                .statusBarsPadding().navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Box(
                    Modifier.fillMaxSize().padding(16.dp).clipToBounds()
                        .transformable(transform, canPan = { zoom > 1f })
                        .combinedClickable(onClick = {}, onDoubleClick = {
                            zoom = if (zoom > 1f) 1f else 2f
                            offset = Offset.Zero
                        })
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = localizedText(lang, "Fotoğraf", "Photo"),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offset.x.coerceIn(-size.width * (zoom - 1) / 2, size.width * (zoom - 1) / 2)
                            translationY = offset.y.coerceIn(-size.height * (zoom - 1) / 2, size.height * (zoom - 1) / 2)
                        }
                    )
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (bitmap != null) {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Outlined.Download, localizedText(lang, "İndir", "Download"), tint = Color.White)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, localizedText(lang, "Kapat", "Close"), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DmVoiceNotePlayer(voice: DmVoicePlayback, message: DmMessage, onToggle: (Float) -> Unit) {
    val lang = LocalAppLanguage.current
    val active = voice.activeId == message.msgId
    val playing = active && voice.playing
    val loading = active && voice.loading
    val failed = active && voice.failed
    val duration = if (active && voice.durationMs > 0) voice.durationMs else message.mediaDurationSec * 1000
    val position = if (active) voice.positionMs else 0
    var speed by rememberSaveable(message.msgId) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var seekPosition by remember(message.msgId) { mutableStateOf<Float?>(null) }
    val displayedPosition = seekPosition?.toInt() ?: position

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 238.dp).fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = DressKitStyle.accentColor(),
            modifier = Modifier.size(32.dp).clip(CircleShape).clickable(enabled = !loading) { onToggle(speed) }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        localizedText(lang, if (playing) "Duraklat" else "Oynat", if (playing) "Pause" else "Play"),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (failed) localizedText(lang, "Ses oynatılamadı", "Could not play audio")
                else "${dmVoiceTime(displayedPosition)} / ${dmVoiceTime(duration)}",
                color = DressKitStyle.primaryInk(),
                fontSize = 11.sp
            )
            Slider(
                value = displayedPosition.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                onValueChange = { seekPosition = it },
                onValueChangeFinished = {
                    seekPosition?.let { voice.seek(it.toInt()) }
                    seekPosition = null
                },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                enabled = active && !loading && !failed && voice.durationMs > 0,
                modifier = Modifier.height(24.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = DressKitStyle.secondarySurface(),
            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable {
                speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
                if (active) voice.changeSpeed(speed)
            }
        ) {
            Text(
                when (speed) { 1.5f -> "1.5x"; 2f -> "2x"; else -> "1x" },
                color = DressKitStyle.accentColor(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private fun dmVoiceTime(ms: Int): String = "%d:%02d".format(Locale.ROOT, ms / 60_000, ms / 1000 % 60)

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
    val launchReplyCamera = rememberDmPhotoCamera { encoded ->
        if (encoded != null) photoBase64 = encoded
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
                        onClick = launchReplyCamera,
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
