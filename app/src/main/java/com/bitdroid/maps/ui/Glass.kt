package com.bitdroid.maps.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

// Tinted liquid glass, the same as bitDialer: the map blurs behind every bar and button, under a dark
// tint so text stays readable, with a hairline edge that's brightest along the top.

/** Selected-tab color from bitDialer's dock. */
val GlassSelected = Color(0xFF9CB2FF)

/** Real glass: blurs the map behind it. [tint] defaults to bitDialer's dark tint. */
fun Modifier.frosted(state: HazeState, shape: Shape, tint: Color = Color(0xFF101014), alpha: Float = 0.55f): Modifier =
    this.clip(shape)
        .hazeEffect(state) {
            blurRadius = 28.dp
            tints = listOf(HazeTint(tint.copy(alpha = alpha)))
            noiseFactor = 0.03f
        }
        .border(0.75.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.05f))), shape)

/** Clickable with a gentle press-in and settle. */
fun Modifier.squish(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = if (pressed) Spring.DampingRatioNoBouncy else 0.6f, stiffness = 600f),
        label = "squish",
    )
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Round glass button over the map. */
@Composable
fun GlassFab(haze: HazeState, label: String, size: Int = 52, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size.dp).squish(onClick = onClick).frosted(haze, CircleShape)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { content() }
}

class TabItem(val icon: ImageVector, val label: String)

// bitHome tonal colors for the bar (from bitMessages).
private val Lav = Color(0xFFCBC2EB)
private val OnLav = Color(0xFF241C4A)
private val BarTint = Color(0xFF2E2842)
private val Ink = Color(0xFFE8E0FF)

/** A soft rounded pill whose edge ripples like the bitHome cookie shapes when [wave] > 0. */
private class WavyPill(private val wave: Float, private val phase: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = 160
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val r = (abs(c).pow(4.0) + abs(s).pow(4.0)).pow(-0.25)
            val w = 1.0 + wave * cos(9 * t + phase)
            val x = cx + (size.width / 2f) * (c * r * w).toFloat()
            val y = cy + (size.height / 2f) * (s * r * w).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

/** bitHome clover (the search button). */
private object Clover : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val s = min(size.width, size.height) / 2f
        val path = Path()
        for (i in 0..180) {
            val t = 2 * PI * i / 180
            val r = ((0.74 + 0.25 * abs(cos(2 * t)).pow(0.6)) * s).toFloat()
            val x = cx + r * cos(t).toFloat()
            val y = cy + r * sin(t).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * bitMessages' floating bitHome-style tab bar. A lavender pill marks the tab you're on; drag along the bar
 * and it follows your finger, its edge rippling like a bitHome cookie, then settles on the nearest tab.
 * Only the selected tab shows its label. The clover on the right opens search.
 */
@Composable
fun GlassDock(
    haze: HazeState,
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val barShape = RoundedCornerShape(30.dp)
    val tonal = Modifier.clip(barShape).hazeEffect(haze) {
        blurRadius = 24.dp
        tints = listOf(HazeTint(BarTint.copy(alpha = 0.82f)))
        noiseFactor = 0f
    }
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.weight(1f).height(68.dp).then(tonal).padding(7.dp)) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            // Lens position in tab units. The tab under the lens grows so its label always fits.
            val pos = remember { Animatable(selected.toFloat()) }
            var dragging by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(selected) {
                if (!dragging) pos.animateTo(selected.toFloat(), spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow))
            }
            val p = pos.value
            val weights = List(tabs.size) { i -> 1f + 0.9f * (1f - abs(i - p)).coerceIn(0f, 1f) }
            val unit = widthPx / weights.sum()
            val lo = p.toInt().coerceIn(0, tabs.lastIndex)
            val hi = (lo + 1).coerceAtMost(tabs.lastIndex)
            val frac = p - lo
            val lensLeft = unit * (weights.take(lo).sum() + frac * weights[lo])
            val lensW = unit * (weights[lo] + (weights[hi] - weights[lo]) * frac)
            val wave by animateFloatAsState(if (dragging) 0.07f else 0f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "wave")
            val spin = rememberInfiniteTransition(label = "ripple")
            val phase by spin.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
            val hover = p.roundToInt().coerceIn(0, tabs.lastIndex)
            Box(
                Modifier.offset { IntOffset(lensLeft.roundToInt(), 0) }
                    .width(with(density) { lensW.toDp() }).fillMaxHeight()
                    .graphicsLayer { val s2 = 1f + wave * 0.8f; scaleX = s2; scaleY = s2 }
                    .clip(WavyPill(wave, phase))
                    .background(Lav),
            )
            Row(
                Modifier.fillMaxSize().pointerInput(tabs.size, widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            val target = pos.value.roundToInt().coerceIn(0, tabs.lastIndex)
                            dragging = false
                            scope.launch { pos.animateTo(target.toFloat(), spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)) }
                            if (target != currentSelected) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                currentOnSelect(target)
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { pos.animateTo(currentSelected.toFloat()) }
                        },
                    ) { change, dx ->
                        change.consume()
                        val avg = widthPx / (tabs.size + 0.9f)
                        scope.launch { pos.snapTo((pos.value + dx / avg).coerceIn(0f, tabs.lastIndex.toFloat())) }
                    }
                },
            ) {
                tabs.forEachIndexed { i, t ->
                    val on = i == hover
                    Row(
                        Modifier.weight(weights[i]).fillMaxHeight().clip(RoundedCornerShape(24.dp)).squish {
                            if (i != selected) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelect(i)
                        },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(t.icon, t.label, tint = if (on) OnLav else Ink, modifier = Modifier.size(23.dp))
                        AnimatedVisibility(on, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                            Row {
                                Spacer(Modifier.width(6.dp))
                                Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnLav, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(68.dp).squish(onClick = onSearch).clip(Clover).background(Lav),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Search, "Search", tint = OnLav, modifier = Modifier.size(26.dp)) }
    }
}
