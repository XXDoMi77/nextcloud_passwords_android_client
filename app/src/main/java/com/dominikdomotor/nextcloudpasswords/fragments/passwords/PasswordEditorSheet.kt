package com.dominikdomotor.nextcloudpasswords.fragments.passwords

import android.annotation.SuppressLint
import android.app.Activity
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.PasswordGenerator
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputLayout

/**
 * The "create password" bottom sheet.
 *
 * `PasswordsFragment` and `FoldersFragment` each carried their own ~120-line copy of this, plus identical copies of the
 * field-inflation and action-button helpers. The two had already drifted: only the folders copy honoured the
 * `expandBottomSheet` setting.
 */
class PasswordEditorSheet(
    private val activity: Activity,
    private val settings: Settings,
    private val folderPicker: FolderPicker,
) {
    /**
     * Inflated with a null root on purpose: the dialog is the parent, and it does not exist until this content is
     * handed to it.
     *
     * @param defaultFolderId folder pre-selected in the picker
     * @param onSave invoked with the new password; the sheet waits, frozen, until the outcome comes back
     */
    @SuppressLint("InflateParams")
    fun show(defaultFolderId: String = Folder.ROOT_ID, onSave: (Password, SaveOutcome) -> Unit) {
        val dialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.password_edit_bottom_sheet_dialog, null)
        val fields = view.findViewById<LinearLayout>(R.id.myLinearLayout)
        val root = view.findViewById<ViewGroup>(R.id.passwordEditPopup)

        view.findViewById<View>(R.id.tableRowButtons).visibility = View.GONE
        view.findViewById<View>(R.id.tableRowTitle).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.textViewTitle).setText(R.string.create_password)

        val saveButton =
            view.findViewById<ImageButton>(R.id.imageButtonSwoosh).apply {
                setImageResource(R.drawable.icon_check_24)
                contentDescription = activity.getString(R.string.save_password)
            }
        val closeButton =
            view.findViewById<ImageButton>(R.id.imageButtonCloseRow).apply {
                // "Cancel", not "Close": leaving here discards what was typed.
                contentDescription = activity.getString(R.string.cancel)
            }

        val label = addField(fields, root, R.string.label)
        val username = addField(fields, root, R.string.username)
        val password =
            addField(
                fields,
                root,
                R.string.password,
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                showAction = true,
                passwordToggle = true,
            )
        val url = addField(fields, root, R.string.url)
        val notes =
            addField(fields, root, R.string.notes, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                .apply { minLines = NOTES_MIN_LINES }
        val folder = addField(fields, root, R.string.folder)

        var selectedFolderId = defaultFolderId
        folder.isFocusable = false
        folder.setText(folderPicker.folderName(selectedFolderId))
        val pickFolder = {
            folderPicker.show { id, name ->
                selectedFolderId = id
                folder.setText(name)
            }
        }
        folder.setOnClickListener { pickFolder() }
        folder.inputLayout.apply {
            endIconMode = TextInputLayout.END_ICON_CUSTOM
            setEndIconDrawable(R.drawable.icon_folder_24)
            setEndIconContentDescription(R.string.choose_folder)
            setEndIconOnClickListener { pickFolder() }
        }

        password.actionButton.apply {
            setImageResource(R.drawable.icon_autorenew_24)
            contentDescription = activity.getString(R.string.generate_password)
            setOnClickListener { password.setText(PasswordGenerator.generate(settings)) }
        }

        dialog.setContentView(view)
        val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)!!
        sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        val behavior = BottomSheetBehavior.from(sheet)
        if (settings.expandBottomSheet) {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            behavior.peekHeight = (activity.resources.displayMetrics.heightPixels * COLLAPSED_FRACTION).toInt()
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        val inputs = listOf(label, username, password, url, notes)
        // The same switch the details sheet uses for its read-only state; disabling the EditText alone leaves the box
        // around it looking editable.
        val editors =
            (inputs + folder).map { PasswordDetailsController.EditorField(it.inputLayout, it, it.actionButton) }
        val setSaving = { saving: Boolean ->
            editors.forEach {
                it.setEditable(!saving)
                it.action.isEnabled = !saving
            }
            saveButton.isEnabled = !saving
        }
        val confirmAndClose = {
            if (inputs.none { it.text.isNotEmpty() }) {
                dialog.dismiss()
            } else {
                AppDialog(activity)
                    .message(R.string.are_you_sure)
                    .button(R.string.cancel) { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
                    .button(R.string.yes, destructive = true) { dialog.dismiss() }
                    .showCompact()
            }
        }

        closeButton.setOnClickListener { confirmAndClose() }
        // Back has to ask as well. The sheet is deliberately not cancelable, so without this the key press
        // was simply swallowed and a half-filled password looked impossible to leave.
        dialog.onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    confirmAndClose()
                }
            }
        )
        behavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                        confirmAndClose()
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            }
        )

        saveButton.setOnClickListener {
            val enteredUrl = url.text.toString().trim()
            when {
                label.text.isNullOrBlank() ->
                    label.error = activity.getString(R.string.label_is_required_for_password_creation)
                password.text.isNullOrBlank() ->
                    password.error = activity.getString(R.string.password_is_required_for_password_creation)
                enteredUrl.isNotEmpty() && !android.webkit.URLUtil.isValidUrl(enteredUrl) ->
                    url.error = activity.getString(R.string.not_a_valid_url_alert_message)
                else -> {
                    // A second tap would create the password twice, and a request with no visible effect looks like
                    // nothing happened at all.
                    setSaving(true)
                    onSave(
                        Password(
                            label = label.text.toString(),
                            username = username.text.toString(),
                            password = password.text.toString(),
                            url = enteredUrl,
                            notes = notes.text.toString(),
                            folder = selectedFolderId,
                        ),
                        SaveOutcome { created -> if (created) dialog.dismiss() else setSaving(false) },
                    )
                }
            }
        }

        dialog.setCancelable(false)
        dialog.show()
    }

    private fun addField(
        parent: LinearLayout,
        root: ViewGroup,
        labelResId: Int,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        showAction: Boolean = false,
        passwordToggle: Boolean = false,
    ): EditText {
        val item =
            activity.layoutInflater.inflate(R.layout.password_edit_bottom_sheet_dialog_property_item, root, false)
        item.findViewById<TextInputLayout>(R.id.property_input_layout).apply {
            setHint(labelResId)
            if (passwordToggle) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        item.findViewById<ImageButton>(R.id.imagebutton_right).visibility = if (showAction) View.VISIBLE else View.GONE
        parent.addView(item)
        return item.findViewById<EditText>(R.id.edittext_middle_left).apply { this.inputType = inputType }
    }

    /**
     * How the save went, reported back by whoever ran the request.
     *
     * The sheet freezes itself the moment Save is tapped, so it has to hear about a failure as well: a plain `dismiss:
     * () -> Unit` could only say "created", which left a failed attempt with disabled fields and no way to correct and
     * retry.
     */
    class SaveOutcome internal constructor(private val report: (created: Boolean) -> Unit) {
        /** The password was created; the sheet closes. */
        operator fun invoke() = report(true)

        /** The request failed; the fields come back so the user can correct and try again. */
        fun failed() = report(false)
    }

    private companion object {
        const val NOTES_MIN_LINES = 3
        const val COLLAPSED_FRACTION = 0.6
    }
}

/**
 * The action button belonging to this field's row.
 *
 * Replaces `findEditorAction`, which walked up the view hierarchy looking for the first ancestor containing the id and
 * threw [IllegalStateException] when it ran out of parents.
 */
internal val EditText.actionButton: ImageButton
    get() = row().findViewById(R.id.imagebutton_right)

/** The text box this field lives in, for the actions that belong inside it rather than beside it. */
internal val EditText.inputLayout: TextInputLayout
    get() = row().findViewById(R.id.property_input_layout)

private fun EditText.row(): View = parent.parentRow() ?: error("Editor field is not inside a property item row")

private tailrec fun Any?.parentRow(): View? =
    when {
        this !is View -> null
        findViewById<View?>(R.id.imagebutton_right) != null -> this
        else -> parent.parentRow()
    }
