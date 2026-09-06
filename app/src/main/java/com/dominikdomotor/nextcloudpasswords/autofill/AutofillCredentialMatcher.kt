package com.dominikdomotor.nextcloudpasswords.autofill

import com.dominikdomotor.nextcloudpasswords.data.Domains
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import java.net.URI

internal object AutofillCredentialMatcher {
    /**
     * The entries worth offering for this request, best first.
     *
     * [linkedPasswordId] is the entry the user picked by hand for this target once before. It is scored above every
     * kind of guess, which does two things: it comes first, and it is offered at all. The second half is the point,
     * because the reason someone reaches for the picker is usually that nothing matched.
     */
    fun matchingPasswords(
        passwords: List<Password>,
        webDomain: String?,
        applicationName: String,
        linkedPasswordId: String? = null,
    ): List<Password> {
        val targetHost = Domains.normalizeHost(webDomain)
        return passwords
            .mapNotNull { password ->
                val score =
                    when {
                        // An unsynced entry has no id yet, and neither has a link that was written wrong.
                        // Comparing those two blanks would hand a random entry the top score.
                        password.id.isNotBlank() && password.id == linkedPasswordId -> LINKED_BY_USER
                        targetHost != null -> scoreWebMatch(password, targetHost)
                        else -> scoreApplicationMatch(password, applicationName)
                    }
                score.takeIf { it > 0 }?.let { password to it }
            }
            .sortedWith(compareByDescending<Pair<Password, Int>> { it.second }.thenBy { it.first.label.lowercase() })
            .map(Pair<Password, Int>::first)
    }

    /** Above every score below, so a choice the user made themselves is never displaced by a guess. */
    private const val LINKED_BY_USER = 200

    private fun scoreWebMatch(password: Password, targetHost: String): Int {
        val credentialHost = hostFromUrl(password.url)
        if (credentialHost == targetHost) return 100
        if (
            credentialHost != null &&
                (credentialHost.endsWith(".$targetHost") || targetHost.endsWith(".$credentialHost"))
        ) {
            return 80
        }
        if (password.label.contains(targetHost, ignoreCase = true)) return 40
        return 0
    }

    private fun scoreApplicationMatch(password: Password, applicationName: String): Int {
        if (applicationName.isBlank()) return 0
        return when {
            password.label.equals(applicationName, ignoreCase = true) -> 80
            password.label.contains(applicationName, ignoreCase = true) -> 50
            password.url.contains(applicationName, ignoreCase = true) -> 30
            else -> 0
        }
    }

    private fun hostFromUrl(url: String): String? =
        runCatching {
                val uri = URI(if ("://" in url) url else "https://$url")
                if (uri.scheme != "https" && uri.scheme != "http") return@runCatching null
                Domains.normalizeHost(uri.host)
            }
            .getOrNull()
}
