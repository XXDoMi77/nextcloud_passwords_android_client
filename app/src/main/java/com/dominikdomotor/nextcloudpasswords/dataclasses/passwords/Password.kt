package com.dominikdomotor.nextcloudpasswords.dataclasses.passwords

import com.google.gson.annotations.SerializedName

data class Password(
    @SerializedName("client") val client: String = "",
    @SerializedName("created") val created: Int = 0,
    @SerializedName("cseKey") val cseKey: String = "",
    @SerializedName("cseType") val cseType: String = "",
    @SerializedName("customFields") var customFields: String = "",
    @SerializedName("editable") val editable: Boolean = false,
    @SerializedName("edited") val edited: Int = 0,
    @SerializedName("favorite") var favorite: Boolean = false,
    @SerializedName("folder") var folder: String = "",
    @SerializedName("hash") var hash: String = "",
    @SerializedName("hidden") val hidden: Boolean = false,
    @SerializedName("id") val id: String = "",
    @SerializedName("label") var label: String = "",
    @SerializedName("notes") var notes: String = "",
    @SerializedName("password") var password: String = "",
    @SerializedName("revision") val revision: String = "",
    @SerializedName("share") val share: String = "",
    @SerializedName("shared") val shared: Boolean = false,
    @SerializedName("sseType") val sseType: String = "",
    @SerializedName("status") val status: Int = 0,
    @SerializedName("statusCode") val statusCode: String = "",
    @SerializedName("trashed") val trashed: Boolean = false,
    @SerializedName("updated") val updated: Int = 0,
    @SerializedName("url") var url: String = "",
    @SerializedName("username") var username: String = ""
)
