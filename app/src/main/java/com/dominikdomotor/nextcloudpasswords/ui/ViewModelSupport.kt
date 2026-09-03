package com.dominikdomotor.nextcloudpasswords.ui

import com.dominikdomotor.nextcloudpasswords.data.ApiResult
import com.dominikdomotor.nextcloudpasswords.data.messageResId
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageDuration
import com.dominikdomotor.nextcloudpasswords.managers.UiMessageManager

/**
 * Surfaces a failed [ApiResult] as a message.
 *
 * Every failure now carries a reason, so the UI can say what actually went wrong instead of defaulting to "something
 * went wrong, try again" for a parse error, an expired session and a dead network alike.
 */
fun ApiResult<*>.reportFailure(uiMessageManager: UiMessageManager): ApiResult<*> {
    failureOrNull()?.let { uiMessageManager.show(it.reason.messageResId, UiMessageDuration.LONG) }
    return this
}
