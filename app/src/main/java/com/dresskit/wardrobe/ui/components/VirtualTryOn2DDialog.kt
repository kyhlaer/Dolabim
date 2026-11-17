package com.dresskit.wardrobe.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.theme.DressKitColors
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class TryOnLayer(
    val item: ClothingItem,
    var normX: Float,
    var normY: Float,
    var scale: Float,
    var rotationDeg: Float
)

@Composable
fun VirtualTryOn2DDialog(
    initialItems: List<ClothingItem>,
    allWardrobeItems: List<ClothingItem>,
    bodyPhotoVersion: Int,
    onPickBodyFromGallery: () -> Unit,
    onCaptureBodyFromCamera: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val isEn = lang == AppLanguage.EN

    var bodyImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Reload directly from disk whenever bodyPhotoVersion increments
    LaunchedEffect(bodyPhotoVersion) {
        val loaded = withContext(Dispatchers.IO) {
            val f = ImageStorage.getUserBodyPhotoFile(context)
            if (f.exists() && f.length() > 0L) {
                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
            } else {
                null
            }
        }
        bodyImageBitmap = loaded
    }

    val layers = remember {
        mutableStateListOf<TryOnLayer>().apply {
            initialItems.forEach { item ->
                add(defaultLayerFor(item))
            }
        }
    }

    var selectedLayerIndex by remember {
        mutableIntStateOf(if (layers.isNotEmpty()) 0 else -1)
    }
    var showAddPicker by remember { mutableStateOf(false) }

    val transparentBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    val activeFilesKey = layers.mapNotNull { it.item.photoFileNames.firstOrNull() }

    LaunchedEffect(activeFilesKey) {
        withContext(Dispatchers.Default) {
            layers.forEach { layer ->
                val studioName = layer.item.photoFileNames.firstOrNull() ?: return@forEach
                if (!transparentBitmaps.containsKey(studioName)) {
                    val file = ImageStorage.getPhotoFile(context, studioName)
                    if (file.exists()) {
                        val bmp = BitmapFactory.decodeFile(file.absolutePath)
                        if (bmp != null) {
                            val transparent = knockOutWhiteBackground(bmp)
                            bmp.recycle()
                            transparentBitmaps[studioName] = transparent.asImageBitmap()
                        }
                    }
                }
            }
        }
    }

    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = DressKitStyle.canvasBg(),
            topBar = {
                Surface(color = DressKitStyle.cardSurface()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Kapat", tint = primaryInk)
                                }
                                Column {
                                    Text(
                                        text = localizedText(lang, "2D Sanal Kabin", "2D Virtual Fitting", "2D-Anprobe", "Cabine 2D", "Probador 2D"),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryInk
                                    )
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Sürükle, iki parmakla yakınlaştırıp büyüt/küçült",
                                            "Drag, pinch anywhere with 2 fingers to resize & rotate",
                                            "Ziehen, mit 2 Fingern überall zoomen & drehen",
                                            "Glissez, pincez partout à 2 doigts pour ajuster",
                                            "Arrastra y pellizca con 2 dedos para ajustar"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryInk
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = onCaptureBodyFromCamera) {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoCamera,
                                        contentDescription = "Kamera",
                                        tint = primaryInk
                                    )
                                }
                                IconButton(onClick = onPickBodyFromGallery) {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoLibrary,
                                        contentDescription = "Galeri",
                                        tint = primaryInk
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                            color = DressKitStyle.accentSoft(),
                            border = BorderStroke(1.dp, DressKitStyle.accentColor())
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = DressKitStyle.accentColor(),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = localizedText(
                                        lang,
                                        "✨ AI Destekli 3D Sanal Kabin Çok Yakında — 360° Vücut & Kumaş Simülasyonu",
                                        "✨ AI-Powered 3D Virtual Fitting Room Coming Soon — 360° Body & Drape Simulation",
                                        "✨ KI-gestützte 3D-Anprobe bald verfügbar — 360°-Körper- & Stoffsimulation",
                                        "✨ Cabine 3D IA bientôt disponible — Simulation 360° corps & tissu",
                                        "✨ Probador Virtual 3D con IA muy pronto — Simulación 360° de cuerpo y tela"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DressKitStyle.accentColor()
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = DressKitStyle.cardSurface(),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(layers.size) { idx ->
                                val layer = layers[idx]
                                val selected = idx == selectedLayerIndex
                                Surface(
                                    onClick = { selectedLayerIndex = idx },
                                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                    color = if (selected) DressKitStyle.buttonBg() else DressKitStyle.secondarySurface()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = layer.item.subCategory.display(lang),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) DressKitStyle.buttonFg() else primaryInk
                                        )
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Kaldır",
                                            tint = if (selected) DressKitStyle.buttonFg() else secondaryInk,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    layers.removeAt(idx)
                                                    selectedLayerIndex = when {
                                                        layers.isEmpty() -> -1
                                                        idx >= layers.size -> layers.lastIndex
                                                        else -> idx
                                                    }
                                                }
                                        )
                                    }
                                }
                            }

                            item {
                                Surface(
                                    onClick = { showAddPicker = !showAddPicker },
                                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                    color = DressKitStyle.accentSoft(),
                                    border = BorderStroke(1.dp, DressKitStyle.accentColor())
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = null,
                                            tint = DressKitStyle.accentColor(),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isEn) "Add Piece" else "Parça Ekle",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DressKitStyle.accentColor()
                                        )
                                    }
                                }
                            }
                        }

                        if (showAddPicker) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(allWardrobeItems, key = { it.id }) { item ->
                                    val file = item.coverPhoto?.let { ImageStorage.getPhotoFile(context, it) }
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(RoundedCornerShape(DressKitStyle.buttonCorner()))
                                            .background(Color.White)
                                            .border(
                                                1.dp,
                                                DressKitStyle.borderColor(),
                                                RoundedCornerShape(DressKitStyle.buttonCorner())
                                            )
                                            .clickable {
                                                layers.add(defaultLayerFor(item))
                                                selectedLayerIndex = layers.lastIndex
                                                showAddPicker = false
                                            }
                                    ) {
                                        if (file != null && file.exists()) {
                                            AsyncImage(
                                                model = file,
                                                contentDescription = item.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val activeLayer = layers.getOrNull(selectedLayerIndex)
                        if (activeLayer != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (isEn) "Size" else "Boyut",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryInk
                                )
                                Slider(
                                    value = activeLayer.scale,
                                    onValueChange = { v ->
                                        layers[selectedLayerIndex] = activeLayer.copy(scale = v)
                                    },
                                    valueRange = 0.25f..1.85f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DressKitStyle.buttonBg(),
                                        activeTrackColor = DressKitStyle.buttonBg()
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        layers[selectedLayerIndex] = defaultLayerFor(activeLayer.item)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "Sıfırla",
                                        tint = secondaryInk
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(DressKitStyle.cardCorner()))
                    .background(DressKitStyle.cardSurface())
                    .border(
                        DressKitStyle.borderWidth(),
                        DressKitStyle.borderColor(),
                        RoundedCornerShape(DressKitStyle.cardCorner())
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2 && selectedLayerIndex in layers.indices) {
                                    val idx = selectedLayerIndex
                                    val cur = layers.getOrNull(idx) ?: continue
                                    val zoom = event.calculateZoom()
                                    val rot = event.calculateRotation()
                                    val pan = event.calculatePan()
                                    if (zoom != 1f || rot != 0f || pan != Offset.Zero) {
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        val nextScale = (cur.scale * zoom).coerceIn(0.25f, 1.85f)
                                        layers[idx] = cur.copy(
                                            normX = (cur.normX + pan.x / w).coerceIn(0.05f, 0.95f),
                                            normY = (cur.normY + pan.y / h).coerceIn(0.05f, 0.95f),
                                            scale = nextScale,
                                            rotationDeg = cur.rotationDeg + rot
                                        )
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    }
            ) {
                val canvasW = constraints.maxWidth.toFloat()
                val canvasH = constraints.maxHeight.toFloat()

                // 1. User Body Photo (directly loaded ImageBitmap) OR Mannequin Guide
                val loadedBody = bodyImageBitmap
                if (loadedBody != null) {
                    Image(
                        bitmap = loadedBody,
                        contentDescription = "Boy Fotoğrafım",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val headR = size.minDimension * 0.065f
                            val stroke = Stroke(width = 2.dp.toPx())
                            val guideColor = Color(0xFF9EA2AB)

                            drawCircle(
                                color = guideColor,
                                radius = headR,
                                center = Offset(cx, size.height * 0.14f),
                                style = stroke
                            )
                            val bodyPath = Path().apply {
                                moveTo(cx - size.width * 0.16f, size.height * 0.23f)
                                lineTo(cx + size.width * 0.16f, size.height * 0.23f)
                                lineTo(cx + size.width * 0.22f, size.height * 0.46f)
                                lineTo(cx + size.width * 0.13f, size.height * 0.50f)
                                lineTo(cx + size.width * 0.11f, size.height * 0.87f)
                                lineTo(cx + size.width * 0.02f, size.height * 0.87f)
                                lineTo(cx, size.height * 0.55f)
                                lineTo(cx - size.width * 0.02f, size.height * 0.87f)
                                lineTo(cx - size.width * 0.11f, size.height * 0.87f)
                                lineTo(cx - size.width * 0.13f, size.height * 0.50f)
                                lineTo(cx - size.width * 0.22f, size.height * 0.46f)
                                close()
                            }
                            drawPath(bodyPath, color = guideColor, style = stroke)
                        }

                        Surface(
                            onClick = onPickBodyFromGallery,
                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                            color = DressKitStyle.secondarySurface(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = primaryInk,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isEn) "Upload your full-body photo" else "Kendi boy fotoğrafını yükle",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = primaryInk
                                )
                            }
                        }
                    }
                }

                // 2. Multi-touch Drag + Pinch-to-Zoom + Rotate Garment Layers
                val baseSizePx = minOf(canvasW, canvasH) * 0.52f
                val density = LocalDensity.current
                layers.forEachIndexed { index, layer ->
                    val studioName = layer.item.photoFileNames.firstOrNull()
                    val cutoutBitmap = studioName?.let { transparentBitmaps[it] }
                    val isSelected = index == selectedLayerIndex

                    val cx = layer.normX * canvasW
                    val cy = layer.normY * canvasH

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (cx - baseSizePx / 2f).roundToInt(),
                                    (cy - baseSizePx / 2f).roundToInt()
                                )
                            }
                            .size(with(density) { baseSizePx.toDp() })
                            .graphicsLayer(
                                scaleX = layer.scale,
                                scaleY = layer.scale,
                                rotationZ = layer.rotationDeg
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) DressKitStyle.accentColor() else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .pointerInput(index, canvasW, canvasH) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    selectedLayerIndex = index
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        if (pressed.size == 1) {
                                            val change = pressed.first()
                                            val cur = layers.getOrNull(index) ?: continue
                                            val delta = change.positionChange()
                                            if (delta != Offset.Zero) {
                                                val rad = Math.toRadians(cur.rotationDeg.toDouble())
                                                val cosA = kotlin.math.cos(rad).toFloat()
                                                val sinA = kotlin.math.sin(rad).toFloat()
                                                val sx = delta.x * cur.scale
                                                val sy = delta.y * cur.scale
                                                val dx = (sx * cosA - sy * sinA) / canvasW.coerceAtLeast(1f)
                                                val dy = (sx * sinA + sy * cosA) / canvasH.coerceAtLeast(1f)
                                                layers[index] = cur.copy(
                                                    normX = (cur.normX + dx).coerceIn(0.05f, 0.95f),
                                                    normY = (cur.normY + dy).coerceIn(0.05f, 0.95f)
                                                )
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        if (cutoutBitmap != null) {
                            Image(
                                bitmap = cutoutBitmap,
                                contentDescription = layer.item.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun defaultLayerFor(item: ClothingItem): TryOnLayer {
    return when (item.category) {
        ClothingCategory.TOP -> TryOnLayer(item, normX = 0.50f, normY = 0.34f, scale = 0.68f, rotationDeg = 0f)
        ClothingCategory.OUTERWEAR -> TryOnLayer(item, normX = 0.50f, normY = 0.35f, scale = 0.74f, rotationDeg = 0f)
        ClothingCategory.BOTTOM -> TryOnLayer(item, normX = 0.50f, normY = 0.64f, scale = 0.74f, rotationDeg = 0f)
        ClothingCategory.SUIT -> TryOnLayer(item, normX = 0.50f, normY = 0.48f, scale = 0.88f, rotationDeg = 0f)
        ClothingCategory.SHOES -> TryOnLayer(item, normX = 0.50f, normY = 0.87f, scale = 0.42f, rotationDeg = 0f)
        ClothingCategory.ACCESSORY -> TryOnLayer(item, normX = 0.66f, normY = 0.24f, scale = 0.34f, rotationDeg = 0f)
    }
}

private fun knockOutWhiteBackground(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    for (i in pixels.indices) {
        val px = pixels[i]
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        if (r >= 244 && g >= 244 && b >= 244) {
            pixels[i] = android.graphics.Color.TRANSPARENT
        } else if (r >= 232 && g >= 232 && b >= 232) {
            val minC = minOf(r, g, b)
            val alpha = ((244 - minC) * 21).coerceIn(0, 255)
            pixels[i] = android.graphics.Color.argb(alpha, r, g, b)
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
