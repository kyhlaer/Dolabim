package com.dresskit.wardrobe.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dresskit.wardrobe.data.AnalyticsService
import com.dresskit.wardrobe.data.OutfitRecommendationEngine
import com.dresskit.wardrobe.data.PriceTrackerService
import com.dresskit.wardrobe.data.SecurityGuards
import com.dresskit.wardrobe.data.SocialProfileService
import com.dresskit.wardrobe.data.WardrobeRepository
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.CustomShowcaseItem
import com.dresskit.wardrobe.model.DeepLinkTarget
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.LoadedProfileBundle
import com.dresskit.wardrobe.model.LocalProfileConfig
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.PublicProfileHeader
import com.dresskit.wardrobe.model.STYLE_VIBE_PRESETS
import com.dresskit.wardrobe.model.SharedGarmentData
import com.dresskit.wardrobe.model.SharedOutfitData
import com.dresskit.wardrobe.model.ShowcaseItemData
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.components.ColorDot
import com.dresskit.wardrobe.ui.components.FilterChipPill
import com.dresskit.wardrobe.ui.components.HarmonyScoreBadge
import com.dresskit.wardrobe.ui.theme.DressKitColors
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ProfileIconButton(
    avatarBase64: String,
    pendingCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(DressKitStyle.secondarySurface())
            .border(1.dp, DressKitStyle.borderColor(), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        UserAvatarCircle(
            avatarBase64 = avatarBase64,
            displayName = "",
            size = 34.dp,
            showBorder = false
        )
        if (pendingCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(DressKitStyle.accentColor())
                    .border(1.5.dp, DressKitStyle.canvasBg(), CircleShape)
            )
        }
    }
}

@Composable
fun UserAvatarCircle(
    avatarBase64: String,
    displayName: String,
    size: Dp = 56.dp,
    showBorder: Boolean = true
) {
    val bmp = remember(avatarBase64) {
        if (avatarBase64.isNotBlank()) SocialProfileService.decodeDataUrlToBitmap(avatarBase64)?.asImageBitmap()
        else null
    }
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = DressKitStyle.secondarySurface(),
        border = if (showBorder) BorderStroke(1.dp, DressKitStyle.borderColor()) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString()
                if (!initial.isNullOrBlank()) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = (size.value * 0.38f).sp
                        ),
                        color = DressKitStyle.accentColor()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PersonOutline,
                        contentDescription = null,
                        tint = DressKitStyle.primaryInk(),
                        modifier = Modifier.size(size * 0.56f)
                    )
                }
            }
        }
    }
}

@Composable
fun ShareGarmentDialog(
    item: ClothingItem,
    profile: LocalProfileConfig,
    allItems: List<ClothingItem>,
    allOutfits: List<Outfit>,
    onProfileUpdated: (LocalProfileConfig) -> Unit,
    onOpenProfileSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var curProfile by remember { mutableStateOf(profile) }
    var isUploading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var sharedData by remember { mutableStateOf<SharedGarmentData?>(null) }

    // Inline quick username creation when user has no username yet
    var showInlineCreate by remember { mutableStateOf(false) }
    var inlineUsername by remember { mutableStateOf("") }
    var inlineDisplayName by remember { mutableStateOf(curProfile.displayName) }
    var isSavingUser by remember { mutableStateOf(false) }
    var userError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id) {
        isUploading = true
        errorMsg = null
        val res = SocialProfileService.shareGarment(context, item, curProfile)
        isUploading = false
        res.onSuccess { sharedData = it }
            .onFailure { errorMsg = it.message ?: "Link oluşturulamadı" }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .heightIn(max = 680.dp)
                .imePadding(),
            shape = RoundedCornerShape(DressKitStyle.cardCorner()),
            color = DressKitStyle.cardSurface(),
            border = BorderStroke(1.dp, DressKitStyle.borderColor())
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            tint = DressKitStyle.accentColor()
                        )
                        Text(
                            text = localizedText(lang, "Kıyafeti Paylaş", "Share Garment"),
                            style = MaterialTheme.typography.titleLarge,
                            color = primaryInk
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = primaryInk)
                    }
                }

                // Sharer Identity Preview
                Surface(
                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                    color = DressKitStyle.secondarySurface(),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UserAvatarCircle(
                            avatarBase64 = curProfile.avatarThumbBase64.ifBlank { curProfile.avatarBase64 },
                            displayName = curProfile.displayName.ifBlank { curProfile.username },
                            size = 38.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name.ifBlank { item.subCategory.display(lang) },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (curProfile.username.isNotBlank()) {
                                    "${localizedText(lang, "Paylaşan", "Shared by")}: @${curProfile.username}"
                                } else {
                                    localizedText(lang, "Paylaşan: Kullanıcı adı yok", "Shared anonymously")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (curProfile.username.isNotBlank()) DressKitStyle.accentColor() else secondaryInk
                            )
                        }
                    }
                }

                if (isUploading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = DressKitStyle.accentColor()
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = localizedText(lang, "Paylaşım linki hazırlanıyor...", "Creating share link..."),
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryInk
                        )
                    }
                } else if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = DressKitColors.DangerRust
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isUploading = true
                                errorMsg = null
                                val res = SocialProfileService.shareGarment(context, item, curProfile)
                                isUploading = false
                                res.onSuccess { sharedData = it }
                                    .onFailure { errorMsg = it.message }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(localizedText(lang, "Tekrar Dene", "Try Again"), color = primaryInk)
                    }
                } else if (sharedData != null) {
                    val url = sharedData!!.shareUrl
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DressKitStyle.secondarySurface(),
                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryInk,
                            modifier = Modifier.padding(12.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(url))
                                Toast.makeText(
                                    context,
                                    localizedText(lang, "Link kopyalandı", "Link copied"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = primaryInk)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(localizedText(lang, "Kopyala", "Copy"), color = primaryInk)
                        }

                        Button(
                            onClick = {
                                val who = if (curProfile.username.isNotBlank()) " (@${curProfile.username})" else ""
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "${item.name.ifBlank { item.subCategory.display(lang) }}$who — Dolabım\n$url"
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, localizedText(lang, "Kıyafeti Paylaş", "Share Garment")))
                            },
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DressKitStyle.buttonBg(),
                                contentColor = DressKitStyle.buttonFg()
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(localizedText(lang, "Paylaş", "Share"))
                        }
                    }

                    // Bottom prompt when user does NOT have a username yet
                    if (curProfile.username.isBlank()) {
                        Surface(
                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                            color = DressKitStyle.accentSoft(),
                            border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = localizedText(
                                        lang,
                                        "Henüz kullanıcı adın yok — linkte görünmesi için hemen oluşturabilirsin",
                                        "You don't have a username yet — create one so friends see who shared this"
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = primaryInk
                                )
                                if (!showInlineCreate) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { showInlineCreate = true },
                                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = DressKitStyle.accentColor(),
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                localizedText(lang, "Hemen Kullanıcı Adı Oluştur", "Create Username Now"),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        TextButton(onClick = onOpenProfileSettings) {
                                            Text(localizedText(lang, "Profil", "Profile"), color = DressKitStyle.accentColor(), fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = inlineUsername,
                                        onValueChange = { inlineUsername = it.lowercase().filter { c -> c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '.' }.take(24) },
                                        singleLine = true,
                                        prefix = { Text("@", color = DressKitStyle.accentColor()) },
                                        label = { Text(localizedText(lang, "Kullanıcı Adı", "Username")) },
                                        placeholder = { Text("ör: kerem_style") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = inlineDisplayName,
                                        onValueChange = { inlineDisplayName = it.take(50) },
                                        singleLine = true,
                                        label = { Text(localizedText(lang, "Görünen Ad", "Display Name")) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (userError != null) {
                                        Text(userError!!, color = DressKitColors.DangerRust, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = {
                                            if (!SocialProfileService.isValidUsername(inlineUsername)) {
                                                userError = localizedText(
                                                    lang,
                                                    "En az 3 karakter (küçük harf, rakam, _, .)",
                                                    "Min 3 chars (lowercase, digits, _, .)"
                                                )
                                                return@Button
                                            }
                                            scope.launch {
                                                isSavingUser = true
                                                userError = null
                                                val draft = curProfile.copy(
                                                    username = inlineUsername,
                                                    displayName = inlineDisplayName.trim().ifBlank { inlineUsername }
                                                )
                                                val syncRes = SocialProfileService.saveAndSyncProfile(context, draft, allItems, allOutfits)
                                                syncRes.onSuccess { updated ->
                                                    curProfile = updated
                                                    onProfileUpdated(updated)
                                                    val curShared = sharedData
                                                    if (curShared != null) {
                                                        SocialProfileService.updateSharedItemOwner(context, curShared, updated)
                                                            .onSuccess { sharedData = it }
                                                    }
                                                    showInlineCreate = false
                                                    Toast.makeText(
                                                        context,
                                                        localizedText(lang, "@${updated.username} oluşturuldu!", "@${updated.username} created!"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }.onFailure { err ->
                                                    userError = err.message
                                                }
                                                isSavingUser = false
                                            }
                                        },
                                        enabled = !isSavingUser,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DressKitStyle.accentColor(),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        if (isSavingUser) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                        } else {
                                            Text(localizedText(lang, "Oluştur ve Linke Ekle", "Save & Attach to Link"))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedItemViewerDialog(
    shareId: String,
    myProfile: LocalProfileConfig,
    repository: WardrobeRepository,
    onOpenUserProfile: (uid: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var loading by remember { mutableStateOf(true) }
    var shared by remember { mutableStateOf<SharedGarmentData?>(null) }
    var errText by remember { mutableStateOf<String?>(null) }
    var liked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(shareId) {
        loading = true
        errText = null
        val res = SocialProfileService.fetchSharedItem(context, shareId)
        res.onSuccess { shared = it }
            .onFailure { errText = it.message }
        val (isL, cnt) = SocialProfileService.fetchLikeInfo(context, "item_$shareId")
        liked = isL
        likeCount = cnt
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
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = primaryInk)
                    }
                    Text(
                        text = localizedText(lang, "Paylaşılan Parça", "Shared Garment"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryInk
                    )
                    IconButton(
                        onClick = {
                            val s = shared ?: return@IconButton
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${s.name} — ${s.shareUrl}")
                            }
                            context.startActivity(Intent.createChooser(i, localizedText(lang, "Paylaş", "Share")))
                        },
                        enabled = shared != null
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = primaryInk)
                    }
                }

                when {
                    loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DressKitStyle.accentColor())
                    }
                    errText != null || shared == null -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errText ?: localizedText(lang, "Paylaşılan ürün bulunamadı.", "Shared item not found."),
                            color = secondaryInk
                        )
                    }
                    else -> {
                        val item = shared!!
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Sharer header pill
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenUserProfile(item.ownerUid) },
                                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                color = DressKitStyle.cardSurface(),
                                border = BorderStroke(1.dp, DressKitStyle.borderColor())
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    UserAvatarCircle(
                                        avatarBase64 = item.ownerAvatar,
                                        displayName = item.ownerDisplayName.ifBlank { item.ownerUsername.ifBlank { "Dolabım" } },
                                        size = 42.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.ownerDisplayName.ifBlank {
                                                if (item.ownerUsername.isNotBlank()) "@${item.ownerUsername}" else localizedText(lang, "Dolabım Kullanıcısı", "Dolabım User")
                                            },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = primaryInk
                                        )
                                        Text(
                                            text = if (item.ownerUsername.isNotBlank()) "@${item.ownerUsername}" else localizedText(lang, "Profilini görüntüle", "View profile"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DressKitStyle.accentColor()
                                        )
                                    }
                                    Text(
                                        text = localizedText(lang, "Profil →", "Profile →"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DressKitStyle.accentColor()
                                    )
                                }
                            }

                            // Photos Pager
                            val pager = rememberPagerState(pageCount = { item.photos.size.coerceAtLeast(1) })
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(DressKitStyle.cardCorner()))
                                    .background(Color.White)
                                    .border(1.dp, DressKitStyle.borderColor(), RoundedCornerShape(DressKitStyle.cardCorner()))
                            ) {
                                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { idx ->
                                    val bmp = remember(item.photos[idx]) {
                                        SocialProfileService.decodeDataUrlToBitmap(item.photos[idx])?.asImageBitmap()
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp)
                                        )
                                    }
                                }
                                if (item.photos.size > 1) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(10.dp),
                                        shape = RoundedCornerShape(999.dp),
                                        color = DressKitColors.CharcoalInk.copy(alpha = 0.8f)
                                    ) {
                                        Text(
                                            text = "${pager.currentPage + 1} / ${item.photos.size}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Title + Like button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = primaryInk
                                    )
                                    if (item.priceText.isNotBlank()) {
                                        Text(
                                            text = item.priceText,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = DressKitStyle.accentColor()
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            SocialProfileService.toggleLike(context, "item_${item.shareId}")
                                                .onSuccess { (nextL, nextC) ->
                                                    liked = nextL
                                                    likeCount = nextC
                                                }
                                        }
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (liked) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                                    border = BorderStroke(1.dp, if (liked) DressKitStyle.accentColor() else DressKitStyle.borderColor())
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (liked) DressKitStyle.accentColor() else primaryInk,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("$likeCount", color = primaryInk, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }

                            val catDisp = runCatching { ClothingCategory.valueOf(item.category).display(lang) }.getOrDefault(item.category)
                            val subDisp = runCatching { ClothingSubCategory.valueOf(item.subCategory).display(lang) }.getOrNull()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChipPill(label = catDisp, selected = false, onClick = {})
                                if (!subDisp.isNullOrBlank()) FilterChipPill(label = subDisp, selected = false, onClick = {})
                                item.colors.forEach { cname ->
                                    val cObj = runCatching { GarmentColor.valueOf(cname) }.getOrNull()
                                    if (cObj != null) FilterChipPill(label = cObj.display(lang), selected = false, onClick = {})
                                }
                            }

                            if (item.notes.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                    color = DressKitStyle.secondarySurface(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.notes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = primaryInk,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            if (PriceTrackerService.isSafeHttpsUrl(item.productUrl)) {
                                OutlinedButton(
                                    onClick = { SecurityGuards.openExternalUrlSafely(context, item.productUrl) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = primaryInk)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(localizedText(lang, "Mağaza Linkini Aç", "Open Store Link"), color = primaryInk)
                                }
                            }

                            // Import buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (importing) return@OutlinedButton
                                        scope.launch {
                                            importing = true
                                            val added = SocialProfileService.importRemoteGarmentLocally(
                                                context = context,
                                                repository = repository,
                                                name = item.name,
                                                categoryStr = item.category,
                                                subCategoryStr = item.subCategory,
                                                styleTagStr = item.styleTag,
                                                seasonStrs = item.seasons,
                                                colorStrs = item.colors,
                                                notes = item.notes,
                                                priceText = item.priceText,
                                                productUrl = item.productUrl,
                                                photosBase64 = item.photos,
                                                asWishlist = true
                                            )
                                            importing = false
                                            Toast.makeText(
                                                context,
                                                if (added != null) localizedText(lang, "İstek listene eklendi", "Added to wishlist")
                                                else localizedText(lang, "Eklenemedi", "Failed"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    enabled = !importing,
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, DressKitStyle.accentColor())
                                ) {
                                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp), tint = DressKitStyle.accentColor())
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(localizedText(lang, "İstek Listeme", "Wishlist"), color = DressKitStyle.accentColor(), maxLines = 1)
                                }
                                Button(
                                    onClick = {
                                        if (importing) return@Button
                                        scope.launch {
                                            importing = true
                                            val added = SocialProfileService.importRemoteGarmentLocally(
                                                context = context,
                                                repository = repository,
                                                name = item.name,
                                                categoryStr = item.category,
                                                subCategoryStr = item.subCategory,
                                                styleTagStr = item.styleTag,
                                                seasonStrs = item.seasons,
                                                colorStrs = item.colors,
                                                notes = item.notes,
                                                priceText = item.priceText,
                                                productUrl = item.productUrl,
                                                photosBase64 = item.photos,
                                                asWishlist = false
                                            )
                                            importing = false
                                            Toast.makeText(
                                                context,
                                                if (added != null) localizedText(lang, "Dolabına eklendi!", "Added to wardrobe!")
                                                else localizedText(lang, "Eklenemedi", "Failed"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    enabled = !importing,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DressKitStyle.buttonBg(),
                                        contentColor = DressKitStyle.buttonFg()
                                    )
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(localizedText(lang, "Dolabıma Ekle", "Add to Closet"), maxLines = 1)
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoverUsersDialog(
    myUid: String,
    @Suppress("UNUSED_PARAMETER") myProfile: LocalProfileConfig,
    @Suppress("UNUSED_PARAMETER") repository: WardrobeRepository,
    onOpenDeepLink: (DeepLinkTarget) -> Unit,
    onSelectUser: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PublicProfileHeader>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        searching = true
        SocialProfileService.searchProfiles(context, "").onSuccess { searchResults = it }
        searching = false
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
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = primaryInk)
                    }
                    Text(
                        text = localizedText(lang, "Keşfet & Ara", "Discover & Search"),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = primaryInk
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { next ->
                            searchQuery = next
                            scope.launch {
                                searching = true
                                SocialProfileService.searchProfiles(context, next)
                                    .onSuccess { searchResults = it }
                                searching = false
                            }
                        },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = secondaryInk) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    scope.launch {
                                        searching = true
                                        SocialProfileService.searchProfiles(context, "").onSuccess { searchResults = it }
                                        searching = false
                                    }
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = null, tint = secondaryInk)
                                }
                            }
                        },
                        label = { Text(localizedText(lang, "Kullanıcı adı, ad veya Dolabım linki", "Search @username, name, or paste link")) },
                        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val pastedLink = remember(searchQuery) { SocialProfileService.parseDeepLink(searchQuery) }
                    if (pastedLink != null) {
                        Button(
                            onClick = { onOpenDeepLink(pastedLink) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.accentColor(), contentColor = Color.White)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(localizedText(lang, "Yapıştırılan Dolabım Linkini Aç", "Open Pasted Dolabım Link"))
                        }
                    }

                    if (searching) {
                        Box(modifier = Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DressKitStyle.accentColor())
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val visibleResults = searchResults.filter { it.uid != myUid }
                        if (visibleResults.isEmpty() && !searching) {
                            item {
                                Text(
                                    text = localizedText(lang, "Eşleşen kullanıcı bulunamadı.", "No matching profiles found."),
                                    color = secondaryInk,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        items(visibleResults, key = { it.uid }) { user ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectUser(user.uid, false) },
                                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                color = DressKitStyle.cardSurface(),
                                border = BorderStroke(1.dp, DressKitStyle.borderColor())
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    UserAvatarCircle(user.avatarBase64, user.displayName.ifBlank { user.username }, size = 46.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = user.displayName.ifBlank { "@${user.username}" },
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = primaryInk,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (user.isPrivate) {
                                                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(13.dp), tint = secondaryInk)
                                            }
                                        }
                                        if (user.username.isNotBlank()) {
                                            Text("@${user.username}", style = MaterialTheme.typography.labelSmall, color = DressKitStyle.accentColor())
                                        }
                                        if (user.bio.isNotBlank()) {
                                            Text(user.bio, style = MaterialTheme.typography.bodySmall, color = secondaryInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = secondaryInk)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileHubScreen(
    myProfile: LocalProfileConfig,
    items: List<ClothingItem>,
    outfits: List<Outfit>,
    repository: WardrobeRepository,
    pendingCustomPhotoDataUrl: String?,
    onConsumeCustomPhotoDataUrl: () -> Unit,
    onProfileUpdated: (LocalProfileConfig) -> Unit,
    onPickAvatarGallery: () -> Unit,
    onCaptureAvatarCamera: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onRequestCustomShowcasePhoto: () -> Unit,
    unreadActivityCount: Int = 0,
    onOpenActivity: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenDeepLink: (DeepLinkTarget) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var subTab by remember { mutableIntStateOf(0) } // 0=Profilim, 1=Düzenle
    var myUid by remember { mutableStateOf(AnalyticsService.getCurrentUid(context)) }
    var followersCount by remember { mutableIntStateOf(0) }
    var followingCount by remember { mutableIntStateOf(0) }
    var pendingRequests by remember { mutableStateOf<List<PublicProfileHeader>>(emptyList()) }
    var blockedAccounts by remember { mutableStateOf<List<PublicProfileHeader>>(emptyList()) }
    var activeShowcaseSection by remember { mutableStateOf("all") }

    // Other profile inspection inside modal
    var inspectingOtherUser by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var followListModal by remember { mutableStateOf<Pair<String, List<PublicProfileHeader>>?>(null) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    // Edit form draft state synced cleanly when opening / updating
    var editUsername by remember(myProfile.username) { mutableStateOf(myProfile.username) }
    var editDisplayName by remember(myProfile.displayName) { mutableStateOf(myProfile.displayName) }
    var editBio by remember(myProfile.bio) { mutableStateOf(myProfile.bio) }
    var editIsPrivate by remember(myProfile.isPrivate) { mutableStateOf(myProfile.isPrivate) }
    var editTags by remember(myProfile.styleTags) { mutableStateOf(myProfile.styleTags) }
    var editVis by remember(myProfile.statsVisibility) { mutableStateOf(myProfile.statsVisibility) }
    var editSections by remember(myProfile.showcaseSections) { mutableStateOf(myProfile.showcaseSections) }
    var editSelectedIds by remember(myProfile.selectedShowcaseItemIds) { mutableStateOf(myProfile.selectedShowcaseItemIds) }
    var editCustomItems by remember(myProfile.customShowcaseItems) { mutableStateOf(myProfile.customShowcaseItems) }
    var usernameAvailability by remember { mutableStateOf<Boolean?>(null) }
    var isSavingProfile by remember { mutableStateOf(false) }

    // Refresh social stats & pending requests
    suspend fun refreshMySocialMeta() {
        val creds = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AnalyticsService.getOrCreateAuthCredentials(context)
        }
        val uid = creds?.first ?: AnalyticsService.getCurrentUid(context)
        myUid = uid
        if (uid.isNotBlank()) {
            SocialProfileService.fetchProfileBundle(context, uid, isUsername = false).onSuccess { bundle ->
                if (bundle != null) {
                    followersCount = bundle.followersCount
                    followingCount = bundle.followingCount
                }
            }
            SocialProfileService.fetchPendingFollowRequests(context).onSuccess { list ->
                pendingRequests = list
            }
            SocialProfileService.fetchBlockedAccounts(context).onSuccess { list ->
                blockedAccounts = list
            }
        }
    }

    LaunchedEffect(Unit) {
        AnalyticsService.trackFeature("profile_open")
        refreshMySocialMeta()
    }

    // Add Custom Showcase Piece dialog when user picks a photo
    LaunchedEffect(pendingCustomPhotoDataUrl) {
        if (!pendingCustomPhotoDataUrl.isNullOrBlank()) {
            showAddCustomDialog = true
        }
    }

    if (showAddCustomDialog && !pendingCustomPhotoDataUrl.isNullOrBlank()) {
        var cName by remember { mutableStateOf("") }
        var cCat by remember { mutableStateOf(ClothingCategory.TOP) }
        var cNotes by remember { mutableStateOf("") }
        var cPrice by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showAddCustomDialog = false
                onConsumeCustomPhotoDataUrl()
            },
            containerColor = DressKitStyle.cardSurface(),
            title = { Text(localizedText(lang, "Vitrine Özel Parça Ekle", "Add Custom Showcase Piece"), color = primaryInk) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val bmp = remember(pendingCustomPhotoDataUrl) {
                        SocialProfileService.decodeDataUrlToBitmap(pendingCustomPhotoDataUrl)?.asImageBitmap()
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DressKitStyle.photoBoxSurface())
                        )
                    }
                    OutlinedTextField(
                        value = cName,
                        onValueChange = { cName = it.take(100) },
                        singleLine = true,
                        label = { Text(localizedText(lang, "Parça Adı", "Piece Name")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ClothingCategory.entries) { cat ->
                            FilterChipPill(
                                label = cat.display(lang),
                                selected = cCat == cat,
                                onClick = { cCat = cat }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = cPrice,
                        onValueChange = { cPrice = it.take(32) },
                        singleLine = true,
                        label = { Text(localizedText(lang, "Fiyat / Not (Opsiyonel)", "Price (Optional)")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cNotes,
                        onValueChange = { cNotes = it.take(240) },
                        label = { Text(localizedText(lang, "Açıklama (Opsiyonel)", "Caption")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nextItem = CustomShowcaseItem(
                            id = UUID.randomUUID().toString().take(12),
                            name = cName.trim().ifBlank { cCat.display(lang) },
                            category = cCat.name,
                            notes = cNotes.trim(),
                            priceText = cPrice.trim(),
                            photoBase64 = pendingCustomPhotoDataUrl
                        )
                        val updatedCustomList = listOf(nextItem) + editCustomItems
                        val updatedSections = if ("custom" in editSections) editSections else (editSections + "custom")
                        editCustomItems = updatedCustomList
                        editSections = updatedSections
                        showAddCustomDialog = false
                        onConsumeCustomPhotoDataUrl()
                        scope.launch {
                            val nextCfg = myProfile.copy(
                                showcaseSections = updatedSections,
                                customShowcaseItems = updatedCustomList
                            )
                            SocialProfileService.saveAndSyncProfile(context, nextCfg, items, outfits)
                                .onSuccess { onProfileUpdated(it) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                ) { Text(localizedText(lang, "Ekle & Yayınla", "Add & Publish")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddCustomDialog = false
                    onConsumeCustomPhotoDataUrl()
                }) { Text(localizedText(lang, "Vazgeç", "Cancel"), color = primaryInk) }
            }
        )
    }

    inspectingOtherUser?.let { (uidOrUser, isUser) ->
        OtherProfileDialog(
            targetUidOrUsername = uidOrUser,
            isUsername = isUser,
            myProfile = myProfile,
            repository = repository,
            onSelectUser = { u, flag -> inspectingOtherUser = u to flag },
            onDismiss = {
                inspectingOtherUser = null
                scope.launch { refreshMySocialMeta() }
            }
        )
    }

    followListModal?.let { (title, profiles) ->
        AlertDialog(
            onDismissRequest = { followListModal = null },
            containerColor = DressKitStyle.cardSurface(),
            title = { Text(title, color = primaryInk) },
            text = {
                if (profiles.isEmpty()) {
                    Text(localizedText(lang, "Henüz kimse yok.", "Nobody yet."), color = secondaryInk)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profiles.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        followListModal = null
                                        inspectingOtherUser = p.uid to false
                                    }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                UserAvatarCircle(p.avatarBase64, p.displayName.ifBlank { p.username }, size = 36.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.displayName.ifBlank { "@${p.username}" }, color = primaryInk, fontWeight = FontWeight.SemiBold)
                                    if (p.username.isNotBlank()) Text("@${p.username}", color = secondaryInk, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { followListModal = null }) {
                    Text(localizedText(lang, "Kapat", "Close"), color = primaryInk)
                }
            }
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
                .navigationBarsPadding()
                .imePadding(),
            color = DressKitStyle.canvasBg()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = primaryInk)
                        }
                        Text(
                            text = localizedText(lang, "Profilim & Dolap Vitrini", "My Profile & Showcase"),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = primaryInk
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActivityHeartIconButton(
                            unreadCount = unreadActivityCount,
                            onClick = onOpenActivity
                        )
                        if (myUid.isNotBlank() || myProfile.username.isNotBlank()) {
                            val shareLink = if (myProfile.username.isNotBlank()) {
                                "https://kyhlaer.github.io/Dolabim/?u=${myProfile.username}"
                            } else {
                                "https://kyhlaer.github.io/Dolabim/?uid=$myUid"
                            }
                            IconButton(onClick = {
                                AnalyticsService.trackFeature("profile_share")
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareLink)
                                }
                                context.startActivity(Intent.createChooser(intent, localizedText(lang, "Profilimi Paylaş", "Share Profile")))
                            }) {
                                Icon(Icons.Outlined.Share, contentDescription = null, tint = DressKitStyle.accentColor())
                            }
                        }
                    }
                }

                // 2-Tab Pill Bar (1:1 with Web: Profilim | Düzenle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to localizedText(lang, "Profilim", "My Profile"),
                        1 to localizedText(lang, "Düzenle", "Edit")
                    ).forEach { (idx, title) ->
                        val active = subTab == idx
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { subTab = idx },
                            shape = RoundedCornerShape(999.dp),
                            color = if (active) DressKitStyle.buttonBg() else DressKitStyle.secondarySurface(),
                            border = BorderStroke(1.dp, if (active) DressKitStyle.buttonBg() else DressKitStyle.borderColor())
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (active) DressKitStyle.buttonFg() else primaryInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (subTab) {
                    0 -> {
                        val summaryMap = remember(items, outfits, myProfile.statsVisibility) {
                            SocialProfileService.computeFilteredWardrobeSummary(items, outfits, myProfile.statsVisibility)
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 30.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Profile Header Card
                            item {
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            UserAvatarCircle(
                                                avatarBase64 = myProfile.avatarBase64,
                                                displayName = myProfile.displayName.ifBlank { myProfile.username.ifBlank { "Dolabım" } },
                                                size = 68.dp
                                            )
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(
                                                    text = myProfile.displayName.ifBlank {
                                                        if (myProfile.username.isNotBlank()) myProfile.username
                                                        else localizedText(lang, "Profilini Oluştur", "Set Up Profile")
                                                    },
                                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = primaryInk
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (myProfile.username.isNotBlank()) "@${myProfile.username}" else localizedText(lang, "Henüz kullanıcı adı yok", "No username set"),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (myProfile.username.isNotBlank()) DressKitStyle.accentColor() else secondaryInk
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = DressKitStyle.secondarySurface()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (myProfile.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(11.dp),
                                                                tint = secondaryInk
                                                            )
                                                            Text(
                                                                text = if (myProfile.isPrivate) localizedText(lang, "Gizli", "Private") else localizedText(lang, "Açık", "Public"),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = secondaryInk
                                                            )
                                                        }
                                                    }
                                                }
                                                if (myProfile.bio.isNotBlank()) {
                                                    Text(
                                                        text = myProfile.bio,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                                        color = primaryInk
                                                    )
                                                }
                                            }
                                        }

                                        if (myProfile.styleTags.isNotEmpty()) {
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                myProfile.styleTags.forEach { tag ->
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = DressKitStyle.accentSoft()
                                                    ) {
                                                        Text(
                                                            text = tag,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = DressKitStyle.accentColor(),
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Boxed Followers / Following tiles (1:1 with Web .profile-follow-counts)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (myUid.isNotBlank()) {
                                                            scope.launch {
                                                                SocialProfileService.fetchFollowList(context, myUid, followers = true)
                                                                    .onSuccess { followListModal = localizedText(lang, "Takipçiler", "Followers") to it }
                                                            }
                                                        }
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                color = DressKitStyle.secondarySurface(),
                                                border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        "$followersCount",
                                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
                                                        color = primaryInk
                                                    )
                                                    Text(localizedText(lang, "Takipçi", "Followers"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = secondaryInk)
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (myUid.isNotBlank()) {
                                                            scope.launch {
                                                                SocialProfileService.fetchFollowList(context, myUid, followers = false)
                                                                    .onSuccess { followListModal = localizedText(lang, "Takip Edilenler", "Following") to it }
                                                            }
                                                        }
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                color = DressKitStyle.secondarySurface(),
                                                border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        "$followingCount",
                                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
                                                        color = primaryInk
                                                    )
                                                    Text(localizedText(lang, "Takip", "Following"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = secondaryInk)
                                                }
                                            }
                                        }

                                        // Equal 2-col Edit / Share Profile row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { subTab = 1 },
                                                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = DressKitStyle.cardSurface(),
                                                    contentColor = primaryInk
                                                ),
                                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)
                                            ) {
                                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = primaryInk)
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(localizedText(lang, "Düzenle", "Edit"), color = primaryInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Button(
                                                onClick = {
                                                    AnalyticsService.trackFeature("profile_share")
                                                    val profileUrl = if (myProfile.username.isNotBlank()) {
                                                        "https://kyhlaer.github.io/Dolabim/?u=${myProfile.username}"
                                                    } else if (myUid.isNotBlank()) {
                                                        "https://kyhlaer.github.io/Dolabim/?uid=$myUid"
                                                    } else ""
                                                    if (profileUrl.isBlank()) {
                                                        Toast.makeText(context, localizedText(lang, "Önce profili kaydet", "Save profile first"), Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    val textToShare = buildString {
                                                        append("👗 ")
                                                        append(myProfile.displayName.ifBlank { "@${myProfile.username}" })
                                                        append(" · Dolabım\n")
                                                        summaryMap["totalItems"]?.let { append("Kıyafet: $it  ") }
                                                        summaryMap["outfitCount"]?.let { append("Kombin: $it  ") }
                                                        summaryMap["favoriteCount"]?.let { append("Favori: $it") }
                                                        append("\n$profileUrl")
                                                    }
                                                    val send = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, textToShare)
                                                    }
                                                    context.startActivity(Intent.createChooser(send, localizedText(lang, "Profilimi Paylaş", "Share Profile")))
                                                },
                                                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = DressKitStyle.buttonBg(),
                                                    contentColor = DressKitStyle.buttonFg()
                                                )
                                            ) {
                                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(localizedText(lang, "Profilimi Paylaş", "Share Profile"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }

                            // Pending Follow Requests Card
                            if (pendingRequests.isNotEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                        color = DressKitStyle.accentSoft(),
                                        border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = localizedText(lang, "Takip İstekleri (${pendingRequests.size})", "Follow Requests (${pendingRequests.size})"),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = primaryInk
                                            )
                                            pendingRequests.forEach { reqUser ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    UserAvatarCircle(reqUser.avatarBase64, reqUser.displayName.ifBlank { reqUser.username }, size = 34.dp)
                                                    Column(modifier = Modifier.weight(1f).clickable { inspectingOtherUser = reqUser.uid to false }) {
                                                        Text(reqUser.displayName.ifBlank { "@${reqUser.username}" }, color = primaryInk, style = MaterialTheme.typography.labelLarge)
                                                        if (reqUser.username.isNotBlank()) Text("@${reqUser.username}", color = secondaryInk, style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                SocialProfileService.acceptFollowRequest(context, reqUser.uid)
                                                                refreshMySocialMeta()
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.accentColor(), contentColor = Color.White)
                                                    ) { Text(localizedText(lang, "Onayla", "Accept"), fontSize = 11.sp) }
                                                    OutlinedButton(
                                                        onClick = {
                                                            scope.launch {
                                                                SocialProfileService.unfollowOrCancel(context, reqUser.uid, myUid)
                                                                refreshMySocialMeta()
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) { Text(localizedText(lang, "Reddet", "Decline"), fontSize = 11.sp, color = primaryInk) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Shared Wardrobe Summary Preview Card (WYSIWYG according to statsVisibility)
                            item {
                                WardrobeSummaryPublicCard(summary = summaryMap, lang = lang, isSelf = true)
                            }

                            // Showcase Section Selector + Grid
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = localizedText(lang, "Sergilenen Kıyafetler", "Wardrobe Showcase"),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = primaryInk
                                        )
                                        TextButton(onClick = onRequestCustomShowcasePhoto) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = DressKitStyle.accentColor())
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(localizedText(lang, "Özel Ekle", "Add Custom"), color = DressKitStyle.accentColor(), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item {
                                            FilterChipPill(
                                                label = localizedText(lang, "Tümü", "All"),
                                                selected = activeShowcaseSection == "all",
                                                onClick = { activeShowcaseSection = "all" }
                                            )
                                        }
                                        if ("selected" in myProfile.showcaseSections) {
                                            item {
                                                FilterChipPill(
                                                    label = localizedText(lang, "Seçtiklerim", "Selected"),
                                                    selected = activeShowcaseSection == "selected",
                                                    onClick = { activeShowcaseSection = "selected" }
                                                )
                                            }
                                        }
                                        if ("favorites" in myProfile.showcaseSections) {
                                            item {
                                                FilterChipPill(
                                                    label = localizedText(lang, "Favoriler", "Favorites"),
                                                    selected = activeShowcaseSection == "favorites",
                                                    onClick = { activeShowcaseSection = "favorites" }
                                                )
                                            }
                                        }
                                        if ("wishlist" in myProfile.showcaseSections) {
                                            item {
                                                FilterChipPill(
                                                    label = localizedText(lang, "İstek Listesi", "Wishlist"),
                                                    selected = activeShowcaseSection == "wishlist",
                                                    onClick = { activeShowcaseSection = "wishlist" }
                                                )
                                            }
                                        }
                                        if ("custom" in myProfile.showcaseSections || myProfile.customShowcaseItems.isNotEmpty()) {
                                            item {
                                                FilterChipPill(
                                                    label = localizedText(lang, "Özel Parçalar", "Custom"),
                                                    selected = activeShowcaseSection == "custom",
                                                    onClick = { activeShowcaseSection = "custom" }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val previewCards = buildList {
                                if ((activeShowcaseSection == "all" || activeShowcaseSection == "selected") && "selected" in myProfile.showcaseSections) {
                                    val setIds = myProfile.selectedShowcaseItemIds.toSet()
                                    items.filter { it.id in setIds }.forEach {
                                        add(ShowcaseDisplayTile(it.id, it.name.ifBlank { it.subCategory.display(lang) }, it.category.display(lang), localFileName = it.coverPhoto))
                                    }
                                }
                                if ((activeShowcaseSection == "all" || activeShowcaseSection == "favorites") && "favorites" in myProfile.showcaseSections) {
                                    items.filter { it.isFavorite }.forEach {
                                        add(ShowcaseDisplayTile("fav_${it.id}", it.name.ifBlank { it.subCategory.display(lang) }, it.category.display(lang), localFileName = it.coverPhoto))
                                    }
                                }
                                if ((activeShowcaseSection == "all" || activeShowcaseSection == "wishlist") && "wishlist" in myProfile.showcaseSections) {
                                    items.filter { it.isWishlist }.forEach {
                                        add(ShowcaseDisplayTile("wish_${it.id}", it.name.ifBlank { it.subCategory.display(lang) }, it.priceText.ifBlank { it.category.display(lang) }, localFileName = it.coverPhoto))
                                    }
                                }
                                if ((activeShowcaseSection == "all" || activeShowcaseSection == "custom") && ("custom" in myProfile.showcaseSections || myProfile.customShowcaseItems.isNotEmpty())) {
                                    myProfile.customShowcaseItems.forEach { cu ->
                                        add(ShowcaseDisplayTile("custom_${cu.id}", cu.name, cu.priceText.ifBlank { cu.category }, photoBase64 = cu.photoBase64))
                                    }
                                }
                            }

                            if (previewCards.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                        color = DressKitStyle.secondarySurface(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = localizedText(
                                                lang,
                                                "Vitrinin henüz boş. 'Düzenle' sekmesinden favorilerini, istek listeni veya özel seçtiklerini açıp 'Profili Kaydet & Yayınla'ya dokun.",
                                                "Your showcase is empty. Enable Favorites, Wishlist, Selected or Custom items in 'Edit'."
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryInk,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                }
                            } else {
                                val rows = previewCards.chunked(2)
                                items(rows) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        row.forEach { card ->
                                            ShowcaseMiniCard(tile = card, modifier = Modifier.weight(1f))
                                        }
                                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Düzenle Tab
                        Column(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Avatar Card
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        UserAvatarCircle(myProfile.avatarBase64, editDisplayName.ifBlank { editUsername }, size = 62.dp)
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(localizedText(lang, "Profil Fotoğrafı", "Profile Photo"), fontWeight = FontWeight.Bold, color = primaryInk)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedButton(
                                                    onClick = onPickAvatarGallery,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp), tint = primaryInk)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(localizedText(lang, "Galeri", "Gallery"), fontSize = 11.sp, color = primaryInk)
                                                }
                                                OutlinedButton(
                                                    onClick = onCaptureAvatarCamera,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp), tint = primaryInk)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(localizedText(lang, "Kamera", "Camera"), fontSize = 11.sp, color = primaryInk)
                                                }
                                                    if (myProfile.avatarBase64.isNotBlank()) {
                                                        IconButton(onClick = onRemoveAvatar, modifier = Modifier.size(32.dp)) {
                                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = DressKitStyle.dangerColor(), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                            }
                                        }
                                    }
                                }

                                // Basic Identity Card
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = editUsername,
                                            onValueChange = {
                                                editUsername = SocialProfileService.normalizeUsername(it).take(24)
                                                usernameAvailability = null
                                            },
                                            singleLine = true,
                                            prefix = { Text("@", color = DressKitStyle.accentColor()) },
                                            trailingIcon = {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        val res = SocialProfileService.checkUsernameAvailable(context, editUsername)
                                                        usernameAvailability = res.getOrDefault(false)
                                                    }
                                                }) {
                                                    Text(localizedText(lang, "Kontrol Et", "Check"), fontSize = 11.sp, color = DressKitStyle.accentColor())
                                                }
                                            },
                                            label = { Text(localizedText(lang, "Kullanıcı Adı", "Username")) },
                                            supportingText = {
                                                when (usernameAvailability) {
                                                    true -> Text(localizedText(lang, "Kullanılabilir ✓", "Available ✓"), color = DressKitColors.OliveSage)
                                                    false -> Text(localizedText(lang, "Alınmış veya geçersiz", "Taken or invalid"), color = DressKitColors.DangerRust)
                                                    null -> Text(localizedText(lang, "3-24 karakter: a-z, 0-9, _, .", "3-24 chars: a-z, 0-9, _, ."), color = secondaryInk)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = editDisplayName,
                                            onValueChange = { editDisplayName = it.take(50) },
                                            singleLine = true,
                                            label = { Text(localizedText(lang, "Görünen Ad", "Display Name")) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = editBio,
                                            onValueChange = { editBio = it.take(220) },
                                            maxLines = 3,
                                            label = { Text(localizedText(lang, "Açıklama (Bio)", "Bio")) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(localizedText(lang, "Tarz Etiketleri (En fazla 4)", "Style Tags (Up to 4)"), style = MaterialTheme.typography.labelLarge, color = secondaryInk)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            STYLE_VIBE_PRESETS.forEach { tag ->
                                                val sel = tag in editTags
                                                FilterChipPill(
                                                    label = tag,
                                                    selected = sel,
                                                    onClick = {
                                                        editTags = if (sel) editTags - tag else (editTags + tag).take(4)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Account Privacy Card (2-Segment Toggle matching Web 1:1)
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = localizedText(lang, "Gizlilik Ayarı", "Account Privacy"),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = primaryInk
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = DressKitStyle.secondarySurface(),
                                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf(
                                                    false to (Icons.Outlined.Public to localizedText(lang, "Herkese Açık", "Public")),
                                                    true to (Icons.Outlined.Lock to localizedText(lang, "Gizli Profil", "Private"))
                                                ).forEach { (priv, iconAndLabel) ->
                                                    val (ico, lbl) = iconAndLabel
                                                    val sel = editIsPrivate == priv
                                                    Surface(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable { editIsPrivate = priv },
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = if (sel) DressKitStyle.buttonBg() else Color.Transparent
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(ico, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (sel) DressKitStyle.buttonFg() else primaryInk)
                                                            Spacer(modifier = Modifier.width(5.dp))
                                                            Text(
                                                                text = lbl,
                                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                                color = if (sel) DressKitStyle.buttonFg() else primaryInk,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = if (editIsPrivate) localizedText(lang, "Dolap özetin ve sergi parçaların yalnızca onayladığın takipçilere görünür.", "Only approved followers can view your summary and showcase.")
                                            else localizedText(lang, "Profil linkinle herkes dolap özetini ve seçtiğin vitrini görebilir.", "Anyone with your link or searching can view your profile."),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryInk
                                        )
                                    }
                                }

                                // Shared Stats Visibility Controls (Exact 9 toggles 1:1 with Web)
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = localizedText(lang, "Paylaşılan İstatistikleri Seç", "Shared Summary Statistics Visibility"),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = primaryInk
                                        )
                                        StatToggleRow(localizedText(lang, "Toplam Kıyafet & Fotoğraf Sayısı", "Total Clothes & Photos"), editVis.showTotalItems) {
                                            editVis = editVis.copy(showTotalItems = it)
                                        }
                                        StatToggleRow(localizedText(lang, "Gardırop Renk Paleti", "Wardrobe Color Palette"), editVis.showColorPalette) {
                                            editVis = editVis.copy(showColorPalette = it)
                                        }
                                        StatToggleRow(localizedText(lang, "Favori Parça Sayısı", "Favorite Count"), editVis.showFavoriteCount) {
                                            editVis = editVis.copy(showFavoriteCount = it)
                                        }
                                        StatToggleRow(localizedText(lang, "İstek Listesi Sayısı", "Wishlist Count"), editVis.showWishlistCount) {
                                            editVis = editVis.copy(showWishlistCount = it)
                                        }
                                        StatToggleRow(localizedText(lang, "Kayıtlı Kombin Sayısı", "Saved Outfits Count"), editVis.showOutfitCount) {
                                            editVis = editVis.copy(showOutfitCount = it)
                                        }
                                        StatToggleRow(localizedText(lang, "Kategori Dağılımı", "Category Breakdown"), editVis.showCategoryBreakdown) {
                                            editVis = editVis.copy(showCategoryBreakdown = it)
                                        }
                                        StatToggleRow(localizedText(lang, "En Baskın Renkler", "Top Wardrobe Colors"), editVis.showTopColors) {
                                            editVis = editVis.copy(showTopColors = it)
                                        }
                                        StatToggleRow(localizedText(lang, "En Çok Bulunan Kıyafet Türleri", "Top Clothing Types"), editVis.showTopSubCategories) {
                                            editVis = editVis.copy(showTopSubCategories = it)
                                        }
                                        StatToggleRow(localizedText(lang, "Tahmini Dolap & İstek Değeri (₺)", "Estimated Value (₺)"), editVis.showEstimatedValue) {
                                            editVis = editVis.copy(showEstimatedValue = it)
                                        }
                                    }
                                }

                                // Showcase Section Selector + Picker
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = localizedText(lang, "Profilde Sergilenecek Kıyafetler", "Showcase Sections"),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = primaryInk
                                        )
                                        StatToggleRow(localizedText(lang, "Favorilerim", "My Favorites"), "favorites" in editSections) { on ->
                                            editSections = if (on) (editSections + "favorites").distinct() else editSections - "favorites"
                                        }
                                        StatToggleRow(localizedText(lang, "İstek Listem", "My Wishlist"), "wishlist" in editSections) { on ->
                                            editSections = if (on) (editSections + "wishlist").distinct() else editSections - "wishlist"
                                        }
                                        StatToggleRow(localizedText(lang, "Dolabımdan Seçtiklerim (${editSelectedIds.size})", "Selected Pieces (${editSelectedIds.size})"), "selected" in editSections) { on ->
                                            editSections = if (on) (editSections + "selected").distinct() else editSections - "selected"
                                        }
                                        if ("selected" in editSections && items.isNotEmpty()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 180.dp)
                                                    .verticalScroll(rememberScrollState())
                                                    .background(DressKitStyle.secondarySurface(), RoundedCornerShape(10.dp))
                                                    .padding(6.dp)
                                            ) {
                                                items.take(40).forEach { gar ->
                                                    val checked = gar.id in editSelectedIds
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                editSelectedIds = if (checked) editSelectedIds - gar.id else (editSelectedIds + gar.id).take(12)
                                                            },
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(checked = checked, onCheckedChange = {
                                                            editSelectedIds = if (checked) editSelectedIds - gar.id else (editSelectedIds + gar.id).take(12)
                                                        })
                                                        Text(
                                                            text = gar.name.ifBlank { gar.subCategory.display(lang) },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = primaryInk,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        StatToggleRow(localizedText(lang, "Özel Parçalarım (${editCustomItems.size})", "Custom Pieces (${editCustomItems.size})"), "custom" in editSections) { on ->
                                            editSections = if (on) (editSections + "custom").distinct() else editSections - "custom"
                                        }
                                        OutlinedButton(onClick = onRequestCustomShowcasePhoto, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = DressKitStyle.accentColor())
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(localizedText(lang, "Galeriden Farklı Kıyafet Ekle", "Upload Custom Showcase Piece"), color = DressKitStyle.accentColor())
                                        }
                                        editCustomItems.forEach { cu ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(cu.name, style = MaterialTheme.typography.bodySmall, color = primaryInk, modifier = Modifier.weight(1f))
                                                IconButton(onClick = { editCustomItems = editCustomItems.filterNot { it.id == cu.id } }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = DressKitStyle.dangerColor(), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Blocked Accounts Management Card
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(17.dp), tint = DressKitStyle.dangerColor())
                                            Text(
                                                text = localizedText(lang, "Engellenen Hesaplar (${blockedAccounts.size})", "Blocked Accounts (${blockedAccounts.size})"),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = primaryInk
                                            )
                                        }
                                        if (blockedAccounts.isEmpty()) {
                                            Text(
                                                text = localizedText(lang, "Engellediğin kullanıcı bulunmuyor.", "You haven't blocked anyone."),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = secondaryInk
                                            )
                                        } else {
                                            blockedAccounts.forEach { blocked ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        UserAvatarCircle(blocked.avatarBase64, blocked.displayName.ifBlank { blocked.username }, size = 30.dp)
                                                        Column {
                                                            Text(
                                                                text = blocked.displayName.ifBlank { "@${blocked.username}".ifBlank { "Kullanıcı" } },
                                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                                color = primaryInk,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (blocked.username.isNotBlank()) {
                                                                Text("@${blocked.username}", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
                                                            }
                                                        }
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            scope.launch {
                                                                SocialProfileService.unblockUser(context, blocked.uid)
                                                                    .onSuccess {
                                                                        blockedAccounts = blockedAccounts.filterNot { it.uid == blocked.uid }
                                                                        Toast.makeText(context, localizedText(lang, "Engel kaldırıldı", "Unblocked"), Toast.LENGTH_SHORT).show()
                                                                    }
                                                                    .onFailure { err ->
                                                                        Toast.makeText(context, err.message ?: "Hata", Toast.LENGTH_SHORT).show()
                                                                    }
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(localizedText(lang, "Engeli Kaldır", "Unblock"), fontSize = 11.sp, color = primaryInk)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            Surface(
                                color = DressKitStyle.cardSurface(),
                                shadowElevation = 6.dp
                            ) {
                                Button(
                                    onClick = {
                                        if (editUsername.isNotBlank() && !SocialProfileService.isValidUsername(editUsername)) {
                                            Toast.makeText(
                                                context,
                                                localizedText(lang, "Kullanıcı adı 3-24 karakter olmalı (a-z, 0-9, _, .)", "Invalid username format"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@Button
                                        }
                                        scope.launch {
                                            isSavingProfile = true
                                            AnalyticsService.trackFeature("profile_edit")
                                            val draft = myProfile.copy(
                                                username = SocialProfileService.normalizeUsername(editUsername),
                                                displayName = editDisplayName.trim(),
                                                bio = editBio.trim(),
                                                isPrivate = editIsPrivate,
                                                styleTags = editTags,
                                                statsVisibility = editVis,
                                                showcaseSections = editSections,
                                                selectedShowcaseItemIds = editSelectedIds,
                                                customShowcaseItems = editCustomItems
                                            )
                                            val res = SocialProfileService.saveAndSyncProfile(context, draft, items, outfits)
                                            isSavingProfile = false
                                            res.onSuccess { updated ->
                                                onProfileUpdated(updated)
                                                Toast.makeText(context, localizedText(lang, "Profil güncellendi ve senkronize edildi", "Profile saved & synced"), Toast.LENGTH_SHORT).show()
                                                subTab = 0
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.message ?: "Hata", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    enabled = !isSavingProfile,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                    colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                                ) {
                                    if (isSavingProfile) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DressKitStyle.buttonFg())
                                    } else {
                                        Text(localizedText(lang, "Profili Kaydet & Yayınla", "Save & Publish Profile"), fontWeight = FontWeight.Bold)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OtherProfileDialog(
    targetUidOrUsername: String,
    isUsername: Boolean,
    myProfile: LocalProfileConfig,
    repository: WardrobeRepository,
    onSelectUser: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val dangerInk = DressKitStyle.dangerColor()

    var loading by remember { mutableStateOf(true) }
    var bundle by remember { mutableStateOf<LoadedProfileBundle?>(null) }
    var followBusy by remember { mutableStateOf(false) }
    var blockBusy by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf("all") }
    var inspectShowcase by remember { mutableStateOf<ShowcaseItemData?>(null) }
    var followListModal by remember { mutableStateOf<Pair<String, List<PublicProfileHeader>>?>(null) }
    val myUid = remember { AnalyticsService.getCurrentUid(context) }

    suspend fun reload() {
        loading = true
        SocialProfileService.fetchProfileBundle(context, targetUidOrUsername, isUsername)
            .onSuccess { bundle = it }
        loading = false
    }

    LaunchedEffect(targetUidOrUsername, isUsername) {
        reload()
    }

    if (showBlockConfirm) {
        val target = bundle?.header
        if (target != null) {
            AlertDialog(
                onDismissRequest = { showBlockConfirm = false },
                containerColor = DressKitStyle.cardSurface(),
                title = { Text(localizedText(lang, "Kullanıcıyı Engelle", "Block User"), color = primaryInk) },
                text = {
                    Text(
                        text = localizedText(
                            lang,
                            "${target.displayName.ifBlank { "@${target.username}" }} engellensin mi? Takipler kaldırılır ve birbirinizin içeriklerini göremezsiniz.",
                            "Block ${target.displayName.ifBlank { "@${target.username}" }}? Follow relations will be removed and you won't see each other's content."
                        ),
                        color = secondaryInk
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (blockBusy) return@Button
                            showBlockConfirm = false
                            scope.launch {
                                blockBusy = true
                                SocialProfileService.blockUser(context, target.uid)
                                    .onSuccess {
                                        Toast.makeText(context, localizedText(lang, "Kullanıcı engellendi", "User blocked"), Toast.LENGTH_SHORT).show()
                                        reload()
                                    }
                                    .onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Hata", Toast.LENGTH_SHORT).show()
                                    }
                                blockBusy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = dangerInk, contentColor = Color.White)
                    ) { Text(localizedText(lang, "Engelle", "Block")) }
                },
                dismissButton = {
                    TextButton(onClick = { showBlockConfirm = false }) { Text(localizedText(lang, "Vazgeç", "Cancel"), color = primaryInk) }
                }
            )
        }
    }

    inspectShowcase?.let { sc ->
        ShowcaseInspectDialog(
            sc = sc,
            repository = repository,
            onDismiss = { inspectShowcase = null }
        )
    }

    followListModal?.let { (title, list) ->
        AlertDialog(
            onDismissRequest = { followListModal = null },
            containerColor = DressKitStyle.cardSurface(),
            title = { Text(title, color = primaryInk) },
            text = {
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (list.isEmpty()) Text(localizedText(lang, "Kimse yok.", "Empty."), color = secondaryInk)
                    list.forEach { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                followListModal = null
                                onSelectUser(p.uid, false)
                            }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UserAvatarCircle(p.avatarBase64, p.displayName.ifBlank { p.username }, size = 34.dp)
                            Column {
                                Text(p.displayName.ifBlank { "@${p.username}" }, color = primaryInk, fontWeight = FontWeight.SemiBold)
                                if (p.username.isNotBlank()) Text("@${p.username}", color = secondaryInk, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { followListModal = null }) { Text(localizedText(lang, "Kapat", "Close"), color = primaryInk) } }
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
                .navigationBarsPadding()
                .imePadding(),
            color = DressKitStyle.canvasBg()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = primaryInk)
                    }
                    Text(
                        text = localizedText(lang, "Kullanıcı Profili", "User Profile"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryInk
                    )
                    IconButton(
                        onClick = {
                            val h = bundle?.header ?: return@IconButton
                            val link = if (h.username.isNotBlank()) "https://kyhlaer.github.io/Dolabim/?u=${h.username}"
                            else "https://kyhlaer.github.io/Dolabim/?uid=${h.uid}"
                            val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
                            context.startActivity(Intent.createChooser(i, localizedText(lang, "Profili Paylaş", "Share Profile")))
                        },
                        enabled = bundle != null
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = primaryInk)
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DressKitStyle.accentColor())
                    }
                } else if (bundle == null) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(localizedText(lang, "Kullanıcı profili bulunamadı.", "Profile not found."), color = secondaryInk)
                    }
                } else {
                    val b = bundle!!
                    val h = b.header
                    val shareThisProfile = {
                        val link = if (h.username.isNotBlank()) "https://kyhlaer.github.io/Dolabim/?u=${h.username}"
                        else "https://kyhlaer.github.io/Dolabim/?uid=${h.uid}"
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
                        context.startActivity(Intent.createChooser(i, localizedText(lang, "Profili Paylaş", "Share Profile")))
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                color = DressKitStyle.cardSurface(),
                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        UserAvatarCircle(h.avatarBase64, h.displayName.ifBlank { h.username }, size = 68.dp)
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(h.displayName.ifBlank { "@${h.username}" }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = primaryInk)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (h.username.isNotBlank()) {
                                                    Text("@${h.username}", style = MaterialTheme.typography.labelMedium, color = DressKitStyle.accentColor())
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = DressKitStyle.secondarySurface()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (h.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(11.dp),
                                                            tint = secondaryInk
                                                        )
                                                        Text(
                                                            text = if (h.isPrivate) localizedText(lang, "Gizli", "Private") else localizedText(lang, "Açık", "Public"),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = secondaryInk
                                                        )
                                                    }
                                                }
                                            }
                                            if (h.bio.isNotBlank()) {
                                                Text(
                                                    h.bio,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                                    color = primaryInk
                                                )
                                            }
                                        }
                                    }

                                    if (h.styleTags.isNotEmpty()) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            h.styleTags.forEach { tag ->
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = DressKitStyle.accentSoft()
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = DressKitStyle.accentColor(),
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = b.canViewContent) {
                                                    scope.launch {
                                                        SocialProfileService.fetchFollowList(context, h.uid, followers = true)
                                                            .onSuccess { followListModal = localizedText(lang, "Takipçiler", "Followers") to it }
                                                    }
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            color = DressKitStyle.secondarySurface(),
                                            border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    "${b.followersCount}",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
                                                    color = primaryInk
                                                )
                                                Text(localizedText(lang, "Takipçi", "Followers"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = secondaryInk)
                                            }
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = b.canViewContent) {
                                                    scope.launch {
                                                        SocialProfileService.fetchFollowList(context, h.uid, followers = false)
                                                            .onSuccess { followListModal = localizedText(lang, "Takip Edilenler", "Following") to it }
                                                    }
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            color = DressKitStyle.secondarySurface(),
                                            border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    "${b.followingCount}",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
                                                    color = primaryInk
                                                )
                                                Text(localizedText(lang, "Takip", "Following"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = secondaryInk)
                                            }
                                        }
                                    }

                                    if (h.uid != myUid) {
                                        if (b.isBlockedByMe) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        if (blockBusy) return@OutlinedButton
                                                        scope.launch {
                                                            blockBusy = true
                                                            SocialProfileService.unblockUser(context, h.uid)
                                                                .onSuccess {
                                                                    Toast.makeText(context, localizedText(lang, "Engel kaldırıldı", "Unblocked"), Toast.LENGTH_SHORT).show()
                                                                    reload()
                                                                }
                                                                .onFailure { err ->
                                                                    Toast.makeText(context, err.message ?: "Hata", Toast.LENGTH_SHORT).show()
                                                                }
                                                            blockBusy = false
                                                        }
                                                    },
                                                    enabled = !blockBusy,
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = DressKitStyle.cardSurface(),
                                                        contentColor = primaryInk
                                                    ),
                                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(16.dp), tint = primaryInk)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(localizedText(lang, "Engeli Kaldır", "Unblock User"), color = primaryInk)
                                                }
                                                OutlinedButton(
                                                    onClick = shareThisProfile,
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = DressKitStyle.cardSurface(),
                                                        contentColor = primaryInk
                                                    ),
                                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = primaryInk)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(localizedText(lang, "Profili Paylaş", "Share Profile"), color = primaryInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        } else if (!b.hasBlockedMe) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        if (followBusy) return@Button
                                                        scope.launch {
                                                            followBusy = true
                                                            if (b.followStatus != null) {
                                                                SocialProfileService.unfollowOrCancel(context, myUid, h.uid)
                                                            } else {
                                                                SocialProfileService.followUser(context, h)
                                                            }
                                                            followBusy = false
                                                            reload()
                                                        }
                                                    },
                                                    enabled = !followBusy,
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                                    border = if (b.followStatus != null) BorderStroke(1.dp, DressKitStyle.borderColor()) else null,
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (b.followStatus == null) DressKitStyle.buttonBg() else DressKitStyle.cardSurface(),
                                                        contentColor = if (b.followStatus == null) DressKitStyle.buttonFg() else primaryInk
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = if (b.followStatus == "accepted") Icons.Outlined.Check else Icons.Outlined.PersonAdd,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = when (b.followStatus) {
                                                            "accepted" -> localizedText(lang, "Takip Ediliyor (Bırak)", "Following (Unfollow)")
                                                            "pending" -> localizedText(lang, "İstek Gönderildi (İptal)", "Requested (Cancel)")
                                                            else -> if (h.isPrivate) localizedText(lang, "Takip İsteği Gönder", "Request Follow") else localizedText(lang, "Takip Et", "Follow")
                                                        },
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = shareThisProfile,
                                                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            containerColor = DressKitStyle.cardSurface(),
                                                            contentColor = primaryInk
                                                        ),
                                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)
                                                    ) {
                                                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = primaryInk)
                                                        Spacer(modifier = Modifier.width(5.dp))
                                                        Text(localizedText(lang, "Profili Paylaş", "Share Profile"), color = primaryInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { showBlockConfirm = true },
                                                        enabled = !blockBusy,
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            containerColor = DressKitStyle.cardSurface(),
                                                            contentColor = dangerInk
                                                        ),
                                                        border = BorderStroke(1.dp, dangerInk.copy(alpha = 0.70f)),
                                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)
                                                    ) {
                                                        Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(15.dp), tint = dangerInk)
                                                        Spacer(modifier = Modifier.width(5.dp))
                                                        Text(localizedText(lang, "Engelle", "Block"), color = dangerInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = shareThisProfile,
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = DressKitStyle.cardSurface(),
                                                    contentColor = primaryInk
                                                ),
                                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                                            ) {
                                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = primaryInk)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(localizedText(lang, "Profili Paylaş", "Share Profile"), color = primaryInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = shareThisProfile,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = DressKitStyle.cardSurface(),
                                                contentColor = primaryInk
                                            ),
                                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                                        ) {
                                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = primaryInk)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(localizedText(lang, "Profili Paylaş", "Share Profile"), color = primaryInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }

                        if (!b.canViewContent) {
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
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isBlockedState = b.isBlockedByMe || b.hasBlockedMe
                                        Icon(
                                            imageVector = if (isBlockedState) Icons.Outlined.Block else Icons.Outlined.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(34.dp),
                                            tint = if (isBlockedState) dangerInk else DressKitStyle.accentColor()
                                        )
                                        Text(
                                            text = when {
                                                b.isBlockedByMe -> localizedText(lang, "Bu Kullanıcıyı Engelledin", "You Blocked This User")
                                                b.hasBlockedMe -> localizedText(lang, "Bu Profile Erişilemiyor", "Profile Unavailable")
                                                else -> localizedText(lang, "Bu Hesap Gizli", "This Profile Is Private")
                                            },
                                            fontWeight = FontWeight.Bold,
                                            color = primaryInk
                                        )
                                        Text(
                                            text = when {
                                                b.isBlockedByMe -> localizedText(lang, "Engeli kaldırdığında profilini ve paylaşımlarını yeniden görebilirsin.", "Unblock this user to view their profile and posts again.")
                                                b.hasBlockedMe -> localizedText(lang, "Kullanıcı içeriği şu anda görüntülenemiyor.", "This user's content is currently unavailable.")
                                                else -> localizedText(
                                                    lang,
                                                    "Dolap özetini ve sergilenen kıyafetleri görmek için takip isteğinin onaylanması gerekiyor.",
                                                    "Follow this user to view their wardrobe summary and showcase once approved."
                                                )
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryInk
                                        )
                                    }
                                }
                            }
                        } else {
                            b.content?.let { cnt ->
                                if (cnt.wardrobeSummary.isNotEmpty()) {
                                    item { WardrobeSummaryPublicCard(summary = cnt.wardrobeSummary, lang = lang, isSelf = false) }
                                }
                            }

                            if (b.showcaseItems.isNotEmpty()) {
                                item {
                                    val availSections = b.showcaseItems.map { it.section }.distinct()
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item {
                                            FilterChipPill(
                                                label = localizedText(lang, "Tümü (${b.showcaseItems.size})", "All (${b.showcaseItems.size})"),
                                                selected = activeSection == "all",
                                                onClick = { activeSection = "all" }
                                            )
                                        }
                                        availSections.forEach { sec ->
                                            val lbl = when (sec) {
                                                "favorites" -> localizedText(lang, "Favoriler", "Favorites")
                                                "wishlist" -> localizedText(lang, "İstek Listesi", "Wishlist")
                                                "selected" -> localizedText(lang, "Seçtikleri", "Selected")
                                                else -> localizedText(lang, "Özel Parçalar", "Custom")
                                            }
                                            item {
                                                FilterChipPill(label = lbl, selected = activeSection == sec, onClick = { activeSection = sec })
                                            }
                                        }
                                    }
                                }
                                val filtered = b.showcaseItems.filter { activeSection == "all" || it.section == activeSection }
                                val rows = filtered.chunked(2)
                                items(rows) { row ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEach { sc ->
                                            ShowcaseMiniCard(
                                                tile = ShowcaseDisplayTile(
                                                    key = sc.itemId,
                                                    title = sc.name,
                                                    subTitle = sc.priceText.ifBlank { runCatching { ClothingCategory.valueOf(sc.category).display(lang) }.getOrDefault(sc.category) },
                                                    photoBase64 = sc.photoBase64
                                                ),
                                                onClick = { inspectShowcase = sc },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
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
private fun ShowcaseInspectDialog(
    sc: ShowcaseItemData,
    repository: WardrobeRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val likeKey = "sc_${sc.ownerUid.take(18)}_${sc.itemId.take(40)}"
    var liked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(likeKey) {
        val (l, c) = SocialProfileService.fetchLikeInfo(context, likeKey)
        liked = l
        likeCount = c
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(DressKitStyle.cardCorner()),
            color = DressKitStyle.cardSurface(),
            border = BorderStroke(1.dp, DressKitStyle.borderColor())
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val bmp = remember(sc.photoBase64) {
                    if (sc.photoBase64.isNotBlank()) SocialProfileService.decodeDataUrlToBitmap(sc.photoBase64)?.asImageBitmap() else null
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = sc.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(DressKitStyle.photoBoxSurface())
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(sc.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = primaryInk, modifier = Modifier.weight(1f))
                    Surface(
                        modifier = Modifier.clickable {
                            scope.launch {
                                SocialProfileService.toggleLike(context, likeKey).onSuccess { (l, c) -> liked = l; likeCount = c }
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = if (liked) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface()
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (liked) DressKitStyle.accentColor() else primaryInk)
                            Text("$likeCount", style = MaterialTheme.typography.labelSmall, color = primaryInk)
                        }
                    }
                }
                if (sc.priceText.isNotBlank()) Text(sc.priceText, color = DressKitStyle.accentColor(), fontWeight = FontWeight.SemiBold)
                if (sc.notes.isNotBlank()) Text(sc.notes, style = MaterialTheme.typography.bodySmall, color = secondaryInk)
                if (PriceTrackerService.isSafeHttpsUrl(sc.productUrl)) {
                    OutlinedButton(onClick = { SecurityGuards.openExternalUrlSafely(context, sc.productUrl) }, modifier = Modifier.fillMaxWidth()) {
                        Text(localizedText(lang, "Mağazada Aç", "Open Store"), color = primaryInk)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                SocialProfileService.importRemoteGarmentLocally(
                                    context, repository, sc.name, sc.category, sc.subCategory, sc.styleTag,
                                    sc.seasons, sc.colors, sc.notes, sc.priceText, sc.productUrl,
                                    listOfNotNull(sc.photoBase64.takeIf { it.isNotBlank() }), asWishlist = true
                                )
                                Toast.makeText(context, localizedText(lang, "İstek listene eklendi", "Added to wishlist"), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(localizedText(lang, "İstek Listeme", "Wishlist"), color = primaryInk, fontSize = 12.sp) }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                    ) { Text(localizedText(lang, "Kapat", "Close"), fontSize = 12.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WardrobeSummaryPublicCard(
    summary: Map<String, Any>,
    lang: AppLanguage,
    isSelf: Boolean
) {
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    Surface(
        shape = RoundedCornerShape(DressKitStyle.cardCorner()),
        color = DressKitStyle.cardSurface(),
        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isSelf) localizedText(lang, "Paylaşılan Dolap Özeti (Önizleme)", "Shared Wardrobe Summary (Preview)")
                else localizedText(lang, "Dolap Özeti", "Wardrobe Summary"),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = primaryInk
            )
            if (summary.isEmpty()) {
                Text(localizedText(lang, "Paylaşılan istatistikler gizlendi.", "Statistics hidden by owner."), style = MaterialTheme.typography.bodySmall, color = secondaryInk)
            } else {
                val statChips = buildList {
                    summary["totalItems"]?.let { add(localizedText(lang, "Kıyafet", "Items") to "$it") }
                    summary["totalPhotos"]?.let { add(localizedText(lang, "Fotoğraf", "Photos") to "$it") }
                    summary["outfitCount"]?.let { add(localizedText(lang, "Kombin", "Outfits") to "$it") }
                    summary["favoriteCount"]?.let { add(localizedText(lang, "Favori", "Favorites") to "$it") }
                    summary["wishlistCount"]?.let { add(localizedText(lang, "İstek", "Wishlist") to "$it") }
                }
                if (statChips.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        statChips.chunked(4).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { (lbl, v) ->
                                    MiniStatChip(lbl, v, Modifier.weight(1f))
                                }
                                repeat((4 - row.size).coerceAtLeast(0)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                val paletteParts = summary["colorPalette"]?.toString().orEmpty()
                    .split(',')
                    .filter { it.isNotBlank() }
                    .mapNotNull { seg ->
                        val p = seg.split(':')
                        if (p.size == 2) {
                            val col = runCatching { GarmentColor.valueOf(p[0]) }.getOrNull() ?: return@mapNotNull null
                            val cnt = (p[1].toIntOrNull() ?: 0).coerceAtLeast(1)
                            col to cnt
                        } else null
                    }
                if (paletteParts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = localizedText(lang, "Gardırop Renk Paleti", "Wardrobe Color Palette"),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = secondaryInk
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .border(1.dp, DressKitStyle.borderColor(), RoundedCornerShape(999.dp))
                        ) {
                            paletteParts.forEach { (color, count) ->
                                Box(
                                    modifier = Modifier
                                        .weight(count.toFloat())
                                        .fillMaxHeight()
                                        .background(color.composeColor)
                                )
                            }
                        }
                    }
                }

                val topColorParts = summary["topColors"]?.toString().orEmpty()
                    .split(',')
                    .filter { it.isNotBlank() }
                    .mapNotNull { seg ->
                        val p = seg.split(':')
                        if (p.size == 2) {
                            val col = runCatching { GarmentColor.valueOf(p[0]) }.getOrNull() ?: return@mapNotNull null
                            col to p[1]
                        } else null
                    }
                if (topColorParts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        topColorParts.forEach { (col, cnt) ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = DressKitStyle.cardSurface(),
                                border = BorderStroke(1.dp, DressKitStyle.borderColor())
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    ColorDot(color = col.composeColor, size = 10.dp)
                                    Text("${col.display(lang)} ($cnt)", style = MaterialTheme.typography.labelSmall, color = primaryInk)
                                }
                            }
                        }
                    }
                }

                val catStr = summary["categoryCounts"]?.toString().orEmpty()
                if (catStr.isNotBlank()) {
                    val parts = catStr.split(',').mapNotNull { seg ->
                        val p = seg.split(':')
                        if (p.size == 2) {
                            val lbl = runCatching { ClothingCategory.valueOf(p[0]).display(lang) }.getOrDefault(p[0])
                            "$lbl (${p[1]})"
                        } else null
                    }
                    if (parts.isNotEmpty()) {
                        Text(
                            text = parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryInk
                        )
                    }
                }

                val subStr = summary["topSubCategories"]?.toString().orEmpty()
                if (subStr.isNotBlank()) {
                    val subLabels = subStr.split(',').mapNotNull { seg ->
                        val p = seg.split(':')
                        if (p.size == 2) {
                            val lbl = runCatching { ClothingSubCategory.valueOf(p[0]).display(lang) }.getOrDefault(p[0])
                            "${p[1]} $lbl"
                        } else null
                    }
                    if (subLabels.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            subLabels.forEach { txt ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = DressKitStyle.secondarySurface()
                                ) {
                                    Text(
                                        text = txt,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                        color = primaryInk,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                val hasWish = summary.containsKey("estimatedWishlistValue")
                val hasCloset = summary.containsKey("estimatedWardrobeValue")
                if (hasWish || hasCloset) {
                    val wishVal = (summary["estimatedWishlistValue"] as? Number)?.toLong() ?: 0L
                    val closetVal = (summary["estimatedWardrobeValue"] as? Number)?.toLong() ?: 0L
                    Text(
                        text = "${localizedText(lang, "Tahmini Değer:", "Estimated Value:")} $closetVal ₺ (${localizedText(lang, "Dolap", "Closet")}) · $wishVal ₺ (${localizedText(lang, "İstek", "Wishlist")})",
                        style = MaterialTheme.typography.labelMedium,
                        color = DressKitStyle.accentColor()
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(DressKitStyle.cardCorner()),
        color = DressKitStyle.cardSurface(),
        border = BorderStroke(1.dp, DressKitStyle.borderColor())
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                color = DressKitStyle.primaryInk()
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = DressKitStyle.secondaryInk(),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DressKitStyle.primaryInk(), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.8f))
    }
}

private data class ShowcaseDisplayTile(
    val key: String,
    val title: String,
    val subTitle: String,
    val localFileName: String? = null,
    val photoBase64: String? = null
)

@Composable
private fun ShowcaseMiniCard(
    tile: ShowcaseDisplayTile,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = DressKitStyle.cardSurface(),
        border = BorderStroke(1.dp, DressKitStyle.borderColor())
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(DressKitStyle.photoBoxSurface()),
                contentAlignment = Alignment.Center
            ) {
                val remoteBmp = remember(tile.photoBase64) {
                    tile.photoBase64?.takeIf { it.isNotBlank() }?.let { SocialProfileService.decodeDataUrlToBitmap(it)?.asImageBitmap() }
                }
                if (remoteBmp != null) {
                    Image(
                        bitmap = remoteBmp,
                        contentDescription = tile.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                } else if (!tile.localFileName.isNullOrBlank()) {
                    val file = remember(tile.localFileName) {
                        com.dresskit.wardrobe.data.ImageStorage.getPhotoFile(context, tile.localFileName)
                    }
                    coil.compose.AsyncImage(
                        model = file,
                        contentDescription = tile.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                } else {
                    Icon(Icons.Outlined.Checkroom, contentDescription = null, tint = DressKitColors.MutedInk, modifier = Modifier.size(32.dp))
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(tile.title, style = MaterialTheme.typography.labelLarge, color = DressKitStyle.primaryInk(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tile.subTitle.isNotBlank()) {
                    Text(tile.subTitle, style = MaterialTheme.typography.labelSmall, color = DressKitStyle.secondaryInk(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun ShareOutfitDialog(
    title: String,
    pieces: List<ClothingItem>,
    harmonyScore: Int,
    weatherLabel: String,
    profile: LocalProfileConfig,
    allItems: List<ClothingItem>,
    allOutfits: List<Outfit>,
    onProfileUpdated: (LocalProfileConfig) -> Unit,
    onOpenProfileSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    val (_, harmonyNote) = remember(pieces, lang) {
        OutfitRecommendationEngine.calculateColorHarmony(pieces, lang)
    }

    var curProfile by remember { mutableStateOf(profile) }
    var isUploading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var sharedData by remember { mutableStateOf<SharedOutfitData?>(null) }

    var showInlineCreate by remember { mutableStateOf(false) }
    var inlineUsername by remember { mutableStateOf("") }
    var inlineDisplayName by remember { mutableStateOf(curProfile.displayName) }
    var isSavingUser by remember { mutableStateOf(false) }
    var userError by remember { mutableStateOf<String?>(null) }

    suspend fun uploadOutfitShare() {
        isUploading = true
        errorMsg = null
        val res = SocialProfileService.shareOutfit(
            context = context,
            title = title,
            pieces = pieces,
            harmonyScore = harmonyScore,
            harmonyNote = harmonyNote,
            weatherLabel = weatherLabel,
            notes = "",
            profile = curProfile
        )
        isUploading = false
        res.onSuccess { sharedData = it }
            .onFailure { errorMsg = it.message ?: localizedText(lang, "Link oluşturulamadı", "Could not create link") }
    }

    LaunchedEffect(title, pieces) {
        uploadOutfitShare()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .heightIn(max = 700.dp)
                .imePadding(),
            shape = RoundedCornerShape(DressKitStyle.cardCorner()),
            color = DressKitStyle.cardSurface(),
            border = BorderStroke(1.dp, DressKitStyle.borderColor())
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = DressKitStyle.accentColor())
                        Text(localizedText(lang, "Kombini Paylaş", "Share Outfit"), style = MaterialTheme.typography.titleLarge, color = primaryInk)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = primaryInk)
                    }
                }

                // Sharer Identity + Outfit Header Preview
                Surface(
                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                    color = DressKitStyle.secondarySurface(),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UserAvatarCircle(
                            avatarBase64 = curProfile.avatarThumbBase64.ifBlank { curProfile.avatarBase64 },
                            displayName = curProfile.displayName.ifBlank { curProfile.username },
                            size = 38.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.ifBlank { localizedText(lang, "Kombinim", "My Outfit") },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (curProfile.username.isNotBlank()) {
                                    "${localizedText(lang, "Paylaşan", "Shared by")}: @${curProfile.username}"
                                } else {
                                    localizedText(lang, "Paylaşan: Kullanıcı adı yok", "Shared anonymously")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (curProfile.username.isNotBlank()) DressKitStyle.accentColor() else secondaryInk
                            )
                        }
                        HarmonyScoreBadge(score = harmonyScore)
                    }
                }

                // Piece thumbnails strip
                if (pieces.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pieces.take(8).distinctBy { it.id }, key = { it.id }) { p ->
                            val file = p.activePhotos.firstOrNull()?.let {
                                com.dresskit.wardrobe.data.ImageStorage.getPhotoFile(context, it)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                modifier = Modifier.size(56.dp)
                            ) {
                                if (file != null) {
                                    coil.compose.AsyncImage(
                                        model = file,
                                        contentDescription = p.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(4.dp)
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Checkroom, contentDescription = null, tint = DressKitColors.MutedInk, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (isUploading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = DressKitStyle.accentColor())
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(localizedText(lang, "Kombin linki hazırlanıyor...", "Creating outfit link..."), style = MaterialTheme.typography.bodySmall, color = secondaryInk)
                    }
                } else if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = DressKitColors.DangerRust)
                    OutlinedButton(onClick = { scope.launch { uploadOutfitShare() } }, modifier = Modifier.fillMaxWidth()) {
                        Text(localizedText(lang, "Tekrar Dene", "Retry"), color = primaryInk)
                    }
                } else if (sharedData != null) {
                    val sd = sharedData!!
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DressKitStyle.secondarySurface(),
                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = localizedText(lang, "Paylaşım Bağlantısı (Web & Mobil Uygulamada Açılır)", "Share Link (Opens in Web & Mobile App)"),
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryInk
                            )
                            Text(sd.shareUrl, style = MaterialTheme.typography.bodySmall, color = primaryInk)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(sd.shareUrl))
                                        Toast.makeText(context, localizedText(lang, "Link panoya kopyalandı", "Link copied"), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp), tint = primaryInk)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(localizedText(lang, "Kopyala", "Copy"), color = primaryInk, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val i = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "${sd.title} - Dolabım\n${sd.shareUrl}")
                                        }
                                        context.startActivity(Intent.createChooser(i, localizedText(lang, "Kombini Paylaş", "Share Outfit")))
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                                ) {
                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(localizedText(lang, "Paylaş", "Share"), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Inline quick username creation when user has no username yet
                    if (SocialProfileService.normalizeUsername(curProfile.username).isBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DressKitStyle.accentSoft(),
                            border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = DressKitStyle.accentColor(), modifier = Modifier.size(18.dp))
                                    Text(
                                        text = localizedText(lang, "Henüz bir kullanıcı adın yok", "No username yet"),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = primaryInk
                                    )
                                }
                                Text(
                                    text = localizedText(
                                        lang,
                                        "Arkadaşlarının bu kombini senin oluşturduğunu görebilmesi için hemen kullanıcı adı seçebilirsin.",
                                        "Create a username now so friends see this outfit comes from you."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryInk
                                )
                                if (!showInlineCreate) {
                                    OutlinedButton(onClick = { showInlineCreate = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text(localizedText(lang, "Hemen Kullanıcı Adı Oluştur", "Create Username Now"), color = DressKitStyle.accentColor(), fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = inlineUsername,
                                        onValueChange = { inlineUsername = SocialProfileService.normalizeUsername(it); userError = null },
                                        singleLine = true,
                                        label = { Text(localizedText(lang, "Kullanıcı Adı (@...)", "Username (@...)")) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = inlineDisplayName,
                                        onValueChange = { inlineDisplayName = it.take(50) },
                                        singleLine = true,
                                        label = { Text(localizedText(lang, "Görünen Ad (İsteğe Bağlı)", "Display Name")) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (userError != null) {
                                        Text(userError!!, style = MaterialTheme.typography.labelSmall, color = DressKitColors.DangerRust)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = onOpenProfileSettings, modifier = Modifier.weight(1f)) {
                                            Text(localizedText(lang, "Tam Profil", "Full Profile"), color = primaryInk, fontSize = 11.sp)
                                        }
                                        Button(
                                            onClick = {
                                                val norm = SocialProfileService.normalizeUsername(inlineUsername)
                                                if (!SocialProfileService.isValidUsername(norm)) {
                                                    userError = localizedText(lang, "En az 3 karakter: a-z, 0-9, _, .", "3-24 chars: a-z, 0-9, _, .")
                                                    return@Button
                                                }
                                                scope.launch {
                                                    isSavingUser = true
                                                    userError = null
                                                    val availRes = SocialProfileService.checkUsernameAvailable(context, norm)
                                                    if (availRes.getOrNull() != true) {
                                                        userError = availRes.exceptionOrNull()?.message
                                                            ?: localizedText(lang, "@$norm başka biri tarafından alınmış", "@$norm is taken")
                                                        isSavingUser = false
                                                        return@launch
                                                    }
                                                    val nextDraft = curProfile.copy(
                                                        username = norm,
                                                        displayName = inlineDisplayName.trim().ifBlank { norm }
                                                    )
                                                    SocialProfileService.saveAndSyncProfile(context, nextDraft, allItems, allOutfits)
                                                        .onSuccess { updated ->
                                                            curProfile = updated
                                                            onProfileUpdated(updated)
                                                            SocialProfileService.updateSharedOutfitOwner(context, sd, updated)
                                                                .onSuccess { sharedData = it }
                                                            showInlineCreate = false
                                                            Toast.makeText(context, localizedText(lang, "@$norm linke eklendi", "@$norm attached"), Toast.LENGTH_SHORT).show()
                                                        }
                                                        .onFailure { userError = it.message ?: "Kaydedilemedi" }
                                                    isSavingUser = false
                                                }
                                            },
                                            enabled = !isSavingUser,
                                            modifier = Modifier.weight(1.3f),
                                            colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                                        ) {
                                            if (isSavingUser) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DressKitStyle.buttonFg())
                                            else Text(localizedText(lang, "Oluştur ve Ekle", "Save & Attach"), fontSize = 11.sp)
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
fun SharedOutfitViewerDialog(
    shareId: String,
    myProfile: LocalProfileConfig,
    repository: WardrobeRepository,
    onOpenUserProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var shared by remember { mutableStateOf<SharedOutfitData?>(null) }
    var isLiked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableIntStateOf(0) }
    var importingAll by remember { mutableStateOf(false) }

    LaunchedEffect(shareId) {
        loading = true
        errorMsg = null
        SocialProfileService.fetchSharedOutfit(context, shareId)
            .onSuccess { shared = it }
            .onFailure { errorMsg = it.message ?: localizedText(lang, "Kombin yüklenemedi", "Could not load outfit") }
        val (liked, count) = SocialProfileService.fetchLikeInfo(context, "outfit_$shareId")
        isLiked = liked
        likeCount = count
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
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = primaryInk)
                    }
                    Text(localizedText(lang, "Paylaşılan Kombin", "Shared Outfit"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = primaryInk)
                    if (shared != null) {
                        Surface(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable {
                                    scope.launch {
                                        SocialProfileService.toggleLike(context, "outfit_$shareId")
                                            .onSuccess { (l, c) -> isLiked = l; likeCount = c }
                                    }
                                },
                            shape = RoundedCornerShape(999.dp),
                            color = if (isLiked) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface()
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(if (isLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isLiked) DressKitStyle.accentColor() else primaryInk)
                                Text("$likeCount", style = MaterialTheme.typography.labelMedium, color = primaryInk)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
                HorizontalDivider(color = DressKitStyle.borderColor())

                when {
                    loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DressKitStyle.accentColor())
                    }
                    errorMsg != null -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(errorMsg!!, color = DressKitColors.DangerRust)
                    }
                    shared == null -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(localizedText(lang, "Kombin bulunamadı veya silinmiş.", "Outfit not found or removed."), color = secondaryInk)
                    }
                    else -> {
                        val s = shared!!
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Sharer header
                            item {
                                val ownerTag = if (s.ownerUsername.isNotBlank()) "@${s.ownerUsername}"
                                else s.ownerDisplayName.ifBlank { localizedText(lang, "Dolabım Kullanıcısı", "DressKit User") }
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth().clickable { onOpenUserProfile(s.ownerUid) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        UserAvatarCircle(s.ownerAvatar, ownerTag, size = 40.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(s.ownerDisplayName.ifBlank { ownerTag }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = primaryInk)
                                            if (s.ownerUsername.isNotBlank()) Text("@${s.ownerUsername}", style = MaterialTheme.typography.labelSmall, color = DressKitStyle.accentColor())
                                        }
                                        Text(localizedText(lang, "Profile Git >", "Profile >"), style = MaterialTheme.typography.labelMedium, color = DressKitStyle.accentColor())
                                    }
                                }
                            }

                            // Outfit Overview Card
                            item {
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = s.title,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = primaryInk,
                                                modifier = Modifier.weight(1f)
                                            )
                                            HarmonyScoreBadge(score = s.harmonyScore)
                                        }
                                        if (s.harmonyNote.isNotBlank()) {
                                            Text(s.harmonyNote, style = MaterialTheme.typography.bodySmall, color = secondaryInk)
                                        }
                                        if (s.weatherLabel.isNotBlank()) {
                                            Text("${localizedText(lang, "Hava", "Weather")}: ${s.weatherLabel}", style = MaterialTheme.typography.labelMedium, color = DressKitStyle.accentColor())
                                        }
                                        Text("${s.pieces.size} ${localizedText(lang, "parça", "pieces")}", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
                                    }
                                }
                            }

                            // Pieces
                            items(s.pieces.size) { idx ->
                                val piece = s.pieces[idx]
                                val catLbl = runCatching { ClothingCategory.valueOf(piece.category).display(lang) }.getOrDefault(piece.category)
                                val subLbl = runCatching { ClothingSubCategory.valueOf(piece.subCategory).display(lang) }.getOrDefault(piece.subCategory)
                                val bmp = remember(piece.photoBase64) {
                                    piece.photoBase64.takeIf { it.isNotBlank() }?.let { SocialProfileService.decodeDataUrlToBitmap(it)?.asImageBitmap() }
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (bmp != null) {
                                                Image(bitmap = bmp, contentDescription = piece.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                                            } else {
                                                Icon(Icons.Outlined.Checkroom, contentDescription = null, tint = DressKitColors.MutedInk)
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(piece.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = primaryInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("$catLbl · $subLbl", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
                                            if (piece.priceText.isNotBlank()) {
                                                Text(piece.priceText, style = MaterialTheme.typography.labelSmall, color = DressKitStyle.accentColor(), fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    SocialProfileService.importRemoteGarmentLocally(
                                                        context, repository, piece.name, piece.category, piece.subCategory, piece.styleTag,
                                                        piece.seasons, piece.colors, piece.notes, piece.priceText, piece.productUrl,
                                                        listOfNotNull(piece.photoBase64.takeIf { it.isNotBlank() }), asWishlist = true
                                                    )
                                                    Toast.makeText(context, localizedText(lang, "İstek listene eklendi", "Added to wishlist"), Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(localizedText(lang, "İstek", "Wish"), fontSize = 11.sp, color = primaryInk)
                                        }
                                    }
                                }
                            }
                        }

                        Surface(color = DressKitStyle.cardSurface(), shadowElevation = 8.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(0.8f).height(46.dp)) {
                                    Text(localizedText(lang, "Kapat", "Close"), color = primaryInk)
                                }
                                Button(
                                    onClick = {
                                        if (importingAll) return@Button
                                        scope.launch {
                                            importingAll = true
                                            SocialProfileService.importSharedOutfitLocally(context, repository, s)
                                                .onSuccess {
                                                    Toast.makeText(context, localizedText(lang, "Kombin ve parçaları dolabına kaydedildi", "Outfit saved to your closet"), Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                }
                                                .onFailure { err ->
                                                    Toast.makeText(context, err.message ?: "Hata", Toast.LENGTH_SHORT).show()
                                                }
                                            importingAll = false
                                        }
                                    },
                                    enabled = !importingAll,
                                    modifier = Modifier.weight(1.6f).height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DressKitStyle.buttonBg(), contentColor = DressKitStyle.buttonFg())
                                ) {
                                    if (importingAll) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DressKitStyle.buttonFg())
                                    } else {
                                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(localizedText(lang, "Kombini Dolabıma Kaydet", "Save Outfit to Closet"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
