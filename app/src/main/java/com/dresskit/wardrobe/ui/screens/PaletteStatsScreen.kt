package com.dresskit.wardrobe.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dresskit.wardrobe.data.PersonalColorAnalyzer
import com.dresskit.wardrobe.data.PersonalColorProfile
import com.dresskit.wardrobe.data.PriceTrackerService
import com.dresskit.wardrobe.data.RemoteConfigState
import com.dresskit.wardrobe.data.StoreSearchLinks
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.GenderPreference
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.components.ColorDot
import com.dresskit.wardrobe.ui.components.RemoteAdSlotBanner
import com.dresskit.wardrobe.ui.components.bouncyOverscroll
import com.dresskit.wardrobe.ui.theme.DressKitColors
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CapsuleGapItem(
    val title: String,
    val unlockedCombosText: String,
    val description: String,
    val recommendedColors: List<GarmentColor>,
    val searchQuery: String
)

private data class ColorGapSuggestion(
    val color: GarmentColor,
    val reason: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteStatsScreen(
    items: List<ClothingItem>,
    outfits: List<Outfit>,
    onOpenSettings: () -> Unit,
    facePhotoVersion: Int = 0,
    genderPref: GenderPreference = GenderPreference.UNISEX,
    onPickBodyFromGallery: () -> Unit = {},
    onCaptureBodyFromCamera: () -> Unit = {},
    onOpenItem: (String) -> Unit = {},
    remoteConfig: RemoteConfigState = RemoteConfigState(),
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val isEn = lang != AppLanguage.TR
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val cardCorner = DressKitStyle.cardCorner()

    val closetItems = remember(items) { items.filter { !it.isWishlist } }
    val wishlistItems = remember(items) { items.filter { it.isWishlist } }
    val totalPhotos = remember(closetItems) { closetItems.sumOf { it.photoFileNames.size } }
    val favoriteCount = remember(items) { items.count { it.isFavorite } }

    val personalProfile by produceState<PersonalColorProfile?>(
        initialValue = null,
        key1 = facePhotoVersion,
        key2 = lang
    ) {
        value = withContext(Dispatchers.IO) {
            PersonalColorAnalyzer.analyzeUserPhoto(context, lang)
        }
    }

    val colorCounts = remember(closetItems) {
        GarmentColor.entries
            .map { color -> color to closetItems.count { it.colors.contains(color) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }

    val categoryCounts = remember(closetItems) {
        ClothingCategory.entries
            .map { cat -> cat to closetItems.count { it.category == cat } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }

    val subCategoryCounts = remember(closetItems) {
        ClothingSubCategory.entries
            .map { sub -> sub to closetItems.count { it.subCategory == sub } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }

    val mostWorn = remember(closetItems) {
        closetItems.filter { it.wearCount > 0 }.sortedByDescending { it.wearCount }.take(5)
    }
    val unwornPieces = remember(closetItems) {
        closetItems.filter { it.wearCount == 0 }.sortedBy { it.createdAt }
    }

    val missingPieces = remember(closetItems, lang, genderPref, personalProfile) {
        analyzeMissingCapsulePieces(closetItems, lang, genderPref, personalProfile)
    }
    val recommendedColors = remember(closetItems, lang) { suggestMissingHarmoniousColors(closetItems, lang) }

    var expandMissingPieces by rememberSaveable { mutableStateOf(false) }
    var expandMissingColors by rememberSaveable { mutableStateOf(false) }
    var expandUnworn by rememberSaveable { mutableStateOf(false) }
    var selectedGapForShop by remember { mutableStateOf<CapsuleGapItem?>(null) }

    fun openStoreSearch(url: String) {
        com.dresskit.wardrobe.data.SecurityGuards.openExternalUrlSafely(context, url)
    }

    selectedGapForShop?.let { gap ->
        val genderPrefix = when (genderPref) {
            GenderPreference.FEMALE -> if (lang == AppLanguage.TR) "kadın" else "women"
            GenderPreference.MALE -> if (lang == AppLanguage.TR) "erkek" else "men"
            GenderPreference.UNISEX -> ""
        }
        val profileMatches = personalProfile?.flatteringColors?.let { fl ->
            (gap.recommendedColors.filter { it in fl } + gap.recommendedColors).distinct()
        } ?: gap.recommendedColors

        AlertDialog(
            onDismissRequest = { selectedGapForShop = null },
            containerColor = DressKitStyle.cardSurface(),
            titleContentColor = primaryInk,
            textContentColor = secondaryInk,
            title = { Text(gap.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = gap.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryInk
                    )
                    Text(
                        text = gap.unlockedCombosText,
                        style = MaterialTheme.typography.labelMedium,
                        color = DressKitStyle.accentColor()
                    )
                    if (profileMatches.isNotEmpty()) {
                        Text(
                            text = localizedText(
                                lang,
                                "Önerilen Renkler",
                                "Recommended Colors",
                                "Empfohlene Farben",
                                "Couleurs recommandées",
                                "Colores recomendados",
                                "推荐颜色"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryInk
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            profileMatches.take(5).forEach { recCol ->
                                Surface(
                                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                    color = DressKitStyle.secondarySurface()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        ColorDot(color = recCol.composeColor, size = 10.dp)
                                        Text(
                                            text = recCol.display(lang),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = primaryInk
                                        )
                                    }
                                }
                            }
                        }
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
                            genderPrefix = genderPrefix,
                            rawQuery = gap.searchQuery,
                            googleLabel = "Google"
                        ).forEach { (store, url) ->
                            Surface(
                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                color = DressKitStyle.buttonBg(),
                                modifier = Modifier.clickable { openStoreSearch(url) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = DressKitStyle.buttonFg(),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = store,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DressKitStyle.buttonFg()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedGapForShop = null },
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DressKitStyle.canvasBg())
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.tabSummary,
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryInk
                    )
                    Text(
                        text = strings.summarySubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = strings.settingsTitle,
                        tint = primaryInk
                    )
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 114.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (remoteConfig.isBannerVisible("stats_top")) {
                    item {
                        RemoteAdSlotBanner(
                            slot = "stats_top",
                            config = remoteConfig,
                            lang = lang
                        )
                    }
                }
                // Top 4 Stat Tiles (classic clean layout matching v1.3)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(
                            label = if (isEn) "Clothes" else "Kıyafet",
                            value = "${closetItems.size}",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = if (isEn) "Photos" else "Fotoğraf",
                            value = "$totalPhotos",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = if (isEn) "Outfits" else "Kombin",
                            value = "${outfits.size}",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = if (isEn) "Favorites" else "Favori",
                            value = "$favoriteCount",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Wishlist & Price Tracker Summary Card
                if (wishlistItems.isNotEmpty()) {
                    item {
                        val totalBudget = remember(wishlistItems) {
                            wishlistItems.sumOf { PriceTrackerService.parsePrice(it.priceText) ?: 0.0 }
                        }
                        val trackedCount = remember(wishlistItems) {
                            wishlistItems.count { PriceTrackerService.isSafeHttpsUrl(it.productUrl) }
                        }
                        val droppedCount = remember(wishlistItems) {
                            wishlistItems.count { it.priceDropped }
                        }
                        Surface(
                            shape = RoundedCornerShape(cardCorner),
                            color = DressKitStyle.cardSurface(),
                            border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.BookmarkBorder,
                                        contentDescription = null,
                                        tint = DressKitStyle.accentColor(),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isEn) "Wishlist & Price Tracker" else "İstek Listesi & Fiyat Takibi",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryInk
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isEn) {
                                            "${wishlistItems.size} items · $trackedCount links · $droppedCount price drops"
                                        } else {
                                            "${wishlistItems.size} parça · $trackedCount takipli link · $droppedCount indirim"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryInk
                                    )
                                    if (totalBudget > 0.0) {
                                        Text(
                                            text = PriceTrackerService.formatPrice(totalBudget),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = DressKitStyle.accentColor()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Wardrobe Color Spectrum Bar + Breakdown List
                item {
                    Surface(
                        shape = RoundedCornerShape(cardCorner),
                        color = DressKitStyle.cardSurface(),
                        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (isEn) "Wardrobe Color Palette" else "Gardırop Renk Paleti",
                                style = MaterialTheme.typography.titleMedium,
                                color = primaryInk
                            )

                            if (colorCounts.isEmpty()) {
                                Text(
                                    text = if (isEn) {
                                        "Add clothes to visualize your wardrobe's color spectrum."
                                    } else {
                                        "Kıyafet ekledikçe dolabının baskın renk tonları burada görünecek."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryInk
                                )
                            } else {
                                val totalColorTags = colorCounts.sumOf { it.second }.coerceAtLeast(1)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(22.dp)
                                        .clip(RoundedCornerShape(DressKitStyle.chipCorner()))
                                ) {
                                    colorCounts.forEach { (color, count) ->
                                        Box(
                                            modifier = Modifier
                                                .weight(count.toFloat() / totalColorTags.toFloat())
                                                .fillMaxHeight()
                                                .background(color.composeColor)
                                        )
                                    }
                                }

                                colorCounts.take(8).forEach { (color, count) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            ColorDot(color = color.composeColor, size = 16.dp)
                                            Text(
                                                text = color.display(lang),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = primaryInk
                                            )
                                        }
                                        Text(
                                            text = if (isEn) "$count pcs" else "$count parça",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = secondaryInk
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Clothing Sub-Category Breakdown ("Kıyafet Türleri")
                if (subCategoryCounts.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(cardCorner),
                            color = DressKitStyle.cardSurface(),
                            border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (isEn) "Clothing Types" else "Kıyafet Türleri",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = primaryInk
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    subCategoryCounts.forEach { (sub, count) ->
                                        Surface(
                                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                            color = DressKitStyle.secondarySurface()
                                        ) {
                                            Text(
                                                text = "$count ${sub.display(lang)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = primaryInk,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // General Category Breakdown ("Genel Kategori Dağılımı")
                item {
                    Surface(
                        shape = RoundedCornerShape(cardCorner),
                        color = DressKitStyle.cardSurface(),
                        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (isEn) "General Category Breakdown" else "Genel Kategori Dağılımı",
                                style = MaterialTheme.typography.titleMedium,
                                color = primaryInk
                            )

                            if (categoryCounts.isEmpty()) {
                                Text(
                                    text = if (isEn) "No category data yet." else "Henüz kategori verisi yok.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryInk
                                )
                            } else {
                                val totalItems = closetItems.size.coerceAtLeast(1)
                                categoryCounts.forEach { (cat, count) ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = cat.display(lang),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = primaryInk
                                            )
                                            Text(
                                                text = "$count",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = secondaryInk
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { count.toFloat() / totalItems.toFloat() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(DressKitStyle.chipCorner())),
                                            color = if (DressKitStyle.isStreet()) {
                                                DressKitStyle.accentColor()
                                            } else {
                                                primaryInk
                                            },
                                            trackColor = DressKitStyle.secondarySurface()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Most Worn Pieces
                if (mostWorn.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(cardCorner),
                            color = DressKitStyle.cardSurface(),
                            border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (isEn) "Most Worn Pieces" else "En Çok Giyilen Parçalar",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = primaryInk
                                )
                                mostWorn.forEach { garment ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenItem(garment.id) },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = garment.name.ifBlank { garment.subCategory.display(lang) },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = primaryInk
                                        )
                                        Text(
                                            text = if (isEn) "${garment.wearCount}x" else "${garment.wearCount} kez",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (DressKitStyle.isStreet()) {
                                                DressKitStyle.accentColor()
                                            } else {
                                                DressKitColors.OliveSage
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bonus: Wardrobe Detox (Unworn Pieces waiting to be styled)
                if (unwornPieces.isNotEmpty() && closetItems.size >= 3) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(cardCorner),
                            color = DressKitStyle.cardSurface(),
                            border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = unwornPieces.size > 3) {
                                            expandUnworn = !expandUnworn
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Gardırop Detoksu (${unwornPieces.size} Hiç Giyilmedi)",
                                            "Closet Detox (${unwornPieces.size} Unworn)",
                                            "Schrank-Detox (${unwornPieces.size} ungetragen)",
                                            "Dressing Détox (${unwornPieces.size} non portés)",
                                            "Detox de Armario (${unwornPieces.size} sin usar)",
                                            "衣橱断舍离（${unwornPieces.size} 件未穿过）"
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryInk
                                    )
                                    if (unwornPieces.size > 3) {
                                        Text(
                                            text = if (expandUnworn) strings.collapse else "+${unwornPieces.size - 3}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DressKitStyle.accentColor()
                                        )
                                    }
                                }
                                Text(
                                    text = localizedText(
                                        lang,
                                        "Dolabında bekleyen parçalara şans ver (dokun → detayı aç):",
                                        "Give unworn pieces a spin (tap to inspect):",
                                        "Ungetragene Teile entdecken (tippen → öffnen):",
                                        "Redécouvrez vos pièces non portées (toucher) :",
                                        "Dale una oportunidad a tus prendas sin usar (toca):",
                                        "给闲置衣物一次出场机会（点击查看详情）："
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryInk
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    val shownUnworn = if (expandUnworn) unwornPieces else unwornPieces.take(3)
                                    shownUnworn.forEach { garment ->
                                        Surface(
                                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                            color = DressKitStyle.secondarySurface(),
                                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                            modifier = Modifier.clickable { onOpenItem(garment.id) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                garment.colors.firstOrNull()?.let { col ->
                                                    ColorDot(color = col.composeColor, size = 10.dp)
                                                }
                                                Text(
                                                    text = garment.name.ifBlank { garment.subCategory.display(lang) },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = primaryInk
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Compact Expandable Card 1: Missing Capsule Pieces
                item {
                    Surface(
                        shape = RoundedCornerShape(cardCorner),
                        color = DressKitStyle.cardSurface(),
                        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = missingPieces.size > 3) {
                                        expandMissingPieces = !expandMissingPieces
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.missingPiecesTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = primaryInk
                                )
                                if (missingPieces.size > 3) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (expandMissingPieces) strings.collapse else "+${missingPieces.size - 3}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DressKitStyle.accentColor()
                                        )
                                        Icon(
                                            imageVector = if (expandMissingPieces) {
                                                Icons.Outlined.KeyboardArrowUp
                                            } else {
                                                Icons.Outlined.KeyboardArrowDown
                                            },
                                            contentDescription = null,
                                            tint = DressKitStyle.accentColor(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (missingPieces.isEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = DressKitColors.OliveSage,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isEn) {
                                            "All core capsule pieces are in your wardrobe."
                                        } else {
                                            "Temel parçalarının tümü dolabında mevcut."
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DressKitColors.OliveSage
                                    )
                                }
                            } else {
                                val shown = if (expandMissingPieces) missingPieces else missingPieces.take(3)
                                shown.forEach { gap ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(DressKitStyle.buttonCorner()))
                                            .clickable { selectedGapForShop = gap }
                                            .background(DressKitStyle.canvasBg())
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AddCircleOutline,
                                            contentDescription = null,
                                            tint = DressKitStyle.accentColor(),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = gap.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = primaryInk
                                            )
                                            Text(
                                                text = gap.unlockedCombosText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = secondaryInk
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = DressKitStyle.accentColor(),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Compact Expandable Card 2: Missing Harmonious Colors
                item {
                    Surface(
                        shape = RoundedCornerShape(cardCorner),
                        color = DressKitStyle.cardSurface(),
                        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = recommendedColors.size > 3) {
                                        expandMissingColors = !expandMissingColors
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Palette,
                                        contentDescription = null,
                                        tint = primaryInk,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = strings.missingColorsTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryInk
                                    )
                                }
                                if (recommendedColors.size > 3) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (expandMissingColors) strings.collapse else "+${recommendedColors.size - 3}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DressKitStyle.accentColor()
                                        )
                                        Icon(
                                            imageVector = if (expandMissingColors) {
                                                Icons.Outlined.KeyboardArrowUp
                                            } else {
                                                Icons.Outlined.KeyboardArrowDown
                                            },
                                            contentDescription = null,
                                            tint = DressKitStyle.accentColor(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (recommendedColors.isEmpty()) {
                                Text(
                                    text = if (isEn) {
                                        "Your wardrobe color variety is very well balanced!"
                                    } else {
                                        "Dolabındaki renk çeşitliliği çok dengeli!"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DressKitColors.OliveSage
                                )
                            } else {
                                val shown = if (expandMissingColors) recommendedColors else recommendedColors.take(3)
                                shown.forEach { rec ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(DressKitStyle.buttonCorner()))
                                            .clickable {
                                                selectedGapForShop = CapsuleGapItem(
                                                    title = "${rec.color.display(lang)} ${localizedText(lang, "Parçalar", "Pieces", "Teile", "Pièces", "Prendas", "单品")}",
                                                    unlockedCombosText = rec.reason,
                                                    description = rec.reason,
                                                    recommendedColors = listOf(rec.color),
                                                    searchQuery = "${rec.color.display(lang)} basic üst giyim"
                                                )
                                            }
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ColorDot(color = rec.color.composeColor, size = 18.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = rec.color.display(lang),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = primaryInk
                                            )
                                            Text(
                                                text = rec.reason,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = secondaryInk
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = secondaryInk,
                                            modifier = Modifier.size(15.dp)
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

private fun analyzeMissingCapsulePieces(
    items: List<ClothingItem>,
    lang: AppLanguage,
    genderPref: GenderPreference,
    personalProfile: PersonalColorProfile? = null
): List<CapsuleGapItem> {
    val isTr = lang == AppLanguage.TR
    val topsCount = items.count { it.category == ClothingCategory.TOP }.coerceAtLeast(1)
    val bottomsCount = items.count { it.category == ClothingCategory.BOTTOM }.coerceAtLeast(1)
    val gaps = mutableListOf<CapsuleGapItem>()

    val hasWhiteOrEcruTee = items.any {
        (it.subCategory == ClothingSubCategory.TSHIRT || it.subCategory == ClothingSubCategory.POLO || it.subCategory == ClothingSubCategory.CROP_TOP) &&
            (it.colors.contains(GarmentColor.WHITE) || it.colors.contains(GarmentColor.ECRU))
    }
    if (!hasWhiteOrEcruTee) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Beyaz veya Ekru Basic Tişört" else "White or Ecru Basic T-Shirt",
                unlockedCombosText = if (isTr) "Mevcut $bottomsCount alt giyiminle +$bottomsCount yeni kombin açar" else "Unlocks +$bottomsCount new combos with your $bottomsCount bottoms",
                description = if (isTr) "Katmanlamanın ve günlük kombinlerin en kurtarıcı temel parçası." else "The ultimate everyday layering foundation for any wardrobe.",
                recommendedColors = listOf(GarmentColor.WHITE, GarmentColor.ECRU, GarmentColor.CHAMPAGNE),
                searchQuery = if (isTr) "beyaz oversize basic tişört" else "white heavyweight basic t-shirt"
            )
        )
    }

    if (items.none { it.subCategory == ClothingSubCategory.SHIRT }) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Düğmeli Gömlek (Beyaz / Mavi / Lacivert)" else "Button-Down Shirt (White / Sky / Navy)",
                unlockedCombosText = if (isTr) "Alt parçalarınla +${maxOf(2, bottomsCount)} kombin seçeneği ekler" else "Adds +${maxOf(2, bottomsCount)} smart styling options",
                description = if (isTr) "Hem açık önlü tişört üstü katman hem smart casual görünüm için idealdir." else "Works buttoned up for smart casual or worn open over a tee.",
                recommendedColors = listOf(GarmentColor.WHITE, GarmentColor.ICE_BLUE, GarmentColor.NAVY, GarmentColor.PETROL),
                searchQuery = if (isTr) "poplin veya oxford gömlek" else "oxford button down shirt"
            )
        )
    }

    val hasHoodieOrSweat = items.any {
        it.subCategory == ClothingSubCategory.HOODIE || it.subCategory == ClothingSubCategory.SWEATSHIRT
    }
    if (!hasHoodieOrSweat) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Hoodie veya Sweatshirt" else "Hoodie or Sweatshirt",
                unlockedCombosText = if (isTr) "Serin hava sokak stili kombinlerini artırır" else "Boosts cool-weather streetwear outfits",
                description = if (isTr) "Kot, kargo ve eşofman altlarıyla zahmetsiz sokak stili oluşturur." else "Pairs effortlessly with denim, cargo pants, and sneakers.",
                recommendedColors = listOf(GarmentColor.BLACK, GarmentColor.GRAY, GarmentColor.ECRU, GarmentColor.FOREST),
                searchQuery = if (isTr) "oversize hoodie sweatshirt" else "oversize cotton hoodie"
            )
        )
    }

    val hasKnitOrCardigan = items.any {
        it.subCategory == ClothingSubCategory.SWEATER || it.subCategory == ClothingSubCategory.CARDIGAN
    }
    if (!hasKnitOrCardigan) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Triko Kazak veya Hırka" else "Knit Sweater or Cardigan",
                unlockedCombosText = if (isTr) "Sonbahar ve kış şıklığını dengeler" else "Elevates autumn & winter smart layering",
                description = if (isTr) "Gömlek veya basic tişört üstüne sıcak ve temiz bir doku katar." else "Adds refined warmth and texture over tees or shirts.",
                recommendedColors = listOf(GarmentColor.SAND, GarmentColor.CHARCOAL, GarmentColor.OLIVE, GarmentColor.WINE),
                searchQuery = if (isTr) "triko kazak hırka" else "knit sweater cardigan"
            )
        )
    }

    if (items.none { it.subCategory == ClothingSubCategory.JEANS }) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Mavi veya Antrasit Kot / Jean" else "Blue or Charcoal Denim Jeans",
                unlockedCombosText = if (isTr) "Üst giyimlerinle +$topsCount kombin oluşturur" else "Pairs cleanly with your $topsCount tops",
                description = if (isTr) "4 mevsim her renk tişört, gömlek ve ceketle eşleşen ana alt parça." else "All-season anchor that matches every top and jacket.",
                recommendedColors = listOf(GarmentColor.DENIM, GarmentColor.SLATE_BLUE, GarmentColor.BLACK, GarmentColor.CHARCOAL),
                searchQuery = if (isTr) "relaxed straight fit jean pantolon" else "straight fit denim jeans"
            )
        )
    }

    val hasChinoOrBeigePants = items.any {
        (it.subCategory == ClothingSubCategory.CHINO || it.subCategory == ClothingSubCategory.PANTS) &&
            (it.colors.contains(GarmentColor.BEIGE) || it.colors.contains(GarmentColor.SAND) ||
                it.colors.contains(GarmentColor.STONE) || it.colors.contains(GarmentColor.CAMEL) ||
                it.colors.contains(GarmentColor.BLACK))
    }
    if (!hasChinoOrBeigePants) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Kum Beji / Taş veya Siyah Kumaş Pantolon" else "Sand / Stone or Black Trousers",
                unlockedCombosText = if (isTr) "Koyu üstleri dengeleyip +${maxOf(2, topsCount)} kombin açar" else "Balances dark tops for +${maxOf(2, topsCount)} outfits",
                description = if (isTr) "Siyah ve koyu lacivert üstlerle yüksek kontrastlı modern görünüm sağlar." else "Creates clean high-contrast silhouettes with dark tops.",
                recommendedColors = listOf(GarmentColor.SAND, GarmentColor.BEIGE, GarmentColor.STONE, GarmentColor.BLACK),
                searchQuery = if (isTr) "bej pileli kumaş pantolon chino" else "beige relaxed chino trousers"
            )
        )
    }

    if (items.none { it.subCategory == ClothingSubCategory.CARGO }) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Kargo Pantolon" else "Cargo Pants",
                unlockedCombosText = if (isTr) "Sokak stili görünümünü çeşitlendirir" else "Expands casual & street outfits",
                description = if (isTr) "Hoodie, bomber ve sneaker'larla hacimli sokak stili sunar." else "Pairs with hoodies, bombers, and chunky sneakers.",
                recommendedColors = listOf(GarmentColor.OLIVE, GarmentColor.SAND, GarmentColor.BLACK),
                searchQuery = if (isTr) "kargo pantolon" else "relaxed cargo pants"
            )
        )
    }

    val hasLightJacket = items.any {
        it.subCategory == ClothingSubCategory.JACKET || it.subCategory == ClothingSubCategory.BLAZER ||
            it.subCategory == ClothingSubCategory.VEST
    }
    if (!hasLightJacket) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Günlük Ceket veya Overshirt" else "Everyday Jacket or Overshirt",
                unlockedCombosText = if (isTr) "Serin günlerin dış giyim katmanını tamamlar" else "Completes transitional cool-day outfits",
                description = if (isTr) "Deri, denim veya bomber kesimli bir ceket sade tişörtleri anında yükseltir." else "Denim, bomber or leather jacket instantly upgrades plain tees.",
                recommendedColors = listOf(GarmentColor.CHOCOLATE, GarmentColor.BLACK, GarmentColor.DENIM, GarmentColor.OLIVE),
                searchQuery = if (isTr) "bomber ceket denim ceket" else "bomber denim jacket"
            )
        )
    }

    val hasCoatOrTrench = items.any {
        it.subCategory == ClothingSubCategory.COAT || it.subCategory == ClothingSubCategory.TRENCH
    }
    if (!hasCoatOrTrench) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Trençkot veya Kışlık Mont / Kaban" else "Trench Coat or Winter Coat",
                unlockedCombosText = if (isTr) "Yağmurlu & soğuk hava önerilerini eksiksiz yapar" else "Completes rainy & cold weather suggestions",
                description = if (isTr) "Soğuk ve yağışlı günlerde şıklığı ve korumayı bir arada tutar." else "Keeps layered outfits warm and weather-protected.",
                recommendedColors = listOf(GarmentColor.BEIGE, GarmentColor.CHOCOLATE, GarmentColor.NAVY, GarmentColor.CHARCOAL),
                searchQuery = if (isTr) "trençkot yün kaban mont" else "trench coat wool overcoat"
            )
        )
    }

    if (items.none { it.subCategory == ClothingSubCategory.SNEAKER }) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Temiz Beyaz veya Siyah Sneaker" else "Clean White or Black Sneakers",
                unlockedCombosText = if (isTr) "Tüm günlük kombinleri eksiksiz tamamlar" else "Finishes almost every everyday outfit",
                description = if (isTr) "Jean'den kumaş pantolona kadar her alt giyime uyan sade sneaker." else "Versatile minimalist sneakers matching jeans and tailored trousers.",
                recommendedColors = listOf(GarmentColor.WHITE, GarmentColor.BLACK, GarmentColor.ECRU),
                searchQuery = if (isTr) "beyaz deri günlük sneaker" else "white minimalist leather sneakers"
            )
        )
    }

    val hasDressyOrBootShoes = items.any {
        it.subCategory == ClothingSubCategory.BOOT || it.subCategory == ClothingSubCategory.LOAFER ||
            it.subCategory == ClothingSubCategory.HEELS
    }
    if (!hasDressyOrBootShoes) {
        gaps.add(
            CapsuleGapItem(
                title = if (isTr) "Bot veya Loafer / Klasik Ayakkabı" else "Boots or Loafers",
                unlockedCombosText = if (isTr) "Kışlık ve smart casual kombinleri tamamlar" else "Finishes winter & smart-casual looks",
                description = if (isTr) "Yağmurlu havalarda ve şık kombinlerde spor ayakkabıya güçlü alternatif." else "Refined alternative to sneakers for wet or smart days.",
                recommendedColors = listOf(GarmentColor.BLACK, GarmentColor.CHOCOLATE),
                searchQuery = if (isTr) "chelsea bot loafer ayakkabı" else "chelsea boots loafers"
            )
        )
    }

    if (genderPref == GenderPreference.FEMALE) {
        if (items.none { it.subCategory == ClothingSubCategory.BLAZER }) {
            gaps.add(
                CapsuleGapItem(
                    title = if (isTr) "Oversize Blazer Ceket" else "Oversized Blazer",
                    unlockedCombosText = if (isTr) "Smart Street görünümü güçlendirir" else "Elevates Smart Street styling",
                    description = if (isTr) "Tişört, crop veya gömlek üzerine atılabilen kurtarıcı ceket." else "Sharp blazer effortlessly styled over tees, crops, or dresses.",
                    recommendedColors = listOf(GarmentColor.BLACK, GarmentColor.SAND, GarmentColor.NAVY, GarmentColor.SLATE_BLUE),
                    searchQuery = if (isTr) "oversize blazer ceket" else "women oversized blazer"
                )
            )
        }
        if (items.none { it.subCategory == ClothingSubCategory.DRESS || it.subCategory == ClothingSubCategory.SKIRT }) {
            gaps.add(
                CapsuleGapItem(
                    title = if (isTr) "Elbise veya Etek" else "Versatile Dress or Skirt",
                    unlockedCombosText = if (isTr) "Tek parça şık veya katmanlı kombin açar" else "Unlocks effortless one-piece & layered looks",
                    description = if (isTr) "Sneaker, bot ve ceketlerle farklı stillere kolayca uyarlanır." else "Adaptable for sneakers, boots, or layered blazers.",
                    recommendedColors = listOf(GarmentColor.BLACK, GarmentColor.WINE, GarmentColor.ROSEWOOD, GarmentColor.OLIVE),
                    searchQuery = if (isTr) "midi elbise pileli etek" else "midi dress pleated skirt"
                )
            )
        }
    }

    // Tier 2: Seasonal, Smart Casual & Accessory Expansion
    val tier2Extras = listOf(
        items.none { it.subCategory == ClothingSubCategory.POLO } to CapsuleGapItem(
            title = if (isTr) "Polo Yaka veya Örgü Triko Üst" else "Knit Polo Shirt",
            unlockedCombosText = if (isTr) "Smart casual yaz & bahar kombinlerini zenginleştirir" else "Adds effortless smart-casual spring options",
            description = if (isTr) "Chino ve kumaş pantolonlarla çok temiz bir hafta sonu görünümü verir." else "Clean knit or pique polo pairing seamlessly with chinos.",
            recommendedColors = listOf(GarmentColor.ECRU, GarmentColor.NAVY, GarmentColor.FOREST, GarmentColor.WINE),
            searchQuery = if (isTr) "triko polo yaka tişört" else "knit polo shirt"
        ),
        items.none { it.subCategory == ClothingSubCategory.VEST } to CapsuleGapItem(
            title = if (isTr) "Şişme Yelek veya Triko Süveter" else "Puffer Vest or Layering Vest",
            unlockedCombosText = if (isTr) "Hoodie ve gömlek üstüne derinlik katar" else "Adds versatile layered depth over hoodies & shirts",
            description = if (isTr) "Mevsim geçişlerinde hareket özgürlüğüyle sıcak katman sağlar." else "Great mid-season outerwear piece over hoodies or knits.",
            recommendedColors = listOf(GarmentColor.BLACK, GarmentColor.OLIVE, GarmentColor.SAND, GarmentColor.CHOCOLATE),
            searchQuery = if (isTr) "şişme yelek" else "puffer layering vest"
        ),
        items.none { it.subCategory == ClothingSubCategory.SHORTS } to CapsuleGapItem(
            title = if (isTr) "Yazlık Kumaş / Denim Şort" else "Relaxed Cotton or Denim Shorts",
            unlockedCombosText = if (isTr) "25°C+ sıcak günler için yeni kombinler açar" else "Unlocks warm summer-weather combos",
            description = if (isTr) "Sıcak yaz günlerinde tişört ve keten gömleklerle ideal." else "Essential summer bottom for hot forecast days.",
            recommendedColors = listOf(GarmentColor.SAND, GarmentColor.BLACK, GarmentColor.DENIM, GarmentColor.OLIVE),
            searchQuery = if (isTr) "baggy keten şort denim şort" else "relaxed cotton shorts"
        ),
        items.none { it.subCategory == ClothingSubCategory.TRACKSUIT || it.subCategory == ClothingSubCategory.SWEATPANTS } to CapsuleGapItem(
            title = if (isTr) "Eşofman Altı / Athleisure Takım" else "Tailored Jogger or Tracksuit",
            unlockedCombosText = if (isTr) "Seyahat ve hafta sonu rahatlığını tamamlar" else "Completes travel & off-duty athleisure looks",
            description = if (isTr) "Kalın pamuklu düz paça eşofman altı sweatshirtlerle modern durur." else "Heavyweight straight-leg sweatpants built for everyday comfort.",
            recommendedColors = listOf(GarmentColor.CHARCOAL, GarmentColor.LIGHT_GRAY, GarmentColor.FOREST, GarmentColor.NAVY),
            searchQuery = if (isTr) "düz paça heavyweight eşofman altı" else "heavyweight straight sweatpants"
        ),
        items.none { it.subCategory == ClothingSubCategory.BAG } to CapsuleGapItem(
            title = if (isTr) "Günlük Çanta (Tote / Çapraz Askılı)" else "Everyday Bag (Tote or Crossbody)",
            unlockedCombosText = if (isTr) "Kombinlerine işlevsel aksesuar vurgusu katar" else "Adds functional accessory polish to outfits",
            description = if (isTr) "Sade kombinleri tamamlayan pratik omuz veya çapraz çanta." else "Practical shoulder or crossbody bag tying outfits together.",
            recommendedColors = listOf(GarmentColor.BLACK, GarmentColor.CHOCOLATE, GarmentColor.ECRU),
            searchQuery = if (isTr) "kanvas tote çanta çapraz çanta" else "minimal crossbody tote bag"
        ),
        items.none { it.subCategory == ClothingSubCategory.WATCH || it.subCategory == ClothingSubCategory.GLASSES } to CapsuleGapItem(
            title = if (isTr) "Güneş Gözlüğü & Minimal Kol Saati" else "Minimalist Watch & Sunglasses",
            unlockedCombosText = if (isTr) "Her kombinin görünümünü %15 daha özenli gösterir" else "Instantly sharpens every outfit silhouette",
            description = if (isTr) "Klasik çerçeveli güneş gözlüğü ve sade saat ikilisi." else "Timeless everyday accessories that polish simple outfits.",
            recommendedColors = listOf(GarmentColor.BLACK, GarmentColor.CHOCOLATE, GarmentColor.CHAMPAGNE),
            searchQuery = if (isTr) "minimal güneş gözlüğü saat" else "minimal sunglasses classic watch"
        ),
        items.none { it.subCategory == ClothingSubCategory.BELT || it.subCategory == ClothingSubCategory.CAP } to CapsuleGapItem(
            title = if (isTr) "Deri Kemer & Sokak Şapkası / Bere" else "Leather Belt & Streetwear Cap",
            unlockedCombosText = if (isTr) "Alt-üst geçişlerini ve sokak stilini güçlendirir" else "Frames waistlines and completes street outfits",
            description = if (isTr) "Kombinlerde kontrast ve bitiriş sağlayan imza aksesuarlar." else "Finishing accessories for effortless off-duty styling.",
            recommendedColors = listOf(GarmentColor.BLACK, GarmentColor.BROWN, GarmentColor.NAVY, GarmentColor.OLIVE),
            searchQuery = if (isTr) "deri kemer beyzbol şapkası" else "leather belt baseball cap"
        )
    )
    tier2Extras.forEach { (missing, gap) ->
        if (missing) gaps.add(gap)
    }

    // Tier 3: Continuous Color x Garment Suggestions so recommendations never run empty
    val dynamicExpansions = listOf(
        Triple(GarmentColor.ECRU, ClothingSubCategory.HOODIE, if (isTr) "Ekru Oversize Hoodie" else "Ecru Oversized Hoodie"),
        Triple(GarmentColor.OLIVE, ClothingSubCategory.JACKET, if (isTr) "Haki Overshirt / Utility Ceket" else "Olive Utility Overshirt"),
        Triple(GarmentColor.ICE_BLUE, ClothingSubCategory.SHIRT, if (isTr) "Buz Mavisi Keten / Oxford Gömlek" else "Ice Blue Linen or Oxford Shirt"),
        Triple(GarmentColor.CHOCOLATE, ClothingSubCategory.JACKET, if (isTr) "Çikolata Kahve Deri / Süet Ceket" else "Chocolate Suede or Leather Jacket"),
        Triple(GarmentColor.SAND, ClothingSubCategory.CARGO, if (isTr) "Kum Beji Relaxed Kargo / Chino" else "Sand Relaxed Cargo Trousers"),
        Triple(GarmentColor.FOREST, ClothingSubCategory.SWEATER, if (isTr) "Orman Yeşili Triko Kazak" else "Forest Green Knit Sweater"),
        Triple(GarmentColor.PETROL, ClothingSubCategory.SHIRT, if (isTr) "Petrol Yeşili Oversize Gömlek" else "Petrol Overshirt / Shirt"),
        Triple(GarmentColor.WINE, ClothingSubCategory.SWEATSHIRT, if (isTr) "Şarap Bordo Heavy Sweatshirt" else "Wine Heavyweight Sweatshirt"),
        Triple(GarmentColor.SLATE_BLUE, ClothingSubCategory.BLAZER, if (isTr) "Arduvaz Mavi Smart Blazer" else "Slate Blue Casual Blazer"),
        Triple(GarmentColor.CHAMPAGNE, ClothingSubCategory.TSHIRT, if (isTr) "Şampanya Tonu Heavyweight Tişört" else "Champagne Heavyweight Tee"),
        Triple(GarmentColor.ROSEWOOD, ClothingSubCategory.CARDIGAN, if (isTr) "Gül Kurusu Yumuşak Hırka" else "Rosewood Textured Cardigan"),
        Triple(GarmentColor.RUST, ClothingSubCategory.JACKET, if (isTr) "Pas Turuncu Mevsimlik Ceket" else "Rust Transitional Jacket"),
        Triple(GarmentColor.CHARCOAL, ClothingSubCategory.PANTS, if (isTr) "Antrasit Geniş Paça Pantolon" else "Charcoal Wide-Leg Trousers"),
        Triple(GarmentColor.TEAL, ClothingSubCategory.TSHIRT, if (isTr) "Camgöbeği / Teal Yazlık Tişört" else "Teal Relaxed Summer Tee")
    )

    val skinMatches = personalProfile?.flatteringColors?.toSet() ?: emptySet()
    val prioritizedExpansions = dynamicExpansions.sortedByDescending { (col, _, _) ->
        if (col in skinMatches) 1 else 0
    }

    for ((targetColor, targetSub, title) in prioritizedExpansions) {
        if (gaps.size >= 12) break
        if (genderPref == GenderPreference.MALE &&
            targetSub in setOf(ClothingSubCategory.DRESS, ClothingSubCategory.SKIRT, ClothingSubCategory.CROP_TOP, ClothingSubCategory.HEELS)
        ) continue

        val alreadyOwned = items.any { it.subCategory == targetSub && targetColor in it.colors }
        if (!alreadyOwned) {
            val isSkinMatch = targetColor in skinMatches
            val subLabel = targetSub.display(lang)
            val colorLabel = targetColor.display(lang)
            gaps.add(
                CapsuleGapItem(
                    title = title,
                    unlockedCombosText = if (isSkinMatch) {
                        if (isTr) "✨ Ten tonunla tam uyumlu · yeni stil kombinleri açar" else "✨ Skin-tone flattering pick · unlocks fresh combos"
                    } else {
                        if (isTr) "Mevcut dolabınla +${maxOf(3, bottomsCount)} yeni renk uyumu sağlar" else "Adds +${maxOf(3, bottomsCount)} fresh color pairings"
                    },
                    description = if (isTr) {
                        "Dolabındaki eksikleri $colorLabel tonlu $subLabel ile tamamlayarak daha çeşitli kombinler üretebilirsin."
                    } else {
                        "Completing your closet with a $colorLabel $subLabel adds rich visual variety to daily outfits."
                    },
                    recommendedColors = listOf(targetColor),
                    searchQuery = "$colorLabel $subLabel"
                )
            )
        }
    }

    return gaps
}

private fun suggestMissingHarmoniousColors(items: List<ClothingItem>, lang: AppLanguage): List<ColorGapSuggestion> {
    val isEn = lang != AppLanguage.TR
    val existing = items.flatMap { it.colors }.toSet()
    val candidates = listOf(
        ColorGapSuggestion(GarmentColor.BEIGE, if (isEn) "Matches black, navy & olive effortlessly" else "Siyah, lacivert ve hakiyle yüksek uyum sağlar"),
        ColorGapSuggestion(GarmentColor.ECRU, if (isEn) "Softens dark garments with clean contrast" else "Koyu parçaları yumuşatıp temiz kontrast yaratır"),
        ColorGapSuggestion(GarmentColor.SAND, if (isEn) "Modern neutral base for warm & cool tops" else "Tüm üstlerle çalışan modern kum beji nötr ton"),
        ColorGapSuggestion(GarmentColor.NAVY, if (isEn) "Pairs cleanly with gray, white, beige & blush" else "Gri, beyaz, bej ve pudra tonlarla kolay eşleşir"),
        ColorGapSuggestion(GarmentColor.OLIVE, if (isEn) "Natural street harmony with black, ecru & denim" else "Siyah, ekru ve denimle doğal sokak uyumu verir"),
        ColorGapSuggestion(GarmentColor.CHOCOLATE, if (isEn) "Quiet luxury depth with cream, ice blue & camel" else "Ekru, buz mavisi ve camel ile zengin görünüm sunar"),
        ColorGapSuggestion(GarmentColor.FOREST, if (isEn) "Deep botanical green elevating beige & gray" else "Bej ve gri tonları derinleştiren şık orman yeşili"),
        ColorGapSuggestion(GarmentColor.SLATE_BLUE, if (isEn) "Crisp architectural blue pairing with neutrals" else "Nötrlerle çok şık duran modern arduvaz mavisi"),
        ColorGapSuggestion(GarmentColor.ICE_BLUE, if (isEn) "Fresh airy contrast under navy, gray & brown" else "Lacivert, gri ve kahve altında ferah aydınlık ton"),
        ColorGapSuggestion(GarmentColor.WINE, if (isEn) "Statement burgundy accent with gray & denim" else "Gri, ekru ve denimle dikkat çeken asil şarap bordo"),
        ColorGapSuggestion(GarmentColor.TERRACOTTA, if (isEn) "Sun-baked warmth boosting navy & cream outfits" else "Lacivert ve krem kombinlere sıcak enerji katar"),
        ColorGapSuggestion(GarmentColor.PETROL, if (isEn) "Sophisticated blue-green depth for smart fits" else "Smart-street stillere derinlik katan petrol yeşili"),
        ColorGapSuggestion(GarmentColor.SAGE, if (isEn) "Calm earthy green matching sand & white" else "Kum beji ve beyazla çok doğal duran adaçayı yeşili"),
        ColorGapSuggestion(GarmentColor.CHAMPAGNE, if (isEn) "Soft satin highlight replacing stark white" else "Sert beyaz yerine yumuşak, şık şampanya tonu"),
        ColorGapSuggestion(GarmentColor.RUST, if (isEn) "Autumn accent popping over black & olive" else "Siyah ve hakiyle harika kontrast kuran pas turuncu"),
        ColorGapSuggestion(GarmentColor.ROSEWOOD, if (isEn) "Dusty rosewood softening charcoal & denim" else "Antrasit ve denimi yumuşatan gül kurusu vurgu"),
        ColorGapSuggestion(GarmentColor.EMERALD, if (isEn) "Rich jewel-tone green for crisp white & black" else "Siyah ve beyazla canlı duran zümrüt yeşili"),
        ColorGapSuggestion(GarmentColor.TEAL, if (isEn) "Balanced blue-green working year-round" else "4 mevsim kullanılabilen dengeli camgöbeği / teal"),
        ColorGapSuggestion(GarmentColor.OCHRE, if (isEn) "Warm saffron spice accent over dark layers" else "Koyu katmanları canlandıran sıcak safran vurgusu"),
        ColorGapSuggestion(GarmentColor.LAVENDER, if (isEn) "Fresh pastel twist with charcoal, white & denim" else "Antrasit ve beyazla modern duran soft lavanta"),
        ColorGapSuggestion(GarmentColor.PLUM, if (isEn) "Moody dark purple pairing cleanly with gray" else "Gri ve taş tonlarıyla asil duran mürdüm rengi"),
        ColorGapSuggestion(GarmentColor.CORAL, if (isEn) "Lively spring-summer warmth over ecru & beige" else "Ekru ve bej üzerinde bahar enerjisi veren mercan"),
        ColorGapSuggestion(GarmentColor.BLACK, if (isEn) "Balances vibrant colors on its own" else "Tüm renkli parçaları tek başına dengeler"),
        ColorGapSuggestion(GarmentColor.DENIM, if (isEn) "All-season staple with white, camel & black tops" else "Beyaz, camel ve siyah üstlerle her mevsim çalışır")
    )
    return candidates.filter { it.color !in existing }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(DressKitStyle.cardCorner()),
        color = DressKitStyle.cardSurface(),
        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = DressKitStyle.primaryInk()
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = DressKitStyle.secondaryInk(),
                maxLines = 1
            )
        }
    }
}
