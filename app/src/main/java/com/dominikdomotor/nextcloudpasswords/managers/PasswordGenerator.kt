package com.dominikdomotor.nextcloudpasswords.managers

import com.dominikdomotor.nextcloudpasswords.dataclasses.Settings

object PasswordGenerator {
    const val DEFAULT_SYMBOLS = Settings.DEFAULT_SYMBOLS

    fun generate(settings: Settings): String {
        val length = settings.passwordLength.coerceAtLeast(1)
        val similarCharacters = settings.similarCharacters
        val baseCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val availableCharacters =
            if (settings.excludeSimilarCharacters && similarCharacters.isNotEmpty()) {
                // An empty list would otherwise leave nothing to pick from.
                baseCharacters.filterNot { it in similarCharacters }.ifEmpty { baseCharacters }
            } else {
                baseCharacters
            }
        val symbols = settings.includedSymbols
        val symbolCount = if (symbols.isEmpty()) 0 else settings.includedSymbolsQuantity.coerceIn(0, length)
        val symbolPositions = (0 until length).shuffled().take(symbolCount).toSet()

        return (0 until length).joinToString("") { index ->
            if (index in symbolPositions) symbols.random().toString() else availableCharacters.random().toString()
        }
    }
}
