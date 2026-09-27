package com.example.worktime.data

import android.graphics.Color as AColor

object Palette {

    /** Base hues offered when creating an area. */
    val base: List<Int> = listOf(
        0xFFFF4D8D, // pink
        0xFFF2544B, // red
        0xFFFF8A3D, // orange
        0xFFFFC24B, // amber
        0xFFB8D94A, // lime
        0xFF4ECB71, // green
        0xFF3FC9B5, // teal
        0xFF45BDE8, // cyan
        0xFF5B8DEF, // blue
        0xFF7C6BF0, // indigo
        0xFFA96BF0, // purple
        0xFFE05BD4, // magenta
        0xFFB08968, // brown
        0xFF9AA0A6  // grey
    ).map { it.toInt() }

    /**
     * Muted versions of the same hues, offered for the app accent. The accent fills
     * whole segments and sits behind text, so the saturated project colours are too
     * loud for it.
     */
    val accents: List<Int> = listOf(
        0xFFEDEDF0, // plain white, the default
        0xFFC9718F, // pink
        0xFFC96F63, // brick
        0xFFC98A5A, // clay
        0xFFC4A55C, // sand
        0xFF9DAD63, // olive
        0xFF6FAD7E, // sage
        0xFF5FA89C, // teal
        0xFF6395AD, // steel
        0xFF6C7FC9, // slate blue
        0xFF7D74C4, // indigo
        0xFF9A76BE, // lilac
        0xFFB673B0, // mauve
        0xFFA08A76, // taupe
        0xFF8C8C96  // grey
    ).map { it.toInt() }

    /**
     * Twenty shades of the same hue, light to dark, for the projects inside an area.
     * Saturation drops slightly as the shade lightens so the family stays readable
     * on a dark background but never turns into twenty identical swatches.
     */
    fun shadesArgb(baseArgb: Int, count: Int = 20): List<Int> {
        val hsv = FloatArray(3)
        AColor.colorToHSV(baseArgb, hsv)
        val hue = hsv[0]
        return (0 until count).map { i ->
            val t = i / (count - 1f)                 // 0 light .. 1 dark
            val v = 1.00f - 0.55f * t                // value 1.00 .. 0.45
            val s = 0.30f + 0.62f * t                // saturation 0.30 .. 0.92
            AColor.HSVToColor(floatArrayOf(hue, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        }
    }

    /** Black or white, whichever reads on top of the given colour. */
    fun onColor(argb: Int): Int {
        val r = AColor.red(argb) / 255.0
        val g = AColor.green(argb) / 255.0
        val b = AColor.blue(argb) / 255.0
        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return if (lum > 0.55) 0xFF101010.toInt() else 0xFFFFFFFF.toInt()
    }

    /** First shade in the family not already used by another project in the area. */
    fun nextFreeShade(baseArgb: Int, used: List<Int>): Int {
        val shades = shadesArgb(baseArgb)
        return shades.firstOrNull { it !in used } ?: shades[used.size % shades.size]
    }
}
