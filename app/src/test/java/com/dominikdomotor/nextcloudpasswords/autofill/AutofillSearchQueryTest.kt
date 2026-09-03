package com.dominikdomotor.nextcloudpasswords.autofill

import org.junit.Assert.assertEquals
import org.junit.Test

class AutofillSearchQueryTest {
    @Test
    fun extractsMainDomainLabel() {
        assertEquals("amazon", AutofillSearchQuery.from("https://www.amazon.com", "Edge"))
        assertEquals("amazon", AutofillSearchQuery.from("accounts.amazon.co.uk", "Edge"))
    }

    @Test
    fun usesApplicationNameWithoutDomain() {
        assertEquals("Example App", AutofillSearchQuery.from(null, "Example App"))
    }
}
