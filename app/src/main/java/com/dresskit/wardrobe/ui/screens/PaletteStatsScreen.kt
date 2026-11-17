package com.dresskit.wardrobe.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.ui.components.ColorDot
import com.dresskit.wardrobe.ui.components.bouncyOverscroll
import com.dresskit.wardrobe.ui.theme.DressKitColors
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings

private data class CapsuleGapItem(
    val title: String,
    val unlockedCombosText: String
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
    listState: LazyListState = rememberLazyListState()
) {
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val isEn = lang == AppLanguage.EN
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val cardCorner = DressKitStyle.cardCorner()

    val closetItems = remember(items) { items.filter { !it.isWishlist } }
    val totalPhotos = remember(closetItems) { closetItems.sumOf { it.photoFileNames.size } }
    val favoriteCount = remember(items) { items.count { it.isFavorite } }

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

    val missingPieces = remember(closetItems, lang) { analyzeMissingCapsulePieces(closetItems, lang) }
    val recommendedColors = remember(closetItems, lang) { suggestMissingHarmoniousColors(closetItems, lang) }

    var expandMissingPieces by rememberSaveable { mutableStateOf(false) }
    var expandMissingColors by rememberSaveable { mutableStateOf(false) }

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
                                        modifier = Modifier.fillMaxWidth(),
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
                                    .clickable(enabled = missingPieces.size > 2) {
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
                                if (missingPieces.size > 2) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (expandMissingPieces) strings.collapse else "+${missingPieces.size - 2}",
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
                                val shown = if (expandMissingPieces) missingPieces else missingPieces.take(2)
                                shown.forEach { gap ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(DressKitStyle.buttonCorner()))
                                            .background(DressKitStyle.canvasBg())
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                    .clickable(enabled = recommendedColors.size > 2) {
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
                                if (recommendedColors.size > 2) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (expandMissingColors) strings.collapse else "+${recommendedColors.size - 2}",
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
                                val shown = if (expandMissingColors) recommendedColors else recommendedColors.take(2)
                                shown.forEach { rec ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
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

private fun analyzeMissingCapsulePieces(items: List<ClothingItem>, lang: AppLanguage): List<CapsuleGapItem> {
    val isEn = lang == AppLanguage.EN
    val topsCount = items.count { it.category == ClothingCategory.TOP }.coerceAtLeast(1)
    val bottomsCount = items.count { it.category == ClothingCategory.BOTTOM }.coerceAtLeast(1)
    val gaps = mutableListOf<CapsuleGapItem>()

    val hasWhiteOrEcruTee = items.any {
        (it.subCategory == ClothingSubCategory.TSHIRT || it.subCategory == ClothingSubCategory.POLO) &&
            (it.colors.contains(GarmentColor.WHITE) || it.colors.contains(GarmentColor.ECRU))
    }
    if (!hasWhiteOrEcruTee) {
        gaps.add(
            CapsuleGapItem(
                title = if (isEn) "White or Ecru T-Shirt" else "Beyaz veya Ekru Tişört",
                unlockedCombosText = if (isEn) {
                    "Unlocks +$bottomsCount new combos with your $bottomsCount bottoms"
                } else {
                    "Mevcut $bottomsCount alt giyiminle +$bottomsCount yeni kombin açar"
                }
            )
        )
    }

    if (items.none { it.subCategory == ClothingSubCategory.SHIRT }) {
        gaps.add(
            CapsuleGapItem(
                title = if (isEn) "Shirt (White / Sky Blue / Navy)" else "Gömlek (Beyaz / Açık Mavi / Lacivert)",
                unlockedCombosText = if (isEn) {
                    "Adds +${maxOf(2, bottomsCount)} styling options with bottoms"
                } else {
                    "Alt parçalarınla +${maxOf(2, bottomsCount)} kombin seçeneği ekler"
                }
            )
        )
    }

    val hasHoodieOrCardigan = items.any {
        it.subCategory == ClothingSubCategory.HOODIE ||
            it.subCategory == ClothingSubCategory.CARDIGAN ||
            it.subCategory == ClothingSubCategory.SWEATSHIRT
    }
    if (!hasHoodieOrCardigan) {
        gaps.add(
            CapsuleGapItem(
                title = if (isEn) "Hoodie or Cardigan" else "Hoodie veya Hırka",
                unlockedCombosText = if (isEn) {
                    "Expands cool-weather layered outfits"
                } else {
                    "Serin hava katman kombinlerini artırır"
                }
            )
        )
    }

    if (items.none { it.subCategory == ClothingSubCategory.JEANS }) {
        gaps.add(
            CapsuleGapItem(
                title = if (isEn) "Denim Jeans" else "Kot / Jean Pantolon",
                unlockedCombosText = if (isEn) {
                    "Pairs cleanly with your $topsCount tops"
                } else {
                    "Üst giyimlerinle +$topsCount kombin oluşturur"
                }
            )
        )
    }

    val hasChinoOrBeigePants = items.any {
        (it.subCategory == ClothingSubCategory.CHINO || it.subCategory == ClothingSubCategory.PANTS) &&
            (it.colors.contains(GarmentColor.BEIGE) || it.colors.contains(GarmentColor.STONE) ||
                it.colors.contains(GarmentColor.CAMEL) || it.colors.contains(GarmentColor.BLACK))
    }
    if (!hasChinoOrBeigePants) {
        gaps.add(
            CapsuleGapItem(
                title = if (isEn) "Beige / Stone or Black Trousers" else "Bej / Taş veya Siyah Pantolon",
                unlockedCombosText = if (isEn) {
                    "Balances dark tops for +${maxOf(2, topsCount)} outfits"
                } else {
                    "Koyu üstleri dengeleyip +${maxOf(2, topsCount)} kombin açar"
                }
            )
        )
    }

    if (items.none { it.category == ClothingCategory.OUTERWEAR }) {
        gaps.add(
            CapsuleGapItem(
                title = if (isEn) "Jacket or Coat" else "Ceket veya Mont",
                unlockedCombosText = if (isEn) {
                    "Completes rainy & cold weather suggestions"
                } else {
                    "Yağmurlu ve soğuk hava önerilerini tamamlar"
                }
            )
        )
    }

    if (items.none { it.category == ClothingCategory.SHOES }) {
        gaps.add(
            CapsuleGapItem(
                title = if (isEn) "White or Black Sneakers" else "Beyaz veya Siyah Sneaker",
                unlockedCombosText = if (isEn) {
                    "Finishes all everyday streetwear fits"
                } else {
                    "Tüm sokak kombinlerini eksiksiz tamamlar"
                }
            )
        )
    }

    return gaps
}

private fun suggestMissingHarmoniousColors(items: List<ClothingItem>, lang: AppLanguage): List<ColorGapSuggestion> {
    val isEn = lang == AppLanguage.EN
    val existing = items.flatMap { it.colors }.toSet()
    val candidates = listOf(
        ColorGapSuggestion(
            GarmentColor.BEIGE,
            if (isEn) "Matches black, navy & olive effortlessly" else "Siyah, lacivert ve hakiyle yüksek uyum sağlar"
        ),
        ColorGapSuggestion(
            GarmentColor.ECRU,
            if (isEn) "Softens dark garments with clean contrast" else "Koyu parçaları yumuşatıp temiz kontrast yaratır"
        ),
        ColorGapSuggestion(
            GarmentColor.NAVY,
            if (isEn) "Pairs cleanly with gray, white, beige & blush" else "Gri, beyaz, bej ve pudra tonlarla kolay eşleşir"
        ),
        ColorGapSuggestion(
            GarmentColor.OLIVE,
            if (isEn) "Natural street harmony with black, ecru & denim" else "Siyah, ekru ve denimle doğal sokak uyumu verir"
        ),
        ColorGapSuggestion(
            GarmentColor.BLACK,
            if (isEn) "Balances vibrant colors on its own" else "Tüm renkli parçaları tek başına dengeler"
        ),
        ColorGapSuggestion(
            GarmentColor.GRAY,
            if (isEn) "Pairs smoothly with pastel pink, black & navy" else "Pudra, pembe, siyah ve lacivertle çok uyumludur"
        ),
        ColorGapSuggestion(
            GarmentColor.BLUSH,
            if (isEn) "Modern accent for gray & navy looks" else "Gri ve lacivert kombinlere modern vurgu katar"
        ),
        ColorGapSuggestion(
            GarmentColor.DENIM,
            if (isEn) "All-season staple with white, camel & black tops" else "Beyaz, camel ve siyah üstlerle her mevsim çalışır"
        )
    )
    return candidates.filter { it.color !in existing }.take(6)
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
