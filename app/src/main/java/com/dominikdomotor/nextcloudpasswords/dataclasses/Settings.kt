package com.dominikdomotor.nextcloudpasswords.dataclasses

import com.dominikdomotor.nextcloudpasswords.autofill.HintWords
import com.google.gson.annotations.SerializedName

data class Settings(
    @SerializedName("loggedIn") var loggedIn: Boolean = false,
    @SerializedName("server") var server: String = "",
    @SerializedName("username") var username: String = "",
    @SerializedName("token") var token: String = "",
    @SerializedName("basicAuth") var basicAuth: String = "",
    @SerializedName("trustedCertificateHost") var trustedCertificateHost: String = "",
    @SerializedName("trustedCertificateSha256") var trustedCertificateSha256: String = "",
    @SerializedName("e2ePassphrase") var e2ePassphrase: String = "",
    @SerializedName("passwordLength") var passwordLength: Int = 20,
    @SerializedName("includeSymbols") var includedSymbolsQuantity: Int = 1,
    @SerializedName("includedSymbolCharacters") var includedSymbols: String = DEFAULT_SYMBOLS,
    @SerializedName("excludeSimilarCharacters") var excludeSimilarCharacters: Boolean = true,
    @SerializedName("similarCharacters") var similarCharacters: String = DEFAULT_SIMILAR_CHARACTERS,
    @SerializedName("expandBottomSheet") var expandBottomSheet: Boolean = false,
    @SerializedName("animateSearchResults") var animateSearchResults: Boolean = true,
    @SerializedName("inlineAutofillSuggestions") var inlineAutofillSuggestions: Boolean = true,
    @SerializedName("autofillManualFallback") var autofillManualFallback: Boolean = false,
    @SerializedName("autofillBlockedApps") var autofillBlockedApps: List<String> = emptyList(),
    @SerializedName("autofillUsernameWords") var autofillUsernameWords: List<String> = HintWords.DEFAULT_USERNAME_WORDS,
    @SerializedName("autofillPasswordWords") var autofillPasswordWords: List<String> = HintWords.DEFAULT_PASSWORD_WORDS,
    @SerializedName("allowScreenshots") var allowScreenshots: Boolean = false,
    /** The colour the Nextcloud admin set in the Theming app, refreshed on every sync. */
    @SerializedName("serverThemeColor") var serverThemeColor: String = "",
    /** Set only when the user picked a colour by hand; empty means "follow the server". */
    @SerializedName("accentColorOverride") var accentColorOverride: String = "",
    /** Which of the three colours above the whole generated palette is seeded from. */
    @SerializedName("themeSeedSource") var themeSeedSource: ThemeSeedSource = ThemeSeedSource.SERVER,
    /** Light, dark, or either flavour of dark. AMOLED implies dark rather than sitting beside it. */
    @SerializedName("themeMode") var themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Whether text carries the seed's hue too, or stays a shade of black and white. Off is the readable default. */
    @SerializedName("tintedText") var tintedText: Boolean = false,
    @SerializedName("loginInProgress") var loginInProgress: Boolean = false,
) {
    companion object {
        const val DEFAULT_SYMBOLS = "!@#$%&*._-"

        /** Characters that are easy to confuse with one another in most fonts. */
        const val DEFAULT_SIMILAR_CHARACTERS = "iIlL1oO0uvcemnwWbdpqsS5"
        const val LEGACY_DEFAULT_SYMBOLS = "#*+,-.:<=>?@^_~"
    }
}
