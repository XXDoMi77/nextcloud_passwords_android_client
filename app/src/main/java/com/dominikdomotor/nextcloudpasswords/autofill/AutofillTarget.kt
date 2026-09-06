package com.dominikdomotor.nextcloudpasswords.autofill

import com.dominikdomotor.nextcloudpasswords.data.Domains

/**
 * What a fill request is *for*, reduced to one string.
 *
 * Two requests should be treated as the same target when what the user chose for one ought to be offered for the
 * other. For a browser that is the host, for anything else the package that asked. The host wins when both are
 * present, because a browser's own package would otherwise put every site the user ever visits under one key.
 */
internal object AutofillTarget {
    private const val SITE = "site:"
    private const val APP = "app:"

    /**
     * The key [AutofillLinkStore] files a remembered choice under, or null when the request identifies nothing.
     *
     * The prefixes are not decoration: without them a package named `example.com` and the site `example.com` would
     * share an entry, and one of the two would silently get the other's credential.
     */
    fun keyFor(webDomain: String?, requestingPackage: String?): String? {
        Domains.normalizeHost(webDomain)?.let {
            return SITE + it
        }
        return requestingPackage?.trim()?.takeIf(String::isNotBlank)?.let { APP + it.lowercase() }
    }

    /** The host a site key stands for, or null when [key] names an app. */
    fun hostOf(key: String): String? = key.takeIf { it.startsWith(SITE) }?.removePrefix(SITE)

    /** The package an app key stands for, or null when [key] names a site. */
    fun packageOf(key: String): String? = key.takeIf { it.startsWith(APP) }?.removePrefix(APP)
}
