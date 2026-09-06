package com.dominikdomotor.nextcloudpasswords.data

import java.net.URI
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

    /**
     * Whether a callback's server is the same origin as the one a login was started against.
     *
     * `nc://` is a custom scheme: unlike an `https` app link there is no `assetlinks.json` proving who may use it, so
     * any page the user visits can navigate to one and any installed app can register the same filter. An inline
     * callback therefore has to be matched against the server the user actually typed - otherwise a link is enough to
     * point the app at someone else's Nextcloud, and every entry created afterwards is created there.
     *
     * Compared by origin rather than by string. The two spellings differ in ways that do not change where the
     * credentials go - a trailing slash, upper case in the host, the default port written out - while an attacker's
     * URL has to differ in the host to be worth anything. The path is deliberately not compared: an installation under
     * a subdirectory is still the same server, and whoever controls the host controls all of it anyway.
     */
    fun isSameOrigin(expected: String, actual: String): Boolean {
        val left = origin(expected) ?: return false
        val right = origin(actual) ?: return false
        return left == right
    }

    /** Scheme, host and effective port, lower-cased; null when the value is not a usable absolute URL. */
    private fun origin(url: String): String? =
        runCatching {
                val uri = URI(url.trim())
                val scheme = uri.scheme?.lowercase() ?: return null
                val host = uri.host?.lowercase() ?: return null
                if (host.isBlank()) return null
                val port = if (uri.port != -1) uri.port else if (scheme == "https") 443 else if (scheme == "http") 80 else -1
                "$scheme://$host:$port"
            }
            .getOrNull()

    /** `URLDecoder` already maps `+` to a space, which is the encoding this callback uses. */
    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private const val PREFIX = "nc://login"
}
