package com.dominikdomotor.nextcloudpasswords.fragments.folders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.ui.FaviconBinder
import com.dominikdomotor.nextcloudpasswords.ui.PasswordStatus

/** A row in the folder browser: either a subfolder or a password inside the current folder. */
sealed interface FolderListItem {
    data class FolderRow(val folder: Folder, val passwordCount: Int) : FolderListItem

    data class PasswordRow(val password: Password) : FolderListItem
}

/**
 * Renders the folder browser.
 *
 * Favicons travel on the item rather than in a field updated by `notifyDataSetChanged()`, so an arriving icon rebinds
 * only its own row.
 */
class FoldersRecyclerViewAdapter(
    private val favicons: FaviconBinder,
    private val onFolderOpen: (Folder) -> Unit,
    private val onFolderEdit: (Folder) -> Unit,
    private val onFolderDelete: (Folder) -> Unit,
    private val onPasswordClick: (Password) -> Unit,
    private val onCopyUsername: (Password) -> Unit,
    private val onCopyPassword: (Password) -> Unit,
) : ListAdapter<FolderListItem, RecyclerView.ViewHolder>(DIFF) {

    /** Rebinds just the row showing [passwordId], if it is currently in the list. */
    fun notifyFaviconChanged(passwordId: String) {
        val index = currentList.indexOfFirst { it is FolderListItem.PasswordRow && it.password.id == passwordId }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is FolderListItem.FolderRow -> VIEW_TYPE_FOLDER
            is FolderListItem.PasswordRow -> VIEW_TYPE_PASSWORD
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_FOLDER) {
            FolderViewHolder(inflater.inflate(R.layout.folder_item, parent, false))
        } else {
            PasswordViewHolder(inflater.inflate(R.layout.password_overview_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is FolderListItem.FolderRow -> (holder as FolderViewHolder).bind(item)
            is FolderListItem.PasswordRow -> (holder as PasswordViewHolder).bind(item)
        }
    }

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.folder_name)
        private val subtitle: TextView = view.findViewById(R.id.folder_password_count)
        private val edit: ImageButton = view.findViewById(R.id.edit_folder)
        private val delete: ImageButton = view.findViewById(R.id.delete_folder)

        fun bind(item: FolderListItem.FolderRow) {
            val folder = item.folder
            title.text = folder.label
            subtitle.text =
                itemView.resources.getQuantityString(R.plurals.password_count, item.passwordCount, item.passwordCount)
            edit.contentDescription = itemView.context.getString(R.string.edit_folder)
            delete.contentDescription = itemView.context.getString(R.string.delete_folder_confirmation, folder.label)
            itemView.setOnClickListener { onFolderOpen(folder) }
            edit.setOnClickListener { onFolderEdit(folder) }
            delete.setOnClickListener { onFolderDelete(folder) }
        }
    }

    inner class PasswordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.textview_password_label)
        private val username: TextView = view.findViewById(R.id.textview_username)
        private val copyUsername: ImageButton = view.findViewById(R.id.imagebutton_copy_username)
        private val copyPassword: ImageButton = view.findViewById(R.id.imagebutton_copy_password)
        private val icon: ImageView = view.findViewById(R.id.imageview_password_listview_item)

        fun bind(item: FolderListItem.PasswordRow) {
            val password = item.password
            label.text = password.label
            username.text = password.username
            favicons.bind(icon, password.id)

            val status = PasswordStatus.from(password.status)
            copyPassword.setColorFilter(ContextCompat.getColor(itemView.context, status.colorResId))
            copyUsername.contentDescription = itemView.context.getString(R.string.copy_username_for, password.label)
            copyPassword.contentDescription = itemView.context.getString(R.string.copy_password_for, password.label)

            copyUsername.setOnClickListener { onCopyUsername(password) }
            copyPassword.setOnClickListener { onCopyPassword(password) }
            itemView.setOnClickListener { onPasswordClick(password) }
        }
    }

    private companion object {
        const val VIEW_TYPE_FOLDER = 0
        const val VIEW_TYPE_PASSWORD = 1

        val DIFF =
            object : DiffUtil.ItemCallback<FolderListItem>() {
                override fun areItemsTheSame(oldItem: FolderListItem, newItem: FolderListItem): Boolean =
                    when {
                        oldItem is FolderListItem.FolderRow && newItem is FolderListItem.FolderRow ->
                            oldItem.folder.id == newItem.folder.id
                        oldItem is FolderListItem.PasswordRow && newItem is FolderListItem.PasswordRow ->
                            oldItem.password.id == newItem.password.id
                        else -> false
                    }

                override fun areContentsTheSame(oldItem: FolderListItem, newItem: FolderListItem): Boolean =
                    oldItem == newItem
            }
    }
}
