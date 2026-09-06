package com.dominikdomotor.nextcloudpasswords.ui.theme

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.TonalPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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
    fun generate(seed: Int, isDark: Boolean, isAmoled: Boolean, tintedText: Boolean = false): Map<String, Int> {
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
                "npac_outline" to scheme.outline,
                "npac_outline_variant" to scheme.outlineVariant,
                "npac_error" to scheme.error,
                "npac_on_error" to scheme.onError,
                "npac_error_container" to scheme.errorContainer,
                "npac_on_error_container" to scheme.onErrorContainer,
            )
        val readable = palette + readableOnContainers(scheme)
        val surfaced = if (isAmoled) readable + amoledSurfaces(scheme) else readable
        val texted = if (tintedText) surfaced else surfaced + neutralBodyText(surfaced)
        val opaqued = texted.mapValues { (_, value) -> opaque(value) }
        // The ripple is the one slot that must stay translucent: it is drawn over whatever it touches, so an opaque
        // value would blank out the row, button or icon underneath instead of tinting it.
        return opaqued + ("npac_ripple" to translucent(opaqued.getValue("npac_on_surface"), RIPPLE_ALPHA))
    }

    /**
     * Every slot the app declares, so one added here and forgotten in the XML fails a test rather than a screen.
     *
     * Lazy, not eager: it is derived by running [generate], which reads private state declared further down this
     * object. An eager initialiser runs before that state exists and threw a NullPointerException from inside the class
     * initialiser - a failure that points nowhere near its cause.
     */
    val SLOTS: Set<String> by lazy { generate(NEXTCLOUD_BLUE, isDark = false, isAmoled = false).keys }

    /**
     * Makes the filled containers readable, which for the secondary one means moving the container too.
     *
     * `SchemeContent` aims its on-container colours at roughly 4.5:1 - the WCAG floor, and no more. That is a
     * defensible choice for a scheme meant to stay faithful to one source colour, but it is thin for text, and this app
     * puts text on `secondaryContainer` more than anywhere else: every row of the folder picker, the share list and the
     * blocked-apps list, plus every tonal button in a dialog. At 4.5:1 those rows read as washed out.
     *
     * Two steps, because fixing only the text is not enough. Each on-container role is taken from its own tonal palette
     * at whichever end reads best against the container - decided per container, since `primaryContainer` under
     * `SchemeContent` is the source colour itself and may be light or dark, so one fixed tone would be right for one
     * seed and unreadable for the next.
     *
     * `secondaryContainer` is then also put back on Material's own tones, 90 in light and 30 in dark. A container that
     * lands mid-tone cannot carry readable text at all - a pure red seed produced one where even black-or-white only
     * reached 5.4:1 - so no choice of text colour would have fixed it. At the standard tones the pair clears 8:1 for
     * every seed. The vivid containers are left alone: `primaryContainer` is the floating action button, where being
     * eye-catching is the job and the only thing on it is an icon.
     */
    private fun readableOnContainers(scheme: SchemeContent): Map<String, Int> {
        val secondaryContainer =
            scheme.secondaryPalette.tone(if (scheme.isDark) DARK_CONTAINER_TONE else LIGHT_CONTAINER_TONE)
        return mapOf(
            "npac_secondary_container" to secondaryContainer,
            "npac_on_primary_container" to readableOn(scheme.primaryContainer, scheme.primaryPalette),
            "npac_on_secondary_container" to readableOn(secondaryContainer, scheme.secondaryPalette),
            "npac_on_tertiary_container" to readableOn(scheme.tertiaryContainer, scheme.tertiaryPalette),
            "npac_on_error_container" to readableOn(scheme.errorContainer, scheme.errorPalette),
        )
    }

    /**
     * The most readable text tone for a filled container, preferring one that still carries the hue.
     *
     * Tinted first: tones 10 and 98 keep the palette's hue and chroma, so a label still belongs to the container it
     * sits on, and against a container at either end of the tone range one of them is far away and gives 8:1 or more.
     *
     * The escalation is for containers that land in the middle, which `SchemeContent` produces because it uses the
     * source colour itself as `primaryContainer`. Neither tinted tone is far enough from a tone-50 container to be
     * comfortable, so the choice widens to the palette's extremes - effectively black and white - and takes whichever
     * is better. Readable and untinted beats tinted and unreadable.
     *
     * Chosen by measuring contrast rather than by comparing tones, because at the boundary the two disagree: against
     * `#007ABD`, tone 98 and tone 10 are almost equidistant, but white is a fifth more readable than black.
     */
    private fun readableOn(container: Int, palette: TonalPalette): Int {
        val tinted = mostReadable(container, palette, TINTED_TEXT_TONES)
        return if (contrast(tinted, container) >= MINIMUM_CONTRAST) tinted
        else mostReadable(container, palette, EXTREME_TEXT_TONES)
    }

    private fun mostReadable(container: Int, palette: TonalPalette, tones: List<Int>): Int =
        tones.map(palette::tone).maxBy { contrast(it, container) }

    /** WCAG contrast. Every colour here is opaque, so there is no alpha to flatten first. */
    private fun contrast(a: Int, b: Int): Double {
        val first = relativeLuminance(a)
        val second = relativeLuminance(b)
        return (max(first, second) + WCAG_OFFSET) / (min(first, second) + WCAG_OFFSET)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and CHANNEL) / CHANNEL_MAX
            return if (value <= SRGB_KNEE) value / SRGB_SLOPE
            else ((value + SRGB_OFFSET) / (1 + SRGB_OFFSET)).pow(SRGB_GAMMA)
        }
        return RED_LUMA * channel(RED_SHIFT) + GREEN_LUMA * channel(GREEN_SHIFT) + BLUE_LUMA * channel(0)
    }

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

    /**
     * Re-emits the body-text roles as greys, at exactly the tone the scheme chose for them.
     *
     * `SchemeContent` keeps the seed's chroma all the way through the neutral palette, which is what makes the surfaces
     * read as tinted - the point of the mode. Carried into the text roles, though, the same chroma is a cast over every
     * word in the app: a saturated red seed gives pink paragraphs. Text is the one thing being *itself* matters more
     * than being on-brand for, so by default it is a shade of black or white and only the surfaces are tinted.
     * `tintedText` turns that off for anyone who wants the full effect.
     *
     * Tone is what carries luminance in HCT, so holding the tone and dropping the chroma to zero leaves contrast where
     * it was - the WCAG assertions hold identically either way, which is what makes this safe to default to.
     *
     * Only the roles that sit on a surface. `onPrimary` and the on-container roles belong to the accent itself, and
     * greying a button's own label would just make the button look broken.
     */
    private fun neutralBodyText(palette: Map<String, Int>): Map<String, Int> =
        BODY_TEXT_SLOTS.associateWith { slot -> greyOf(palette.getValue(slot)) }

    /** The same colour with its hue and chroma removed, and therefore the same perceived lightness. */
    private fun greyOf(color: Int): Int = Hct.from(0.0, 0.0, Hct.fromInt(color).tone).toInt()

    /** A seed arriving as `#rrggbb` has no alpha; a translucent surface would let the window show through. */
    private fun opaque(color: Int): Int = color or OPAQUE

    private fun translucent(color: Int, alpha: Int): Int = (color and RGB_MASK) or (alpha shl ALPHA_SHIFT)

    /** The app's own colour, and the default seed when the server has none. */
    const val NEXTCLOUD_BLUE: Int = 0xFF0082C9.toInt()

    private const val OPAQUE = 0xFF000000.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val RGB_MASK = 0x00FFFFFF
    private const val ALPHA_SHIFT = 24
    private const val CHANNEL = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /** Material's own press-state opacity, about 12%. */
    private const val RIPPLE_ALPHA = 0x1F

    /** Material's neutral contrast. Positive values raise it; the roles already meet WCAG AA at zero. */
    private const val CONTRAST_DEFAULT = 0.0

    /** Above this the container is light enough for dark text; below it, the other way round. */
    /** Material's own container tones, which are chosen to carry text. */
    private const val LIGHT_CONTAINER_TONE = 90
    private const val DARK_CONTAINER_TONE = 30

    /** Tones that keep the palette's hue, so a readable label is still a tinted one wherever it can be. */
    private val TINTED_TEXT_TONES = listOf(10, 98)

    /** The fallback for a mid-tone container: the ends of the tone range, which are effectively black and white. */
    private val EXTREME_TEXT_TONES = listOf(0, 100)

    /** WCAG AA for body text. Text on a container is still text, so this is the floor the choice has to clear. */
    private const val MINIMUM_CONTRAST = 4.5

    private const val WCAG_OFFSET = 0.05
    private const val CHANNEL_MAX = 255.0
    private const val SRGB_KNEE = 0.03928
    private const val SRGB_SLOPE = 12.92
    private const val SRGB_OFFSET = 0.055
    private const val SRGB_GAMMA = 2.4
    private const val RED_LUMA = 0.2126
    private const val GREEN_LUMA = 0.7152
    private const val BLUE_LUMA = 0.0722

    private val BODY_TEXT_SLOTS =
        listOf("npac_on_surface", "npac_on_surface_variant", "npac_on_background", "npac_inverse_on_surface")

    private const val AMOLED_CONTAINER_LOW = 6
    private const val AMOLED_CONTAINER = 9
    private const val AMOLED_CONTAINER_HIGH = 13
    private const val AMOLED_CONTAINER_HIGHEST = 18
    private const val AMOLED_BRIGHT = 25
    private const val AMOLED_ON_SURFACE = 96
}
