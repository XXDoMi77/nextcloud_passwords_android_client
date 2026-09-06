package com.dominikdomotor.nextcloudpasswords.autofill

import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.managers.EncryptedFileManager
import com.dominikdomotor.nextcloudpasswords.managers.Keys
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * What the user did by hand the last time this site or app came up.
 *
 * Matching a credential to a login form is guesswork: an entry can be labelled anything and its url can be missing or
 * point somewhere else entirely. When the guess comes up short the user opens "Search all passwords", types something
 * to narrow the list, and picks. Both halves of that are worth keeping. The entry goes to the top of the suggestions
 * next time, and the words they typed become what the picker opens with, so a vault where nothing is named after the
 * site it belongs to still only has to be searched once per site.
 *
 * Kept in its own file rather than in the settings inside the stored document, and that is not an arbitrary choice.
 * The picker runs in the autofill process; the document is written whole by whichever process writes last, and the
 * main process writes its own in-memory copy on every sync. A link added over here would therefore survive only until
 * the next sync over there. A separate file has one writer and cannot be clobbered. It goes with everything else when
 * the user signs out, because [EncryptedFileManager.deleteAllFiles] clears the whole directory.
 */
@Singleton
class AutofillLinkStore @Inject constructor(private val encryptedFileManager: EncryptedFileManager) {

    /** One remembered target. Either half can be empty: a user can pick without typing, or type without picking. */
    data class Link(
        @SerializedName("passwordId") val passwordId: String = "",
        @SerializedName("query") val query: String = "",
    )

    /** What was remembered for [targetKey], or null when nothing was. */
    fun linkFor(targetKey: String?): Link? = targetKey?.let { read()[it] }

    /** Everything remembered, for the settings screen that lists and clears it. */
    fun all(): Map<String, Link> = read()

    /**
     * Records [passwordId] and [query] as what the user did for this target.
     *
     * [knownPasswordIds] is the vault as the caller sees it, used to drop links to entries that have since been
     * deleted. It is ignored when empty, because "no passwords" is far more likely to mean the caller had not loaded
     * any than that the user deleted all of them, and acting on it would throw away every link.
     */
    fun remember(targetKey: String?, passwordId: String, query: String, knownPasswordIds: Set<String> = emptySet()) {
        if (targetKey == null || passwordId.isBlank()) return
        val links = read()
        if (knownPasswordIds.isNotEmpty()) links.entries.retainAll { it.value.passwordId in knownPasswordIds }
        // Removed before being put back so it lands at the end. Insertion order is what makes the cap below drop the
        // target the user has gone longest without choosing for, rather than an arbitrary one.
        links.remove(targetKey)
        links[targetKey] = Link(passwordId, query.trim())
        while (links.size > MAX_LINKS) links.remove(links.keys.first())
        write(links)
    }

    /** Drops everything not named in [keep], which is how the settings screen applies its removals. */
    fun retainOnly(keep: Set<String>) {
        val links = read()
        if (links.keys.retainAll(keep)) write(links)
    }

    private fun read(): LinkedHashMap<String, Link> {
        if (!encryptedFileManager.isFile(Keys.AUTOFILL_LINKS)) return LinkedHashMap()
        val json = encryptedFileManager.read(Keys.AUTOFILL_LINKS)
        if (json == Keys.NOT_FOUND) return LinkedHashMap()
        return runCatching { Gson().fromJson<LinkedHashMap<String, Link>>(json, TYPE) }
            .onFailure { GF.println("Autofill: remembered choices are unreadable; carrying on without them") }
            .getOrNull() ?: LinkedHashMap()
    }

    private fun write(links: Map<String, Link>) {
        runCatching { encryptedFileManager.store(Keys.AUTOFILL_LINKS, Gson().toJson(links)) }
            .onFailure { GF.println("Autofill: could not save what was chosen for this app or site") }
    }

    private companion object {
        val TYPE = object : TypeToken<LinkedHashMap<String, Link>>() {}.type

        /**
         * Far more than anyone will reach, and there so the file cannot grow without bound.
         *
         * It is read on every fill request, and a fill request is the one thing in this app that has to be quick.
         */
        const val MAX_LINKS = 200
    }
}
