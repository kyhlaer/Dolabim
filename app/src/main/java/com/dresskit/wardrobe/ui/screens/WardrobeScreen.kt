package com.dresskit.wardrobe.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.dresskit.wardrobe.R
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.Season
import com.dresskit.wardrobe.model.StyleTag
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.components.FilterChipPill
import com.dresskit.wardrobe.ui.components.GarmentCard
import com.dresskit.wardrobe.ui.components.WinterTitleWithSantaHat
import com.dresskit.wardrobe.ui.components.bouncyOverscroll
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WardrobeScreen(
    items: List<ClothingItem>,
    onItemClick: (ClothingItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onShareItem: (ClothingItem) -> Unit = {},
    onReorderItem: (String, String) -> Unit = { _, _ -> },
    onAddFromCamera: () -> Unit,
    onAddFromGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckAllWishlistPrices: () -> Unit = {},
    isCheckingPrices: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val isEn = lang != AppLanguage.TR

    var selectedCategory by rememberSaveable { mutableStateOf<ClothingCategory?>(null) }
    var selectedSubCategory by rememberSaveable { mutableStateOf<ClothingSubCategory?>(null) }
    var selectedColor by rememberSaveable { mutableStateOf<GarmentColor?>(null) }
    var selectedSeason by rememberSaveable { mutableStateOf<Season?>(null) }
    var selectedStyle by rememberSaveable { mutableStateOf<StyleTag?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var wishlistOnly by rememberSaveable { mutableStateOf(false) }

    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }

    val activeFilterCount = remember(selectedColor, selectedSeason, selectedStyle, selectedSubCategory) {
        listOfNotNull(selectedColor, selectedSeason, selectedStyle, selectedSubCategory).size
    }

    val wishlistCount = remember(items) { items.count { it.isWishlist } }

    val availableSubCategories = remember(items, selectedCategory, wishlistOnly) {
        val base = items.filter { if (wishlistOnly) it.isWishlist else !it.isWishlist }
        val inPool = base
            .filter { selectedCategory == null || it.category == selectedCategory }
            .map { it.subCategory }
            .distinct()
        if (inPool.isNotEmpty()) {
            inPool
        } else {
            val pool = if (selectedCategory != null) {
                ClothingSubCategory.forCategory(selectedCategory!!)
            } else {
                listOf(
                    ClothingSubCategory.TSHIRT,
                    ClothingSubCategory.SHIRT,
                    ClothingSubCategory.HOODIE,
                    ClothingSubCategory.SWEATER,
                    ClothingSubCategory.CARDIGAN,
                    ClothingSubCategory.JEANS,
                    ClothingSubCategory.PANTS,
                    ClothingSubCategory.SWEATPANTS,
                    ClothingSubCategory.JACKET,
                    ClothingSubCategory.COAT,
                    ClothingSubCategory.SNEAKER
                )
            }
            pool
        }
    }

    val filteredItems = remember(
        items,
        selectedCategory,
        selectedSubCategory,
        selectedColor,
        selectedSeason,
        selectedStyle,
        favoritesOnly,
        wishlistOnly,
        searchQuery
    ) {
        val q = searchQuery.trim().lowercase()
        items.filter { item ->
            val matchBucket = if (wishlistOnly) item.isWishlist else !item.isWishlist
            val matchCat = selectedCategory == null || item.category == selectedCategory
            val matchSub = selectedSubCategory == null || item.subCategory == selectedSubCategory
            val matchFav = !favoritesOnly || item.isFavorite
            val matchColor = selectedColor == null || item.colors.contains(selectedColor)
            val matchSeason = selectedSeason == null || item.seasons.contains(selectedSeason)
            val matchStyle = selectedStyle == null || item.styleTag == selectedStyle
            val matchQuery = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.subCategory.display(lang).lowercase().contains(q) ||
                item.category.display(lang).lowercase().contains(q) ||
                item.colors.any { it.display(lang).lowercase().contains(q) } ||
                item.styleTag.display(lang).lowercase().contains(q)
            matchBucket && matchCat && matchSub && matchFav && matchColor && matchSeason && matchStyle && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DressKitStyle.canvasBg())
    ) {
        // Top header bar (pure title on left, action icons on right — zero phantom oval pills)
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
                WinterTitleWithSantaHat(
                    title = strings.appTitle,
                    style = MaterialTheme.typography.displaySmall,
                    color = primaryInk
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = strings.searchPlaceholder,
                            tint = primaryInk
                        )
                    }

                    Box {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Filter",
                                tint = primaryInk
                            )
                        }
                        if (activeFilterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(DressKitStyle.accentColor()),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$activeFilterCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DressKitStyle.buttonFg()
                                )
                            }
                        }
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = strings.settingsTitle,
                            tint = primaryInk
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(40.dp)
                            .clickable(onClick = onAddFromCamera),
                        shape = CircleShape,
                        color = DressKitStyle.buttonBg()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = strings.takePhotoAdd,
                                tint = DressKitStyle.buttonFg(),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showSearch) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = strings.searchPlaceholder,
                                color = secondaryInk
                            )
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = secondaryInk
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = strings.close,
                                        tint = secondaryInk
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DressKitStyle.cardSurface(),
                            unfocusedContainerColor = DressKitStyle.cardSurface(),
                            focusedBorderColor = DressKitStyle.accentColor(),
                            unfocusedBorderColor = DressKitStyle.borderColor(),
                            focusedTextColor = primaryInk,
                            unfocusedTextColor = primaryInk
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary chips row: All | Favorites | Wishlist | Categories
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 12.dp)
            ) {
                item {
                    FilterChipPill(
                        label = strings.all,
                        selected = selectedCategory == null && !favoritesOnly && !wishlistOnly,
                        onClick = {
                            selectedCategory = null
                            selectedSubCategory = null
                            favoritesOnly = false
                            wishlistOnly = false
                        }
                    )
                }
                item {
                    FilterChipPill(
                        label = strings.favorites,
                        selected = favoritesOnly,
                        onClick = {
                            val next = !favoritesOnly
                            favoritesOnly = next
                            if (next) wishlistOnly = false
                        }
                    )
                }
                item {
                    val wishLabel = if (wishlistCount > 0) {
                        "${strings.wishlist} ($wishlistCount)"
                    } else {
                        strings.wishlist
                    }
                    FilterChipPill(
                        label = wishLabel,
                        selected = wishlistOnly,
                        leadingDotColor = if (wishlistOnly) DressKitStyle.accentColor() else null,
                        onClick = {
                            val next = !wishlistOnly
                            wishlistOnly = next
                            if (next) favoritesOnly = false
                        }
                    )
                }
                items(ClothingCategory.entries) { cat ->
                    FilterChipPill(
                        label = cat.display(lang),
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = if (selectedCategory == cat) null else cat
                            selectedSubCategory = null
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subcategory quick row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(end = 12.dp)
            ) {
                items(availableSubCategories) { sub ->
                    FilterChipPill(
                        label = sub.display(lang),
                        selected = selectedSubCategory == sub,
                        onClick = {
                            selectedSubCategory = if (selectedSubCategory == sub) null else sub
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val trackedWishlistCount = remember(items) {
                items.count { it.isWishlist && it.productUrl.trim().startsWith("https://", ignoreCase = true) }
            }
            if (wishlistOnly && trackedWishlistCount > 0) {
                OutlinedButton(
                    onClick = onCheckAllWishlistPrices,
                    enabled = !isCheckingPrices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    border = BorderStroke(1.dp, DressKitStyle.accentColor()),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    if (isCheckingPrices) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = DressKitStyle.accentColor()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = DressKitStyle.accentColor(),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEn) {
                            "Check Price Drops Now ($trackedWishlistCount tracked)"
                        } else {
                            "Fiyat Düşüşlerini Şimdi Kontrol Et ($trackedWishlistCount link)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = DressKitStyle.accentColor()
                    )
                }
            }
        }

        // Clipped scrollable content region so overscroll never bleeds over header/chips
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            when {
                items.isEmpty() -> {
                    EmptyWardrobeBox(
                        title = strings.emptyWardrobeTitle,
                        body = strings.emptyWardrobeBody,
                        takePhotoLabel = strings.takePhotoAdd,
                        galleryLabel = strings.pickFromGallery,
                        onCamera = onAddFromCamera,
                        onGallery = onAddFromGallery
                    )
                }

                filteredItems.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                            color = DressKitStyle.cardSurface(),
                            border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (wishlistOnly) {
                                        Icons.Outlined.BookmarkBorder
                                    } else {
                                        Icons.Outlined.Tune
                                    },
                                    contentDescription = null,
                                    tint = DressKitStyle.accentColor(),
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = if (wishlistOnly) {
                                        if (isEn) "Your wishlist is empty" else "İstek listen henüz boş"
                                    } else {
                                        strings.noFilterMatch
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = primaryInk,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (wishlistOnly) {
                                        if (isEn) {
                                            "Add clothes you want to buy with price tags by selecting 'Wishlist' when adding an item."
                                        } else {
                                            "Almak istediğin parçaları fiyat etiketiyle kaydetmek için eklerken 'İstek Listesi' seçeneğini işaretle."
                                        }
                                    } else {
                                        if (isEn) "Try resetting active filters." else "Seçili filtreleri temizleyerek tüm parçaları görebilirsin."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryInk,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedButton(
                                    onClick = {
                                        selectedCategory = null
                                        selectedSubCategory = null
                                        selectedColor = null
                                        selectedSeason = null
                                        selectedStyle = null
                                        favoritesOnly = false
                                        wishlistOnly = false
                                        searchQuery = ""
                                    },
                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                ) {
                                    Text(strings.clearFilters, color = primaryInk)
                                }
                            }
                        }
                    }
                }

                else -> {
                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    var draggingItemId by remember { mutableStateOf<String?>(null) }
                    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    var pointerPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    val latestItems by rememberUpdatedState(filteredItems)

                    fun trySwapDraggedItem(activeId: String, scrollDir: Float = 0f) {
                        val visible = gridState.layoutInfo.visibleItemsInfo
                        if (visible.isEmpty()) return
                        val activeInfo = visible.firstOrNull { it.key == activeId }
                        if (activeInfo != null) {
                            val centerX = activeInfo.offset.x + activeInfo.size.width / 2f + dragOffset.x
                            val centerY = activeInfo.offset.y + activeInfo.size.height / 2f + dragOffset.y
                            val hovered = visible.firstOrNull { target ->
                                target.key != activeId &&
                                    centerX >= target.offset.x &&
                                    centerX <= target.offset.x + target.size.width &&
                                    centerY >= target.offset.y &&
                                    centerY <= target.offset.y + target.size.height
                            }
                            val hoveredId = hovered?.key as? String
                            if (hoveredId != null && latestItems.any { it.id == hoveredId }) {
                                val deltaX = activeInfo.offset.x - hovered.offset.x
                                val deltaY = activeInfo.offset.y - hovered.offset.y
                                dragOffset = androidx.compose.ui.geometry.Offset(
                                    x = dragOffset.x + deltaX,
                                    y = dragOffset.y + deltaY
                                )
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onReorderItem(activeId, hoveredId)
                            }
                        } else if (scrollDir != 0f) {
                            val fallback = if (scrollDir < 0f) {
                                visible.firstOrNull { it.key != activeId }
                            } else {
                                visible.lastOrNull { it.key != activeId }
                            }
                            val fallbackId = fallback?.key as? String
                            if (fallbackId != null && latestItems.any { it.id == fallbackId }) {
                                dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onReorderItem(activeId, fallbackId)
                            }
                        }
                    }

                    androidx.compose.runtime.LaunchedEffect(draggingItemId) {
                        val activeId = draggingItemId ?: return@LaunchedEffect
                        var prevFrameNanos = androidx.compose.runtime.withFrameNanos { it }
                        while (draggingItemId == activeId) {
                            val frameNanos = androidx.compose.runtime.withFrameNanos { it }
                            val dt = ((frameNanos - prevFrameNanos).coerceIn(1_000_000L, 48_000_000L) / 16_666_667f)
                            prevFrameNanos = frameNanos
                            val viewportH = gridState.layoutInfo.viewportSize.height.toFloat()
                            if (viewportH <= 0f) continue
                            val topEdgePx = with(density) { 104.dp.toPx() }.coerceAtMost(viewportH * 0.26f)
                            val bottomEdgePx = with(density) { 156.dp.toPx() }.coerceAtMost(viewportH * 0.34f)
                            val minSpeedPx = with(density) { 2.8.dp.toPx() }
                            val maxExtraSpeedPx = with(density) { 11.5.dp.toPx() }
                            val pointerY = pointerPosition.y
                            val step = when {
                                pointerY < topEdgePx && topEdgePx > 1f -> {
                                    val t = ((topEdgePx - pointerY) / topEdgePx).coerceIn(0f, 1f)
                                    -(minSpeedPx + t * t * maxExtraSpeedPx) * dt
                                }
                                pointerY > viewportH - bottomEdgePx && bottomEdgePx > 1f -> {
                                    val t = ((pointerY - (viewportH - bottomEdgePx)) / bottomEdgePx).coerceIn(0f, 1f)
                                    (minSpeedPx + t * t * maxExtraSpeedPx) * dt
                                }
                                else -> 0f
                            }
                            if (step != 0f) {
                                val consumed = gridState.scrollBy(step)
                                if (consumed != 0f) {
                                    dragOffset = androidx.compose.ui.geometry.Offset(
                                        x = dragOffset.x,
                                        y = dragOffset.y + consumed
                                    )
                                    trySwapDraggedItem(activeId, step)
                                }
                            }
                        }
                    }

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .bouncyOverscroll()
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startOffset ->
                                        val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                            val left = info.offset.x.toFloat()
                                            val top = info.offset.y.toFloat()
                                            startOffset.x in left..(left + info.size.width) &&
                                                startOffset.y in top..(top + info.size.height)
                                        }
                                        val id = hit?.key as? String
                                        if (id != null) {
                                            draggingItemId = id
                                            dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                            pointerPosition = startOffset
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        }
                                    },
                                    onDragCancel = {
                                        draggingItemId = null
                                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                        pointerPosition = androidx.compose.ui.geometry.Offset.Zero
                                    },
                                    onDragEnd = {
                                        draggingItemId = null
                                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                        pointerPosition = androidx.compose.ui.geometry.Offset.Zero
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val activeId = draggingItemId ?: return@detectDragGesturesAfterLongPress
                                        pointerPosition = change.position
                                        dragOffset += dragAmount
                                        trySwapDraggedItem(activeId, 0f)
                                    }
                                )
                            },
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 4.dp,
                            bottom = 114.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            val isDragging = draggingItemId == item.id
                            val cardScale by animateFloatAsState(
                                targetValue = if (isDragging) 1.04f else 1f,
                                label = "dragCardScale"
                            )
                            GarmentCard(
                                item = item,
                                onClick = {
                                    if (draggingItemId == null) onItemClick(item)
                                },
                                onToggleFavorite = { onToggleFavorite(item.id) },
                                onShare = {
                                    if (draggingItemId == null) onShareItem(item)
                                },
                                modifier = Modifier
                                    .then(if (isDragging) Modifier else Modifier.animateItem())
                                    .zIndex(if (isDragging) 10f else 0f)
                                    .graphicsLayer {
                                        if (isDragging) {
                                            translationX = dragOffset.x
                                            translationY = dragOffset.y
                                            shadowElevation = 24f
                                        }
                                        scaleX = cardScale
                                        scaleY = cardScale
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        val presentColorSet = remember(items) {
            items.flatMap { it.colors }.toSet()
        }
        val ownedColors = remember(presentColorSet) {
            GarmentColor.entries.filter { it in presentColorSet }
        }
        val otherColors = remember(presentColorSet) {
            GarmentColor.entries.filter { it !in presentColorSet }
        }
        val coreFilterColors = remember {
            setOf(
                GarmentColor.BLACK,
                GarmentColor.WHITE,
                GarmentColor.GRAY,
                GarmentColor.NAVY,
                GarmentColor.BEIGE,
                GarmentColor.BROWN
            )
        }
        val primaryColors = remember(ownedColors) {
            if (ownedColors.isNotEmpty()) ownedColors else GarmentColor.entries.filter { it in coreFilterColors }
        }
        val extraColors = remember(ownedColors, otherColors) {
            if (ownedColors.isNotEmpty()) otherColors else GarmentColor.entries.filter { it !in coreFilterColors }
        }
        var expandAllColors by remember(showFilterSheet) {
            mutableStateOf(selectedColor != null && selectedColor in extraColors)
        }
        val displayedColors = if (expandAllColors || extraColors.isEmpty()) {
            primaryColors + extraColors
        } else {
            primaryColors
        }
        Dialog(
            onDismissRequest = { showFilterSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .heightIn(max = 640.dp),
                shape = RoundedCornerShape(22.dp),
                color = DressKitStyle.cardSurface(),
                border = BorderStroke(1.dp, DressKitStyle.borderColor())
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEn) "Filter Wardrobe" else "Detaylı Filtrele",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = primaryInk
                        )
                        IconButton(onClick = { showFilterSheet = false }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = strings.close,
                                tint = primaryInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = DressKitStyle.borderColor())

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEn) "Colors (${displayedColors.size})" else "Renkler (${displayedColors.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = primaryInk
                                )
                                if (extraColors.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable { expandAllColors = !expandAllColors },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (expandAllColors) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                                        border = BorderStroke(
                                            1.dp,
                                            if (expandAllColors) DressKitStyle.accentColor() else DressKitStyle.borderColor()
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = if (expandAllColors) {
                                                    strings.collapse
                                                } else {
                                                    localizedText(
                                                        lang,
                                                        "Diğer Renkler (+${extraColors.size})",
                                                        "Other Colors (+${extraColors.size})",
                                                        "Weitere Farben (+${extraColors.size})",
                                                        "Autres couleurs (+${extraColors.size})",
                                                        "Otros colores (+${extraColors.size})",
                                                        "其他颜色 (+${extraColors.size})"
                                                    )
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (expandAllColors) DressKitStyle.accentColor() else primaryInk
                                            )
                                            Icon(
                                                imageVector = if (expandAllColors) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                                contentDescription = null,
                                                tint = if (expandAllColors) DressKitStyle.accentColor() else primaryInk,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                displayedColors.forEach { color ->
                                    FilterChipPill(
                                        label = color.display(lang),
                                        selected = selectedColor == color,
                                        leadingDotColor = color.composeColor,
                                        onClick = {
                                            selectedColor = if (selectedColor == color) null else color
                                        }
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isEn) "Style" else "Stil",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = primaryInk
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                StyleTag.entries.forEach { style ->
                                    FilterChipPill(
                                        label = style.display(lang),
                                        selected = selectedStyle == style,
                                        onClick = {
                                            selectedStyle = if (selectedStyle == style) null else style
                                        }
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isEn) "Season" else "Mevsim",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = primaryInk
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Season.entries.forEach { season ->
                                    FilterChipPill(
                                        label = season.display(lang),
                                        selected = selectedSeason == season,
                                        onClick = {
                                            selectedSeason = if (selectedSeason == season) null else season
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = DressKitStyle.borderColor())

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedColor = null
                                selectedSeason = null
                                selectedStyle = null
                                selectedSubCategory = null
                                showFilterSheet = false
                            },
                            modifier = Modifier.weight(1.4f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor())
                        ) {
                            Text(
                                text = strings.clearFilters,
                                color = primaryInk,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp
                            )
                        }
                        Button(
                            onClick = { showFilterSheet = false },
                            modifier = Modifier.weight(0.85f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DressKitStyle.buttonBg(),
                                contentColor = DressKitStyle.buttonFg()
                            )
                        ) {
                            Text(
                                text = strings.ok,
                                maxLines = 1,
                                softWrap = false,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWardrobeBox(
    title: String,
    body: String,
    takePhotoLabel: String,
    galleryLabel: String,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DressKitStyle.cardCorner()),
            color = DressKitStyle.cardSurface(),
            border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = DressKitStyle.accentSoft(),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tshirt_add),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = primaryInk,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryInk,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onCamera,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DressKitStyle.buttonBg(),
                        contentColor = DressKitStyle.buttonFg()
                    )
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(takePhotoLabel)
                }
                OutlinedButton(
                    onClick = onGallery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor())
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = primaryInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(galleryLabel, color = primaryInk)
                }
            }
        }
    }
}
