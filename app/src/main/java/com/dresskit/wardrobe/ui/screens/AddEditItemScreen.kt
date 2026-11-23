package com.dresskit.wardrobe.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import coil.compose.AsyncImage
import com.dresskit.wardrobe.data.AiStudioService
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.PriceTrackerService
import com.dresskit.wardrobe.data.SavedPhotoPair
import com.dresskit.wardrobe.data.StudioCutoutProcessor
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.Season
import com.dresskit.wardrobe.model.StyleTag
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.components.ColorDot
import com.dresskit.wardrobe.ui.components.ColorSwatchPicker
import com.dresskit.wardrobe.ui.components.FilterChipPill
import com.dresskit.wardrobe.ui.theme.DressKitColors
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditItemScreen(
    existingItem: ClothingItem?,
    pendingPhotoPairs: List<SavedPhotoPair>,
    batchLabel: String? = null,
    hasMoreInBatch: Boolean = false,
    onClearPendingPhotos: () -> Unit,
    onRequestCamera: () -> Unit,
    onRequestGallery: () -> Unit,
    onSave: (
        name: String,
        category: ClothingCategory,
        subCategory: ClothingSubCategory,
        styleTag: StyleTag,
        seasons: List<Season>,
        colors: List<GarmentColor>,
        extractedHexes: List<Long>,
        studioPhotos: List<String>,
        originalPhotos: List<String>,
        useOriginalPhotos: Boolean,
        catalogPhotos: List<String>,
        useCatalogPhotos: Boolean,
        notes: String,
        isWishlist: Boolean,
        priceText: String,
        productUrl: String,
        lastCheckedPrice: Double,
        addAnother: Boolean
    ) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val isEn = lang == AppLanguage.EN
    val isEditing = existingItem != null
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    val studioPhotos = remember(existingItem?.id) {
        mutableStateListOf<String>().apply {
            existingItem?.photoFileNames?.let { addAll(it) }
        }
    }
    val originalPhotos = remember(existingItem?.id) {
        mutableStateListOf<String>().apply {
            if (existingItem != null) {
                if (existingItem.originalPhotoFileNames.size == existingItem.photoFileNames.size &&
                    existingItem.originalPhotoFileNames.isNotEmpty()
                ) {
                    addAll(existingItem.originalPhotoFileNames)
                } else {
                    addAll(existingItem.photoFileNames)
                }
            }
        }
    }
    val catalogPhotos = remember(existingItem?.id) {
        mutableStateListOf<String>().apply {
            if (existingItem != null) {
                val targetSize = existingItem.photoFileNames.size
                for (i in 0 until targetSize) {
                    add(existingItem.catalogPhotoFileNames.getOrNull(i) ?: "")
                }
            }
        }
    }

    var useOriginalPhotos by remember(existingItem?.id) {
        mutableStateOf(existingItem?.useOriginalPhotos ?: false)
    }
    var useCatalogPhotos by remember(existingItem?.id) {
        mutableStateOf(existingItem?.useCatalogPhotos ?: false)
    }
    var isWishlist by remember(existingItem?.id) {
        mutableStateOf(existingItem?.isWishlist ?: false)
    }
    var priceText by remember(existingItem?.id) {
        mutableStateOf(existingItem?.priceText ?: "")
    }
    var productUrl by remember(existingItem?.id) {
        mutableStateOf(existingItem?.productUrl ?: "")
    }
    var lastCheckedPrice by remember(existingItem?.id) {
        mutableDoubleStateOf(existingItem?.lastCheckedPrice ?: 0.0)
    }
    var isFetchingPrice by remember { mutableStateOf(false) }
    var priceFetchNote by remember { mutableStateOf<String?>(null) }
    var isTransformingFlatLay by remember { mutableStateOf(false) }
    var isRemovingBg by remember { mutableStateOf(false) }
    var studioFlatLayNote by remember { mutableStateOf<String?>(null) }
    var name by remember(existingItem?.id) { mutableStateOf(existingItem?.name ?: "") }
    var userEditedName by remember(existingItem?.id) { mutableStateOf(isEditing) }
    var autoPredictedBadge by remember(existingItem?.id) { mutableStateOf(false) }

    var category by remember(existingItem?.id) {
        mutableStateOf(existingItem?.category ?: ClothingCategory.TOP)
    }
    var subCategory by remember(existingItem?.id) {
        mutableStateOf(existingItem?.subCategory ?: ClothingSubCategory.TSHIRT)
    }
    var styleTag by remember(existingItem?.id) {
        mutableStateOf(existingItem?.styleTag ?: StyleTag.CASUAL)
    }
    val seasons = remember(existingItem?.id) {
        mutableStateListOf<Season>().apply {
            if (existingItem != null && existingItem.seasons.isNotEmpty()) {
                addAll(existingItem.seasons)
            } else {
                add(Season.ALL)
            }
        }
    }
    val colors = remember(existingItem?.id) {
        mutableStateListOf<GarmentColor>().apply {
            existingItem?.colors?.let { addAll(it) }
        }
    }
    var extractedHexes by remember(existingItem?.id) {
        mutableStateOf(existingItem?.extractedHexes ?: emptyList())
    }
    var notes by remember(existingItem?.id) { mutableStateOf(existingItem?.notes ?: "") }
    var analyzedCoverFile by remember(existingItem?.id) {
        mutableStateOf(existingItem?.photoFileNames?.firstOrNull())
    }

    // Append newly captured/picked photo pairs into the current item
    LaunchedEffect(pendingPhotoPairs) {
        if (pendingPhotoPairs.isNotEmpty()) {
            pendingPhotoPairs.forEach { pair ->
                if (!studioPhotos.contains(pair.studioFileName)) {
                    studioPhotos.add(pair.studioFileName)
                    originalPhotos.add(pair.originalFileName)
                    catalogPhotos.add("")
                }
            }
            onClearPendingPhotos()
        }
    }

    fun localizedAutoName(color: GarmentColor?, sub: ClothingSubCategory): String {
        return if (isEn) {
            listOfNotNull(color?.display(lang), sub.display(lang)).joinToString(" ")
        } else {
            ImageStorage.buildAutoName(color, sub)
        }
    }

    // Automatic garment color, sub-category, and name prediction from cover studio photo
    val coverStudioFile = studioPhotos.firstOrNull()
    LaunchedEffect(coverStudioFile) {
        if (coverStudioFile != null && coverStudioFile != analyzedCoverFile) {
            analyzedCoverFile = coverStudioFile
            val analysis = withContext(Dispatchers.Default) {
                ImageStorage.analyzeGarment(context, coverStudioFile)
            }
            extractedHexes = analysis.extractedHexes
            if (!isEditing) {
                if (colors.isEmpty() && analysis.detectedColors.isNotEmpty()) {
                    colors.addAll(analysis.detectedColors)
                }
                subCategory = analysis.predictedSubCategory
                category = analysis.predictedSubCategory.parentCategory
                if (!userEditedName) {
                    name = localizedAutoName(colors.firstOrNull(), subCategory)
                    autoPredictedBadge = true
                }
            }
        }
    }

    fun syncAutoNameIfNotManual() {
        if (!userEditedName) {
            name = localizedAutoName(colors.firstOrNull(), subCategory)
        }
    }

    fun resetFormForNextItem() {
        studioPhotos.clear()
        originalPhotos.clear()
        catalogPhotos.clear()
        useOriginalPhotos = false
        useCatalogPhotos = false
        isWishlist = false
        priceText = ""
        productUrl = ""
        lastCheckedPrice = 0.0
        priceFetchNote = null
        name = ""
        userEditedName = false
        autoPredictedBadge = false
        analyzedCoverFile = null
        category = ClothingCategory.TOP
        subCategory = ClothingSubCategory.TSHIRT
        styleTag = StyleTag.CASUAL
        seasons.clear()
        seasons.add(Season.ALL)
        colors.clear()
        extractedHexes = emptyList()
        notes = ""
    }

    val displayedPhotos = when {
        useOriginalPhotos -> originalPhotos
        useCatalogPhotos && catalogPhotos.isNotEmpty() -> studioPhotos.mapIndexed { idx, cutout ->
            catalogPhotos.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: cutout
        }
        else -> studioPhotos
    }

    Scaffold(
        containerColor = DressKitStyle.canvasBg(),
        topBar = {
            Surface(color = DressKitStyle.canvasBg()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    isEditing -> if (isEn) "Edit Piece" else "Kıyafeti Düzenle"
                                    batchLabel != null -> if (isEn) "Add Piece ($batchLabel)" else "Yeni Kıyafet ($batchLabel)"
                                    else -> if (isEn) "Add New Piece" else "Yeni Kıyafet Ekle"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                color = primaryInk
                            )
                            if (batchLabel != null) {
                                Text(
                                    text = if (isEn) {
                                        "Each selected photo is saved as its own item"
                                    } else {
                                        "Seçilen her fotoğraf ayrı bir kıyafet olarak düzenleniyor"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryInk
                                )
                            }
                        }
                        IconButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = strings.close,
                                tint = primaryInk
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isEditing && !hasMoreInBatch) {
                        OutlinedButton(
                            onClick = {
                                onSave(
                                    name.trim(),
                                    category,
                                    subCategory,
                                    styleTag,
                                    seasons.toList().ifEmpty { listOf(Season.ALL) },
                                    colors.toList(),
                                    extractedHexes,
                                    studioPhotos.toList(),
                                    originalPhotos.toList(),
                                    useOriginalPhotos,
                                    catalogPhotos.toList(),
                                    useCatalogPhotos,
                                    notes.trim(),
                                    isWishlist,
                                    priceText.trim(),
                                    PriceTrackerService.normalizeProductUrl(productUrl),
                                    lastCheckedPrice.takeIf { it > 0.0 }
                                        ?: PriceTrackerService.parsePrice(priceText)
                                        ?: 0.0,
                                    true
                                )
                                resetFormForNextItem()
                            },
                            enabled = studioPhotos.isNotEmpty(),
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = primaryInk
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isEn) "Save & New" else "Kaydet & Yeni")
                        }
                    }

                    Button(
                        onClick = {
                            onSave(
                                name.trim(),
                                category,
                                subCategory,
                                styleTag,
                                seasons.toList().ifEmpty { listOf(Season.ALL) },
                                colors.toList(),
                                extractedHexes,
                                studioPhotos.toList(),
                                originalPhotos.toList(),
                                useOriginalPhotos,
                                catalogPhotos.toList(),
                                useCatalogPhotos,
                                notes.trim(),
                                isWishlist,
                                priceText.trim(),
                                PriceTrackerService.normalizeProductUrl(productUrl),
                                lastCheckedPrice.takeIf { it > 0.0 }
                                    ?: PriceTrackerService.parsePrice(priceText)
                                    ?: 0.0,
                                false
                            )
                        },
                        enabled = studioPhotos.isNotEmpty(),
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DressKitStyle.buttonBg(),
                            contentColor = DressKitStyle.buttonFg()
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        val primaryBtnLabel = when {
                            isEditing -> if (isEn) "Update" else "Güncelle"
                            hasMoreInBatch -> if (isEn) "Save & Next" else "Kaydet & Sıradaki"
                            else -> strings.save
                        }
                        Text(primaryBtnLabel)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Destination Switch: My Closet vs Wishlist + Price
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isEn) "Where should this piece live?" else "Bu parça nereye eklensin?",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryInk
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeSelectButton(
                        label = strings.addToWardrobeLabel,
                        icon = Icons.Outlined.Checkroom,
                        selected = !isWishlist,
                        onClick = { isWishlist = false },
                        modifier = Modifier.weight(1f)
                    )
                    ModeSelectButton(
                        label = strings.addToWishlistLabel,
                        icon = Icons.Outlined.BookmarkBorder,
                        selected = isWishlist,
                        onClick = { isWishlist = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                AnimatedVisibility(visible = isWishlist) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = {
                                priceText = it
                                PriceTrackerService.parsePrice(it)?.let { parsed ->
                                    lastCheckedPrice = parsed
                                }
                            },
                            label = { Text(strings.priceLabel) },
                            placeholder = { Text(strings.pricePlaceholder) },
                            singleLine = true,
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

                        OutlinedTextField(
                            value = productUrl,
                            onValueChange = { raw ->
                                productUrl = if (raw.contains("https://", ignoreCase = true) && raw.trim().contains(' ')) {
                                    PriceTrackerService.normalizeProductUrl(raw)
                                } else {
                                    raw
                                }
                                priceFetchNote = null
                            },
                            label = {
                                Text(
                                    if (isEn) {
                                        "Product Link (https:// for price-drop alerts)"
                                    } else {
                                        "Ürün Linki (Fiyat düşünce bildirim için)"
                                    }
                                )
                            },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = null,
                                    tint = secondaryInk
                                )
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

                        if (productUrl.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isFetchingPrice = true
                                            priceFetchNote = null
                                            val cleanUrl = PriceTrackerService.normalizeProductUrl(productUrl)
                                            if (cleanUrl.isNotEmpty() && cleanUrl != productUrl) {
                                                productUrl = cleanUrl
                                            }
                                            val result = PriceTrackerService.fetchProductPrice(cleanUrl)
                                            isFetchingPrice = false
                                            if (result != null) {
                                                lastCheckedPrice = result.first
                                                priceText = result.second
                                                priceFetchNote = if (isEn) {
                                                    "Live price fetched: ${result.second}"
                                                } else {
                                                    "Güncel fiyat çekildi: ${result.second}"
                                                }
                                            } else {
                                                priceFetchNote = if (isEn) {
                                                    "Could not auto-read price (enter price manually; link saved)."
                                                } else {
                                                    "Site koruması nedeniyle fiyat otomatik okunamadı; fiyatı elle girebilirsin."
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isFetchingPrice && PriceTrackerService.isSafeHttpsUrl(productUrl),
                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                    border = BorderStroke(1.dp, DressKitStyle.accentColor()),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = DressKitStyle.accentColor()
                                    )
                                ) {
                                    if (isFetchingPrice) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp,
                                            color = DressKitStyle.accentColor()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.NotificationsActive,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isEn) "Fetch Price" else "Linkten Fiyat Çek")
                                }
                            }
                        }

                        if (priceFetchNote != null) {
                            Text(
                                text = priceFetchNote!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = DressKitStyle.accentColor()
                            )
                        }
                    }
                }
            }

            // 1. Photos Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) "Photos (${displayedPhotos.size})" else "Fotoğraflar (${displayedPhotos.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryInk
                    )
                    if (displayedPhotos.isNotEmpty()) {
                        Text(
                            text = if (isEn) "Tap camera/gallery to add angles" else "Ek açı fotoğrafları ekleyebilirsin",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryInk
                        )
                    }
                }

                if (displayedPhotos.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        itemsIndexed(displayedPhotos) { index, fileName ->
                            val file = remember(fileName) { ImageStorage.getPhotoFile(context, fileName) }
                            Box(
                                modifier = Modifier
                                    .width(138.dp)
                                    .height(138.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (useOriginalPhotos) DressKitStyle.photoBoxSurface() else Color.White)
                                    .border(
                                        width = if (index == 0) 2.dp else 1.dp,
                                        color = if (index == 0) DressKitStyle.accentColor() else DressKitStyle.borderColor(),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Photo ${index + 1}",
                                    contentScale = if (useOriginalPhotos) ContentScale.Crop else ContentScale.Fit,
                                    modifier = if (useOriginalPhotos) {
                                        Modifier.fillMaxSize()
                                    } else {
                                        Modifier
                                            .fillMaxSize()
                                            .padding(6.dp)
                                    }
                                )

                                // Cover selector badge
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                        .clickable {
                                            if (index > 0) {
                                                val movedStudio = studioPhotos.removeAt(index)
                                                studioPhotos.add(0, movedStudio)
                                                if (index in originalPhotos.indices) {
                                                    val movedOrig = originalPhotos.removeAt(index)
                                                    originalPhotos.add(0, movedOrig)
                                                }
                                                if (index in catalogPhotos.indices) {
                                                    val movedCat = catalogPhotos.removeAt(index)
                                                    catalogPhotos.add(0, movedCat)
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (index == 0) {
                                        DressKitStyle.buttonBg()
                                    } else {
                                        DressKitStyle.cardSurface().copy(alpha = 0.92f)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (index == 0) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                                            contentDescription = null,
                                            tint = if (index == 0) DressKitStyle.buttonFg() else primaryInk,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = if (index == 0) {
                                                if (isEn) "Cover" else "Kapak"
                                            } else {
                                                if (isEn) "Set Cover" else "Kapak yap"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (index == 0) DressKitStyle.buttonFg() else primaryInk
                                        )
                                    }
                                }

                                // Remove button
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clickable {
                                            studioPhotos.removeAt(index)
                                            if (index in originalPhotos.indices) {
                                                originalPhotos.removeAt(index)
                                            }
                                            if (index in catalogPhotos.indices) {
                                                catalogPhotos.removeAt(index)
                                            }
                                        },
                                    shape = CircleShape,
                                    color = DressKitColors.CharcoalInk.copy(alpha = 0.8f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Offline Remove BG / Original / Catalog Toggle
                    val hasCoverCatalog = catalogPhotos.firstOrNull()?.isNotBlank() == true
                    val isCatalogSelected = !useOriginalPhotos && useCatalogPhotos && hasCoverCatalog
                    val isRemoveBgSelected = !useOriginalPhotos && !isCatalogSelected

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeSelectButton(
                            label = if (isRemovingBg) {
                                localizedText(lang, "Temizleniyor...", "Removing BG...", "Wird entfernt...", "Suppression...", "Eliminando...", "去背景中...")
                            } else {
                                localizedText(lang, "Arka Planı Kaldır", "Remove BG", "Hintergrund", "Sans fond", "Sin fondo", "去背景")
                            },
                            icon = Icons.Outlined.AutoFixHigh,
                            selected = isRemoveBgSelected,
                            onClick = {
                                if (isRemovingBg || isTransformingFlatLay) return@ModeSelectButton
                                val needsCutoutRefresh = studioPhotos.indices.any { idx ->
                                    !ImageStorage.isOfflineCutoutFileName(studioPhotos[idx]) &&
                                        originalPhotos.getOrNull(idx) != null
                                }
                                if (!needsCutoutRefresh) {
                                    useOriginalPhotos = false
                                    useCatalogPhotos = false
                                } else {
                                    scope.launch {
                                        isRemovingBg = true
                                        withContext(Dispatchers.IO) {
                                            for (idx in studioPhotos.indices) {
                                                if (!ImageStorage.isOfflineCutoutFileName(studioPhotos[idx])) {
                                                    val origName = originalPhotos.getOrNull(idx) ?: continue
                                                    val origBmp = ImageStorage.decodePhotoBitmap(context, origName) ?: continue
                                                    try {
                                                        val cutoutBmp = StudioCutoutProcessor.toEcommerceFlatLayShot(context, origBmp)
                                                        try {
                                                            val newCutout = ImageStorage.saveNewCutoutVariant(context, cutoutBmp)
                                                            studioPhotos[idx] = newCutout
                                                        } finally {
                                                            if (cutoutBmp !== origBmp && !cutoutBmp.isRecycled) cutoutBmp.recycle()
                                                        }
                                                    } finally {
                                                        if (!origBmp.isRecycled) origBmp.recycle()
                                                    }
                                                }
                                            }
                                        }
                                        isRemovingBg = false
                                        useOriginalPhotos = false
                                        useCatalogPhotos = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ModeSelectButton(
                            label = localizedText(lang, "Orijinal", "Original", "Original", "Original", "Original", "原图"),
                            icon = Icons.Outlined.Photo,
                            selected = useOriginalPhotos,
                            onClick = {
                                useOriginalPhotos = true
                                useCatalogPhotos = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Generative Catalog Studio Flat-Lay Generator / Use Toggle (Cover Photo)
                    OutlinedButton(
                        onClick = {
                            if (isTransformingFlatLay || isRemovingBg) return@OutlinedButton
                            if (hasCoverCatalog) {
                                useOriginalPhotos = false
                                useCatalogPhotos = true
                                return@OutlinedButton
                            }
                            val srcFileName = originalPhotos.firstOrNull() ?: studioPhotos.firstOrNull()
                            if (srcFileName == null) return@OutlinedButton
                            scope.launch {
                                isTransformingFlatLay = true
                                studioFlatLayNote = null
                                val outcome = withContext(Dispatchers.IO) {
                                    val srcBmp = ImageStorage.decodePhotoBitmap(context, srcFileName)
                                        ?: return@withContext Result.failure<String>(
                                            IllegalStateException("Source image unreadable")
                                        )
                                    try {
                                        val aiRes = AiStudioService.generateEcommerceFlatLay(context, srcBmp)
                                        if (aiRes.isFailure) {
                                            return@withContext Result.failure<String>(
                                                aiRes.exceptionOrNull() ?: IllegalStateException("Catalog generation failed")
                                            )
                                        }
                                        val finalBmp = aiRes.getOrThrow()
                                        val newName = ImageStorage.saveNewCatalogVariant(context, finalBmp)
                                        if (finalBmp !== srcBmp && !finalBmp.isRecycled) finalBmp.recycle()
                                        Result.success(newName)
                                    } finally {
                                        if (!srcBmp.isRecycled) srcBmp.recycle()
                                    }
                                }
                                isTransformingFlatLay = false
                                outcome.onSuccess { newName ->
                                    while (catalogPhotos.size < studioPhotos.size) {
                                        catalogPhotos.add("")
                                    }
                                    if (catalogPhotos.isNotEmpty()) {
                                        catalogPhotos[0] = newName
                                    } else {
                                        catalogPhotos.add(newName)
                                    }
                                    useOriginalPhotos = false
                                    useCatalogPhotos = true
                                    studioFlatLayNote = localizedText(
                                        lang,
                                        "Katalog fotoğrafı hazır",
                                        "Catalog photo ready",
                                        "Katalogfoto bereit",
                                        "Photo catalogue prête",
                                        "Foto de catálogo lista",
                                        "商品图已生成"
                                    )
                                }.onFailure { err ->
                                    val msg = when (err) {
                                        is AiStudioService.GeminiApiException -> localizedText(
                                            lang,
                                            "Gemini API hatası: ${err.message}",
                                            "Gemini API error: ${err.message}"
                                        )
                                        is AiStudioService.StudioQuotaExceededException -> localizedText(
                                            lang,
                                            "Ücretsiz AI günlük limiti doldu. Ayarlar'dan ücretsiz Hugging Face tokenı veya Gemini anahtarı ekleyebilirsin.",
                                            "Free daily AI limit reached. Add a free Hugging Face token or Gemini key in Settings."
                                        )
                                        else -> localizedText(
                                            lang,
                                            "Katalog fotoğrafı oluşturulamadı. İnternet bağlantını kontrol edip tekrar dene.",
                                            "Could not create catalog photo. Check your connection and try again."
                                        )
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isTransformingFlatLay && !isRemovingBg && studioPhotos.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        border = BorderStroke(1.dp, DressKitStyle.accentColor()),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isCatalogSelected) DressKitStyle.accentColor() else DressKitStyle.accentSoft(),
                            contentColor = if (isCatalogSelected) Color.White else DressKitStyle.accentColor()
                        )
                    ) {
                        if (isTransformingFlatLay) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = DressKitStyle.accentColor()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when {
                                isTransformingFlatLay -> localizedText(
                                    lang,
                                    "Katalog fotoğrafı oluşturuluyor...",
                                    "Creating catalog photo...",
                                    "Katalogfoto wird erstellt...",
                                    "Création en cours...",
                                    "Creando foto de catálogo...",
                                    "正在生成商品图..."
                                )
                                hasCoverCatalog -> localizedText(
                                    lang,
                                    "Katalog Fotoğrafı Kullan",
                                    "Use Catalog Photo",
                                    "Katalogfoto nutzen",
                                    "Utiliser photo catalogue",
                                    "Usar foto de catálogo",
                                    "使用商品图"
                                )
                                else -> localizedText(
                                    lang,
                                    "Katalog Fotoğrafı Oluştur",
                                    "Create Catalog Photo",
                                    "Katalogfoto erstellen",
                                    "Créer photo catalogue",
                                    "Crear foto de catálogo",
                                    "生成商品图"
                                )
                            }
                        )
                    }
                    if (studioFlatLayNote != null) {
                        Text(
                            text = studioFlatLayNote!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = DressKitStyle.accentColor()
                        )
                    }
                }

                // Camera & Gallery Add Buttons (strict equal dimensions & single-line text)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRequestCamera,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DressKitStyle.buttonBg(),
                            contentColor = DressKitStyle.buttonFg()
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (displayedPhotos.isEmpty()) {
                                if (isEn) "Take Photo" else "Fotoğraf Çek"
                            } else {
                                if (isEn) "Add Photo" else "Fotoğraf Ekle"
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onRequestGallery,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DressKitStyle.cardSurface(),
                            contentColor = primaryInk
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (displayedPhotos.isEmpty()) {
                                if (isEn) "Gallery" else "Galeriden Seç"
                            } else {
                                if (isEn) "Add Angle" else "Açı Ekle"
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 2. Auto-predicted / Editable Name
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) "Item Name" else "Kıyafet Adı",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryInk
                    )
                    if (autoPredictedBadge && !userEditedName) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = DressKitStyle.accentSoft()
                        ) {
                            Text(
                                text = if (isEn) "Auto-predicted" else "Otomatik tahmin",
                                style = MaterialTheme.typography.labelSmall,
                                color = DressKitStyle.accentColor(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        userEditedName = it.isNotBlank()
                    },
                    placeholder = {
                        Text(
                            if (isEn) "Auto-predicted from photo or type your own" else "Fotoğraf ekleyince tahmin edilir veya yazabilirsin"
                        )
                    },
                    singleLine = true,
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

            // 3. General Category + Specific Sub-Category
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isEn) "Category" else "Genel Kategori",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryInk
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClothingCategory.entries.forEach { cat ->
                        FilterChipPill(
                            label = cat.display(lang),
                            selected = category == cat,
                            onClick = {
                                category = cat
                                val subs = ClothingSubCategory.forCategory(cat)
                                if (subCategory.parentCategory != cat && subs.isNotEmpty()) {
                                    subCategory = subs.first()
                                    syncAutoNameIfNotManual()
                                }
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isEn) "Piece Type" else "Kıyafet Türü",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryInk
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClothingSubCategory.forCategory(category).forEach { sub ->
                        FilterChipPill(
                            label = sub.display(lang),
                            selected = subCategory == sub,
                            onClick = {
                                subCategory = sub
                                syncAutoNameIfNotManual()
                            }
                        )
                    }
                }
            }

            // 4. Colors + Extracted Swatches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) "Colors" else "Renkler",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryInk
                    )
                    if (extractedHexes.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = if (isEn) "Detected:" else "Algılanan:",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryInk
                            )
                            extractedHexes.forEach { hex ->
                                ColorDot(color = Color(hex), size = 13.dp)
                            }
                        }
                    }
                }

                ColorSwatchPicker(
                    selectedColors = colors,
                    onToggleColor = { c ->
                        if (colors.contains(c)) colors.remove(c) else colors.add(0, c)
                        syncAutoNameIfNotManual()
                    }
                )
            }

            // 5. Style Tag
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isEn) "Style" else "Stil",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryInk
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StyleTag.entries.forEach { tag ->
                        FilterChipPill(
                            label = tag.display(lang),
                            selected = styleTag == tag,
                            onClick = { styleTag = tag }
                        )
                    }
                }
            }

            // 6. Seasons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isEn) "Season" else "Mevsim",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryInk
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Season.entries.forEach { season ->
                        FilterChipPill(
                            label = season.display(lang),
                            selected = seasons.contains(season),
                            onClick = {
                                if (season == Season.ALL) {
                                    seasons.clear()
                                    seasons.add(Season.ALL)
                                } else {
                                    seasons.remove(Season.ALL)
                                    if (seasons.contains(season)) {
                                        seasons.remove(season)
                                    } else {
                                        seasons.add(season)
                                    }
                                    if (seasons.isEmpty()) seasons.add(Season.ALL)
                                }
                            }
                        )
                    }
                }
            }

            // 7. Notes
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isEn) "Notes (optional)" else "Not (isteğe bağlı)",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryInk
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = {
                        Text(if (isEn) "Fabric, fit, brand or styling notes..." else "Kumaş, kalıp, not...")
                    },
                    minLines = 2,
                    maxLines = 4,
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModeSelectButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
        color = if (selected) DressKitStyle.buttonBg() else DressKitStyle.cardSurface(),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) DressKitStyle.buttonBg() else DressKitStyle.borderColor()
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) DressKitStyle.buttonFg() else DressKitStyle.primaryInk(),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = if (selected) DressKitStyle.buttonFg() else DressKitStyle.primaryInk()
            )
        }
    }
}
