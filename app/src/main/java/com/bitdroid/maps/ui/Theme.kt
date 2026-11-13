package com.bitdroid.maps.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.bitdroid.maps.R

/** bitMaps palette: near-black, glassy panels, violet → blue → cyan gradient. */
object C {
    val Bg = Color(0xFF090B10)
    val Sheet = Color(0xFF12151C)
    val Surface = Color(0xFF1A1E27)
    val SurfaceHigh = Color(0xFF1F2430)
    val Glass = Color(0xEB161A23)
    val Hairline = Color(0xFF1E232D)
    val Outline = Color(0x14FFFFFF)
    val Text = Color(0xFFF2F4F8)
    val Dim = Color(0xFF9AA3B2)
    val Violet = Color(0xFF7C4DFF)
    val Blue = Color(0xFF2F6BFF)
    val Cyan = Color(0xFF22D3EE)
    val Pink = Color(0xFFF472B6)
    val Green = Color(0xFF6EE7B7)
    val Red = Color(0xFFE11D48)
    val Lilac = Color(0xFFC4B5FD)

    val gradient = Brush.linearGradient(listOf(Violet, Blue))
    val gradient3 = Brush.linearGradient(listOf(Violet, Blue, Cyan))
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int): FontFamily = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

/** Wide display type, same as the other bitDroid apps. */
val Display: FontFamily = variable(R.font.unbounded, 600, 700, 800)
val Body: FontFamily = variable(R.font.inter, 400, 500, 600, 700)

@Composable
fun BitTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = C.Violet, onPrimary = Color.White,
        background = C.Bg, onBackground = C.Text,
        surface = C.Sheet, onSurface = C.Text,
        surfaceVariant = C.Surface, onSurfaceVariant = C.Dim,
        surfaceContainer = C.Sheet, surfaceContainerHigh = C.Surface, surfaceContainerHighest = C.SurfaceHigh,
        outline = C.Dim, outlineVariant = C.Hairline,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Body, color = C.Text), content = content)
    }
}
