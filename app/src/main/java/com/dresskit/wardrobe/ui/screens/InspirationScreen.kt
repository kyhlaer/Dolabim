package com.dresskit.wardrobe.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Female
import androidx.compose.material.icons.outlined.Male
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dresskit.wardrobe.data.AppFeatureKey
import com.dresskit.wardrobe.data.ColorSeason
import com.dresskit.wardrobe.data.FacialContrast
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.InspirationCategory
import com.dresskit.wardrobe.data.LookbookCatalog
import com.dresskit.wardrobe.data.LookbookGender
import com.dresskit.wardrobe.data.LookbookItem
import com.dresskit.wardrobe.data.PersonalColorAnalyzer
import com.dresskit.wardrobe.data.StoreSearchLinks
import com.dresskit.wardrobe.data.PersonalColorProfile
import com.dresskit.wardrobe.data.RemoteConfigState
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.GenderPreference
import com.dresskit.wardrobe.model.Season
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.components.ColorDot
import com.dresskit.wardrobe.ui.components.FilterChipPill
import com.dresskit.wardrobe.ui.components.HarmonyScoreBadge
import com.dresskit.wardrobe.ui.components.RemoteAdSlotBanner
import com.dresskit.wardrobe.ui.components.WinterTitleWithSantaHat
import com.dresskit.wardrobe.ui.components.bouncyOverscroll
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InspirationScreen(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit = {},
    profileAvatarBase64: String = "",
    pendingRequestsCount: Int = 0,
    unreadActivityCount: Int = 0,
    onOpenActivity: () -> Unit = {},
    preferredGender: GenderPreference = GenderPreference.UNISEX,
    persistedGender: LookbookGender? = null,
    onSelectGender: ((LookbookGender) -> Unit)? = null,
    items: List<ClothingItem> = emptyList(),
    facePhotoVersion: Int = 0,
    onPickFaceFromGallery: () -> Unit = {},
    onCaptureFaceFromCamera: () -> Unit = {},
    likedLookIds: Set<String> = emptySet(),
    dislikedLookIds: Set<String> = emptySet(),
    visitNonce: Int = 1,
    onRecordLookSwipe: (String, Boolean?) -> Unit = { _, _ -> },
    remoteConfig: RemoteConfigState = RemoteConfigState(),
    onRequireFeature: (AppFeatureKey, () -> Unit) -> Unit = { _, run -> run() },
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val closetItems = remember(items) { items.filter { !it.isWishlist } }

    var localGender by rememberSaveable {
        mutableStateOf(persistedGender ?: if (preferredGender == GenderPreference.FEMALE) LookbookGender.WOMEN else LookbookGender.MEN)
    }
    val selectedGender = persistedGender ?: localGender
    val updateSelectedGender: (LookbookGender) -> Unit = { next ->
        localGender = next
        onSelectGender?.invoke(next)
    }
    var selectedCategory by rememberSaveable { mutableStateOf(InspirationCategory.ALL) }
    var selectedSeason by rememberSaveable { mutableStateOf<Season?>(null) }
    val catRowState = rememberLazyListState()
    val seasonRowState = rememberLazyListState()
    var zoomedLook by remember { mutableStateOf<LookbookItem?>(null) }
    var selectedColorForShop by remember { mutableStateOf<GarmentColor?>(null) }
    var showSwipeDiscovery by rememberSaveable { mutableStateOf(false) }

    var isAnalyzingFace by remember(facePhotoVersion, lang) { mutableStateOf(true) }
    val personalProfile by produceState<PersonalColorProfile?>(
        initialValue = null,
        key1 = facePhotoVersion,
        key2 = lang
    ) {
        isAnalyzingFace = true
        value = withContext(Dispatchers.IO) {
            PersonalColorAnalyzer.analyzeUserPhoto(context, lang)
        }
        isAnalyzingFace = false
    }
    var overrideSeason by remember(facePhotoVersion) { mutableStateOf<ColorSeason?>(null) }
    var showAllSeasons by remember { mutableStateOf(false) }
    val activePersonalProfile = remember(personalProfile, overrideSeason, lang) {
        val base = personalProfile ?: return@remember null
        val ov = overrideSeason ?: return@remember base
        PersonalColorAnalyzer.profileForSeason(ov, base.averageSkinHex, base.faceDetected, base.contrast, lang)
    }

    fun openStoreSearchUrl(url: String) {
        com.dresskit.wardrobe.data.SecurityGuards.openExternalUrlSafely(context, url)
    }

    selectedColorForShop?.let { color ->
        val genderWord = if (selectedGender == LookbookGender.WOMEN) {
            if (lang == AppLanguage.TR) "kadın" else "women"
        } else {
            if (lang == AppLanguage.TR) "erkek" else "men"
        }
        val colorQuery = color.display(lang)
        AlertDialog(
            onDismissRequest = { selectedColorForShop = null },
            containerColor = DressKitStyle.cardSurface(),
            titleContentColor = primaryInk,
            textContentColor = secondaryInk,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorDot(color = color.composeColor, size = 16.dp)
                    Text("${color.display(lang)} ${localizedText(lang, "Kombin Önerileri", "Outfit Picks", "Teile", "Pièces", "Prendas", "穿搭推荐")}")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    activePersonalProfile?.summaryText?.let { summary ->
                        Text(text = summary, style = MaterialTheme.typography.bodySmall, color = primaryInk)
                    }
                    Text(
                        text = localizedText(
                            lang,
                            "Mağazalarda Ara",
                            "Search in Stores",
                            "In Shops suchen",
                            "Rechercher en boutique",
                            "Buscar en tiendas",
                            "在商店中搜索"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryInk
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StoreSearchLinks.buildStoreSearchLinks(
                            genderPrefix = genderWord,
                            rawQuery = colorQuery,
                            googleLabel = "Google Alışveriş"
                        ).forEach { (store, url) ->
                            Surface(
                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                color = DressKitStyle.buttonBg(),
                                modifier = Modifier.clickable { openStoreSearchUrl(url) }
                            ) {
                                Text(
                                    text = store,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DressKitStyle.buttonFg(),
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedColorForShop = null },
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DressKitStyle.buttonBg(),
                        contentColor = DressKitStyle.buttonFg()
                    )
                ) {
                    Text(strings.close)
                }
            }
        )
    }

    val shuffledGenderLooks = remember(selectedGender, visitNonce) {
        val baseLooks = LookbookCatalog.looksForGender(selectedGender)
        val seed = (System.nanoTime() xor (visitNonce.toLong() * 0x5DEECE66DL))
        baseLooks.shuffled(kotlin.random.Random(seed))
    }
    val filtered = remember(shuffledGenderLooks, selectedCategory, selectedSeason) {
        LookbookCatalog.filterLooks(shuffledGenderLooks, selectedCategory, selectedSeason)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DressKitStyle.canvasBg())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    WinterTitleWithSantaHat(
                        title = localizedText(
                            lang,
                            "Kombin İlhamı",
                            "Outfit Inspiration",
                            "Outfit-Inspiration",
                            "Inspiration Tenues",
                            "Inspiración Outfits",
                            "穿搭灵感"
                        ),
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryInk
                    )
                    Text(
                        text = "${strings.inspirationSubtitle} · ${filtered.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = strings.settingsTitle,
                            tint = primaryInk
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                color = DressKitStyle.secondarySurface(),
                border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LookbookGender.entries.forEach { gender ->
                        val isSelected = selectedGender == gender
                        val count = LookbookCatalog.looksForGender(gender).size
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { updateSelectedGender(gender) },
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            color = if (isSelected) DressKitStyle.buttonBg() else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (gender) {
                                        LookbookGender.WOMEN -> Icons.Outlined.Female
                                        LookbookGender.MEN -> Icons.Outlined.Male
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) DressKitStyle.buttonFg() else primaryInk,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${gender.label(lang)} ($count)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) DressKitStyle.buttonFg() else primaryInk,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onRequireFeature(AppFeatureKey.STYLE_DISCOVERY) {
                            showSwipeDiscovery = true
                        }
                    },
                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                color = DressKitStyle.accentSoft(),
                border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = DressKitStyle.accentColor(),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = localizedText(
                                lang,
                                "Stil Keşfi · Kaydır & Beğen",
                                "Style Discovery · Swipe Looks",
                                "Style Discovery · Wischen",
                                "Découverte Style · Swiper",
                                "Descubre Estilo · Desliza",
                                "风格发现 · 滑动挑选"
                            ),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = DressKitStyle.accentColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "❤️ ${likedLookIds.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = primaryInk,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "✕ ${dislikedLookIds.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryInk,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            // Unified two-row Filter Capsule aligned flush with Discovery bar & Face card
            val capsuleBg = DressKitStyle.secondarySurface()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = capsuleBg,
                border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            state = catRowState,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            contentPadding = PaddingValues(start = 10.dp, end = 22.dp)
                        ) {
                            items(InspirationCategory.entries, key = { it.name }) { cat ->
                                FilterChipPill(
                                    label = cat.label(lang),
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat }
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(22.dp)
                                .height(36.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, capsuleBg)
                                    )
                                )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .height(1.dp)
                            .background(DressKitStyle.borderColor().copy(alpha = 0.55f))
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            state = seasonRowState,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(start = 10.dp, end = 22.dp)
                        ) {
                            item(key = "SEASON_ANY") {
                                FilterChipPill(
                                    label = localizedText(lang, "Tümü", "All", "Alle", "Tout", "Todo", "全部"),
                                    selected = selectedSeason == null,
                                    onClick = { selectedSeason = null }
                                )
                            }
                            items(Season.entries, key = { it.name }) { season ->
                                FilterChipPill(
                                    label = season.display(lang),
                                    selected = selectedSeason == season,
                                    onClick = { selectedSeason = season }
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(22.dp)
                                .height(34.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, capsuleBg)
                                    )
                                )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .bouncyOverscroll(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (remoteConfig.isBannerVisible("lookbook_top")) {
                    item(key = "lookbook_top_banner") {
                        RemoteAdSlotBanner(
                            slot = "lookbook_top",
                            config = remoteConfig,
                            lang = lang
                        )
                    }
                }
                item(key = "face_skin_tone_card") {
                    val prefs = remember { context.getSharedPreferences("inspiration", android.content.Context.MODE_PRIVATE) }
                    var expanded by remember { mutableStateOf(prefs.getBoolean("faceExpanded", true)) }
                    val profile = activePersonalProfile
                    val faceFile = remember(facePhotoVersion) {
                        ImageStorage.getUserFacePhotoFile(context)
                    }
                    Surface(
                        shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                        color = DressKitStyle.cardSurface(),
                        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.animateContentSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    expanded = !expanded
                                    prefs.edit().putBoolean("faceExpanded", expanded).apply()
                                },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FaceRetouchingNatural,
                                        contentDescription = null,
                                        tint = DressKitStyle.accentColor(),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Yüz & Ten Rengi Uyum Analizi",
                                            "Face & Skin Tone Color Match",
                                            "Gesichts- & Hautton-Farbanalyse",
                                            "Analyse teint & couleurs",
                                            "Análisis de rostro y tono",
                                            "面容与肤色色彩匹配"
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryInk,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (faceFile.exists()) {
                                        val lastMod = faceFile.lastModified()
                                        val request = remember(facePhotoVersion, lastMod) {
                                            ImageRequest.Builder(context)
                                                .data(faceFile)
                                                .memoryCacheKey("face_thumb_${facePhotoVersion}_$lastMod")
                                                .diskCacheKey("face_thumb_${facePhotoVersion}_$lastMod")
                                                .build()
                                        }
                                        AsyncImage(
                                            model = request,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, DressKitStyle.borderColor(), CircleShape)
                                        )
                                    }
                                    if (profile != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color(profile.averageSkinHex))
                                                .border(1.dp, DressKitStyle.borderColor(), CircleShape)
                                        )
                                    }
                                    Box(Modifier.size(24.dp).border(1.dp, DressKitStyle.borderColor(), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                            localizedText(lang, if (expanded) "Küçült" else "Büyüt", if (expanded) "Collapse" else "Expand"), modifier = Modifier.size(16.dp), tint = secondaryInk)
                                    }
                                }
                            }
                            if (!expanded && profile != null) Text("${profile.seasonTitle} · ${profile.undertone.label(lang)}",
                                style = MaterialTheme.typography.labelLarge, color = DressKitStyle.accentColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (expanded) {
                                if (profile != null) {
                                    val matchingWardrobeCount = remember(closetItems, profile) {
                                        closetItems.count { garment ->
                                            garment.colors.any { it in profile.flatteringColors }
                                        }
                                    }
                                    Text(
                                        text = "${profile.seasonTitle} · ${profile.undertone.label(lang)} · ${profile.contrast.label(lang)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = DressKitStyle.accentColor()
                                    )
                                    Text(
                                        text = profile.summaryText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = primaryInk
                                    )
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Sana En Çok Yakışacak Renkler (dokun → ara):",
                                            "Most Flattering Colors (tap → search):",
                                            "Beste Farben für deinen Teint (tippen → suchen):",
                                            "Couleurs qui vous subliment (toucher → chercher) :",
                                            "Colores que más te favorecen (toca → buscar):",
                                            "最衬肤色的推荐色彩（点击搜索）："
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = secondaryInk
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        profile.flatteringColors.forEach { color ->
                                            Surface(
                                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                                color = DressKitStyle.secondarySurface(),
                                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                modifier = Modifier.clickable { selectedColorForShop = color }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    ColorDot(color = color.composeColor, size = 12.dp)
                                                    Text(
                                                        text = color.display(lang),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = primaryInk
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (profile.avoidColors.isNotEmpty()) {
                                        Text(
                                            text = "${localizedText(lang, "Kaçınılacak Tonlar", "Avoid Tones")}: ${profile.avoidColors.joinToString(", ") { it.display(lang) }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryInk
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                            color = if (showAllSeasons) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                                            border = BorderStroke(1.dp, if (showAllSeasons) DressKitStyle.accentColor() else DressKitStyle.borderColor()),
                                            modifier = Modifier.clickable { showAllSeasons = !showAllSeasons }
                                        ) {
                                            Text(
                                                text = if (showAllSeasons) {
                                                    localizedText(lang, "12 Sezonu Gizle ▲", "Hide 12 Seasons ▲")
                                                } else {
                                                    localizedText(lang, "Daha Fazla Öneri · Tüm 12 Sezon ▼", "More Options · All 12 Seasons ▼")
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (showAllSeasons) DressKitStyle.accentColor() else primaryInk,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                        if (overrideSeason != null) {
                                            Text(
                                                text = localizedText(lang, "Otomatiğe Dön", "Reset Auto"),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = DressKitStyle.accentColor(),
                                                modifier = Modifier.clickable { overrideSeason = null }
                                            )
                                        }
                                    }
                                    if (showAllSeasons) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ColorSeason.entries.forEach { cs ->
                                                val isSelected = profile.season == cs
                                                FilterChipPill(
                                                    label = cs.shortLabel(lang),
                                                    selected = isSelected,
                                                    onClick = { overrideSeason = if (personalProfile?.season == cs) null else cs }
                                                )
                                            }
                                        }
                                    }
                                    if (closetItems.isNotEmpty()) {
                                        Text(
                                            text = localizedText(
                                                lang,
                                                "Dolabındaki $matchingWardrobeCount parça ten tonunla yüksek uyumlu.",
                                                "$matchingWardrobeCount pieces in your closet match your skin tone.",
                                                "$matchingWardrobeCount Teile in deinem Schrank passen zu deinem Teint.",
                                                "$matchingWardrobeCount pièces de votre dressing subliment votre teint.",
                                                "$matchingWardrobeCount prendas de tu armario combinan con tu tono.",
                                                "衣橱中有 $matchingWardrobeCount 件单品非常衬你的肤色。"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryInk
                                        )
                                    }
                                } else if (faceFile.exists() && !isAnalyzingFace) {
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Fotoğraf analiz edilemedi; lütfen daha aydınlık ve net bir fotoğraf dene.",
                                            "Could not analyze photo; please try a brighter, clearer photo.",
                                            "Foto konnte nicht analysiert werden; bitte versuche ein helleres, klareres Foto.",
                                            "Impossible d'analyser la photo ; essayez une photo plus lumineuse et nette.",
                                            "No se pudo analizar la foto; prueba una más clara y luminosa.",
                                            "无法分析该照片，请尝试更清晰明亮的照片。"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DressKitStyle.accentColor()
                                    )
                                } else {
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Kendi fotoğrafını veya boy fotoğrafını yüklediğinde yüzünü ve ten alt tonunu analiz edip sana en çok yakışacak renk paletini otomatik çıkarır.",
                                            "Upload a portrait or full-body photo to analyze your face & skin tone and discover your most flattering color palette.",
                                            "Lade ein Foto hoch, um deinen Hautton zu analysieren und passende Farben zu finden.",
                                            "Ajoutez votre photo pour analyser votre teint et révéler vos couleurs idéales.",
                                            "Sube tu foto para analizar tu rostro y tono de piel y descubrir tus mejores colores.",
                                            "上传你的照片即可分析面容与肤色调，自动推荐最适合你的显白色系。"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryInk
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onRequireFeature(AppFeatureKey.FACE_TONE) {
                                                onPickFaceFromGallery()
                                            }
                                        },
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = primaryInk, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.size(5.dp))
                                        Text(
                                            text = if (profile != null) {
                                                localizedText(lang, "Değiştir", "Change", "Ändern", "Changer", "Cambiar", "更换照片")
                                            } else {
                                                localizedText(lang, "Galeri", "Gallery", "Galerie", "Galerie", "Galería", "从相册选")
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = primaryInk,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onRequireFeature(AppFeatureKey.FACE_TONE) {
                                                onCaptureFaceFromCamera()
                                            }
                                        },
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = primaryInk, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.size(5.dp))
                                        Text(
                                            text = localizedText(lang, "Kamera", "Camera", "Kamera", "Caméra", "Cámara", "拍照"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = primaryInk,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(filtered, key = { it.id }) { look ->
                    val skinMatch = remember(look.id, activePersonalProfile) {
                        activePersonalProfile?.let { prof ->
                            LookbookCatalog.computeLookSkinToneMatch(
                                paletteColors = look.paletteColors,
                                flatteringColors = prof.flatteringColors.toSet(),
                                avoidColors = prof.avoidColors.toSet()
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { zoomedLook = look },
                        shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                        color = DressKitStyle.cardSurface(),
                        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                        shadowElevation = if (DressKitStyle.isStreet()) 0.dp else 1.dp
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(256.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = DressKitStyle.cardCorner(),
                                            topEnd = DressKitStyle.cardCorner()
                                        )
                                    )
                                    .background(DressKitStyle.photoBoxSurface())
                            ) {
                                AsyncImage(
                                    model = look.imageUrl,
                                    contentDescription = if (lang == AppLanguage.TR) look.trTitle else look.enTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp),
                                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                    color = DressKitStyle.cardSurface().copy(alpha = 0.92f)
                                ) {
                                    Text(
                                        text = look.category.label(lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = primaryInk,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                HarmonyScoreBadge(
                                    score = look.harmonyPercent,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                )

                                if (skinMatch != null) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(12.dp),
                                        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                        color = Color(0xFF14161A).copy(alpha = 0.78f)
                                    ) {
                                        Text(
                                            text = "${localizedText(lang, "Ten Uyumu", "Skin Match", "Hautton", "Teint", "Tono", "肤色匹配")} %$skinMatch",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (lang == AppLanguage.TR) look.trTitle else look.enTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = primaryInk,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        look.colorDots.forEach { dot ->
                                            ColorDot(color = dot, size = 12.dp)
                                        }
                                    }
                                }

                                Text(
                                    text = if (lang == AppLanguage.TR) look.trPalette else look.enPalette,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DressKitStyle.accentColor()
                                )

                                val pieces = if (lang == AppLanguage.TR) look.trPieces else look.enPieces
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    pieces.forEach { piece ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AutoAwesome,
                                                contentDescription = null,
                                                tint = secondaryInk,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = piece,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = secondaryInk
                                            )
                                        }
                                    }
                                }

                                val tipText = if (lang == AppLanguage.TR) look.trTip else look.enTip
                                if (tipText.isNotBlank()) {
                                    Text(
                                        text = tipText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryInk,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSwipeDiscovery) {
        LookbookSwipeDiscoveryDialog(
            selectedGender = selectedGender,
            visitNonce = visitNonce,
            onGenderChange = { updateSelectedGender(it) },
            likedLookIds = likedLookIds,
            dislikedLookIds = dislikedLookIds,
            onRecordLookSwipe = onRecordLookSwipe,
            onInspectLook = { look -> zoomedLook = look },
            onDismiss = { showSwipeDiscovery = false }
        )
    }

    zoomedLook?.let { look ->
        val lookTitle = if (lang == AppLanguage.TR) look.trTitle else look.enTitle
        val lookPalette = if (lang == AppLanguage.TR) look.trPalette else look.enPalette
        val lookTip = if (lang == AppLanguage.TR) look.trTip else look.enTip
        val pieces = if (lang == AppLanguage.TR) look.trPieces else look.enPieces
        val genderPrefix = if (selectedGender == LookbookGender.WOMEN) {
            if (lang == AppLanguage.TR) "kadın" else "women"
        } else {
            if (lang == AppLanguage.TR) "erkek" else "men"
        }
        val detailSkinMatch = activePersonalProfile?.let { prof ->
            LookbookCatalog.computeLookSkinToneMatch(
                paletteColors = look.paletteColors,
                flatteringColors = prof.flatteringColors.toSet(),
                avoidColors = prof.avoidColors.toSet()
            )
        }
        var selectedStorePieceIdx by remember(look.id) { mutableIntStateOf(0) }

        fun openSearchUrl(url: String) {
            com.dresskit.wardrobe.data.SecurityGuards.openExternalUrlSafely(context, url)
        }

        Dialog(
            onDismissRequest = { zoomedLook = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable { zoomedLook = null },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 28.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                    color = DressKitStyle.cardSurface(),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(DressKitStyle.photoBoxSurface())
                        ) {
                            AsyncImage(
                                model = look.imageUrl,
                                contentDescription = lookTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            HarmonyScoreBadge(
                                score = look.harmonyPercent,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f)
                            ) {
                                IconButton(onClick = { zoomedLook = null }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = strings.close,
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = lookTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = primaryInk
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = lookPalette,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DressKitStyle.accentColor(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (detailSkinMatch != null) {
                                        Surface(
                                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                            color = DressKitStyle.accentSoft()
                                        ) {
                                            Text(
                                                text = "${localizedText(lang, "Ten Uyumu", "Skin Match", "Hautton", "Teint", "Tono", "肤色匹配")} %$detailSkinMatch",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = DressKitStyle.accentColor(),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                                if (lookTip.isNotBlank()) {
                                    Text(
                                        text = lookTip,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryInk,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = localizedText(
                                    lang,
                                    "Kombin Parçaları & Benzer Ürün Ara",
                                    "Pieces & Find Similar Products",
                                    "Teile & Ähnliche Produkte",
                                    "Pièces & Articles similaires",
                                    "Prendas y Buscar similares",
                                    "穿搭单品与相似款搜索"
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = primaryInk
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                pieces.forEachIndexed { idx, piece ->
                                    val isSelectedForStores = idx == selectedStorePieceIdx
                                    Surface(
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        color = if (isSelectedForStores) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelectedForStores) DressKitStyle.accentColor() else DressKitStyle.borderColor()
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedStorePieceIdx = idx }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = piece,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = primaryInk,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                                color = DressKitStyle.cardSurface(),
                                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                modifier = Modifier.clickable {
                                                    openSearchUrl(StoreSearchLinks.googleShoppingUrl(genderPrefix, piece))
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Search,
                                                        contentDescription = null,
                                                        tint = DressKitStyle.accentColor(),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "Google",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = primaryInk
                                                    )
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                                color = DressKitStyle.accentSoft(),
                                                modifier = Modifier.clickable {
                                                    openSearchUrl(StoreSearchLinks.trendyolUrl(genderPrefix, piece))
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.ShoppingBag,
                                                        contentDescription = null,
                                                        tint = DressKitStyle.accentColor(),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "Trendyol",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = DressKitStyle.accentColor()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val activePieceQuery = pieces.getOrElse(selectedStorePieceIdx) {
                                pieces.firstOrNull() ?: lookTitle
                            }
                            Text(
                                text = localizedText(
                                    lang,
                                    "Mağazalarda Benzer Ürün Ara ($activePieceQuery)",
                                    "Search Stores ($activePieceQuery)",
                                    "In Shops suchen ($activePieceQuery)",
                                    "Chercher en boutique ($activePieceQuery)",
                                    "Buscar en tiendas ($activePieceQuery)",
                                    "在商店搜索相似单品 ($activePieceQuery)"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryInk
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StoreSearchLinks.buildStoreSearchLinks(
                                    genderPrefix = genderPrefix,
                                    rawQuery = activePieceQuery,
                                    googleLabel = "Google Alışveriş"
                                ).forEach { (storeLabel, storeUrl) ->
                                    Surface(
                                        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                        color = DressKitStyle.buttonBg(),
                                        modifier = Modifier.clickable { openSearchUrl(storeUrl) }
                                    ) {
                                        Text(
                                            text = storeLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = DressKitStyle.buttonFg(),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
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

@Composable
private fun LookbookSwipeDiscoveryDialog(
    selectedGender: LookbookGender,
    visitNonce: Int = 1,
    onGenderChange: (LookbookGender) -> Unit,
    likedLookIds: Set<String>,
    dislikedLookIds: Set<String>,
    onRecordLookSwipe: (String, Boolean?) -> Unit,
    onInspectLook: (LookbookItem) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var activeTab by rememberSaveable { mutableIntStateOf(0) } // 0=Explore, 1=Liked, 2=Disliked
    var deckVersion by remember { mutableIntStateOf(0) }
    val allLooks = remember { LookbookCatalog.ALL_LOOKS }
    val basePool = remember(selectedGender) {
        LookbookCatalog.looksForGender(selectedGender)
    }
    val sessionDeck = remember(selectedGender, deckVersion, visitNonce) {
        val unseen = basePool.filter { it.id !in likedLookIds && it.id !in dislikedLookIds }
        val seed = System.nanoTime() xor ((visitNonce.toLong() + deckVersion.toLong() + 1L) * 0x5DEECE66DL)
        unseen.shuffled(kotlin.random.Random(seed))
    }
    var currentIndex by remember(selectedGender, deckVersion, visitNonce) { mutableIntStateOf(0) }
    val undoStack = remember(selectedGender, deckVersion, visitNonce) { mutableStateListOf<Pair<String, Boolean>>() }

    val likedLooks = remember(likedLookIds) {
        allLooks.filter { it.id in likedLookIds }
    }
    val dislikedLooks = remember(dislikedLookIds) {
        allLooks.filter { it.id in dislikedLookIds }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DressKitStyle.canvasBg())
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = localizedText(
                                lang,
                                "✨ Stil Keşfi",
                                "✨ Style Discovery",
                                "✨ Style Discovery",
                                "✨ Découverte Style",
                                "✨ Descubre Estilo",
                                "✨ 风格发现"
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryInk
                        )
                        Text(
                            text = localizedText(
                                lang,
                                "Sağa kaydır beğen ❤️ · Sola kaydır geç ✕",
                                "Swipe right to like ❤️ · Left to pass ✕",
                                "Rechts wischen: Like ❤️ · Links: Nein ✕",
                                "Droite : J'aime ❤️ · Gauche : Passer ✕",
                                "Derecha: Me gusta ❤️ · Izquierda: Pasar ✕",
                                "右滑喜欢 ❤️ · 左滑跳过 ✕"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = strings.close, tint = primaryInk)
                    }
                }

                // Gender quick switcher inside Discovery
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LookbookGender.entries.forEach { gender ->
                        FilterChipPill(
                            label = gender.label(lang),
                            selected = selectedGender == gender,
                            onClick = { onGenderChange(gender) }
                        )
                    }
                }

                // 3 Tabs: Explore / Liked / Passed
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChipPill(
                            label = localizedText(lang, "Keşfet (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})", "Discover (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})", "Entdecken (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})", "Découvrir (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})", "Explorar (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})", "发现 (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})"),
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 }
                        )
                    }
                    item {
                        FilterChipPill(
                            label = "❤️ ${localizedText(lang, "Beğendiklerim", "Liked", "Favoriten", "Aimés", "Me gusta", "喜欢")} (${likedLooks.size})",
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 }
                        )
                    }
                    item {
                        FilterChipPill(
                            label = "✕ ${localizedText(lang, "Geçtiklerim", "Passed", "Übersprungen", "Passés", "Pasados", "跳过")} (${dislikedLooks.size})",
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 }
                        )
                    }
                }

                when (activeTab) {
                    0 -> {
                        val activeLook = sessionDeck.getOrNull(currentIndex)
                        if (activeLook == null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                color = DressKitStyle.cardSurface(),
                                border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = DressKitStyle.accentColor(),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Bu kategorideki tüm kombinleri keşfettin!",
                                            "You've swiped through every look here!",
                                            "Du hast alle Looks in dieser Kategorie gesehen!",
                                            "Vous avez découvert tous ces looks !",
                                            "¡Ya viste todos los looks de esta sección!",
                                            "已浏览完当前分类的所有穿搭！"
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryInk
                                    )
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Beğendiklerini görebilir ya da listeyi sıfırlayıp yeniden karıştırabilirsin.",
                                            "View your liked outfits or reset & shuffle to discover again.",
                                            "Sieh dir deine Favoriten an oder mische die Karten neu.",
                                            "Consultez vos coups de cœur ou réinitialisez les cartes.",
                                            "Mira tus favoritos o reinicia las tarjetas.",
                                            "可查看已喜欢的穿搭，或重置列表重新随机探索。"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryInk
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = { activeTab = 1 },
                                            shape = RoundedCornerShape(DressKitStyle.buttonCorner())
                                        ) {
                                            Text("❤️ ${likedLooks.size}")
                                        }
                                        Button(
                                            onClick = {
                                                basePool.forEach { onRecordLookSwipe(it.id, null) }
                                                deckVersion++
                                            },
                                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = DressKitStyle.buttonBg(),
                                                contentColor = DressKitStyle.buttonFg()
                                            )
                                        ) {
                                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(localizedText(lang, "Yeniden Karıştır", "Shuffle Again", "Neu mischen", "Remélanger", "Reintentar", "重新洗牌"))
                                        }
                                    }
                                }
                            }
                        } else {
                            val title = if (lang == AppLanguage.TR) activeLook.trTitle else activeLook.enTitle
                            val palette = if (lang == AppLanguage.TR) activeLook.trPalette else activeLook.enPalette
                            var dragX by remember(activeLook.id) { mutableFloatStateOf(0f) }
                            val animatedX by animateFloatAsState(targetValue = dragX, label = "cardSwipeX")
                            val swipeThresholdPx = androidx.compose.ui.platform.LocalDensity.current.run { 95.dp.toPx() }

                            fun swipeCurrent(liked: Boolean) {
                                undoStack.add(activeLook.id to liked)
                                onRecordLookSwipe(activeLook.id, liked)
                                dragX = 0f
                                currentIndex++
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset { IntOffset(animatedX.roundToInt(), 0) }
                                        .graphicsLayer {
                                            rotationZ = (animatedX / 30f).coerceIn(-12f, 12f)
                                        }
                                        .pointerInput(activeLook.id) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    when {
                                                        dragX > swipeThresholdPx -> swipeCurrent(true)
                                                        dragX < -swipeThresholdPx -> swipeCurrent(false)
                                                        else -> dragX = 0f
                                                    }
                                                },
                                                onDragCancel = { dragX = 0f }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                dragX += dragAmount
                                            }
                                        }
                                        .clickable { onInspectLook(activeLook) },
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                                    shadowElevation = 4.dp
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = activeLook.imageUrl,
                                            contentDescription = title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (animatedX > 36f) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(18.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF16A34A).copy(alpha = 0.92f)
                                            ) {
                                                Text(
                                                    text = localizedText(lang, "BEĞENDİM ❤️", "LIKED ❤️", "GEFÄLLT ❤️", "J'AIME ❤️", "ME GUSTA ❤️", "喜欢 ❤️"),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        } else if (animatedX < -36f) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(18.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFFDC2626).copy(alpha = 0.92f)
                                            ) {
                                                Text(
                                                    text = localizedText(lang, "GEÇ ✕", "PASS ✕", "NEIN ✕", "PASSER ✕", "PASAR ✕", "跳过 ✕"),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }

                                        // Bottom overlay info strip
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            color = DressKitStyle.cardSurface().copy(alpha = 0.90f),
                                            border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = activeLook.category.label(lang),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = DressKitStyle.accentColor()
                                                    )
                                                    HarmonyScoreBadge(score = activeLook.harmonyPercent)
                                                }
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = primaryInk,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = palette,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = secondaryInk,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Explicit Action Buttons: Pass / Undo / Like
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { swipeCurrent(false) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.55f)),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "✕ ${localizedText(lang, "Geç", "Pass", "Nein", "Passer", "Pasar", "跳过")}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFDC2626),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (currentIndex > 0 && undoStack.isNotEmpty()) {
                                            val (prevId, _) = undoStack.removeAt(undoStack.lastIndex)
                                            onRecordLookSwipe(prevId, null)
                                            currentIndex--
                                        }
                                    },
                                    enabled = currentIndex > 0 && undoStack.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = localizedText(lang, "Geri Al", "Undo", "Zurück", "Annuler", "Deshacer", "撤销"),
                                        tint = if (currentIndex > 0 && undoStack.isNotEmpty()) primaryInk else secondaryInk.copy(alpha = 0.4f)
                                    )
                                }
                                Button(
                                    onClick = { swipeCurrent(true) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF16A34A),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "❤️ ${localizedText(lang, "Beğen", "Like", "Like", "J'aime", "Gustar", "喜欢")}",
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        SwipeHistoryList(
                            looks = likedLooks,
                            isLikedList = true,
                            lang = lang,
                            primaryInk = primaryInk,
                            secondaryInk = secondaryInk,
                            onInspect = onInspectLook,
                            onToggle = { id, toLiked -> onRecordLookSwipe(id, toLiked) },
                            emptyMessage = localizedText(
                                lang,
                                "Henüz beğendiğin kombin yok. Keşfet'ten sağa kaydır!",
                                "No liked looks yet. Swipe right in Discover!",
                                "Noch keine Favoriten. Wische nach rechts!",
                                "Aucun look aimé. Glissez à droite !",
                                "Aún sin favoritos. ¡Desliza a la derecha!",
                                "暂无喜欢的穿搭，去“发现”右滑收藏吧！"
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    else -> {
                        SwipeHistoryList(
                            looks = dislikedLooks,
                            isLikedList = false,
                            lang = lang,
                            primaryInk = primaryInk,
                            secondaryInk = secondaryInk,
                            onInspect = onInspectLook,
                            onToggle = { id, toLiked -> onRecordLookSwipe(id, toLiked) },
                            emptyMessage = localizedText(
                                lang,
                                "Geçtiğin kombin bulunmuyor.",
                                "No passed looks.",
                                "Keine übersprungenen Looks.",
                                "Aucun look passé.",
                                "Sin looks pasados.",
                                "暂无跳过的穿搭。"
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeHistoryList(
    looks: List<LookbookItem>,
    isLikedList: Boolean,
    lang: AppLanguage,
    primaryInk: Color,
    secondaryInk: Color,
    onInspect: (LookbookItem) -> Unit,
    onToggle: (String, Boolean?) -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    if (looks.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryInk
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(looks, key = { it.id }) { look ->
            val title = if (lang == AppLanguage.TR) look.trTitle else look.enTitle
            val palette = if (lang == AppLanguage.TR) look.trPalette else look.enPalette
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInspect(look) },
                shape = RoundedCornerShape(14.dp),
                color = DressKitStyle.cardSurface(),
                border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = look.imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp, 92.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = palette,
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { onToggle(look.id, !isLikedList) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isLikedList) {
                                        "✕ ${localizedText(lang, "Çıkar", "Move to Pass", "Entfernen", "Retirer", "Quitar", "移除")}"
                                    } else {
                                        "❤️ ${localizedText(lang, "Beğen", "Move to Liked", "Gefällt", "Aimer", "Gustar", "移至喜欢")}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DressKitStyle.accentColor()
                                )
                            }
                            TextButton(
                                onClick = { onToggle(look.id, null) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = localizedText(lang, "Sıfırla", "Reset", "Reset", "Reset", "Reset", "重置"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryInk
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
