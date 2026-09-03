package com.dominikdomotor.nextcloudpasswords.fragments.settings

import android.app.Activity
import android.view.LayoutInflater
import android.widget.EditText
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.autofill.HintWords
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog

/**
 * Editor for the words that field detection falls back on.
 *
 * Most forms carry an explicit autofill hint and never reach these lists, but plenty of sites and apps ship nothing
 * usable, and then a field is recognised only if its own label, id or description contains one of these words. Which
 * words those are depends on the language someone browses in, so the lists are editable rather than fixed.
 */
object AutofillHintWordsDialog {
    fun show(
        activity: Activity,
        usernameWords: List<String>,
        passwordWords: List<String>,
        onSave: (username: List<String>, password: List<String>) -> Unit,
    ) {
        val dialog = AppDialog(activity)
        val content = LayoutInflater.from(dialog.context).inflate(R.layout.autofill_hint_words_dialog, null, false)
        val usernameInput = content.findViewById<EditText>(R.id.autofillUsernameWords)
        val passwordInput = content.findViewById<EditText>(R.id.autofillPasswordWords)
        usernameInput.setText(HintWords.format(usernameWords))
        passwordInput.setText(HintWords.format(passwordWords))

        dialog
            .title(R.string.autofill_hint_words)
            .content(content)
            // Refills the fields rather than saving, so a mistaken tap can still be cancelled - which is
            // also why it does not dismiss.
            .button(R.string.reset_to_default, dismissOnClick = false) {
                usernameInput.setText(HintWords.format(HintWords.DEFAULT_USERNAME_WORDS))
                passwordInput.setText(HintWords.format(HintWords.DEFAULT_PASSWORD_WORDS))
            }
            .button(R.string.cancel, destructive = true)
            .button(R.string.save) {
                onSave(HintWords.parse(usernameInput.text.toString()), HintWords.parse(passwordInput.text.toString()))
            }
            .show()
    }
}
