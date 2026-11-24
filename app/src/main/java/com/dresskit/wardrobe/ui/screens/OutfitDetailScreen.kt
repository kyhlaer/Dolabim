package com.dresskit.wardrobe.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.OutfitRecommendationEngine
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.ui.components.ColorDot
import com.dresskit.wardrobe.ui.components.FilterChipPill
import com.dresskit.wardrobe.ui.components.HarmonyScoreBadge
import com.dresskit.wardrobe.ui.components.WearActionButton
import com.dresskit.wardrobe.ui.components.WearCountEditorDialog
import com.dresskit.wardrobe.ui.theme.DressKitColors
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitDetailScreen(
    initialTitle: String,
    initialItems: List<ClothingItem>,
    allWardrobeItems: List<ClothingItem>,
    existingOutfitId: String? = null,
    onBack: () -> Unit,
    onOpenTryOn2D: (List<ClothingItem>) -> Unit,
    onMarkOutfitWorn: (List<ClothingItem>) -> Unit,
    onUpdateWearCounts: (Map<String, Int>) -> Unit = {},
    onOpenItem: (String) -> Unit = {},
    onShareOutfit: (title: String, items: List<ClothingItem>, score: Int) -> Unit = { _, _, _ -> },
    onSaveOutfit: (
        title: String,
        items: List<ClothingItem>,
        score: Int,
        note: String,
        existingId: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val isEn = lang != AppLanguage.TR
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var activeItems by remember(initialItems) {
        mutableStateOf(OutfitRecommendationEngine.sortTopToBottom(initialItems))
    }

    var pieceToSwap by remember { mutableStateOf<ClothingItem?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showWearCountEditor by remember { mutableStateOf(false) }

    if (showWearCountEditor) {
        val liveItems = remember(activeItems, allWardrobeItems) {
            activeItems.map { cur -> allWardrobeItems.find { it.id == cur.id } ?: cur }
        }
        WearCountEditorDialog(
            itemsToEdit = liveItems,
            onSave = { updates ->
                onUpdateWearCounts(updates)
                showWearCountEditor = false
            },
            onDismiss = { showWearCountEditor = false }
        )
    }

    val (harmonyScore, harmonyNote) = remember(activeItems, lang) {
        OutfitRecommendationEngine.calculateColorHarmony(activeItems, lang)
    }

    Scaffold(
        containerColor = DressKitStyle.canvasBg(),
        topBar = {
            Surface(color = DressKitStyle.canvasBg()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = if (isEn) "Back" else "Geri",
                                    tint = primaryInk
                                )
                            }
                            Text(
                                text = strings.outfitDetailTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onShareOutfit(title, activeItems, harmonyScore) },
                                enabled = activeItems.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = if (isEn) "Share" else "Paylaş",
                                    tint = primaryInk
                                )
                            }
                            HarmonyScoreBadge(
                                score = harmonyScore,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = DressKitStyle.borderColor())
                }
            }
        },
        bottomBar = {
            Surface(
                color = DressKitStyle.cardSurface(),
                border = BorderStroke(1.dp, DressKitStyle.borderColor())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onOpenTryOn2D(activeItems) },
                        enabled = activeItems.isNotEmpty(),
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(0.85f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccessibilityNew,
                            contentDescription = null,
                            tint = primaryInk,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.try2DShort, color = primaryInk, maxLines = 1)
                    }

                    WearActionButton(
                        label = strings.wornBtn,
                        onClick = { onMarkOutfitWorn(activeItems) },
                        onLongClick = { if (activeItems.isNotEmpty()) showWearCountEditor = true },
                        filled = false,
                        enabled = activeItems.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(0.85f)
                    )

                    Button(
                        onClick = {
                            onSaveOutfit(
                                title.ifBlank { strings.outfitDetailTitle },
                                activeItems,
                                harmonyScore,
                                harmonyNote,
                                existingOutfitId
                            )
                        },
                        enabled = activeItems.isNotEmpty(),
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DressKitStyle.buttonBg(),
                            contentColor = DressKitStyle.buttonFg()
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkAdded,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (existingOutfitId != null) {
                                if (isEn) "Update" else "Güncelle"
                            } else {
                                strings.save
                            },
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Summary & Name Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                    color = DressKitStyle.cardSurface(),
                    border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            singleLine = true,
                            label = { Text(if (isEn) "Outfit Name" else "Kombin Başlığı") },
                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DressKitStyle.secondarySurface(),
                                unfocusedContainerColor = DressKitStyle.secondarySurface(),
                                focusedBorderColor = DressKitStyle.accentColor(),
                                unfocusedBorderColor = DressKitStyle.borderColor(),
                                focusedTextColor = primaryInk,
                                unfocusedTextColor = primaryInk
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = harmonyNote,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = primaryInk
                                )
                                Text(
                                    text = "${activeItems.size} ${if (isEn) "pieces" else "parça"} · ${strings.topToBottomHint}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryInk
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                activeItems.mapNotNull { it.colors.firstOrNull()?.composeColor }.forEach { col ->
                                    ColorDot(color = col, size = 14.dp)
                                }
                            }
                        }
                    }
                }
            }

            // Head-to-toe stacked pieces
            items(activeItems, key = { it.id }) { piece ->
                val photoName = piece.coverPhoto
                val photoFile = remember(photoName) {
                    photoName?.let { ImageStorage.getPhotoFile(context, it) }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                    color = DressKitStyle.cardSurface(),
                    border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenItem(piece.id) }
                                .background(
                                    if (piece.useOriginalPhotos) DressKitStyle.photoBoxSurface() else Color.White
                                )
                        ) {
                            if (photoFile != null && photoFile.exists()) {
                                AsyncImage(
                                    model = photoFile,
                                    contentDescription = piece.name,
                                    contentScale = if (piece.useOriginalPhotos) ContentScale.Crop else ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(if (piece.useOriginalPhotos) 0.dp else 6.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                color = DressKitStyle.secondarySurface()
                            ) {
                                Text(
                                    text = piece.category.display(lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DressKitStyle.accentColor(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Text(
                                text = piece.name.ifBlank { piece.subCategory.display(lang) },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onOpenItem(piece.id) }
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                piece.colors.take(2).forEach { col ->
                                    ColorDot(color = col.composeColor, size = 11.dp)
                                }
                                Text(
                                    text = piece.subCategory.display(lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryInk
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { pieceToSwap = piece },
                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SwapHoriz,
                                        contentDescription = null,
                                        tint = primaryInk,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.swapPiece, style = MaterialTheme.typography.labelSmall, color = primaryInk)
                                }

                                if (activeItems.size > 1) {
                                    OutlinedButton(
                                        onClick = {
                                            activeItems = activeItems.filterNot { it.id == piece.id }
                                        },
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        border = BorderStroke(1.dp, DressKitColors.DangerRust.copy(alpha = 0.45f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = null,
                                            tint = DressKitColors.DangerRust,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            strings.removePiece,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DressKitColors.DangerRust
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Add Piece CTA
            item {
                OutlinedButton(
                    onClick = { showAddSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = DressKitStyle.accentColor(),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.addPiece,
                        style = MaterialTheme.typography.titleSmall,
                        color = DressKitStyle.accentColor()
                    )
                }
            }
        }
    }

    // Swap Piece Bottom Sheet
    pieceToSwap?.let { target ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var showAllCategories by remember { mutableStateOf(false) }

        val candidates = remember(target, allWardrobeItems, activeItems, showAllCategories) {
            val activeIds = activeItems.map { it.id }.toSet()
            allWardrobeItems.filter { item ->
                item.id != target.id &&
                    !activeIds.contains(item.id) &&
                    (showAllCategories || item.category == target.category)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { pieceToSwap = null },
            sheetState = sheetState,
            containerColor = DressKitStyle.cardSurface(),
            contentColor = primaryInk
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) "Swap: ${target.name}" else "Değiştir: ${target.name}",
                            style = MaterialTheme.typography.titleLarge,
                            color = primaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isEn) "Tap an alternative to preview harmony" else "Yeni uyum puanıyla seç",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryInk
                        )
                    }
                    FilterChipPill(
                        label = if (showAllCategories) {
                            target.category.display(lang)
                        } else {
                            if (isEn) "All Types" else "Tüm Türler"
                        },
                        selected = showAllCategories,
                        onClick = { showAllCategories = !showAllCategories }
                    )
                }

                if (candidates.isEmpty()) {
                    Text(
                        text = if (isEn) {
                            "No other pieces in this category yet. Tap 'All Types' or add more items to your closet."
                        } else {
                            "Bu kategoride başka parça bulunamadı. 'Tüm Türler' tuşuna basabilir veya dolabına parça ekleyebilirsin."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(candidates, key = { it.id }) { candidate ->
                            val simulated = remember(candidate, activeItems, target, lang) {
                                val nextList = activeItems.map { if (it.id == target.id) candidate else it }
                                OutfitRecommendationEngine.calculateColorHarmony(nextList, lang).first
                            }
                            CandidatePickRow(
                                item = candidate,
                                predictedScore = simulated,
                                onSelect = {
                                    val next = activeItems.map { if (it.id == target.id) candidate else it }
                                    activeItems = OutfitRecommendationEngine.sortTopToBottom(next)
                                    pieceToSwap = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Piece Bottom Sheet
    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var filterCategory by remember { mutableStateOf<ClothingCategory?>(null) }

        val addable = remember(allWardrobeItems, activeItems, filterCategory) {
            val activeIds = activeItems.map { it.id }.toSet()
            allWardrobeItems.filter { item ->
                !activeIds.contains(item.id) &&
                    (filterCategory == null || item.category == filterCategory)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
            containerColor = DressKitStyle.cardSurface(),
            contentColor = primaryInk
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.addPiece,
                        style = MaterialTheme.typography.titleLarge,
                        color = primaryInk
                    )
                    IconButton(onClick = { showAddSheet = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = strings.close, tint = primaryInk)
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChipPill(
                            label = strings.all,
                            selected = filterCategory == null,
                            onClick = { filterCategory = null }
                        )
                    }
                    items(ClothingCategory.entries) { cat ->
                        FilterChipPill(
                            label = cat.display(lang),
                            selected = filterCategory == cat,
                            onClick = { filterCategory = if (filterCategory == cat) null else cat }
                        )
                    }
                }

                if (addable.isEmpty()) {
                    Text(
                        text = strings.noFilterMatch,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(addable, key = { it.id }) { candidate ->
                            val simulated = remember(candidate, activeItems, lang) {
                                OutfitRecommendationEngine.calculateColorHarmony(activeItems + candidate, lang).first
                            }
                            CandidatePickRow(
                                item = candidate,
                                predictedScore = simulated,
                                onSelect = {
                                    activeItems = OutfitRecommendationEngine.sortTopToBottom(activeItems + candidate)
                                    showAddSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidatePickRow(
    item: ClothingItem,
    predictedScore: Int,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val photoFile = remember(item.coverPhoto) {
        item.coverPhoto?.let { ImageStorage.getPhotoFile(context, it) }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        color = DressKitStyle.secondarySurface(),
        border = BorderStroke(1.dp, DressKitStyle.borderColor())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (item.useOriginalPhotos) DressKitStyle.photoBoxSurface() else Color.White)
            ) {
                if (photoFile != null && photoFile.exists()) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = item.name,
                        contentScale = if (item.useOriginalPhotos) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (item.useOriginalPhotos) 0.dp else 4.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank { item.subCategory.display(lang) },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = primaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = item.subCategory.display(lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryInk
                    )
                    if (item.isWishlist) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = DressKitStyle.accentSoft()
                        ) {
                            Text(
                                text = if (lang == AppLanguage.EN) "Wishlist" else "İstek",
                                style = MaterialTheme.typography.labelSmall,
                                color = DressKitStyle.accentColor(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            HarmonyScoreBadge(score = predictedScore)
        }
    }
}
