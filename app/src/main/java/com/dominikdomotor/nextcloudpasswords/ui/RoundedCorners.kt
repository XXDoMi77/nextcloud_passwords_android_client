package com.dominikdomotor.nextcloudpasswords.ui

import android.graphics.Color
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.graphics.drawable.toDrawable

/**
 * Rounds a view's corners by clipping, so whatever is inside is cut to the same shape.
 *
 * This is how a grouped list gets its rounded ends: rows are square and the list clips them, so the corner stays put
 * while rows scroll through it.
 */
fun View.clipToRoundedCorners(radiusPx: Float) {
    outlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
    // A view with no background is skipped by the draw pipeline that applies the outline clip, so it
    // needs one even though it paints nothing.
    if (background == null) background = Color.TRANSPARENT.toDrawable()
    clipToOutline = true
}
