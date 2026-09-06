package com.dominikdomotor.nextcloudpasswords.ui

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.dominikdomotor.nextcloudpasswords.R

/**
 * The server's password security rating.
 *
 * The colour and explanation used to be re-derived in four places from hardcoded `Color.GREEN` / `YELLOW` / `RED`
 * values, which were unreadable against the light theme.
 */
enum class PasswordStatus(@param:ColorRes val colorResId: Int, @param:StringRes val explanationResId: Int) {
    SECURE(R.color.password_status_secure, R.string.the_password_is_secure),
    WEAKENED(R.color.password_status_warning, R.string.the_password_is_either_outdated_or_a_duplicate),
    INSECURE(R.color.password_status_insecure, R.string.the_password_is_insecure),
    UNKNOWN(R.color.password_status_warning, R.string.the_security_status_of_the_password_was_not_checked);

    companion object {
        fun from(status: Int): PasswordStatus =
            when (status) {
                0 -> SECURE
                1 -> WEAKENED
                2 -> INSECURE
                else -> UNKNOWN
            }
    }
}
