package com.dresskit.wardrobe.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.PriceTrackerService
import com.dresskit.wardrobe.data.SecurityGuards
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.components.ColorDot
import com.dresskit.wardrobe.ui.components.FullscreenPhotoDialog
import com.dresskit.wardrobe.ui.components.WearActionButton
import com.dresskit.wardrobe.ui.components.WearCountEditorDialog
import com.dresskit.wardrobe.ui.theme.DressKitColors
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(
    item: ClothingItem,
    relatedOutfits: List<Outfit>,
    isTransformingStudio: Boolean = false,
    isRemovingBg: Boolean = false,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShareItem: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onSelectRemoveBg: (Int) -> Unit,
    onSelectOriginal: () -> Unit,
    onSelectOrCreateCatalog: (Int) -> Unit = {},
    onMarkWorn: () -> Unit,
    onUpdateWearCount: (Int) -> Unit = {},
    onMoveToWardrobe: () -> Unit,
    onCheckWishlistPrice: () -> Unit = {},
    onAddCameraPhoto: () -> Unit,
    onAddGalleryPhotos: () -> Unit,
    onEditCurrentPhoto: (Int) -> Unit = {},
    onSetCoverPhoto: (Int) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onDeleteItem: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val isEn = lang != AppLanguage.TR
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val scope = rememberCoroutineScope()

    val photos = item.activePhotos
    val pagerState = rememberPagerState(pageCount = { photos.size.coerceAtLeast(1) })
    var fullscreenPhoto by remember { mutableStateOf<String?>(null) }
    var showDeleteItemDialog by remember { mutableStateOf(false) }
    var showWearCountEditor by remember { mutableStateOf(false) }
    var photoIndexToDelete by remember { mutableStateOf<Int?>(null) }

    if (showWearCountEditor) {
        WearCountEditorDialog(
            itemsToEdit = listOf(item),
            onSave = { updated ->
                onUpdateWearCount(updated[item.id] ?: item.wearCount)
                showWearCountEditor = false
            },
            onDismiss = { showWearCountEditor = false }
        )
    }

    if (fullscreenPhoto != null) {
        FullscreenPhotoDialog(
            fileName = fullscreenPhoto!!,
            onDismiss = { fullscreenPhoto = null }
        )
    }

    if (showDeleteItemDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteItemDialog = false },
            containerColor = DressKitStyle.cardSurface(),
            titleContentColor = primaryInk,
            textContentColor = secondaryInk,
            title = { Text(if (isEn) "Delete Item" else "Kıyafeti Sil") },
            text = {
                Text(
                    if (isEn) {
                        "\"${item.name}\" and all of its photos will be deleted. Are you sure?"
                    } else {
                        "\"${item.name}\" ve tüm fotoğrafları silinecek. Emin misin?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteItemDialog = false
                        onDeleteItem()
                    }
                ) {
                    Text(
                        if (isEn) "Delete" else "Sil",
                        color = DressKitColors.DangerRust
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteItemDialog = false }) {
                    Text(if (isEn) "Cancel" else "Vazgeç", color = primaryInk)
                }
            }
        )
    }

    if (photoIndexToDelete != null) {
        AlertDialog(
            onDismissRequest = { photoIndexToDelete = null },
            containerColor = DressKitStyle.cardSurface(),
            titleContentColor = primaryInk,
            textContentColor = secondaryInk,
            title = { Text(if (isEn) "Remove Photo" else "Fotoğrafı Kaldır") },
            text = {
                Text(
                    if (isEn) "Remove this photo from the item?" else "Bu fotoğrafı kıyafetten kaldırmak istiyor musun?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetIdx = photoIndexToDelete!!
                        photoIndexToDelete = null
                        onRemovePhoto(targetIdx)
                    }
                ) {
                    Text(
                        if (isEn) "Remove" else "Kaldır",
                        color = DressKitColors.DangerRust
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { photoIndexToDelete = null }) {
                    Text(if (isEn) "Cancel" else "Vazgeç", color = primaryInk)
                }
            }
        )
    }

    Scaffold(
        containerColor = DressKitStyle.canvasBg(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = if (isEn) "Back" else "Geri",
                        tint = primaryInk
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShareItem) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = localizedText(lang, "Paylaş", "Share"),
                            tint = primaryInk
                        )
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (item.isFavorite) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = strings.favorites,
                            tint = if (item.isFavorite) DressKitStyle.accentColor() else primaryInk
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = if (isEn) "Edit" else "Düzenle",
                            tint = primaryInk
                        )
                    }
                    IconButton(onClick = { showDeleteItemDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = if (isEn) "Delete" else "Sil",
                            tint = DressKitColors.DangerRust
                        )
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
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (photos.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(DressKitStyle.cardCorner()))
                        .background(if (item.useOriginalPhotos) DressKitStyle.photoBoxSurface() else Color.White)
                        .border(
                            DressKitStyle.borderWidth(),
                            DressKitStyle.borderColor(),
                            RoundedCornerShape(DressKitStyle.cardCorner())
                        )
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val fileName = photos[page]
                        val file = remember(fileName) { ImageStorage.getPhotoFile(context, fileName) }
                        AsyncImage(
                            model = file,
                            contentDescription = "${item.name} ${page + 1}",
                            contentScale = if (item.useOriginalPhotos) ContentScale.Crop else ContentScale.Fit,
                            modifier = if (item.useOriginalPhotos) {
                                Modifier
                                    .fillMaxSize()
                                    .clickable { fullscreenPhoto = fileName }
                            } else {
                                Modifier
                                    .fillMaxSize()
                                    .padding(14.dp)
                                    .clickable { fullscreenPhoto = fileName }
                            }
                        )
                    }

                    // Page Counter
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = DressKitColors.CharcoalInk.copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomIn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${pagerState.currentPage + 1} / ${photos.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }

                // Studio / Original / Catalog Photo Mode Switcher
                val hasOrigPairs = item.originalPhotoFileNames.size == item.photoFileNames.size &&
                    item.originalPhotoFileNames.isNotEmpty()
                val hasCurrentCatalog = item.catalogPhotoFileNames
                    .getOrNull(pagerState.currentPage)
                    ?.isNotBlank() == true
                val isCatalogSelected = !item.useOriginalPhotos && item.useCatalogPhotos && hasCurrentCatalog
                val isRemoveBgSelected = !item.useOriginalPhotos && !isCatalogSelected

                if (hasOrigPairs) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailModeChip(
                            label = if (isRemovingBg) {
                                localizedText(lang, "Temizleniyor...", "Removing BG...", "Wird entfernt...", "Suppression...", "Eliminando...", "去背景中...")
                            } else {
                                localizedText(lang, "Arka Planı Kaldır", "Remove BG", "Hintergrund", "Sans fond", "Sin fondo", "去背景")
                            },
                            icon = Icons.Outlined.AutoFixHigh,
                            selected = isRemoveBgSelected,
                            onClick = { onSelectRemoveBg(pagerState.currentPage) },
                            modifier = Modifier.weight(1f)
                        )
                        DetailModeChip(
                            label = localizedText(lang, "Orijinal", "Original", "Original", "Original", "Original", "原图"),
                            icon = Icons.Outlined.Photo,
                            selected = item.useOriginalPhotos,
                            onClick = onSelectOriginal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Generative Catalog Studio Conversion / Use Toggle Action
                OutlinedButton(
                    onClick = { onSelectOrCreateCatalog(pagerState.currentPage) },
                    enabled = !isTransformingStudio && !isRemovingBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    border = BorderStroke(1.dp, DressKitStyle.accentColor()),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isCatalogSelected) DressKitStyle.accentColor() else DressKitStyle.accentSoft(),
                        contentColor = if (isCatalogSelected) Color.White else DressKitStyle.accentColor()
                    )
                ) {
                    if (isTransformingStudio) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isTransformingStudio -> localizedText(
                                lang,
                                "Katalog fotoğrafı oluşturuluyor...",
                                "Creating catalog photo...",
                                "Katalogfoto wird erstellt...",
                                "Création en cours...",
                                "Creando foto de catálogo...",
                                "正在生成商品图..."
                            )
                            hasCurrentCatalog -> localizedText(
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

                // Thumbnail Strip
                if (photos.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(photos) { idx, fileName ->
                            val file = remember(fileName) { ImageStorage.getPhotoFile(context, fileName) }
                            val isCurrent = pagerState.currentPage == idx
                            Box(
                                modifier = Modifier
                                    .size(width = 68.dp, height = 68.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (item.useOriginalPhotos) DressKitStyle.photoBoxSurface() else Color.White)
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) DressKitStyle.accentColor() else DressKitStyle.borderColor(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        scope.launch { pagerState.animateScrollToPage(idx) }
                                    }
                            ) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    contentScale = if (item.useOriginalPhotos) ContentScale.Crop else ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onEditCurrentPhoto(pagerState.currentPage) },
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DressKitStyle.cardSurface(),
                                contentColor = primaryInk
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Crop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                localizedText(
                                    lang,
                                    "Fotoğrafı Düzenle",
                                    "Edit Photo",
                                    "Foto bearbeiten",
                                    "Modifier la photo",
                                    "Editar foto",
                                    "编辑照片"
                                )
                            )
                        }

                        if (photos.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (pagerState.currentPage != 0) {
                                    OutlinedButton(
                                        onClick = {
                                            onSetCoverPhoto(pagerState.currentPage)
                                            scope.launch { pagerState.animateScrollToPage(0) }
                                        },
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = DressKitStyle.cardSurface(),
                                            contentColor = primaryInk
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.StarOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isEn) "Set Cover" else "Kapak Yap")
                                    }
                                }

                                OutlinedButton(
                                    onClick = { photoIndexToDelete = pagerState.currentPage },
                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = DressKitStyle.cardSurface(),
                                        contentColor = DressKitColors.DangerRust
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isEn) "Delete Photo" else "Fotoğrafı Sil")
                                }
                            }
                        }
                    }
                }
            }

            // Wishlist Banner & Purchase Move CTA
            if (item.isWishlist) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                    color = DressKitStyle.accentSoft(),
                    border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    imageVector = Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                    tint = DressKitStyle.accentColor()
                                )
                                Text(
                                    text = strings.wishlist,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = primaryInk
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (item.priceDropped && item.previousPrice > 0.0) {
                                    Text(
                                        text = PriceTrackerService.formatPrice(item.previousPrice, item.priceText),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            textDecoration = TextDecoration.LineThrough
                                        ),
                                        color = secondaryInk
                                    )
                                }
                                if (item.priceText.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (item.priceDropped) Color(0xFF1D6B42) else DressKitStyle.buttonBg()
                                    ) {
                                        Text(
                                            text = if (item.priceDropped) "🔥 ${item.priceText}" else item.priceText,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (item.priceDropped) Color.White else DressKitStyle.buttonFg(),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (PriceTrackerService.isSafeHttpsUrl(item.productUrl)) {
                            Text(
                                text = item.productUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        SecurityGuards.openExternalUrlSafely(context, item.productUrl)
                                    },
                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = null,
                                        tint = primaryInk,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isEn) "Open Store" else "Mağazada Aç",
                                        color = primaryInk,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = onCheckWishlistPrice,
                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                    border = BorderStroke(1.dp, DressKitStyle.accentColor()),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.NotificationsActive,
                                        contentDescription = null,
                                        tint = DressKitStyle.accentColor(),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isEn) "Check Price" else "Fiyat Kontrol",
                                        color = DressKitStyle.accentColor(),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onMoveToWardrobe,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DressKitStyle.buttonBg(),
                                contentColor = DressKitStyle.buttonFg()
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ShoppingBag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.moveToWardrobeBtn)
                        }
                    }
                }
            }

            // Add More Photos Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                color = DressKitStyle.cardSurface(),
                border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isEn) "Add more angles to this piece" else "Bu kıyafete yeni fotoğraf ekle",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryInk
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onAddCameraPhoto,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
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
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "Camera" else "Kamera",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onAddGalleryPhotos,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = primaryInk
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "Gallery" else "Galeri",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Garment Details Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                color = DressKitStyle.cardSurface(),
                border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name.ifBlank { item.subCategory.display(lang) },
                            style = MaterialTheme.typography.headlineSmall,
                            color = primaryInk,
                            modifier = Modifier.weight(1f)
                        )
                        if (!item.isWishlist) {
                            WearActionButton(
                                label = if (item.wearCount == 0) {
                                    strings.wornBtn
                                } else {
                                    "${strings.wornBtn} (${item.wearCount})"
                                },
                                onClick = onMarkWorn,
                                onLongClick = { showWearCountEditor = true },
                                filled = false,
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TagChip(item.category.display(lang))
                        TagChip(item.subCategory.display(lang))
                        TagChip(item.styleTag.display(lang))
                        item.seasons.forEach { season ->
                            TagChip(season.display(lang))
                        }
                    }

                    if (item.colors.isNotEmpty() || item.extractedHexes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isEn) "Color Palette" else "Renk Paleti",
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryInk
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item.colors.forEach { color ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ColorDot(color = color.composeColor, size = 16.dp)
                                        Text(
                                            text = color.display(lang),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = primaryInk
                                        )
                                    }
                                }
                                if (item.colors.isEmpty()) {
                                    item.extractedHexes.forEach { hex ->
                                        ColorDot(color = Color(hex), size = 18.dp)
                                    }
                                }
                            }
                        }
                    }

                    if (item.notes.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isEn) "Notes" else "Notlar",
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryInk
                            )
                            Text(
                                text = item.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = primaryInk
                            )
                        }
                    }

                    if (relatedOutfits.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isEn) "Featured in Outfits" else "Kullanıldığı Kombinler",
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryInk
                            )
                            relatedOutfits.forEach { outfit ->
                                Text(
                                    text = "• ${outfit.title}",
                                    style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun DetailModeChip(
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) DressKitStyle.buttonFg() else DressKitStyle.primaryInk(),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) DressKitStyle.buttonFg() else DressKitStyle.primaryInk()
            )
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
        color = DressKitStyle.secondarySurface()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = DressKitStyle.primaryInk(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
