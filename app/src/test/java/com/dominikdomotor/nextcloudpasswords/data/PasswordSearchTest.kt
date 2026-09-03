package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordSearchTest {
    private val github = password(label = "GitHub", username = "octocat", url = "https://github.com")
    private val gitlab = password(label = "GitLab", username = "dev", url = "https://gitlab.com")
    private val mail = password(label = "Mail", username = "git@example.com", url = "https://mail.example.com")

    private val all = listOf(mail, gitlab, github)

    @Test
    fun emptyQueryKeepsEverythingInOrder() {
        assertEquals(all, PasswordSearch.filter(all, ""))
    }

    @Test
    fun matchesLabelUsernameAndUrl() {
        assertTrue(PasswordSearch.matches(github, "hub"))
        assertTrue(PasswordSearch.matches(github, "octo"))
        assertTrue(PasswordSearch.matches(github, "github.com"))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertTrue(PasswordSearch.matches(github, "GITHUB"))
        assertTrue(PasswordSearch.matches(github, "github"))
    }

    @Test
    fun nonMatchesAreDropped() {
        assertFalse(PasswordSearch.matches(github, "zzz-nothing"))
        assertEquals(emptyList<Password>(), PasswordSearch.filter(all, "zzz-nothing"))
    }

    /** What the user typed is usually the entry's name, so a label hit beats a username hit. */
    @Test
    fun labelMatchOutranksUsernameMatch() {
        val byLabel = password(label = "Dominikscloud", username = "admin", url = "")
        val byUsername = password(label = "Some Server", username = "domi77full", url = "")

        val results = PasswordSearch.filter(listOf(byUsername, byLabel), "domi")

        assertEquals(listOf("Dominikscloud", "Some Server"), results.map { it.label })
    }

    @Test
    fun labelMatchOutranksUsernameEvenInTheMiddleOfTheLabel() {
        val byLabel = password(label = "My Dominikscloud", username = "admin", url = "")
        val byUsername = password(label = "Some Server", username = "domi77full", url = "")

        val results = PasswordSearch.filter(listOf(byUsername, byLabel), "domi")

        assertEquals(listOf("My Dominikscloud", "Some Server"), results.map { it.label })
    }

    @Test
    fun labelPrefixOutranksLabelSubstring() {
        val prefix = password(label = "Domi Cloud", username = "", url = "")
        val substring = password(label = "My Domi Cloud", username = "", url = "")

        val results = PasswordSearch.filter(listOf(substring, prefix), "domi")

        assertEquals(listOf("Domi Cloud", "My Domi Cloud"), results.map { it.label })
    }

    @Test
    fun usernameMatchOutranksUrlMatch() {
        val byUsername = password(label = "A", username = "domi", url = "")
        val byUrl = password(label = "B", username = "", url = "https://domi.example.com")

        val results = PasswordSearch.filter(listOf(byUrl, byUsername), "domi")

        assertEquals(listOf("A", "B"), results.map { it.label })
    }

    @Test
    fun labelPrefixMatchesRankFirst() {
        val results = PasswordSearch.filter(all, "git")
        assertEquals(listOf("GitLab", "GitHub", "Mail"), results.map { it.label })
    }

    /** Equal-ranking entries keep the alphabetical order the store already applied. */
    @Test
    fun tiesKeepTheirIncomingOrder() {
        val a = password(label = "Domi A", username = "", url = "")
        val b = password(label = "Domi B", username = "", url = "")

        assertEquals(listOf("Domi A", "Domi B"), PasswordSearch.filter(listOf(a, b), "domi").map { it.label })
        assertEquals(listOf("Domi B", "Domi A"), PasswordSearch.filter(listOf(b, a), "domi").map { it.label })
    }

    private fun password(label: String, username: String, url: String) =
        Password(label = label, username = username, url = url, password = "secret")
}
