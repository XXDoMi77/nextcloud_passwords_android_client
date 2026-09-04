package com.dominikdomotor.nextcloudpasswords.ui.theme

import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the generated palette, across every seed a user can actually produce.
 *
 * This is the test that replaced a static check of `colors.xml`. It has to be: the colours are no longer written down
 * anywhere, so "is the text readable" is now a question about a function rather than about a file, and a seed nobody
 * anticipated is exactly where it would break. The seeds below are the awkward ones - a fully saturated primary, a
 * near-black, a near-white, and a mid-grey with no hue at all, which is the input that breaks naive generators.
 *
 * Android Studio only flags contrast as an IDE inspection, so nothing caught it in a build or in CI; the app's shaded
 * text sat at 2.6:1 for a long time before anyone noticed by eye.
 */
class PaletteGeneratorTest {
    private val seeds =
        mapOf(
            "nextcloud blue" to PaletteGenerator.NEXTCLOUD_BLUE,
            "pure red" to 0xFFFF0000.toInt(),
            "pure green" to 0xFF00FF00.toInt(),
            "pure yellow" to 0xFFFFFF00.toInt(),
            "near black" to 0xFF050505.toInt(),
            "near white" to 0xFFFAFAFA.toInt(),
            "mid grey" to 0xFF808080.toInt(),
        )

    private val modes = listOf(Triple("light", false, false), Triple("dark", true, false), Triple("amoled", true, true))

    /** Both text treatments, because the readable one is the default and the tinted one is still a supported look. */
    private val textTreatments = listOf("neutral text" to false, "tinted text" to true)

    @Test
    fun `text is readable on every surface it is shown on`() {
        // Only the pairs the app actually puts on screen. A role pair Material defines but no screen uses would fail
        // here for no one's benefit.
        val bodyPairs =
            listOf(
                "npac_on_surface" to "npac_surface",
                "npac_on_surface_variant" to "npac_surface",
                "npac_on_surface" to "npac_surface_container",
                "npac_on_surface_variant" to "npac_surface_container",
                "npac_on_surface" to "npac_surface_container_high",
                "npac_on_primary" to "npac_primary",
                "npac_on_primary_container" to "npac_primary_container",
                "npac_on_secondary_container" to "npac_secondary_container",
                "npac_on_error_container" to "npac_error_container",
                "npac_inverse_on_surface" to "npac_inverse_surface",
            )
        forEachPalette { name, palette ->
            bodyPairs.forEach { (fg, bg) -> assertReadable(name, palette, fg, bg, NORMAL_TEXT_MINIMUM) }
            // Outlines and icons are the large-text case: they only have to be findable, not legible.
            assertReadable(name, palette, "npac_outline", "npac_surface", LARGE_TEXT_MINIMUM)
        }
    }

    @Test
    fun `surfaces stay distinguishable from the containers stacked on them`() {
        // Cards, dialogs, the bottom sheet and the nav bar are told apart from their background only by tone. This is
        // the assertion that stops AMOLED mode from blacking out the whole ramp and losing every edge.
        forEachPalette { name, palette ->
            listOf("npac_surface_container", "npac_surface_container_high").forEach { container ->
                val ratio = contrast(palette.getValue(container), palette.getValue("npac_surface"))
                assertTrue(
                    "$container is indistinguishable from npac_surface in $name",
                    ratio >= SURFACE_SEPARATION_MINIMUM,
                )
            }
        }
    }

    @Test
    fun `amoled paints the window itself black`() {
        seeds.forEach { (seedName, seed) ->
            val palette = PaletteGenerator.generate(seed, isDark = true, isAmoled = true)
            assertEquals("npac_surface is not black for $seedName", BLACK, palette.getValue("npac_surface"))
            assertEquals("npac_background is not black for $seedName", BLACK, palette.getValue("npac_background"))
        }
    }

    @Test
    fun `every slot is opaque except the ripple`() {
        // A translucent surface would let the window show through; a translucent role handed to a tint would wash out.
        // The ripple is the deliberate exception - it is drawn over what it touches.
        forEachPalette { name, palette ->
            palette
                .filterKeys { it != "npac_ripple" }
                .forEach { (slot, color) ->
                    assertEquals("$slot is not opaque in $name", OPAQUE, color.toLong() and OPAQUE)
                }
            val rippleAlpha = (palette.getValue("npac_ripple").toLong() shr ALPHA_SHIFT) and CHANNEL
            assertTrue("the ripple is opaque in $name", rippleAlpha in 1 until CHANNEL)
        }
    }

    @Test
    fun `body text is a shade of grey unless tinting is asked for`() {
        // Text carrying the seed's hue is legible but distracting - a red seed gave pink paragraphs - so by default
        // only the surfaces are tinted. The pure-red seed is the one that makes a regression here obvious.
        seeds.forEach { (seedName, seed) ->
            modes.forEach { (modeName, dark, amoled) ->
                val palette = PaletteGenerator.generate(seed, dark, amoled, tintedText = false)
                BODY_TEXT_SLOTS.forEach { slot ->
                    val color = palette.getValue(slot)
                    val r = (color shr RED_SHIFT) and 0xFF
                    val g = (color shr GREEN_SHIFT) and 0xFF
                    val b = color and 0xFF
                    assertEquals("$slot is not grey for $seedName / $modeName: %06X".format(color and 0xFFFFFF), r, g)
                    assertEquals("$slot is not grey for $seedName / $modeName: %06X".format(color and 0xFFFFFF), g, b)
                }
            }
        }
    }

    @Test
    fun `neutralising the text does not cost contrast`() {
        // Holding the tone and dropping only the chroma is what makes the readable default safe: it must not quietly
        // trade legibility for neutrality. A tenth of a ratio point covers the rounding in the sRGB conversion.
        seeds.forEach { (seedName, seed) ->
            modes.forEach { (modeName, dark, amoled) ->
                val tinted = PaletteGenerator.generate(seed, dark, amoled, tintedText = true)
                val neutral = PaletteGenerator.generate(seed, dark, amoled, tintedText = false)
                val before = contrast(tinted.getValue("npac_on_surface"), tinted.getValue("npac_surface"))
                val after = contrast(neutral.getValue("npac_on_surface"), neutral.getValue("npac_surface"))
                assertTrue(
                    "greying the text cost contrast for $seedName / $modeName: $before:1 -> $after:1",
                    after >= before - CONTRAST_TOLERANCE,
                )
            }
        }
    }

    @Test
    fun `generation is deterministic`() {
        // The palette is regenerated on every activity start. If it were not stable, a rotation would re-tint the app.
        seeds.values.forEach { seed ->
            assertEquals(
                PaletteGenerator.generate(seed, isDark = false, isAmoled = false),
                PaletteGenerator.generate(seed, isDark = false, isAmoled = false),
            )
        }
    }

    @Test
    fun `the generator's slots are exactly the ones the XML declares`() {
        // The two halves are wired by name, and a name that exists on only one side fails silently: the generator's
        // value goes nowhere, or the theme keeps painting a static default. This is the check that catches it.
        listOf("src/main/res/values/theme_colors.xml", "src/main/res/values-night/theme_colors.xml").forEach { path ->
            val declared = COLOR_ENTRY.findAll(File(path).readText()).map { it.groupValues[1] }.toSet()
            assertEquals("$path does not match PaletteGenerator's slots", PaletteGenerator.SLOTS, declared)
        }
    }

    @Test
    fun `the static defaults are what the generator produces`() {
        // They are the Android 10 palette and the first frame of every start, so a hand-edit here would show up as the
        // app briefly painting a colour it never generates.
        listOf(false to "src/main/res/values/theme_colors.xml", true to "src/main/res/values-night/theme_colors.xml")
            .forEach { (dark, path) ->
                val declared =
                    COLOR_ENTRY.findAll(File(path).readText()).associate {
                        it.groupValues[1] to it.groupValues[2].toLong(HEX_RADIX).toInt()
                    }
                val generated = PaletteGenerator.generate(PaletteGenerator.NEXTCLOUD_BLUE, dark, isAmoled = false)
                assertEquals("$path is stale - regenerate it from PaletteGenerator", generated, declared)
            }
    }

    private fun forEachPalette(check: (String, Map<String, Int>) -> Unit) {
        seeds.forEach { (seedName, seed) ->
            modes.forEach { (modeName, dark, amoled) ->
                textTreatments.forEach { (textName, tinted) ->
                    check("$seedName / $modeName / $textName", PaletteGenerator.generate(seed, dark, amoled, tinted))
                }
            }
        }
    }

    private fun assertReadable(
        name: String,
        palette: Map<String, Int>,
        foreground: String,
        background: String,
        minimum: Double,
    ) {
        val ratio = contrast(palette.getValue(foreground), palette.getValue(background))
        assertTrue(
            "$foreground on $background in $name is ${"%.2f".format(ratio)}:1, below $minimum:1",
            ratio >= minimum,
        )
    }

    /** WCAG contrast between two colours. Every slot but the ripple is opaque, so there is no alpha to flatten. */
    private fun contrast(foreground: Int, background: Int): Double {
        val lighter = max(luminance(foreground), luminance(background))
        val darker = min(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val v = ((color.toLong() shr shift) and CHANNEL) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(RED_SHIFT) + 0.7152 * channel(GREEN_SHIFT) + 0.0722 * channel(0)
    }

    private companion object {
        val COLOR_ENTRY = Regex("""<color name="([^"]+)">#([0-9A-Fa-f]{6,8})</color>""")

        /** WCAG AA for body text. */
        const val NORMAL_TEXT_MINIMUM = 4.5

        /** WCAG AA for large or bold text, and for icons. */
        const val LARGE_TEXT_MINIMUM = 3.0

        /** Enough for an edge to be visible without the container reading as a different colour. */
        const val SURFACE_SEPARATION_MINIMUM = 1.05

        const val BLACK = 0xFF000000.toInt()
        const val OPAQUE = 0xFF000000L
        const val CHANNEL = 0xFFL
        const val ALPHA_SHIFT = 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val HEX_RADIX = 16

        /** Enough to absorb the rounding in the HCT-to-sRGB conversion, not enough to hide a real regression. */
        const val CONTRAST_TOLERANCE = 0.1

        /** The roles that carry body copy, and therefore the ones the tinting toggle applies to. */
        val BODY_TEXT_SLOTS =
            listOf("npac_on_surface", "npac_on_surface_variant", "npac_on_background", "npac_inverse_on_surface")
    }
}
