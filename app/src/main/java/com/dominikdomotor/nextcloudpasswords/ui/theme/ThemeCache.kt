package com.dominikdomotor.nextcloudpasswords.ui.theme

import android.app.WallpaperManager
import android.content.Context
import com.dominikdomotor.nextcloudpasswords.data.AccentColor
import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import com.dominikdomotor.nextcloudpasswords.dataclasses.ThemeMode
import com.dominikdomotor.nextcloudpasswords.dataclasses.ThemeSeedSource
import java.io.File

/** The seed and mode to paint one activity with. */
data class AppTheme(val seed: Int, val mode: ThemeMode)

/**
 * The theme, mirrored somewhere an activity can read it synchronously.
 *
 * `StorageManager` is the real source of truth, but it publishes asynchronously: it decrypts the data document on
 * `Dispatchers.IO` and only then emits. A theme has to be applied in `onCreate`, before the first inflater call, so
 * waiting for that flow would mean every start painting one frame in the default palette and then flickering.
 *
 * A plain file, deliberately not `SharedPreferences`. The autofill picker runs in `:autofill_process`, and preferences
 * are cached per process - that process would keep serving a stale theme for as long as it stayed alive, which on a
 * phone is effectively forever. A file is re-read each time. None of the three values is a secret, so this sits outside
 * the encrypted document without weakening anything.
 */
object ThemeCache {
    /** The theme to use right now. Falls back to the app's own colour when nothing has been written yet. */
    fun read(context: Context): AppTheme {
        val parts = runCatching { file(context).readText().trim().split(SEPARATOR) }.getOrNull()
        val seed = parts?.getOrNull(0)?.toIntOrNull() ?: PaletteGenerator.NEXTCLOUD_BLUE
        val mode = parts?.getOrNull(1)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
        return AppTheme(seed, mode ?: ThemeMode.SYSTEM)
    }

    /**
     * Mirrors the theme out of settings, resolving the seed to a concrete colour first.
     *
     * Resolving here rather than at read time is what keeps [read] cheap and free of a `WallpaperManager` call on the
     * critical path of every activity start.
     */
    fun write(context: Context, settings: Settings) {
        val theme = AppTheme(seedFrom(context, settings), settings.themeMode)
        // Written via a temporary file and renamed: a half-written cache read by the next start would otherwise be
        // indistinguishable from a corrupt one, and the app would silently lose the user's theme.
        runCatching {
            val temp = File(file(context).parentFile, "$FILE_NAME.tmp")
            temp.writeText("${theme.seed}$SEPARATOR${theme.mode.name}")
            temp.renameTo(file(context))
        }
    }

    /** The colour the whole palette is generated from. */
    fun seedFrom(context: Context, settings: Settings): Int =
        when (settings.themeSeedSource) {
            ThemeSeedSource.CUSTOM -> AccentColor.parse(settings.accentColorOverride)
            ThemeSeedSource.WALLPAPER -> wallpaperSeed(context)
            ThemeSeedSource.SERVER -> AccentColor.parse(settings.serverThemeColor)
        } ?: PaletteGenerator.NEXTCLOUD_BLUE

    /**
     * The dominant colour of the system wallpaper.
     *
     * Available from API 27, unlike `DynamicColors`, which needs 31 plus an OEM allowlist - so reading the colour
     * ourselves and seeding our own palette with it reaches far more devices than asking the platform to theme us.
     */
    private fun wallpaperSeed(context: Context): Int? =
        runCatching {
                WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    ?.primaryColor
                    ?.toArgb()
            }
            .getOrNull()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private const val FILE_NAME = "theme.cache"
    private const val SEPARATOR = "|"
}
