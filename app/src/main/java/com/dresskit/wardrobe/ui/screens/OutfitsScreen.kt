package com.dresskit.wardrobe.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.dresskit.wardrobe.data.AnalyticsService
import com.dresskit.wardrobe.data.AppFeatureKey
import com.dresskit.wardrobe.data.DailyForecast
import com.dresskit.wardrobe.data.DestinationPlace
import com.dresskit.wardrobe.data.HourlyForecast
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.OutfitRecommendationEngine
import com.dresskit.wardrobe.data.RemoteConfigState
import com.dresskit.wardrobe.data.TurkishCity
import com.dresskit.wardrobe.data.WeatherCondition
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.GenderPreference
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.components.FilterChipPill
import com.dresskit.wardrobe.ui.components.HarmonyScoreBadge
import com.dresskit.wardrobe.ui.components.RemoteAdSlotBanner
import com.dresskit.wardrobe.ui.components.WearActionButton
import com.dresskit.wardrobe.ui.components.WearCountEditorDialog
import com.dresskit.wardrobe.ui.components.bouncyOverscroll
import com.dresskit.wardrobe.ui.theme.DressKitColors
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitsScreen(
    items: List<ClothingItem>,
    savedOutfits: List<Outfit>,
    onOpenTryOn2D: (List<ClothingItem>) -> Unit,
    onOpenOutfitDetail: (title: String, items: List<ClothingItem>, existingOutfitId: String?) -> Unit,
    onSaveOutfit: (title: String, items: List<ClothingItem>, harmonyScore: Int) -> Unit,
    onMarkOutfitWorn: (List<ClothingItem>) -> Unit,
    onUpdateWearCounts: (Map<String, Int>) -> Unit = {},
    onDeleteOutfit: (String) -> Unit,
    onShareOutfit: (title: String, items: List<ClothingItem>, harmonyScore: Int, weatherLabel: String) -> Unit = { _, _, _, _ -> },
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit = {},
    profileAvatarBase64: String = "",
    pendingRequestsCount: Int = 0,
    unreadActivityCount: Int = 0,
    onOpenActivity: () -> Unit = {},
    genderPref: GenderPreference = GenderPreference.UNISEX,
    flatteringColors: Set<GarmentColor> = emptySet(),
    onOpenItem: (String) -> Unit = {},
    remoteConfig: RemoteConfigState = RemoteConfigState(),
    onRequireFeature: (AppFeatureKey, () -> Unit) -> Unit = { _, run -> run() },
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val isEn = lang != AppLanguage.TR
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val scope = rememberCoroutineScope()

    val closetItems = remember(items) { items.filter { !it.isWishlist } }

    val weatherPrefs = remember(context) {
        context.applicationContext.getSharedPreferences("dolabim_prefs", android.content.Context.MODE_PRIVATE)
    }
    var selectedCity by remember {
        mutableStateOf(
            runCatching {
                val savedId = weatherPrefs.getString("weather_city_id", null)
                if (!savedId.isNullOrBlank()) TurkishCity.valueOf(savedId) else TurkishCity.ISTANBUL
            }.getOrDefault(TurkishCity.ISTANBUL)
        )
    }
    var customLocationLabel by remember {
        mutableStateOf(weatherPrefs.getString("weather_custom_label", null)?.takeIf { it.isNotBlank() })
    }
    var customLocationLat by remember {
        val raw = weatherPrefs.getString("weather_custom_lat", null)?.toDoubleOrNull()
        mutableStateOf(raw?.takeIf { !it.isNaN() && it in -90.0..90.0 })
    }
    var customLocationLon by remember {
        val raw = weatherPrefs.getString("weather_custom_lon", null)?.toDoubleOrNull()
        mutableStateOf(raw?.takeIf { !it.isNaN() && it in -180.0..180.0 })
    }
    fun saveWeatherLocationPrefs(
        city: TurkishCity = selectedCity,
        label: String? = customLocationLabel,
        lat: Double? = customLocationLat,
        lon: Double? = customLocationLon
    ) {
        weatherPrefs.edit().apply {
            putString("weather_city_id", city.name)
            if (!label.isNullOrBlank() && lat != null && lon != null) {
                putString("weather_custom_label", label)
                putString("weather_custom_lat", lat.toString())
                putString("weather_custom_lon", lon.toString())
            } else {
                remove("weather_custom_label")
                remove("weather_custom_lat")
                remove("weather_custom_lon")
            }
            apply()
        }
    }
    var cityMenuExpanded by remember { mutableStateOf(false) }

    var forecastDays by remember {
        mutableStateOf(OutfitRecommendationEngine.generateFallbackWeeklyForecast(lang))
    }
    var selectedDayIndex by rememberSaveable { mutableIntStateOf(0) }
    var manualWeatherOverride by rememberSaveable { mutableStateOf<WeatherCondition?>(null) }
    var locatingWeather by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var showCreatorSheet by remember { mutableStateOf(false) }
    var showTravelSheet by remember { mutableStateOf(false) }
    var showHourlyWeatherDialog by remember { mutableStateOf(false) }
    var wearEditorTargetItems by remember { mutableStateOf<List<ClothingItem>?>(null) }

    wearEditorTargetItems?.let { targetItems ->
        WearCountEditorDialog(
            itemsToEdit = targetItems,
            onSave = { updates ->
                onUpdateWearCounts(updates)
                wearEditorTargetItems = null
            },
            onDismiss = { wearEditorTargetItems = null }
        )
    }

    val recentSignatures = remember { mutableStateListOf<String>() }
    val lastPrimaryIds = remember { mutableSetOf<String>() }

    fun triggerLocationDetect(hasPermission: Boolean) {
        scope.launch {
            locatingWeather = true
            val result = OutfitRecommendationEngine.detectUserLocationAndForecast(
                context = context,
                hasLocationPermission = hasPermission,
                lang = lang
            )
            if (result != null) {
                customLocationLabel = result.label
                customLocationLat = result.lat
                customLocationLon = result.lon
                forecastDays = result.days
                manualWeatherOverride = null
                selectedDayIndex = 0
                saveWeatherLocationPrefs(
                    city = selectedCity,
                    label = result.label,
                    lat = result.lat,
                    lon = result.lon
                )
            }
            locatingWeather = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        triggerLocationDetect(granted)
    }

    // Load forecast on persisted location, city selection, or language switch without re-running GPS/IP
    LaunchedEffect(selectedCity, customLocationLabel, customLocationLat, customLocationLon, lang) {
        val savedLabel = customLocationLabel
        val savedLat = customLocationLat
        val savedLon = customLocationLon
        if (savedLabel != null && savedLat != null && savedLon != null) {
            forecastDays = OutfitRecommendationEngine.generateFallbackWeeklyForecast(lang)
            val live = OutfitRecommendationEngine.fetchWeeklyForecastForCoords(savedLat, savedLon, lang)
            if (!live.isNullOrEmpty()) {
                forecastDays = live
            }
        } else {
            val fallback = OutfitRecommendationEngine.generateFallbackWeeklyForecast(lang)
            forecastDays = fallback
            val live = OutfitRecommendationEngine.fetchWeeklyForecast(selectedCity, lang)
            if (!live.isNullOrEmpty()) {
                forecastDays = live
            }
        }
    }

    val activeDayForecast: DailyForecast = forecastDays.getOrElse(selectedDayIndex) {
        forecastDays.first()
    }
    val activeCondition: WeatherCondition = manualWeatherOverride ?: activeDayForecast.condition

    if (showHourlyWeatherDialog) {
        HourlyWeatherDialog(
            locationLabel = customLocationLabel ?: selectedCity.label,
            day = activeDayForecast,
            lang = lang,
            onDismiss = { showHourlyWeatherDialog = false }
        )
    }

    val suggestions = remember(
        closetItems,
        activeCondition,
        activeDayForecast.label,
        refreshTrigger,
        lang,
        genderPref,
        flatteringColors
    ) {
        OutfitRecommendationEngine.suggestOutfits(
            items = closetItems,
            weather = activeCondition,
            targetDayLabel = activeDayForecast.label,
            recentSignatures = recentSignatures,
            lastUsedPrimaryIds = lastPrimaryIds,
            count = 3,
            lang = lang,
            genderPref = genderPref,
            flatteringColors = flatteringColors
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DressKitStyle.canvasBg())
    ) {
        // Dedicated clean header + dedicated full-width CTA row (never squishes/clips text)
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
                        text = strings.tabOutfits,
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = strings.outfitsSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onRequireFeature(AppFeatureKey.TRY_ON_2D) {
                            onOpenTryOn2D(emptyList())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DressKitStyle.cardSurface(),
                        contentColor = primaryInk
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessibilityNew,
                        contentDescription = null,
                        tint = DressKitStyle.accentColor(),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = strings.tryOn2D,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        onRequireFeature(AppFeatureKey.SMART_TRAVEL) {
                            showTravelSheet = true
                        }
                    },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DressKitStyle.cardSurface(),
                        contentColor = primaryInk
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Luggage,
                        contentDescription = null,
                        tint = DressKitStyle.accentColor(),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isEn) "Pack" else "Bavul",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { showCreatorSheet = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DressKitStyle.buttonBg(),
                        contentColor = DressKitStyle.buttonFg()
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.create,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (remoteConfig.isBannerVisible("outfits_top")) {
                    item {
                        RemoteAdSlotBanner(
                            slot = "outfits_top",
                            config = remoteConfig,
                            lang = lang
                        )
                    }
                }
                // Weather & 7-Day Planner Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            AnalyticsService.trackFeature("weather_hourly")
                                            showHourlyWeatherDialog = true
                                        }
                                        .padding(vertical = 2.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = activeCondition.emoji,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${activeCondition.displayLabel(lang)} (${activeDayForecast.maxTempC}°C)",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = primaryInk,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = DressKitStyle.accentSoft()
                                            ) {
                                                Text(
                                                    text = if (isEn) "24h ▸" else "24 Saat ▸",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = DressKitStyle.accentColor(),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = activeCondition.displayHint(lang),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Box {
                                    Surface(
                                        modifier = Modifier.clickable { cityMenuExpanded = true },
                                        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                        color = DressKitStyle.secondarySurface(),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.LocationOn,
                                                contentDescription = null,
                                                tint = DressKitStyle.accentColor(),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = customLocationLabel ?: selectedCity.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = primaryInk,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = cityMenuExpanded,
                                        onDismissRequest = { cityMenuExpanded = false },
                                        containerColor = DressKitStyle.cardSurface()
                                    ) {
                                        TurkishCity.entries.forEach { city ->
                                            DropdownMenuItem(
                                                text = { Text(city.label, color = primaryInk) },
                                                onClick = {
                                                    customLocationLabel = null
                                                    customLocationLat = null
                                                    customLocationLon = null
                                                    selectedCity = city
                                                    manualWeatherOverride = null
                                                    saveWeatherLocationPrefs(
                                                        city = city,
                                                        label = null,
                                                        lat = null,
                                                        lon = null
                                                    )
                                                    cityMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Auto-Locate GPS/IP chip + Day forecast strip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.clickable(enabled = !locatingWeather) {
                                        val fine = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        val coarse = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (fine || coarse) {
                                            triggerLocationDetect(true)
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                    color = DressKitStyle.accentSoft()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (locatingWeather) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                strokeWidth = 2.dp,
                                                color = DressKitStyle.accentColor()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.MyLocation,
                                                contentDescription = null,
                                                tint = DressKitStyle.accentColor(),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = if (isEn) "Locate Me" else "Konumum",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = DressKitStyle.accentColor()
                                        )
                                    }
                                }

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    itemsIndexed(forecastDays) { idx, day ->
                                        val selected = selectedDayIndex == idx
                                        Surface(
                                            modifier = Modifier.clickable {
                                                selectedDayIndex = idx
                                                manualWeatherOverride = null
                                            },
                                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                            color = if (selected) DressKitStyle.buttonBg() else DressKitStyle.secondarySurface()
                                        ) {
                                            Text(
                                                text = "${day.condition.emoji} ${day.label} ${day.maxTempC}°",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) DressKitStyle.buttonFg() else primaryInk,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            // Manual Weather Condition Override row
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(WeatherCondition.entries) { cond ->
                                    FilterChipPill(
                                        label = "${cond.emoji} ${cond.displayLabel(lang)}",
                                        selected = activeCondition == cond,
                                        onClick = {
                                            manualWeatherOverride = if (manualWeatherOverride == cond) null else cond
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Suggestions Header + Shuffle
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${activeDayForecast.label} ${strings.suggestionsFor}",
                                style = MaterialTheme.typography.titleLarge,
                                color = primaryInk
                            )
                            Text(
                                text = if (isEn) {
                                    "Tap any outfit card to edit pieces & score"
                                } else {
                                    "Parça değiştirmek için kombin kartına dokun"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryInk
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onRequireFeature(AppFeatureKey.SURPRISE_OUTFIT) {
                                        val pick = suggestions.randomOrNull() ?: OutfitRecommendationEngine.suggestOutfits(
                                            items = closetItems,
                                            weather = WeatherCondition.entries.random(),
                                            targetDayLabel = activeDayForecast.label,
                                            recentSignatures = recentSignatures,
                                            lastUsedPrimaryIds = lastPrimaryIds,
                                            count = 3,
                                            lang = lang,
                                            genderPref = genderPref,
                                            flatteringColors = flatteringColors
                                        ).randomOrNull()
                                        if (pick != null) {
                                            onOpenOutfitDetail(pick.title, pick.items, null)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.45f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "🎲 ${if (isEn) "Surprise" else "Sürpriz"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DressKitStyle.accentColor(),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            OutlinedButton(
                                onClick = { refreshTrigger++ },
                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    tint = primaryInk,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.suggestDifferent,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = primaryInk,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                if (closetItems.size < 2 || suggestions.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                            color = DressKitStyle.cardSurface(),
                            border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = strings.needTwoItemsTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = primaryInk
                                )
                                Text(
                                    text = strings.needTwoItemsBody,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryInk
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(suggestions, key = { idx, s -> "${s.title}_$idx" }) { _, suggestion ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenOutfitDetail(suggestion.title, suggestion.items, null)
                                },
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = primaryInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = suggestion.harmonyNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryInk
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    onShareOutfit(
                                                        suggestion.title,
                                                        suggestion.items,
                                                        suggestion.harmonyScore,
                                                        "${activeCondition.displayLabel(lang)} (${activeDayForecast.maxTempC}°C)"
                                                    )
                                                },
                                            shape = CircleShape,
                                            color = DressKitStyle.secondarySurface(),
                                            border = BorderStroke(0.75.dp, DressKitStyle.borderColor())
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Share,
                                                    contentDescription = if (isEn) "Share" else "Paylaş",
                                                    tint = secondaryInk,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        HarmonyScoreBadge(score = suggestion.harmonyScore)
                                    }
                                }

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(suggestion.items, key = { it.id }) { piece ->
                                        PieceMiniTile(
                                            piece = piece,
                                            onClick = { onOpenItem(piece.id) }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onOpenTryOn2D(suggestion.items) },
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = strings.try2DShort,
                                            color = primaryInk,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    WearActionButton(
                                        label = strings.wornBtn,
                                        onClick = { onMarkOutfitWorn(suggestion.items) },
                                        onLongClick = { wearEditorTargetItems = suggestion.items },
                                        filled = false,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    Button(
                                        onClick = {
                                            onSaveOutfit(
                                                suggestion.title,
                                                suggestion.items,
                                                suggestion.harmonyScore
                                            )
                                        },
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DressKitStyle.buttonBg(),
                                            contentColor = DressKitStyle.buttonFg()
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.BookmarkAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = strings.save,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Saved Outfits Section
                if (savedOutfits.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${strings.savedOutfitsTitle} (${savedOutfits.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = primaryInk
                        )
                    }

                    items(savedOutfits, key = { it.id }) { outfit ->
                        val outfitPieces = remember(outfit.itemIds, items) {
                            OutfitRecommendationEngine.sortTopToBottom(
                                outfit.itemIds.mapNotNull { id -> items.find { it.id == id } }
                            )
                        }
                        val (liveScore, liveNote) = remember(outfitPieces, lang) {
                            OutfitRecommendationEngine.calculateColorHarmony(outfitPieces, lang)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (outfitPieces.isNotEmpty()) {
                                        onOpenOutfitDetail(outfit.title, outfitPieces, outfit.id)
                                    }
                                },
                            shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                            color = DressKitStyle.cardSurface(),
                            border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = outfit.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = primaryInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = liveNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryInk
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        HarmonyScoreBadge(score = liveScore)
                                        Surface(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .clickable(enabled = outfitPieces.isNotEmpty()) {
                                                    onShareOutfit(outfit.title, outfitPieces, liveScore, "")
                                                },
                                            shape = CircleShape,
                                            color = DressKitStyle.secondarySurface(),
                                            border = BorderStroke(0.75.dp, DressKitStyle.borderColor())
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Share,
                                                    contentDescription = if (isEn) "Share" else "Paylaş",
                                                    tint = if (outfitPieces.isNotEmpty()) secondaryInk else secondaryInk.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .clickable { onDeleteOutfit(outfit.id) },
                                            shape = CircleShape,
                                            color = DressKitStyle.secondarySurface(),
                                            border = BorderStroke(0.75.dp, DressKitStyle.borderColor())
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.DeleteOutline,
                                                    contentDescription = null,
                                                    tint = DressKitColors.DangerRust,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (outfitPieces.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(outfitPieces, key = { it.id }) { piece ->
                                            PieceMiniTile(
                                                piece = piece,
                                                onClick = { onOpenItem(piece.id) }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onOpenTryOn2D(outfitPieces) },
                                        enabled = outfitPieces.isNotEmpty(),
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(strings.try2DShort, color = primaryInk, maxLines = 1, softWrap = false)
                                    }
                                    WearActionButton(
                                        label = strings.wornBtn,
                                        onClick = { onMarkOutfitWorn(outfitPieces) },
                                        onLongClick = { wearEditorTargetItems = outfitPieces },
                                        filled = true,
                                        enabled = outfitPieces.isNotEmpty(),
                                        showIcon = false,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Manual Outfit Creator Modal Sheet
    if (showCreatorSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var draftTitle by remember { mutableStateOf(if (isEn) "Custom Outfit" else "Özel Kombinim") }
        val selectedIds = remember { mutableStateListOf<String>() }

        val pickedItems = remember(selectedIds.toList(), items, lang) {
            OutfitRecommendationEngine.sortTopToBottom(
                items.filter { selectedIds.contains(it.id) }
            )
        }
        val (draftScore, draftNote) = remember(pickedItems, lang) {
            OutfitRecommendationEngine.calculateColorHarmony(pickedItems, lang)
        }

        ModalBottomSheet(
            onDismissRequest = { showCreatorSheet = false },
            sheetState = sheetState,
            containerColor = DressKitStyle.cardSurface(),
            contentColor = primaryInk
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) "Create Custom Outfit" else "Yeni Kombin Oluştur",
                        style = MaterialTheme.typography.titleLarge,
                        color = primaryInk
                    )
                    if (pickedItems.isNotEmpty()) {
                        HarmonyScoreBadge(score = draftScore)
                    } else {
                        IconButton(onClick = { showCreatorSheet = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = strings.close, tint = primaryInk)
                        }
                    }
                }

                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it },
                    label = { Text(if (isEn) "Outfit Name" else "Kombin Adı") },
                    singleLine = true,
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

                Text(
                    text = if (pickedItems.isEmpty()) {
                        if (isEn) "Select at least 2 garments below:" else "Aşağıdan en az 2 parça seç:"
                    } else {
                        "$draftNote · ${pickedItems.size} ${if (isEn) "selected" else "seçili"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryInk
                )

                LazyColumn(
                    modifier = Modifier.height(270.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { piece ->
                        val isPicked = selectedIds.contains(piece.id)
                        val file = remember(piece.coverPhoto) {
                            piece.coverPhoto?.let { ImageStorage.getPhotoFile(context, it) }
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isPicked) selectedIds.remove(piece.id) else selectedIds.add(piece.id)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPicked) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                            border = BorderStroke(
                                width = if (isPicked) 1.5.dp else 1.dp,
                                color = if (isPicked) DressKitStyle.accentColor() else DressKitStyle.borderColor()
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (piece.useOriginalPhotos) DressKitStyle.photoBoxSurface() else Color.White
                                        )
                                ) {
                                    if (file != null && file.exists()) {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = piece.name,
                                            contentScale = if (piece.useOriginalPhotos) ContentScale.Crop else ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(if (piece.useOriginalPhotos) 0.dp else 3.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = piece.name.ifBlank { piece.subCategory.display(lang) },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = primaryInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = piece.subCategory.display(lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryInk
                                    )
                                }
                                if (isPicked) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = DressKitStyle.accentColor()
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val selected = pickedItems
                            showCreatorSheet = false
                            onOpenTryOn2D(selected)
                        },
                        enabled = pickedItems.isNotEmpty(),
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.tryOn2D, color = primaryInk)
                    }
                    Button(
                        onClick = {
                            onSaveOutfit(
                                draftTitle.trim().ifBlank { if (isEn) "Custom Outfit" else "Özel Kombinim" },
                                pickedItems,
                                draftScore
                            )
                            showCreatorSheet = false
                        },
                        enabled = pickedItems.size >= 2,
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DressKitStyle.buttonBg(),
                            contentColor = DressKitStyle.buttonFg()
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.saveOutfit)
                    }
                }
            }
        }
    }

    // Smart Travel Capsule Packing Sheet
    if (showTravelSheet) {
        val travelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var destinationQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<DestinationPlace>>(emptyList()) }
        var selectedDestinationLabel by remember { mutableStateOf(customLocationLabel ?: selectedCity.label) }
        var destinationForecast by remember { mutableStateOf(forecastDays) }
        var isLoadingDestination by remember { mutableStateOf(false) }
        var searchNoMatch by remember { mutableStateOf(false) }
        var tripDays by remember { mutableIntStateOf(4) }
        var selectedTravelTab by remember { mutableIntStateOf(0) } // 0 = Günlük Kombinler, 1 = Bavul Listesi
        val packedIds = remember { mutableStateListOf<String>() }

        val quickDestinations = remember {
            listOf(
                DestinationPlace("İstanbul", "Türkiye", 41.0082, 28.9784),
                DestinationPlace("Antalya", "Türkiye", 36.8969, 30.7133),
                DestinationPlace("İzmir", "Türkiye", 38.4192, 27.1287),
                DestinationPlace("Kapadokya", "Türkiye", 38.6244, 34.7239),
                DestinationPlace("Ankara", "Türkiye", 39.9334, 32.8597),
                DestinationPlace("Trabzon", "Türkiye", 41.0027, 39.7168),
                DestinationPlace("Paris", "France", 48.8566, 2.3522),
                DestinationPlace("London", "UK", 51.5072, -0.1276),
                DestinationPlace("Berlin", "Germany", 52.5200, 13.4050),
                DestinationPlace("Rome", "Italy", 41.9028, 12.4964),
                DestinationPlace("Barcelona", "Spain", 41.3874, 2.1686)
            )
        }

        fun selectDestinationPlace(place: DestinationPlace) {
            selectedDestinationLabel = place.displayLabel
            searchResults = emptyList()
            searchNoMatch = false
            scope.launch {
                isLoadingDestination = true
                val fetched = OutfitRecommendationEngine.fetchWeeklyForecastForCoords(place.lat, place.lon, lang)
                if (!fetched.isNullOrEmpty()) {
                    destinationForecast = fetched
                }
                isLoadingDestination = false
            }
        }

        fun runDestinationSearch() {
            val clean = destinationQuery.trim()
            if (clean.length < 2) return
            scope.launch {
                isLoadingDestination = true
                val found = OutfitRecommendationEngine.searchDestinations(clean, lang)
                searchResults = found
                searchNoMatch = found.isEmpty()
                if (found.size == 1) {
                    selectDestinationPlace(found.first())
                } else {
                    isLoadingDestination = false
                }
            }
        }

        val travelPlan = remember(
            closetItems,
            selectedDestinationLabel,
            destinationForecast,
            tripDays,
            genderPref,
            flatteringColors,
            lang
        ) {
            OutfitRecommendationEngine.generateTravelPlan(
                items = closetItems,
                destinationLabel = selectedDestinationLabel,
                forecasts = destinationForecast,
                tripDays = tripDays,
                lang = lang,
                genderPref = genderPref,
                flatteringColors = flatteringColors
            )
        }

        val activeForecastSlice = remember(destinationForecast, tripDays) {
            (0 until tripDays).map { idx -> destinationForecast.getOrElse(idx) { destinationForecast.last() } }
        }

        val itemDaysUsage = remember(travelPlan) {
            buildMap<String, List<Int>> {
                travelPlan.dayPlans.forEach { dayPlan ->
                    dayPlan.outfit.items.forEach { piece ->
                        val prev = get(piece.id).orEmpty()
                        put(piece.id, prev + dayPlan.dayNumber)
                    }
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showTravelSheet = false },
            sheetState = travelSheetState,
            containerColor = DressKitStyle.cardSurface(),
            contentColor = primaryInk
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) "Smart Suitcase Planner" else "Akıllı Bavul Hazırlayıcı",
                            style = MaterialTheme.typography.titleLarge,
                            color = primaryInk
                        )
                        Text(
                            text = if (isEn) {
                                "${travelPlan.destinationLabel} · ${travelPlan.packingChecklist.size} pieces · ${travelPlan.dayPlans.size} day looks"
                            } else {
                                "${travelPlan.destinationLabel} · ${travelPlan.packingChecklist.size} otomatik parça · ${travelPlan.dayPlans.size} günlük kombin"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = DressKitStyle.accentColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { showTravelSheet = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = strings.close, tint = primaryInk)
                    }
                }

                // Destination Input ("Nereye Gidiyorsun?")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = destinationQuery,
                        onValueChange = {
                            destinationQuery = it
                            if (it.isBlank()) {
                                searchResults = emptyList()
                                searchNoMatch = false
                            }
                        },
                        placeholder = {
                            Text(
                                text = if (isEn) "Where are you going? (e.g. Paris, Antalya)" else "Nereye gidiyorsun? (ör. Antalya, Paris)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runDestinationSearch() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DressKitStyle.accentColor(),
                            unfocusedBorderColor = DressKitStyle.borderColor(),
                            focusedTextColor = primaryInk,
                            unfocusedTextColor = primaryInk
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { runDestinationSearch() },
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DressKitStyle.buttonBg(),
                            contentColor = DressKitStyle.buttonFg()
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (isLoadingDestination) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = DressKitStyle.accentColor(),
                        trackColor = DressKitStyle.secondarySurface()
                    )
                }

                if (searchNoMatch) {
                    Text(
                        text = if (isEn) "City not found. Try a larger city nearby." else "Şehir bulunamadı. Yakın bir il veya popüler şehir yazabilirsin.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (searchResults.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(searchResults) { place ->
                            FilterChipPill(
                                label = "📍 ${place.displayLabel}",
                                selected = selectedDestinationLabel == place.displayLabel,
                                onClick = { selectDestinationPlace(place) }
                            )
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickDestinations) { place ->
                            val isSelected = selectedDestinationLabel.startsWith(place.name, ignoreCase = true)
                            FilterChipPill(
                                label = place.name,
                                selected = isSelected,
                                onClick = { selectDestinationPlace(place) }
                            )
                        }
                    }
                }

                // Trip Duration selector + Live destination forecast mini strip
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(2, 3, 4, 5, 7)) { days ->
                        FilterChipPill(
                            label = if (isEn) "$days Days" else "$days Gün",
                            selected = tripDays == days,
                            onClick = { tripDays = days }
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(activeForecastSlice) { idx, day ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DressKitStyle.secondarySurface(),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor())
                        ) {
                            Text(
                                text = "${if (isEn) "D" else "G"}${idx + 1} ${day.condition.emoji} ${day.maxTempC}°",
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryInk,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Two-Tab Switch: Auto Daily Outfits vs Auto Packing Checklist
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChipPill(
                        label = if (isEn) "🗓️ Daily Looks (${travelPlan.dayPlans.size})" else "🗓️ Günlük Kombinler (${travelPlan.dayPlans.size})",
                        selected = selectedTravelTab == 0,
                        onClick = { selectedTravelTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChipPill(
                        label = if (isEn) "🧳 Suitcase (${travelPlan.packingChecklist.size})" else "🧳 Bavul Listesi (${travelPlan.packingChecklist.size})",
                        selected = selectedTravelTab == 1,
                        onClick = { selectedTravelTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (closetItems.size < 2 || travelPlan.packingChecklist.isEmpty()) {
                    Text(
                        text = strings.needTwoItemsBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                } else if (selectedTravelTab == 0) {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(travelPlan.dayPlans, key = { it.dayNumber }) { dayPlan ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenOutfitDetail(dayPlan.outfit.title, dayPlan.outfit.items, null)
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = DressKitStyle.secondarySurface(),
                                border = BorderStroke(1.dp, DressKitStyle.borderColor())
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isEn) {
                                                    "Day ${dayPlan.dayNumber} · ${dayPlan.forecast.condition.emoji} ${dayPlan.forecast.maxTempC}°C / ${dayPlan.forecast.minTempC}°C"
                                                } else {
                                                    "${dayPlan.dayNumber}. Gün · ${dayPlan.forecast.condition.emoji} ${dayPlan.forecast.maxTempC}°C / ${dayPlan.forecast.minTempC}°C"
                                                },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = primaryInk
                                            )
                                            Text(
                                                text = dayPlan.outfit.harmonyNote,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = secondaryInk,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        HarmonyScoreBadge(score = dayPlan.outfit.harmonyScore)
                                    }

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(dayPlan.outfit.items, key = { "${dayPlan.dayNumber}_${it.id}" }) { piece ->
                                            PieceMiniTile(
                                                piece = piece,
                                                onClick = { onOpenItem(piece.id) }
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onOpenTryOn2D(dayPlan.outfit.items)
                                            },
                                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = strings.tryOn2D,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = primaryInk,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onSaveOutfit(
                                                    dayPlan.outfit.title,
                                                    dayPlan.outfit.items,
                                                    dayPlan.outfit.harmonyScore
                                                )
                                            },
                                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                            border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.5f)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = strings.saveOutfit,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = DressKitStyle.accentColor(),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(270.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(travelPlan.packingChecklist, key = { it.id }) { piece ->
                            val isChecked = packedIds.contains(piece.id)
                            val file = remember(piece.coverPhoto) {
                                piece.coverPhoto?.let { ImageStorage.getPhotoFile(context, it) }
                            }
                            val usedDays = itemDaysUsage[piece.id].orEmpty()
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) packedIds.remove(piece.id) else packedIds.add(piece.id)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = DressKitStyle.secondarySurface(),
                                border = BorderStroke(1.dp, DressKitStyle.borderColor())
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (piece.useOriginalPhotos) DressKitStyle.photoBoxSurface() else Color.White)
                                    ) {
                                        if (file != null && file.exists()) {
                                            AsyncImage(
                                                model = file,
                                                contentDescription = piece.name,
                                                contentScale = if (piece.useOriginalPhotos) ContentScale.Crop else ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(if (piece.useOriginalPhotos) 0.dp else 3.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = piece.name.ifBlank { piece.subCategory.display(lang) },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = primaryInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val usageLabel = if (usedDays.isNotEmpty()) {
                                            val prefix = if (isEn) "Days" else "Günler"
                                            "${piece.category.display(lang)} · $prefix: ${usedDays.joinToString(",")}"
                                        } else {
                                            piece.category.display(lang)
                                        }
                                        Text(
                                            text = usageLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = {
                                            if (isChecked) packedIds.remove(piece.id) else packedIds.add(piece.id)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val totalCount = travelPlan.packingChecklist.size
                    val checkedCount = packedIds.count { id -> travelPlan.packingChecklist.any { it.id == id } }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$checkedCount / $totalCount ${if (isEn) "packed" else "bavula konuldu"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryInk
                        )
                        TextButton(
                            onClick = {
                                if (checkedCount == totalCount) {
                                    packedIds.clear()
                                } else {
                                    packedIds.clear()
                                    packedIds.addAll(travelPlan.packingChecklist.map { it.id })
                                }
                            }
                        ) {
                            Text(
                                text = if (checkedCount == totalCount) {
                                    if (isEn) "Clear All" else "Temizle"
                                } else {
                                    if (isEn) "Check All" else "Hepsini İşaretle"
                                },
                                color = DressKitStyle.accentColor()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PieceMiniTile(
    piece: ClothingItem,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val file = remember(piece.coverPhoto) {
        piece.coverPhoto?.let { ImageStorage.getPhotoFile(context, it) }
    }
    Column(
        modifier = Modifier
            .width(86.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (piece.useOriginalPhotos) DressKitStyle.photoBoxSurface() else Color.White)
                .border(1.dp, DressKitStyle.borderColor(), RoundedCornerShape(12.dp))
        ) {
            if (file != null && file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = piece.name,
                    contentScale = if (piece.useOriginalPhotos) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (piece.useOriginalPhotos) 0.dp else 6.dp)
                )
            }
        }
        Text(
            text = piece.name.ifBlank { piece.subCategory.display(lang) },
            modifier = Modifier.padding(horizontal = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = DressKitStyle.secondaryInk(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HourlyWeatherDialog(
    locationLabel: String,
    day: DailyForecast,
    lang: AppLanguage,
    onDismiss: () -> Unit
) {
    val isEn = lang != AppLanguage.TR
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val hourlyList = remember(day) {
        day.hourly.ifEmpty {
            OutfitRecommendationEngine.buildFallbackHourly(day.dayIndex, day.maxTempC, day.minTempC, day.condition)
        }
    }
    val stripState = rememberLazyListState()
    LaunchedEffect(hourlyList) {
        val curIdx = hourlyList.indexOfFirst { it.isCurrentHour }
        if (curIdx > 1) {
            stripState.scrollToItem((curIdx - 1).coerceAtLeast(0))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 22.dp)
                .height(640.dp),
            shape = RoundedCornerShape(DressKitStyle.cardCorner()),
            color = DressKitStyle.cardSurface(),
            border = BorderStroke(1.dp, DressKitStyle.borderColor())
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = DressKitStyle.accentColor(),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = localizedText(
                                    lang,
                                    "24 Saatlik Hava Durumu · ${day.label}",
                                    "24-Hour Forecast · ${day.label}"
                                ),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$locationLabel · ${day.dateText} · ${day.minTempC}° / ${day.maxTempC}°C",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryInk
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = primaryInk)
                    }
                }

                // Horizontal quick temperature pills strip
                LazyRow(
                    state = stripState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(hourlyList) { _, h ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (h.isCurrentHour) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                            border = BorderStroke(
                                width = if (h.isCurrentHour) 1.5.dp else 1.dp,
                                color = if (h.isCurrentHour) DressKitStyle.accentColor() else DressKitStyle.borderColor()
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (h.isNextDay) "${h.hourLabel} · ${if (isEn) "Tmrw" else "Yarın"}" else h.hourLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (h.isCurrentHour) DressKitStyle.accentColor() else secondaryInk
                                )
                                Text(text = h.condition.emoji, fontSize = 18.sp)
                                Text(
                                    text = "${h.tempC}°",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = primaryInk
                                )
                            }
                        }
                    }
                }

                // Full 24-hour vertical breakdown
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    hourlyList.forEach { h ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (h.isCurrentHour) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                            border = BorderStroke(
                                width = if (h.isCurrentHour) 1.5.dp else 1.dp,
                                color = if (h.isCurrentHour) DressKitStyle.accentColor() else DressKitStyle.borderColor()
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = h.hourLabel,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (h.isCurrentHour) DressKitStyle.accentColor() else primaryInk
                                    )
                                    Text(text = h.condition.emoji, fontSize = 20.sp)
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "${h.tempC}°C (${if (isEn) "Feels" else "His"}: ${h.feelsLikeC}°)",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = primaryInk
                                            )
                                            if (h.isCurrentHour) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = DressKitStyle.accentColor()
                                                ) {
                                                    Text(
                                                        text = if (isEn) "Now" else "Şu an",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else if (h.isNextDay) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = DressKitStyle.secondarySurface(),
                                                    border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                                ) {
                                                    Text(
                                                        text = if (isEn) "Tomorrow" else "Yarın",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = secondaryInk,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = h.condition.displayHint(lang),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Outlined.WaterDrop, contentDescription = null, modifier = Modifier.size(12.dp), tint = secondaryInk)
                                        Text("${h.precipitationProb}%", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Outlined.Air, contentDescription = null, modifier = Modifier.size(12.dp), tint = secondaryInk)
                                        Text("${h.windKmh} km/s", style = MaterialTheme.typography.labelSmall, color = secondaryInk)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}
