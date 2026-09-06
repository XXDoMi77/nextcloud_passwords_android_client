package com.dominikdomotor.nextcloudpasswords.dataclasses

/**
 * Light, dark, or the two variants of dark.
 *
 * One enum rather than a night-mode setting plus a separate AMOLED switch. "AMOLED forces dark" is then a property of
 * the value instead of a rule two places have to agree on - the version with a switch can desync, and the state where
 * the app is in light mode with AMOLED on has no meaning.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,

    /** Dark, with the surfaces pushed to true black so an OLED panel can switch those pixels off. */
    AMOLED;

    val isAmoled: Boolean
        get() = this == AMOLED
}

/** Where the seed colour that the whole palette is generated from comes from. */
enum class ThemeSeedSource {
    /** The colour the Nextcloud admin set in the Theming app. */
    SERVER,

    /** A colour the user picked by hand, which is what makes "reset" mean "go back to the server's". */
    CUSTOM,

    /** The system wallpaper, the way the rest of the phone is themed. */
    WALLPAPER,
}
