package com.dominikdomotor.nextcloudpasswords.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Keeps a scrolling view's last row clear of the navigation bar.
 *
 * The app draws edge to edge, so a sheet reaches under the system bars and whatever sits at the bottom of its content
 * ends up behind them. Padding rather than a margin, with `clipToPadding="false"` in the layout: the content then
 * scrolls through the padded strip instead of stopping short of it, so nothing is hidden and no gap appears when there
 * is little to scroll.
 *
 * The padding declared in XML is kept and the inset added to it, which is why the original is captured once - applying
 * insets can run more than once for the same view, and reading the current padding each time would compound it.
 */
fun View.padBottomForSystemBars() {
    val declared = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()).bottom
        view.updatePadding(bottom = declared + bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * Keeps a header clear of the status bar.
 *
 * The counterpart of [padBottomForSystemBars] for a screen that draws its own top bar rather than using one the
 * framework insets for it.
 */
fun View.padTopForStatusBar() {
    val declared = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        view.updatePadding(top = declared + insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
