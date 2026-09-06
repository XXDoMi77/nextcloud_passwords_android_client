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

    @Test
    fun aRememberedChoiceOutranksAPerfectHostMatch() {
        val perfect = password("Perfect", "https://example.com", id = "perfect")
        val chosen = password("Chosen", "https://somewhere-else.test", id = "chosen")

        val matches =
            AutofillCredentialMatcher.matchingPasswords(
                listOf(perfect, chosen),
                "example.com",
                "Chrome",
                linkedPasswordId = "chosen",
            )

        assertEquals(listOf(chosen, perfect), matches)
    }

    /** The reason someone opens the picker is usually that nothing matched, so the entry they chose has to be let in. */
    @Test
    fun aRememberedChoiceIsOfferedEvenWhenNothingAboutItMatches() {
        val chosen = password("Nothing In Common", "", id = "chosen")

        val matches =
            AutofillCredentialMatcher.matchingPasswords(
                listOf(chosen),
                "example.com",
                "Chrome",
                linkedPasswordId = "chosen",
            )

        assertEquals(listOf(chosen), matches)
    }

    @Test
    fun anEntryWithoutAnIdIsNotMistakenForTheRememberedOne() {
        val unsaved = password("Never Synced", "https://somewhere-else.test")

        val matches =
            AutofillCredentialMatcher.matchingPasswords(listOf(unsaved), "example.com", "Chrome", linkedPasswordId = "")

        assertTrue(matches.isEmpty())
    }

    private fun password(label: String, url: String, id: String = "") =
        Password(id = id, label = label, url = url, username = "user", password = "secret")
}
