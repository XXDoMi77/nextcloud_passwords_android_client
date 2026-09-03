package com.dominikdomotor.nextcloudpasswords.managers

import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    @Test
    fun honoursTheRequestedLength() {
        repeat(REPEATS) { assertEquals(24, PasswordGenerator.generate(settings(length = 24)).length) }
    }

    @Test
    fun neverProducesAnEmptyPassword() {
        assertEquals(1, PasswordGenerator.generate(settings(length = 0)).length)
        assertEquals(1, PasswordGenerator.generate(settings(length = -5)).length)
    }

    @Test
    fun insertsTheRequestedNumberOfSymbols() {
        val symbols = "!@#"
        repeat(REPEATS) {
            val generated = PasswordGenerator.generate(settings(length = 20, symbolCount = 3, symbols = symbols))
            assertEquals(3, generated.count { it in symbols })
        }
    }

    @Test
    fun omitsSymbolsWhenTheSetIsEmpty() {
        repeat(REPEATS) {
            val generated = PasswordGenerator.generate(settings(length = 16, symbolCount = 5, symbols = ""))
            assertTrue(generated.all(Char::isLetterOrDigit))
        }
    }

    /** Asking for more symbols than there are characters must not overflow the length. */
    @Test
    fun clampsSymbolCountToTheLength() {
        val generated = PasswordGenerator.generate(settings(length = 4, symbolCount = 99, symbols = "!@#"))
        assertEquals(4, generated.length)
    }

    @Test
    fun excludesLookAlikeCharactersWhenAsked() {
        val confusable = "iIlL1oO0uvcemnwWbdpqsS5"
        repeat(REPEATS) {
            val generated = PasswordGenerator.generate(settings(length = 40, excludeSimilar = true, symbolCount = 0))
            assertTrue(generated.none { it in confusable })
        }
    }

    @Test
    fun allowsLookAlikeCharactersWhenNotExcluded() {
        val generated =
            (1..REPEATS).joinToString("") {
                PasswordGenerator.generate(settings(length = 40, excludeSimilar = false, symbolCount = 0))
            }
        // Across this many characters at least one confusable character is essentially certain.
        assertTrue(generated.any { it in "iIlL1oO0" })
    }

    @Test
    fun excludesTheUsersOwnListOfLookAlikeCharacters() {
        repeat(REPEATS) {
            val generated =
                PasswordGenerator.generate(
                    settings(length = 40, excludeSimilar = true, symbolCount = 0, similar = "aeiou")
                )
            assertTrue(generated.none { it in "aeiou" })
            // Only the listed characters are excluded, so the previous default set is now allowed.
            assertTrue(generated.any(Char::isLetterOrDigit))
        }
    }

    /** An emptied list must not leave the generator with nothing to choose from. */
    @Test
    fun fallsBackToEveryCharacterWhenTheListIsEmptied() {
        val generated = PasswordGenerator.generate(settings(length = 12, excludeSimilar = true, similar = ""))
        assertEquals(12, generated.length)
    }

    /** Excluding every letter and digit would otherwise leave an empty alphabet. */
    @Test
    fun fallsBackWhenTheListExcludesEverything() {
        val everything = ('a'..'z').joinToString("") + ('A'..'Z').joinToString("") + "0123456789"
        val generated =
            PasswordGenerator.generate(
                settings(length = 12, excludeSimilar = true, symbolCount = 0, similar = everything)
            )
        assertEquals(12, generated.length)
    }

    private fun settings(
        length: Int = 20,
        symbolCount: Int = 0,
        symbols: String = Settings.DEFAULT_SYMBOLS,
        excludeSimilar: Boolean = false,
        similar: String = Settings.DEFAULT_SIMILAR_CHARACTERS,
    ) =
        Settings(
            passwordLength = length,
            includedSymbolsQuantity = symbolCount,
            includedSymbols = symbols,
            excludeSimilarCharacters = excludeSimilar,
            similarCharacters = similar,
        )

    private companion object {
        const val REPEATS = 50
    }
}
