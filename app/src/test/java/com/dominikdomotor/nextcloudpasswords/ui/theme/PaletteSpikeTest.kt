package com.dominikdomotor.nextcloudpasswords.ui.theme

import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Spike: does Material's bundled colour science run on the plain JVM test classpath?
 *
 * The whole seeded-theme design rests on this. The classes are `@RestrictTo(LIBRARY_GROUP)` but pure Java with no
 * `Build.VERSION` checks, so they should compute a scheme with no device involved. If anything in here reached for
 * `android.util.Log` or a `Resources`, this test would fail and the design would need a build-time generator instead.
 *
 * Delete this file once PaletteGenerator and its own tests exist.
 */
class PaletteSpikeTest {
    @Test
    fun `a scheme can be generated from a seed on the JVM`() {
        val scheme = SchemeContent(Hct.fromInt(NEXTCLOUD_BLUE), /* isDark= */ false, /* contrastLevel= */ 0.0)

        // Print a few roles so the spike shows what we actually get to work with.
        println("seed        #%06X".format(NEXTCLOUD_BLUE and RGB))
        listOf(
                "primary" to scheme.primary,
                "onPrimary" to scheme.onPrimary,
                "primaryContainer" to scheme.primaryContainer,
                "onPrimaryContainer" to scheme.onPrimaryContainer,
                "secondaryContainer" to scheme.secondaryContainer,
                "onSecondaryContainer" to scheme.onSecondaryContainer,
                "surface" to scheme.surface,
                "onSurface" to scheme.onSurface,
                "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                "onSurfaceVariant" to scheme.onSurfaceVariant,
                "outline" to scheme.outline,
                "errorContainer" to scheme.errorContainer,
            )
            .forEach { (name, value) -> println("%-22s #%06X".format(name, value and RGB)) }

        // Every role must be opaque, or a tinted surface would let the window show through.
        assertEquals(OPAQUE, scheme.surface.toLong() and OPAQUE)
        // A light scheme's surface must not equal its on-surface, or nothing would be readable.
        assertNotEquals(scheme.surface, scheme.onSurface)
    }

    @Test
    fun `light and dark schemes from one seed differ`() {
        val light = SchemeContent(Hct.fromInt(NEXTCLOUD_BLUE), false, 0.0)
        val dark = SchemeContent(Hct.fromInt(NEXTCLOUD_BLUE), true, 0.0)
        println("light surface #%06X / dark surface #%06X".format(light.surface and RGB, dark.surface and RGB))
        assertNotEquals(light.surface, dark.surface)
    }

    @Test
    fun `generation is deterministic`() {
        val first = SchemeContent(Hct.fromInt(NEXTCLOUD_BLUE), false, 0.0).primary
        val second = SchemeContent(Hct.fromInt(NEXTCLOUD_BLUE), false, 0.0).primary
        assertEquals(first, second)
    }

    @Test
    fun `a zero-chroma seed still produces a usable scheme`() {
        // Mid-grey has no hue at all, which is the input that breaks naive palette generators.
        val scheme = SchemeContent(Hct.fromInt(0xFF808080.toInt()), false, 0.0)
        println("grey seed -> primary #%06X surface #%06X".format(scheme.primary and RGB, scheme.surface and RGB))
        assertNotEquals(scheme.surface, scheme.onSurface)
    }

    private companion object {
        const val NEXTCLOUD_BLUE = 0xFF0082C9.toInt()
        const val RGB = 0xFFFFFF
        const val OPAQUE = 0xFF000000L
    }
}
