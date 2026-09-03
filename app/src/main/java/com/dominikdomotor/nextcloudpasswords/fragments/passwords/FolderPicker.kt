package com.dominikdomotor.nextcloudpasswords.fragments.passwords

import android.annotation.SuppressLint
import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.data.FolderTree
import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import com.dominikdomotor.nextcloudpasswords.ui.ListGroupBackground
import com.dominikdomotor.nextcloudpasswords.ui.asGroupedList

/** Modal folder browser used to choose a password's folder or a folder's parent. */
class FolderPicker(private val activity: Activity, private val folders: () -> List<Folder>) {
    /** The full path of [folderId], e.g. `Home / Work / Servers`. */
    fun folderName(folderId: String): String = path(folderId, includeRoot = false)

    /**
     * @param excluded folders that may not be chosen — for a move, the folder itself and all of its descendants, which
     *   is what stops a folder being moved underneath its own child
     */
    /**
     * Inflated with a null root on purpose: the dialog is the parent, and it does not exist until this content is
     * handed to it.
     */
    @SuppressLint("InflateParams")
    fun show(excluded: Set<String> = emptySet(), onSelected: (id: String, name: String) -> Unit) {
        val allFolders = folders()
        var currentFolderId = Folder.ROOT_ID

        val dialog = AppDialog(activity)
        val view = LayoutInflater.from(dialog.context).inflate(R.layout.folder_picker_dialog, null)
        val list = view.findViewById<RecyclerView>(R.id.rv_folders)
        val pathLabel = view.findViewById<TextView>(R.id.tv_current_path)

        lateinit var render: () -> Unit
        val adapter = FolderPickerAdapter { folder ->
            currentFolderId = folder.id
            render()
        }
        list.isVerticalScrollBarEnabled = false
        list.layoutManager = LinearLayoutManager(activity)
        list.adapter = adapter
        list.asGroupedList()

        render = {
            pathLabel.text = path(currentFolderId, includeRoot = true)
            adapter.submitList(
                allFolders
                    .filter { FolderTree.isChildOf(it, currentFolderId) && it.id !in excluded }
                    .sortedBy { it.label.lowercase() }
            )
        }
        render()

        dialog
            .title(R.string.choose_folder)
            .content(view)
            // Back walks up a folder, matching the folder browser, and closes the picker at the root.
            .onBack {
                if (currentFolderId == Folder.ROOT_ID) return@onBack false
                val current = allFolders.firstOrNull { it.id == currentFolderId }
                currentFolderId = current?.parent?.takeIf { it.isNotBlank() && it != Folder.ROOT_ID } ?: Folder.ROOT_ID
                render()
                true
            }
            .button(R.string.cancel, destructive = true)
            .button(R.string.save) { onSelected(currentFolderId, folderName(currentFolderId)) }
            .show()
    }

    /**
     * Builds a folder's display path.
     *
     * `folderName` and `getFullPath` used to be two near-identical copies of this walk that differed only in whether
     * the root label was prefixed.
     */
    private fun path(folderId: String, includeRoot: Boolean): String {
        // The same name the folder browser's breadcrumb uses. The root is a place a password can sit,
        // not the absence of one, so calling it "No folder" made an entry in it look unfiled.
        val rootLabel = activity.getString(R.string.home)
        if (folderId == Folder.ROOT_ID) return rootLabel

        val segments = FolderTree.pathTo(folders(), folderId).map { it.label }.toMutableList()
        if (segments.isEmpty()) return rootLabel
        if (includeRoot) segments.add(0, rootLabel)
        return segments.joinToString(" / ")
    }

    private class FolderPickerAdapter(private val onClick: (Folder) -> Unit) :
        ListAdapter<Folder, FolderPickerAdapter.ViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.folder_picker_item, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val folder = getItem(position)
            holder.name.text = folder.label
            holder.icon.setImageResource(R.drawable.icon_folder_24)
            ListGroupBackground.apply(holder.itemView, position, itemCount)
            holder.itemView.setOnClickListener { onClick(folder) }
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_icon)
            val name: TextView = view.findViewById(R.id.tv_name)
        }

        private companion object {
            val DIFF =
                object : DiffUtil.ItemCallback<Folder>() {
                    override fun areItemsTheSame(oldItem: Folder, newItem: Folder) = oldItem.id == newItem.id

                    override fun areContentsTheSame(oldItem: Folder, newItem: Folder) = oldItem == newItem
                }
        }
    }

    private companion object {}
}
