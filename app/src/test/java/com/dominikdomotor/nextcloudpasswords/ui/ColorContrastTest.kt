package com.dominikdomotor.nextcloudpasswords.ui

import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the app's text colours against their backgrounds, in both themes.
 *
 * Android Studio flags contrast as an IDE-only inspection, so nothing caught it in a build or in CI - the shaded text
 * sat at 2.6:1 for a long time before anyone noticed by eye. This reads the real colour resources so a future edit that
 * dims a colour fails here instead of shipping.
 */
class ColorContrastTest {
    private val light = colorsFrom("src/main/res/values/colors.xml")
    private val dark = colorsFrom("src/main/res/values-night/colors.xml")

    @Test
    fun `body text is readable in both themes`() {
        assertReadable("normal_text_color", "background_color", NORMAL_TEXT_MINIMUM)
    }

    @Test
    fun `secondary text is readable in both themes`() {
        // Descriptions under every settings row, and the folder browser's breadcrumb trail.
        assertReadable("shaded_text_color", "background_color", NORMAL_TEXT_MINIMUM)
        assertReadable("breadcrumb_parent", "background_color", NORMAL_TEXT_MINIMUM)
        assertReadable("breadcrumb_current", "background_color", NORMAL_TEXT_MINIMUM)
    }

    @Test
    fun `the unselected navigation label is readable in both themes`() {
        assertReadable("navigation_menu_icon_not_selected", "navigation_menu_background", LARGE_TEXT_MINIMUM)
    }

    private fun assertReadable(foreground: String, background: String, minimum: Double) {
        listOf("light" to light, "dark" to dark).forEach { (name, palette) ->
            val ratio = contrast(palette.getValue(foreground), palette.getValue(background))
            assertTrue(
                "$foreground on $background in $name is ${"%.2f".format(ratio)}:1, below $minimum:1",
                ratio >= minimum,
            )
        }
    }

    /** Reads `<color name="x">#aarrggbb</color>` pairs straight from the resource file. */
    private fun colorsFrom(path: String): Map<String, Long> =
        COLOR_ENTRY.findAll(File(path).readText())
            .associate { it.groupValues[1] to it.groupValues[2].toLong(16) }
            .mapValues { (_, value) -> value }

    /**
     * WCAG contrast between two colours, flattening any alpha onto the background first.
     *
     * The alpha step matters here: the app's secondary text is a translucent black or white, so comparing the raw value
     * would say it contrasts perfectly with itself.
     */
    private fun contrast(foreground: Long, background: Long): Double {
        val bg = opaque(background)
        val fg = flatten(foreground, bg)
        val lighter = max(luminance(fg), luminance(bg))
        val darker = min(luminance(fg), luminance(bg))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun opaque(color: Long) = Triple(red(color), green(color), blue(color))

    private fun flatten(color: Long, onto: Triple<Int, Int, Int>): Triple<Int, Int, Int> {
        val alpha = if (color > 0xFFFFFF) ((color shr 24) and 0xFF) / 255.0 else 1.0
        fun blend(over: Int, under: Int) = (over * alpha + under * (1 - alpha)).toInt()
        return Triple(blend(red(color), onto.first), blend(green(color), onto.second), blend(blue(color), onto.third))
    }

    private fun luminance(color: Triple<Int, Int, Int>): Double {
        fun channel(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.first) + 0.7152 * channel(color.second) + 0.0722 * channel(color.third)
    }

    private fun red(color: Long) = ((color shr 16) and 0xFF).toInt()

    private fun green(color: Long) = ((color shr 8) and 0xFF).toInt()

    private fun blue(color: Long) = (color and 0xFF).toInt()

    private companion object {
        val COLOR_ENTRY = Regex("""<color name="([^"]+)">#([0-9A-Fa-f]{6,8})</color>""")

        /** WCAG AA for body text. */
        const val NORMAL_TEXT_MINIMUM = 4.5

        /** WCAG AA for large or bold text, and for icons. */
        const val LARGE_TEXT_MINIMUM = 3.0
    }
}
