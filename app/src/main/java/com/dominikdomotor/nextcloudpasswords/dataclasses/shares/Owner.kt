package com.dominikdomotor.nextcloudpasswords.dataclasses.shares
import com.google.gson.annotations.SerializedName

data class Owner(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String
)