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
import androidx.compose.runtime.mutableFloatStateOf
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

private enum class CropAspectMode(val labelTr: String, val labelEn: String, val ratio: Float?) {
    FREE("Serbest", "Free", null),
    SQUARE("1:1", "1:1", 1f),
    PORTRAIT("3:4", "3:4", 0.75f);

    fun label(lang: AppLanguage): String = if (lang == AppLanguage.EN) labelEn else labelTr
}

private enum class DragTarget {
    NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
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
    var workingBitmap by remember(sourceBitmap) { mutableStateOf(sourceBitmap) }
    var aspectMode by remember(sourceBitmap) { mutableStateOf(CropAspectMode.FREE) }

    var normLeft by remember(workingBitmap) { mutableFloatStateOf(0.06f) }
    var normTop by remember(workingBitmap) { mutableFloatStateOf(0.06f) }
    var normRight by remember(workingBitmap) { mutableFloatStateOf(0.94f) }
    var normBottom by remember(workingBitmap) { mutableFloatStateOf(0.94f) }

    val imageBitmap = remember(workingBitmap) { workingBitmap.asImageBitmap() }

    Dialog(
        onDismissRequest = onCancel,
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
                    IconButton(onClick = onCancel) {
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
                        onClick = {
                            val matrix = Matrix().apply { postRotate(90f) }
                            val rotated = Bitmap.createBitmap(
                                workingBitmap,
                                0,
                                0,
                                workingBitmap.width,
                                workingBitmap.height,
                                matrix,
                                true
                            )
                            workingBitmap = rotated
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
                                    onClick = {
                                        aspectMode = mode
                                        mode.ratio?.let { targetAspect ->
                                            val imgRatio = workingBitmap.width.toFloat() /
                                                workingBitmap.height.toFloat().coerceAtLeast(1f)
                                            val cx = (normLeft + normRight) / 2f
                                            val cy = (normTop + normBottom) / 2f
                                            var nw = (normRight - normLeft).coerceIn(0.25f, 0.88f)
                                            var nh = (nw * imgRatio) / targetAspect
                                            if (nh > 0.88f) {
                                                nh = 0.88f
                                                nw = (nh * targetAspect) / imgRatio
                                            }
                                            normLeft = (cx - nw / 2f).coerceIn(0f, 1f - nw)
                                            normTop = (cy - nh / 2f).coerceIn(0f, 1f - nh)
                                            normRight = normLeft + nw
                                            normBottom = normTop + nh
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
                                onClick = { onUseFull(workingBitmap) },
                                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                border = BorderStroke(1.dp, Color(0xFF555550)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isEn) "Use Full" else "Kırpmadan Kullan", maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    val x = (normLeft * workingBitmap.width).toInt()
                                        .coerceIn(0, workingBitmap.width - 1)
                                    val y = (normTop * workingBitmap.height).toInt()
                                        .coerceIn(0, workingBitmap.height - 1)
                                    val w = ((normRight - normLeft) * workingBitmap.width).toInt()
                                        .coerceIn(16, workingBitmap.width - x)
                                    val h = ((normBottom - normTop) * workingBitmap.height).toInt()
                                        .coerceIn(16, workingBitmap.height - y)
                                    val cropped = Bitmap.createBitmap(workingBitmap, x, y, w, h)
                                    onCropConfirmed(cropped)
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
                val bmpRatio = workingBitmap.width.toFloat() / workingBitmap.height.toFloat().coerceAtLeast(1f)
                val boxRatio = containerW / containerH.coerceAtLeast(1f)

                val drawW: Float
                val drawH: Float
                if (bmpRatio > boxRatio) {
                    drawW = containerW
                    drawH = containerW / bmpRatio
                } else {
                    drawH = containerH
                    drawW = containerH * bmpRatio
                }

                val density = LocalDensity.current
                val widthDp = with(density) { drawW.toDp() }
                val heightDp = with(density) { drawH.toDp() }
                val handleHitRadius = with(density) { 30.dp.toPx() }
                val minCropNorm = 0.16f

                var activeDrag by remember { mutableStateOf(DragTarget.NONE) }

                Box(
                    modifier = Modifier
                        .size(widthDp, heightDp)
                        .pointerInput(workingBitmap, drawW, drawH, aspectMode) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val lx = normLeft * drawW
                                    val ty = normTop * drawH
                                    val rx = normRight * drawW
                                    val by = normBottom * drawH

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
                                    val dx = dragAmount.x / drawW
                                    val dy = dragAmount.y / drawH
                                    when (activeDrag) {
                                        DragTarget.MOVE -> {
                                            val w = normRight - normLeft
                                            val h = normBottom - normTop
                                            val nl = (normLeft + dx).coerceIn(0f, 1f - w)
                                            val nt = (normTop + dy).coerceIn(0f, 1f - h)
                                            normLeft = nl
                                            normTop = nt
                                            normRight = nl + w
                                            normBottom = nt + h
                                        }
                                        DragTarget.TOP_LEFT -> {
                                            normLeft = min(normLeft + dx, normRight - minCropNorm).coerceAtLeast(0f)
                                            normTop = min(normTop + dy, normBottom - minCropNorm).coerceAtLeast(0f)
                                        }
                                        DragTarget.TOP_RIGHT -> {
                                            normRight = max(normRight + dx, normLeft + minCropNorm).coerceAtMost(1f)
                                            normTop = min(normTop + dy, normBottom - minCropNorm).coerceAtLeast(0f)
                                        }
                                        DragTarget.BOTTOM_LEFT -> {
                                            normLeft = min(normLeft + dx, normRight - minCropNorm).coerceAtLeast(0f)
                                            normBottom = max(normBottom + dy, normTop + minCropNorm).coerceAtMost(1f)
                                        }
                                        DragTarget.BOTTOM_RIGHT -> {
                                            normRight = max(normRight + dx, normLeft + minCropNorm).coerceAtMost(1f)
                                            normBottom = max(normBottom + dy, normTop + minCropNorm).coerceAtMost(1f)
                                        }
                                        DragTarget.NONE -> Unit
                                    }
                                    aspectMode.ratio?.let { targetAspect ->
                                        if (activeDrag != DragTarget.MOVE && activeDrag != DragTarget.NONE) {
                                            val curW = normRight - normLeft
                                            val desiredH = ((curW * bmpRatio) / targetAspect)
                                            val clampedH = desiredH.coerceIn(minCropNorm, 1f - normTop)
                                            normBottom = (normTop + clampedH).coerceAtMost(1f)
                                        }
                                    }
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
                        val lx = normLeft * size.width
                        val ty = normTop * size.height
                        val rx = normRight * size.width
                        val by = normBottom * size.height

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
