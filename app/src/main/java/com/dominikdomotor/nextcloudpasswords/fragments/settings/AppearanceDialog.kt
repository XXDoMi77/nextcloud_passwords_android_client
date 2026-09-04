package com.dominikdomotor.nextcloudpasswords.fragments.settings

import android.app.Activity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.view.updatePadding
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.ThemeMode
import com.dominikdomotor.nextcloudpasswords.dataclasses.ThemeSeedSource
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog

/**
 * Picks the light/dark mode and where the palette's seed colour comes from.
 *
 * Both live in one dialog because they answer the same question - what the app looks like - and because choosing either
 * one costs an activity recreate. Two separate rows would mean two recreates for what a user thinks of as one decision.
 *
 * Built in code rather than as a layout: it is two radio groups over enums, so the entries and the layout would have to
 * be kept in step by hand in a second file, and adding a mode later would mean remembering to edit both.
 */
object AppearanceDialog {
    fun show(
        activity: Activity,
        mode: ThemeMode,
        source: ThemeSeedSource,
        onChosen: (ThemeMode, ThemeSeedSource) -> Unit,
    ) {
        val dialog = AppDialog(activity).title(R.string.theme_setting)
        val context = dialog.context

        val modes = ThemeMode.entries.toList()
        val sources = ThemeSeedSource.entries.toList()
        lateinit var modeGroup: RadioGroup
        lateinit var sourceGroup: RadioGroup

        dialog.content(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                updatePadding(
                    left = context.resources.getDimensionPixelSize(R.dimen.spacing_horizontal),
                    right = context.resources.getDimensionPixelSize(R.dimen.spacing_horizontal),
                )
                addView(heading(context, R.string.appearance_mode))
                modeGroup = radioGroup(context, modes.map { context.getString(labelFor(it)) }, modes.indexOf(mode))
                addView(modeGroup)
                addView(heading(context, R.string.appearance_colour_source))
                sourceGroup =
                    radioGroup(context, sources.map { context.getString(labelFor(it)) }, sources.indexOf(source))
                addView(sourceGroup)
            }
        )

        dialog
            .button(R.string.cancel)
            .button(R.string.save) {
                onChosen(
                    modes.getOrElse(modeGroup.checkedRadioButtonId) { mode },
                    sources.getOrElse(sourceGroup.checkedRadioButtonId) { source },
                )
            }
            .show()
    }

    /** The description under the settings row: the mode, and what it is taking its colour from. */
    fun summaryFor(mode: ThemeMode, source: ThemeSeedSource): Pair<Int, Int> = labelFor(mode) to labelFor(source)

    private fun heading(activity: android.content.Context, text: Int): TextView =
        TextView(activity).apply {
            setText(text)
            textSize = HEADING_TEXT_SIZE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            updatePadding(
                top = resources.getDimensionPixelSize(R.dimen.spacing_vertical),
                bottom = resources.getDimensionPixelSize(R.dimen.spacing_group_vertical),
            )
        }

    /**
     * A group whose button ids are the enum's own ordinals, so reading the choice back needs no lookup table.
     *
     * `AppCompatRadioButton` rather than the platform one: only the AppCompat version tints itself from
     * `colorControlActivated`, and a platform radio here would be the one control in the app still painted in
     * Material's baseline purple.
     */
    private fun radioGroup(context: android.content.Context, labels: List<String>, checked: Int): RadioGroup =
        RadioGroup(context).apply {
            labels.forEachIndexed { index, label ->
                addView(
                    AppCompatRadioButton(context).apply {
                        id = index
                        text = label
                        minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_minimum)
                        layoutParams =
                            RadioGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                    }
                )
            }
            check(checked.coerceAtLeast(0))
        }

    private fun labelFor(mode: ThemeMode): Int =
        when (mode) {
            ThemeMode.SYSTEM -> R.string.appearance_mode_system
            ThemeMode.LIGHT -> R.string.appearance_mode_light
            ThemeMode.DARK -> R.string.appearance_mode_dark
            ThemeMode.AMOLED -> R.string.appearance_mode_amoled
        }

    private fun labelFor(source: ThemeSeedSource): Int =
        when (source) {
            ThemeSeedSource.SERVER -> R.string.appearance_source_server
            ThemeSeedSource.CUSTOM -> R.string.appearance_source_custom
            ThemeSeedSource.WALLPAPER -> R.string.appearance_source_wallpaper
        }

    private const val HEADING_TEXT_SIZE = 14f
}
