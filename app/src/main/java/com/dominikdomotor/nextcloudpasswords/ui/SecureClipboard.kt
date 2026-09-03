package com.dominikdomotor.nextcloudpasswords.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.core.content.getSystemService
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Copies secrets to the clipboard, marks them sensitive so the system does not show a preview, and clears them again
 * after [CLEAR_DELAY_MILLIS] if nothing else has taken the clipboard since.
 *
 * Previously duplicated verbatim in `FoldersFragment` and `PasswordListAdapter`.
 */
@Singleton
class SecureClipboard
@Inject
constructor(@param:ApplicationContext private val context: Context, private val uiMessageManager: UiMessageManager) {
    private val handler = Handler(Looper.getMainLooper())

    /**
     * @param label clipboard label, also used in the confirmation message
     * @param announce whether to show the "… copied to clipboard" message
     */
    fun copy(text: String, label: String, announce: Boolean = true) {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return
        val clip =
            ClipData.newPlainText(label, text).apply {
                description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
            }
        clipboard.setPrimaryClip(clip)

        if (announce && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ shows its own copy confirmation, so only announce it ourselves below that.
            val name = label.replaceFirstChar { it.uppercase() }
            uiMessageManager.show("$name ${context.getString(R.string.copied_to_clipboard)}")
        }

        handler.postDelayed({ clearIfUnchanged(clipboard, text) }, CLEAR_DELAY_MILLIS)
    }

    private fun clearIfUnchanged(clipboard: ClipboardManager, expected: String) {
        val current = runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        if (current == expected) {
            runCatching { clipboard.clearPrimaryClip() }
        }
    }

    private companion object {
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        const val CLEAR_DELAY_MILLIS = 30_000L
    }
}
