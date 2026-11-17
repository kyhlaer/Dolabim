package com.dresskit.wardrobe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Style
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.OutfitRecommendationEngine
import com.dresskit.wardrobe.data.PriceTrackerService
import com.dresskit.wardrobe.data.SavedPhotoPair
import com.dresskit.wardrobe.data.WardrobeRepository
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.AppThemeMode
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.Season
import com.dresskit.wardrobe.ui.components.ImageCropperDialog
import com.dresskit.wardrobe.ui.components.VirtualTryOn2DDialog
import com.dresskit.wardrobe.ui.screens.AddEditItemScreen
import com.dresskit.wardrobe.ui.screens.InspirationScreen
import com.dresskit.wardrobe.ui.screens.ItemDetailScreen
import com.dresskit.wardrobe.ui.screens.OutfitDetailScreen
import com.dresskit.wardrobe.ui.screens.OutfitsScreen
import com.dresskit.wardrobe.ui.screens.PaletteStatsScreen
import com.dresskit.wardrobe.ui.screens.WardrobeScreen
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.DressKitTheme
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.STUDIO_ECOMMERCE_PROMPT
import com.dresskit.wardrobe.ui.theme.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PREFS_NAME = "dolabim_prefs"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_APP_LANGUAGE = "app_language"

private enum class PhotoTarget {
    NEW_SEPARATE_ITEMS_FROM_HOME,
    APPEND_TO_OPEN_DRAFT,
    APPEND_TO_EXISTING_ITEM,
    USER_BODY_PHOTO
}

private data class CropSession(
    val bitmaps: List<Bitmap>,
    val currentIndex: Int,
    val target: PhotoTarget,
    val targetItemId: String? = null,
    val accumulatedSaved: MutableList<SavedPhotoPair> = mutableListOf()
)

private data class OutfitDetailState(
    val title: String,
    val items: List<ClothingItem>,
    val existingOutfitId: String?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.filterTouchesWhenObscured = true

        Coil.setImageLoader {
            ImageLoader.Builder(applicationContext)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(applicationContext.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(80L * 1024L * 1024L)
                        .build()
                }
                .allowHardware(true)
                .crossfade(true)
                .build()
        }

        val repository = WardrobeRepository(applicationContext)
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialTheme = runCatching {
            AppThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, AppThemeMode.MODERN.name) ?: AppThemeMode.MODERN.name)
        }.getOrDefault(AppThemeMode.MODERN)
        val initialLang = runCatching {
            AppLanguage.valueOf(prefs.getString(KEY_APP_LANGUAGE, AppLanguage.TR.name) ?: AppLanguage.TR.name)
        }.getOrDefault(AppLanguage.TR)

        setContent {
            var themeMode by remember { mutableStateOf(initialTheme) }
            var appLanguage by remember { mutableStateOf(initialLang) }

            DressKitTheme(themeMode = themeMode, language = appLanguage) {
                DressKitApp(
                    repository = repository,
                    themeMode = themeMode,
                    onChangeTheme = { next ->
                        themeMode = next
                        prefs.edit().putString(KEY_THEME_MODE, next.name).apply()
                    },
                    onChangeLanguage = { next ->
                        appLanguage = next
                        prefs.edit().putString(KEY_APP_LANGUAGE, next.name).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun DressKitApp(
    repository: WardrobeRepository,
    themeMode: AppThemeMode,
    onChangeTheme: (AppThemeMode) -> Unit,
    onChangeLanguage: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val isEn = lang == AppLanguage.EN
    val scope = rememberCoroutineScope()

    val items by repository.items.collectAsState()
    val outfits by repository.outfits.collectAsState()

    var showSplash by remember { mutableStateOf(true) }
    var isCheckingPrices by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            ImageStorage.cleanStaleCameraTempFiles(context)
            PriceTrackerService.schedulePeriodicPriceCheck(context)
        }
        scope.launch { repository.load() }
        delay(1250L)
        showSplash = false
    }

    fun checkSingleWishlistItemPrice(targetItem: ClothingItem) {
        if (isCheckingPrices) return
        ensureNotificationPermission()
        scope.launch {
            isCheckingPrices = true
            val live = PriceTrackerService.fetchProductPrice(targetItem.productUrl)
            isCheckingPrices = false
            if (live == null) {
                Toast.makeText(
                    context,
                    if (isEn) "Could not read price from link" else "Linkten fiyat okunamadı",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val (livePrice, liveFormatted) = live
            val baseline = targetItem.lastCheckedPrice.takeIf { it > 0.0 }
                ?: PriceTrackerService.parsePrice(targetItem.priceText)

            if (baseline != null && livePrice < baseline - 0.5) {
                val oldText = targetItem.priceText.ifBlank { PriceTrackerService.formatPrice(baseline) }
                repository.updateItem(
                    targetItem.copy(
                        priceText = liveFormatted,
                        previousPrice = baseline,
                        lastCheckedPrice = livePrice,
                        priceDropped = true
                    )
                )
                PriceTrackerService.sendPriceDropNotification(
                    context = context,
                    itemName = targetItem.name,
                    oldPriceText = oldText,
                    newPriceText = liveFormatted,
                    notificationId = targetItem.id.hashCode()
                )
                Toast.makeText(
                    context,
                    if (isEn) "Price dropped to $liveFormatted!" else "Fiyat düştü: $liveFormatted!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                if (targetItem.lastCheckedPrice <= 0.0) {
                    repository.updateItem(
                        targetItem.copy(
                            priceText = targetItem.priceText.ifBlank { liveFormatted },
                            lastCheckedPrice = livePrice
                        )
                    )
                }
                Toast.makeText(
                    context,
                    if (isEn) "Price checked: $liveFormatted (no drop)" else "Güncel fiyat: $liveFormatted (indirim yok)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun checkAllWishlistPricesNow() {
        if (isCheckingPrices) return
        ensureNotificationPermission()
        scope.launch {
            isCheckingPrices = true
            val summary = PriceTrackerService.checkAllWishlistPrices(context, repository)
            isCheckingPrices = false
            val msg = when {
                summary.droppedCount > 0 -> if (isEn) {
                    "${summary.droppedCount} price drop(s) found!"
                } else {
                    "${summary.droppedCount} üründe fiyat düşüşü bulundu!"
                }
                summary.checkedCount > 0 -> if (isEn) {
                    "${summary.checkedCount} link(s) checked, no drops yet"
                } else {
                    "${summary.checkedCount} link kontrol edildi, indirim yok"
                }
                else -> if (isEn) {
                    "Could not fetch live prices from links"
                } else {
                    "Linklerden canlı fiyat okunamadı"
                }
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val wardrobeGridState = rememberLazyGridState()
    val outfitsListState = rememberLazyListState()
    val inspirationListState = rememberLazyListState()
    val summaryListState = rememberLazyListState()
    val savedScrollPositions = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }

    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var isAddingOrEditing by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ClothingItem?>(null) }
    var pendingDraftPhotos by remember { mutableStateOf<List<SavedPhotoPair>>(emptyList()) }

    // Sequential multi-item gallery queue (when selecting multiple photos from home)
    val batchQueue = remember { mutableStateListOf<SavedPhotoPair>() }
    var batchTotalCount by remember { mutableIntStateOf(0) }
    var batchStepIndex by remember { mutableIntStateOf(0) }

    var activeOutfitDetail by remember { mutableStateOf<OutfitDetailState?>(null) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }

    // Shared 2D Virtual Try-On state
    var showTryOnDialog by remember { mutableStateOf(false) }
    var tryOnInitialItems by remember { mutableStateOf<List<ClothingItem>>(emptyList()) }
    var bodyPhotoVersion by remember { mutableIntStateOf(0) }

    var activePhotoTarget by remember { mutableStateOf(PhotoTarget.NEW_SEPARATE_ITEMS_FROM_HOME) }
    var targetItemIdForPhoto by remember { mutableStateOf<String?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var activeCropSession by remember { mutableStateOf<CropSession?>(null) }

    fun finishOrAdvanceCropSession(chosenBitmap: Bitmap, recycleChosen: Boolean) {
        val session = activeCropSession ?: return
        if (session.target == PhotoTarget.USER_BODY_PHOTO) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    val file = ImageStorage.getUserBodyPhotoFile(context)
                    runCatching {
                        file.outputStream().use { fos ->
                            chosenBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                        }
                        true
                    }.getOrDefault(false)
                }
                if (recycleChosen) chosenBitmap.recycle()
                session.bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                activeCropSession = null
                if (ok) {
                    bodyPhotoVersion++
                    Toast.makeText(
                        context,
                        if (isEn) "Full-body photo updated" else "Boy fotoğrafın güncellendi",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }

        scope.launch {
            val savedPair = withContext(Dispatchers.IO) {
                ImageStorage.saveFromBitmap(context, chosenBitmap)
            }
            if (recycleChosen) chosenBitmap.recycle()
            session.accumulatedSaved.add(savedPair)

            val nextIndex = session.currentIndex + 1
            if (nextIndex < session.bitmaps.size) {
                activeCropSession = session.copy(currentIndex = nextIndex)
            } else {
                val completedPairs = session.accumulatedSaved.toList()
                val target = session.target
                val targetItemId = session.targetItemId
                session.bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                activeCropSession = null

                when (target) {
                    PhotoTarget.NEW_SEPARATE_ITEMS_FROM_HOME -> {
                        if (completedPairs.isNotEmpty()) {
                            batchQueue.clear()
                            if (completedPairs.size > 1) {
                                batchQueue.addAll(completedPairs.drop(1))
                                batchTotalCount = completedPairs.size
                                batchStepIndex = 1
                            } else {
                                batchTotalCount = 0
                                batchStepIndex = 0
                            }
                            editingItem = null
                            pendingDraftPhotos = listOf(completedPairs.first())
                            isAddingOrEditing = true
                        }
                    }

                    PhotoTarget.APPEND_TO_OPEN_DRAFT -> {
                        pendingDraftPhotos = pendingDraftPhotos + completedPairs
                        editingItem = null
                        isAddingOrEditing = true
                    }

                    PhotoTarget.APPEND_TO_EXISTING_ITEM -> {
                        if (targetItemId != null) {
                            repository.addPhotoPairsToItem(targetItemId, completedPairs)
                        }
                    }

                    PhotoTarget.USER_BODY_PHOTO -> Unit
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (!success || file == null || !file.exists()) {
            file?.delete()
            return@rememberLauncherForActivityResult
        }

        if (activePhotoTarget == PhotoTarget.USER_BODY_PHOTO) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    ImageStorage.saveUserBodyPhotoFromFile(context, file)
                }
                if (ok) {
                    bodyPhotoVersion++
                    Toast.makeText(
                        context,
                        if (isEn) "Full-body photo saved" else "Boy fotoğrafın kaydedildi",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageStorage.decodeOrientedBitmapFromFile(file).also { file.delete() }
                }
                if (bitmap != null) {
                    activeCropSession = CropSession(
                        bitmaps = listOf(bitmap),
                        currentIndex = 0,
                        target = activePhotoTarget,
                        targetItemId = targetItemIdForPhoto
                    )
                }
            }
        }
    }

    val galleryMultiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val targetSnapshot = activePhotoTarget
        val targetIdSnapshot = targetItemIdForPhoto
        scope.launch {
            val bitmaps = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> ImageStorage.decodeOrientedBitmapFromUri(context, uri) }
            }
            if (bitmaps.isNotEmpty()) {
                activeCropSession = CropSession(
                    bitmaps = bitmaps,
                    currentIndex = 0,
                    target = targetSnapshot,
                    targetItemId = targetIdSnapshot
                )
            }
        }
    }

    val bodyGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ImageStorage.saveUserBodyPhotoFromUri(context, uri)
            }
            if (ok) {
                bodyPhotoVersion++
                Toast.makeText(
                    context,
                    if (isEn) "Full-body photo loaded" else "Boy fotoğrafın yüklendi",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    if (isEn) "Could not load photo" else "Fotoğraf yüklenemedi",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun launchCameraFor(target: PhotoTarget, existingItemId: String? = null) {
        activePhotoTarget = target
        targetItemIdForPhoto = existingItemId
        val (file, uri) = ImageStorage.createCameraTempFile(context)
        pendingCameraFile = file
        cameraLauncher.launch(uri)
    }

    fun launchGalleryFor(target: PhotoTarget, existingItemId: String? = null) {
        activePhotoTarget = target
        targetItemIdForPhoto = existingItemId
        galleryMultiLauncher.launch("image/*")
    }

    fun clearUnsavedBatchAndExit() {
        val remainingFiles = batchQueue.flatMap { listOf(it.studioFileName, it.originalFileName) }
        batchQueue.clear()
        batchTotalCount = 0
        batchStepIndex = 0
        pendingDraftPhotos = emptyList()
        editingItem = null
        isAddingOrEditing = false
        if (remainingFiles.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                remainingFiles.forEach { ImageStorage.deletePhoto(context, it) }
            }
        }
    }

    val selectedItem = remember(selectedItemId, items) {
        items.find { it.id == selectedItemId }
    }

    // Cropper modal
    activeCropSession?.let { session ->
        val bmp = session.bitmaps.getOrNull(session.currentIndex)
        if (bmp != null) {
            val qLabel = if (session.bitmaps.size > 1) {
                "${session.currentIndex + 1} / ${session.bitmaps.size}"
            } else {
                null
            }
            ImageCropperDialog(
                sourceBitmap = bmp,
                queueLabel = qLabel,
                onCropConfirmed = { cropped -> finishOrAdvanceCropSession(cropped, recycleChosen = cropped !== bmp) },
                onUseFull = { full -> finishOrAdvanceCropSession(full, recycleChosen = false) },
                onCancel = {
                    session.bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                    val unusedPairs = session.accumulatedSaved.flatMap {
                        listOf(it.studioFileName, it.originalFileName)
                    }
                    activeCropSession = null
                    if (unusedPairs.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            unusedPairs.forEach { ImageStorage.deletePhoto(context, it) }
                        }
                    }
                }
            )
        }
    }

    // Shared 2D Virtual Try-On dialog
    if (showTryOnDialog) {
        VirtualTryOn2DDialog(
            initialItems = tryOnInitialItems,
            allWardrobeItems = items,
            bodyPhotoVersion = bodyPhotoVersion,
            onPickBodyFromGallery = { bodyGalleryLauncher.launch("image/*") },
            onCaptureBodyFromCamera = { launchCameraFor(PhotoTarget.USER_BODY_PHOTO) },
            onDismiss = { showTryOnDialog = false }
        )
    }

    // Theme & Language & Studio Prompt Settings Dialog
    if (showSettingsDialog) {
        ThemeSettingsDialog(
            currentTheme = themeMode,
            currentLanguage = lang,
            onSelectTheme = onChangeTheme,
            onSelectLanguage = onChangeLanguage,
            onOpenPromptDialog = {
                showSettingsDialog = false
                showPromptDialog = true
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showPromptDialog) {
        StudioPromptDialog(
            onDismiss = { showPromptDialog = false }
        )
    }

    var showExitConfirmDialog by remember { mutableStateOf(false) }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            containerColor = DressKitStyle.cardSurface(),
            titleContentColor = DressKitStyle.primaryInk(),
            textContentColor = DressKitStyle.secondaryInk(),
            title = { Text(if (isEn) "Exit App?" else "Uygulamadan Çıkılsın mı?") },
            text = {
                Text(
                    if (isEn) {
                        "Are you sure you want to exit Dolabım?"
                    } else {
                        "Dolabım uygulamasından çıkmak istediğine emin misin?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        (context as? android.app.Activity)?.finish()
                    }
                ) {
                    Text(
                        if (isEn) "Exit" else "Çıkış Yap",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text(
                        if (isEn) "Cancel" else "Vazgeç",
                        color = DressKitStyle.primaryInk()
                    )
                }
            }
        )
    }

    // Hardware back navigation across sub-routes + confirm before closing app
    BackHandler(enabled = true) {
        when {
            isAddingOrEditing -> clearUnsavedBatchAndExit()
            activeOutfitDetail != null -> activeOutfitDetail = null
            selectedItem != null -> selectedItemId = null
            else -> showExitConfirmDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DressKitStyle.canvasBg())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when {
            isAddingOrEditing -> {
                key(editingItem?.id ?: "new_item_batch_$batchStepIndex") {
                    val bLabel = if (batchTotalCount > 1) "$batchStepIndex/$batchTotalCount" else null
                    AddEditItemScreen(
                        existingItem = editingItem,
                        pendingPhotoPairs = pendingDraftPhotos,
                        batchLabel = bLabel,
                        hasMoreInBatch = batchQueue.isNotEmpty(),
                        onClearPendingPhotos = { pendingDraftPhotos = emptyList() },
                        onRequestCamera = { launchCameraFor(PhotoTarget.APPEND_TO_OPEN_DRAFT) },
                        onRequestGallery = { launchGalleryFor(PhotoTarget.APPEND_TO_OPEN_DRAFT) },
                        onSave = { name, category, subCategory, styleTag, seasons, colors, hexes,
                                   studioPhotos, originalPhotos, useOrig, notes, isWishlist, priceText, productUrl, lastCheckedPrice, addAnother ->
                            scope.launch {
                                val existing = editingItem
                                val itemToSave = ClothingItem(
                                    id = existing?.id ?: repository.generateId(),
                                    name = name.ifBlank { subCategory.display(lang) },
                                    category = category,
                                    subCategory = subCategory,
                                    styleTag = styleTag,
                                    seasons = seasons,
                                    colors = colors,
                                    extractedHexes = hexes,
                                    photoFileNames = studioPhotos,
                                    originalPhotoFileNames = originalPhotos,
                                    useOriginalPhotos = useOrig,
                                    notes = notes,
                                    isFavorite = existing?.isFavorite ?: false,
                                    isWishlist = isWishlist,
                                    priceText = if (isWishlist) priceText else "",
                                    productUrl = if (isWishlist) productUrl else "",
                                    lastCheckedPrice = if (isWishlist) lastCheckedPrice else 0.0,
                                    previousPrice = if (isWishlist) (existing?.previousPrice ?: 0.0) else 0.0,
                                    priceDropped = if (isWishlist) (existing?.priceDropped ?: false) else false,
                                    wearCount = existing?.wearCount ?: 0,
                                    lastWornAt = existing?.lastWornAt ?: 0L,
                                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                                )
                                if (isWishlist && PriceTrackerService.isSafeHttpsUrl(productUrl)) {
                                    ensureNotificationPermission()
                                }
                                if (existing != null) {
                                    repository.updateItem(itemToSave)
                                } else {
                                    repository.addItem(itemToSave)
                                }

                                if (batchQueue.isNotEmpty()) {
                                    val nextPair = batchQueue.removeAt(0)
                                    batchStepIndex++
                                    pendingDraftPhotos = listOf(nextPair)
                                } else if (addAnother) {
                                    editingItem = null
                                    pendingDraftPhotos = emptyList()
                                } else {
                                    batchTotalCount = 0
                                    batchStepIndex = 0
                                    editingItem = null
                                    pendingDraftPhotos = emptyList()
                                    isAddingOrEditing = false
                                }
                            }
                        },
                        onCancel = { clearUnsavedBatchAndExit() }
                    )
                }
            }

            activeOutfitDetail != null -> {
                val detail = activeOutfitDetail!!
                OutfitDetailScreen(
                    initialTitle = detail.title,
                    initialItems = detail.items,
                    allWardrobeItems = items,
                    existingOutfitId = detail.existingOutfitId,
                    onBack = { activeOutfitDetail = null },
                    onOpenTryOn2D = { chosenItems ->
                        tryOnInitialItems = chosenItems
                        showTryOnDialog = true
                    },
                    onMarkOutfitWorn = { wornItems ->
                        scope.launch {
                            repository.markItemsWorn(wornItems.map { it.id })
                            Toast.makeText(
                                context,
                                if (isEn) "Marked as worn today" else "Bugün giyildi olarak işlendi",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onSaveOutfit = { title, chosenItems, score, _, existingId ->
                        scope.launch {
                            val outfitObj = Outfit(
                                id = existingId ?: repository.generateId(),
                                title = title,
                                itemIds = chosenItems.map { it.id },
                                season = Season.ALL,
                                harmonyScore = score
                            )
                            if (existingId != null) {
                                repository.updateOutfit(outfitObj)
                            } else {
                                repository.addOutfit(outfitObj)
                            }
                            activeOutfitDetail = null
                            Toast.makeText(
                                context,
                                if (isEn) "Outfit saved" else "Kombin kaydedildi",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            selectedItem != null -> {
                val item = selectedItem
                val relatedOutfits = remember(item.id, outfits) {
                    outfits.filter { it.itemIds.contains(item.id) }
                }
                ItemDetailScreen(
                    item = item,
                    relatedOutfits = relatedOutfits,
                    onBack = { selectedItemId = null },
                    onEdit = {
                        editingItem = item
                        pendingDraftPhotos = emptyList()
                        isAddingOrEditing = true
                    },
                    onToggleFavorite = {
                        scope.launch { repository.toggleFavorite(item.id) }
                    },
                    onTogglePhotoMode = { useOrig ->
                        scope.launch { repository.setUseOriginalPhotos(item.id, useOrig) }
                    },
                    onMarkWorn = {
                        scope.launch { repository.markItemsWorn(listOf(item.id)) }
                    },
                    onMoveToWardrobe = {
                        scope.launch {
                            repository.toggleWishlist(item.id, moveToWardrobe = true)
                            Toast.makeText(context, strings.movedToWardrobeMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCheckWishlistPrice = {
                        checkSingleWishlistItemPrice(item)
                    },
                    onAddCameraPhoto = {
                        launchCameraFor(PhotoTarget.APPEND_TO_EXISTING_ITEM, item.id)
                    },
                    onAddGalleryPhotos = {
                        launchGalleryFor(PhotoTarget.APPEND_TO_EXISTING_ITEM, item.id)
                    },
                    onSetCoverPhoto = { idx ->
                        scope.launch { repository.setCoverPhotoAtIndex(item.id, idx) }
                    },
                    onRemovePhoto = { idx ->
                        scope.launch { repository.removePhotoAtIndex(item.id, idx) }
                    },
                    onDeleteItem = {
                        val id = item.id
                        selectedItemId = null
                        scope.launch { repository.deleteItem(id) }
                    }
                )
            }

            else -> {
                // Main 4-tab pager + floating bottom dock + Wardrobe FAB
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { page ->
                        when (page) {
                            0 -> WardrobeScreen(
                                items = items,
                                onItemClick = { selectedItemId = it.id },
                                onToggleFavorite = { id -> scope.launch { repository.toggleFavorite(id) } },
                                onAddFromCamera = { launchCameraFor(PhotoTarget.NEW_SEPARATE_ITEMS_FROM_HOME) },
                                onAddFromGallery = { launchGalleryFor(PhotoTarget.NEW_SEPARATE_ITEMS_FROM_HOME) },
                                onOpenSettings = { showSettingsDialog = true },
                                onCheckAllWishlistPrices = { checkAllWishlistPricesNow() },
                                isCheckingPrices = isCheckingPrices,
                                gridState = wardrobeGridState
                            )

                            1 -> OutfitsScreen(
                                items = items,
                                savedOutfits = outfits,
                                onOpenTryOn2D = { chosen ->
                                    tryOnInitialItems = chosen
                                    showTryOnDialog = true
                                },
                                onOpenOutfitDetail = { title, pieceList, existingId ->
                                    activeOutfitDetail = OutfitDetailState(title, pieceList, existingId)
                                },
                                onSaveOutfit = { title, pieceList, score ->
                                    scope.launch {
                                        val sorted = OutfitRecommendationEngine.sortTopToBottom(pieceList)
                                        repository.addOutfit(
                                            Outfit(
                                                id = repository.generateId(),
                                                title = title,
                                                itemIds = sorted.map { it.id },
                                                season = Season.ALL,
                                                harmonyScore = score
                                            )
                                        )
                                        Toast.makeText(
                                            context,
                                            if (isEn) "Outfit saved" else "Kombin kaydedildi",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onMarkOutfitWorn = { worn ->
                                    scope.launch {
                                        repository.markItemsWorn(worn.map { it.id })
                                        Toast.makeText(
                                            context,
                                            if (isEn) "Marked as worn" else "Giyildi olarak işaretlendi",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onDeleteOutfit = { id -> scope.launch { repository.deleteOutfit(id) } },
                                onOpenSettings = { showSettingsDialog = true },
                                listState = outfitsListState
                            )

                            2 -> InspirationScreen(
                                onOpenSettings = { showSettingsDialog = true },
                                listState = inspirationListState
                            )

                            else -> PaletteStatsScreen(
                                items = items,
                                outfits = outfits,
                                onOpenSettings = { showSettingsDialog = true },
                                listState = summaryListState
                            )
                        }
                    }

                    // Gallery Add FAB on Wardrobe tab
                    if (pagerState.currentPage == 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 92.dp)
                                .size(56.dp)
                                .clickable { launchGalleryFor(PhotoTarget.NEW_SEPARATE_ITEMS_FROM_HOME) },
                            shape = CircleShape,
                            color = DressKitStyle.buttonBg(),
                            shadowElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_tshirt_add),
                                    contentDescription = strings.pickFromGallery,
                                    tint = DressKitStyle.buttonFg(),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Floating Bottom Dock with Smart Scroll-to-Top & Position Restore
                    FloatingDock(
                        currentPage = pagerState.currentPage,
                        onTabSelected = { targetPage ->
                            scope.launch {
                                if (pagerState.currentPage == targetPage) {
                                    handleSmartScrollToggle(
                                        tabIndex = targetPage,
                                        wardrobeGridState = wardrobeGridState,
                                        outfitsListState = outfitsListState,
                                        inspirationListState = inspirationListState,
                                        summaryListState = summaryListState,
                                        savedScrollPositions = savedScrollPositions
                                    )
                                } else {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            }
        }

        // Launch Splash Overlay with "Dolabım by Kyhlaer"
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(durationMillis = 380))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DressKitStyle.canvasBg()),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(92.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo_custom),
                                contentDescription = "Dolabım Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(26.dp))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dolabım",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.8).sp
                        ),
                        color = DressKitStyle.primaryInk()
                    )
                    Text(
                        text = "by Kyhlaer",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp
                        ),
                        color = DressKitStyle.secondaryInk()
                    )
                }
            }
        }
    }
}

private suspend fun handleSmartScrollToggle(
    tabIndex: Int,
    wardrobeGridState: LazyGridState,
    outfitsListState: LazyListState,
    inspirationListState: LazyListState,
    summaryListState: LazyListState,
    savedScrollPositions: MutableMap<Int, Pair<Int, Int>>
) {
    if (tabIndex == 0) {
        val idx = wardrobeGridState.firstVisibleItemIndex
        val off = wardrobeGridState.firstVisibleItemScrollOffset
        if (idx > 0 || off > 0) {
            savedScrollPositions[0] = idx to off
            wardrobeGridState.animateScrollToItem(0, 0)
        } else {
            val saved = savedScrollPositions[0]
            if (saved != null && (saved.first > 0 || saved.second > 0)) {
                wardrobeGridState.animateScrollToItem(saved.first, saved.second)
            }
        }
        return
    }

    val listState = when (tabIndex) {
        1 -> outfitsListState
        2 -> inspirationListState
        else -> summaryListState
    }
    val idx = listState.firstVisibleItemIndex
    val off = listState.firstVisibleItemScrollOffset
    if (idx > 0 || off > 0) {
        savedScrollPositions[tabIndex] = idx to off
        listState.animateScrollToItem(0, 0)
    } else {
        val saved = savedScrollPositions[tabIndex]
        if (saved != null && (saved.first > 0 || saved.second > 0)) {
            listState.animateScrollToItem(saved.first, saved.second)
        }
    }
}

@Composable
private fun FloatingDock(
    currentPage: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val tabs: List<Pair<ImageVector, String>> = listOf(
        Icons.Outlined.Checkroom to strings.appTitle,
        Icons.Outlined.Style to strings.tabOutfits,
        Icons.Outlined.Explore to strings.tabInspiration,
        Icons.Outlined.Palette to strings.tabSummary
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = DressKitStyle.dockBg(),
        shadowElevation = 8.dp,
        border = if (DressKitStyle.isStreet()) BorderStroke(1.dp, DressKitStyle.borderColor()) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, (icon, desc) ->
                val active = currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) DressKitStyle.dockActiveBg() else Color.Transparent)
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = desc,
                        tint = if (active) DressKitStyle.dockActiveFg() else DressKitStyle.dockInactiveFg(),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsDialog(
    currentTheme: AppThemeMode,
    currentLanguage: AppLanguage,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenPromptDialog: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = appStrings()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DressKitStyle.cardCorner()),
            color = DressKitStyle.cardSurface(),
            border = BorderStroke(1.dp, DressKitStyle.borderColor())
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.settingsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = primaryInk
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = strings.close, tint = primaryInk)
                    }
                }

                // Theme Selection
                Text(
                    text = strings.themeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = secondaryInk
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        val selected = currentTheme == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTheme(mode) },
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            color = if (selected) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) DressKitStyle.accentColor() else DressKitStyle.borderColor()
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.label(currentLanguage),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = primaryInk
                                    )
                                    Text(
                                        text = mode.subtitle(currentLanguage),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryInk
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = DressKitStyle.accentColor()
                                    )
                                }
                            }
                        }
                    }
                }

                // Language Selection
                Text(
                    text = strings.languageLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = secondaryInk
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { langOption ->
                        val selected = currentLanguage == langOption
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectLanguage(langOption) },
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            color = if (selected) DressKitStyle.buttonBg() else DressKitStyle.secondarySurface(),
                            border = BorderStroke(
                                1.dp,
                                if (selected) DressKitStyle.buttonBg() else DressKitStyle.borderColor()
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = langOption.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) DressKitStyle.buttonFg() else primaryInk
                                )
                            }
                        }
                    }
                }

                // Studio E-Commerce Photo Prompt Button
                OutlinedButton(
                    onClick = onOpenPromptDialog,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    border = BorderStroke(1.dp, DressKitStyle.accentColor()),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = DressKitStyle.accentColor(),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.studioPromptBtn, color = DressKitStyle.accentColor())
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentLanguage == AppLanguage.EN) {
                            "Version v1.42 · by Kyhlaer"
                        } else {
                            "Sürüm v1.42 · by Kyhlaer"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryInk
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DressKitStyle.buttonBg(),
                        contentColor = DressKitStyle.buttonFg()
                    )
                ) {
                    Text(strings.ok)
                }
            }
        }
    }
}

@Composable
private fun StudioPromptDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val strings = appStrings()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DressKitStyle.cardSurface(),
        titleContentColor = primaryInk,
        textContentColor = secondaryInk,
        title = { Text(strings.studioPromptBtn) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DressKitStyle.secondarySurface(),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = STUDIO_ECOMMERCE_PROMPT,
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryInk,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(STUDIO_ECOMMERCE_PROMPT))
                    Toast.makeText(context, strings.copiedMsg, Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DressKitStyle.buttonBg(),
                    contentColor = DressKitStyle.buttonFg()
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.copyPrompt)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close, color = primaryInk)
            }
        }
    )
}
