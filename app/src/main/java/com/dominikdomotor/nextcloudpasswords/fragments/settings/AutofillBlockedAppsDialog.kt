package com.dominikdomotor.nextcloudpasswords.fragments.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import com.dominikdomotor.nextcloudpasswords.ui.ListGroupBackground
import com.dominikdomotor.nextcloudpasswords.ui.asGroupedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lets the user pick apps the autofill service should stay out of. */
object AutofillBlockedAppsDialog {
    /** Loads the launchable apps off the main thread; the package manager query is slow. */
    suspend fun loadApps(context: Context, alreadyBlocked: Set<String>): List<BlockedApp> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val launchable =
                packageManager.queryIntentActivities(launcherIntent, 0).associateBy { it.activityInfo.packageName }

            (launchable.keys + alreadyBlocked)
                .asSequence()
                .filterNot { it == context.packageName }
                .distinct()
                .map { packageName ->
                    val info = launchable[packageName]
                    BlockedApp(
                        packageName = packageName,
                        label = info?.loadLabel(packageManager)?.toString() ?: packageName,
                        icon = info?.loadIcon(packageManager),
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        }

    fun show(activity: Activity, apps: List<BlockedApp>, blocked: Set<String>, onSave: (Set<String>) -> Unit) {
        val selected = blocked.toMutableSet()
        val dialog = AppDialog(activity)

        val list =
            ListView(dialog.context).apply {
                adapter = Adapter(apps, selected)
                asGroupedList()
            }

        dialog
            .title(R.string.autofill_blocked_apps)
            .content(list)
            .button(R.string.cancel, destructive = true)
            .button(R.string.save) { onSave(selected) }
            .show()
    }

    data class BlockedApp(val packageName: String, val label: String, val icon: Drawable?)

    private class Adapter(private val apps: List<BlockedApp>, private val selected: MutableSet<String>) :
        BaseAdapter() {
        override fun getCount(): Int = apps.size

        override fun getItem(position: Int): BlockedApp = apps[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view =
                convertView
                    ?: LayoutInflater.from(parent.context).inflate(R.layout.autofill_blocked_app_item, parent, false)
            val app = getItem(position)
            view.findViewById<ImageView>(R.id.blocked_app_icon).setImageDrawable(app.icon)
            view.findViewById<TextView>(R.id.blocked_app_label).text = app.label
            view.findViewById<TextView>(R.id.blocked_app_package).text = app.packageName

            ListGroupBackground.apply(view, position, count)

            val checkbox = view.findViewById<CheckBox>(R.id.blocked_app_checkbox)
            // Detach before setting state, or recycling a row would toggle the wrong package.
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = app.packageName in selected
            checkbox.setOnCheckedChangeListener { _, checked ->
                if (checked) selected += app.packageName else selected -= app.packageName
            }
            view.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
            return view
        }
    }
}
