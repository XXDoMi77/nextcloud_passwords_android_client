package com.dominikdomotor.nextcloudpasswords.autofill

object HintWords {
    val usernameHintList: List<String> =
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
            "usuário" // Brazilian Portuguese for "user"
            )
    val passwordHintList: List<String> =
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
            "senha", // Portuguese for "password"
            "κωδικός", // Greek for "password"
            "heslo", // Slovak for "password"
            "geslo", // Slovenian for "password"
            "parola", // Romanian for "password"
            "hasło", // Polish for "password"
            "senha" // Brazilian Portuguese for "password"
            )
}
