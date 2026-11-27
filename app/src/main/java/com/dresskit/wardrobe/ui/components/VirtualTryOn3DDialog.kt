package com.dresskit.wardrobe.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.dresskit.wardrobe.data.AiStudioService
import com.dresskit.wardrobe.data.BodySegmentationResult
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.StudioCutoutProcessor
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MESH_COLS = 12
private const val MESH_ROWS = 12

private data class TryOn3DLayer(
    val item: ClothingItem,
    var normX: Float,
    var normY: Float,
    var scale: Float,
    var rotationDeg: Float,
    var depthCurve: Float = 0.58f
)

@Composable
fun VirtualTryOn3DDialog(
    initialItems: List<ClothingItem>,
    allWardrobeItems: List<ClothingItem>,
    bodyPhotoVersion: Int,
    onPickBodyFromGallery: () -> Unit,
    onCaptureBodyFromCamera: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val isEn = lang != AppLanguage.TR
    val scope = rememberCoroutineScope()

    var rawBodyBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bodyImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var bodySeg by remember { mutableStateOf<BodySegmentationResult?>(null) }
    var lastFitRect by remember { mutableStateOf<RectF?>(null) }
    var lastCanvasSize by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var hasAutoFittedForCurrentPhoto by remember { mutableStateOf(false) }

    val layers = remember {
        mutableStateListOf<TryOn3DLayer>().apply {
            initialItems.forEach { item ->
                add(default3DLayerFor(item, null))
            }
        }
    }

    var selectedLayerIndex by remember {
        mutableIntStateOf(if (layers.isNotEmpty()) 0 else -1)
    }
    var showAddPicker by remember { mutableStateOf(false) }
    var bodyIntegrationEnabled by remember { mutableStateOf(true) }
    var lightHarmony by remember { mutableFloatStateOf(0.45f) }

    var isGeneratingAiTryOn by remember { mutableStateOf(false) }
    var aiTryOnResultBmp by remember { mutableStateOf<Bitmap?>(null) }
    var showOriginalInsteadOfAi by remember { mutableStateOf(false) }
    var showAiKeyPrompt by remember { mutableStateOf(false) }

    // Reload user body photo + compute on-device silhouette segmentation & landmarks
    LaunchedEffect(bodyPhotoVersion) {
        hasAutoFittedForCurrentPhoto = false
        val loadedPair = withContext(Dispatchers.IO) {
            val f = ImageStorage.getUserBodyPhotoFile(context)
            if (f.exists() && f.length() > 0L) {
                val bmp = ImageStorage.decodeOrientedBitmapFromFile(f)
                if (bmp != null) {
                    val seg = StudioCutoutProcessor.analyzeUserBodyFor3D(context, bmp)
                    bmp to seg
                } else {
                    null
                }
            } else {
                null
            }
        }
        rawBodyBitmap = loadedPair?.first
        bodyImageBitmap = loadedPair?.first?.asImageBitmap()
        bodySeg = loadedPair?.second
        aiTryOnResultBmp = null
        showOriginalInsteadOfAi = false
    }

    LaunchedEffect(bodySeg, lastFitRect, lastCanvasSize) {
        val seg = bodySeg ?: return@LaunchedEffect
        val fit = lastFitRect ?: return@LaunchedEffect
        val (cw, ch) = lastCanvasSize ?: return@LaunchedEffect
        if (!hasAutoFittedForCurrentPhoto && cw > 0f && ch > 0f) {
            for (i in layers.indices) {
                layers[i] = default3DLayerFor(layers[i].item, seg, fit, cw, ch)
            }
            hasAutoFittedForCurrentPhoto = true
        }
    }

    val cutoutBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    val rawGarmentBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    var removeGarmentBg by remember { mutableStateOf(true) }
    val activeFilesKey = layers.mapNotNull { it.item.coverPhoto ?: it.item.photoFileNames.firstOrNull() }

    LaunchedEffect(activeFilesKey) {
        val snapshotLayers = layers.toList()
        val missingCutouts = snapshotLayers.mapNotNull { it.item.photoFileNames.firstOrNull() }
            .distinct()
            .filterNot { cutoutBitmaps.containsKey(it) }
        val missingRaws = snapshotLayers.mapNotNull {
            it.item.originalPhotoFileNames.firstOrNull() ?: it.item.photoFileNames.firstOrNull()
        }.distinct().filterNot { rawGarmentBitmaps.containsKey(it) }

        val (decodedCutouts, decodedRaws) = withContext(Dispatchers.Default) {
            val cuts = LinkedHashMap<String, Bitmap>()
            for (studioName in missingCutouts) {
                runCatching {
                    val file = ImageStorage.getPhotoFile(context, studioName)
                    val bmp = ImageStorage.decodeOrientedBitmapFromFile(file)
                    if (bmp != null) {
                        val transparent = knockOutWhiteFor3D(bmp)
                        bmp.recycle()
                        cuts[studioName] = transparent
                    }
                }
            }
            val raws = LinkedHashMap<String, Bitmap>()
            for (rawName in missingRaws) {
                runCatching {
                    val rawFile = ImageStorage.getPhotoFile(context, rawName)
                    val rawBmp = ImageStorage.decodeOrientedBitmapFromFile(rawFile)
                    if (rawBmp != null) raws[rawName] = rawBmp
                }
            }
            cuts to raws
        }
        if (decodedCutouts.isNotEmpty()) cutoutBitmaps.putAll(decodedCutouts)
        if (decodedRaws.isNotEmpty()) rawGarmentBitmaps.putAll(decodedRaws)
    }

    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    if (showAiKeyPrompt) {
        AlertDialog(
            onDismissRequest = { showAiKeyPrompt = false },
            containerColor = DressKitStyle.cardSurface(),
            titleContentColor = primaryInk,
            textContentColor = secondaryInk,
            title = {
                Text(
                    localizedText(
                        lang,
                        "AI Gerçekçi Giydirme (Gemini)",
                        "AI Photorealistic Try-On (Gemini)",
                        "KI Fotorealistische Anprobe",
                        "Essayage IA Photoréaliste",
                        "Probador IA Fotorrealista",
                        "AI 真实感换装"
                    )
                )
            },
            text = {
                Text(
                    localizedText(
                        lang,
                        "Şu anda cihaz içi 3D kumaş kıvrımı + vücut silüeti entegrasyonu aktif olarak çalışıyor. Fotoğrafınla birebir fotogerçekçi AI giydirme için Ayarlar bölümünden kendi ücretsiz/kişisel Gemini API anahtarını ekleyebilirsin.",
                        "On-device 3D mesh drape and body silhouette integration are already active. For cloud photorealistic AI synthesis, add your own Gemini API key in Settings.",
                        "Geräteinterne 3D-Stoffsimulation ist aktiv. Für Cloud-KI hinterlege deinen Gemini-API-Schlüssel in den Einstellungen.",
                        "Le drapé 3D sur l'appareil est actif. Pour la synthèse IA cloud, ajoutez votre clé API Gemini dans les paramètres.",
                        "La simulación 3D local está activa. Para síntesis IA en la nube, añade tu clave API de Gemini en Ajustes.",
                        "本地 3D 布料贴合已激活。如需云端超逼真 AI 试穿，请在设置中填入你自己的 Gemini API 密钥。"
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAiKeyPrompt = false
                        onOpenAiSettings()
                    },
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DressKitStyle.buttonBg(),
                        contentColor = DressKitStyle.buttonFg()
                    )
                ) {
                    Text(if (isEn) "Open Settings" else "Ayarlara Git")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiKeyPrompt = false }) {
                    Text(if (isEn) "Keep Local 3D" else "Cihaz İçi 3D'de Kal", color = primaryInk)
                }
            }
        )
    }

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
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Kapat", tint = primaryInk)
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ViewInAr,
                                            contentDescription = null,
                                            tint = DressKitStyle.accentColor(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = localizedText(
                                                lang,
                                                "3D Sanal Kabin",
                                                "3D Virtual Fitting",
                                                "3D-Anprobe",
                                                "Cabine 3D",
                                                "Probador 3D",
                                                "3D 虚拟试衣间"
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = primaryInk
                                        )
                                    }
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Vücut entegrasyonlu 3D kıvrım · 2 parmakla büyüt/döndür",
                                            "Body-integrated 3D drape · Pinch anywhere with 2 fingers",
                                            "3D-Körperanpassung · Überall mit 2 Fingern zoomen",
                                            "Drapé 3D intégré · Pincez à 2 doigts pour ajuster",
                                            "Ajuste 3D al cuerpo · Pellizca con 2 dedos",
                                            "3D 身体贴合 · 双指任意缩放旋转"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryInk
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (bodySeg != null && layers.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            val seg = bodySeg
                                            val (cw, ch) = lastCanvasSize ?: (0f to 0f)
                                            for (i in layers.indices) {
                                                layers[i] = default3DLayerFor(layers[i].item, seg, lastFitRect, cw, ch)
                                            }
                                            aiTryOnResultBmp = null
                                            Toast.makeText(
                                                context,
                                                if (isEn) "Fitted to body contours" else "Kıyafetler vücut hatlarına oturtuldu",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CenterFocusStrong,
                                            contentDescription = "Auto Fit",
                                            tint = DressKitStyle.accentColor()
                                        )
                                    }
                                }
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

                        // Action Bar for Integration Mode + AI Photorealistic 3D Synthesis
                        LazyRow(
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = bodyIntegrationEnabled,
                                    onClick = { bodyIntegrationEnabled = !bodyIntegrationEnabled },
                                    label = {
                                        Text(
                                            text = if (isEn) "Body Silhouette" else "Vücut Entegrasyonu",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = DressKitStyle.accentSoft(),
                                        selectedLabelColor = DressKitStyle.accentColor()
                                    )
                                )
                            }

                            item {
                                FilterChip(
                                    selected = removeGarmentBg,
                                    onClick = { removeGarmentBg = !removeGarmentBg },
                                    label = {
                                        Text(
                                            text = if (removeGarmentBg) {
                                                if (isEn) "Remove BG" else "Arka Planı Kaldır"
                                            } else {
                                                if (isEn) "Keep BG" else "Arka Planı Koru"
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = DressKitStyle.accentSoft(),
                                        selectedLabelColor = DressKitStyle.accentColor()
                                    )
                                )
                            }

                            item {
                                Surface(
                                    onClick = {
                                        val body = rawBodyBitmap
                                        if (body == null) {
                                            Toast.makeText(
                                                context,
                                                if (isEn) "Please upload your photo first" else "Önce kendi fotoğrafını yüklemelisin",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@Surface
                                        }
                                        if (layers.isEmpty()) {
                                            Toast.makeText(
                                                context,
                                                if (isEn) "Add at least one garment first" else "Önce en az bir kıyafet seç",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@Surface
                                        }
                                        if (!AiStudioService.hasApiKey(context)) {
                                            showAiKeyPrompt = true
                                            return@Surface
                                        }
                                        val garmentPairs = layers.mapNotNull { layer ->
                                            val bmp = if (removeGarmentBg) {
                                                layer.item.photoFileNames.firstOrNull()?.let { cutoutBitmaps[it] }
                                            } else {
                                                (layer.item.originalPhotoFileNames.firstOrNull()
                                                    ?: layer.item.photoFileNames.firstOrNull())?.let { rawGarmentBitmaps[it] }
                                            } ?: return@mapNotNull null
                                            layer.item.name.ifBlank { layer.item.subCategory.display(lang) } to bmp
                                        }
                                        if (garmentPairs.isEmpty()) return@Surface
                                        scope.launch {
                                            isGeneratingAiTryOn = true
                                            val res = AiStudioService.generateVirtualTryOn3D(
                                                context = context,
                                                bodyBitmap = body,
                                                garments = garmentPairs
                                            )
                                            isGeneratingAiTryOn = false
                                            res.onSuccess { generated ->
                                                aiTryOnResultBmp = generated
                                                showOriginalInsteadOfAi = false
                                            }.onFailure { err ->
                                                Toast.makeText(
                                                    context,
                                                    err.localizedMessage ?: "AI Try-On error",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                    color = DressKitStyle.buttonBg()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = DressKitStyle.buttonFg(),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (isEn) "AI Realistic Fit" else "AI Gerçekçi Giydir",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DressKitStyle.buttonFg()
                                        )
                                    }
                                }
                            }

                            if (aiTryOnResultBmp != null) {
                                item {
                                    FilterChip(
                                        selected = showOriginalInsteadOfAi,
                                        onClick = { showOriginalInsteadOfAi = !showOriginalInsteadOfAi },
                                        label = {
                                            Text(
                                                text = if (showOriginalInsteadOfAi) {
                                                    if (isEn) "3D Mesh View" else "3D Önizleme"
                                                } else {
                                                    if (isEn) "AI Output (Tap Compare)" else "AI Çıktısı (Karşılaştır)"
                                                },
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                                item {
                                    Surface(
                                        onClick = {
                                            val bmpToSave = aiTryOnResultBmp ?: return@Surface
                                            scope.launch(Dispatchers.IO) {
                                                val ok = saveCompositeToGallery(context, bmpToSave)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        if (ok) {
                                                            if (isEn) "Saved to Pictures/Dolabim" else "Galeriye (Pictures/Dolabim) kaydedildi"
                                                        } else {
                                                            if (isEn) "Could not save" else "Kaydedilemedi"
                                                        },
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                        color = DressKitStyle.secondarySurface(),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = null,
                                                tint = primaryInk,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = if (isEn) "Save" else "Kaydet",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = primaryInk
                                            )
                                        }
                                    }
                                }
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
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(DressKitStyle.buttonCorner()))
                                            .background(Color.White)
                                            .border(
                                                1.dp,
                                                DressKitStyle.borderColor(),
                                                RoundedCornerShape(DressKitStyle.buttonCorner())
                                            )
                                            .clickable {
                                                val (cw, ch) = lastCanvasSize ?: (0f to 0f)
                                                layers.add(default3DLayerFor(item, bodySeg, lastFitRect, cw, ch))
                                                selectedLayerIndex = layers.lastIndex
                                                showAddPicker = false
                                                aiTryOnResultBmp = null
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
                        if (activeLayer != null && (aiTryOnResultBmp == null || showOriginalInsteadOfAi)) {
                            // 3D Depth Curvature & Size Sliders
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isEn) "Size" else "Boyut",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryInk,
                                    modifier = Modifier.width(52.dp)
                                )
                                Slider(
                                    value = activeLayer.scale,
                                    onValueChange = { v ->
                                        if (selectedLayerIndex in layers.indices) {
                                            layers[selectedLayerIndex] = activeLayer.copy(scale = v)
                                        }
                                    },
                                    valueRange = 0.25f..1.85f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DressKitStyle.buttonBg(),
                                        activeTrackColor = DressKitStyle.buttonBg()
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                )
                                IconButton(
                                    onClick = {
                                        if (selectedLayerIndex in layers.indices) {
                                            val (cw, ch) = lastCanvasSize ?: (0f to 0f)
                                            layers[selectedLayerIndex] = default3DLayerFor(activeLayer.item, bodySeg, lastFitRect, cw, ch)
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "Sıfırla",
                                        tint = secondaryInk,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isEn) "3D Wrap" else "3D Kıvrım",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryInk,
                                    modifier = Modifier.width(56.dp)
                                )
                                Slider(
                                    value = activeLayer.depthCurve,
                                    onValueChange = { v ->
                                        if (selectedLayerIndex in layers.indices) {
                                            layers[selectedLayerIndex] = activeLayer.copy(depthCurve = v)
                                        }
                                    },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DressKitStyle.accentColor(),
                                        activeTrackColor = DressKitStyle.accentColor()
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                )
                                Text(
                                    text = if (isEn) "Light" else "Işık",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryInk
                                )
                                Slider(
                                    value = lightHarmony,
                                    onValueChange = { lightHarmony = it },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DressKitStyle.accentColor(),
                                        activeTrackColor = DressKitStyle.accentColor()
                                    ),
                                    modifier = Modifier
                                        .weight(0.75f)
                                        .height(28.dp)
                                )
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
                    .pointerInput(layers.size) {
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                if (pressed.size >= 2 && selectedLayerIndex in layers.indices) {
                                    val idx = selectedLayerIndex
                                    val cur = layers.getOrNull(idx) ?: continue
                                    val zoom = event.calculateZoom()
                                    val rot = event.calculateRotation()
                                    val pan = event.calculatePan()
                                    if (zoom != 1f || rot != 0f || pan != Offset.Zero) {
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        layers[idx] = cur.copy(
                                            normX = (cur.normX + pan.x / w).coerceIn(0.05f, 0.95f),
                                            normY = (cur.normY + pan.y / h).coerceIn(0.05f, 0.95f),
                                            scale = (cur.scale * zoom).coerceIn(0.25f, 1.85f),
                                            rotationDeg = cur.rotationDeg + rot
                                        )
                                        event.changes.forEach { it.consume() }
                                    }
                                } else if (pressed.size == 1 && selectedLayerIndex in layers.indices) {
                                    val down = pressed.first()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    // Tap selects nearest garment layer if closer to another piece
                                    if (!down.previousPressed) {
                                        var nearestIdx = selectedLayerIndex
                                        var bestDist = Float.MAX_VALUE
                                        layers.forEachIndexed { idx, l ->
                                            val dx = l.normX * w - down.position.x
                                            val dy = l.normY * h - down.position.y
                                            val d2 = dx * dx + dy * dy
                                            if (d2 < bestDist) {
                                                bestDist = d2
                                                nearestIdx = idx
                                            }
                                        }
                                        val maxHitR = min(w, h) * 0.30f
                                        if (bestDist < maxHitR * maxHitR) {
                                            selectedLayerIndex = nearestIdx
                                        }
                                    }
                                    val idx = selectedLayerIndex
                                    val cur = layers.getOrNull(idx) ?: continue
                                    val delta = down.positionChange()
                                    if (delta != Offset.Zero) {
                                        layers[idx] = cur.copy(
                                            normX = (cur.normX + delta.x / w).coerceIn(0.05f, 0.95f),
                                            normY = (cur.normY + delta.y / h).coerceIn(0.05f, 0.95f)
                                        )
                                        down.consume()
                                    }
                                }
                            }
                        }
                    }
            ) {
                val canvasW = constraints.maxWidth.toFloat()
                val canvasH = constraints.maxHeight.toFloat()

                val aiBmp = aiTryOnResultBmp
                if (aiBmp != null && !showOriginalInsteadOfAi) {
                    Image(
                        bitmap = aiBmp.asImageBitmap(),
                        contentDescription = "AI 3D Try-On",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val loadedBody = bodyImageBitmap
                    val fitRect = remember(loadedBody, canvasW, canvasH) {
                        if (loadedBody != null && canvasW > 0f && canvasH > 0f) {
                            val imgRatio = loadedBody.width.toFloat() / loadedBody.height.toFloat().coerceAtLeast(1f)
                            val boxRatio = canvasW / canvasH
                            val drawW: Float
                            val drawH: Float
                            if (imgRatio > boxRatio) {
                                drawW = canvasW
                                drawH = canvasW / imgRatio
                            } else {
                                drawH = canvasH
                                drawW = canvasH * imgRatio
                            }
                            val left = (canvasW - drawW) * 0.5f
                            val top = (canvasH - drawH) * 0.5f
                            RectF(left, top, left + drawW, top + drawH)
                        } else {
                            null
                        }
                    }
                    LaunchedEffect(fitRect, canvasW, canvasH) {
                        lastFitRect = fitRect
                        if (canvasW > 0f && canvasH > 0f) {
                            lastCanvasSize = canvasW to canvasH
                        }
                    }

                    // 1. Background: User body photo OR clean mannequin silhouette guide
                    if (loadedBody != null && fitRect != null) {
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
                                        text = if (isEn) "Upload your photo for 3D try-on" else "3D deneme için kendi fotoğrafını yükle",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = primaryInk
                                    )
                                }
                            }
                        }
                    }

                    // 2. Real-time 3D Mesh Drape + Ambient Lighting + Body Silhouette Integration
                    val accentArgb = android.graphics.Color.argb(
                        220,
                        (DressKitStyle.accentColor().red * 255).toInt(),
                        (DressKitStyle.accentColor().green * 255).toInt(),
                        (DressKitStyle.accentColor().blue * 255).toInt()
                    )
                    // Read reactive state before Canvas block so Compose invalidates immediately on drag/slider
                    val layerSnapshot = layers.toList()
                    val selIdxSnapshot = selectedLayerIndex
                    val segSnapshot = bodySeg
                    val integrateSnapshot = bodyIntegrationEnabled
                    val removeBgSnapshot = removeGarmentBg
                    val harmonySnapshot = lightHarmony

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val native = drawContext.canvas.nativeCanvas
                        val baseSize = min(size.width, size.height) * 0.52f

                        // Sort naturally back-to-front: BOTTOM -> SHOES -> TOP -> SUIT -> OUTERWEAR -> ACCESSORY
                        val ordered = layerSnapshot.withIndex().sortedBy { (_, l) ->
                            categoryZOrder(l.item.category)
                        }

                        ordered.forEach { (origIdx, layer) ->
                            val garmentBmp = if (removeBgSnapshot) {
                                layer.item.photoFileNames.firstOrNull()?.let { cutoutBitmaps[it] }
                            } else {
                                (layer.item.originalPhotoFileNames.firstOrNull()
                                    ?: layer.item.photoFileNames.firstOrNull())?.let { rawGarmentBitmaps[it] }
                                    ?: layer.item.photoFileNames.firstOrNull()?.let { cutoutBitmaps[it] }
                            } ?: return@forEach
                            draw3DIntegratedGarment(
                                canvas = native,
                                garmentBmp = garmentBmp,
                                layer = layer,
                                canvasW = size.width,
                                canvasH = size.height,
                                baseBoxSize = baseSize,
                                bodySeg = if (integrateSnapshot) segSnapshot else null,
                                fitRect = fitRect,
                                lightHarmony = harmonySnapshot,
                                isSelected = origIdx == selIdxSnapshot,
                                accentColorArgb = accentArgb
                            )
                        }
                    }
                }

                if (isGeneratingAiTryOn) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DressKitStyle.cardSurface(),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor())
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = DressKitStyle.accentColor())
                                Text(
                                    text = if (isEn) {
                                        "Integrating garments onto your photo in 3D..."
                                    } else {
                                        "Kıyafetler fotoğrafına 3D entegre ediliyor..."
                                    },
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

private fun categoryZOrder(cat: ClothingCategory): Int = when (cat) {
    ClothingCategory.SHOES -> 0
    ClothingCategory.BOTTOM -> 1
    ClothingCategory.TOP -> 2
    ClothingCategory.SUIT -> 3
    ClothingCategory.OUTERWEAR -> 4
    ClothingCategory.ACCESSORY -> 5
}

private fun default3DLayerFor(
    item: ClothingItem,
    seg: BodySegmentationResult?,
    fitRect: RectF? = null,
    canvasW: Float = 0f,
    canvasH: Float = 0f
): TryOn3DLayer {
    if (seg == null) {
        return when (item.category) {
            ClothingCategory.TOP -> TryOn3DLayer(item, normX = 0.50f, normY = 0.34f, scale = 0.68f, rotationDeg = 0f)
            ClothingCategory.OUTERWEAR -> TryOn3DLayer(item, normX = 0.50f, normY = 0.35f, scale = 0.74f, rotationDeg = 0f)
            ClothingCategory.BOTTOM -> TryOn3DLayer(item, normX = 0.50f, normY = 0.64f, scale = 0.74f, rotationDeg = 0f)
            ClothingCategory.SUIT -> TryOn3DLayer(item, normX = 0.50f, normY = 0.48f, scale = 0.88f, rotationDeg = 0f)
            ClothingCategory.SHOES -> TryOn3DLayer(item, normX = 0.50f, normY = 0.87f, scale = 0.42f, rotationDeg = 0f)
            ClothingCategory.ACCESSORY -> TryOn3DLayer(item, normX = 0.66f, normY = 0.24f, scale = 0.34f, rotationDeg = 0f)
        }
    }

    fun toCanvasX(imgNormX: Float): Float {
        return if (fitRect != null && canvasW > 0f) {
            ((fitRect.left + imgNormX * fitRect.width()) / canvasW).coerceIn(0.08f, 0.92f)
        } else {
            imgNormX
        }
    }

    fun toCanvasY(imgNormY: Float): Float {
        return if (fitRect != null && canvasH > 0f) {
            ((fitRect.top + imgNormY * fitRect.height()) / canvasH).coerceIn(0.08f, 0.92f)
        } else {
            imgNormY
        }
    }

    val widthScaleAdj = if (fitRect != null && canvasW > 0f && canvasH > 0f) {
        (fitRect.width() / min(canvasW, canvasH).coerceAtLeast(1f)).coerceIn(0.45f, 1.35f)
    } else {
        1f
    }

    val torsoMidY = toCanvasY((seg.shoulderY + seg.waistY) * 0.52f)
    val legsMidY = toCanvasY((seg.waistY + seg.ankleY) * 0.50f)
    val centerX = toCanvasX(seg.headCenterX)
    val shoulderScale = (seg.shoulderSpanNorm * widthScaleAdj * 1.38f).coerceIn(0.40f, 1.50f)

    return when (item.category) {
        ClothingCategory.TOP -> TryOn3DLayer(
            item = item,
            normX = centerX,
            normY = torsoMidY,
            scale = shoulderScale,
            rotationDeg = 0f,
            depthCurve = 0.60f
        )
        ClothingCategory.OUTERWEAR -> TryOn3DLayer(
            item = item,
            normX = centerX,
            normY = (torsoMidY + 0.01f).coerceIn(0.08f, 0.92f),
            scale = (shoulderScale * 1.12f).coerceIn(0.45f, 1.60f),
            rotationDeg = 0f,
            depthCurve = 0.64f
        )
        ClothingCategory.BOTTOM -> TryOn3DLayer(
            item = item,
            normX = centerX,
            normY = legsMidY,
            scale = (shoulderScale * 1.15f).coerceIn(0.45f, 1.60f),
            rotationDeg = 0f,
            depthCurve = 0.54f
        )
        ClothingCategory.SUIT -> TryOn3DLayer(
            item = item,
            normX = centerX,
            normY = toCanvasY((seg.shoulderY + seg.ankleY) * 0.48f),
            scale = (shoulderScale * 1.25f).coerceIn(0.50f, 1.65f),
            rotationDeg = 0f,
            depthCurve = 0.60f
        )
        ClothingCategory.SHOES -> TryOn3DLayer(
            item = item,
            normX = centerX,
            normY = toCanvasY(seg.ankleY),
            scale = (shoulderScale * 0.65f).coerceIn(0.28f, 0.95f),
            rotationDeg = 0f,
            depthCurve = 0.35f
        )
        ClothingCategory.ACCESSORY -> TryOn3DLayer(
            item = item,
            normX = toCanvasX((seg.headCenterX + seg.shoulderSpanNorm * 0.52f).coerceIn(0.15f, 0.85f)),
            normY = toCanvasY(seg.waistY),
            scale = (shoulderScale * 0.55f).coerceIn(0.25f, 0.80f),
            rotationDeg = 0f,
            depthCurve = 0.35f
        )
    }
}

private fun draw3DIntegratedGarment(
    canvas: AndroidCanvas,
    garmentBmp: Bitmap,
    layer: TryOn3DLayer,
    canvasW: Float,
    canvasH: Float,
    baseBoxSize: Float,
    bodySeg: BodySegmentationResult?,
    fitRect: RectF?,
    lightHarmony: Float,
    isSelected: Boolean,
    accentColorArgb: Int
) {
    val cx = layer.normX * canvasW
    val cy = layer.normY * canvasH
    val bmpAspect = garmentBmp.width.toFloat() / garmentBmp.height.toFloat().coerceAtLeast(1f)
    val boxSize = baseBoxSize * layer.scale
    val drawW = if (bmpAspect >= 1f) boxSize else boxSize * bmpAspect
    val drawH = if (bmpAspect >= 1f) boxSize / bmpAspect else boxSize

    val curve = layer.depthCurve.coerceIn(0f, 1f)
    val verts = FloatArray((MESH_COLS + 1) * (MESH_ROWS + 1) * 2)
    val rad = Math.toRadians(layer.rotationDeg.toDouble())
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()

    var vi = 0
    for (row in 0..MESH_ROWS) {
        val v = row.toFloat() / MESH_ROWS
        for (col in 0..MESH_COLS) {
            val u = col.toFloat() / MESH_COLS
            val nx = (u - 0.5f) * 2f // -1..1

            // Cylindrical horizontal wrap compression around body edges
            val maxTheta = (PI.toFloat() * 0.42f * curve).coerceAtLeast(0.001f)
            val wrappedNx = if (curve > 0.02f) {
                sin(nx * maxTheta) / sin(maxTheta)
            } else {
                nx
            }

            // Category-specific 3D contour (shoulder/waist taper + curved hem + chest depth)
            val widthFactor = when (layer.item.category) {
                ClothingCategory.TOP, ClothingCategory.OUTERWEAR, ClothingCategory.SUIT ->
                    1f - 0.08f * curve * v + 0.04f * curve * sin(v * PI.toFloat())
                ClothingCategory.BOTTOM ->
                    1f - 0.06f * curve * v
                else -> 1f
            }

            val yArc = when (layer.item.category) {
                ClothingCategory.TOP, ClothingCategory.OUTERWEAR, ClothingCategory.SUIT ->
                    -(1f - nx * nx) * drawH * 0.032f * curve * (0.25f + 0.75f * v)
                ClothingCategory.BOTTOM ->
                    (1f - nx * nx) * drawH * 0.024f * curve * (1f - v)
                else -> 0f
            }

            val lx = wrappedNx * (drawW * 0.5f) * widthFactor
            val ly = (v - 0.5f) * drawH + yArc

            val rx = lx * cosA - ly * sinA + cx
            val ry = lx * sinA + ly * cosA + cy
            verts[vi++] = rx
            verts[vi++] = ry
        }
    }

    // Build ambient color-temperature filter from user photo lighting
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (bodySeg != null && lightHarmony > 0.01f) {
        val amb = bodySeg.ambientColor
        val ar = AndroidColor.red(amb) / 255f
        val ag = AndroidColor.green(amb) / 255f
        val ab = AndroidColor.blue(amb) / 255f
        val avg = ((ar + ag + ab) / 3f).coerceAtLeast(0.25f)
        val castStrength = lightHarmony * 0.38f
        val exposure = (1f - lightHarmony * 0.25f) + (avg * 1.15f).coerceIn(0.72f, 1.12f) * (lightHarmony * 0.25f)
        val scaleR = ((1f - castStrength) + (ar / avg) * castStrength) * exposure
        val scaleG = ((1f - castStrength) + (ag / avg) * castStrength) * exposure
        val scaleB = ((1f - castStrength) + (ab / avg) * castStrength) * exposure
        val cm = ColorMatrix(
            floatArrayOf(
                scaleR, 0f, 0f, 0f, 0f,
                0f, scaleG, 0f, 0f, 0f,
                0f, 0f, scaleB, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
    }

    // Subtle soft body drop shadow behind garment for depth
    val saveLayer = canvas.saveLayer(0f, 0f, canvasW, canvasH, null)
    if (bodySeg != null && fitRect != null) {
        val clipPadW = fitRect.width() * 0.045f
        canvas.clipRect(
            fitRect.left - clipPadW,
            fitRect.top,
            fitRect.right + clipPadW,
            fitRect.bottom
        )
    }

    if (curve > 0.1f) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(
                AndroidColor.argb((38 * curve).toInt().coerceIn(8, 45), 0, 0, 0),
                PorterDuff.Mode.SRC_IN
            )
        }
        val shadowVerts = verts.copyOf()
        for (i in shadowVerts.indices step 2) {
            shadowVerts[i] += 5f * curve
            shadowVerts[i + 1] += 8f * curve
        }
        canvas.drawBitmapMesh(garmentBmp, MESH_COLS, MESH_ROWS, shadowVerts, 0, null, 0, shadowPaint)
    }

    // Main 3D mesh garment draw
    canvas.drawBitmapMesh(garmentBmp, MESH_COLS, MESH_ROWS, verts, 0, null, 0, paint)

    // 3D Cylindrical side shading (subtle highlight along center spine, shaded sides via SRC_ATOP)
    if (curve > 0.05f) {
        val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            val edgeAlpha = (64 * curve).roundToInt().coerceIn(0, 72)
            val midAlpha = (14 * curve).roundToInt().coerceIn(0, 18)
            shader = LinearGradient(
                cx - drawW * 0.5f, cy,
                cx + drawW * 0.5f, cy,
                intArrayOf(
                    AndroidColor.argb(edgeAlpha, 0, 0, 0),
                    AndroidColor.argb(midAlpha, 255, 255, 255),
                    AndroidColor.argb(edgeAlpha, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(
            cx - drawW * 0.65f,
            cy - drawH * 0.65f,
            cx + drawW * 0.65f,
            cy + drawH * 0.65f,
            shadePaint
        )
    }

    // Body Silhouette Integration + Neck/Head Protection above chin line
    if (bodySeg != null && fitRect != null) {
        // Slightly expanded person silhouette clip with soft edges so background edges don't stick out awkwardly
        val padW = fitRect.width() * 0.045f
        val expandedDst = RectF(
            fitRect.left - padW,
            fitRect.top,
            fitRect.right + padW,
            fitRect.bottom
        )
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            alpha = 235
        }
        canvas.drawBitmap(bodySeg.personMask, null, expandedDst, maskPaint)

        // Ensure face / head stays visibly above top collar/hoodie edge (unless layer is an accessory near head)
        if (layer.item.category != ClothingCategory.ACCESSORY) {
            val neckScreenY = fitRect.top + bodySeg.neckY * fitRect.height()
            val fadeSpan = (fitRect.height() * 0.035f).coerceAtLeast(10f)
            val headCutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                shader = LinearGradient(
                    0f, neckScreenY - fadeSpan,
                    0f, neckScreenY,
                    AndroidColor.argb(245, 0, 0, 0),
                    AndroidColor.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(
                fitRect.left,
                0f,
                fitRect.right,
                neckScreenY,
                headCutPaint
            )
        }
    }

    canvas.restoreToCount(saveLayer)

    if (isSelected) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColorArgb
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.save()
        canvas.rotate(layer.rotationDeg, cx, cy)
        canvas.drawRoundRect(
            RectF(cx - drawW * 0.5f, cy - drawH * 0.5f, cx + drawW * 0.5f, cy + drawH * 0.5f),
            12f,
            12f,
            borderPaint
        )
        canvas.restore()
    }
}

private fun knockOutWhiteFor3D(src: Bitmap): Bitmap {
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
            pixels[i] = AndroidColor.TRANSPARENT
        } else if (r >= 230 && g >= 230 && b >= 230) {
            val minC = min(r, min(g, b))
            val alpha = ((244 - minC) * 18).coerceIn(0, 255)
            pixels[i] = AndroidColor.argb(alpha, r, g, b)
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun saveCompositeToGallery(context: Context, bmp: Bitmap): Boolean {
    val fileName = "Dolabim_3D_${System.currentTimeMillis()}.jpg"
    val resolver = context.contentResolver
    var insertedUri: android.net.Uri? = null
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Dolabim")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            insertedUri = uri
            val wrote = resolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } == true
            if (!wrote) {
                runCatching { resolver.delete(uri, null, null) }
                return false
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val savedUrl = MediaStore.Images.Media.insertImage(resolver, bmp, fileName, "Dolabim 3D")
            !savedUrl.isNullOrBlank()
        }
    } catch (_: Exception) {
        insertedUri?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
        false
    }
}
