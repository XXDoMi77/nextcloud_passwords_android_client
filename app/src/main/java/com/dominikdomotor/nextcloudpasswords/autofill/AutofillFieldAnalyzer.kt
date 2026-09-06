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

/**
 * Reads one view's own properties. What they add up to is [AutofillFormAnalyzer]'s job.
 *
 * The split matters: a field cannot be classified alone. An unlabelled text box is a username or a search term
 * depending entirely on what sits under it, so everything here stops at "what does this view say about itself" and the
 * decision happens once the whole form is known.
 */
internal object AutofillFieldAnalyzer {
    private val usernameHints = setOf("username", "emailaddress", "email", "login", "userid")
    private val passwordHints = setOf("password", "currentpassword", "newpassword")
    private val ignoredHints =
        setOf("otp", "onetimecode", "smsotp", "verificationcode", "2fa", "totp", "captcha", "pin")

    /** Views that accept typing. Anything else cannot be filled, whatever it is called. */
    private val editableClasses = setOf("android.widget.EditText", "android.widget.AutoCompleteTextView")

    /**
     * One view as a candidate, or null when it cannot be filled at all.
     *
     * [id] is the caller's traversal counter, and becomes the candidate's identity. [originX] and [originY] are the
     * parent's absolute position: `ViewNode.getLeft` is relative to the parent, so a raw left and top can only be
     * compared between siblings. The pairing here compares fields that routinely sit under different parents - inside
     * a browser, always - so the caller accumulates the offsets as it walks and passes the running total.
     */
    fun candidate(
        node: ViewNode,
        id: Int,
        words: AutofillHintWords,
        originX: Int,
        originY: Int,
    ): AutofillFormAnalyzer.Candidate? {
        if (node.autofillId == null) return null
        val type = node.autofillType
        if (type != View.AUTOFILL_TYPE_TEXT && type != View.AUTOFILL_TYPE_NONE) return null

        val html = node.htmlInfo
        val htmlAttributes = html?.attributes?.associate { it.first.lowercase() to it.second.orEmpty() }.orEmpty()

        val explicit =
            buildList {
                    node.autofillHints?.forEach(::add)
                    htmlAttributes["autocomplete"]?.split(' ')?.forEach(::add)
                }
                .map(::squash)

        // Names the field goes by, each kept separate: matching a word against the whole lot joined together is how
        // "search_username_bar" ends up looking like a username field.
        val names =
            listOfNotNull(
                node.hint,
                node.idEntry,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.hintIdEntry else null,
                node.textIdEntry,
                node.contentDescription?.toString(),
                htmlAttributes["label"],
                htmlAttributes["name"],
                htmlAttributes["id"],
                htmlAttributes["placeholder"],
                htmlAttributes["aria-label"],
            )
        val tokens = names.flatMapTo(hashSetOf(), ::tokenize)

        val declared =
            when {
                !node.isEnabled -> AutofillFieldType.IGNORE
                explicit.any { hint -> ignoredHints.any(hint::contains) } -> AutofillFieldType.IGNORE
                tokens.any { it in ignoredHints } -> AutofillFieldType.IGNORE
                explicit.any { hint -> passwordHints.any(hint::contains) } -> AutofillFieldType.PASSWORD
                explicit.any { hint -> usernameHints.any(hint::contains) } -> AutofillFieldType.USERNAME
                else -> null
            }

        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        return AutofillFormAnalyzer.Candidate(
            id = id,
            editable = node.className in editableClasses || html?.tag.equals("input", ignoreCase = true),
            focused = node.isFocused,
            declared = declared,
            passwordByType =
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
                    htmlAttributes["type"].equals("password", ignoreCase = true),
            usernameByType =
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                    htmlAttributes["type"].equals("email", ignoreCase = true),
            passwordByWord = words.password.any { it in tokens },
            usernameByWord = words.username.any { it in tokens },
            looksLikeSearch = tokens.any { it in searchWords },
            bounds =
                AutofillFormAnalyzer.Bounds(
                    left = originX + node.left,
                    top = originY + node.top,
                    right = originX + node.left + node.width,
                    bottom = originY + node.top + node.height,
                ),
        )
    }

    /** A field that says it searches is never a login field, whatever else it resembles. */
    private val searchWords = setOf("search", "suche", "suchen", "recherche", "buscar", "zoeken", "query")

    /**
     * A name broken into the words it is made of, plus the whole thing with the separators removed.
     *
     * Both are needed. The tokens stop "password" matching "passwordless", and the squashed form lets a word list
     * entry like "email" still match a hint written "E-Mail".
     */
    private fun tokenize(value: String): List<String> =
        value.lowercase().split(NON_WORD).filter(String::isNotEmpty) + squash(value)

    private fun squash(value: CharSequence): String = value.toString().lowercase().filter(Char::isLetterOrDigit)

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
}
