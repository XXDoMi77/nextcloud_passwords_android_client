package com.dominikdomotor.nextcloudpasswords.fragments.settings

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.ui.AppDialog

/**
 * How to let Chrome use this app, in four pictures.
 *
 * Chrome does not hand a fill request to a third-party autofill service until the user opts in, in Chrome's own
 * settings, and it restarts. Until then nothing happens at all: no prompt, no error, no entry in any log. Every other
 * browser tested delegates without an extra step, so this is Chrome's own policy rather than a browser problem, and it
 * cannot be fixed from inside the app - only explained.
 *
 * There is no deep link to that screen. Chrome's settings activity is not exported, so even `adb` is refused, which is
 * why this is a walkthrough rather than a button.
 */
object ChromeAutofillDialog {
    fun show(activity: Activity) {
        val content = LayoutInflater.from(activity).inflate(R.layout.dialog_chrome_autofill_steps, null)
        AppDialog(activity).title(R.string.chrome_autofill_setup).content(content).button(R.string.close).show()
    }

    /** Whether Chrome is installed at all; without it the walkthrough is noise. */
    fun chromeIsInstalled(context: Context): Boolean =
        CHROME_PACKAGES.any { packageName ->
            runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess ||
                runCatching {
                        context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                    }
                    .isSuccess
        }

    private val CHROME_PACKAGES = listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary")
}
