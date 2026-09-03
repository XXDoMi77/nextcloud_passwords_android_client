package com.dominikdomotor.nextcloudpasswords.dataclasses.folders

import com.google.gson.annotations.SerializedName

data class Folder(
    @SerializedName("id") val id: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("parent") val parent: String = "",
    @SerializedName("revision") val revision: String = "",
    @SerializedName("cseKey") val cseKey: String = "",
    @SerializedName("cseType") val cseType: String = "none",
    @SerializedName("trashed") val trashed: Boolean = false,
) {
    companion object {
        const val ROOT_ID = "00000000-0000-0000-0000-000000000000"
    }
}

class Folders : ArrayList<Folder>()
