package com.dominikdomotor.nextcloudpasswords.dataclasses.shares

import com.google.gson.annotations.SerializedName

data class SharesItem(
    @SerializedName("client") val client: String,
    @SerializedName("created") val created: Int,
    @SerializedName("editable") val editable: Boolean,
    @SerializedName("expires") val expires: Any?,
    @SerializedName("id") val id: String,
    @SerializedName("owner") val owner: Owner,
    @SerializedName("password") val password: String,
    @SerializedName("receiver") val `receiver`: Receiver,
    @SerializedName("shareable") val shareable: Boolean,
    @SerializedName("updatePending") val updatePending: Boolean,
    @SerializedName("updated") val updated: Int
)
