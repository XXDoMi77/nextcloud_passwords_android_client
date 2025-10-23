package com.dominikdomotor.nextcloudpasswords.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A thread-safe singleton for showing Toasts from anywhere in the app. This version is managed by Hilt. */
@Singleton
class ToastManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Shows a toast with the given string message. This is thread-safe and can be called from any background thread.
     */
    fun makeToast(message: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    /** Shows a toast with a string resource ID. This is thread-safe and can be called from any background thread. */
    fun makeToast(
        @StringRes resId: Int,
    ) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, resId, Toast.LENGTH_SHORT).show() }
    }
}
