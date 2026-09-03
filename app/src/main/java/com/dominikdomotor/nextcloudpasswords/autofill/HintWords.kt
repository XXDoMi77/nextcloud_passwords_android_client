package com.dominikdomotor.nextcloudpasswords.autofill

/** The word lists that field detection falls back on when a form carries no usable autofill hint. */
object HintWords {
    val DEFAULT_USERNAME_WORDS: List<String> =
        listOf(
            "login",
            "user",
            "email",
            "e-mail",
            "mail",
            "versichertennummer", // German for insurance number
            "gebruiker", // Dutch for "user"
            "utilisateur", // French for "user"
            "utente", // Italian for "user"
            "benutzer", // German for "user"
            "usuario", // Spanish for "user"
            "naamgebruiker", // Dutch for "username"
            "användare", // Swedish for "user"
            "bruker", // Norwegian for "user"
            "utilizador", // Portuguese for "user"
            "χρήστης", // Greek for "user"
            "uživatel", // Czech for "user"
            "uporabnik", // Slovenian for "user"
            "utilizator", // Romanian for "user"
            "użytkownik", // Polish for "user"
            "usuário", // Brazilian Portuguese for "user"
        )
    val DEFAULT_PASSWORD_WORDS: List<String> =
        listOf(
            "pin",
            "password",
            "wachtwoord", // Dutch for "password"
            "motdepasse", // French for "password"
            "passwort", // German for "password"
            "kennwort", // German for "keyword"
            "jelszo", // Hungarian for "password"
            "mdp", // French acronym for "mot de passe" (password)
            "pwd", // Common acronym for "password"
            "clave", // Spanish for "password"
            "lösenord", // Swedish for "password"
            "passord", // Norwegian for "password"
            "senha", // Portuguese, and Brazilian Portuguese, for "password"
            "κωδικός", // Greek for "password"
            "heslo", // Slovak for "password"
            "geslo", // Slovenian for "password"
            "parola", // Romanian for "password"
            "hasło", // Polish for "password"
        )

    /** Splits a user-edited list. Commas, semicolons, whitespace and newlines all separate entries. */
    fun parse(text: String): List<String> =
        text.split(SEPARATORS).map(String::trim).filter(String::isNotEmpty).distinct()

    /** Formats a list for the editor: one word per line, so a long list stays readable. */
    fun format(words: List<String>): String = words.joinToString(LINE_BREAK)

    private val SEPARATORS = Regex("""[,;\s]+""")
    private const val LINE_BREAK = "\n"
}

/**
 * The word lists for one fill request, normalised once.
 *
 * [AutofillFieldAnalyzer] compares against these for every node in an assist structure, which can run to hundreds of
 * views, so stripping punctuation and case happens here rather than per comparison.
 */
internal class AutofillHintWords(usernameWords: List<String>, passwordWords: List<String>) {
    val username: List<String> = usernameWords.map(::normalize).filter(String::isNotEmpty)
    val password: List<String> = passwordWords.map(::normalize).filter(String::isNotEmpty)

    private companion object {
        fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)
    }
}
