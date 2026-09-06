package com.dominikdomotor.nextcloudpasswords.fragments.settings

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.autofill.AutofillLinkStore
import com.dominikdomotor.nextcloudpasswords.autofill.AutofillTarget
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import com.dominikdomotor.nextcloudpasswords.ui.ListGroupBackground
import com.dominikdomotor.nextcloudpasswords.ui.asGroupedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows every site and app the autofill service has learned something about, and lets each one be forgotten.
 *
 * Autofill picks up these associations quietly, as a side effect of the user searching the picker and choosing an
 * entry, so there has to be somewhere that says what it has learned and undoes any of it. Without that the app would
 * be keeping a list of the sites someone visits with no way to look at it or clear it.
 */
object AutofillRememberedDialog {
    /**
     * Turns the stored keys into rows, resolving names and icons off the main thread.
     *
     * The package manager lookup and [favicon], which decrypts and decodes a file per row, are both too slow for the
     * main thread. An app that has since been uninstalled keeps its package name as its label rather than dropping out
     * of the list: the user still needs to be able to clear it.
     */
    suspend fun load(
        context: Context,
        links: Map<String, AutofillLinkStore.Link>,
        passwords: List<Password>,
        favicon: (String) -> Bitmap?,
    ): List<Remembered> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val labelsById = passwords.associate { it.id to it.label }
            val fallbackIcon = ContextCompat.getDrawable(context, R.drawable.icon_foreground_24)

            links.entries
                .map { (key, link) ->
                    val packageName = AutofillTarget.packageOf(key)
                    val info = packageName?.let { applicationInfo(packageManager, it) }
                    Remembered(
                        key = key,
                        target = info?.loadLabel(packageManager)?.toString() ?: AutofillTarget.hostOf(key) ?: key,
                        entryLabel = labelsById[link.passwordId],
                        query = link.query,
                        // An app is best recognised by its launcher icon, a site by the favicon of the entry
                        // chosen for it, which is the same picture the suggestion and the password list show.
                        icon =
                            info?.loadIcon(packageManager)
                                ?: favicon(link.passwordId)?.toDrawable(context.resources)
                                ?: fallbackIcon,
                    )
                }
                .sortedBy { it.target.lowercase() }
        }

    private fun applicationInfo(packageManager: PackageManager, packageName: String) =
        runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()

    /**
     * [onSave] receives the keys that survived, in the same shape [AutofillLinkStore.retainOnly] wants.
     *
     * Removals are collected and applied on save rather than as they are tapped, so a mis-tap costs a cancel rather
     * than a trip back to every site to teach it again.
     */
    fun show(activity: Activity, remembered: List<Remembered>, onSave: (Set<String>) -> Unit) {
        val dialog = AppDialog(activity)
        if (remembered.isEmpty()) {
            dialog
                .title(R.string.autofill_remembered)
                .message(R.string.autofill_remembered_empty)
                .button(R.string.close)
                .showCompact()
            return
        }

        val kept = remembered.toMutableList()
        val list = ListView(dialog.context)
        list.adapter = Adapter(kept, activity)
        list.asGroupedList()

        dialog
            .title(R.string.autofill_remembered)
            .content(list)
            .button(R.string.cancel, destructive = true)
            .button(R.string.save) { onSave(kept.mapTo(mutableSetOf()) { it.key }) }
            .show()
    }

    data class Remembered(
        val key: String,
        val target: String,
        val entryLabel: String?,
        val query: String,
        val icon: Drawable?,
    )

    private class Adapter(private val items: MutableList<Remembered>, private val context: Context) : BaseAdapter() {
        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Remembered = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view =
                convertView
                    ?: LayoutInflater.from(parent.context).inflate(R.layout.autofill_remembered_item, parent, false)
            val item = getItem(position)
            view.findViewById<ImageView>(R.id.remembered_icon).setImageDrawable(item.icon)
            view.findViewById<TextView>(R.id.remembered_target).text = item.target
            view.findViewById<TextView>(R.id.remembered_detail).text = detailOf(item)

            ListGroupBackground.apply(view, position, count)

            view.findViewById<ImageButton>(R.id.remembered_forget).setOnClickListener {
                items.removeAt(position)
                notifyDataSetChanged()
            }
            return view
        }

        /** The entry that will be offered, and the words that will be typed for the user, in that order. */
        private fun detailOf(item: Remembered): String {
            val entry = item.entryLabel ?: context.getString(R.string.autofill_remembered_missing_entry)
            if (item.query.isBlank()) return entry
            return context.getString(R.string.autofill_remembered_detail, entry, item.query)
        }
    }
}
