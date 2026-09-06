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
    @SerializedName("expandBottomSheet") var expandBottomSheet: Boolean = true,
    @SerializedName("animateSearchResults") var animateSearchResults: Boolean = true,
    @SerializedName("inlineAutofillSuggestions") var inlineAutofillSuggestions: Boolean = false,
    @SerializedName("autofillManualFallback") var autofillManualFallback: Boolean = false,
    @SerializedName("autofillBlockedApps") var autofillBlockedApps: List<String> = emptyList(),
    @SerializedName("autofillUsernameWords") var autofillUsernameWords: List<String> = HintWords.DEFAULT_USERNAME_WORDS,
    @SerializedName("autofillPasswordWords") var autofillPasswordWords: List<String> = HintWords.DEFAULT_PASSWORD_WORDS,
    @SerializedName("allowScreenshots") var allowScreenshots: Boolean = true,
    /** Whether a copied password is taken back out of the clipboard again. */
    @SerializedName("clearClipboard") var clearClipboard: Boolean = true,
    /** How long a copied password stays on the clipboard, in seconds. */
    @SerializedName("clipboardClearSeconds") var clipboardClearSeconds: Int = 30,
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
    /**
     * The server the in-flight login was started against, or empty when none is.
     *
     * Recorded so an inline `nc://login/...` callback can be checked against it. Kept in settings rather than in the
     * activity because the browser can take the activity down while the user authenticates.
     */
    @SerializedName("pendingLoginServer") var pendingLoginServer: String = "",
) {
    companion object {
        const val DEFAULT_SYMBOLS = "!@#$%&*._-"

        /** Characters that are easy to confuse with one another in most fonts. */
        const val DEFAULT_SIMILAR_CHARACTERS = "iIlL1oO0uvcemnwWbdpqsS5"
        const val LEGACY_DEFAULT_SYMBOLS = "#*+,-.:<=>?@^_~"
    }
}
