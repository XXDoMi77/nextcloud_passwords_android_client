package com.dominikdomotor.nextcloudpasswords.autofill

import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillCredentialMatcherTest {
    @Test
    fun exactHostRanksBeforeRelatedSubdomain() {
        val exact = password("Exact", "https://accounts.example.com/login")
        val related = password("Related", "https://example.com")

        val matches =
            AutofillCredentialMatcher.matchingPasswords(listOf(related, exact), "accounts.example.com", "Chrome")

        assertEquals(listOf(exact, related), matches)
    }

    @Test
    fun unrelatedSubstringHostDoesNotMatch() {
        val unrelated = password("Not Example", "https://notexample.com")

        val matches = AutofillCredentialMatcher.matchingPasswords(listOf(unrelated), "example.com", "Chrome")

        assertTrue(matches.isEmpty())
    }

    @Test
    fun nativeAppFallsBackToCredentialLabel() {
        val credential = password("Example App", "")

        val matches = AutofillCredentialMatcher.matchingPasswords(listOf(credential), null, "Example App")

        assertEquals(listOf(credential), matches)
    }

    private fun password(label: String, url: String) =
        Password(label = label, url = url, username = "user", password = "secret")
}
