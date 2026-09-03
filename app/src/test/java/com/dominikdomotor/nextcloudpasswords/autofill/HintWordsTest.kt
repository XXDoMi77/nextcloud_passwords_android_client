package com.dominikdomotor.nextcloudpasswords.autofill

import org.junit.Assert.assertEquals
import org.junit.Test

class HintWordsTest {
    @Test
    fun `newlines commas semicolons and spaces all separate words`() {
        assertEquals(listOf("login", "user", "email", "mail"), HintWords.parse("login\nuser, email; mail"))
    }

    @Test
    fun `blank entries and duplicates are dropped`() {
        assertEquals(listOf("user", "login"), HintWords.parse("  user , , login ,, user \n\n"))
    }

    @Test
    fun `an empty editor yields an empty list rather than one blank word`() {
        assertEquals(emptyList<String>(), HintWords.parse("   \n  "))
    }

    @Test
    fun `formatting round-trips through parsing`() {
        val words = listOf("login", "benutzer", "utilisateur")
        assertEquals(words, HintWords.parse(HintWords.format(words)))
    }

    @Test
    fun `the built-in lists survive a round trip`() {
        // The defaults are what the editor shows first; a separator inside one of them would silently split it.
        assertEquals(
            HintWords.DEFAULT_USERNAME_WORDS,
            HintWords.parse(HintWords.format(HintWords.DEFAULT_USERNAME_WORDS)),
        )
        assertEquals(
            HintWords.DEFAULT_PASSWORD_WORDS,
            HintWords.parse(HintWords.format(HintWords.DEFAULT_PASSWORD_WORDS)),
        )
    }
}
