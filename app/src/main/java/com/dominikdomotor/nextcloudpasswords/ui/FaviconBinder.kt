package com.dominikdomotor.nextcloudpasswords.ui

import android.widget.ImageView
import com.dominikdomotor.nextcloudpasswords.R
import com.dominikdomotor.nextcloudpasswords.managers.FaviconStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Puts a password's favicon into a row, decoding it only when that row is actually shown.
 *
 * Decoding the whole cache up front was the bulk of the app's start-up time on a large account. Rows that are never
 * scrolled to now cost nothing.
 */
class FaviconBinder(private val store: FaviconStore, private val scope: CoroutineScope) {
    fun bind(imageView: ImageView, passwordId: String) {
        imageView.setTag(R.id.favicon_password_id, passwordId)

        store.cached(passwordId)?.let {
            imageView.setImageBitmap(it)
            return
        }

        imageView.setImageResource(R.drawable.icon_foreground_24)
        scope.launch {
            val bitmap = store.load(passwordId) ?: return@launch
            // The row may have been recycled onto a different password while we were decoding.
            if (imageView.getTag(R.id.favicon_password_id) == passwordId) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }
}
