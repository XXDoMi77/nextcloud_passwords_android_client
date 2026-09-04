package com.dominikdomotor.nextcloudpasswords.ui.theme

import android.app.Activity
import androidx.annotation.ColorInt
import androidx.core.view.WindowCompat
import com.dominikdomotor.nextcloudpasswords.data.AccentColor
import com.google.android.material.R as MaterialR

/**
 * Matches the status and navigation bar icons to whatever the app paints behind them.
 *
 * The bars themselves are transparent - `window.statusBarColor` has been a no-op since API 35, and the theme sets both
 * bars transparent so every version behaves the same way. So their colour is simply the colour of the view underneath,
 * and the only thing left to decide is whether the clock and the gesture pill should be drawn dark or light.
 *
 * Asked per bar, and answered from the colour rather than from the night mode. Those come apart in both directions: a
 * light seed can produce a light surface while the phone is in dark mode, and the bottom bar sits on a different tone
 * from the top one. Deciding by luminance is right in every combination, and needs no list of exceptions.
 */
fun Activity.applySystemBarAppearance(
    @ColorInt statusBarBackground: Int = themeColor(MaterialR.attr.colorSurface),
    @ColorInt navigationBarBackground: Int = statusBarBackground,
) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = isLight(statusBarBackground)
        isAppearanceLightNavigationBars = isLight(navigationBarBackground)
    }
}

/**
 * Whether a bar over this colour needs dark icons.
 *
 * "Light appearance" means a light bar background, so the system draws its icons dark - which is the same question
 * [AccentColor.contrastingTextOn] already answers for a swatch.
 */
private fun isLight(@ColorInt background: Int): Boolean = AccentColor.contrastingTextOn(background) == AccentColor.BLACK
