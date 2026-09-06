package com.dominikdomotor.nextcloudpasswords.autofill.debug

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import com.dominikdomotor.nextcloudpasswords.R

/**
 * What an app is called and what it looks like, for naming a capture.
 *
 * A capture stores only a package name, which says little at a glance - and for a web page the capture is titled by
 * its domain, so without this there is nothing to say which browser produced it.
 *
 * Resolved when a row is bound rather than when the capture is written: an icon is a Drawable and does not belong in a
 * JSON file, and the app may have been updated or uninstalled since. A missing app falls back to its package name and
 * the app's own key icon, so a capture from something no longer installed still reads sensibly.
 */
data class InstalledApp(val label: String, val icon: Drawable?) {
    companion object {
        fun of(context: Context, packageName: String): InstalledApp {
            val packages = context.packageManager
            val info = runCatching { packages.getApplicationInfo(packageName, PackageManager.GET_META_DATA) }.getOrNull()
            return InstalledApp(
                label = info?.let { packages.getApplicationLabel(it).toString() } ?: packageName,
                icon =
                    info?.let { runCatching { packages.getApplicationIcon(it) }.getOrNull() }
                        ?: AppCompatResources.getDrawable(context, R.drawable.icon_vpn_key_24),
            )
        }
    }
}
