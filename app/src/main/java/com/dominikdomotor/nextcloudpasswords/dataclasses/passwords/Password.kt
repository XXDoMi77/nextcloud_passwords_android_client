package com.dominikdomotor.nextcloudpasswords.dataclasses.passwords

import com.google.gson.annotations.SerializedName

data class Password(
    @SerializedName("client") val client: String = "",
    @SerializedName("created") val created: Int = 0,
    @SerializedName("cseKey") val cseKey: String = "",
    @SerializedName("cseType") val cseType: String = "none",
    @SerializedName("customFields") val customFields: String = "",
    @SerializedName("editable") val editable: Boolean = false,
    @SerializedName("edited") val edited: Int = 0,
    @SerializedName("favorite") val favorite: Boolean = false,
    @SerializedName("folder") val folder: String = "",
    @SerializedName("hash") val hash: String = "",
    @SerializedName("hidden") val hidden: Boolean = false,
    @SerializedName("id") val id: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("notes") val notes: String = "",
    @SerializedName("password") val password: String = "",
    @SerializedName("revision") val revision: String = "",
    @SerializedName("share") val share: String? = null,
    @SerializedName("shared") val shared: Boolean = false,
    @SerializedName("sseType") val sseType: String = "",
    @SerializedName("status") val status: Int = 0,
    @SerializedName("statusCode") val statusCode: String = "",
    @SerializedName("trashed") val trashed: Boolean = false,
    @SerializedName("updated") val updated: Int = 0,
    @SerializedName("url") val url: String = "",
    @SerializedName("username") val username: String = "",
)
