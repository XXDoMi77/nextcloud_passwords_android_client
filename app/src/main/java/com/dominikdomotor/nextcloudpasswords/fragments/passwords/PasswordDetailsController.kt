package com.dominikdomotor.nextcloudpasswords.fragments.passwords

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.CseCryptoManager
import com.dominikdomotor.nextcloudpasswords.managers.PasswordGenerator
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageDuration
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import com.dominikdomotor.nextcloudpasswords.ui.PasswordStatus
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputLayout

/**
 * The password details / edit bottom sheet.
 *
 * [Password] is immutable, so edits build a copy and hand it to the view model; the previous version mutated the shared
 * instance in place before the server had accepted the change.
 */
class PasswordDetailsController(
    private val activity: Activity,
    private val actions: PasswordActionsViewModel,
    private val folderPicker: FolderPicker,
    private val uiMessageManager: UiMessageManager,
) {
    /**
     * Inflated with a null root on purpose: the dialog is the parent, and it does not exist until this content is
     * handed to it.
     */
    @SuppressLint("InflateParams")
    fun show(password: Password) {
        val dialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.password_edit_bottom_sheet_dialog, null)
        val fields = view.findViewById<LinearLayout>(R.id.myLinearLayout)

        view.findViewById<View>(R.id.tableRowButtons).visibility = View.VISIBLE
        view.findViewById<View>(R.id.tableRowTitle).visibility = View.GONE
        view.findViewById<ImageButton>(R.id.imageButtonClose).apply {
            contentDescription = activity.getString(R.string.close)
            setOnClickListener { dialog.dismiss() }
        }

        val label = addField(fields, R.string.name, password.label, copyLabel = "name")
        val username = addField(fields, R.string.username, password.username, copyLabel = "username")
        val secret = addField(fields, R.string.password, password.password, copyLabel = "password")
        val url = addField(fields, R.string.url, password.url, copyLabel = null)
        val notes =
            addField(
                    fields,
                    R.string.notes,
                    password.notes,
                    copyLabel = null,
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                )
                .also { it.input.minLines = NOTES_MIN_LINES }

        configureUrlAction(url)

        // Held in one place so the folder picker knows whether it should PATCH immediately.
        var editing = false
        var selectedFolderId = password.folder

        val folderField = addField(fields, R.string.folder, folderPicker.folderName(password.folder), copyLabel = null)
        // Live even while the rest of the card is read-only, because picking a folder is its own request
        // rather than part of an edit. Not focusable, so a tap opens the picker instead of the keyboard.
        folderField.setEditable(true)
        folderField.input.isFocusable = false
        val pickFolder = {
            folderPicker.show(excluded = emptySet()) { id, name ->
                selectedFolderId = id
                folderField.input.setText(name)
                if (!editing) actions.update(password.copy(folder = id))
            }
        }
        folderField.input.setOnClickListener { pickFolder() }
        // Inside the box rather than beside it, matching the folder editor's field. The row's own action
        // button is for things that act on the value - copy, open - and the picker is not one of those.
        folderField.layout.apply {
            endIconMode = TextInputLayout.END_ICON_CUSTOM
            setEndIconDrawable(R.drawable.icon_folder_24)
            setEndIconContentDescription(R.string.choose_folder)
            setEndIconOnClickListener { pickFolder() }
        }

        val editable = listOf(label, username, secret, url, notes)
        configureEditing(view, password, editable, { editing = it }, { selectedFolderId })
        configureStatus(view, password)
        configureFavorite(view, password)
        configureDelete(view, dialog, password)

        if (password.cseType != CseCryptoManager.CSE_TYPE) {
            PasswordShareController(activity, actions, uiMessageManager).addSection(password, fields)
        }

        dialog.setContentView(view)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }

    private fun configureUrlAction(url: EditorField) {
        url.action.apply {
            visibility = View.VISIBLE
            setImageResource(R.drawable.icon_open_in_new_24)
            contentDescription = activity.getString(R.string.open_url)
            setOnClickListener {
                val value = url.input.text.toString().trim()
                if (value.isEmpty()) return@setOnClickListener
                val address = if (value.toUri().scheme == null) "https://$value" else value
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, address.toUri()))
                } catch (_: ActivityNotFoundException) {
                    uiMessageManager.show(R.string.something_went_wrong_try_again)
                }
            }
            setOnLongClickListener {
                val value = url.input.text.toString().trim()
                if (value.isEmpty()) false else true.also { actions.copy(value, "url") }
            }
        }
    }

    private fun addField(
        container: LinearLayout,
        labelResId: Int,
        value: String,
        copyLabel: String?,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
    ): EditorField {
        val item =
            activity.layoutInflater.inflate(R.layout.password_edit_bottom_sheet_dialog_property_item, container, false)
        val inputLayout = item.findViewById<TextInputLayout>(R.id.property_input_layout).apply { setHint(labelResId) }
        val input =
            item.findViewById<EditText>(R.id.edittext_middle_left).apply {
                setText(value)
                this.inputType = inputType
            }
        val action =
            item.findViewById<ImageButton>(R.id.imagebutton_right).apply {
                if (copyLabel == null) {
                    visibility = View.GONE
                } else {
                    // Naming the field matters here: several of these buttons sit one under another.
                    contentDescription = activity.getString(R.string.copy_named_value, activity.getString(labelResId))
                    setOnClickListener { actions.copy(input.text.toString(), copyLabel) }
                }
            }
        container.addView(item)
        // The card opens read-only, and the initial state goes through the same setter the pencil uses.
        // Disabling only the EditText left the box around it looking editable, which only showed once the
        // outline started following the enabled state.
        return EditorField(inputLayout, input, action).also { it.setEditable(false) }
    }

    private fun configureEditing(
        view: View,
        password: Password,
        fields: List<EditorField>,
        onEditingChanged: (Boolean) -> Unit,
        selectedFolderId: () -> String,
    ) {
        val button = view.findViewById<ImageButton>(R.id.imageButtonPencil)
        button.contentDescription = activity.getString(R.string.edit_password)

        if (!password.editable) {
            button.setColorFilter(color(R.color.password_status_insecure))
            button.setOnClickListener {
                uiMessageManager.show(R.string.this_password_is_not_editable, UiMessageDuration.LONG)
            }
            return
        }

        val (label, username, secret, url, notes) = fields
        var editing = false
        button.setColorFilter(color(R.color.password_status_secure))
        button.setOnClickListener {
            editing = !editing
            onEditingChanged(editing)
            fields.forEach { it.setEditable(editing) }
            secret.action.apply {
                setImageResource(if (editing) R.drawable.icon_autorenew_24 else R.drawable.icon_copy_24)
                contentDescription =
                    activity.getString(if (editing) R.string.generate_password else R.string.copy_value)
                setOnClickListener {
                    if (editing) secret.input.setText(PasswordGenerator.generate(actions.settings))
                    else actions.copy(secret.input.text.toString(), "password")
                }
            }
            button.setImageResource(if (editing) R.drawable.icon_save_24 else R.drawable.icon_edit_24)

            if (!editing) {
                actions.update(
                    password.copy(
                        label = label.input.text.toString(),
                        username = username.input.text.toString(),
                        password = secret.input.text.toString(),
                        url = url.input.text.toString(),
                        notes = notes.input.text.toString(),
                        folder = selectedFolderId(),
                    )
                )
            }
        }
    }

    private fun configureStatus(view: View, password: Password) {
        val status = PasswordStatus.from(password.status)
        view.findViewById<ImageButton>(R.id.imageButtonShield).apply {
            contentDescription = activity.getString(R.string.security_status)
            setColorFilter(color(status.colorResId))
            setOnClickListener { uiMessageManager.show(status.explanationResId, UiMessageDuration.LONG) }
        }
    }

    private fun configureFavorite(view: View, password: Password) {
        view.findViewById<ImageButton>(R.id.imageButtonStar).apply {
            contentDescription = activity.getString(R.string.toggle_favorite)
            setColorFilter(color(R.color.favorite_star))
            var favorite = password.favorite
            val render = {
                setImageResource(if (favorite) R.drawable.icon_star_filled_24 else R.drawable.icon_star_outline_24)
            }
            render()
            setOnClickListener {
                favorite = !favorite
                render()
                actions.update(password.copy(favorite = favorite))
            }
        }
    }

    private fun configureDelete(view: View, dialog: BottomSheetDialog, password: Password) {
        view.findViewById<ImageButton>(R.id.imageButtonTrashcan).apply {
            contentDescription = activity.getString(R.string.delete_password)
            setOnClickListener {
                AppDialog(activity)
                    .message(R.string.are_you_sure)
                    .button(R.string.cancel)
                    .button(R.string.yes, destructive = true) {
                        dialog.dismiss()
                        actions.delete(password)
                    }
                    .showCompact()
            }
        }
    }

    private fun color(resId: Int) = ContextCompat.getColor(activity, resId)

    data class EditorField(val layout: TextInputLayout, val input: EditText, val action: ImageButton) {
        fun setEditable(editable: Boolean) {
            input.isEnabled = editable
            layout.isEnabled = editable
        }
    }

    private companion object {
        const val NOTES_MIN_LINES = 3
    }
}

private operator fun <T> List<T>.component4(): T = this[3]

private operator fun <T> List<T>.component5(): T = this[4]
