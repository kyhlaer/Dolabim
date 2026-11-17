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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.dresskit.wardrobe.data.DailyForecast
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.OutfitRecommendationEngine
import com.dresskit.wardrobe.data.TurkishCity
import com.dresskit.wardrobe.data.WeatherCondition
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.ui.components.FilterChipPill
import com.dresskit.wardrobe.ui.components.HarmonyScoreBadge
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
    onOpenSettings: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val isEn = lang == AppLanguage.EN
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val scope = rememberCoroutineScope()

    val closetItems = remember(items) { items.filter { !it.isWishlist } }

    var selectedCity by rememberSaveable { mutableStateOf(TurkishCity.ISTANBUL) }
    var customLocationLabel by rememberSaveable { mutableStateOf<String?>(null) }
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
                customLocationLabel = result.first
                forecastDays = result.second
                manualWeatherOverride = null
                selectedDayIndex = 0
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

    // Load forecast on city or language switch when not pinned to detected GPS location
    LaunchedEffect(selectedCity, lang) {
        if (customLocationLabel == null) {
            val fallback = OutfitRecommendationEngine.generateFallbackWeeklyForecast(lang)
            forecastDays = fallback
            val live = OutfitRecommendationEngine.fetchWeeklyForecast(selectedCity, lang)
            if (live != null) {
                forecastDays = live
            }
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            val refreshed = OutfitRecommendationEngine.detectUserLocationAndForecast(
                context,
                hasPerm,
                lang
            )
            if (refreshed != null) {
                forecastDays = refreshed.second
            }
        }
    }

    val activeDayForecast: DailyForecast = forecastDays.getOrElse(selectedDayIndex) {
        forecastDays.first()
    }
    val activeCondition: WeatherCondition = manualWeatherOverride ?: activeDayForecast.condition

    val suggestions = remember(
        closetItems,
        activeCondition,
        activeDayForecast.label,
        refreshTrigger,
        lang
    ) {
        OutfitRecommendationEngine.suggestOutfits(
            items = closetItems,
            weather = activeCondition,
            targetDayLabel = activeDayForecast.label,
            recentSignatures = recentSignatures,
            lastUsedPrimaryIds = lastPrimaryIds,
            count = 3,
            lang = lang
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
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = strings.settingsTitle,
                        tint = primaryInk
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onOpenTryOn2D(emptyList()) },
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
                    onClick = { showTravelSheet = true },
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
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = activeCondition.emoji,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Column {
                                        Text(
                                            text = "${activeCondition.displayLabel(lang)} (${activeDayForecast.maxTempC}°C)",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = primaryInk
                                        )
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
                                                    selectedCity = city
                                                    manualWeatherOverride = null
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
                        OutlinedButton(
                            onClick = { refreshTrigger++ },
                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                tint = primaryInk,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
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
                                    HarmonyScoreBadge(score = suggestion.harmonyScore)
                                }

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(suggestion.items, key = { it.id }) { piece ->
                                        PieceMiniTile(piece = piece)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp),
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        HarmonyScoreBadge(score = liveScore)
                                        IconButton(onClick = { onDeleteOutfit(outfit.id) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = null,
                                                tint = DressKitColors.DangerRust,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                }

                                if (outfitPieces.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(outfitPieces, key = { it.id }) { piece ->
                                            PieceMiniTile(piece = piece)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onOpenTryOn2D(outfitPieces) },
                                        enabled = outfitPieces.isNotEmpty(),
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
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
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
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
        var tripDays by remember { mutableIntStateOf(3) }
        var tripWeather by remember { mutableStateOf(activeCondition) }
        val packedIds = remember { mutableStateListOf<String>() }

        val capsuleList = remember(closetItems, tripDays, tripWeather) {
            val topsTarget = when {
                tripDays <= 3 -> 3
                tripDays <= 5 -> 4
                else -> 6
            }
            val bottomsTarget = if (tripDays <= 3) 2 else 3
            val outerTarget = if (tripWeather == WeatherCondition.HOT) 0 else if (tripDays <= 4) 1 else 2

            val preferredTops = closetItems
                .filter { it.category == com.dresskit.wardrobe.model.ClothingCategory.TOP }
                .take(topsTarget)
            val preferredBottoms = closetItems
                .filter { it.category == com.dresskit.wardrobe.model.ClothingCategory.BOTTOM }
                .take(bottomsTarget)
            val preferredOuters = closetItems
                .filter { it.category == com.dresskit.wardrobe.model.ClothingCategory.OUTERWEAR }
                .take(outerTarget)
            val preferredShoes = closetItems
                .filter { it.category == com.dresskit.wardrobe.model.ClothingCategory.SHOES }
                .take(if (tripDays >= 5) 2 else 1)
            val preferredAcc = closetItems
                .filter { it.category == com.dresskit.wardrobe.model.ClothingCategory.ACCESSORY }
                .take(2)

            OutfitRecommendationEngine.sortTopToBottom(
                (preferredOuters + preferredTops + preferredBottoms + preferredShoes + preferredAcc).distinctBy { it.id }
            )
        }

        val unlockedCombos = remember(capsuleList) {
            val tops = capsuleList.count { it.category == com.dresskit.wardrobe.model.ClothingCategory.TOP }
            val bots = capsuleList.count { it.category == com.dresskit.wardrobe.model.ClothingCategory.BOTTOM }
            (tops * bots).coerceAtLeast(if (capsuleList.size >= 2) 1 else 0)
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
                            text = if (isEn) "Smart Travel Packing" else "Akıllı Bavul Hazırlayıcı",
                            style = MaterialTheme.typography.titleLarge,
                            color = primaryInk
                        )
                        Text(
                            text = if (isEn) {
                                "${capsuleList.size} capsule pieces · unlocks ~$unlockedCombos outfits"
                            } else {
                                "${capsuleList.size} kapsül parça · ~$unlockedCombos farklı kombin üretir"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = DressKitStyle.accentColor()
                        )
                    }
                    IconButton(onClick = { showTravelSheet = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = strings.close, tint = primaryInk)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3 to (if (isEn) "2-3 Days" else "2-3 Gün"), 5 to (if (isEn) "4-5 Days" else "4-5 Gün"), 7 to (if (isEn) "7 Days" else "7 Gün")).forEach { (days, lbl) ->
                        FilterChipPill(
                            label = lbl,
                            selected = tripDays == days,
                            onClick = { tripDays = days }
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WeatherCondition.entries) { cond ->
                        FilterChipPill(
                            label = "${cond.emoji} ${cond.displayLabel(lang)}",
                            selected = tripWeather == cond,
                            onClick = { tripWeather = cond }
                        )
                    }
                }

                if (capsuleList.isEmpty()) {
                    Text(
                        text = strings.needTwoItemsBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(capsuleList, key = { it.id }) { piece ->
                            val isChecked = packedIds.contains(piece.id)
                            val file = remember(piece.coverPhoto) {
                                piece.coverPhoto?.let { ImageStorage.getPhotoFile(context, it) }
                            }
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
                                        Text(
                                            text = piece.category.display(lang),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryInk
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

                    Text(
                        text = "${packedIds.size} / ${capsuleList.size} ${if (isEn) "packed" else "bavula konuldu"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryInk
                    )
                }
            }
        }
    }
}

@Composable
private fun PieceMiniTile(piece: ClothingItem) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val file = remember(piece.coverPhoto) {
        piece.coverPhoto?.let { ImageStorage.getPhotoFile(context, it) }
    }
    Column(
        modifier = Modifier.width(86.dp),
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
            style = MaterialTheme.typography.labelSmall,
            color = DressKitStyle.secondaryInk(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
