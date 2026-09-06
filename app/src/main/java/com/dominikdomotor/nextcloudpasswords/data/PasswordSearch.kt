package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password

/** Filtering and ranking for the password list's search box. */
object PasswordSearch {
    fun matches(password: Password, query: String): Boolean = rank(password, query) < NO_MATCH

    /**
     * Matching passwords, best match first.
     *
     * What the user typed is almost always the name of the entry they want, so the label outranks the username and the
     * URL. Searching "cloud" puts an entry named "Cloudstore" above one merely owned by "cloudadmin@…", and an exact
     * prefix outranks a match in the middle of the text.
     *
     * Ties keep their incoming order, which is the alphabetical order the store already applies.
     */
    fun filter(passwords: List<Password>, query: String): List<Password> {
        if (query.isEmpty()) return passwords
        return passwords
            .map { it to rank(it, query) }
            .filter { it.second < NO_MATCH }
            .sortedBy { it.second }
            .map { it.first }
    }

    /** Lower is a better match. [NO_MATCH] means the query does not appear at all. */
    private fun rank(password: Password, query: String): Int {
        if (query.isEmpty()) return LABEL_PREFIX
        return when {
            password.label.startsWith(query, ignoreCase = true) -> LABEL_PREFIX
            password.label.contains(query, ignoreCase = true) -> LABEL_CONTAINS
            password.username.startsWith(query, ignoreCase = true) -> USERNAME_PREFIX
            password.username.contains(query, ignoreCase = true) -> USERNAME_CONTAINS
            password.url.contains(query, ignoreCase = true) -> URL_CONTAINS
            else -> NO_MATCH
        }
    }

    private const val LABEL_PREFIX = 0
    private const val LABEL_CONTAINS = 1
    private const val USERNAME_PREFIX = 2
    private const val USERNAME_CONTAINS = 3
    private const val URL_CONTAINS = 4
    private const val NO_MATCH = 5
}
