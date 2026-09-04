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
import com.dominikdomotor.nextcloudpasswords.ui.theme.ThemeApplier
import com.dominikdomotor.nextcloudpasswords.ui.theme.ThemeCache
import com.dominikdomotor.nextcloudpasswords.ui.theme.applySystemBarAppearance
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

        // The one safe window to re-seed the palette, and the reason this lives in the base class rather than in each
        // activity. It has to be after super.onCreate(), which is where AppCompat resolves night mode and where the
        // splash screen hands over to postSplashScreenTheme, and before any binding is inflated - every widget reads
        // its colours once, at inflation. All four activities extend this, so :autofill_process is covered too.
        ThemeApplier.apply(this)

        // Secure by default: settings load asynchronously, and the window must never be capturable
        // in the window between the activity starting and that load finishing.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Draw behind the system bars. Not a choice any more - Android 15 enforces edge-to-edge for apps
        // targeting 35+, and on 16 the opt-out attribute is disabled outright - so each screen takes the insets
        // and pads itself. enableEdgeToEdge supersedes setDecorFitsSystemWindows and also makes both bars
        // transparent on versions that still honour a bar colour.
        WindowCompat.enableEdgeToEdge(window)

        // After enableEdgeToEdge, which picks the bar icons from the device theme. The palette is what decides
        // here instead: it can produce a light surface while the phone is in dark mode, and the previous code
        // hardcoded light-on-dark, so white icons ended up on a near-white status bar. In the base class so the
        // login and autofill screens are covered as well.
        applySystemBarAppearance()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                storageManager.settings.collect { settings ->
                    if (settings.allowScreenshots) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

                    // Mirror the theme where the next start can read it synchronously. Writing it - never applying it
                    // - is the whole job here: this runs on every settings emission, and recreating from inside a
                    // collect would loop. A colour the admin changed server-side therefore lands on the next start
                    // rather than yanking the palette out from under whatever the user is doing.
                    ThemeCache.write(this@BaseActivity, settings)
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
