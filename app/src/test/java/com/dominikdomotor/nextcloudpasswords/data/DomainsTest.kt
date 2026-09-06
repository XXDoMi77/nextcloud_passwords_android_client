package com.dominikdomotor.nextcloudpasswords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainsTest {
    @Test
    fun registrableDomainKeepsTwoLabelsForOrdinarySuffixes() {
        assertEquals("example.com", Domains.registrableDomain("www.example.com"))
        assertEquals("example.com", Domains.registrableDomain("accounts.login.example.com"))
    }

    /** The old `takeLast(2)` returned `co.uk`, so every UK site shared a single favicon. */
    @Test
    fun registrableDomainKeepsThreeLabelsForSecondLevelSuffixes() {
        assertEquals("example.co.uk", Domains.registrableDomain("www.example.co.uk"))
        assertEquals("bbc.co.uk", Domains.registrableDomain("news.bbc.co.uk"))
    }

    @Test
    fun registrableDomainLeavesShortHostsAlone() {
        assertEquals("example.com", Domains.registrableDomain("example.com"))
        assertEquals("localhost", Domains.registrableDomain("localhost"))
    }

    @Test
    fun registrableDomainPassesThroughIpAddresses() {
        assertEquals("192.168.1.10", Domains.registrableDomain("192.168.1.10"))
    }

    @Test
    fun registrableDomainNormalisesCaseAndTrailingDot() {
        assertEquals("example.com", Domains.registrableDomain("WWW.Example.COM."))
    }

    @Test
    fun registrableDomainRejectsBlankInput() {
        assertNull(Domains.registrableDomain(null))
        assertNull(Domains.registrableDomain("   "))
    }

    @Test
    fun primaryLabelPicksTheDistinctivePart() {
        assertEquals("amazon", Domains.primaryLabel("www.amazon.com"))
        assertEquals("amazon", Domains.primaryLabel("accounts.amazon.co.uk"))
        assertEquals("example", Domains.primaryLabel("example.com"))
    }

    @Test
    fun primaryLabelHandlesSingleLabelHosts() {
        assertEquals("localhost", Domains.primaryLabel("localhost"))
    }
}
