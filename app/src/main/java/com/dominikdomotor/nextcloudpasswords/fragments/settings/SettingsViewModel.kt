package com.dominikdomotor.nextcloudpasswords.fragments.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.data.ApiResult
import com.dominikdomotor.nextcloudpasswords.autofill.AutofillLinkStore
import com.dominikdomotor.nextcloudpasswords.data.PasswordRepository
import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import com.dominikdomotor.nextcloudpasswords.managers.E2eSessionManager
import com.dominikdomotor.nextcloudpasswords.managers.FaviconStore
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import com.dominikdomotor.nextcloudpasswords.ui.reportFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val repository: PasswordRepository,
    private val storageManager: StorageManager,
    private val e2eSessionManager: E2eSessionManager,
    private val uiMessageManager: UiMessageManager,
    private val autofillLinkStore: AutofillLinkStore,
    private val faviconStore: FaviconStore,
) : ViewModel() {
    /** Observable so the screen reflects changes made elsewhere, such as an E2E unlock. */
    val settings: StateFlow<Settings> = repository.settings

    fun update(block: (Settings) -> Unit) = storageManager.updateSettings(block)

    /** One write for both lists, so autofill never sees a half-applied change. */
    fun updateHintWords(usernameWords: List<String>, passwordWords: List<String>) {
        update {
            it.autofillUsernameWords = usernameWords
            it.autofillPasswordWords = passwordWords
        }
        uiMessageManager.show(R.string.autofill_hint_words_saved)
    }

    /**
     * What autofill has learned about each site and app, read straight from disk.
     *
     * Not a flow: the autofill service writes this file from its own process, so nothing in this one would be told
     * when it changes. Reading it when the screen asks is both simpler and always right.
     */
    fun rememberedAutofillChoices(): Map<String, AutofillLinkStore.Link> = autofillLinkStore.all()

    /** Keeps only the sites and apps still listed after the user removed some. */
    fun keepRememberedAutofillChoices(keys: Set<String>) = autofillLinkStore.retainOnly(keys)

    /** Where the labels for the remembered entries come from; the ids on disk mean nothing on their own. */
    fun passwordsForDisplay() = storageManager.passwords.value

    /** Decodes on a miss, so callers keep it off the main thread. */
    fun faviconFor(passwordId: String) = faviconStore.peek(passwordId)

    fun forgetStoredPassphrase() {
        e2eSessionManager.forgetStoredPassphrase()
        uiMessageManager.show(R.string.e2e_passphrase_forgotten)
    }

    fun clearCaches() {
        viewModelScope.launch {
            repository.clearCaches()
            uiMessageManager.show(R.string.offline_cache_cleared)
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            when (val result = repository.logout()) {
                is ApiResult.Success -> {
                    uiMessageManager.show(R.string.successfully_logged_out)
                    onLoggedOut()
                }
                is ApiResult.Failure -> result.reportFailure(uiMessageManager)
            }
        }
    }
}
