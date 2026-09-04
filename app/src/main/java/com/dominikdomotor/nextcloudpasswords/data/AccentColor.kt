package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import com.dominikdomotor.nextcloudpasswords.ui.theme.PaletteGenerator

/**
 * The one colour the app treats as its accent, and where it comes from.
 *
 * Three sources in order: what the user picked here, the colour the Nextcloud admin set in the Theming app, and finally
 * the built-in fallback. Storing the server's colour rather than applying it directly is what makes "reset"
 * meaningful - the app can always go back to what the server says without another round trip.
 */
object AccentColor {
    /**
     * The seed colour, which the whole palette is then generated from.
     *
     * No `Context` any more: the fallback used to be a colour resource, and a resource is exactly what this can no
     * longer read - `@color/npac_*` is overridden at runtime *from* this value, so asking the resources for it would be
     * circular. The default now sits next to the generator that consumes it.
     */
    fun of(settings: Settings): Int =
        parse(settings.accentColorOverride) ?: parse(settings.serverThemeColor) ?: PaletteGenerator.NEXTCLOUD_BLUE

    /** What the reset button goes back to: the server's colour, or the built-in one if the server has none. */
    fun serverDefault(settings: Settings): Int = parse(settings.serverThemeColor) ?: PaletteGenerator.NEXTCLOUD_BLUE

    /** True when the app is showing the server's colour rather than a hand-picked one. */
    fun isServerColour(settings: Settings): Boolean = parse(settings.accentColorOverride) == null

    /**
     * Parses `#rrggbb`, tolerating a missing hash and surrounding space. Null when it is not a colour.
     *
     * Done by hand rather than with `Color.parseColor`, so the hex field's validation is plain Kotlin and can be unit
     * tested without a device.
     */
    fun parse(value: String?): Int? {
        val hex = value?.trim()?.removePrefix("#").orEmpty()
        if (hex.length != RGB_DIGITS && hex.length != ARGB_DIGITS) return null
        val parsed = hex.toLongOrNull(radix = HEX_RADIX) ?: return null
        return if (hex.length == RGB_DIGITS) (parsed or OPAQUE).toInt() else parsed.toInt()
    }

    /** Formats a colour the way the server states it, so a stored value round-trips. */
    fun format(color: Int): String = String.format("#%06X", color and RGB_MASK)

    /** Picks black or white text for a swatch of this colour, by perceived brightness. */
    fun contrastingTextOn(color: Int): Int {
        val brightness =
            RED_WEIGHT * ((color shr RED_SHIFT) and CHANNEL) +
                GREEN_WEIGHT * ((color shr GREEN_SHIFT) and CHANNEL) +
                BLUE_WEIGHT * (color and CHANNEL)
        return if (brightness > BRIGHTNESS_MIDPOINT) BLACK else WHITE
    }

    const val BLACK: Int = 0xFF000000.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()

    private const val RGB_DIGITS = 6
    private const val ARGB_DIGITS = 8
    private const val HEX_RADIX = 16
    private const val OPAQUE = 0xFF000000L
    private const val RGB_MASK = 0xFFFFFF
    private const val CHANNEL = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    // The usual perceptual weights: the eye reads green as far brighter than blue.
    private const val RED_WEIGHT = 0.299
    private const val GREEN_WEIGHT = 0.587
    private const val BLUE_WEIGHT = 0.114
    private const val BRIGHTNESS_MIDPOINT = 140
}
