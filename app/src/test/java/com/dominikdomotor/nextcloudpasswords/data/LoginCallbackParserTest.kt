package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.data.LoginCallbackParser.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginCallbackParserTest {
    @Test
    fun bareCallbackIsJustAReturnSignal() {
        assertTrue(LoginCallbackParser.parse("nc://login") is Result.ReturnedToApp)
        assertTrue(LoginCallbackParser.parse("nc://login/") is Result.ReturnedToApp)
    }

    @Test
    fun unrelatedUrisAreNotCallbacks() {
        assertTrue(LoginCallbackParser.parse(null) is Result.NotACallback)
        assertTrue(LoginCallbackParser.parse("https://example.com") is Result.NotACallback)
        assertTrue(LoginCallbackParser.parse("ncx://login/") is Result.NotACallback)
    }

    @Test
    fun legacyFormCarriesCredentials() {
        val result = LoginCallbackParser.parse("nc://login/server:https://cloud.example.com&user:jane&password:s3cr3t")
        assertEquals(Result.Credentials("https://cloud.example.com", "jane", "s3cr3t"), result)
    }

    @Test
    fun percentAndPlusEncodingAreDecoded() {
        val result =
            LoginCallbackParser.parse("nc://login/server:https%3A%2F%2Fexample.com&user:jane+doe&password:a%20b")
        assertEquals(Result.Credentials("https://example.com", "jane doe", "a b"), result)
    }

    /** `substring(0, -1)` on a part with no colon used to throw and crash the callback. */
    @Test
    fun malformedPartsAreSkippedRatherThanCrashing() {
        val result = LoginCallbackParser.parse("nc://login/garbage&server:https://x&user:u&password:p")
        assertEquals(Result.Credentials("https://x", "u", "p"), result)
    }

    @Test
    fun missingFieldsFallBackToTheReturnSignal() {
        assertTrue(LoginCallbackParser.parse("nc://login/server:https://x&user:u") is Result.ReturnedToApp)
        assertTrue(LoginCallbackParser.parse("nc://login/nonsense") is Result.ReturnedToApp)
    }

    @Test
    fun blankValuesAreNotAcceptedAsCredentials() {
        assertTrue(LoginCallbackParser.parse("nc://login/server:&user:u&password:p") is Result.ReturnedToApp)
    }
}
