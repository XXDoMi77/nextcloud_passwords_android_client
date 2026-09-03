package com.dominikdomotor.nextcloudpasswords.fragments.passwords

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.CseCryptoManager
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageDuration
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import com.dominikdomotor.nextcloudpasswords.ui.ListGroupBackground
import com.dominikdomotor.nextcloudpasswords.ui.asGroupedList

/** The "shared with" section of the details sheet, plus the recipient picker. */
class PasswordShareController(
    private val activity: Activity,
    private val actions: PasswordActionsViewModel,
    private val uiMessageManager: UiMessageManager,
) {
    private val recipients = linkedMapOf<String, ShareRecipient>()
    private var shareContainer: LinearLayout? = null
    private var displayedPassword: Password? = null

    fun addSection(password: Password, container: LinearLayout) {
        displayedPassword = password
        activity.layoutInflater.inflate(R.layout.password_shared_with_section, container, false).also { section ->
            shareContainer = section.findViewById(R.id.shared_with_list)
            section.findViewById<View>(R.id.add_share_recipient).setOnClickListener { showRecipientPicker(password) }
            container.addView(section)
        }
        renderExistingShares()
        actions.searchRecipients("") { merge(it) }
    }

    private fun showRecipientPicker(password: Password) {
        if (password.cseType == CseCryptoManager.CSE_TYPE) {
            uiMessageManager.show(R.string.e2e_passwords_cannot_be_shared, UiMessageDuration.LONG)
            return
        }

        val dialog = AppDialog(activity)
        val content = LayoutInflater.from(dialog.context).inflate(R.layout.password_share_dialog, null)
        val input = content.findViewById<EditText>(R.id.share_recipient_search)
        val list = content.findViewById<ListView>(R.id.share_recipient_list)
        val progress = content.findViewById<ProgressBar>(R.id.share_recipient_progress)
        val empty = content.findViewById<TextView>(R.id.share_recipient_empty)
        // Each suggestion draws the grouped container, so a run of them reads as one block.
        val suggestions =
            object : ArrayAdapter<ShareRecipient>(dialog.context, R.layout.share_recipient_item) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getView(position, convertView, parent).also { ListGroupBackground.apply(it, position, count) }
            }
        val handler = Handler(Looper.getMainLooper())
        var pendingSearch: Runnable? = null

        list.isVerticalScrollBarEnabled = false
        list.adapter = suggestions
        list.asGroupedList()

        fun showMatches(query: String) {
            val alreadyShared = actions.sharesFor(password.id).mapTo(hashSetOf()) { it.receiver.id }
            val matches =
                recipients.values
                    .filterNot { it.id in alreadyShared }
                    .filter {
                        query.isBlank() ||
                            it.name.contains(query, ignoreCase = true) ||
                            it.id.contains(query, ignoreCase = true)
                    }
                    .sortedBy { it.name.lowercase() }
            suggestions.clear()
            suggestions.addAll(matches)
            suggestions.notifyDataSetChanged()
            empty.visibility = if (matches.isEmpty() && progress.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        }

        showMatches("")
        progress.visibility = if (recipients.isEmpty()) View.VISIBLE else View.GONE

        input.doAfterTextChanged { text ->
            pendingSearch?.let(handler::removeCallbacks)
            val query = text?.toString()?.trim().orEmpty()
            showMatches(query)
            if (query.length < MIN_SEARCH_LENGTH) return@doAfterTextChanged
            pendingSearch = Runnable {
                progress.visibility = View.VISIBLE
                empty.visibility = View.GONE
                actions.searchRecipients(query) { serverRecipients ->
                    if (input.text.toString().trim() != query) return@searchRecipients
                    merge(serverRecipients)
                    progress.visibility = View.GONE
                    showMatches(query)
                }
            }
            handler.postDelayed(pendingSearch!!, SEARCH_DEBOUNCE_MILLIS)
        }

        list.setOnItemClickListener { _, _, position, _ ->
            val recipient = suggestions.getItem(position) ?: return@setOnItemClickListener
            AppDialog(activity)
                .message(activity.getString(R.string.share_password_with, recipient.name))
                .button(R.string.cancel)
                .button(R.string.share) {
                    actions.createShare(password, recipient.id) {
                        dialog.dismiss()
                        renderExistingShares()
                    }
                }
                .showCompact()
        }

        dialog.onDismiss { pendingSearch?.let(handler::removeCallbacks) }
        dialog.title(R.string.share_with).content(content).button(R.string.cancel, destructive = true).show()

        actions.searchRecipients("") { serverRecipients ->
            if (!dialog.isShowing) return@searchRecipients
            merge(serverRecipients)
            progress.visibility = View.GONE
            showMatches(input.text?.toString()?.trim().orEmpty())
        }
    }

    private fun merge(serverRecipients: Map<String, String>) {
        serverRecipients.forEach { (id, name) -> recipients[id] = ShareRecipient(id, name) }
    }

    private fun renderExistingShares() {
        val password = displayedPassword ?: return
        val container = shareContainer ?: return
        container.removeAllViews()

        actions.sharesFor(password.id).forEach { share ->
            val shareView =
                activity.layoutInflater.inflate(R.layout.password_edit_bottom_sheet_dialog_share_item, container, false)
            shareView.findViewById<TextView>(R.id.share_name).text = share.receiver.name
            configurePermissionButton(shareView.findViewById(R.id.allow_edit), share.editable, R.string.allow_editing) {
                actions.updateShare(share.id, !share.editable, share.shareable) { renderExistingShares() }
            }
            configurePermissionButton(
                shareView.findViewById(R.id.allow_reshare),
                share.shareable,
                R.string.allow_resharing,
            ) {
                actions.updateShare(share.id, share.editable, !share.shareable) { renderExistingShares() }
            }
            shareView.findViewById<ImageButton>(R.id.delete_share).apply {
                contentDescription = activity.getString(R.string.revoke_share_with, share.receiver.name)
                setOnClickListener {
                    AppDialog(activity)
                        .message(activity.getString(R.string.revoke_share_with, share.receiver.name))
                        .button(R.string.cancel)
                        .button(R.string.yes, destructive = true) {
                            actions.revokeShare(share.id) { renderExistingShares() }
                        }
                        .showCompact()
                }
            }
            container.addView(shareView)
        }
    }

    private fun configurePermissionButton(
        button: ImageButton,
        enabled: Boolean,
        descriptionResId: Int,
        onClick: () -> Unit,
    ) {
        val description = activity.getString(descriptionResId)
        button.contentDescription = description
        TooltipCompat.setTooltipText(button, description)
        button.alpha = if (enabled) 1f else DISABLED_ALPHA
        button.setColorFilter(
            ContextCompat.getColor(activity, if (enabled) R.color.password_status_secure else R.color.normal_text_color)
        )
        button.setOnClickListener { onClick() }
    }

    private data class ShareRecipient(val id: String, val name: String) {
        override fun toString(): String = name
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L
        const val MIN_SEARCH_LENGTH = 2
        const val DISABLED_ALPHA = 0.45f
    }
}
