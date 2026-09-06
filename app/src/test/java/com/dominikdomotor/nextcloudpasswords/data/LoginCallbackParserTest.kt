package com.dominikdomotor.nextcloudpasswords.data

import com.dominikdomotor.nextcloudpasswords.data.LoginCallbackParser.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // isSameOrigin is what stops a link on any page from pointing the app at someone else's Nextcloud. `nc://` is a
    // custom scheme with no assetlinks.json behind it, so the callback's server is only believed when it names the
    // server the login was actually started against.

    @Test
    fun theSameServerIsAccepted() {
        assertTrue(LoginCallbackParser.isSameOrigin("https://cloud.example.com", "https://cloud.example.com"))
    }

    @Test
    fun spellingsThatDoNotChangeWhereCredentialsGoAreAccepted() {
        val expected = "https://cloud.example.com"
        assertTrue(LoginCallbackParser.isSameOrigin(expected, "https://cloud.example.com/"))
        assertTrue(LoginCallbackParser.isSameOrigin(expected, "https://CLOUD.Example.COM"))
        assertTrue(LoginCallbackParser.isSameOrigin(expected, "https://cloud.example.com:443"))
        // A subdirectory install is the same server; whoever holds the host holds all of it anyway.
        assertTrue(LoginCallbackParser.isSameOrigin(expected, "https://cloud.example.com/nextcloud"))
    }

    @Test
    fun aDifferentHostIsRejected() {
        val expected = "https://cloud.example.com"
        assertFalse(LoginCallbackParser.isSameOrigin(expected, "https://evil.example"))
        assertFalse(LoginCallbackParser.isSameOrigin(expected, "https://cloud.example.com.evil.example"))
        assertFalse(LoginCallbackParser.isSameOrigin(expected, "https://evil.example/?x=cloud.example.com"))
    }

    @Test
    fun aDifferentSchemeOrPortIsRejected() {
        val expected = "https://cloud.example.com"
        assertFalse(LoginCallbackParser.isSameOrigin(expected, "http://cloud.example.com"))
        assertFalse(LoginCallbackParser.isSameOrigin(expected, "https://cloud.example.com:8443"))
    }

    /** No login in flight means nothing to match, and an unparseable side must never compare equal. */
    @Test
    fun anythingUnusableIsRejected() {
        assertFalse(LoginCallbackParser.isSameOrigin("", "https://cloud.example.com"))
        assertFalse(LoginCallbackParser.isSameOrigin("https://cloud.example.com", ""))
        assertFalse(LoginCallbackParser.isSameOrigin("", ""))
        assertFalse(LoginCallbackParser.isSameOrigin("not a url", "not a url"))
        assertFalse(LoginCallbackParser.isSameOrigin("https://cloud.example.com", "cloud.example.com"))
    }
}
