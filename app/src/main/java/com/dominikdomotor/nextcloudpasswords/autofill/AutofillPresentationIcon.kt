package com.dominikdomotor.nextcloudpasswords.autofill

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.dominikdomotor.nextcloudpasswords.R

/**
 * The icon for one autofill suggestion: the site's favicon with a small badge in its corner.
 *
 * A suggestion list offers three rows per entry - fill both fields, fill only the username, fill only the password -
 * and they need telling apart at a glance, in a row two lines tall, without reading anything. The badge is the same
 * person and key pair the password list uses, in the same colours, so the meaning carries over from a screen the user
 * already knows.
 *
 * It sits on a rounded backdrop taking the bottom-right quarter. Drawn straight onto the favicon the glyph disappears
 * into whatever happens to be in that corner - a dark key on a dark logo. The backdrop is the popup's own background
 * colour, which flips with the theme, so the badge reads as a small panel laid over the icon rather than as part of it.
 *
 * Composited here rather than expressed in the layout because a RemoteViews tree is inflated by the app that asked for
 * the fill. That process cannot resolve this package's theme attributes and, below API 31, cannot be told to clear an
 * ImageView's tint either. A finished bitmap sidesteps all of it: the drawing happens in this process, where the
 * resources do resolve, and the other side only ever receives pixels.
 */
internal object AutofillPresentationIcon {
    /**
     * [favicon] with [badgeRes] in its bottom-right corner, or null when there is no favicon to badge.
     *
     * A null return is the caller's signal to fall back to the plain badge drawable: an entry with no favicon is
     * better served by a full-size person or key than by a blank square wearing a small one.
     */
    fun badged(context: Context, favicon: Bitmap?, @DrawableRes badgeRes: Int, @ColorInt badgeColor: Int): Bitmap? {
        if (favicon == null) return null

        val output = createBitmap(SIZE, SIZE)
        val canvas = Canvas(output)
        canvas.drawBitmap(favicon, null, Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG))

        val side = SIZE * BADGE_FRACTION
        val box = RectF(SIZE - side, SIZE - side, SIZE.toFloat(), SIZE.toFloat())
        val backdrop =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.autofill_presentation_badge_backdrop)
            }
        canvas.drawRoundRect(box, side * CORNER_FRACTION, side * CORNER_FRACTION, backdrop)

        val glyph = AppCompatResources.getDrawable(context, badgeRes) ?: return output
        val inset = (side * GLYPH_INSET).toInt()
        glyph.setTint(badgeColor)
        glyph.setBounds(
            box.left.toInt() + inset,
            box.top.toInt() + inset,
            box.right.toInt() - inset,
            box.bottom.toInt() - inset,
        )
        glyph.draw(canvas)

        return output
    }

    /** Comfortably above the 24dp the row draws it at, so it stays sharp on a dense screen. */
    private const val SIZE = 96

    /** The bottom-right quarter, near enough: large enough to read as a person or a key rather than a dot. */
    private const val BADGE_FRACTION = 0.55f

    /** Rounded rather than square-cornered; at this size a hard box reads as a crop of the favicon. */
    private const val CORNER_FRACTION = 0.3f
    private const val GLYPH_INSET = 0.12f
}
