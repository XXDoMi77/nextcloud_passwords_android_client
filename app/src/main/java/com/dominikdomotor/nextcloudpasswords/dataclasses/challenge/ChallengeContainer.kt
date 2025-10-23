package com.dominikdomotor.nextcloudpasswords.dataclasses.challenge

import com.google.gson.annotations.SerializedName

data class ChallengeContainer(@SerializedName("challenge") val challenge: Challenge)
