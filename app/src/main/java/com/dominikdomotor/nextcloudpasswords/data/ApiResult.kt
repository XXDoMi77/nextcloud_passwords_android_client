package com.dominikdomotor.nextcloudpasswords.data

import androidx.annotation.StringRes
import com.dominikdomotor.nextcloudpasswords.R

/** Why a request could not be completed. Each value maps to exactly one user-facing message. */
enum class FailureReason {
    /**
     * The request never reached the server, or the connection dropped.
     *
     * Deliberately not phrased as "no internet": the same failure covers a device that is offline, a server that
     * is down, and a URL that no longer resolves. Naming only the first would be wrong two times out of three,
     * and the action - check the connection, try again - is the same for all of them.
     */
    NETWORK,

    /** The Passwords API session expired; the E2E challenge has to be solved again. */
    SESSION_EXPIRED,

    /** The stored app password is no longer accepted. */
    UNAUTHORIZED,

    /** The server answered, but not with success. */
    SERVER,

    /** The response arrived but could not be parsed or decrypted. */
    PARSE,

    /** An E2E passphrase is needed before this account's data can be read. */
    PASSPHRASE_REQUIRED,

    /** The account uses a challenge or token scheme this client does not implement. */
    UNSUPPORTED_CHALLENGE,

    /** CSEv1r1 passwords cannot be shared; the protocol has no provision for it. */
    SHARING_UNAVAILABLE,
}

@get:StringRes
val FailureReason.messageResId: Int
    get() =
        when (this) {
            FailureReason.NETWORK -> R.string.could_not_reach_server
            FailureReason.SESSION_EXPIRED -> R.string.e2e_session_expired
            FailureReason.UNAUTHORIZED -> R.string.your_token_is_no_longer_valid_please_login_again
            FailureReason.SERVER -> R.string.something_went_wrong_try_again
            FailureReason.PARSE -> R.string.something_went_wrong_try_again
            FailureReason.PASSPHRASE_REQUIRED -> R.string.e2e_unlock_required
            FailureReason.UNSUPPORTED_CHALLENGE -> R.string.unsupported_e2e_challenge
            FailureReason.SHARING_UNAVAILABLE -> R.string.e2e_passwords_cannot_be_shared
        }

/**
 * The outcome of a network operation.
 *
 * Replaces the previous `func: () -> Unit` callbacks, which fired identically whether the request succeeded or failed
 * and so made every failure look like an empty result.
 */
sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>

    data class Failure(val reason: FailureReason, val cause: Throwable? = null) : ApiResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    fun valueOrNull(): T? = (this as? Success)?.value

    fun failureOrNull(): Failure? = this as? Failure
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(value))
        is ApiResult.Failure -> this
    }

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(value)
    return this
}

inline fun <T> ApiResult<T>.onFailure(action: (ApiResult.Failure) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) action(this)
    return this
}
