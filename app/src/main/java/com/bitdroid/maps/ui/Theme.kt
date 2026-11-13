package com.bitdroid.maps.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bitdroid.maps.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * bitMaps colors, taken from the wallpaper (Material You), so the app matches bitDroid's theme.
 * Names are kept from the first design so every screen picks the new look up automatically.
 */
object C {
    /** Light lilac tiles and buttons (primary). */
    var Accent = Color(0xFFD9CCF6); private set
    /** Text/icons on [Accent]. */
    var OnAccent = Color(0xFF2A2140); private set
    /** Deep violet (cookies, alternate squircles). */
    var AccentDeep = Color(0xFF4B3B78); private set
    var OnAccentDeep = Color(0xFFEDE6F7); private set
    /** Slightly darker lilac, e.g. the "Then" pill. */
    var AccentSoft = Color(0xFFC6B6EE); private set
    /** Soft pink End button. */
    var Danger = Color(0xFFF2B8C6); private set
    var OnDanger = Color(0xFF5C1A2B); private set

    var Bg = Color(0xFF16131C); private set
    var Sheet = Color(0xFF1E1A25); private set
    var Surface = Color(0xFF2A2532); private set
    var SurfaceHigh = Color(0xFF35303E); private set
    /** See-through dark tiles over the map. */
    var Glass = Color(0x9E463E56); private set
    var Hairline = Color(0x1FFFFFFF); private set
    var Outline = Color(0x14FFFFFF); private set
    var Text = Color(0xFFEDE6F7); private set
    var Dim = Color(0xFFB9AECB); private set

    // Older names used around the app.
    val Violet get() = Accent
    val Blue get() = AccentDeep
    val Lilac get() = Accent
    var Cyan = Color(0xFF9FD3E8); private set
    var Pink = Color(0xFFF4B6D2); private set
    var Green = Color(0xFFB5E6C4); private set
    val Red get() = Danger

    val gradient: Brush get() = SolidColor(Accent)
    val gradient3: Brush get() = SolidColor(Accent)

    /** Wallpaper-tinted background glow (top-left accent, bottom-right tertiary), like the home screen. */
    var glowA = Color(0xFF3A2B57); private set
    var glowB = Color(0xFF1D3B3A); private set

    fun apply(s: ColorScheme) {
        Accent = s.primary
        OnAccent = s.onPrimary
        AccentDeep = s.primaryContainer
        OnAccentDeep = s.onPrimaryContainer
        AccentSoft = lerp(s.primary, s.primaryContainer, 0.22f)
        Danger = s.error
        OnDanger = s.onError
        Bg = s.surface
        Sheet = s.surfaceContainer
        Surface = s.surfaceContainerHigh
        SurfaceHigh = s.surfaceContainerHighest
        Glass = s.surfaceContainerHighest.copy(alpha = 0.72f)
        Text = s.onSurface
        Dim = s.onSurfaceVariant
        Hairline = s.outlineVariant.copy(alpha = 0.5f)
        Outline = Color.White.copy(alpha = 0.07f)
        Cyan = s.tertiary
        Pink = lerp(s.error, s.primary, 0.4f)
        Green = lerp(s.tertiary, Color(0xFFB5E6C4), 0.6f)
        glowA = lerp(s.surface, s.primaryContainer, 0.75f)
        glowB = lerp(s.surface, s.tertiaryContainer, 0.6f)
    }
}

/** The wallpaper color scheme (Android 12+), dark. */
fun bitScheme(context: Context): ColorScheme = dynamicDarkColorScheme(context)

/** Map colors that match the theme. */
data class MapPalette(
    val bg: Int, val block: Int, val road: Int, val roadMajor: Int, val water: Int, val park: Int,
    val label: Int, val labelStrong: Int, val routeStart: Int, val routeEnd: Int, val alt: Int, val pin: Int,
)

fun mapPalette(s: ColorScheme) = MapPalette(
    bg = lerp(s.surface, s.surfaceContainerLow, 0.6f).toArgb(),
    block = s.surfaceContainer.toArgb(),
    road = s.surfaceContainerHighest.toArgb(),
    roadMajor = lerp(s.surfaceContainerHighest, s.primaryContainer, 0.6f).toArgb(),
    water = lerp(s.surfaceContainerLow, Color(0xFF2B4A7A), 0.35f).toArgb(),
    park = lerp(s.surfaceContainerLow, Color(0xFF2E5A3C), 0.3f).toArgb(),
    label = s.onSurfaceVariant.copy(alpha = 0.75f).toArgb(),
    labelStrong = s.onSurface.toArgb(),
    routeStart = lerp(s.primary, s.primaryContainer, 0.45f).toArgb(),
    routeEnd = lerp(s.primary, Color.White, 0.35f).toArgb(),
    alt = s.outline.toArgb(),
    pin = s.primary.toArgb(),
)

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int): FontFamily = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

val Body: FontFamily = variable(R.font.inter, 300, 400, 500, 600, 700, 800)
/** Big headings: heavy Inter, like the Bloom widgets. */
val Display: FontFamily = Body

@Composable
fun BitTheme(content: @Composable () -> Unit) {
    val scheme = bitScheme(LocalContext.current)
    C.apply(scheme)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Body, color = C.Text), content = content)
    }
}

// ---------------- Material You shapes ----------------

/** The wavy "cookie" shape from Material 3 Expressive (like the date widget). */
class CookieShape(private val lobes: Int = 8, private val depth: Float = 0.09f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        val steps = 180
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val rr = r * (1 - depth) + r * depth * cos(lobes * t)
            val x = cx + (rr * cos(t)).toFloat()
            val y = cy + (rr * sin(t)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

val Cookie8 = CookieShape(8, 0.09f)
val Cookie12 = CookieShape(12, 0.06f)
val Cookie5 = CookieShape(5, 0.12f)

/** A filled cookie with content centered on it. */
@Composable
fun Cookie(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = Cookie8,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(size).clip(shape).background(color), contentAlignment = Alignment.Center, content = content)
}

val TileRadius = 34.dp
