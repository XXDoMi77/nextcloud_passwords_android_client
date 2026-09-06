package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.SharesItem

/**
 * Which way round a share goes.
 *
 * `share/list` returns both directions in one list: shares this account granted to other people, and shares other
 * people granted to this account. They are told apart only by who the receiver is, and the details sheet used to show
 * every share on a password as a row reading "shared with <receiver>". For a password somebody shared *with* you the
 * receiver is you, so it claimed you had shared it with yourself.
 *
 * Kept free of Android types so it can be unit tested.
 */
object ShareDirection {
    /** Shares this account granted to somebody else. These are the ones the owner may edit or revoke. */
    fun outgoing(shares: List<SharesItem>, username: String): List<SharesItem> =
        shares.filterNot { it.isReceivedBy(username) }

    /**
     * The share a password arrived through, when somebody granted it to this account.
     *
     * At most one: a password reaches an account through a single share. Returned rather than a boolean because the
     * owner's name is what the sheet needs to show.
     */
    fun incoming(shares: List<SharesItem>, username: String): SharesItem? =
        shares.firstOrNull { it.isReceivedBy(username) }

    /**
     * Compared case-insensitively, and blanks never match.
     *
     * Nextcloud user ids are case-insensitive in practice, and the login name the app stored can differ in case from
     * the id the server reports on a share. A blank username would otherwise match a share with a blank receiver and
     * silently hide a real one.
     */
    private fun SharesItem.isReceivedBy(username: String): Boolean =
        username.isNotBlank() && receiver.id.equals(username, ignoreCase = true)
}
