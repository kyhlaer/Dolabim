package com.dresskit.wardrobe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Adds a clipped spring overscroll bounce so scrolled content never overlaps headers above it.
 */
fun Modifier.bouncyOverscroll(maxOffsetPx: Float = 56f): Modifier = composed {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val cur = offset.value
                if (cur != 0f && source == NestedScrollSource.UserInput) {
                    if ((cur > 0f && available.y < 0f) || (cur < 0f && available.y > 0f)) {
                        val next = cur + available.y * 0.55f
                        val clamped = if (cur > 0f) next.coerceAtLeast(0f) else next.coerceAtMost(0f)
                        val consumed = (clamped - cur) / 0.55f
                        scope.launch { offset.snapTo(clamped) }
                        return Offset(0f, consumed)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y != 0f && source == NestedScrollSource.UserInput) {
                    val resistance = 0.24f
                    val next = (offset.value + available.y * resistance)
                        .coerceIn(-maxOffsetPx, maxOffsetPx)
                    scope.launch { offset.snapTo(next) }
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (abs(available.y) > 400f && offset.value == 0f) {
                    val kick = (available.y * 0.012f).coerceIn(-maxOffsetPx * 0.4f, maxOffsetPx * 0.4f)
                    offset.snapTo(kick)
                }
                if (offset.value != 0f) {
                    offset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                return Velocity.Zero
            }
        }
    }

    this
        .clipToBounds()
        .nestedScroll(connection)
        .graphicsLayer {
            val yOff = offset.value
            val stretch = 1f + (abs(yOff) / maxOffsetPx) * 0.018f
            transformOrigin = if (yOff >= 0f) TransformOrigin(0.5f, 0f) else TransformOrigin(0.5f, 1f)
            translationY = yOff * 0.35f
            scaleY = stretch
        }
}
