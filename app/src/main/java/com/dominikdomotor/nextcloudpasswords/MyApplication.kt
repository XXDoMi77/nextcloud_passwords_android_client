package com.dominikdomotor.nextcloudpasswords

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dominikdomotor.nextcloudpasswords.data.ForegroundSyncCoordinator
import com.dominikdomotor.nextcloudpasswords.ui.theme.ThemeApplier
import com.dominikdomotor.nextcloudpasswords.ui.theme.ThemeCache
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {
    @Inject lateinit var foregroundSync: ForegroundSyncCoordinator

    override fun onCreate() {
        super.onCreate()

        // Night mode has to be set before the first activity is created, or AppCompat resolves the old value and the
        // app starts in the wrong theme and then recreates itself in view of the user. Read from the cache rather than
        // from settings, which are still encrypted on disk at this point.
        AppCompatDelegate.setDefaultNightMode(ThemeApplier.nightModeFor(ThemeCache.read(this).mode))

        // Process-level, not activity-level: this fires on a cold start and on every return from the
        // background, but not when switching tabs or rotating.
        ProcessLifecycleOwner.get()
            .lifecycle
            .addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) = foregroundSync.onEnterForeground()
                }
            )
    }
}
