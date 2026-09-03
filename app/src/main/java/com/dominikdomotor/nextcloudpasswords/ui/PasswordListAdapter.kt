package com.dominikdomotor.nextcloudpasswords.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password

/**
 * Renders the password list.
 *
 * Favicons are fetched per row through [FaviconBinder] as rows come into view, rather than decoded up front. Use
 * [notifyFaviconChanged] when one arrives so only that row rebinds; the original adapter called
 * `notifyDataSetChanged()` for every single downloaded favicon.
 */
class PasswordListAdapter(
    private val favicons: FaviconBinder,
    private val onClick: (Password) -> Unit,
    private val onCopyUsername: (Password) -> Unit,
    private val onCopyPassword: (Password) -> Unit,
) : ListAdapter<Password, PasswordListAdapter.ViewHolder>(DIFF) {

    /** Rebinds just the row showing [passwordId], if it is currently in the list. */
    fun notifyFaviconChanged(passwordId: String) {
        val index = currentList.indexOfFirst { it.id == passwordId }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.password_overview_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), favicons, onClick, onCopyUsername, onCopyPassword)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.textview_password_label)
        private val username: TextView = itemView.findViewById(R.id.textview_username)
        private val copyUsername: ImageButton = itemView.findViewById(R.id.imagebutton_copy_username)
        private val copyPassword: ImageButton = itemView.findViewById(R.id.imagebutton_copy_password)
        private val icon: ImageView = itemView.findViewById(R.id.imageview_password_listview_item)
        private val card: ConstraintLayout = itemView.findViewById(R.id.constraint_layout_cardview_password)

        fun bind(
            password: Password,
            favicons: FaviconBinder,
            onClick: (Password) -> Unit,
            onCopyUsername: (Password) -> Unit,
            onCopyPassword: (Password) -> Unit,
        ) {
            label.text = password.label
            username.text = password.username
            favicons.bind(icon, password.id)

            val status = PasswordStatus.from(password.status)
            copyPassword.setColorFilter(ContextCompat.getColor(itemView.context, status.colorResId))
            copyPassword.contentDescription = itemView.context.getString(R.string.copy_password_for, password.label)
            copyUsername.contentDescription = itemView.context.getString(R.string.copy_username_for, password.label)

            copyUsername.setOnClickListener { onCopyUsername(password) }
            copyPassword.setOnClickListener { onCopyPassword(password) }
            card.setOnClickListener { onClick(password) }
        }
    }

    private companion object {
        val DIFF =
            object : DiffUtil.ItemCallback<Password>() {
                override fun areItemsTheSame(oldItem: Password, newItem: Password) = oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: Password, newItem: Password) = oldItem == newItem
            }
    }
}
