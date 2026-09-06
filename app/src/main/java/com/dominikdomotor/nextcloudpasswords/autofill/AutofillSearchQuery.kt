package com.dominikdomotor.nextcloudpasswords.autofill

import com.dominikdomotor.nextcloudpasswords.data.Domains

/** The search term the picker opens with: the site's name, or the app's when there is no domain. */
internal object AutofillSearchQuery {
    fun from(webDomain: String?, applicationName: String): String {
        if (webDomain.isNullOrBlank()) return applicationName
        val host = webDomain.substringAfter("://").substringBefore('/').substringBefore(':')
        return Domains.primaryLabel(host) ?: applicationName
    }
}
