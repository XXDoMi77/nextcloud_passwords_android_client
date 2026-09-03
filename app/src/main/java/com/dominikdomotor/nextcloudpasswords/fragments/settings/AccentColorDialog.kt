package com.dominikdomotor.nextcloudpasswords.fragments.settings

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import androidx.core.widget.doAfterTextChanged
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.data.AccentColor
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog

/**
 * Picks the app's accent colour.
 *
 * The hex field and the sliders are two views of the same value, so each writes back to the other; [applying] stops
 * that becoming a loop, since setting the text fires the watcher that would move the sliders that would rewrite the
 * text.
 */
object AccentColorDialog {
    fun show(activity: Activity, current: Int, serverDefault: Int, onPick: (Int?) -> Unit) {
        val dialog = AppDialog(activity)
        val content = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_accent_color, null, false)
        val preview = content.findViewById<View>(R.id.accent_preview)
        val hex = content.findViewById<EditText>(R.id.accent_hex)
        val hue = content.findViewById<SeekBar>(R.id.accent_hue)
        val saturation = content.findViewById<SeekBar>(R.id.accent_saturation)
        val brightness = content.findViewById<SeekBar>(R.id.accent_brightness)

        preview.background = GradientDrawable().apply { cornerRadius = radiusOf(activity) }
        hue.progressDrawable = hueSpectrum(activity)

        var applying = false
        var chosen = current

        fun render(color: Int, updateHex: Boolean, updateSliders: Boolean) {
            chosen = color
            applying = true
            (preview.background as GradientDrawable).setColor(color)
            if (updateHex) hex.setText(AccentColor.format(color))
            if (updateSliders) {
                val hsv = FloatArray(HSV_COMPONENTS)
                Color.colorToHSV(color, hsv)
                hue.progress = hsv[0].toInt()
                saturation.progress = (hsv[1] * PERCENT).toInt()
                brightness.progress = (hsv[2] * PERCENT).toInt()
            }
            applying = false
        }

        val fromSliders =
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (applying) return
                    val hsv =
                        floatArrayOf(
                            hue.progress.toFloat(),
                            saturation.progress / PERCENT,
                            brightness.progress / PERCENT,
                        )
                    render(Color.HSVToColor(hsv), updateHex = true, updateSliders = false)
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit

                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            }
        listOf(hue, saturation, brightness).forEach { it.setOnSeekBarChangeListener(fromSliders) }

        hex.doAfterTextChanged { text ->
            if (applying) return@doAfterTextChanged
            // Typed values are only half-written most of the time; ignore anything that is not yet a colour.
            AccentColor.parse(text?.toString())?.let { render(it, updateHex = false, updateSliders = true) }
        }

        render(current, updateHex = true, updateSliders = true)

        dialog
            .title(R.string.accent_colour)
            .content(content)
            // Puts the server's colour back in the picker rather than saving it, so it can still be cancelled.
            .button(R.string.reset_to_default, dismissOnClick = false) {
                render(serverDefault, updateHex = true, updateSliders = true)
                chosen = serverDefault
            }
            .button(R.string.cancel, destructive = true)
            .button(R.string.save) {
                // Choosing the server's own colour means "follow the server" rather than pinning that value.
                onPick(chosen.takeIf { it != serverDefault })
            }
            .show()
    }

    /** The rainbow behind the hue slider, so the control shows what it selects. */
    private fun hueSpectrum(activity: Activity): GradientDrawable {
        val colors = IntArray(HUE_STOPS) { Color.HSVToColor(floatArrayOf(it * HUE_STEP, 1f, 1f)) }
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = radiusOf(activity)
        }
    }

    private fun radiusOf(activity: Activity) = activity.resources.getDimension(R.dimen.radius_item)

    private const val HSV_COMPONENTS = 3
    private const val PERCENT = 100f
    private const val HUE_STOPS = 13
    private const val HUE_STEP = 30f
}
