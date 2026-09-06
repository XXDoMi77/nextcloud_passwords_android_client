package com.dominikdomotor.nextcloudpasswords.managers

/** File names used by [EncryptedFileManager]. */
object Keys {
    const val NOT_FOUND: String = "not_found"
    const val LOG_TAG: String = "Nextcloud Passwords"
    const val DATA: String = "data"
    /**
     * Per-favicon cache directory.
     *
     * Deliberately not "favicons": that name is taken by the pre-Preview-10 single-blob cache, and a directory cannot
     * be created where a regular file already exists (ENOTDIR).
     */
    const val FAVICON_DIRECTORY: String = "favicon_cache"

    /** The single-blob favicon cache this replaced; read once, then deleted. */
    const val LEGACY_FAVICON_BLOB: String = "favicons"

    /** The entry the user picked by hand for each site or app, kept out of [DATA] so the two cannot clobber. */
    const val AUTOFILL_LINKS: String = "autofill_links"
}
