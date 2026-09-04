package com.dominikdomotor.nextcloudpasswords.ui.theme

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeContent

/**
 * Turns one seed colour into the full set of Material 3 roles the app paints with.
 *
 * The seed is the colour the Nextcloud admin set in the Theming app, one the user picked, or the wallpaper's. Material
 * ships the colour science that does this - HCT tone mapping - and it is plain Java with no `Build.VERSION` checks, so
 * the same maths runs on Android 10 and in a JVM unit test alike. That is why this file imports nothing from
 * `android.*` beyond the suppression annotation: the palette is a pure function of the seed, and it is tested as one.
 *
 * `SchemeContent` is the variant that stays closest to the seed instead of treating it as a hint, which is what makes
 * an admin's brand colour still recognisable as itself once it reaches the screen.
 *
 * Everything in `com.google.android.material.color.utilities` is `@RestrictTo(LIBRARY_GROUP)`. The suppression is
 * confined to this one file so that a Material upgrade means re-testing one file rather than auditing the app.
 */
@SuppressLint("RestrictedApi")
object PaletteGenerator {
    /**
     * The palette for one seed and one mode, keyed by the colour resource names in `values/theme_colors.xml`.
     *
     * A map rather than a data class because the caller's job is to hand the pairs to a `ResourcesLoader` by name, and
     * a test can then assert that these keys are exactly the resources the XML declares.
     */
    fun generate(seed: Int, isDark: Boolean, isAmoled: Boolean): Map<String, Int> {
        val scheme = SchemeContent(Hct.fromInt(opaque(seed)), isDark || isAmoled, CONTRAST_DEFAULT)
        val palette =
            mapOf(
                "npac_primary" to scheme.primary,
                "npac_on_primary" to scheme.onPrimary,
                "npac_primary_container" to scheme.primaryContainer,
                "npac_on_primary_container" to scheme.onPrimaryContainer,
                "npac_inverse_primary" to scheme.inversePrimary,
                "npac_secondary" to scheme.secondary,
                "npac_on_secondary" to scheme.onSecondary,
                "npac_secondary_container" to scheme.secondaryContainer,
                "npac_on_secondary_container" to scheme.onSecondaryContainer,
                "npac_tertiary" to scheme.tertiary,
                "npac_on_tertiary" to scheme.onTertiary,
                "npac_tertiary_container" to scheme.tertiaryContainer,
                "npac_on_tertiary_container" to scheme.onTertiaryContainer,
                "npac_background" to scheme.background,
                "npac_on_background" to scheme.onBackground,
                "npac_surface" to scheme.surface,
                "npac_on_surface" to scheme.onSurface,
                "npac_surface_variant" to scheme.surfaceVariant,
                "npac_on_surface_variant" to scheme.onSurfaceVariant,
                "npac_surface_dim" to scheme.surfaceDim,
                "npac_surface_bright" to scheme.surfaceBright,
                "npac_surface_container_lowest" to scheme.surfaceContainerLowest,
                "npac_surface_container_low" to scheme.surfaceContainerLow,
                "npac_surface_container" to scheme.surfaceContainer,
                "npac_surface_container_high" to scheme.surfaceContainerHigh,
                "npac_surface_container_highest" to scheme.surfaceContainerHighest,
                "npac_inverse_surface" to scheme.inverseSurface,
                "npac_inverse_on_surface" to scheme.inverseOnSurface,
                "npac_surface_tint" to scheme.primary,
                "npac_outline" to scheme.outline,
                "npac_outline_variant" to scheme.outlineVariant,
                "npac_error" to scheme.error,
                "npac_on_error" to scheme.onError,
                "npac_error_container" to scheme.errorContainer,
                "npac_on_error_container" to scheme.onErrorContainer,
                "npac_scrim" to scheme.scrim,
            )
        val surfaced = if (isAmoled) palette + amoledSurfaces(scheme) else palette
        val opaqued = surfaced.mapValues { (_, value) -> opaque(value) }
        // The ripple is the one slot that must stay translucent: it is drawn over whatever it touches, so an opaque
        // value would blank out the row, button or icon underneath instead of tinting it.
        return opaqued + ("npac_ripple" to translucent(opaqued.getValue("npac_on_surface"), RIPPLE_ALPHA))
    }

    /** Every slot the app declares, so a slot added here and forgotten in the XML fails a test rather than a screen. */
    val SLOTS: Set<String> = generate(NEXTCLOUD_BLUE, isDark = false, isAmoled = false).keys

    /**
     * Pushes the surfaces to true black without flattening the ramp.
     *
     * The window and the base surface go to `#000000` - that is the whole point of the mode, and what an OLED panel
     * switches off for. The container ramp above it must not follow, though: cards, dialogs, the bottom sheet and the
     * nav bar are told apart only by sitting a shade above their background, so they keep near-black *tinted* tones
     * from the seed's neutral palette. Black-on-black would leave every one of those edges invisible.
     *
     * On-surface is lifted at the same time, because text tuned for a tone-6 background is dim against pure black.
     */
    private fun amoledSurfaces(scheme: SchemeContent): Map<String, Int> {
        val neutral = scheme.neutralPalette
        return mapOf(
            "npac_background" to BLACK,
            "npac_surface" to BLACK,
            "npac_surface_dim" to BLACK,
            "npac_surface_container_lowest" to BLACK,
            "npac_surface_container_low" to neutral.tone(AMOLED_CONTAINER_LOW),
            "npac_surface_container" to neutral.tone(AMOLED_CONTAINER),
            "npac_surface_container_high" to neutral.tone(AMOLED_CONTAINER_HIGH),
            "npac_surface_container_highest" to neutral.tone(AMOLED_CONTAINER_HIGHEST),
            "npac_surface_bright" to neutral.tone(AMOLED_BRIGHT),
            "npac_on_background" to neutral.tone(AMOLED_ON_SURFACE),
            "npac_on_surface" to neutral.tone(AMOLED_ON_SURFACE),
        )
    }

    /** A seed arriving as `#rrggbb` has no alpha; a translucent surface would let the window show through. */
    private fun opaque(color: Int): Int = color or OPAQUE

    private fun translucent(color: Int, alpha: Int): Int = (color and RGB_MASK) or (alpha shl ALPHA_SHIFT)

    /** The app's own colour, and the default seed when the server has none. */
    const val NEXTCLOUD_BLUE: Int = 0xFF0082C9.toInt()

    private const val OPAQUE = 0xFF000000.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val RGB_MASK = 0x00FFFFFF
    private const val ALPHA_SHIFT = 24

    /** Material's own press-state opacity, about 12%. */
    private const val RIPPLE_ALPHA = 0x1F

    /** Material's neutral contrast. Positive values raise it; the roles already meet WCAG AA at zero. */
    private const val CONTRAST_DEFAULT = 0.0

    private const val AMOLED_CONTAINER_LOW = 6
    private const val AMOLED_CONTAINER = 9
    private const val AMOLED_CONTAINER_HIGH = 13
    private const val AMOLED_CONTAINER_HIGHEST = 18
    private const val AMOLED_BRIGHT = 25
    private const val AMOLED_ON_SURFACE = 96
}
