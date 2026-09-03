package com.dominikdomotor.nextcloudpasswords.activities

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dominikdomotor.nextcloudpasswords.managers.StorageManager
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageDuration
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
abstract class BaseActivity : AppCompatActivity() {
    @Inject lateinit var uiMessageManager: UiMessageManager
    @Inject lateinit var storageManager: StorageManager
    private var activeToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Secure by default: settings load asynchronously, and the window must never be capturable
        // in the window between the activity starting and that load finishing.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                storageManager.settings.collect { settings ->
                    if (settings.allowScreenshots) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                uiMessageManager.messages.collect { message ->
                    activeToast?.cancel()
                    activeToast =
                        Toast.makeText(
                                this@BaseActivity,
                                message.text,
                                if (message.duration == UiMessageDuration.LONG) Toast.LENGTH_LONG
                                else Toast.LENGTH_SHORT,
                            )
                            .also(Toast::show)
                }
            }
        }
    }

    fun showMessage(message: String, duration: UiMessageDuration = UiMessageDuration.SHORT) {
        uiMessageManager.show(message, duration)
    }

    fun showMessage(messageResId: Int, duration: UiMessageDuration = UiMessageDuration.SHORT) {
        uiMessageManager.show(messageResId, duration)
    }
}
