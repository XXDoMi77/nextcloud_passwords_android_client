package com.dominikdomotor.nextcloudpasswords.ui.dataclasses


import com.google.gson.annotations.SerializedName

data class Challenge(
    @SerializedName("salts")
    val salts: List<String>,
    @SerializedName("type")
    val type: String
)