package com.example.worktime.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/* -------- two palettes -------- */

// Dark: near-black neutrals.
private object Dark {
    val Bg = Color(0xFF0D0D0F)
    val Surface1 = Color(0xFF16161A)
    val Surface2 = Color(0xFF26262E)
    val Line = Color(0xFF2A2A31)
    val TextHi = Color(0xFFEDEDF0)
    val TextMid = Color(0xFF9B9BA6)
    val TextLow = Color(0xFF63636E)
}

// Light: warm beige-grey, not white.
private object Light {
    val Bg = Color(0xFFFAFAFA)       // near-white page
    val Surface1 = Color(0xFFFFFFFF) // cards lift with white
    val Surface2 = Color(0xFFF0F0F0) // segmented track, subtle grey
    val Line = Color(0xFFE6E6E6)     // soft hairline
    val TextHi = Color(0xFF1F1F1F)   // near-black
    val TextMid = Color(0xFF6E6E6E)
    val TextLow = Color(0xFFA0A0A0)
}

val Danger = Color(0xFFC0554C)

/* -------- live tokens, swapped by the theme -------- */
// Every screen reads these directly. Making them state lets the whole UI
// recolour the instant the theme toggle flips, no per-screen plumbing.

var Bg by mutableStateOf(Dark.Bg); private set
var Surface1 by mutableStateOf(Dark.Surface1); private set
var Surface2 by mutableStateOf(Dark.Surface2); private set
var Line by mutableStateOf(Dark.Line); private set
var TextHi by mutableStateOf(Dark.TextHi); private set
var TextMid by mutableStateOf(Dark.TextMid); private set
var TextLow by mutableStateOf(Dark.TextLow); private set

private fun applyTokens(dark: Boolean) {
    if (dark) {
        Bg = Dark.Bg; Surface1 = Dark.Surface1; Surface2 = Dark.Surface2
        Line = Dark.Line; TextHi = Dark.TextHi; TextMid = Dark.TextMid; TextLow = Dark.TextLow
    } else {
        Bg = Light.Bg; Surface1 = Light.Surface1; Surface2 = Light.Surface2
        Line = Light.Line; TextHi = Light.TextHi; TextMid = Light.TextMid; TextLow = Light.TextLow
    }
}

private fun sans(size: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = spacing.sp
)

private val typo = Typography(
    displayLarge = sans(64, FontWeight.Light, -1.0),
    headlineMedium = sans(24, FontWeight.Normal),
    titleMedium = sans(16, FontWeight.Medium),
    bodyLarge = sans(15, FontWeight.Normal),
    bodyMedium = sans(14, FontWeight.Normal),
    bodySmall = sans(12, FontWeight.Normal),
    labelSmall = sans(11, FontWeight.Medium, 0.4)
)

@Composable
fun WorktimeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // Only write when the palette actually needs to flip, so composition does not
    // fire a state change on every recomposition.
    if (Bg != (if (darkTheme) Dark.Bg else Light.Bg)) applyTokens(darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Dark theme wants light status-bar icons, light theme wants dark ones.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(Bg.toArgb())
            )
        }
    }

    val scheme = if (darkTheme) darkColorScheme(
        primary = TextHi, onPrimary = Bg, secondary = TextMid,
        background = Bg, onBackground = TextHi,
        surface = Surface1, onSurface = TextHi,
        surfaceVariant = Surface2, onSurfaceVariant = TextMid,
        outline = Line, error = Danger, onError = Bg
    ) else lightColorScheme(
        primary = TextHi, onPrimary = Bg, secondary = TextMid,
        background = Bg, onBackground = TextHi,
        surface = Surface1, onSurface = TextHi,
        surfaceVariant = Surface2, onSurfaceVariant = TextMid,
        outline = Line, error = Danger, onError = Color.White
    )

    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}
