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

    /** Which look is active. */
    var style = com.bitdroid.maps.data.AppStyle.MAPS; private set
    /** True for Material You (wavy cookie shapes); otherwise plain circles. */
    /** The old bitHome cookie shapes are retired: plain Material 3 shapes everywhere. */
    val expressive get() = false
    private val classic get() = style == com.bitdroid.maps.data.AppStyle.CLASSIC

    val gradient: Brush get() = if (!classic) SolidColor(Accent) else Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF2F6BFF)))
    val gradient3: Brush get() = if (!classic) SolidColor(Accent) else Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF2F6BFF), Color(0xFF22D3EE)))

    /** "bitMaps" look: Google Maps-style dark, clean cards, violet accent. */
    fun applyMaps() {
        style = com.bitdroid.maps.data.AppStyle.MAPS
        Accent = Color(0xFFB9A4F0)
        OnAccent = Color(0xFF221A3A)
        AccentDeep = Color(0xFF4B3B78)
        OnAccentDeep = Color(0xFFEDE6F7)
        AccentSoft = Color(0xFF33276A)
        Danger = Color(0xFFF2B8B5)
        OnDanger = Color(0xFF601410)
        Bg = Color(0xFF1B1A22)
        Sheet = Color(0xFF2A2833)
        Surface = Color(0xFF34313E)
        SurfaceHigh = Color(0xFF3F3B4B)
        Glass = Color(0xFF2A2833)
        Text = Color(0xFFE8E6EF)
        Dim = Color(0xFFA8A2B8)
        Hairline = Color(0xFF3A3745)
        Outline = Color(0x14FFFFFF)
        Cyan = Color(0xFF8AB4F8)
        Pink = Color(0xFFF4B6D2)
        Green = Color(0xFF81C995)
        glowA = Color.Transparent
        glowB = Color.Transparent
    }

    /** Wallpaper-tinted background glow (top-left accent, bottom-right tertiary), like the home screen. */
    var glowA = Color(0xFF3A2B57); private set
    var glowB = Color(0xFF1D3B3A); private set

    /** The original bitMaps look: near-black with a violet → blue glow. */
    fun applyClassic() {
        style = com.bitdroid.maps.data.AppStyle.CLASSIC
        Accent = Color(0xFF7C4DFF)
        OnAccent = Color.White
        AccentDeep = Color(0xFF2F6BFF)
        OnAccentDeep = Color.White
        AccentSoft = Color(0x47070A0D)
        Danger = Color(0xFFE11D48)
        OnDanger = Color.White
        Bg = Color(0xFF090B10)
        Sheet = Color(0xFF12151C)
        Surface = Color(0xFF1A1E27)
        SurfaceHigh = Color(0xFF1F2430)
        Glass = Color(0xEB161A23)
        Text = Color(0xFFF2F4F8)
        Dim = Color(0xFF9AA3B2)
        Hairline = Color(0xFF1E232D)
        Outline = Color(0x14FFFFFF)
        Cyan = Color(0xFF22D3EE)
        Pink = Color(0xFFF472B6)
        Green = Color(0xFF6EE7B7)
        glowA = Color(0x337C4DFF)
        glowB = Color(0x222F6BFF)
    }

    fun apply(s: ColorScheme) {
        style = com.bitdroid.maps.data.AppStyle.MATERIAL_YOU
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
fun bitScheme(context: Context, style: com.bitdroid.maps.data.AppStyle = com.bitdroid.maps.data.AppStyle.MATERIAL_YOU): ColorScheme = when (style) {
    com.bitdroid.maps.data.AppStyle.MATERIAL_YOU -> dynamicDarkColorScheme(context)
    com.bitdroid.maps.data.AppStyle.MAPS -> VioletScheme
    com.bitdroid.maps.data.AppStyle.CLASSIC -> BlueScheme
}

/** Material 3 dark tones from a lilac seed (bitDroid). */
private val VioletScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218), onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218), onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFF0F0D13), surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26), surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
)

/** Material 3 dark tones from a blue seed. */
private val BlueScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBFC6DC), onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759), onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFF9DD4B0), onTertiary = Color(0xFF003920),
    tertiaryContainer = Color(0xFF125234), onTertiaryContainer = Color(0xFFB9F0CB),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F), onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
)

/** Map colors that match the theme. */
data class MapPalette(
    val bg: Int, val block: Int, val road: Int, val roadMajor: Int, val water: Int, val park: Int,
    val label: Int, val labelStrong: Int, val routeStart: Int, val routeEnd: Int, val alt: Int, val pin: Int,
    /** Route time bubbles: chosen route pill + its text, other routes' pill. */
    val pill: Int = routeEnd, val onPill: Int = bg, val pillAlt: Int = road,
)

/** The original dark map colors (Classic style). */
val classicMapPalette = MapPalette(
    bg = 0xFF0C0F15.toInt(), block = 0xFF161B24.toInt(), road = 0xFF222936.toInt(), roadMajor = 0xFF3B3470.toInt(),
    water = 0xFF0D1828.toInt(), park = 0xFF0F1D19.toInt(), label = 0xFF8B93A3.toInt(), labelStrong = 0xFFC5CBD6.toInt(),
    routeStart = 0xFF7C4DFF.toInt(), routeEnd = 0xFF22D3EE.toInt(), alt = 0xFF4A5263.toInt(), pin = 0xFFF472B6.toInt(),
)

/** Google Maps-style dark map with a violet tint (bitMaps style). */
val mapsMapPalette = MapPalette(
    bg = 0xFF211F29.toInt(), block = 0xFF2A2733.toInt(), road = 0xFF3A3646.toInt(), roadMajor = 0xFF6B5C8F.toInt(),
    water = 0xFF1A2236.toInt(), park = 0xFF22302A.toInt(), label = 0xFFA79FBF.toInt(), labelStrong = 0xFFE8E6EF.toInt(),
    routeStart = 0xFF8F74F0.toInt(), routeEnd = 0xFFB9A4F0.toInt(), alt = 0xFF5A5568.toInt(), pin = 0xFFB9A4F0.toInt(),
)

fun paletteFor(style: com.bitdroid.maps.data.AppStyle, context: Context): MapPalette = mapPalette(bitScheme(context, style))

fun mapPalette(s: ColorScheme) = MapPalette(
    // Neutral, Google-style dark map (just a hint of your color), so the route is the only bright thing on it.
    bg = lerp(Color(0xFF1B1C20), s.surface, 0.12f).toArgb(),
    block = lerp(Color(0xFF24252B), s.surfaceContainer, 0.12f).toArgb(),
    road = Color(0xFF383A41).toArgb(),
    roadMajor = Color(0xFF4B4E57).toArgb(),
    water = Color(0xFF15243A).toArgb(),
    park = Color(0xFF1A2A21).toArgb(),
    label = Color(0xFF9EA2AA).toArgb(),
    labelStrong = Color(0xFFE3E3E8).toArgb(),
    // Route: bright and solid in your accent color.
    routeStart = lerp(s.primary, Color.White, 0.10f).toArgb(),
    routeEnd = lerp(s.primary, Color.White, 0.10f).toArgb(),
    alt = Color(0xFF6E7380).toArgb(),
    pin = s.tertiary.toArgb(),
    pill = s.primary.toArgb(),
    onPill = s.onPrimary.toArgb(),
    pillAlt = s.surfaceContainerHighest.toArgb(),
)

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int): FontFamily = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

val Body: FontFamily = variable(R.font.inter, 300, 400, 500, 600, 700, 800)
/** Big headings: heavy Inter, like the Bloom widgets. */
val Display: FontFamily = Body

@Composable
fun BitTheme(style: com.bitdroid.maps.data.AppStyle = com.bitdroid.maps.data.AppStyle.MAPS, content: @Composable () -> Unit) {
    val scheme = bitScheme(LocalContext.current, style)
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

private val cookie8 = CookieShape(8, 0.09f)
private val cookie12 = CookieShape(12, 0.06f)
private val cookie5 = CookieShape(5, 0.12f)

/** Wavy cookies in Material You; plain circles in Classic. */
val Cookie8: Shape get() = if (C.expressive) cookie8 else androidx.compose.foundation.shape.CircleShape
val Cookie12: Shape get() = if (C.expressive) cookie12 else androidx.compose.foundation.shape.CircleShape
val Cookie5: Shape get() = if (C.expressive) cookie5 else androidx.compose.foundation.shape.CircleShape

/** A filled cookie with content centered on it. */
@Composable
fun Cookie(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(size).clip(shape ?: Cookie8).background(color), contentAlignment = Alignment.Center, content = content)
}

val TileRadius = 34.dp
