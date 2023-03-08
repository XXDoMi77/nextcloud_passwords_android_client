package com.dominikdomotor.nextcloudpasswords.ui.dataclasses


import com.google.gson.annotations.SerializedName

data class ChallengeContainer(
    @SerializedName("challenge")
    val challenge: Challenge
)