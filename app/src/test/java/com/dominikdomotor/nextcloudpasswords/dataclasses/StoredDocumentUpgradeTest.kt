package com.dominikdomotor.nextcloudpasswords.dataclasses

import com.dominikdomotor.nextcloudpasswords.autofill.HintWords
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that a document written by an older version still loads.
 *
 * The app stores everything as one Gson document and has no schema version, so every release that adds a field is a
 * silent migration: the stored JSON simply will not contain it. Whether that is harmless depends on something not
 * obvious - Kotlin emits a no-argument constructor only when *every* constructor parameter has a default, and Gson uses
 * it when it exists. If it does not, Gson allocates the object without running any constructor, and fields declared
 * non-null come back null. A `when` over a null enum then throws on the first launch after an update, on the user's
 * real data.
 *
 * These tests pin that down. Adding a field to [Settings] without a default would break them, which is the point.
 *
 * `StorageManager` does not rely on any of this in practice - it discards a document tagged with an older schema rather
 * than reinterpreting one - but the parsing has to stay sound for the versions that come after this one, where the
 * difference really will be a single added field.
 */
class StoredDocumentUpgradeTest {
    /** What the app wrote before the theming and autofill work landed - none of the newer keys. */
    private val legacyDocument =
        """
        {
          "settings": {
            "loggedIn": true,
            "server": "https://cloud.example.com",
            "username": "someone",
            "token": "a-token",
            "passwordLength": 24,
            "includeSymbols": 2,
            "excludeSimilarCharacters": true
          },
          "passwords": [],
          "folders": [],
          "shares": []
        }
        """
            .trimIndent()

    @Test
    fun `a document from before the theme settings existed still loads`() {
        val settings = Gson().fromJson(legacyDocument, Data::class.java).settings

        // The three fields this release added. Null here would crash on the first launch after an
        // update, because the enums are read on the way to the first frame.
        assertNotNull("themeMode came back null", settings.themeMode)
        assertNotNull("themeSeedSource came back null", settings.themeSeedSource)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(ThemeSeedSource.SERVER, settings.themeSeedSource)
        assertFalse(settings.tintedText)
    }

    @Test
    fun `fields added earlier in this line also survive`() {
        val settings = Gson().fromJson(legacyDocument, Data::class.java).settings

        // Collections are the other shape that hurts: a null list reaches a `for` loop rather than an
        // `if`, so it fails further from the cause.
        assertNotNull("autofillUsernameWords came back null", settings.autofillUsernameWords)
        assertNotNull("autofillPasswordWords came back null", settings.autofillPasswordWords)
        assertNotNull("autofillBlockedApps came back null", settings.autofillBlockedApps)
        assertEquals(HintWords.DEFAULT_USERNAME_WORDS, settings.autofillUsernameWords)
        assertNotNull("serverThemeColor came back null", settings.serverThemeColor)
        assertNotNull("accentColorOverride came back null", settings.accentColorOverride)
        assertNotNull("similarCharacters came back null", settings.similarCharacters)
        assertEquals(Settings.DEFAULT_SIMILAR_CHARACTERS, settings.similarCharacters)
    }

    @Test
    fun `what the document does carry is still read`() {
        val settings = Gson().fromJson(legacyDocument, Data::class.java).settings

        // The other half of the guarantee: defaults must not overwrite stored values.
        assertTrue(settings.loggedIn)
        assertEquals("https://cloud.example.com", settings.server)
        assertEquals("someone", settings.username)
        assertEquals(24, settings.passwordLength)
        assertEquals(2, settings.includedSymbolsQuantity)
    }

    @Test
    fun `a document written before versioning reports schema zero`() {
        // What the discard-on-upgrade check in StorageManager keys off. If this ever came back as the
        // current number, an unreadable document would be loaded instead of cleared.
        assertEquals(0, Gson().fromJson(legacyDocument, Data::class.java).schemaVersion)
    }

    @Test
    fun `an empty document loads as a fresh install`() {
        // What a truncated or corrupt file degrades to. StorageManager already falls back to Data()
        // when parsing throws, but "{}" parses fine and has to be safe on its own.
        val data = Gson().fromJson("{}", Data::class.java)

        assertNotNull(data.settings)
        assertNotNull(data.passwords)
        assertNotNull(data.folders)
        assertNotNull(data.shares)
        assertEquals(ThemeMode.SYSTEM, data.settings.themeMode)
        assertFalse(data.settings.loggedIn)
    }
}
