package com.dresskit.wardrobe

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.dresskit.wardrobe.data.AiStudioService
import com.dresskit.wardrobe.data.AnalyticsService
import com.dresskit.wardrobe.data.AppFeatureKey
import com.dresskit.wardrobe.data.AppUpdateInfo
import com.dresskit.wardrobe.data.AppUpdateManager
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.OutfitRecommendationEngine
import com.dresskit.wardrobe.data.PersonalColorAnalyzer
import com.dresskit.wardrobe.data.PersonalColorProfile
import com.dresskit.wardrobe.data.PriceTrackerService
import com.dresskit.wardrobe.data.RemoteConfigManager
import com.dresskit.wardrobe.data.RemoteConfigState
import com.dresskit.wardrobe.data.SavedPhotoPair
import com.dresskit.wardrobe.data.SecurityGuards
import com.dresskit.wardrobe.data.SocialNotificationService
import com.dresskit.wardrobe.data.SocialProfileService
import com.dresskit.wardrobe.data.StudioCutoutProcessor
import com.dresskit.wardrobe.data.WardrobeRepository
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.AppThemeMode
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.DeepLinkTarget
import com.dresskit.wardrobe.model.GenderPreference
import com.dresskit.wardrobe.model.LocalProfileConfig
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.Season
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.components.AppUpdateDialog
import com.dresskit.wardrobe.ui.components.ImageCropperDialog
import com.dresskit.wardrobe.ui.components.RemoteProGateDialog
import com.dresskit.wardrobe.ui.components.VirtualTryOn3DDialog
import com.dresskit.wardrobe.ui.screens.ActivityHeartDialog
import com.dresskit.wardrobe.ui.screens.AddEditItemScreen
import com.dresskit.wardrobe.ui.screens.InspirationScreen
import com.dresskit.wardrobe.ui.screens.ItemDetailScreen
import com.dresskit.wardrobe.ui.screens.OtherProfileDialog
import com.dresskit.wardrobe.ui.screens.OutfitDetailScreen
import com.dresskit.wardrobe.ui.screens.OutfitsScreen
import com.dresskit.wardrobe.ui.screens.PaletteStatsScreen
import com.dresskit.wardrobe.ui.screens.ProfileHubScreen
import com.dresskit.wardrobe.ui.screens.ShareGarmentDialog
import com.dresskit.wardrobe.ui.screens.ShareOutfitDialog
import com.dresskit.wardrobe.ui.screens.SharedItemViewerDialog
import com.dresskit.wardrobe.ui.screens.SharedOutfitViewerDialog
import com.dresskit.wardrobe.ui.screens.SinglePostDeepLinkDialog
import com.dresskit.wardrobe.ui.screens.SocialFeedScreen
import com.dresskit.wardrobe.ui.screens.UserAvatarCircle
import com.dresskit.wardrobe.ui.screens.WardrobeScreen
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.DressKitTheme
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.STUDIO_ECOMMERCE_PROMPT
import com.dresskit.wardrobe.ui.theme.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PREFS_NAME = "dolabim_prefs"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_APP_LANGUAGE = "app_language"
private const val KEY_GENDER_PREF = "gender_pref"
private const val KEY_GENDER_USER_SET = "gender_user_set"
private const val KEY_LIKED_LOOK_IDS = "liked_look_ids"
private const val KEY_DISLIKED_LOOK_IDS = "disliked_look_ids"

private enum class PhotoTarget {
    NEW_SEPARATE_ITEMS_FROM_HOME,
    APPEND_TO_OPEN_DRAFT,
    APPEND_TO_EXISTING_ITEM,
    EDIT_SINGLE_DRAFT_PHOTO,
    EDIT_SINGLE_EXISTING_ITEM_PHOTO,
    USER_BODY_PHOTO,
    USER_FACE_PHOTO,
    USER_AVATAR_PHOTO,
    CUSTOM_SHOWCASE_PHOTO
}

private data class CropSession(
    val bitmaps: List<Bitmap>,
    val currentIndex: Int,
    val target: PhotoTarget,
    val targetItemId: String? = null,
    val targetPhotoIndex: Int = -1,
    val accumulatedSaved: MutableList<SavedPhotoPair> = mutableListOf()
)

private data class OutfitDetailState(
    val title: String,
    val items: List<ClothingItem>,
    val existingOutfitId: String?
)

private data class ShareOutfitRequest(
    val title: String,
    val items: List<ClothingItem>,
    val harmonyScore: Int,
    val weatherLabel: String = ""
)

class MainActivity : ComponentActivity() {
    private val pendingDeepLinkFlow = MutableStateFlow<String?>(null)
    private val pendingOpenActivityHeartFlow = MutableStateFlow(false)

    override fun onStart() {
        super.onStart()
        AnalyticsService.onAppForeground(applicationContext)
    }

    override fun onStop() {
        AnalyticsService.onAppBackground(applicationContext)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { pendingDeepLinkFlow.value = it }
        if (intent.getBooleanExtra(SocialNotificationService.EXTRA_OPEN_ACTIVITY_HEART, false)) {
            pendingOpenActivityHeartFlow.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.filterTouchesWhenObscured = true
        intent?.dataString?.let { pendingDeepLinkFlow.value = it }
        if (intent?.getBooleanExtra(SocialNotificationService.EXTRA_OPEN_ACTIVITY_HEART, false) == true) {
            pendingOpenActivityHeartFlow.value = true
        }

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
        val initialGenderPref = runCatching {
            GenderPreference.valueOf(prefs.getString(KEY_GENDER_PREF, GenderPreference.UNISEX.name) ?: GenderPreference.UNISEX.name)
        }.getOrDefault(GenderPreference.UNISEX)
        val initialGenderUserSet = prefs.getBoolean(KEY_GENDER_USER_SET, false)

        setContent {
            var themeMode by remember { mutableStateOf(initialTheme) }
            var appLanguage by remember { mutableStateOf(initialLang) }
            var savedGenderPref by remember { mutableStateOf(initialGenderPref) }
            var genderUserSet by remember { mutableStateOf(initialGenderUserSet) }

            val incomingDeepLink by pendingDeepLinkFlow.collectAsState()
            val openHeartFlag by pendingOpenActivityHeartFlow.collectAsState()

            DressKitTheme(themeMode = themeMode, language = appLanguage) {
                DressKitApp(
                    repository = repository,
                    themeMode = themeMode,
                    savedGenderPref = savedGenderPref,
                    genderUserSet = genderUserSet,
                    incomingDeepLinkUri = incomingDeepLink,
                    onConsumeDeepLink = { pendingDeepLinkFlow.value = null },
                    incomingOpenActivityHeart = openHeartFlag,
                    onConsumeActivityHeart = { pendingOpenActivityHeartFlow.value = false },
                    onChangeTheme = { next ->
                        themeMode = next
                        prefs.edit().putString(KEY_THEME_MODE, next.name).apply()
                    },
                    onChangeLanguage = { next ->
                        appLanguage = next
                        prefs.edit().putString(KEY_APP_LANGUAGE, next.name).apply()
                    },
                    onChangeGender = { next, isExplicit ->
                        savedGenderPref = next
                        genderUserSet = isExplicit
                        prefs.edit()
                            .putString(KEY_GENDER_PREF, next.name)
                            .putBoolean(KEY_GENDER_USER_SET, isExplicit)
                            .apply()
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
    savedGenderPref: GenderPreference,
    genderUserSet: Boolean,
    incomingDeepLinkUri: String? = null,
    onConsumeDeepLink: () -> Unit = {},
    incomingOpenActivityHeart: Boolean = false,
    onConsumeActivityHeart: () -> Unit = {},
    onChangeTheme: (AppThemeMode) -> Unit,
    onChangeLanguage: (AppLanguage) -> Unit,
    onChangeGender: (GenderPreference, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val isEn = lang != AppLanguage.TR
    val scope = rememberCoroutineScope()

    val items by repository.items.collectAsState()
    val outfits by repository.outfits.collectAsState()
    val inferredGender = remember(items) {
        OutfitRecommendationEngine.inferWardrobeGender(items.filter { !it.isWishlist })
    }
    val activeGender = if (genderUserSet) savedGenderPref else inferredGender

    var showSplash by remember { mutableStateOf(true) }
    var isCheckingPrices by remember { mutableStateOf(false) }
    var isTransformingStudio by remember { mutableStateOf(false) }
    var isRemovingBg by remember { mutableStateOf(false) }
    var analyticsEnabled by remember { mutableStateOf(AnalyticsService.isEnabled(context)) }
    var userProfile by remember { mutableStateOf(SocialProfileService.loadLocalProfile(context)) }
    var pendingRequestsCount by remember { mutableIntStateOf(0) }
    var unreadNotificationsCount by remember { mutableIntStateOf(0) }
    var showProfileHub by remember { mutableStateOf(false) }
    var showActivityHeartDialog by remember { mutableStateOf(false) }
    var sharingItemTarget by remember { mutableStateOf<ClothingItem?>(null) }
    var activeSharedItemId by remember { mutableStateOf<String?>(null) }
    var sharingOutfitTarget by remember { mutableStateOf<ShareOutfitRequest?>(null) }
    var activeSharedOutfitId by remember { mutableStateOf<String?>(null) }
    var activeSocialPostTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeOtherProfile by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pendingCustomShowcasePhotoUrl by remember { mutableStateOf<String?>(null) }

    fun handleIncomingTarget(target: DeepLinkTarget) {
        when (target) {
            is DeepLinkTarget.SharedItem -> activeSharedItemId = target.shareId
            is DeepLinkTarget.SharedOutfit -> activeSharedOutfitId = target.shareId
            is DeepLinkTarget.UserProfile -> activeOtherProfile = target.usernameOrUid to !target.isUid
            is DeepLinkTarget.SocialPost -> activeSocialPostTarget = target.postId to target.ownerUid
        }
    }

    LaunchedEffect(incomingOpenActivityHeart) {
        if (incomingOpenActivityHeart) {
            showActivityHeartDialog = true
            onConsumeActivityHeart()
        }
    }

    LaunchedEffect(incomingDeepLinkUri) {
        if (!incomingDeepLinkUri.isNullOrBlank()) {
            SocialProfileService.parseDeepLink(incomingDeepLinkUri)?.let { handleIncomingTarget(it) }
            onConsumeDeepLink()
        }
    }

    LaunchedEffect(items, outfits, themeMode, lang, userProfile.username, userProfile.displayName, userProfile.avatarThumbBase64) {
        AnalyticsService.initContext(
            context = context,
            theme = themeMode.name,
            language = lang.code,
            itemsCount = items.count { !it.isWishlist },
            wishlistItemsCount = items.count { it.isWishlist },
            outfitsCount = outfits.size
        )
        AnalyticsService.updateProfileIdentity(
            context = context,
            username = userProfile.username,
            displayName = userProfile.displayName,
            avatarThumbBase64 = userProfile.avatarThumbBase64.ifBlank { userProfile.avatarBase64 }
        )
    }

    fun applyOfflineRemoveBg(targetItem: ClothingItem, photoIndex: Int) {
        if (isRemovingBg || isTransformingStudio) return
        AnalyticsService.trackFeature("remove_bg")
        val curCutout = targetItem.photoFileNames.getOrNull(photoIndex)
        if (ImageStorage.isOfflineCutoutFileName(curCutout)) {
            scope.launch {
                repository.setPhotoMode(targetItem.id, useOriginal = false, useCatalog = false)
            }
            return
        }
        val origName = targetItem.originalPhotoFileNames.getOrNull(photoIndex)
            ?: curCutout
            ?: return
        scope.launch {
            isRemovingBg = true
            val newCutoutName = withContext(Dispatchers.IO) {
                val origBmp = ImageStorage.decodePhotoBitmap(context, origName) ?: return@withContext null
                try {
                    val cutoutBmp = com.dresskit.wardrobe.data.StudioCutoutProcessor.toEcommerceFlatLayShot(context, origBmp)
                    try {
                        ImageStorage.saveNewCutoutVariant(context, cutoutBmp)
                    } finally {
                        if (cutoutBmp !== origBmp && !cutoutBmp.isRecycled) cutoutBmp.recycle()
                    }
                } finally {
                    if (!origBmp.isRecycled) origBmp.recycle()
                }
            }
            isRemovingBg = false
            if (newCutoutName != null) {
                repository.replaceCutoutPhotoAtIndex(targetItem.id, photoIndex, newCutoutName)
            } else {
                repository.setPhotoMode(targetItem.id, useOriginal = false, useCatalog = false)
            }
        }
    }

    fun transformItemPhotoToEcommerce(targetItem: ClothingItem, photoIndex: Int) {
        if (isTransformingStudio || isRemovingBg) return
        val existingCatalog = targetItem.catalogPhotoFileNames.getOrNull(photoIndex)
        if (!existingCatalog.isNullOrBlank()) {
            AnalyticsService.trackFeature("catalog_ai", "reuse")
            scope.launch {
                repository.setPhotoMode(targetItem.id, useOriginal = false, useCatalog = true)
            }
            return
        }
        AnalyticsService.trackFeature("catalog_ai", "generate")
        AnalyticsService.startFeatureTimer("catalog_ai")
        val srcName = targetItem.originalPhotoFileNames.getOrNull(photoIndex)
            ?: targetItem.photoFileNames.getOrNull(photoIndex)
            ?: return
        scope.launch {
            isTransformingStudio = true
            val outcome = withContext(Dispatchers.IO) {
                val srcBmp = ImageStorage.decodePhotoBitmap(context, srcName)
                    ?: return@withContext Result.failure<String>(
                        IllegalStateException("Photo not found")
                    )
                try {
                    val aiRes = AiStudioService.generateEcommerceFlatLay(context, srcBmp)
                    if (aiRes.isFailure) {
                        return@withContext Result.failure<String>(
                            aiRes.exceptionOrNull() ?: IllegalStateException("Catalog generation failed")
                        )
                    }
                    val outBmp = aiRes.getOrThrow()
                    val newName = ImageStorage.saveNewCatalogVariant(context, outBmp)
                    if (outBmp !== srcBmp && !outBmp.isRecycled) outBmp.recycle()
                    Result.success(newName)
                } finally {
                    if (!srcBmp.isRecycled) srcBmp.recycle()
                }
            }
            isTransformingStudio = false
            AnalyticsService.endFeatureTimer("catalog_ai")
            outcome.onSuccess { newName ->
                repository.setCatalogPhotoAtIndex(targetItem.id, photoIndex, newName)
                Toast.makeText(
                    context,
                    localizedText(
                        lang,
                        "Katalog fotoğrafı hazır",
                        "Catalog photo ready",
                        "Katalogfoto bereit",
                        "Photo catalogue prête",
                        "Foto de catálogo lista",
                        "商品图已生成"
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { err ->
                AnalyticsService.trackError("catalog_ai", err.message ?: err.javaClass.simpleName)
                val msg = when (err) {
                    is AiStudioService.GeminiApiException -> localizedText(
                        lang,
                        "Gemini API hatası: ${err.message}",
                        "Gemini API error: ${err.message}"
                    )
                    is AiStudioService.StudioQuotaExceededException -> if (AiStudioService.hasHfToken(context)) {
                        localizedText(
                            lang,
                            "Hugging Face ücretsiz ZeroGPU günlük limiti doldu veya sunucular yoğun. Biraz sonra tekrar dene veya Ayarlar'dan Gemini anahtarı ekle.",
                            "Hugging Face free ZeroGPU daily limit reached or servers busy. Try again shortly or add a Gemini key in Settings."
                        )
                    } else {
                        localizedText(
                            lang,
                            "Ücretsiz AI günlük limiti doldu. Ayarlar'dan ücretsiz Hugging Face tokenı veya Gemini anahtarı ekleyebilirsin.",
                            "Free daily AI limit reached. Add a free Hugging Face token or Gemini key in Settings."
                        )
                    }
                    is AiStudioService.StudioServiceBusyException -> localizedText(
                        lang,
                        "AI katalog sunucuları şu an yoğun. Biraz sonra tekrar dene veya Ayarlar'dan Gemini anahtarı ekle.",
                        "Catalog AI servers are currently busy. Try again shortly or add a Gemini key in Settings."
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
    }

    val appPrefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var remoteConfig by remember { mutableStateOf(RemoteConfigManager.loadCached(context)) }
    var pendingProGate by remember { mutableStateOf<Pair<AppFeatureKey, () -> Unit>?>(null) }
    var availableUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    fun runGatedFeature(feature: AppFeatureKey, action: () -> Unit) {
        if (remoteConfig.isFeatureLocked(feature)) {
            pendingProGate = feature to action
        } else {
            action()
        }
    }

    fun checkAppUpdateManually(onUpdateFound: () -> Unit = {}) {
        if (isCheckingUpdate) return
        scope.launch {
            isCheckingUpdate = true
            val found = AppUpdateManager.checkForUpdate(context, ignoreSnooze = true)
            isCheckingUpdate = false
            if (found != null) {
                onUpdateFound()
                availableUpdateInfo = found
            } else {
                val curVer = AppUpdateManager.getInstalledVersionName(context)
                Toast.makeText(
                    context,
                    localizedText(
                        lang,
                        "Uygulama güncel (v$curVer)",
                        "App is up to date (v$curVer)",
                        "App ist aktuell (v$curVer)",
                        "Application à jour (v$curVer)",
                        "Aplicación actualizada (v$curVer)",
                        "当前已是最新版本 (v$curVer)"
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    var likedLookIds by remember {
        mutableStateOf(appPrefs.getStringSet(KEY_LIKED_LOOK_IDS, emptySet())?.toSet() ?: emptySet())
    }
    var dislikedLookIds by remember {
        mutableStateOf(appPrefs.getStringSet(KEY_DISLIKED_LOOK_IDS, emptySet())?.toSet() ?: emptySet())
    }

    fun recordLookSwipe(lookId: String, liked: Boolean?) {
        val newLiked = likedLookIds.toMutableSet()
        val newDisliked = dislikedLookIds.toMutableSet()
        when (liked) {
            true -> {
                newLiked.add(lookId)
                newDisliked.remove(lookId)
            }
            false -> {
                newDisliked.add(lookId)
                newLiked.remove(lookId)
            }
            null -> {
                newLiked.remove(lookId)
                newDisliked.remove(lookId)
            }
        }
        likedLookIds = newLiked
        dislikedLookIds = newDisliked
        appPrefs.edit()
            .putStringSet(KEY_LIKED_LOOK_IDS, newLiked)
            .putStringSet(KEY_DISLIKED_LOOK_IDS, newDisliked)
            .apply()
    }

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
            AppUpdateManager.cleanStaleUpdateFiles(context)
            PriceTrackerService.schedulePeriodicPriceCheck(context)
            SocialNotificationService.schedulePeriodicChecks(context)
        }
        scope.launch {
            remoteConfig = RemoteConfigManager.refreshIfNeeded(context)
        }
        scope.launch {
            availableUpdateInfo = AppUpdateManager.checkForUpdate(context, ignoreSnooze = false)
        }
        scope.launch {
            while (true) {
                SocialProfileService.fetchPendingFollowRequests(context).onSuccess {
                    pendingRequestsCount = it.size
                }
                unreadNotificationsCount = SocialNotificationService.pollAndDispatchNotifications(context)
                delay(20_000L)
            }
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

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })
    val wardrobeGridState = rememberLazyGridState()
    val outfitsListState = rememberLazyListState()
    val socialListState = rememberLazyListState()
    val inspirationListState = rememberLazyListState()
    val summaryListState = rememberLazyListState()
    val savedScrollPositions = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }

    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var isAddingOrEditing by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ClothingItem?>(null) }
    var pendingDraftPhotos by remember { mutableStateOf<List<SavedPhotoPair>>(emptyList()) }
    var pendingDraftPhotoReplacement by remember { mutableStateOf<Pair<Int, SavedPhotoPair>?>(null) }
    var inspirationVisitNonce by remember { mutableIntStateOf(1) }

    LaunchedEffect(pagerState.currentPage) {
        val tabKey = when (pagerState.currentPage) {
            0 -> "wardrobe"
            1 -> "outfits"
            2 -> "social"
            3 -> "inspiration"
            else -> "summary"
        }
        AnalyticsService.onTabChanged(tabKey)
        if (pagerState.currentPage == 3) {
            inspirationVisitNonce++
        }
    }

    // Sequential multi-item gallery queue (when selecting multiple photos from home)
    val batchQueue = remember { mutableStateListOf<SavedPhotoPair>() }
    var batchTotalCount by remember { mutableIntStateOf(0) }
    var batchStepIndex by remember { mutableIntStateOf(0) }

    var activeOutfitDetail by remember { mutableStateOf<OutfitDetailState?>(null) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showReleaseNotesDialog by remember { mutableStateOf(false) }

    // Shared 2D Virtual Try-On & Independent Face Tone Analysis states
    var showTryOnDialog by remember { mutableStateOf(false) }
    var tryOnInitialItems by remember { mutableStateOf<List<ClothingItem>>(emptyList()) }
    var bodyPhotoVersion by remember { mutableIntStateOf(0) }
    var facePhotoVersion by remember { mutableIntStateOf(0) }

    val personalColorProfile by produceState<PersonalColorProfile?>(
        initialValue = null,
        key1 = facePhotoVersion,
        key2 = lang
    ) {
        value = withContext(Dispatchers.IO) {
            PersonalColorAnalyzer.analyzeUserPhoto(context, lang)
        }
    }
    val flatteringColors = remember(personalColorProfile) {
        personalColorProfile?.flatteringColors?.toSet() ?: emptySet()
    }

    var activePhotoTarget by remember { mutableStateOf(PhotoTarget.NEW_SEPARATE_ITEMS_FROM_HOME) }
    var targetItemIdForPhoto by remember { mutableStateOf<String?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var activeCropSession by remember { mutableStateOf<CropSession?>(null) }

    fun openCropperForSavedPhoto(
        fileName: String,
        target: PhotoTarget,
        photoIndex: Int,
        itemId: String? = null
    ) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                ImageStorage.decodePhotoBitmap(context, fileName)
            }
            if (bmp != null) {
                activeCropSession = CropSession(
                    bitmaps = listOf(bmp),
                    currentIndex = 0,
                    target = target,
                    targetItemId = itemId,
                    targetPhotoIndex = photoIndex
                )
            } else {
                Toast.makeText(
                    context,
                    if (isEn) "Could not open photo for editing" else "Fotoğraf düzenleme için açılamadı",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun finishOrAdvanceCropSession(chosenBitmap: Bitmap, recycleChosen: Boolean) {
        val session = activeCropSession ?: return
        if (session.target == PhotoTarget.USER_BODY_PHOTO) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    ImageStorage.saveUserBodyPhotoBitmap(context, chosenBitmap)
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
                        isAddingOrEditing = true
                    }

                    PhotoTarget.APPEND_TO_EXISTING_ITEM -> {
                        if (targetItemId != null) {
                            repository.addPhotoPairsToItem(targetItemId, completedPairs)
                        }
                    }

                    PhotoTarget.EDIT_SINGLE_DRAFT_PHOTO -> {
                        val firstPair = completedPairs.firstOrNull()
                        if (firstPair != null && session.targetPhotoIndex >= 0) {
                            pendingDraftPhotoReplacement = session.targetPhotoIndex to firstPair
                        }
                        isAddingOrEditing = true
                    }

                    PhotoTarget.EDIT_SINGLE_EXISTING_ITEM_PHOTO -> {
                        val firstPair = completedPairs.firstOrNull()
                        if (targetItemId != null && firstPair != null && session.targetPhotoIndex >= 0) {
                            repository.replacePhotoPairAtIndex(
                                targetItemId,
                                session.targetPhotoIndex,
                                firstPair
                            )
                            Toast.makeText(
                                context,
                                if (isEn) "Photo updated" else "Fotoğraf güncellendi",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    PhotoTarget.USER_BODY_PHOTO,
                    PhotoTarget.USER_FACE_PHOTO,
                    PhotoTarget.USER_AVATAR_PHOTO,
                    PhotoTarget.CUSTOM_SHOWCASE_PHOTO -> Unit
                }
            }
        }
    }

    suspend fun applyAvatarBitmap(bmp: Bitmap) {
        val (fullUrl, thumbUrl) = withContext(Dispatchers.IO) {
            try {
                SocialProfileService.encodeAvatarPair(bmp)
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        val next = userProfile.copy(avatarBase64 = fullUrl, avatarThumbBase64 = thumbUrl)
        SocialProfileService.saveLocalProfile(context, next)
        userProfile = next
        AnalyticsService.updateProfileIdentity(context, next.username, next.displayName, thumbUrl)
        Toast.makeText(
            context,
            if (isEn) "Profile photo updated" else "Profil fotoğrafı güncellendi",
            Toast.LENGTH_SHORT
        ).show()
        scope.launch {
            SocialProfileService.saveAndSyncProfile(context, next, items, outfits)
                .onSuccess { synced -> userProfile = synced }
        }
    }

    val avatarGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageStorage.decodeOrientedBitmapFromUri(context, uri) }
            if (bmp != null) {
                applyAvatarBitmap(bmp)
            } else {
                Toast.makeText(context, if (isEn) "Could not open image" else "Görsel açılamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val customShowcaseGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val url = withContext(Dispatchers.IO) {
                val bmp = ImageStorage.decodeOrientedBitmapFromUri(context, uri) ?: return@withContext null
                try {
                    SocialProfileService.encodeBitmapForCloudGarment(bmp)
                } finally {
                    if (!bmp.isRecycled) bmp.recycle()
                }
            }
            if (!url.isNullOrBlank()) {
                pendingCustomShowcasePhotoUrl = url
            } else {
                Toast.makeText(context, if (isEn) "Could not open image" else "Görsel açılamadı", Toast.LENGTH_SHORT).show()
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

        if (activePhotoTarget == PhotoTarget.USER_AVATAR_PHOTO) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    ImageStorage.decodeOrientedBitmapFromFile(file).also { file.delete() }
                }
                if (bmp != null) {
                    applyAvatarBitmap(bmp)
                }
            }
        } else if (activePhotoTarget == PhotoTarget.USER_FACE_PHOTO) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    ImageStorage.saveUserFacePhotoFromFile(context, file)
                }
                if (ok) {
                    facePhotoVersion++
                    Toast.makeText(
                        context,
                        if (isEn) "Face photo saved" else "Yüz fotoğrafın kaydedildi",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else if (activePhotoTarget == PhotoTarget.USER_BODY_PHOTO) {
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
            } else {
                Toast.makeText(
                    context,
                    if (isEn) "Could not open image" else "Görsel açılamadı",
                    Toast.LENGTH_SHORT
                ).show()
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

    val faceGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ImageStorage.saveUserFacePhotoFromUri(context, uri)
            }
            if (ok) {
                facePhotoVersion++
                Toast.makeText(
                    context,
                    if (isEn) "Face photo loaded" else "Yüz fotoğrafın yüklendi",
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

    // Shared 3D Virtual Try-On dialog
    if (showTryOnDialog) {
        VirtualTryOn3DDialog(
            initialItems = tryOnInitialItems,
            allWardrobeItems = items,
            bodyPhotoVersion = bodyPhotoVersion,
            onPickBodyFromGallery = { bodyGalleryLauncher.launch("image/*") },
            onCaptureBodyFromCamera = { launchCameraFor(PhotoTarget.USER_BODY_PHOTO) },
            onOpenAiSettings = {
                showTryOnDialog = false
                showSettingsDialog = true
            },
            onDismiss = { showTryOnDialog = false }
        )
    }

    // Theme & Language & Gender & Studio Prompt Settings Dialog
    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog) {
            AnalyticsService.trackFeature("settings_open")
        }
    }
    if (showSettingsDialog) {
        ThemeSettingsDialog(
            currentTheme = themeMode,
            currentLanguage = lang,
            currentGender = savedGenderPref,
            inferredGender = inferredGender,
            genderUserSet = genderUserSet,
            isCheckingUpdate = isCheckingUpdate,
            analyticsEnabled = analyticsEnabled,
            onSelectTheme = { next ->
                AnalyticsService.trackFeature("theme_change", next.name)
                onChangeTheme(next)
            },
            onSelectLanguage = { next ->
                AnalyticsService.trackFeature("language_change", next.code)
                onChangeLanguage(next)
            },
            onSelectGender = { next, userSet ->
                AnalyticsService.trackFeature("gender_change", next.name)
                onChangeGender(next, userSet)
            },
            onToggleAnalytics = { enabled ->
                analyticsEnabled = enabled
                AnalyticsService.setEnabled(context, enabled)
            },
            onOpenPromptDialog = {
                showSettingsDialog = false
                showPromptDialog = true
            },
            onOpenReleaseNotesDialog = {
                showSettingsDialog = false
                showReleaseNotesDialog = true
            },
            onCheckForUpdate = { checkAppUpdateManually { showSettingsDialog = false } },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showPromptDialog) {
        StudioPromptDialog(
            onDismiss = { showPromptDialog = false }
        )
    }

    if (showReleaseNotesDialog) {
        ReleaseNotesDialog(
            currentLanguage = lang,
            onDismiss = { showReleaseNotesDialog = false }
        )
    }

    sharingItemTarget?.let { targetItem ->
        ShareGarmentDialog(
            item = targetItem,
            profile = userProfile,
            allItems = items,
            allOutfits = outfits,
            onProfileUpdated = { next -> userProfile = next },
            onOpenProfileSettings = {
                sharingItemTarget = null
                showProfileHub = true
            },
            onDismiss = { sharingItemTarget = null }
        )
    }

    activeSharedItemId?.let { sId ->
        SharedItemViewerDialog(
            shareId = sId,
            myProfile = userProfile,
            repository = repository,
            onOpenUserProfile = { uid ->
                activeSharedItemId = null
                activeOtherProfile = uid to false
            },
            onDismiss = { activeSharedItemId = null }
        )
    }

    sharingOutfitTarget?.let { req ->
        ShareOutfitDialog(
            title = req.title,
            pieces = req.items,
            harmonyScore = req.harmonyScore,
            weatherLabel = req.weatherLabel,
            profile = userProfile,
            allItems = items,
            allOutfits = outfits,
            onProfileUpdated = { next -> userProfile = next },
            onOpenProfileSettings = {
                sharingOutfitTarget = null
                showProfileHub = true
            },
            onDismiss = { sharingOutfitTarget = null }
        )
    }

    activeSharedOutfitId?.let { oId ->
        SharedOutfitViewerDialog(
            shareId = oId,
            myProfile = userProfile,
            repository = repository,
            onOpenUserProfile = { uid ->
                activeSharedOutfitId = null
                activeOtherProfile = uid to false
            },
            onDismiss = { activeSharedOutfitId = null }
        )
    }

    activeOtherProfile?.let { (uOrUid, isUsername) ->
        OtherProfileDialog(
            targetUidOrUsername = uOrUid,
            isUsername = isUsername,
            myProfile = userProfile,
            repository = repository,
            onSelectUser = { nextId, nextIsUser ->
                activeOtherProfile = nextId to nextIsUser
            },
            onDismiss = { activeOtherProfile = null }
        )
    }

    if (showProfileHub) {
        ProfileHubScreen(
            myProfile = userProfile,
            items = items,
            outfits = outfits,
            repository = repository,
            pendingCustomPhotoDataUrl = pendingCustomShowcasePhotoUrl,
            onConsumeCustomPhotoDataUrl = { pendingCustomShowcasePhotoUrl = null },
            onProfileUpdated = { next -> userProfile = next },
            onPickAvatarGallery = { avatarGalleryLauncher.launch("image/*") },
            onCaptureAvatarCamera = { launchCameraFor(PhotoTarget.USER_AVATAR_PHOTO) },
            onRemoveAvatar = {
                scope.launch {
                    val next = userProfile.copy(avatarBase64 = "", avatarThumbBase64 = "")
                    SocialProfileService.saveLocalProfile(context, next)
                    userProfile = next
                    AnalyticsService.updateProfileIdentity(context, next.username, next.displayName, "")
                    SocialProfileService.saveAndSyncProfile(context, next, items, outfits)
                        .onSuccess { userProfile = it }
                }
            },
            onRequestCustomShowcasePhoto = { customShowcaseGalleryLauncher.launch("image/*") },
            unreadActivityCount = pendingRequestsCount + unreadNotificationsCount,
            onOpenActivity = {
                ensureNotificationPermission()
                showActivityHeartDialog = true
            },
            onOpenDeepLink = { target ->
                showProfileHub = false
                handleIncomingTarget(target)
            },
            onDismiss = {
                showProfileHub = false
                scope.launch {
                    SocialProfileService.fetchPendingFollowRequests(context).onSuccess {
                        pendingRequestsCount = it.size
                    }
                }
            }
        )
    }

    if (showActivityHeartDialog) {
        ActivityHeartDialog(
            myProfile = userProfile,
            repository = repository,
            onOpenProfileUid = { uid -> activeOtherProfile = uid to false },
            onOpenPost = { pId, owner -> activeSocialPostTarget = pId to owner },
            onUnreadChanged = { unreadNotificationsCount = it },
            onDismiss = {
                showActivityHeartDialog = false
                scope.launch {
                    SocialProfileService.fetchPendingFollowRequests(context).onSuccess {
                        pendingRequestsCount = it.size
                    }
                }
            }
        )
    }

    activeSocialPostTarget?.let { (postId, postOwner) ->
        SinglePostDeepLinkDialog(
            postId = postId,
            ownerUid = postOwner,
            myProfile = userProfile,
            items = items,
            repository = repository,
            onOpenOtherProfile = { uid -> activeOtherProfile = uid to false },
            onDismiss = { activeSocialPostTarget = null }
        )
    }

    pendingProGate?.let { (featureKey, onContinue) ->
        RemoteProGateDialog(
            feature = featureKey,
            config = remoteConfig,
            lang = lang,
            onUnlocked = {
                remoteConfig = remoteConfig.copy(hasProUnlocked = true)
                pendingProGate = null
                onContinue()
            },
            onDismiss = { pendingProGate = null }
        )
    }

    availableUpdateInfo?.let { info ->
        AppUpdateDialog(
            updateInfo = info,
            onDismiss = { availableUpdateInfo = null }
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
            selectedItem != null -> selectedItemId = null
            activeOutfitDetail != null -> activeOutfitDetail = null
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
                        onRequestEditPhoto = { idx, fileName ->
                            openCropperForSavedPhoto(
                                fileName = fileName,
                                target = PhotoTarget.EDIT_SINGLE_DRAFT_PHOTO,
                                photoIndex = idx
                            )
                        },
                        pendingPhotoReplacement = pendingDraftPhotoReplacement,
                        onClearPendingPhotoReplacement = { pendingDraftPhotoReplacement = null },
                        onSave = { name, category, subCategory, styleTag, seasons, colors, hexes,
                                   studioPhotos, originalPhotos, useOrig, catalogPhotos, useCatalog, notes, isWishlist, priceText, productUrl, lastCheckedPrice, addAnother ->
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
                                    catalogPhotoFileNames = catalogPhotos,
                                    useCatalogPhotos = useCatalog,
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
                                    AnalyticsService.trackFeature("edit_item", category.name)
                                } else {
                                    repository.addItem(itemToSave)
                                    AnalyticsService.trackFeature("add_item", category.name)
                                }

                                pendingDraftPhotoReplacement = null
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
                        onCancel = {
                            pendingDraftPhotoReplacement = null
                            clearUnsavedBatchAndExit()
                        }
                    )
                }
            }

            selectedItem != null -> {
                val item = selectedItem
                val relatedOutfits = remember(item.id, outfits) {
                    outfits.filter { it.itemIds.contains(item.id) }
                }
                ItemDetailScreen(
                    item = item,
                    relatedOutfits = relatedOutfits,
                    isTransformingStudio = isTransformingStudio,
                    isRemovingBg = isRemovingBg,
                    onBack = { selectedItemId = null },
                    onEdit = {
                        editingItem = item
                        pendingDraftPhotos = emptyList()
                        pendingDraftPhotoReplacement = null
                        isAddingOrEditing = true
                    },
                    onShareItem = { sharingItemTarget = item },
                    onToggleFavorite = {
                        scope.launch { repository.toggleFavorite(item.id) }
                    },
                    onSelectRemoveBg = { photoIdx ->
                        applyOfflineRemoveBg(item, photoIdx)
                    },
                    onSelectOriginal = {
                        scope.launch { repository.setPhotoMode(item.id, useOriginal = true, useCatalog = false) }
                    },
                    onSelectOrCreateCatalog = { photoIdx ->
                        transformItemPhotoToEcommerce(item, photoIdx)
                    },
                    onMarkWorn = {
                        AnalyticsService.trackFeature("wear_mark", "item")
                        scope.launch { repository.markItemsWorn(listOf(item.id)) }
                    },
                    onUpdateWearCount = { newCount ->
                        AnalyticsService.trackFeature("wear_mark", "count")
                        scope.launch {
                            repository.setItemsWearCounts(mapOf(item.id to newCount))
                            Toast.makeText(
                                context,
                                if (isEn) "Wear count updated" else "Giyilme sayısı güncellendi",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onMoveToWardrobe = {
                        AnalyticsService.trackFeature("wishlist_move")
                        scope.launch {
                            repository.toggleWishlist(item.id, moveToWardrobe = true)
                            Toast.makeText(context, strings.movedToWardrobeMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCheckWishlistPrice = {
                        AnalyticsService.trackFeature("price_check")
                        checkSingleWishlistItemPrice(item)
                    },
                    onAddCameraPhoto = {
                        launchCameraFor(PhotoTarget.APPEND_TO_EXISTING_ITEM, item.id)
                    },
                    onAddGalleryPhotos = {
                        launchGalleryFor(PhotoTarget.APPEND_TO_EXISTING_ITEM, item.id)
                    },
                    onEditCurrentPhoto = { idx ->
                        val srcName = item.originalPhotoFileNames.getOrNull(idx)
                            ?: item.photoFileNames.getOrNull(idx)
                        if (srcName != null) {
                            AnalyticsService.trackFeature("crop_photo", "detail")
                            openCropperForSavedPhoto(
                                fileName = srcName,
                                target = PhotoTarget.EDIT_SINGLE_EXISTING_ITEM_PHOTO,
                                photoIndex = idx,
                                itemId = item.id
                            )
                        }
                    },
                    onSetCoverPhoto = { idx ->
                        scope.launch { repository.setCoverPhotoAtIndex(item.id, idx) }
                    },
                    onRemovePhoto = { idx ->
                        scope.launch { repository.removePhotoAtIndex(item.id, idx) }
                    },
                    onDeleteItem = {
                        val id = item.id
                        AnalyticsService.trackFeature("item_delete")
                        selectedItemId = null
                        scope.launch { repository.deleteItem(id) }
                    }
                )
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
                    onUpdateWearCounts = { updates ->
                        scope.launch {
                            repository.setItemsWearCounts(updates)
                            Toast.makeText(
                                context,
                                if (isEn) "Wear count updated" else "Giyilme sayısı güncellendi",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onOpenItem = { itemId -> selectedItemId = itemId },
                    onShareOutfit = { t, ps, sc -> sharingOutfitTarget = ShareOutfitRequest(t, ps, sc) },
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

            else -> {
                // Main 4-tab pager + floating bottom dock + Wardrobe FAB
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                            state = pagerState,
                            snapAnimationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 360,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            )
                        )
                    ) { page ->
                        when (page) {
                            0 -> WardrobeScreen(
                                items = items,
                                onItemClick = { selectedItemId = it.id },
                                onToggleFavorite = { id -> scope.launch { repository.toggleFavorite(id) } },
                                onShareItem = { sharingItemTarget = it },
                                onReorderItem = { fromId, toId ->
                                    scope.launch { repository.reorderItem(fromId, toId) }
                                },
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
                                    AnalyticsService.trackFeature("tryon_3d", "outfits")
                                    tryOnInitialItems = chosen
                                    showTryOnDialog = true
                                },
                                onOpenOutfitDetail = { title, pieceList, existingId ->
                                    AnalyticsService.trackFeature("outfit_builder")
                                    activeOutfitDetail = OutfitDetailState(title, pieceList, existingId)
                                },
                                onSaveOutfit = { title, pieceList, score ->
                                    AnalyticsService.trackFeature("outfit_save")
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
                                    AnalyticsService.trackFeature("wear_mark", "outfit")
                                    scope.launch {
                                        repository.markItemsWorn(worn.map { it.id })
                                        Toast.makeText(
                                            context,
                                            if (isEn) "Marked as worn" else "Giyildi olarak işaretlendi",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onUpdateWearCounts = { updates ->
                                    scope.launch {
                                        repository.setItemsWearCounts(updates)
                                        Toast.makeText(
                                            context,
                                            if (isEn) "Wear count updated" else "Giyilme sayısı güncellendi",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onDeleteOutfit = { id -> scope.launch { repository.deleteOutfit(id) } },
                                onShareOutfit = { t, ps, sc, wl -> sharingOutfitTarget = ShareOutfitRequest(t, ps, sc, wl) },
                                onOpenSettings = { showSettingsDialog = true },
                                onOpenProfile = { showProfileHub = true },
                                profileAvatarBase64 = userProfile.avatarThumbBase64.ifBlank { userProfile.avatarBase64 },
                                pendingRequestsCount = pendingRequestsCount,
                                unreadActivityCount = pendingRequestsCount + unreadNotificationsCount,
                                onOpenActivity = {
                                    ensureNotificationPermission()
                                    showActivityHeartDialog = true
                                },
                                genderPref = activeGender,
                                flatteringColors = flatteringColors,
                                onOpenItem = { itemId -> selectedItemId = itemId },
                                remoteConfig = remoteConfig,
                                onRequireFeature = ::runGatedFeature,
                                listState = outfitsListState
                            )

                            2 -> SocialFeedScreen(
                                items = items,
                                myProfile = userProfile,
                                repository = repository,
                                profileAvatarBase64 = userProfile.avatarThumbBase64.ifBlank { userProfile.avatarBase64 },
                                pendingRequestsCount = pendingRequestsCount,
                                unreadActivityCount = unreadNotificationsCount,
                                onOpenActivity = {
                                    ensureNotificationPermission()
                                    showActivityHeartDialog = true
                                },
                                onOpenProfile = { showProfileHub = true },
                                onOpenOtherProfile = { uid -> activeOtherProfile = uid to false },
                                onOpenSettings = { showSettingsDialog = true },
                                listState = socialListState
                            )

                            3 -> InspirationScreen(
                                onOpenSettings = { showSettingsDialog = true },
                                onOpenProfile = { showProfileHub = true },
                                profileAvatarBase64 = userProfile.avatarThumbBase64.ifBlank { userProfile.avatarBase64 },
                                pendingRequestsCount = pendingRequestsCount,
                                unreadActivityCount = pendingRequestsCount + unreadNotificationsCount,
                                onOpenActivity = {
                                    ensureNotificationPermission()
                                    showActivityHeartDialog = true
                                },
                                preferredGender = activeGender,
                                items = items,
                                facePhotoVersion = facePhotoVersion,
                                onPickFaceFromGallery = { faceGalleryLauncher.launch("image/*") },
                                onCaptureFaceFromCamera = { launchCameraFor(PhotoTarget.USER_FACE_PHOTO) },
                                likedLookIds = likedLookIds,
                                dislikedLookIds = dislikedLookIds,
                                visitNonce = inspirationVisitNonce,
                                onRecordLookSwipe = ::recordLookSwipe,
                                remoteConfig = remoteConfig,
                                onRequireFeature = ::runGatedFeature,
                                listState = inspirationListState
                            )

                            else -> PaletteStatsScreen(
                                items = items,
                                outfits = outfits,
                                onOpenSettings = { showSettingsDialog = true },
                                onOpenProfile = { showProfileHub = true },
                                profileAvatarBase64 = userProfile.avatarThumbBase64.ifBlank { userProfile.avatarBase64 },
                                pendingRequestsCount = pendingRequestsCount,
                                unreadActivityCount = pendingRequestsCount + unreadNotificationsCount,
                                onOpenActivity = {
                                    ensureNotificationPermission()
                                    showActivityHeartDialog = true
                                },
                                facePhotoVersion = facePhotoVersion,
                                genderPref = activeGender,
                                onPickBodyFromGallery = { bodyGalleryLauncher.launch("image/*") },
                                onCaptureBodyFromCamera = { launchCameraFor(PhotoTarget.USER_BODY_PHOTO) },
                                onOpenItem = { itemId -> selectedItemId = itemId },
                                remoteConfig = remoteConfig,
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
                                        socialListState = socialListState,
                                        inspirationListState = inspirationListState,
                                        summaryListState = summaryListState,
                                        savedScrollPositions = savedScrollPositions
                                    )
                                } else {
                                    pagerState.animateScrollToPage(
                                        page = targetPage,
                                        animationSpec = androidx.compose.animation.core.tween(
                                            durationMillis = 380,
                                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                                        )
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
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
    socialListState: LazyListState,
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
        2 -> socialListState
        3 -> inspirationListState
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

private val SocialCommunityIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SocialCommunity",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M12 3C6.9 3 3 6.58 3 11c0 2.02.82 3.87 2.18 5.28L4.1 20.1a.75.75 0 0 0 .97.92l3.94-1.44C9.96 19.86 10.96 20 12 20c5.1 0 9-3.58 9-8.5S17.1 3 12 3zm0 2c4.01 0 7 2.75 7 6.5S16.01 18 12 18c-.94 0-1.85-.14-2.71-.42l-.54-.17-.53.2-1.8.66.52-1.78.17-.57-.42-.42C5.63 14.37 5 12.78 5 11c0-3.32 3.05-6 7-6z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
        addPath(
            pathData = PathParser().parsePathString(
                "M12 7.4l1.15 2.75L15.9 11.3l-2.75 1.15L12 15.2l-1.15-2.75L8.1 11.3l2.75-1.15L12 7.4z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
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
        SocialCommunityIcon to strings.tabSocial,
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
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            val slotWidth = maxWidth / tabs.size
            val pillOffset by androidx.compose.animation.core.animateDpAsState(
                targetValue = slotWidth * currentPage.coerceIn(0, tabs.lastIndex),
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.84f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                ),
                label = "dockPillOffset"
            )
            val pillHeight = 42.dp
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(pillOffset))
                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .height(pillHeight)
                        .clip(RoundedCornerShape(999.dp))
                        .background(DressKitStyle.dockActiveBg())
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, (icon, desc) ->
                    val active = currentPage == index
                    val iconTint by androidx.compose.animation.animateColorAsState(
                        targetValue = if (active) DressKitStyle.dockActiveFg() else DressKitStyle.dockInactiveFg(),
                        animationSpec = androidx.compose.animation.core.tween(220),
                        label = "dockIconTint$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(pillHeight)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(
                                interactionSource = remember {
                                    androidx.compose.foundation.interaction.MutableInteractionSource()
                                },
                                indication = null
                            ) { onTabSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = desc,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSettingsDialog(
    currentTheme: AppThemeMode,
    currentLanguage: AppLanguage,
    currentGender: GenderPreference,
    inferredGender: GenderPreference,
    genderUserSet: Boolean,
    isCheckingUpdate: Boolean = false,
    analyticsEnabled: Boolean = true,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectGender: (GenderPreference, Boolean) -> Unit,
    onToggleAnalytics: (Boolean) -> Unit = {},
    onOpenPromptDialog: () -> Unit,
    onOpenReleaseNotesDialog: () -> Unit,
    onCheckForUpdate: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val strings = appStrings()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
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

                // Language Selection (6 Languages: TR, EN, DE, FR, ES, ZH)
                Text(
                    text = strings.languageLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = secondaryInk
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { langOption ->
                        val selected = currentLanguage == langOption
                        Surface(
                            modifier = Modifier.clickable { onSelectLanguage(langOption) },
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            color = if (selected) DressKitStyle.buttonBg() else DressKitStyle.secondarySurface(),
                            border = BorderStroke(
                                1.dp,
                                if (selected) DressKitStyle.buttonBg() else DressKitStyle.borderColor()
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
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

                // Gender Preference Selection (Male / Female / Unisex + Auto-inferred)
                Text(
                    text = localizedText(
                        currentLanguage,
                        "Tercih Edilen Cinsiyet",
                        "Preferred Gender",
                        "Bevorzugter Stil",
                        "Genre préféré",
                        "Género preferido",
                        "偏好风格"
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = secondaryInk
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GenderPreference.entries.forEach { option ->
                        val selected = genderUserSet && currentGender == option
                        Surface(
                            modifier = Modifier.clickable { onSelectGender(option, true) },
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            color = if (selected) DressKitStyle.buttonBg() else DressKitStyle.secondarySurface(),
                            border = BorderStroke(
                                1.dp,
                                if (selected) DressKitStyle.buttonBg() else DressKitStyle.borderColor()
                            )
                        ) {
                            Text(
                                text = option.label(currentLanguage),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) DressKitStyle.buttonFg() else primaryInk,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.clickable { onSelectGender(inferredGender, false) },
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        color = if (!genderUserSet) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                        border = BorderStroke(
                            1.dp,
                            if (!genderUserSet) DressKitStyle.accentColor() else DressKitStyle.borderColor()
                        )
                    ) {
                        Text(
                            text = localizedText(
                                currentLanguage,
                                "Otomatik (${inferredGender.label(currentLanguage)})",
                                "Auto (${inferredGender.label(currentLanguage)})",
                                "Auto (${inferredGender.label(currentLanguage)})",
                                "Auto (${inferredGender.label(currentLanguage)})",
                                "Auto (${inferredGender.label(currentLanguage)})",
                                "自动 (${inferredGender.label(currentLanguage)})"
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (!genderUserSet) DressKitStyle.accentColor() else primaryInk,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }

                // Generative AI Studio & 3D Try-On Configuration
                val settingsContext = LocalContext.current
                var hfTokenInput by remember {
                    mutableStateOf(AiStudioService.getHfToken(settingsContext))
                }
                var hasHfTokenSaved by remember {
                    mutableStateOf(AiStudioService.hasHfToken(settingsContext))
                }
                var geminiKeyInput by remember {
                    mutableStateOf(AiStudioService.getApiKey(settingsContext))
                }
                var hasGeminiKeySaved by remember {
                    mutableStateOf(AiStudioService.hasApiKey(settingsContext))
                }
                Surface(
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    color = DressKitStyle.secondarySurface(),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = localizedText(
                                currentLanguage,
                                "AI Stüdyo & 3D Kabin",
                                "AI Studio & 3D Try-On",
                                "KI-Studio & 3D-Anprobe",
                                "Studio IA & Cabine 3D",
                                "Estudio IA y 3D",
                                "AI 影棚与 3D 试衣间"
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = primaryInk
                        )
                        Text(
                            text = localizedText(
                                currentLanguage,
                                if (hasGeminiKeySaved || hasHfTokenSaved) {
                                    "Kişisel AI anahtarın aktif; katalog fotoğrafı ve 3D kabin yüksek limitle çalışır."
                                } else {
                                    "Ücretsiz bulut katalog fotoğrafı motoru anahtarsız çalışır. Günlük ücretsiz limiti artırmak için isteğe bağlı ücretsiz Hugging Face tokenı (hf_...) veya Gemini anahtarı ekleyebilirsin."
                                },
                                if (hasGeminiKeySaved || hasHfTokenSaved) {
                                    "Personal AI token active; catalog photos and 3D try-on run with extended quota."
                                } else {
                                    "Free cloud catalog studio works without any key. For higher daily limits, optionally add a free Hugging Face token (hf_...) or Gemini key."
                                },
                                "Kostenloser Katalog-KI-Dienst ist ohne Schlüssel aktiv. Optionalen Hugging-Face-Token oder Gemini-Key für höheres Limit eintragen.",
                                "Le studio catalogue IA gratuit fonctionne sans clé. Ajoutez un token Hugging Face gratuit ou une clé Gemini pour plus de quota.",
                                "El estudio de catálogo IA gratuito funciona sin clave. Añade un token gratuito de Hugging Face o clave Gemini si quieres mayor límite.",
                                "免费云端商品图生成无需密钥即可使用；如需更高每日额度，可填免费 Hugging Face Token 或 Gemini 密钥。"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryInk
                        )
                        OutlinedTextField(
                            value = hfTokenInput,
                            onValueChange = { hfTokenInput = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = {
                                Text(
                                    localizedText(
                                        currentLanguage,
                                        "Hugging Face Token (Ücretsiz · Opsiyonel)",
                                        "Hugging Face Token (Free · Optional)",
                                        "Hugging Face Token (Kostenlos)",
                                        "Token Hugging Face (Gratuit)",
                                        "Token Hugging Face (Gratis)",
                                        "Hugging Face 令牌（免费可选）"
                                    )
                                )
                            },
                            placeholder = { Text("hf_...") },
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
                            value = geminiKeyInput,
                            onValueChange = { geminiKeyInput = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = {
                                Text(
                                    localizedText(
                                        currentLanguage,
                                        "Gemini API Anahtarı (Opsiyonel)",
                                        "Gemini API Key (Optional)",
                                        "Gemini-API-Schlüssel (Optional)",
                                        "Clé API Gemini (Optionnelle)",
                                        "Clave API Gemini (Opcional)",
                                        "Gemini API 密钥（可选）"
                                    )
                                )
                            },
                            placeholder = { Text("AIza... / AQ....") },
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
                        TextButton(
                            onClick = {
                                SecurityGuards.openExternalUrlSafely(
                                    settingsContext,
                                    "https://huggingface.co/settings/tokens"
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = DressKitStyle.accentColor(),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = localizedText(
                                    currentLanguage,
                                    "Ücretsiz HF Token Al",
                                    "Get Free HF Token",
                                    "Kostenlosen HF-Token holen",
                                    "Obtenir un token HF gratuit",
                                    "Obtener token HF gratis",
                                    "免费获取 HF 令牌"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = DressKitStyle.accentColor()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cleanHf = hfTokenInput.trim()
                                    val cleanGem = geminiKeyInput.trim()
                                    if (cleanHf.isNotEmpty() && !AiStudioService.isValidHfToken(cleanHf)) {
                                        Toast.makeText(
                                            settingsContext,
                                            localizedText(
                                                currentLanguage,
                                                "Geçersiz Hugging Face token biçimi (hf_...)",
                                                "Invalid Hugging Face token format (hf_...)"
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@OutlinedButton
                                    }
                                    if (cleanGem.isNotEmpty() && !AiStudioService.isValidGeminiApiKey(cleanGem)) {
                                        Toast.makeText(
                                            settingsContext,
                                            localizedText(
                                                currentLanguage,
                                                "Geçersiz Gemini API anahtarı biçimi (AIza... veya AQ....)",
                                                "Invalid Gemini API key format (AIza... or AQ....)"
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@OutlinedButton
                                    }
                                    AiStudioService.setHfToken(settingsContext, cleanHf)
                                    AiStudioService.setApiKey(settingsContext, cleanGem)
                                    hasHfTokenSaved = AiStudioService.hasHfToken(settingsContext)
                                    hasGeminiKeySaved = AiStudioService.hasApiKey(settingsContext)
                                    Toast.makeText(
                                        settingsContext,
                                        if (hasHfTokenSaved || hasGeminiKeySaved) {
                                            localizedText(currentLanguage, "AI ayarları kaydedildi", "AI settings saved")
                                        } else {
                                            localizedText(currentLanguage, "AI anahtarları temizlendi", "AI keys cleared")
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                border = BorderStroke(1.dp, DressKitStyle.accentColor()),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.save, color = DressKitStyle.accentColor())
                            }
                            if (hasHfTokenSaved || hasGeminiKeySaved || hfTokenInput.isNotBlank() || geminiKeyInput.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        hfTokenInput = ""
                                        geminiKeyInput = ""
                                        AiStudioService.clearHfToken(settingsContext)
                                        AiStudioService.clearApiKey(settingsContext)
                                        hasHfTokenSaved = false
                                        hasGeminiKeySaved = false
                                    },
                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                ) {
                                    Text(
                                        localizedText(currentLanguage, "Temizle", "Clear"),
                                        color = secondaryInk
                                    )
                                }
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

                // Manual In-App Update Check Button
                OutlinedButton(
                    onClick = onCheckForUpdate,
                    enabled = !isCheckingUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = DressKitStyle.accentColor(),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCheckingUpdate) {
                            localizedText(
                                currentLanguage,
                                "Güncellemeler denetleniyor...",
                                "Checking for updates...",
                                "Suche nach Updates...",
                                "Recherche de mises à jour...",
                                "Buscando actualizaciones...",
                                "正在检查更新..."
                            )
                        } else {
                            localizedText(
                                currentLanguage,
                                "Güncellemeleri Denetle",
                                "Check for Updates",
                                "Nach Updates suchen",
                                "Vérifier les mises à jour",
                                "Buscar actualizaciones",
                                "检查更新"
                            )
                        },
                        color = primaryInk
                    )
                }

                // Release Notes Button
                OutlinedButton(
                    onClick = onOpenReleaseNotesDialog,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NewReleases,
                        contentDescription = null,
                        tint = primaryInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.releaseNotesBtn, color = primaryInk)
                }

                // GitHub Profile Button
                OutlinedButton(
                    onClick = {
                        AnalyticsService.trackFeature("open_github")
                        SecurityGuards.openExternalUrlSafely(
                            settingsContext,
                            "https://github.com/kyhlaer?tab=repositories"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = DressKitStyle.accentColor(),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GitHub · Kyhlaer", color = primaryInk)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = localizedText(
                            currentLanguage,
                            "Anonim kullanım istatistikleri",
                            "Anonymous usage analytics",
                            "Anonyme Nutzungsstatistik",
                            "Statistiques anonymes",
                            "Estadísticas anónimas",
                            "匿名使用统计"
                        ),
                        fontSize = 11.sp,
                        color = secondaryInk,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = analyticsEnabled,
                        onCheckedChange = onToggleAnalytics,
                        modifier = Modifier.scale(0.76f)
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = localizedText(
                            currentLanguage,
                            "Sürüm v1.43.271 · by Kyhlaer",
                            "Version v1.43.271 · by Kyhlaer",
                            "Version v1.43.271 · by Kyhlaer",
                            "Version v1.43.271 · by Kyhlaer",
                            "Versión v1.43.271 · by Kyhlaer",
                            "版本 v1.43.271 · by Kyhlaer"
                        ),
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

private data class ReleaseNoteVersion(
    val version: String,
    val badgeTr: String?,
    val badgeEn: String?,
    val bulletsTr: List<String>,
    val bulletsEn: List<String>
)

private val RELEASE_NOTES_HISTORY = listOf(
    ReleaseNoteVersion(
        version = "v1.43.271",
        badgeTr = "Güncel",
        badgeEn = "Current",
        bulletsTr = listOf(
            "Orta Sekme · Sosyal Akış: Alt menünün tam ortasına eklenen yenilenmiş Sosyal simgesinden kombinlerini, look paylaşımlarını, çoklu fotoğraf ve videolarını paylaşabilir; yorumlara kamera/galeri fotoğrafı, GIF çıkartması ve 15 dakika içinde düzenleme ekleyebilirsin.",
            "Kombin Linki Paylaşımı & Tek Tıkla Kaydetme: Önerilen, kayıtlı ve detay kombinlerini link olarak paylaşabilir, hem mobilden hem webden açıp beğeni bırakabilir veya tüm parçalarıyla dolabına aktarabilirsin.",
            "Engelleme, Temiz Üst Bar & Sadece Sosyal'de Profil: Kullanıcıları engelleyip engelleri profil ayarlarından yönetebilirsin. Üst bardaki Profil ve Kalp tuşları artık yalnızca Sosyal sekmesinde görünür; Ayarlar sadeleştirilip en alta GitHub bağlantısı eklendi.",
            "Şu Andan İtibaren Akan 24 Saatlik Hava Durumu: Bugünkü saatlik hava tahmini artık günün başından değil tam bulunduğun saatten başlayarak ertesi güne uzanan 24 saati gösterir.",
            "Gizli Hesap & Takip İstekleri Bildirimi: Gizli hesapların gönderileri yalnızca onaylı takipçilere açılır; takip istekleri cihaza bildirim düşürür."
        ),
        bulletsEn = listOf(
            "Centered Social Feed Tab & Unified Icon: Share outfits, looks, multi-photos, and short videos with camera/gallery photo & animated GIF comments, 15-minute caption editing, and deletion.",
            "Outfit Link Sharing & One-Click Closet Import: Share recommended, saved, or builder outfits as deep-links playable across Android and Web with full piece saving.",
            "User Blocking, Cleaner Headers & GitHub Shortcut: Block/unblock users and filter them cleanly everywhere, keep Profile & Activity Heart actions inside Social only, streamline Settings, and open Kyhlaer's GitHub right from Settings.",
            "Rolling 24-Hour Weather Forecast: Hourly weather on Today now begins right from the current hour and rolls smoothly into tomorrow.",
            "Private Follower Isolation & Notifications: Private-account posts stay restricted to accepted followers with instant follow-request device notifications."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.270",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "24 Saatlik Saat Saat Hava Durumu: Kombinler sekmesindeki hava durumu kartına dokunarak o günün saat saat sıcaklık, hissedilen, yağış ve rüzgar tahminini inceleyebilirsin.",
            "Kıyafet Linki Paylaşımı & Tek Tıkla İçe Aktarma: Dolabındaki herhangi bir kıyafeti arkadaşlarınla link olarak paylaşabilir, hem uygulamada hem webde açıp istek listene veya dolabına ekleyebilirsin.",
            "Herkese Açık / Gizli Profil & Takip Sistemi: Kullanıcı adı, görünen ad, biyografi ve profil fotoğrafı seç; istediğin istatistikleri, favorilerini, istek listeni veya özel seçtiğin parçaları sergile, arkadaşlarını arayıp takip et.",
            "Hızlı Kullanıcı Adı Oluşturucu & Ayarlar Profili: Link paylaşırken kullanıcı adın yoksa alttaki hızlı kutucukla anında oluştur; Ayarlar ve üst bardan profiline tek dokunuşla eriş."
        ),
        bulletsEn = listOf(
            "24-Hour Hourly Weather Forecast: Tap the weather card in Outfits to inspect hour-by-hour temperature, feels-like, rain probability, and wind.",
            "Garment Link Sharing & One-Tap Save: Share any wardrobe piece via link — openable on Android or Web with sharer identity, likes, and instant Wishlist/Closet import.",
            "Public / Private Profiles & Follow System: Set a username, display name, bio, and avatar; control which stats and showcase pieces (favorites, wishlist, selected, or custom uploads) are shared, search users, and follow friends.",
            "Quick Username Prompt & Profile Hub: Create your username inline when sharing links, and manage your public wardrobe profile from Settings or the top header icon."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.263",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Dolabım Sürükle-Bırak Ekran Kaydırma: Kıyafetleri basılı tutup ekranın üst veya alt kenarına doğru sürüklediğinde sayfa artık akıcı şekilde yukarı ve aşağı kayar; tüm dolabın içinde istediğin sıraya kolayca taşıyabilirsin."
        ),
        bulletsEn = listOf(
            "Wardrobe Drag Edge Auto-Scroll: Long-pressing and dragging a garment near the top or bottom edge now smoothly auto-scrolls your closet so you can reorder items across the full list."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.262",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Katalog AI Hata Mesajları Düzeltildi: Hugging Face ZeroGPU sunucu yoğunluğu veya günlük kota hatalarında yanlış çıkan 'İnternet bağlantını kontrol et' uyarısı düzeltildi.",
            "Kıyafet Düzenleme & Tek Fotoğraf Kırpma: 'Kıyafeti Düzenle -> Açı Ekle' akışında yeni ürün oluşturma hatası giderildi; ekleme ve detay ekranından fotoğrafları tek tek kırpma ('Fotoğrafı Düzenle') eklendi.",
            "Dolabım Sürükle-Bırak Sıralama & Rastgele İlham: Kıyafetleri basılı tutarak sıralama ve her açılışta yenilenen rastgele Kombin İlhamı sırası eklendi.",
            "Yumuşak Sekme Geçişi, Kalıcı Konum & Klavye Uyumu: Kayar alt menü hapı ve yumuşak geçişler, seçilen hava durumu konumunun hatırlanması ve Not alanında klavye açılınca ekranın yukarı kayması sağlandı."
        ),
        bulletsEn = listOf(
            "Catalog AI Error Clarity: Fixed false 'Check internet connection' warnings when Hugging Face ZeroGPU spaces report busy or daily quota errors.",
            "Angle Append & Per-Photo Crop: Fixed 'Edit Item -> Add Angle' creating a duplicate item and added single-photo re-cropping from both Edit and Detail views.",
            "Drag-and-Drop Wardrobe Reordering & Fresh Lookbook: Long-press drag reordering in Wardrobe and per-visit randomized Kombin İlhamı order.",
            "Smooth Dock Pill, Persistent Location & Keyboard Avoidance: Added sliding bottom-dock pill & transitions, persisted weather location across restarts, and smooth keyboard scrolling on Notes."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.261",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Arka Planı Kaldır & Katalog Ayrımı: Daha önce katalog fotoğrafına dönüştürülmüş parçalarda dahi 'Arka Planı Kaldır' tıklandığında katalog görüntüsü yerine saf arka planı kaldırılmış (bgrm_) kesime geçilir/yeniden üretilir; katalog görseli ayrı yuvada saklanır.",
            "Katalog Fotoğrafı Kullan Geçişi: Katalog fotoğrafı üretildikten sonra 'Katalog Fotoğrafı Oluştur' butonu 'Katalog Fotoğrafı Kullan' durumuna dönüşür; üç görünüm arasında AI kotası harcamadan anında geçiş yapılır.",
            "Fotoğraf Ekle & Açı Ekle Eşit Boyut: Kıyafet ekleme/düzenleme ve detay ekranlarındaki kamera ve galeri butonları birebir aynı yükseklik (48dp) ve eşit genişliğe getirildi."
        ),
        bulletsEn = listOf(
            "Separate Remove BG & Catalog Photos: Clicking 'Remove BG' even on legacy catalog-converted garments switches/regenerates a pure offline background-removed cutout (bgrm_) while keeping catalog renders in a separate slot (catalog_).",
            "'Use Catalog Photo' Toggle: Once created, the catalog button updates to 'Use Catalog Photo' so you can toggle between Remove BG, Original, and Catalog views instantaneously without re-running AI.",
            "Matched 'Add Photo' & 'Add Angle' Buttons: Equalized height (48dp), width, and single-line alignment on photo/angle action buttons."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.26",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Evrensel Görsel Format & Yüksek Çözünürlük Desteği: ImageStorage Android ImageDecoder (API 28+) ve tek geçişli güvenli önbellek yedeğine taşındı; HEIC, HEIF, AVIF, WebP, GIF, BMP, DNG/RAW ve çok yüksek megapikselli fotoğraflar tüm cihazlarda sorunsuz açılır.",
            "Hafif Akıllı Delta Yama (DPATCH1): v1.43.25 kullanıcıları tam APK yerine yalnızca değişen küçük ikili delta yamasını indirerek güncellenebilir."
        ),
        bulletsEn = listOf(
            "Universal Image Format & High-Res Decode: Upgraded ImageStorage to Android ImageDecoder (API 28+) with single-pass stream caching & OOM-safe downsampling so HEIC, HEIF, AVIF, WebP, GIF, BMP, and ultra-high-res photos open reliably.",
            "Lightweight Delta Update (DPATCH1): v1.43.25 installs update with a tiny binary patch instead of re-downloading the full APK."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.25",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Detaylı Filtrele Renk & Buton Düzeltmesi: Dolap boşken de 6 temel renk + 'Diğer Renkler (+N)' açılır düğmesi getirildi; alt bardaki 'Filtreleri Temizle' butonunun dar ekranlarda kesilmesi giderildi.",
            "Doğrudan Mağaza Arama Linkleri: Bershka (?gender=man/woman) ve Zara (&section=MAN/WOMAN) cinsiyet parametreleri düzeltildi; çoklu kombin sorguları temizlendi ve doğrudan Hepsiburada + Boyner aramaları eklendi.",
            "AndroidKeyStore & Tam Güvenlik Sertleştirmesi: API anahtarları ve HF token'ı AES-256-GCM ile şifrelendi; IPv6/IPv4 SSRF atlatma engelleri, depo URL doğrulaması, usesCleartextTraffic=false ve GitHub güvenlik korumaları devreye alındı.",
            "Hafif Akıllı Delta Yama (DPATCH1): v1.43.24 kullanıcıları tam APK yerine yalnızca değişen küçük ikili delta yamasını indirerek güncellenebilir."
        ),
        bulletsEn = listOf(
            "Filter Color Parity & Button Fix: Added 6 core colors + expandable 'Other Colors (+N)' toggle when empty and widened footer weight/padding so Clear Filters never clips.",
            "Direct Store Search Links: Fixed Bershka (?gender=man/woman) and Zara (&section=MAN/WOMAN) queries, added compound-phrase cleanup, and replaced broken Pull&Bear redirects with Hepsiburada and Boyner.",
            "Full Code & Repo Security Hardening: Encrypted API keys via AndroidKeyStore AES-256-GCM, enforced strict IPv6/IPv4 SSRF checks, validated persisted URLs, set usesCleartextTraffic=false, and enabled GitHub protections.",
            "Lightweight Delta Update (DPATCH1): v1.43.24 installs update with a tiny binary patch instead of re-downloading the full APK."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.24",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Resmi Katalog AI Model Zinciri (FLUX.2 / Qwen-2511 / FireRed-1.1): 4 adımlı bozulmaya yol açan eski modeller kaldırıldı; FLUX.2-dev, FLUX.2-klein-9B (Base), Qwen-Image-Edit-2511, FireRed-Image-Edit-1.1 ve FLUX.1-Kontext-Dev resmi modelleriyle ürünün baskı, renk ve açısını birebir koruyan stüdyo motoruna geçildi.",
            "Sıkı Ürün Kimliği & Açı Kilidi + Yüksek Çözünürlüklü Girdi: Arka/ön açı kilidi, tam baskı koruma promptu, doğal oranda kayıpsız yükleme ve akıllı çerçeve/kenar temizliği eklendi.",
            "İlk Canlı Akıllı Delta Yama (DPATCH1) Güncellemesi: v1.43.23 kullanıcıları tüm APK'yı sıfırdan indirmek yerine yalnızca değişen ikili yamayı indirerek güncellenebilir."
        ),
        bulletsEn = listOf(
            "Official Catalog AI Model Chain (FLUX.2 / Qwen-2511 / FireRed-1.1): Replaced 4-step distilled Spaces with official full-fidelity FLUX.2-dev, FLUX.2-klein-9B Base, Qwen-Image-Edit-2511, FireRed-Image-Edit-1.1, and FLUX.1-Kontext-Dev models.",
            "Strict Garment Identity & Viewing-Side Lock: Enforced same-side camera preservation, full-size print fidelity prompt, natural aspect-ratio 1024px uploads, and speckle-resistant 87% studio framing.",
            "First Live Binary Delta Patch (DPATCH1): v1.43.23 installs receive a lightweight binary patch instead of downloading the full APK again."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.23",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Arka Planı Kaldır (Anahtarsız) & Katalog Promptu: Kıyafet ekranındaki geçiş 'Arka Planı Kaldır' olarak yenilendi; cihaz içi kesim ve tam e-ticaret katalog promptu mobil/web arasında eşitlendi.",
            "Play Store Tarzı Akıllı Güncellemeler & Küçük APK: Native kütüphaneler sıkıştırılarak APK boyutu küçültüldü; sonraki sürümler için yalnızca değişen ikili parçaları indiren doğrulamalı delta yama (DPATCH1) motoru eklendi.",
            "Stil Keşfi & Web Deneyimi 1:1: Tam ekran Stil Keşfi, Web sürümünde tek parmak dikey kaydırma, yatay sekme kaydırma ve akıllı Geri tuşu navigasyonu."
        ),
        bulletsEn = listOf(
            "Offline Background Remover & Exact Catalog Prompt: Renamed toggle to 'Remove BG' and unified catalog studio prompt & error diagnostics across Mobile and Web.",
            "Smart Delta Updates & Compact APK: Compressed native libs and added cryptographic DPATCH1 binary delta patching for smaller incremental updates.",
            "1:1 Style Discovery & Web Navigation: Edge-to-edge Style Discovery polish on mobile plus smooth single-finger scroll, horizontal tab swipe, and back-button stack on Web."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.22",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "AI Katalog & Vertex Express Desteği: Ayarlar'da hem AIza... hem AQ.... (Google Cloud / Vertex AI Express) Gemini API anahtarlarıyla uyumlu katalog fotoğrafı ve 3D Kabin üretimi; anahtarsız ücretsiz motor ve opsiyonel HF tokenı korunur.",
            "Mobil & Web 1:1 Detaylı Filtrele Uyumu: Mobil 'Detaylı Filtrele' penceresi Web sürümüyle birebir aynı modal düzenine getirildi (sahip olunan renkler öncelikli, genişletilebilir tüm renkler ve Stil filtresi).",
            "Kapsamlı Güvenlik Altyapısı: Web PWA tarafında katı CSP, clickjacking koruması, AES-256-GCM şifreli anahtar deposu, SSRF/redirect ağ sınırı ve dosya sihirli bayt doğrulaması eklendi."
        ),
        bulletsEn = listOf(
            "AI Studio & Vertex Express Support: Settings now supports both AIza... and AQ.... Gemini/Vertex AI Express API keys for catalog flat-lay generation and 3D Try-On alongside the zero-key free engine.",
            "1:1 Mobile & Web Filter Modal Parity: Updated mobile 'Filter Wardrobe' dialog to match the Web modal layout with owned colors prioritized first, expandable palette toggle, and Style tags.",
            "End-to-End Web & App Security Hardening: Strict CSP, anti-framing guard, AES-256-GCM non-extractable keystore, SSRF fetch guards, and upload magic-byte verification."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.21",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Gerçek AI Katalog Fotoğrafı Motoru: Kıyafet fotoğraflarını sadece arka plan kesmek yerine yapay zeka ile kırışıklıkları düzeltilmiş, kolları ve formu simetrik hizalanmış, #FFFFFF stüdyo katalog fotoğrafına dönüştürür (anahtarsız ücretsiz çalışır).",
            "Sadeleştirilmiş Arayüz & Ayarlar: 'Stüdyo / Orijinal' geçişi ve 'Katalog Fotoğrafı Oluştur' düğmesi yenilendi; Ayarlar'a daha yüksek günlük limit için opsiyonel ücretsiz Hugging Face token desteği eklendi."
        ),
        bulletsEn = listOf(
            "Generative AI Catalog Photo Engine: Reconstructs real garment photos into steamed, symmetrically arranged flat-lay catalog shots on pure #FFFFFF studio backgrounds with zero API key required.",
            "Clean Studio UI & Settings: Simplified 'Studio / Original' switcher and 'Create Catalog Photo' action, plus optional free Hugging Face token in Settings for extended daily quota."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.2",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "E-Ticaret Stüdyo Flat-Lay Dönüştürücü: Kıyafet fotoğraflarındaki askıları, elleri ve masa arka planını kaldırır; kırışıklık gölgelerini yumuşatıp stüdyo ışığı dengeler ve #FFFFFF 1:1 kare ürün fotoğrafı oluşturur (Kıyafet Ekle & Detay ekranında tek dokunuş + Ayarlar'da opsiyonel Gemini Nano Banana 2.1 AI desteği).",
            "3D Sanal Kabin (Vücut Entegrasyonlu): Eski 2D Kabin tamamen yenilendi! Kendi fotoğrafındaki yüz/omuz/bel/vücut silüetini analiz ederek kıyafetleri üzerine oturtur; 3D silindirik kumaş kıvrımı, ışık & ten sıcaklığı uyumu ve AI Gerçekçi Giydirme sunar."
        ),
        bulletsEn = listOf(
            "E-Commerce Studio Flat-Lay Transformer: Strips hangers, hands & cluttered backgrounds, softens harsh fold shadows with commercial studio lighting, and frames centered on pure #FFFFFF 1:1 canvas (plus optional Gemini Nano Banana 2.1 key in Settings).",
            "3D Virtual Fitting Room (Body-Integrated): Upgraded 2D Kabin to 3D Kabin! Segments body contours & facial landmarks to auto-fit and wrap garments in 3D cylindrical mesh with lighting matching, plus optional generative AI photorealistic try-on."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43.1",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Yeni Minimalist Askı Logosu: Beyaz zemin üzerine net siyah askı ikonu ve yeni açılış logosu eklendi.",
            "İstek Listesi Fiyat Çekme Düzeltmesi: Gzip sıkıştırma, 6 yönlendirme, kısa/Türkçe link normalizasyonu, JSON-LD şeması, iç içe mağaza JSON'u ve Inditex kuruş dönüştürme desteğiyle linklerden fiyat çekme iyileştirildi.",
            "Mağaza Arama Düzeltmesi: Zara, Bershka, Pull&Bear, H&M, Koton, DeFacto, LCW, Trendyol ve Google Alışveriş aramaları doğrudan Türkçe mağaza sorgularıyla sadeleştirilip iyileştirildi.",
            "Uygulama İçi Otomatik Güncelleyici: Yeni sürüm geldiğinde ekranda sorar, APK'yı güvenli indirir & imza doğrulamasıyla kurar; 'Sonra' veya özel saat/gün süreli 'Hatırlat' zamanlayıcısı sunar.",
            "Çince (中文) Dil Desteği: Türkçe, İngilizce, Almanca, Fransızca ve İspanyolca'nın yanına Çince (中文) eklendi.",
            "Güvenlik Sıkılaştırması: SSRF & güvensiz yönlendirme korumalı SecurityGuards, imza doğrulamalı güncelleme, atomik 0600 dosya kaydı ve yüz fotoğrafı yedekleme koruması.",
            "Bağımsız Yüz & 2D Kabin Fotoğrafları: Yüz & Ten Rengi fotoğrafı (user_face_photo.jpg) ile 2D Kabin boy fotoğrafı (user_body_photo.jpg) tamamen ayrıldı; biri değişince diğeri etkilenmez.",
            "Dar Ekran Düğme Düzeltmesi: Yüz analizi kartındaki kırpılan 'Fotoğrafı' yazısı düzeltilip 'Galeri / Değiştir' ve yüz küçük önizlemesi eklendi.",
            "+16 Yeni Renk Tonu & Genişletilmiş Renk Önerileri: Şampanya, Kum, Çikolata, Petrol, Zümrüt, Orman, Hardal, Kiremit, Mercan, Şarap, Gül Kurusu, Lavanta, Mürdüm dahil 44 renk ve 12+ ten alt tonu rengi.",
            "Hiç Bitmeyen Eksik Temel Parçalar & Gardırop Detoksu: Ekledikçe 3 kademeli yeni mantıklı parça önerileri üretir; 0 kez giyilen parçalar için 'Gardırop Detoksu' kartı eklendi.",
            "Yeni Akıllı Bavul Hazırlayıcı: Gideceğin şehri yaz/seç → o şehrin gerçek hava durumunu çek → 2–7 gün için gün gün renk uyumlu kombinler + otomatik bavul listesi.",
            "82 Kombin İlhamı + Tinder Tarzı 'Stil Keşfi': Erkek (41) ve Kadın (41) modern kombinler, sağa/sola kaydırmalı 'Beğendiklerim ❤️ / Geçtiklerim ✕' keşfi ve '🎲 Sürpriz' kombin üretici."
        ),
        bulletsEn = listOf(
            "New Minimalist Hanger Logo: Replaced launcher icon and splash mark with clean black hanger on pure white.",
            "Wishlist Price Fetch Fix: Added gzip decoding, up to 6 redirects, Turkish/share-sheet URL normalization, JSON-LD parser, nested store JSON & Inditex cents conversion.",
            "Fixed Store Search Links: Updated Zara, Bershka, Pull&Bear, H&M, Koton, DeFacto, LCW, Trendyol & Google Shopping with cleaner Turkish product queries.",
            "In-App Self-Updater & Snooze Timer: Prompts when a new release is available, verifies signature & integrity, installs seamlessly, or reminds after a custom hours/days timer.",
            "Chinese (中文) Language Support: Added Simplified Chinese alongside Turkish, English, German, French, and Spanish.",
            "Security Hardening: Strict HTTPS/SSRF SecurityGuards, signed APK verification, atomic 0600 file writes, and face photo backup exclusion.",
            "Separated Face & 2D Try-On Photos: Face tone photo (user_face_photo.jpg) and 2D Try-On body photo (user_body_photo.jpg) are now completely independent.",
            "Compact Button Fix: Fixed truncated button labels on narrow phones ('Gallery / Change') and added a circular face preview.",
            "+16 Rich Shades (44 Colors Total): Champagne, Sand, Chocolate, Teal, Emerald, Forest, Rust, Coral, Wine, Lavender, Plum, and expanded skin & palette color picks.",
            "Never-Ending Capsule Suggestions & Wardrobe Detox: Dynamic 3-tier recommendations that keep adapting + 'Wardrobe Detox' card for unworn items.",
            "Rebuilt Smart Suitcase Planner: Search your destination city -> fetch live destination weather -> auto-generate day-by-day outfits & packing checklist.",
            "82 Lookbook Outfits + Swipe Discovery: 41 Men + 41 Women modern looks, Tinder-style right/left 'Style Discovery' deck, and '🎲 Surprise' outfit button."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.43",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Yüz & Ten Rengi Uyum Analizi: Boy/selfie fotoğrafından yüz ve ten alt tonunu analiz edip en çok yakışacak renk paletini gösterir.",
            "Cinsiyet Tercihi & Otomatik Tahmin: Yüklenen kıyafetlerden cinsiyeti otomatik tahmin eder; Ayarlar'dan Erkek / Kadın / Unisex seçilebilir.",
            "Kombin → Parça Detayı Geçişi: Kombinler veya Kombin Detayı içindeki herhangi bir parçaya tıklayınca ürün detayını açar, geri basınca önceki ekrana döner.",
            "Kombin İlhamı Alışveriş Linkleri & Sıra Güncellemesi: Kadın ilhamda 4→1, 5→2 sıralandı; kombin kartlarında Google Alışveriş, Trendyol, Zara, Pull&Bear, Bershka, H&M, Koton, DeFacto, LCW araması eklendi.",
            "Genişletilmiş Eksik Temel Parçalar: 12+ temel parça kontrol edilir; tıklayınca renk önerisi ve mağaza linkleri açılır.",
            "5 Dil Desteği: Türkçe ve İngilizce'nin yanına Almanca (DE), Fransızca (FR) ve İspanyolca (ES) eklendi.",
            "2D Sanal Kabin Güncellemesi: AI Destekli 3D Sanal Kabin Çok Yakında duyurusu eklendi; Giydim buton hizalaması düzeltildi."
        ),
        bulletsEn = listOf(
            "Face & Skin Tone Color Analysis: Analyzes uploaded user photo undertone and highlights most flattering clothing colors.",
            "Gender Preference & Auto-Inference: Auto-infers wardrobe style or lets you choose Men / Women / Unisex in Settings.",
            "Tap Piece from Outfits: Clicking any garment on Outfits opens its detail view and returns straight back on Back.",
            "Lookbook Shopping Search & Reordering: Women's lookbook reordered (4→1, 5→2) with Google Shopping, Trendyol, Zara, Pull&Bear, Bershka, H&M, Koton, DeFacto & LCW search.",
            "Expanded Missing Capsule Essentials: Checks 12+ essentials with clickable shopping recommendations and store links.",
            "5 Languages: Added German (DE), French (FR), and Spanish (ES) alongside Turkish & English.",
            "2D Try-On Banner & Alignment: Added 'AI-Powered 3D Virtual Fitting Room Coming Soon' banner and fixed button alignment."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.42",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Yeni Uygulama Logosu: Minimalist askı ve turuncu-kırmızı noktalı logo, adaptive ikon ve açılış ekranı olarak eklendi.",
            "Modern (Koyu) Tema: Modern (Açık) ve Sokak Tarzı temalarının yanına sıcak terracotta vurgulu 3. tema eklendi.",
            "İstek Listesi Fiyat Takibi: Ürün linki (https://) ve Linkten Fiyat Çek ile arka plan fiyat kontrolü & indirim bildirimi eklendi.",
            "Kombin İlhamı (Erkek & Kadın · 56 Kombin): Erkek / Kadın cinsiyet seçimi ve kadın sokak stiline özel 28, toplam 56 kombin eklendi.",
            "Akıllı Bavul Hazırlayıcı: Kombinler sekmesinde seyahat süresi ve hava durumuna göre kapsül bavul listesi aracı eklendi.",
            "İstek Listesi Bütçe Özeti: Dolap Özeti ekranında tahmini toplam bütçe, takipli link sayısı ve indirim adedi gösteriliyor.",
            "Ayarlar içi Sürüm Notları: Tüm sürüm yeniliklerini uygulama içinden görüntüleme eklendi."
        ),
        bulletsEn = listOf(
            "New App Logo: Minimalist hanger with orange-red accent dot added to adaptive icon and splash screen.",
            "Modern (Dark) Theme: Added a 3rd theme with warm terracotta accents alongside Modern (Light) and Street Style.",
            "Wishlist Price Tracking: Added HTTPS product links, instant price fetch, and background price-drop notifications.",
            "Expanded Lookbook (Men & Women · 56 Outfits): Added Men/Women gender switcher with 28 curated women's & 28 men's street style looks.",
            "Smart Travel Packing Assistant: Generates weather-aware capsule packing checklists on the Outfits tab.",
            "Wishlist Budget Summary: Shows total estimated wishlist budget, tracked links, and active price drops in Summary.",
            "In-App Release Notes: Added release notes viewer inside Appearance & Settings."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.41",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Kalın Plus Jakarta Sans başlık tipografisi, klasik renk spektrum çubuğu ve kıyafet türleri kartı geri getirildi.",
            "Geri tuşunda uygulamadan çıkış onay penceresi ve Ayarlar altında sürüm bilgisi eklendi.",
            "Coil bellek/disk önbellekleme, yalnızca HTTPS ağ trafiği, allowBackup=\"false\", path traversal ve tapjacking korumaları eklendi."
        ),
        bulletsEn = listOf(
            "Restored bold Plus Jakarta Sans display typography, classic color spectrum bar, and piece breakdown chips.",
            "Added back-press exit confirmation dialog and version attribution in Settings.",
            "Added Coil caching, HTTPS-only network policy, allowBackup=\"false\", path traversal, and tapjacking protections."
        )
    ),
    ReleaseNoteVersion(
        version = "v1.4",
        badgeTr = null,
        badgeEn = null,
        bulletsTr = listOf(
            "Koyu Sokak Tarzı teması, Türkçe / English dil desteği ve Stüdyo Fotoğraf Promptu aracı eklendi.",
            "Çoklu galeri seçimi ile arka arkaya hızlı kıyafet ekleme akışı eklendi.",
            "Kombin Detayı düzenleyici, yakınlaştırmalı 2D Sanal Kabin ve fiyatlı İstek Listesi eklendi."
        ),
        bulletsEn = listOf(
            "Added dark Street Style theme, Turkish / English localization, and Studio Photo Prompt helper.",
            "Added sequential multi-photo gallery import for adding multiple pieces in one flow.",
            "Added live Outfit Builder, pinch-to-zoom 2D Virtual Try-On, and Wishlist with pricing."
        )
    )
)

/**
 * Displays structured per-version release notes in the active language inside Settings.
 */
@Composable
private fun ReleaseNotesDialog(
    currentLanguage: AppLanguage,
    onDismiss: () -> Unit
) {
    val strings = appStrings()
    val isEn = currentLanguage != AppLanguage.TR
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DressKitStyle.cardSurface(),
        titleContentColor = primaryInk,
        textContentColor = secondaryInk,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.NewReleases,
                    contentDescription = null,
                    tint = DressKitStyle.accentColor(),
                    modifier = Modifier.size(22.dp)
                )
                Text(strings.releaseNotesTitle)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RELEASE_NOTES_HISTORY.forEach { entry ->
                    val badge = if (isEn) entry.badgeEn else entry.badgeTr
                    val bullets = if (isEn) entry.bulletsEn else entry.bulletsTr
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        color = DressKitStyle.secondarySurface(),
                        border = BorderStroke(
                            width = if (badge != null) 1.25.dp else 1.dp,
                            color = if (badge != null) DressKitStyle.accentColor() else DressKitStyle.borderColor()
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.version,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = primaryInk
                                )
                                if (badge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = DressKitStyle.accentSoft()
                                    ) {
                                        Text(
                                            text = badge,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = DressKitStyle.accentColor(),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            bullets.forEach { line ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DressKitStyle.accentColor()
                                    )
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = primaryInk
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DressKitStyle.buttonBg(),
                    contentColor = DressKitStyle.buttonFg()
                )
            ) {
                Text(strings.ok)
            }
        }
    )
}
