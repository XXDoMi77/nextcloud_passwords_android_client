package com.dominikdomotor.nextcloudpasswords.data

import java.net.URLDecoder

/**
 * Parses the `nc://login/...` callback the Nextcloud login flow can hand back to the app.
 *
 * Kept free of Android types so it can be unit tested. Login Flow v2 normally delivers credentials through polling and
 * only uses `nc://login/` as a "you are back in the app" signal, but servers and older clients still emit the legacy
 * form carrying the credentials inline.
 */
object LoginCallbackParser {
    /** What a callback URI meant. */
    sealed interface Result {
        /** Not a login callback at all. */
        data object NotACallback : Result

        /** The bare `nc://login/` return signal; credentials arrive via polling. */
        data object ReturnedToApp : Result

        /** The legacy inline form, carrying credentials directly. */
        data class Credentials(val server: String, val username: String, val appPassword: String) : Result
    }

    fun parse(uri: String?): Result {
        if (uri == null) return Result.NotACallback

        val trimmed = uri.trim()
        if (!trimmed.startsWith(PREFIX, ignoreCase = true)) return Result.NotACallback
        if (trimmed.equals(PREFIX, ignoreCase = true) || trimmed.equals("$PREFIX/", ignoreCase = true)) {
            return Result.ReturnedToApp
        }

        val values =
            trimmed
                .removePrefix("$PREFIX/")
                .split('&')
                // A part without a colon is malformed; substring(0, -1) used to throw here.
                .mapNotNull { part ->
                    val delimiter = part.indexOf(':')
                    if (delimiter <= 0) null else part.take(delimiter) to decode(part.substring(delimiter + 1))
                }
                .toMap()

        val server = values["server"]
        val username = values["user"]
        val appPassword = values["password"]
        return if (server.isNullOrBlank() || username.isNullOrBlank() || appPassword.isNullOrBlank()) {
            // Recognisably a callback, but without usable credentials — let polling finish the job.
            Result.ReturnedToApp
        } else {
            Result.Credentials(server, username, appPassword)
        }
    }

    /** `URLDecoder` already maps `+` to a space, which is the encoding this callback uses. */
    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private const val PREFIX = "nc://login"
}
