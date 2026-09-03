package com.dominikdomotor.nextcloudpasswords.managers

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

enum class UiMessageDuration {
    SHORT,
    LONG,
}

data class UiMessage(val text: String, val duration: UiMessageDuration)

@Singleton
class UiMessageManager @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun show(@StringRes messageResId: Int, duration: UiMessageDuration = UiMessageDuration.SHORT) {
        show(context.getString(messageResId), duration)
    }

    fun show(message: String, duration: UiMessageDuration = UiMessageDuration.SHORT) {
        messageChannel.trySend(UiMessage(message, duration))
    }
}
