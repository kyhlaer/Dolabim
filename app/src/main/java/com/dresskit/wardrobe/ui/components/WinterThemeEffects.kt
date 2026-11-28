package com.dresskit.wardrobe.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalWinterThemeEnabled
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Abstraction over boolean preference storage so [WinterThemePreferences]
 * can be verified cleanly on the JVM without Robolectric.
 */
interface BooleanPrefStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

class SharedPreferencesBooleanStore(
    private val prefs: SharedPreferences
) : BooleanPrefStore {
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}

object WinterThemePreferences {
    const val KEY_WINTER_THEME_ENABLED = "winter_theme_enabled"
    private const val PREFS_NAME = "dolabim_prefs"

    fun isEnabled(store: BooleanPrefStore): Boolean =
        store.getBoolean(KEY_WINTER_THEME_ENABLED, true)

    fun isEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return isEnabled(SharedPreferencesBooleanStore(prefs))
    }

    fun setEnabled(store: BooleanPrefStore, enabled: Boolean) {
        store.putBoolean(KEY_WINTER_THEME_ENABLED, enabled)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        setEnabled(SharedPreferencesBooleanStore(prefs), enabled)
    }
}

/**
 * Pure JVM physics simulation for snow accumulating along the upper surface of the FloatingDock,
 * with avalanche slope relaxation, rounded-corner tapering, and tab-switch snow shedding.
 */
class DockSnowSimulation(
    val bins: Int = 56,
    val maxHeightDp: Float = 12.5f,
    private val slopeThresholdDp: Float = 1.45f
) {
    private val kernel = floatArrayOf(0.14f, 0.38f, 0.72f, 1.0f, 0.72f, 0.38f, 0.14f)
    val heightsDp = FloatArray(bins)
    val displayHeightsDp = FloatArray(bins)

    init {
        seedInitialDusting(0.24f)
    }

    fun maxCapDpAt(u: Float): Float {
        val clamped = u.coerceIn(0f, 1f)
        val env = sin(clamped.toDouble() * PI).coerceAtLeast(0.0).pow(0.42).toFloat()
        return maxHeightDp * env
    }

    fun seedInitialDusting(scale: Float = 0.24f) {
        for (i in 0 until bins) {
            val u = i.toFloat() / (bins - 1)
            val cap = maxCapDpAt(u)
            val wave = 0.72f + 0.28f * sin((u * PI * 5.2 + 0.6)).toFloat()
            val v = (cap * scale * wave).coerceIn(0f, cap)
            heightsDp[i] = v
            displayHeightsDp[i] = v
        }
        heightsDp[0] = 0f
        heightsDp[bins - 1] = 0f
        displayHeightsDp[0] = 0f
        displayHeightsDp[bins - 1] = 0f
    }

    fun sampleHeightDp(u: Float): Float {
        val clamped = u.coerceIn(0f, 1f)
        val f = clamped * (bins - 1)
        val i0 = floor(f).toInt().coerceIn(0, bins - 1)
        val i1 = (i0 + 1).coerceAtMost(bins - 1)
        val frac = f - i0
        return displayHeightsDp[i0] * (1f - frac) + displayHeightsDp[i1] * frac
    }

    fun deposit(u: Float, radiusDp: Float) {
        val center = (u * (bins - 1)).roundToInt().coerceIn(0, bins - 1)
        val amount = 0.52f + radiusDp * 0.24f
        for (k in -3..3) {
            val idx = center + k
            if (idx <= 0 || idx >= bins - 1) continue
            val cap = maxCapDpAt(idx.toFloat() / (bins - 1))
            if (cap <= 0.1f) continue
            val cur = heightsDp[idx]
            val headroom = ((cap - cur) / cap).coerceAtLeast(0.10f)
            heightsDp[idx] = (cur + amount * kernel[k + 3] * headroom).coerceAtMost(cap)
        }
        relaxAvalanche()
    }

    fun relaxAvalanche() {
        for (i in 1 until bins - 1) {
            val h = heightsDp[i]
            val leftDiff = h - heightsDp[i - 1]
            if (leftDiff > slopeThresholdDp && i > 1) {
                val flow = (leftDiff - slopeThresholdDp) * 0.30f
                heightsDp[i] -= flow
                heightsDp[i - 1] += flow
            }
            val rightDiff = h - heightsDp[i + 1]
            if (rightDiff > slopeThresholdDp && i < bins - 2) {
                val flow = (rightDiff - slopeThresholdDp) * 0.30f
                heightsDp[i] -= flow
                heightsDp[i + 1] += flow
            }
        }
        for (i in 2 until bins - 2) {
            val avg = (heightsDp[i - 1] + heightsDp[i + 1]) * 0.5f
            val cap = maxCapDpAt(i.toFloat() / (bins - 1))
            heightsDp[i] = (heightsDp[i] + (avg - heightsDp[i]) * 0.12f).coerceIn(0f, cap)
        }
        heightsDp[0] = 0f
        heightsDp[bins - 1] = 0f
    }

    fun stepSmooth(dtSec: Float) {
        if (dtSec <= 0f) return
        val factor = (1.0 - exp((-dtSec * 2.6f).toDouble())).toFloat()
        for (i in 1 until bins - 1) {
            val diff = heightsDp[i] - displayHeightsDp[i]
            if (abs(diff) < 0.002f) {
                displayHeightsDp[i] = heightsDp[i]
            } else {
                displayHeightsDp[i] += diff * factor
            }
        }
        displayHeightsDp[0] = 0f
        displayHeightsDp[bins - 1] = 0f
    }

    fun peakHeightDp(): Float {
        var m = 0f
        for (i in 0 until bins) {
            if (heightsDp[i] > m) m = heightsDp[i]
        }
        return m
    }

    fun displayPeakHeightDp(): Float {
        var m = 0f
        for (i in 0 until bins) {
            if (displayHeightsDp[i] > m) m = displayHeightsDp[i]
        }
        return m
    }

    /**
     * Captures non-zero snow bins as normalized (u, heightDp) pairs and clears both target and
     * rendered dock snow cleanly to 0 so regrowth starts gently from zero with no sudden jump.
     */
    fun shakeAndClear(): List<Pair<Float, Float>> {
        val clumps = ArrayList<Pair<Float, Float>>(bins)
        for (i in 1 until bins - 1) {
            val h = maxOf(heightsDp[i], displayHeightsDp[i])
            if (h >= 0.45f) {
                clumps.add((i.toFloat() / (bins - 1)) to h)
            }
            heightsDp[i] = 0f
            displayHeightsDp[i] = 0f
        }
        heightsDp[0] = 0f
        heightsDp[bins - 1] = 0f
        displayHeightsDp[0] = 0f
        displayHeightsDp[bins - 1] = 0f
        return clumps
    }
}

/**
 * Shared non-allocating bridge between [FloatingDock] and [WinterSnowfallOverlay] so falling
 * snowflakes collide accurately with the dock's upper rim (adjusted for statusBarsPadding /
 * overlay root origin) and tab switches trigger snow shedding.
 */
class WinterDockSnowBridge {
    val sim = DockSnowSimulation()
    var dockLeftPx: Float = 0f
    var dockRightPx: Float = 0f
    var dockTopPx: Float = 0f
    var overlayOriginXPx: Float = 0f
    var overlayOriginYPx: Float = 0f
    var hasBounds: Boolean = false
    var pendingShakeNonce: Int = 0
        private set

    fun updateDockBounds(leftPx: Float, topPx: Float, rightPx: Float) {
        dockLeftPx = leftPx
        dockTopPx = topPx
        dockRightPx = rightPx
        hasBounds = (rightPx - leftPx) > 40f && topPx > 0f
    }

    fun updateOverlayOrigin(xPx: Float, yPx: Float) {
        overlayOriginXPx = xPx
        overlayOriginYPx = yPx
    }

    fun localDockLeftPx(): Float = dockLeftPx - overlayOriginXPx
    fun localDockRightPx(): Float = dockRightPx - overlayOriginXPx
    fun localDockTopPx(): Float = dockTopPx - overlayOriginYPx

    fun triggerTabShake() {
        pendingShakeNonce += 1
    }
}

private const val SNOWFLAKE_COUNT = 44
private const val MAX_SHED_CLUMPS = 64

private class ShedClump(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var radiusPx: Float = 2f,
    var life: Float = 0f,
    var alpha: Float = 0.9f
)

/**
 * Pre-allocated flat arrays for 3 depth layers of gentle snowfall + shed snow clumps.
 */
private class SnowFieldState {
    val rng = Random(202612L)
    val baseXNorm = FloatArray(SNOWFLAKE_COUNT)
    val yNorm = FloatArray(SNOWFLAKE_COUNT)
    val speedDpPerSec = FloatArray(SNOWFLAKE_COUNT)
    val radiusDp = FloatArray(SNOWFLAKE_COUNT)
    val driftAmpDp = FloatArray(SNOWFLAKE_COUNT)
    val driftFreq = FloatArray(SNOWFLAKE_COUNT)
    val phaseOffset = FloatArray(SNOWFLAKE_COUNT)
    val alpha = FloatArray(SNOWFLAKE_COUNT)
    val layer = IntArray(SNOWFLAKE_COUNT)
    val clumps = Array(MAX_SHED_CLUMPS) { ShedClump() }

    var lastFrameSec: Float = 0f
    var handledShakeNonce: Int = 0

    init {
        for (i in 0 until SNOWFLAKE_COUNT) {
            val l = when {
                i < 16 -> 0
                i < 33 -> 1
                else -> 2
            }
            layer[i] = l
            baseXNorm[i] = rng.nextFloat()
            yNorm[i] = rng.nextFloat()
            speedDpPerSec[i] = when (l) {
                0 -> 24f + rng.nextFloat() * 14f
                1 -> 40f + rng.nextFloat() * 18f
                else -> 58f + rng.nextFloat() * 22f
            }
            radiusDp[i] = when (l) {
                0 -> 1.3f + rng.nextFloat() * 0.8f
                1 -> 2.1f + rng.nextFloat() * 1.0f
                else -> 2.9f + rng.nextFloat() * 1.2f
            }
            driftAmpDp[i] = when (l) {
                0 -> 6f + rng.nextFloat() * 6f
                1 -> 10f + rng.nextFloat() * 8f
                else -> 14f + rng.nextFloat() * 9f
            }
            driftFreq[i] = 0.55f + rng.nextFloat() * 0.75f
            phaseOffset[i] = rng.nextFloat() * (2f * PI.toFloat())
            alpha[i] = when (l) {
                0 -> 0.28f + rng.nextFloat() * 0.16f
                1 -> 0.44f + rng.nextFloat() * 0.18f
                else -> 0.58f + rng.nextFloat() * 0.18f
            }
        }
    }

    fun respawnFlake(index: Int, heightDp: Float) {
        val safeH = heightDp.coerceAtLeast(320f)
        yNorm[index] = -(radiusDp[index] + 4f + rng.nextFloat() * 24f) / safeH
        baseXNorm[index] = rng.nextFloat()
    }

    fun spawnShedClumps(
        rawClumps: List<Pair<Float, Float>>,
        spanLeftPx: Float,
        spanWidthPx: Float,
        dockTopPx: Float,
        densityPx: Float
    ) {
        var slot = 0
        for ((u, hDp) in rawClumps) {
            val pieces = if (hDp > 5f) 2 else 1
            repeat(pieces) {
                while (slot < MAX_SHED_CLUMPS && clumps[slot].life > 0f) slot++
                if (slot >= MAX_SHED_CLUMPS) return
                val c = clumps[slot]
                c.x = spanLeftPx + u * spanWidthPx + (rng.nextFloat() - 0.5f) * 6f * densityPx
                c.y = dockTopPx - hDp * (0.35f + rng.nextFloat() * 0.55f) * densityPx
                c.vx = (rng.nextFloat() - 0.5f) * 105f * densityPx
                c.vy = -(16f + rng.nextFloat() * 34f) * densityPx
                c.radiusPx = (1.3f + hDp * 0.25f + rng.nextFloat() * 0.8f).coerceIn(1.5f, 4.2f) * densityPx
                c.life = 0.95f + rng.nextFloat() * 0.45f
                c.alpha = 0.85f + rng.nextFloat() * 0.15f
                slot++
            }
        }
    }
}

/**
 * Full-screen non-interactive layered snowfall + physics-based snow accumulation on the top edge
 * of the FloatingDock (plus shedding snow clumps when tabs switch).
 * Lifecycle-aware: advances only while Lifecycle.State.RESUMED and LocalWinterThemeEnabled is true.
 */
@Composable
fun WinterSnowfallOverlay(
    dockBridge: WinterDockSnowBridge,
    modifier: Modifier = Modifier
) {
    if (!LocalWinterThemeEnabled.current) return

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val isDark = DressKitStyle.isDark()
    val field = remember { SnowFieldState() }
    val shadowDriftPath = remember { Path() }
    val frontDriftPath = remember { Path() }
    val crestStrokePath = remember { Path() }
    var elapsedSecNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        var prevFrame = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - prevFrame).coerceIn(0L, 50_000_000L)
            prevFrame = now
            elapsedSecNanos += dt
        }
    }

    val farColor = if (isDark) Color(0xFFD6E6FA) else Color(0xFF93B3D6)
    val midColor = if (isDark) Color(0xFFEDF5FF) else Color(0xFF7B9FC9)
    val nearColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF6991BF)
    val nearCoreColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFFF3F8FF)

    val shadowRimColor = if (isDark) Color(0xFFAAC8EA).copy(alpha = 0.30f) else Color(0xFF88ACD2).copy(alpha = 0.36f)
    val snowTopColor = if (isDark) Color(0xFFFCFEFF).copy(alpha = 0.96f) else Color(0xFFFFFFFF).copy(alpha = 0.99f)
    val snowMidColor = if (isDark) Color(0xFFE8F2FE).copy(alpha = 0.92f) else Color(0xFFEEF6FF).copy(alpha = 0.96f)
    val snowBotColor = if (isDark) Color(0xFFC4D8F0).copy(alpha = 0.84f) else Color(0xFFCADFF4).copy(alpha = 0.92f)
    val crestColor = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF9ABCE0).copy(alpha = 0.65f)
    val clumpBaseColor = if (isDark) Color(0xFFF2F8FF) else Color(0xFFC3DAF2)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val p = coords.positionInRoot()
                dockBridge.updateOverlayOrigin(p.x, p.y)
            }
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val densityPx = density.coerceAtLeast(1f)
        val hDp = h / densityPx
        val nowSec = elapsedSecNanos * 1e-9f
        val dt = (nowSec - field.lastFrameSec).coerceIn(0f, 0.05f)
        field.lastFrameSec = nowSec

        val insetPx = 18.dp.toPx()
        val localLeft = dockBridge.localDockLeftPx()
        val localRight = dockBridge.localDockRightPx()
        val localTop = dockBridge.localDockTopPx()
        val hasSpan = dockBridge.hasBounds && (localRight - localLeft) > insetPx * 2f + 20f && localTop > 0f
        val spanLeft = if (hasSpan) localLeft + insetPx else 0f
        val spanRight = if (hasSpan) localRight - insetPx else 0f
        val spanWidth = (spanRight - spanLeft).coerceAtLeast(1f)
        val dockTop = localTop + 1.0.dp.toPx()

        // Handle pending tab-switch snow shake & shedding
        if (field.handledShakeNonce != dockBridge.pendingShakeNonce) {
            field.handledShakeNonce = dockBridge.pendingShakeNonce
            val raw = dockBridge.sim.shakeAndClear()
            if (hasSpan && raw.isNotEmpty()) {
                field.spawnShedClumps(raw, spanLeft, spanWidth, dockTop, densityPx)
            }
        }

        // Smoothly ease displayed snow heights toward target heights (prevents any sudden mass spawn)
        if (dt > 0f) {
            dockBridge.sim.stepSmooth(dt)
        }

        // Update & draw falling snowflakes (unchanged gentle 3-layer aesthetic)
        for (i in 0 until SNOWFLAKE_COUNT) {
            val prevY = field.yNorm[i] * h
            if (dt > 0f) {
                field.yNorm[i] += (field.speedDpPerSec[i] * dt) / hDp
            }
            val swayPx = sin((nowSec * field.driftFreq[i] + field.phaseOffset[i]).toDouble()).toFloat() *
                field.driftAmpDp[i].dp.toPx()
            val px = ((field.baseXNorm[i] * w + swayPx) % w + w) % w
            val radPx = field.radiusDp[i].dp.toPx()
            val py = field.yNorm[i] * h

            // Collide with FloatingDock top rim
            if (dt > 0f && hasSpan && px in spanLeft..spanRight) {
                val u = ((px - spanLeft) / spanWidth).coerceIn(0f, 1f)
                val surfaceY = dockTop - dockBridge.sim.sampleHeightDp(u).dp.toPx()
                if (prevY <= dockTop + 8.dp.toPx() && py + radPx >= surfaceY) {
                    dockBridge.sim.deposit(u, field.radiusDp[i])
                    field.respawnFlake(i, hDp)
                    continue
                }
            }

            if (dt > 0f && py - radPx > h + 10.dp.toPx()) {
                field.respawnFlake(i, hDp)
                continue
            }

            if (py < -8.dp.toPx() || py > h) continue
            val l = field.layer[i]
            val baseColor = when (l) {
                0 -> farColor
                1 -> midColor
                else -> nearColor
            }
            val a = field.alpha[i]
            drawCircle(
                color = baseColor.copy(alpha = a),
                radius = radPx,
                center = Offset(px, py)
            )
            if (l == 2) {
                drawCircle(
                    color = nearCoreColor.copy(alpha = (a * 0.72f).coerceAtMost(0.75f)),
                    radius = radPx * 0.45f,
                    center = Offset(px, py)
                )
            }
        }

        // Draw accumulated organic snow cushion along the exact top outer edge of FloatingDock
        val peakDp = dockBridge.sim.displayPeakHeightDp()
        if (hasSpan && peakDp >= 0.04f) {
            val rawT = ((peakDp - 0.04f) / 1.45f).coerceIn(0f, 1f)
            val fadeAlpha = rawT * rawT * (3f - 2f * rawT)
            val bins = dockBridge.sim.bins
            val stepX = spanWidth / (bins - 1)
            val lipY = dockTop + (1.2f * fadeAlpha).dp.toPx()

            fun traceDockCurve(target: Path, offsetDp: Float, closeBottom: Boolean) {
                target.reset()
                fun yAt(idx: Int): Float = dockTop - (dockBridge.sim.displayHeightsDp[idx] + offsetDp).coerceAtLeast(0f).dp.toPx()
                target.moveTo(spanLeft, lipY)
                for (i in 0 until bins - 1) {
                    val x0 = spanLeft + i * stepX
                    val y0 = yAt(i)
                    val x1 = spanLeft + (i + 1) * stepX
                    val y1 = yAt(i + 1)
                    target.quadraticTo(x0, y0, (x0 + x1) * 0.5f, (y0 + y1) * 0.5f)
                }
                target.lineTo(spanRight, lipY)
                if (closeBottom) {
                    target.quadraticTo(
                        spanLeft + spanWidth * 0.5f,
                        dockTop + (2.3f * fadeAlpha).dp.toPx(),
                        spanLeft,
                        lipY
                    )
                    target.close()
                }
            }

            traceDockCurve(shadowDriftPath, offsetDp = 0.75f * fadeAlpha, closeBottom = true)
            drawPath(path = shadowDriftPath, color = shadowRimColor.copy(alpha = shadowRimColor.alpha * fadeAlpha))

            traceDockCurve(frontDriftPath, offsetDp = 0f, closeBottom = true)
            drawPath(
                path = frontDriftPath,
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to snowTopColor.copy(alpha = snowTopColor.alpha * fadeAlpha),
                        0.60f to snowMidColor.copy(alpha = snowMidColor.alpha * fadeAlpha),
                        1.0f to snowBotColor.copy(alpha = snowBotColor.alpha * fadeAlpha)
                    ),
                    startY = dockTop - (peakDp + 1f).dp.toPx(),
                    endY = dockTop + 2.5.dp.toPx()
                )
            )

            traceDockCurve(crestStrokePath, offsetDp = 0f, closeBottom = false)
            drawPath(
                path = crestStrokePath,
                color = crestColor.copy(alpha = crestColor.alpha * fadeAlpha),
                style = Stroke(width = 0.95.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Update & draw falling shed snow clumps from tab-switch shake
        val gravityPx = 360f * densityPx
        for (i in 0 until MAX_SHED_CLUMPS) {
            val c = field.clumps[i]
            if (c.life <= 0f) continue
            if (dt > 0f) {
                c.vy += gravityPx * dt
                c.x += c.vx * dt
                c.y += c.vy * dt
                c.vx *= 0.985f
                c.life -= dt * 1.05f
            }
            if (c.life <= 0f || c.y > h + 16.dp.toPx()) {
                c.life = 0f
                continue
            }
            val a = (c.life * c.alpha).coerceIn(0f, 0.95f)
            drawCircle(
                color = clumpBaseColor.copy(alpha = a),
                radius = c.radiusPx,
                center = Offset(c.x, c.y)
            )
            drawCircle(
                color = Color.White.copy(alpha = (a * 0.7f).coerceAtMost(0.9f)),
                radius = c.radiusPx * 0.48f,
                center = Offset(c.x - c.radiusPx * 0.22f, c.y - c.radiusPx * 0.22f)
            )
        }
    }
}

/**
 * 4 sweet, softly rounded miniature vector holiday ornaments seated gently along the top edge
 * of the FloatingDock (shakes together with the dock on tab change, never intercepts touch input).
 */
@Composable
fun WinterDockMiniOrnamentsOverlay(modifier: Modifier = Modifier) {
    if (!LocalWinterThemeEnabled.current) return
    val isDark = DressKitStyle.isDark()
    val pineColor = if (isDark) Color(0xFF4CA86B) else Color(0xFF2B6F4B)
    val trunkColor = if (isDark) Color(0xFF7B5335) else Color(0xFF5D3A22)
    val deerColor = if (isDark) Color(0xFFD2A97E) else Color(0xFF835834)
    val giftRed = Color(0xFFD64545)
    val ribbonGold = Color(0xFFF3C55E)
    val snowWhite = Color(0xFFF6FAFF)

    val p = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val sy = h / 22f
        val base = h - 1.5f * sy

        // 1) Left (~11%): Snow-dusted mini pine tree with star & micro lights
        val tx = w * 0.11f
        drawRoundRect(
            color = trunkColor,
            topLeft = Offset(tx - 1.4f * sy, base - 3f * sy),
            size = Size(2.8f * sy, 3.2f * sy),
            cornerRadius = CornerRadius(1f * sy, 1f * sy)
        )
        p.reset()
        p.moveTo(tx, base - 16.5f * sy)
        p.lineTo(tx - 4.8f * sy, base - 9.2f * sy)
        p.lineTo(tx - 2.6f * sy, base - 9.2f * sy)
        p.lineTo(tx - 7.0f * sy, base - 2.6f * sy)
        p.lineTo(tx + 7.0f * sy, base - 2.6f * sy)
        p.lineTo(tx + 2.6f * sy, base - 9.2f * sy)
        p.lineTo(tx + 4.8f * sy, base - 9.2f * sy)
        p.close()
        drawPath(p, color = pineColor)
        // Snow dusting on crown
        p.reset()
        p.moveTo(tx, base - 16.2f * sy)
        p.lineTo(tx - 2.9f * sy, base - 12.1f * sy)
        p.quadraticTo(tx, base - 13.0f * sy, tx + 2.9f * sy, base - 12.1f * sy)
        p.close()
        drawPath(p, color = snowWhite.copy(alpha = 0.92f))
        drawCircle(color = ribbonGold, radius = 1.7f * sy, center = Offset(tx, base - 17.5f * sy))
        drawCircle(color = Color(0xFFE5484D), radius = 1.0f * sy, center = Offset(tx - 1.8f * sy, base - 7.2f * sy))
        drawCircle(color = ribbonGold, radius = 1.0f * sy, center = Offset(tx + 2.1f * sy, base - 5.4f * sy))

        // 2) Mid-left (~34%): Playful miniature Santa cap + tiny snowball
        val hx = w * 0.34f
        rotate(degrees = -12f, pivot = Offset(hx, base - 3f * sy)) {
            p.reset()
            p.moveTo(hx - 5.2f * sy, base - 2.8f * sy)
            p.cubicTo(hx - 3.6f * sy, base - 8.8f * sy, hx + 1.0f * sy, base - 10.8f * sy, hx + 6.0f * sy, base - 8.4f * sy)
            p.cubicTo(hx + 4.2f * sy, base - 6.6f * sy, hx + 3.4f * sy, base - 4.6f * sy, hx + 4.2f * sy, base - 2.8f * sy)
            p.close()
            drawPath(p, color = Color(0xFFD9383A))
            drawRoundRect(
                color = snowWhite,
                topLeft = Offset(hx - 6.0f * sy, base - 3.6f * sy),
                size = Size(11.2f * sy, 3.0f * sy),
                cornerRadius = CornerRadius(1.5f * sy, 1.5f * sy)
            )
            drawCircle(color = Color.White, radius = 1.65f * sy, center = Offset(hx + 6.5f * sy, base - 8.0f * sy))
        }
        drawCircle(color = snowWhite, radius = 2.0f * sy, center = Offset(hx + 9.2f * sy, base - 2.0f * sy))

        // 3) Mid-right (~74%): Cute mini reindeer with delicate antlers & tiny red Rudolph nose
        val dx = w * 0.74f
        p.reset()
        p.moveTo(dx - 5.2f * sy, base)
        p.lineTo(dx - 4.6f * sy, base - 5.4f * sy)
        p.lineTo(dx + 1.8f * sy, base - 5.8f * sy)
        p.lineTo(dx + 3.4f * sy, base - 11.4f * sy)
        p.lineTo(dx + 6.8f * sy, base - 11.8f * sy)
        p.lineTo(dx + 5.8f * sy, base - 9.6f * sy)
        p.lineTo(dx + 3.8f * sy, base - 9.0f * sy)
        p.lineTo(dx + 3.6f * sy, base - 4.5f * sy)
        p.lineTo(dx + 4.0f * sy, base)
        p.lineTo(dx + 2.4f * sy, base)
        p.lineTo(dx + 1.8f * sy, base - 3.4f * sy)
        p.lineTo(dx - 2.6f * sy, base - 3.4f * sy)
        p.lineTo(dx - 3.4f * sy, base)
        p.close()
        drawPath(p, color = deerColor)
        drawLine(deerColor, Offset(dx + 3.6f * sy, base - 11.4f * sy), Offset(dx + 1.4f * sy, base - 15.6f * sy), strokeWidth = 1.1f * sy, cap = StrokeCap.Round)
        drawLine(deerColor, Offset(dx + 2.5f * sy, base - 13.5f * sy), Offset(dx + 4.2f * sy, base - 15.0f * sy), strokeWidth = 1.0f * sy, cap = StrokeCap.Round)
        drawCircle(color = Color(0xFFE53935), radius = 1.15f * sy, center = Offset(dx + 7.1f * sy, base - 11.1f * sy))

        // 4) Right (~88%): Wrapped mini gift box with ribbon & double-loop bow
        val gx = w * 0.88f
        val gw = 11.2f * sy
        val gh = 9.6f * sy
        drawRoundRect(
            color = giftRed,
            topLeft = Offset(gx - gw * 0.5f, base - gh),
            size = Size(gw, gh),
            cornerRadius = CornerRadius(2.0f * sy, 2.0f * sy)
        )
        drawRect(color = ribbonGold, topLeft = Offset(gx - 0.9f * sy, base - gh), size = Size(1.8f * sy, gh))
        drawRect(color = ribbonGold, topLeft = Offset(gx - gw * 0.5f, base - gh * 0.56f), size = Size(gw, 1.7f * sy))
        drawCircle(color = ribbonGold, radius = 1.6f * sy, center = Offset(gx - 1.8f * sy, base - gh - 1.2f * sy), style = Stroke(width = 1.1f * sy))
        drawCircle(color = ribbonGold, radius = 1.6f * sy, center = Offset(gx + 1.8f * sy, base - gh - 1.2f * sy), style = Stroke(width = 1.1f * sy))
    }
}

/**
 * Subtle twinkling string-light garland anchored along the top lip of the viewport.
 */
@Composable
fun WinterTopGarlandOverlay(modifier: Modifier = Modifier) {
    if (!LocalWinterThemeEnabled.current) return

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val isDark = DressKitStyle.isDark()
    var twinkleNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        var prev = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - prev).coerceIn(0L, 50_000_000L)
            prev = now
            twinkleNanos += dt
        }
    }

    val wireColor = if (isDark) Color(0xFF9FB5CF).copy(alpha = 0.28f) else Color(0xFF566B82).copy(alpha = 0.22f)
    val goldBulb = Color(0xFFF3C55E)
    val redBulb = Color(0xFFE05252)
    val iceBulb = if (isDark) Color(0xFFDDF0FF) else Color(0xFF83B8E8)
    val wirePath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
    ) {
        val w = size.width
        if (w <= 0f) return@Canvas
        val t = twinkleNanos * 1e-9f
        val bulbCount = 11
        val sag = 7.dp.toPx()
        val topY = 3.dp.toPx()

        wirePath.reset()
        wirePath.moveTo(0f, topY)
        val segs = 5
        val segW = w / segs
        for (s in 0 until segs) {
            val x0 = s * segW
            val x1 = (s + 1) * segW
            wirePath.quadraticTo((x0 + x1) * 0.5f, topY + sag, x1, topY)
        }
        drawPath(
            path = wirePath,
            color = wireColor,
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
        )

        for (i in 0 until bulbCount) {
            val u = (i + 0.5f) / bulbCount
            val bx = u * w
            val segPos = (u * segs) % 1.0f
            val curveDrop = 4f * segPos * (1f - segPos) * sag * 0.52f
            val by = topY + curveDrop + 3.dp.toPx()
            val pulse = 0.55f + 0.45f * sin((t * 2.1f + i * 0.95f).toDouble()).toFloat()
            val c = when (i % 3) {
                0 -> goldBulb
                1 -> iceBulb
                else -> redBulb
            }
            drawCircle(
                color = c.copy(alpha = (0.14f + 0.14f * pulse)),
                radius = 5.4.dp.toPx(),
                center = Offset(bx, by)
            )
            drawCircle(
                color = c.copy(alpha = 0.58f + 0.36f * pulse),
                radius = 2.1.dp.toPx(),
                center = Offset(bx, by)
            )
        }
    }
}

/**
 * Wraps main screen titles with a snugly fitted tilted vector Santa hat hugging the
 * top-left corner of the first capital letter with zero gap.
 */
@Composable
fun WinterTitleWithSantaHat(
    title: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    val winterEnabled = LocalWinterThemeEnabled.current
    Box(modifier = modifier) {
        Text(
            text = title,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        if (winterEnabled) {
            val crownPath = remember { Path() }
            val shadePath = remember { Path() }
            val brimPath = remember { Path() }
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-6).dp, y = (-5).dp)
                    .size(width = 26.dp, height = 22.dp)
            ) {
                val sx = size.width / 28f
                val sy = size.height / 24f
                rotate(degrees = -14f, pivot = Offset(13f * sx, 17f * sy)) {
                    crownPath.reset()
                    crownPath.moveTo(5f * sx, 17f * sy)
                    crownPath.cubicTo(7.5f * sx, 7f * sy, 14.5f * sx, 3.5f * sy, 22.5f * sx, 6.8f * sy)
                    crownPath.cubicTo(19.5f * sx, 10f * sy, 18.2f * sx, 13.5f * sy, 19.6f * sx, 17f * sy)
                    crownPath.close()
                    drawPath(crownPath, color = Color(0xFFD9383A))

                    shadePath.reset()
                    shadePath.moveTo(13.5f * sx, 6.2f * sy)
                    shadePath.cubicTo(16.5f * sx, 5.3f * sy, 19.5f * sx, 5.9f * sy, 22.2f * sx, 7.1f * sy)
                    shadePath.cubicTo(19.8f * sx, 8.8f * sy, 18.4f * sx, 10.8f * sy, 17.6f * sx, 13.2f * sy)
                    shadePath.cubicTo(16.8f * sx, 9.8f * sy, 15.6f * sx, 7.5f * sy, 13.5f * sx, 6.2f * sy)
                    shadePath.close()
                    drawPath(shadePath, color = Color(0xFFB8282B).copy(alpha = 0.55f))

                    brimPath.reset()
                    brimPath.moveTo(3.2f * sx, 17.4f * sy)
                    brimPath.cubicTo(3.2f * sx, 15.3f * sy, 5.2f * sx, 14.4f * sy, 11.8f * sx, 14.2f * sy)
                    brimPath.cubicTo(18.2f * sx, 14.0f * sy, 21.2f * sx, 15.2f * sy, 21.2f * sx, 17.4f * sy)
                    brimPath.cubicTo(21.2f * sx, 19.4f * sy, 18.4f * sx, 20.1f * sy, 12.2f * sx, 20.0f * sy)
                    brimPath.cubicTo(6.2f * sx, 19.9f * sy, 3.2f * sx, 19.4f * sy, 3.2f * sx, 17.4f * sy)
                    brimPath.close()
                    drawPath(brimPath, color = Color(0xFFF8FAFD))
                    drawPath(brimPath, color = Color(0xFFD5DFEC), style = Stroke(width = 0.65f * sx))

                    drawCircle(
                        color = Color(0xFFFFFFFF),
                        radius = 2.9f * sx,
                        center = Offset(23.2f * sx, 7.4f * sy)
                    )
                    drawCircle(
                        color = Color(0xFFDCE5F0),
                        radius = 2.9f * sx,
                        center = Offset(23.2f * sx, 7.4f * sy),
                        style = Stroke(width = 0.55f * sx)
                    )
                }
            }
        }
    }
}
