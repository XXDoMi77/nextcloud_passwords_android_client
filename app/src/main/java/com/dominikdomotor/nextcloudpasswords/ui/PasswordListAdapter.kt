package com.dominikdomotor.nextcloudpasswords.ui

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.ui.theme.themeColor
import com.google.android.material.R as MaterialR

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

    private var recyclerView: RecyclerView? = null
    private var pendingHighlightId: String? = null
    private var highlightAnimator: ValueAnimator? = null
    private var highlightedHolder: ViewHolder? = null

    /** Rebinds just the row showing [passwordId], if it is currently in the list. */
    fun notifyFaviconChanged(passwordId: String) {
        val index = currentList.indexOfFirst { it.id == passwordId }
        if (index >= 0) notifyItemChanged(index)
    }

    /**
     * Flashes the row for [passwordId] once, to point at a password that was just created.
     *
     * A new password sorts by label, so its row is often not on screen — and not even bound — when this is called. The
     * id is remembered instead and the flash starts from [onBindViewHolder] as the row scrolls into view.
     */
    fun highlight(passwordId: String) {
        pendingHighlightId = passwordId
        playPendingHighlight()
    }

    /**
     * Starts a waiting flash if its row is on screen already.
     *
     * Scrolling to a row that RecyclerView had prefetched does not rebind it, so without this the flash would wait for
     * a bind that never comes.
     */
    fun playPendingHighlight() {
        val id = pendingHighlightId ?: return
        val index = currentList.indexOfFirst { it.id == id }
        val holder = if (index < 0) null else recyclerView?.findViewHolderForAdapterPosition(index)
        if (holder is ViewHolder && holder.itemView.getLocalVisibleRect(Rect())) {
            pendingHighlightId = null
            startHighlight(holder)
        }
    }

    /** Drops a running or waiting flash; the list fragment calls this when its view goes away. */
    fun cancelHighlight() {
        pendingHighlightId = null
        highlightAnimator?.cancel()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        cancelHighlight()
        this.recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.password_overview_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val password = getItem(position)
        // The holder may be this row on its way to showing a different password; that one must not inherit the flash.
        if (holder === highlightedHolder) cancelHighlight()
        holder.bind(password, favicons, onClick, onCopyUsername, onCopyPassword)
        if (password.id == pendingHighlightId) {
            pendingHighlightId = null
            startHighlight(holder)
        }
    }

    /**
     * One fade in and out of [MaterialR.attr.colorSecondaryContainer].
     *
     * It runs on the row's own overlay view rather than on its background, which carries the ripple and the row's
     * padding.
     */
    private fun startHighlight(holder: ViewHolder) {
        cancelHighlight()
        val tint = holder.itemView.context.themeColor(MaterialR.attr.colorSecondaryContainer)
        highlightedHolder = holder
        highlightAnimator =
            ValueAnimator.ofArgb(ColorUtils.setAlphaComponent(tint, 0), tint).apply {
                duration = HIGHLIGHT_FADE_MILLIS
                repeatMode = ValueAnimator.REVERSE
                repeatCount = 1
                addUpdateListener { holder.setHighlight(it.animatedValue as Int) }
                doOnEnd {
                    holder.setHighlight(Color.TRANSPARENT)
                    highlightedHolder = null
                    highlightAnimator = null
                }
                start()
            }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.textview_password_label)
        private val username: TextView = itemView.findViewById(R.id.textview_username)
        private val copyUsername: ImageButton = itemView.findViewById(R.id.imagebutton_copy_username)
        private val copyPassword: ImageButton = itemView.findViewById(R.id.imagebutton_copy_password)
        private val icon: ImageView = itemView.findViewById(R.id.imageview_password_listview_item)
        private val card: ConstraintLayout = itemView.findViewById(R.id.constraint_layout_cardview_password)
        private val highlight: View = itemView.findViewById(R.id.view_row_highlight)

        fun setHighlight(@ColorInt color: Int) = highlight.setBackgroundColor(color)

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
        const val HIGHLIGHT_FADE_MILLIS = 500L

        val DIFF =
            object : DiffUtil.ItemCallback<Password>() {
                override fun areItemsTheSame(oldItem: Password, newItem: Password) = oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: Password, newItem: Password) = oldItem == newItem
            }
    }
}
