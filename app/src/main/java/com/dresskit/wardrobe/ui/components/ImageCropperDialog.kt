package com.dresskit.wardrobe.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class CropAspectMode(
    val labelTr: String,
    val labelEn: String,
    val ratioWidth: Int = 0,
    val ratioHeight: Int = 0
) {
    FREE("Serbest", "Free"),
    SQUARE("1:1", "1:1", 1, 1),
    PORTRAIT("3:4", "3:4", 3, 4);

    val ratio: Float? get() = if (ratioHeight > 0) ratioWidth.toFloat() / ratioHeight else null
    fun label(lang: AppLanguage): String = if (lang == AppLanguage.EN) labelEn else labelTr
}

private enum class DragTarget {
    NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

private fun cropRectAt(center: Offset, width: Float, height: Float): Rect {
    val scale = minOf(1f, 1f / width, 1f / height)
    val w = width * scale
    val h = height * scale
    val left = (center.x - w / 2f).coerceIn(0f, (1f - w).coerceAtLeast(0f))
    val top = (center.y - h / 2f).coerceIn(0f, (1f - h).coerceAtLeast(0f))
    return Rect(left, top, left + w, top + h)
}

private fun resizeCrop(
    crop: Rect,
    target: DragTarget,
    delta: Offset,
    imageRatio: Float,
    aspect: Float?
): Rect {
    if (target == DragTarget.NONE) return crop
    if (target == DragTarget.MOVE) return cropRectAt(crop.center + delta, crop.width, crop.height)

    val fromLeft = target == DragTarget.TOP_LEFT || target == DragTarget.BOTTOM_LEFT
    val fromTop = target == DragTarget.TOP_LEFT || target == DragTarget.TOP_RIGHT
    val anchorX = if (fromLeft) crop.right else crop.left
    val anchorY = if (fromTop) crop.bottom else crop.top
    val signX = if (fromLeft) -1f else 1f
    val signY = if (fromTop) -1f else 1f
    val maxWidth = if (fromLeft) anchorX else 1f - anchorX
    val maxHeight = if (fromTop) anchorY else 1f - anchorY
    val minSize = 0.16f
    var width = crop.width + delta.x * signX
    var height = crop.height + delta.y * signY
    if (aspect != null) {
        val heightPerWidth = imageRatio / aspect
        val maxAspectWidth = min(maxWidth, maxHeight / heightPerWidth)
        val minAspectWidth = min(minSize, minSize / heightPerWidth)
        width = (if (abs(delta.x) * imageRatio >= abs(delta.y)) width else height / heightPerWidth)
            .coerceIn(min(minAspectWidth, maxAspectWidth), maxAspectWidth)
        height = width * heightPerWidth
    } else {
        width = width.coerceIn(min(minSize, maxWidth), maxWidth)
        height = height.coerceIn(min(minSize, maxHeight), maxHeight)
    }
    val x = anchorX + signX * width
    val y = anchorY + signY * height
    return Rect(min(x, anchorX), min(y, anchorY), max(x, anchorX), max(y, anchorY))
}

private fun cropBitmap(bitmap: Bitmap, crop: Rect, mode: CropAspectMode): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (mode.ratio != null) {
        val maxUnits = min(width / mode.ratioWidth, height / mode.ratioHeight)
        if (maxUnits > 0) {
            val units = (crop.width * width / mode.ratioWidth).roundToInt().coerceIn(1, maxUnits)
            val w = units * mode.ratioWidth
            val h = units * mode.ratioHeight
            val left = (crop.center.x * width - w / 2f).roundToInt().coerceIn(0, width - w)
            val top = (crop.center.y * height - h / 2f).roundToInt().coerceIn(0, height - h)
            return Bitmap.createBitmap(bitmap, left, top, w, h)
        }
    }
    val left = (crop.left * width).roundToInt().coerceIn(0, width - 1)
    val top = (crop.top * height).roundToInt().coerceIn(0, height - 1)
    val right = (crop.right * width).roundToInt().coerceIn(left + 1, width)
    val bottom = (crop.bottom * height).roundToInt().coerceIn(top + 1, height)
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

@Composable
fun ImageCropperDialog(
    sourceBitmap: Bitmap,
    queueLabel: String? = null,
    onCropConfirmed: (Bitmap) -> Unit,
    onUseFull: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val isEn = lang == AppLanguage.EN
    val accentHandleColor = DressKitStyle.accentColor()
    var rotation by remember(sourceBitmap) { mutableIntStateOf(0) }
    var aspectMode by remember(sourceBitmap) { mutableStateOf(CropAspectMode.FREE) }
    var crop by remember(sourceBitmap) { mutableStateOf(Rect(0.06f, 0.06f, 0.94f, 0.94f)) }
    var saving by remember(sourceBitmap) { mutableStateOf(false) }
    val workingBitmap = remember(sourceBitmap, rotation) {
        if (rotation == 0) sourceBitmap else Bitmap.createBitmap(
            sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, false
        )
    }
    val imageRatio = workingBitmap.width.toFloat() / workingBitmap.height
    val imageBitmap = remember(workingBitmap) { workingBitmap.asImageBitmap() }

    Dialog(
        onDismissRequest = { if (!saving) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = Color(0xFF141413),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel, enabled = !saving) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = if (isEn) "Cancel" else "İptal",
                            tint = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isEn) "Crop Photo" else "Fotoğrafı Kırp",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        if (queueLabel != null) {
                            Text(
                                text = queueLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB5B5B0)
                            )
                        }
                    }
                    IconButton(
                        enabled = !saving,
                        onClick = {
                            val center = Offset(1f - crop.center.y, crop.center.x)
                            crop = when (aspectMode) {
                                CropAspectMode.FREE -> Rect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right)
                                CropAspectMode.SQUARE -> cropRectAt(center, crop.height, crop.width)
                                CropAspectMode.PORTRAIT -> cropRectAt(center, crop.width * imageRatio, crop.height / imageRatio)
                            }
                            rotation = (rotation + 90) % 360
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.RotateRight,
                            contentDescription = if (isEn) "Rotate 90°" else "90° Döndür",
                            tint = Color.White
                        )
                    }
                }
            },
            bottomBar = {
                Surface(color = Color(0xFF1E1E1C)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CropAspectMode.entries.forEach { mode ->
                                val selected = aspectMode == mode
                                Surface(
                                    enabled = !saving,
                                    onClick = {
                                        if (selected) return@Surface
                                        aspectMode = mode
                                        mode.ratio?.let { targetAspect ->
                                            val heightPerWidth = imageRatio / targetAspect
                                            val width = min(crop.width.coerceIn(0.25f, 0.88f), 0.88f / heightPerWidth)
                                            crop = cropRectAt(crop.center, width, width * heightPerWidth)
                                        }
                                    },
                                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                    color = if (selected) Color.White else Color(0xFF2D2D2A),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = mode.label(lang),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) Color.Black else Color.White,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                enabled = !saving,
                                onClick = {
                                    if (!saving) {
                                        saving = true
                                        onUseFull(workingBitmap)
                                    }
                                },
                                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                border = BorderStroke(1.dp, Color(0xFF555550)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isEn) "Use Full" else "Kırpmadan Kullan", maxLines = 1)
                            }
                            Button(
                                enabled = !saving,
                                onClick = {
                                    if (saving) return@Button
                                    saving = true
                                    onCropConfirmed(cropBitmap(workingBitmap, crop, aspectMode))
                                },
                                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isEn) "Crop & Add" else "Kırp & Ekle", maxLines = 1)
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
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val containerW = constraints.maxWidth.toFloat()
                val containerH = constraints.maxHeight.toFloat()
                val boxRatio = containerW / containerH.coerceAtLeast(1f)

                val drawW: Float
                val drawH: Float
                if (imageRatio > boxRatio) {
                    drawW = containerW
                    drawH = containerW / imageRatio
                } else {
                    drawH = containerH
                    drawW = containerH * imageRatio
                }

                val density = LocalDensity.current
                val widthDp = with(density) { drawW.toDp() }
                val heightDp = with(density) { drawH.toDp() }
                val handleHitRadius = with(density) { 30.dp.toPx() }
                var activeDrag by remember { mutableStateOf(DragTarget.NONE) }

                Box(
                    modifier = Modifier
                        .size(widthDp, heightDp)
                        .pointerInput(workingBitmap, drawW, drawH, aspectMode, saving) {
                            if (saving || drawW <= 0f || drawH <= 0f) return@pointerInput
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val lx = crop.left * drawW
                                    val ty = crop.top * drawH
                                    val rx = crop.right * drawW
                                    val by = crop.bottom * drawH

                                    fun dist(ax: Float, ay: Float): Float {
                                        return abs(pos.x - ax) + abs(pos.y - ay)
                                    }

                                    activeDrag = when {
                                        dist(lx, ty) < handleHitRadius * 1.6f -> DragTarget.TOP_LEFT
                                        dist(rx, ty) < handleHitRadius * 1.6f -> DragTarget.TOP_RIGHT
                                        dist(lx, by) < handleHitRadius * 1.6f -> DragTarget.BOTTOM_LEFT
                                        dist(rx, by) < handleHitRadius * 1.6f -> DragTarget.BOTTOM_RIGHT
                                        pos.x in lx..rx && pos.y in ty..by -> DragTarget.MOVE
                                        else -> DragTarget.NONE
                                    }
                                },
                                onDragEnd = { activeDrag = DragTarget.NONE },
                                onDragCancel = { activeDrag = DragTarget.NONE },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    crop = resizeCrop(
                                        crop, activeDrag, Offset(dragAmount.x / drawW, dragAmount.y / drawH),
                                        imageRatio, aspectMode.ratio
                                    )
                                }
                            )
                        }
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val lx = crop.left * size.width
                        val ty = crop.top * size.height
                        val rx = crop.right * size.width
                        val by = crop.bottom * size.height

                        val scrimPath = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addRect(Rect(lx, ty, rx, by))
                        }
                        drawPath(scrimPath, color = Color.Black.copy(alpha = 0.56f))

                        // Crisp white frame
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(lx, ty),
                            size = Size(rx - lx, by - ty),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Rule-of-thirds guides
                        val thirdW = (rx - lx) / 3f
                        val thirdH = (by - ty) / 3f
                        for (i in 1..2) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.35f),
                                start = Offset(lx + thirdW * i, ty),
                                end = Offset(lx + thirdW * i, by),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.35f),
                                start = Offset(lx, ty + thirdH * i),
                                end = Offset(rx, ty + thirdH * i),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Corner handle circles
                        val r = 7.dp.toPx()
                        listOf(
                            Offset(lx, ty),
                            Offset(rx, ty),
                            Offset(lx, by),
                            Offset(rx, by)
                        ).forEach { corner ->
                            drawCircle(color = Color.White, radius = r, center = corner)
                            drawCircle(color = accentHandleColor, radius = r * 0.45f, center = corner)
                        }
                    }
                }
            }
        }
    }
}
