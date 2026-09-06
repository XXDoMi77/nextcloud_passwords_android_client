package com.dominikdomotor.nextcloudpasswords.autofill

import android.app.assist.AssistStructure.ViewNode
import android.os.Build
import android.text.InputType
import android.view.View

internal enum class AutofillFieldType {
    USERNAME,
    PASSWORD,
    UNKNOWN,
    IGNORE,
}

internal object AutofillFieldAnalyzer {
    private val usernameHints = setOf("username", "emailaddress", "email", "login", "userid")
    private val passwordHints = setOf("password", "currentpassword", "newpassword")
    private val ignoredHints =
        setOf("otp", "onetimecode", "smsotp", "verificationcode", "2fa", "totp", "captcha", "pin")

    fun classify(node: ViewNode, words: AutofillHintWords): AutofillFieldType {
        if (node.autofillId == null || !node.isEnabled) {
            return AutofillFieldType.IGNORE
        }
        val type = node.autofillType
        if (type != View.AUTOFILL_TYPE_TEXT && type != View.AUTOFILL_TYPE_NONE) {
            return AutofillFieldType.IGNORE
        }

        val htmlAttributesText = node.htmlInfo?.attributes?.joinToString(" ") { it.second } ?: ""

        val explicitHints =
            buildList {
                    node.autofillHints?.forEach(::add)
                    node.htmlInfo
                        ?.attributes
                        ?.firstOrNull { it.first.equals("autocomplete", ignoreCase = true) }
                        ?.second
                        ?.split(' ')
                        ?.forEach(::add)
                }
                .map(::normalize)

        if (explicitHints.any { hint -> ignoredHints.any { hint.contains(it) } }) return AutofillFieldType.IGNORE
        if (explicitHints.any { hint -> passwordHints.any { hint.contains(it) } }) return AutofillFieldType.PASSWORD
        if (explicitHints.any { hint -> usernameHints.any { hint.contains(it) } }) return AutofillFieldType.USERNAME

        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) {
            return AutofillFieldType.PASSWORD
        }

        val hintIdEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.hintIdEntry else null
        val semanticText =
            listOfNotNull(
                    node.hint,
                    node.idEntry,
                    hintIdEntry,
                    node.textIdEntry,
                    node.contentDescription,
                    htmlAttributesText,
                )
                .joinToString(" ")
                .let(::normalize)
        if (ignoredHints.any(semanticText::contains)) return AutofillFieldType.IGNORE
        if (words.password.any(semanticText::contains)) {
            return AutofillFieldType.PASSWORD
        }
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || words.username.any(semanticText::contains)) {
            return AutofillFieldType.USERNAME
        }
        return AutofillFieldType.UNKNOWN
    }

    private fun normalize(value: CharSequence): String = value.toString().lowercase().filter(Char::isLetterOrDigit)
}
