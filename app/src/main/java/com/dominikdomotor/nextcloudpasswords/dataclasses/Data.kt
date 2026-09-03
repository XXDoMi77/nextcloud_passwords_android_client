package com.dominikdomotor.nextcloudpasswords.dataclasses

import com.dominikdomotor.nextcloudpasswords.dataclasses.folders.Folder
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Password
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.SharesItem

/** The complete on-disk state, serialised as a single encrypted JSON document. */
data class Data(
    var settings: Settings = Settings(),
    var passwords: List<Password> = emptyList(),
    var folders: List<Folder> = emptyList(),
    var shares: List<SharesItem> = emptyList(),
)
