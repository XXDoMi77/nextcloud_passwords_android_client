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

    fun onEnterForeground() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                repository.load()
                // Nothing to sync before the user has an account, and the login screens run their
                // own requests.
                if (!repository.settings.value.loggedIn) return@launch

                repository.sync().reportFailure(uiMessageManager)
                repository.downloadFavicons()
            }
    }
}
