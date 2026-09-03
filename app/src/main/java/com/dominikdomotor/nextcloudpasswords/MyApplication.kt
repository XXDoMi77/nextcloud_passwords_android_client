package com.dominikdomotor.nextcloudpasswords

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dominikdomotor.nextcloudpasswords.data.ForegroundSyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {
    @Inject lateinit var foregroundSync: ForegroundSyncCoordinator

    override fun onCreate() {
        super.onCreate()

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
