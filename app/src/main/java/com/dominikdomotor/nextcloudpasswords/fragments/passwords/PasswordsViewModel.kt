package com.dominikdomotor.nextcloudpasswords.fragments.passwords

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.data.PasswordRepository
import com.dominikdomotor.nextcloudpasswords.data.PasswordSearch
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import com.dominikdomotor.nextcloudpasswords.ui.SecureClipboard
import com.dominikdomotor.nextcloudpasswords.ui.reportFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the password list: search, refresh, and creation. */
@HiltViewModel
class PasswordsViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: PasswordRepository,
    private val uiMessageManager: UiMessageManager,
    private val clipboard: SecureClipboard,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * True while a sync worth showing is running, wherever it started.
     *
     * Shared across tabs, so pulling here and switching to the other list keeps showing progress. Covers the sync at
     * start-up as well as the ones the user asks for; only a silent refresh on returning from the background is left
     * out. See [PasswordRepository.isSyncVisible].
     */
    val isRefreshing: StateFlow<Boolean> = repository.isSyncVisible

    /**
     * True while search reordering should apply instantly.
     *
     * Results are re-ranked on every keystroke, and watching rows slide past each other while typing is distracting for
     * some people — so it is a preference rather than a fixed behaviour.
     */
    val suppressSearchAnimation: StateFlow<Boolean> =
        combine(repository.settings, _query) { settings, query -> query.isNotEmpty() && !settings.animateSearchResults }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    val items: StateFlow<List<Password>> =
        combine(repository.passwords, _query) { passwords, query -> PasswordSearch.filter(passwords, query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        // Cold-start and return-to-foreground syncing is handled by ForegroundSyncCoordinator, so
        // switching to this tab does not re-fetch anything.
        viewModelScope.launch { repository.load() }
    }

    private var warmUpJob: Job? = null

    /**
     * Decodes cached favicons in the given order, restarting if the visible range changes.
     *
     * The list passes the rows on screen first, so those resolve immediately while the rest keep loading behind them.
     */
    fun warmFavicons(orderedIds: List<String>) {
        warmUpJob?.cancel()
        warmUpJob = viewModelScope.launch { repository.warmFavicons(orderedIds) }
    }

    fun search(query: String) {
        _query.value = query
    }

    /** A refresh the user asked for, so it confirms success; the automatic ones stay quiet. */
    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            if (repository.sync(showProgress = true).reportFailure(uiMessageManager).isSuccess) {
                uiMessageManager.show(R.string.password_list_fetch_successful)
            }
            // Deliberately after the indicator clears: favicons are rate limited server-side and can
            // take a while, which used to keep pull-to-refresh spinning long after the data arrived.
            downloadFavicons()
        }
    }

    fun copyUsername(password: Password) = clipboard.copy(password.username, "username")

    fun copyPassword(password: Password) = clipboard.copy(password.password, "password")

    private suspend fun downloadFavicons() = repository.downloadFavicons()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
