package com.dresskit.wardrobe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.ui.theme.DressKitColors
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings

@Composable
fun GarmentCard(
    item: ClothingItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val coverFile = remember(item.coverPhoto) {
        item.coverPhoto?.let { ImageStorage.getPhotoFile(context, it) }
    }
    val gridImageRequest = remember(coverFile) {
        coverFile?.let { file ->
            ImageRequest.Builder(context)
                .data(file)
                .size(512)
                .crossfade(true)
                .build()
        }
    }
    val corner = DressKitStyle.cardCorner()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(corner),
        color = DressKitStyle.cardSurface(),
        shadowElevation = if (DressKitStyle.isStreet()) 0.dp else 1.5.dp,
        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(if (item.useOriginalPhotos) DressKitStyle.photoBoxSurface() else Color.White)
            ) {
                if (coverFile != null && coverFile.exists() && gridImageRequest != null) {
                    AsyncImage(
                        model = gridImageRequest,
                        contentDescription = item.name,
                        contentScale = if (item.useOriginalPhotos) ContentScale.Crop else ContentScale.Fit,
                        modifier = if (item.useOriginalPhotos) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Checkroom,
                            contentDescription = null,
                            tint = DressKitColors.MutedInk,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Multi-photo badge or Wishlist Price / Drop Tag on top-left
                if (item.isWishlist && (item.priceText.isNotBlank() || item.priceDropped)) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (item.priceText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = DressKitStyle.buttonBg()
                            ) {
                                Text(
                                    text = item.priceText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DressKitStyle.buttonFg(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (item.priceDropped) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF1D6B42)
                            ) {
                                Text(
                                    text = if (lang == com.dresskit.wardrobe.model.AppLanguage.EN) {
                                        "🔥 Price Drop"
                                    } else {
                                        "🔥 İndirim"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else if (item.activePhotos.size > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = DressKitStyle.cardSurface().copy(alpha = 0.92f),
                        border = BorderStroke(0.75.dp, DressKitStyle.borderColor())
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Collections,
                                contentDescription = null,
                                tint = secondaryInk,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${item.activePhotos.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryInk
                            )
                        }
                    }
                }

                // Favorite & Share buttons
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onShare != null) {
                        Surface(
                            modifier = Modifier
                                .size(26.dp)
                                .clickable(onClick = onShare),
                            shape = CircleShape,
                            color = DressKitStyle.cardSurface().copy(alpha = 0.88f),
                            border = BorderStroke(0.75.dp, DressKitStyle.borderColor())
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Paylaş",
                                    tint = secondaryInk,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(onClick = onToggleFavorite),
                        shape = CircleShape,
                        color = if (item.isFavorite) {
                            DressKitStyle.accentSoft()
                        } else {
                            DressKitStyle.cardSurface().copy(alpha = 0.88f)
                        },
                        border = if (item.isFavorite) null else BorderStroke(0.75.dp, DressKitStyle.borderColor())
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (item.isFavorite) {
                                    Icons.Rounded.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                                contentDescription = "Favori",
                                tint = if (item.isFavorite) {
                                    DressKitStyle.accentColor()
                                } else {
                                    secondaryInk
                                },
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DressKitStyle.cardSurface())
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.name.ifBlank { item.subCategory.display(lang) },
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.subCategory.display(lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val swatches = if (item.colors.isNotEmpty()) {
                            item.colors.take(3).map { it.composeColor }
                        } else {
                            item.extractedHexes.take(3).map { Color(it) }
                        }
                        swatches.forEach { swatchColor ->
                            ColorDot(color = swatchColor, size = 10.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HarmonyScoreBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val isDark = DressKitStyle.isDark()
    val lang = LocalAppLanguage.current
    val bgColor = when {
        isDark && score >= 83 -> Color(0xFF1D3324)
        isDark && score >= 64 -> Color(0xFF332712)
        isDark -> Color(0xFF381E1E)
        score >= 83 -> DressKitColors.OliveSoft
        score >= 64 -> DressKitColors.AmberSoft
        else -> DressKitColors.TerracottaSoft
    }
    val fgColor = when {
        isDark && score >= 83 -> Color(0xFF6EE7B7)
        isDark && score >= 64 -> DressKitColors.StreetAmber
        isDark -> Color(0xFFFCA5A5)
        score >= 83 -> DressKitColors.OliveSage
        score >= 64 -> DressKitColors.AmberWarm
        else -> DressKitColors.DangerRust
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (lang == com.dresskit.wardrobe.model.AppLanguage.EN) "$score% Match" else "%$score Uyum",
                style = MaterialTheme.typography.labelSmall,
                color = fgColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun ColorDot(
    color: Color,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val ringColor = if (DressKitStyle.isDark()) {
        Color.White.copy(alpha = 0.24f)
    } else {
        Color.Black.copy(alpha = 0.16f)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(0.75.dp, ringColor, CircleShape)
    )
}

@Composable
fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingDotColor: Color? = null
) {
    val activeBg = DressKitStyle.buttonBg()
    val activeFg = DressKitStyle.buttonFg()
    val inactiveBg = DressKitStyle.cardSurface()
    val inactiveFg = DressKitStyle.primaryInk()

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
        color = if (selected) activeBg else inactiveBg,
        border = BorderStroke(
            width = DressKitStyle.borderWidth(),
            color = if (selected) activeBg else DressKitStyle.borderColor()
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingDotColor != null) {
                ColorDot(color = leadingDotColor, size = 10.dp)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                color = if (selected) activeFg else inactiveFg
            )
        }
    }
}

@Composable
fun ColorSwatchPicker(
    selectedColors: List<GarmentColor>,
    onToggleColor: (GarmentColor) -> Unit
) {
    val lang = LocalAppLanguage.current
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(GarmentColor.entries) { garmentColor ->
            val isSelected = selectedColors.contains(garmentColor)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleColor(garmentColor) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(garmentColor.composeColor)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) DressKitStyle.accentColor() else DressKitStyle.borderColor(),
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = garmentColor.display(lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) primaryInk else secondaryInk
                )
            }
        }
    }
}

@Composable
fun FullscreenPhotoDialog(
    fileName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val file = remember(fileName) { ImageStorage.getPhotoFile(context, fileName) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DressKitStyle.canvasBg())
        ) {
            AsyncImage(
                model = file,
                contentDescription = "Kıyafet fotoğrafı",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = nextScale
                            offset = if (nextScale > 1f) {
                                offset + pan
                            } else {
                                Offset.Zero
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp),
                shape = CircleShape,
                color = DressKitColors.CharcoalInk.copy(alpha = 0.8f)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Kapat",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Pill/action button that triggers [onClick] on tap and opens the wear-count editor on long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearActionButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    showIcon: Boolean = true,
    shape: Shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
) {
    val haptic = LocalHapticFeedback.current
    val bgColor = when {
        !enabled -> DressKitStyle.secondarySurface()
        filled -> DressKitStyle.buttonBg()
        else -> Color.Transparent
    }
    val fgColor = when {
        !enabled -> DressKitStyle.secondaryInk()
        filled -> DressKitStyle.buttonFg()
        else -> DressKitStyle.primaryInk()
    }
    val iconColor = when {
        !enabled -> DressKitStyle.secondaryInk()
        filled -> DressKitStyle.buttonFg()
        else -> DressKitStyle.accentColor()
    }
    val borderStroke = if (filled) null else BorderStroke(1.dp, DressKitStyle.borderColor())

    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = shape,
        color = bgColor,
        border = borderStroke
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 40.dp)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showIcon) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = fgColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dialog allowing users to adjust wear counts via - / + buttons or direct numeric input.
 */
@Composable
fun WearCountEditorDialog(
    itemsToEdit: List<ClothingItem>,
    onSave: (Map<String, Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val isEn = lang == AppLanguage.EN
    val strings = appStrings()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    val countInputs = remember(itemsToEdit) {
        mutableStateMapOf<String, String>().apply {
            itemsToEdit.forEach { put(it.id, it.wearCount.coerceAtLeast(0).toString()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DressKitStyle.cardSurface(),
        titleContentColor = primaryInk,
        textContentColor = secondaryInk,
        title = {
            Text(
                text = if (isEn) "Edit Wear Count" else "Giyilme Sayısını Düzenle",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isEn) {
                        "Use - / + or type a number directly."
                    } else {
                        "- / + tuşlarını kullan veya doğrudan sayı gir."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryInk
                )

                itemsToEdit.forEach { item ->
                    val rawText = countInputs[item.id] ?: item.wearCount.toString()
                    val currentValue = rawText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val title = item.name.ifBlank { item.subCategory.display(lang) }

                    Surface(
                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                        color = DressKitStyle.secondarySurface(),
                        border = BorderStroke(1.dp, DressKitStyle.borderColor())
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(DressKitStyle.buttonCorner()))
                                        .clickable {
                                            val next = (currentValue - 1).coerceAtLeast(0)
                                            countInputs[item.id] = next.toString()
                                        },
                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Remove,
                                            contentDescription = if (isEn) "Decrease" else "Azalt",
                                            tint = primaryInk,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = rawText,
                                    onValueChange = { typed ->
                                        val digits = typed.filter { it.isDigit() }.take(4)
                                        countInputs[item.id] = digits
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = primaryInk
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DressKitStyle.accentColor(),
                                        unfocusedBorderColor = DressKitStyle.borderColor(),
                                        focusedTextColor = primaryInk,
                                        unfocusedTextColor = primaryInk,
                                        focusedContainerColor = DressKitStyle.cardSurface(),
                                        unfocusedContainerColor = DressKitStyle.cardSurface()
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                Surface(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(DressKitStyle.buttonCorner()))
                                        .clickable {
                                            val next = (currentValue + 1).coerceAtMost(9999)
                                            countInputs[item.id] = next.toString()
                                        },
                                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                    color = DressKitStyle.buttonBg()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = if (isEn) "Increase" else "Artır",
                                            tint = DressKitStyle.buttonFg(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = itemsToEdit.associate { item ->
                        val parsed = countInputs[item.id]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        item.id to parsed
                    }
                    onSave(result)
                },
                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DressKitStyle.buttonBg(),
                    contentColor = DressKitStyle.buttonFg()
                )
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Cancel" else "İptal", color = secondaryInk)
            }
        }
    )
}
