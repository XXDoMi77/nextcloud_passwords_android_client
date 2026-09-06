package com.dominikdomotor.nextcloudpasswords.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutofillTargetTest {
    @Test
    fun theHostWinsOverTheBrowserThatAsked() {
        val key = AutofillTarget.keyFor("accounts.example.com", "com.android.chrome")

        assertEquals("site:accounts.example.com", key)
    }

    @Test
    fun theSameSiteIsOneTargetHoweverItIsWritten() {
        val plain = AutofillTarget.keyFor("Accounts.Example.com.", "com.android.chrome")
        val messy = AutofillTarget.keyFor("  accounts.example.com  ", "org.mozilla.firefox")

        assertEquals(plain, messy)
    }

    @Test
    fun subdomainsAreSeparateTargets() {
        val accounts = AutofillTarget.keyFor("accounts.example.com", "com.android.chrome")
        val mail = AutofillTarget.keyFor("mail.example.com", "com.android.chrome")

        assertNotEquals(accounts, mail)
    }

    @Test
    fun anAppWithoutADomainIsKeyedByItsPackage() {
        val key = AutofillTarget.keyFor(null, "com.example.app")

        assertEquals("app:com.example.app", key)
    }

    /** Without the prefixes these two would share an entry and one would be handed the other's credential. */
    @Test
    fun aPackageNamedLikeASiteIsStillADifferentTarget() {
        val site = AutofillTarget.keyFor("example.com", "com.android.chrome")
        val app = AutofillTarget.keyFor(null, "example.com")

        assertNotEquals(site, app)
    }

    /** The settings screen reads a key back to name the row it stands for, so the two halves have to agree. */
    @Test
    fun aKeySaysWhichKindOfTargetItIs() {
        val site = AutofillTarget.keyFor("example.com", "com.android.chrome")!!
        val app = AutofillTarget.keyFor(null, "com.example.app")!!

        assertEquals("example.com", AutofillTarget.hostOf(site))
        assertNull(AutofillTarget.packageOf(site))
        assertEquals("com.example.app", AutofillTarget.packageOf(app))
        assertNull(AutofillTarget.hostOf(app))
    }

    @Test
    fun aRequestThatIdentifiesNothingHasNoKey() {
        assertNull(AutofillTarget.keyFor(null, null))
        assertNull(AutofillTarget.keyFor("  ", "  "))
    }
}
