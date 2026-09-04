package com.dominikdomotor.nextcloudpasswords.ui

import android.app.Activity
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toDrawable
import com.dominikdomotor.nextcloudpasswords.R
import com.google.android.material.button.MaterialButton

/**
 * Every dialog in the app: a title, a body, and a row of buttons.
 *
 * Written rather than using `AlertDialog` for two reasons. Its button bar is a fixed arrangement of
 * `button1`/`button2`/`button3` around a weighted spacer, so a dialog that sets no neutral button still carries a
 * phantom one and the leading gap it creates. And it sizes title, content and buttons together, so forcing a common
 * height pushed the buttons off the bottom edge. Here the button row is a sibling of the content with its own height:
 * nothing is clipped, nothing is left over, and which button is destructive is the caller's choice rather than a
 * consequence of which slot it landed in.
 */
class AppDialog(private val activity: Activity) {
    private val dialog = ComponentDialog(activity, R.style.AlertDialogStyle)
    private val inflater = LayoutInflater.from(dialog.context)
    private val root = inflater.inflate(R.layout.app_dialog, null) as LinearLayout

    private val titleView: TextView = root.findViewById(R.id.app_dialog_title)
    private val contentHost: ViewGroup = root.findViewById(R.id.app_dialog_content)
    private val buttonRow: LinearLayout = root.findViewById(R.id.app_dialog_buttons)

    /** Inflate content with this so it picks up the dialog's own colours rather than the activity's. */
    val context = dialog.context

    fun title(@StringRes label: Int) = apply {
        titleView.setText(label)
        titleView.visibility = View.VISIBLE
    }

    fun message(text: CharSequence) = apply {
        content(inflater.inflate(R.layout.app_dialog_message, contentHost, false).also { (it as TextView).text = text })
    }

    fun message(@StringRes text: Int) = message(activity.getString(text))

    fun content(view: View) = apply {
        contentHost.removeAllViews()
        contentHost.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    /**
     * Adds a button to the right of the previous one, so callers add the dismissing action first.
     *
     * [destructive] marks the button that throws work away, whichever side it is on. In a confirmation that is the
     * "Yes" that discards a half-written password; in an editor it is the cancel.
     *
     * [dismissOnClick] is for buttons that validate before closing — a folder name that is still empty has to keep the
     * dialog open and show the error instead.
     */
    fun button(
        @StringRes label: Int,
        destructive: Boolean = false,
        dismissOnClick: Boolean = true,
        onClick: () -> Unit = {},
    ) = apply {
        val layout = if (destructive) R.layout.app_dialog_button_destructive else R.layout.app_dialog_button
        val button = inflater.inflate(layout, buttonRow, false) as MaterialButton
        button.setText(label)
        button.setOnClickListener {
            onClick()
            if (dismissOnClick) dialog.dismiss()
        }
        buttonRow.addView(button)
    }

    /**
     * Adds the reset button, at the start of the row and as an icon rather than a word.
     *
     * Separate from [button] because it is not one of the row's answers - it changes the dialog's contents and leaves
     * it open, so it never dismisses. Pinning it to the start also keeps it away from the button a hurried tap is
     * aiming for.
     *
     * The pinning is a weighted spacer rather than a gravity: the row is laid out end-first so the confirming button
     * stays where the thumb expects it, and a spacer is what pushes this one the other way without disturbing that.
     */
    fun resetButton(onClick: () -> Unit) = apply {
        val button = inflater.inflate(R.layout.app_dialog_button_reset, buttonRow, false) as MaterialButton
        button.setOnClickListener { onClick() }
        buttonRow.addView(button, 0)
        buttonRow.addView(Space(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }, 1)
    }

    fun dismiss() = dialog.dismiss()

    /** False once dismissed, so a late callback knows not to touch views that are gone. */
    val isShowing: Boolean
        get() = dialog.isShowing

    fun onDismiss(action: () -> Unit) = apply { dialog.setOnDismissListener { action() } }

    /**
     * Intercepts back inside the dialog.
     *
     * Return true when the press was used for something — the folder picker walks up a level — and false to let the
     * dialog close as usual.
     */
    fun onBack(handler: () -> Boolean) = apply {
        dialog.onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!handler()) dialog.dismiss()
                }
            }
        )
    }

    /** The shared large footprint, for dialogs with a list or an editor inside. */
    fun show() = apply { show(fillHeight = true) }

    /** Sized to its content, for a question with two answers. */
    fun showCompact() = apply { show(fillHeight = false) }

    /**
     * Clips the content container to a specific corner radius.
     *
     * @param radiusDp The corner radius in dp (defaults to 0f).
     */
    private fun show(fillHeight: Boolean) {
        if (!fillHeight) {
            // Let the body be as tall as it needs instead of claiming the leftover height.
            contentHost.layoutParams =
                (contentHost.layoutParams as LinearLayout.LayoutParams).apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    weight = 0f
                }
            root.layoutParams =
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.setContentView(root)
        dialog.window?.apply {
            // The rounded surface is drawn by the content's own background.
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            val metrics = activity.resources.displayMetrics
            setLayout(
                (metrics.widthPixels * WIDTH_FRACTION).toInt(),
                if (fillHeight) (metrics.heightPixels * HEIGHT_FRACTION).toInt()
                else ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        dialog.show()
    }

    private companion object {
        const val WIDTH_FRACTION = 0.92f
        const val HEIGHT_FRACTION = 0.75f
    }
}
