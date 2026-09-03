package com.dominikdomotor.nextcloudpasswords.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikdomotor.nextcloudpasswords.data.PasswordRepository
import com.dominikdomotor.nextcloudpasswords.managers.E2eSessionManager
import com.dominikdomotor.nextcloudpasswords.managers.E2eSessionResult
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import com.dominikdomotor.nextcloudpasswords.ui.reportFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owns the shell state: whether we are still signed in, and the E2E unlock prompt. */
@HiltViewModel
class OverviewViewModel
@Inject
constructor(
    private val repository: PasswordRepository,
    private val e2eSessionManager: E2eSessionManager,
    private val uiMessageManager: UiMessageManager,
) : ViewModel() {
    private val _loaded = MutableStateFlow(false)

    /** True once the local store has been read, so the shell does not redirect before then. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val signedOut: StateFlow<Boolean> =
        repository.settings
            .map { !it.loggedIn && !it.loginInProgress }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val passphraseRequired = repository.passphraseRequired

    init {
        viewModelScope.launch {
            repository.load()
            _loaded.value = true
            // After the splash is released, so it never delays first paint.
            repository.migrateFaviconCache()
        }
    }

    /** Attempts to open the E2E session; on success triggers a full sync. */
    fun unlock(passphrase: String, storePassphrase: Boolean, onResult: (E2eSessionResult) -> Unit) {
        viewModelScope.launch {
            val result = e2eSessionManager.ensureSession(passphrase, storePassphrase)
            onResult(result)
            if (result == E2eSessionResult.READY) {
                repository.sync().reportFailure(uiMessageManager)
                repository.downloadFavicons()
            }
        }
    }

    fun dismissPassphrasePrompt() = e2eSessionManager.dismissPassphrasePrompt()
}
