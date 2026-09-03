package com.dominikdomotor.nextcloudpasswords.data

/**
 * Host parsing shared by favicon lookup and autofill matching.
 *
 * [SECOND_LEVEL_SUFFIXES] is a short approximation of the public suffix list — enough to keep `example.co.uk` from
 * collapsing to `co.uk`, which is what the favicon downloader's old `takeLast(2)` did, giving every UK site the same
 * icon.
 */
object Domains {
    private val SECOND_LEVEL_SUFFIXES = setOf("ac", "co", "com", "edu", "gov", "net", "org")
    private val IP_ADDRESS = Regex("[0-9a-f:.]+", RegexOption.IGNORE_CASE)

    /** Normalises a host: lower-cased, trailing dot removed, blank becomes null. */
    fun normalizeHost(value: String?): String? = value?.trim()?.lowercase()?.trimEnd('.')?.takeIf(String::isNotBlank)

    /** The registrable domain, e.g. `example.co.uk` for `www.example.co.uk`. */
    fun registrableDomain(host: String?): String? {
        val normalized = normalizeHost(host) ?: return null
        if (normalized.matches(IP_ADDRESS)) return normalized

        val labels = normalized.split('.').filter(String::isNotBlank)
        if (labels.size < 3) return normalized
        val size = if (isSecondLevelSuffix(labels)) 3 else 2
        return labels.takeLast(size).joinToString(".")
    }

    /** The distinctive label of a host, e.g. `amazon` for `accounts.amazon.co.uk`. */
    fun primaryLabel(host: String?): String? {
        val normalized = normalizeHost(host) ?: return null
        if (normalized.matches(IP_ADDRESS)) return normalized

        val labels = normalized.split('.').filter(String::isNotBlank)
        if (labels.size < 2) return normalized
        val suffixSize = if (isSecondLevelSuffix(labels)) 2 else 1
        return labels.getOrNull(labels.size - suffixSize - 1) ?: labels.first()
    }

    private fun isSecondLevelSuffix(labels: List<String>): Boolean =
        labels.last().length == 2 && labels[labels.lastIndex - 1] in SECOND_LEVEL_SUFFIXES
}
