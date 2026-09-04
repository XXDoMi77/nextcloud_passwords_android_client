package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import com.dominikdomotor.nextcloudpasswords.ui.reportFailure
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Refreshes from the server when the app comes to the foreground.
 *
 * Driven by `ProcessLifecycleOwner`, so this fires on a cold start and whenever the app is re-entered after being
 * backgrounded — but not when switching tabs or rotating the device, which do not take the process out of the
 * foreground.
 */
@Singleton
class ForegroundSyncCoordinator
@Inject
constructor(private val repository: PasswordRepository, private val uiMessageManager: UiMessageManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /**
     * Whether the next sync is this process's first.
     *
     * Flipped where the sync actually happens rather than when the job starts, because the first pass usually does not
     * sync at all: the app opens on the login screen, returns here with no account and stops. The sync that matters is
     * the one after the browser hands the user back, and that is the one this is still true for.
     */
    private var isFirstSync = true

    fun onEnterForeground() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                repository.load()
                // Nothing to sync before the user has an account, and the login screens run their
                // own requests.
                if (!repository.settings.value.loggedIn) return@launch

                // Start-up shows a progress bar; a later return from the background does not. On a first run
                // there is nothing cached to look at, so the bar is the only thing telling the user that their
                // passwords are on the way - and re-entering the app is far too frequent to announce.
                val showProgress = isFirstSync
                isFirstSync = false
                repository.sync(showProgress = showProgress).reportFailure(uiMessageManager)
                repository.downloadFavicons()
            }
    }
}
