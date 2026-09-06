package com.dominikdomotor.nextcloudpasswords.dataclasses

import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.SharesItem
import com.google.gson.annotations.SerializedName

/** The complete on-disk state, serialised as a single encrypted JSON document. */
data class Data(
    /**
     * Which layout of this document was written, so a version that cannot read it can say so.
     *
     * Defaults to 0 rather than to the current number on purpose: a document written before this field existed has no
     * such key, Gson leaves the default in place, and 0 is then exactly the signal that it predates versioning.
     * `StorageManager` stamps the real number on every write.
     */
    @SerializedName("schemaVersion") var schemaVersion: Int = 0,
    var settings: Settings = Settings(),
    var passwords: List<Password> = emptyList(),
    var folders: List<Folder> = emptyList(),
    var shares: List<SharesItem> = emptyList(),
)
