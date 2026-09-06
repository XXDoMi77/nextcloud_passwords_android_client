package com.dominikdomotor.nextcloudpasswords.fragments.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.data.ApiResult
import com.dominikdomotor.nextcloudpasswords.data.PasswordRepository
import com.dominikdomotor.nextcloudpasswords.data.ShareDirection
import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.SharesItem
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import com.dominikdomotor.nextcloudpasswords.ui.SecureClipboard
import com.dominikdomotor.nextcloudpasswords.ui.reportFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.launch

/**
 * Password and share mutations shared by the details sheet, which can be opened from either the password list or the
 * folder browser.
 *
 * Scoped to the activity so both tabs act on the same instance.
 */
@HiltViewModel
class PasswordActionsViewModel
@Inject
constructor(
    private val repository: PasswordRepository,
    private val uiMessageManager: UiMessageManager,
    private val clipboard: SecureClipboard,
) : ViewModel() {
    val settings: Settings
        get() = repository.settings.value

    /** A snapshot of the folder tree, for building folder paths and the picker. */
    fun foldersSnapshot() = repository.folders.value

    fun sharesFor(passwordId: String): List<SharesItem> = repository.shares.value.filter { it.password == passwordId }

    /** Shares of this password that this account granted to other people - the ones it may edit or revoke. */
    fun outgoingSharesFor(passwordId: String): List<SharesItem> =
        ShareDirection.outgoing(sharesFor(passwordId), settings.username)

    /** The share this password arrived through, when somebody else granted it to this account. */
    fun incomingShareFor(passwordId: String): SharesItem? =
        ShareDirection.incoming(sharesFor(passwordId), settings.username)

    /** [onFailed] runs on failure so the editor can hand its fields back for a correction and a second try. */
    fun create(password: Password, onCreated: () -> Unit = {}, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            when (val result = repository.createPassword(password)) {
                is ApiResult.Success -> {
                    uiMessageManager.show(R.string.password_successfully_created)
                    // Before the favicons, which are a separate round of requests: the editor is waiting on this to
                    // close, and the new row is already in the list.
                    onCreated()
                    repository.downloadFavicons()
                }
                is ApiResult.Failure -> {
                    result.reportFailure(uiMessageManager)
                    onFailed()
                }
            }
        }
    }

    fun update(password: Password, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            when (val result = repository.updatePassword(password)) {
                is ApiResult.Success -> {
                    uiMessageManager.show(R.string.password_successfully_updated)
                    onDone()
                }
                is ApiResult.Failure -> result.reportFailure(uiMessageManager)
            }
        }
    }

    fun delete(password: Password, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            when (val result = repository.deletePassword(password)) {
                is ApiResult.Success -> {
                    uiMessageManager.show(R.string.password_successfully_deleted)
                    onDone()
                }
                is ApiResult.Failure -> result.reportFailure(uiMessageManager)
            }
        }
    }

    fun copy(text: String, label: String) = clipboard.copy(text, label)

    fun createShare(password: Password, receiverId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = repository.createShare(password, receiverId)) {
                is ApiResult.Success -> {
                    uiMessageManager.show(R.string.password_successfully_shared)
                    onDone()
                }
                is ApiResult.Failure -> result.reportFailure(uiMessageManager)
            }
        }
    }

    fun updateShare(shareId: String, editable: Boolean, shareable: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = repository.updateShare(shareId, editable, shareable)) {
                is ApiResult.Success -> {
                    uiMessageManager.show(R.string.share_permissions_updated)
                    onDone()
                }
                is ApiResult.Failure -> result.reportFailure(uiMessageManager)
            }
        }
    }

    fun revokeShare(shareId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = repository.revokeShare(shareId)) {
                is ApiResult.Success -> {
                    uiMessageManager.show(R.string.share_successfully_revoked)
                    onDone()
                }
                is ApiResult.Failure -> result.reportFailure(uiMessageManager)
            }
        }
    }

    fun searchRecipients(query: String, onResult: (Map<String, String>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.searchShareRecipients(query, RECIPIENT_LIMIT).valueOrNull().orEmpty())
        }
    }

    private companion object {
        const val RECIPIENT_LIMIT = 255
    }
}
