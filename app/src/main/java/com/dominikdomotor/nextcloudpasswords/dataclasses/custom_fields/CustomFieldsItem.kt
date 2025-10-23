package com.dominikdomotor.nextcloudpasswords.dataclasses.custom_fields

import com.google.gson.annotations.SerializedName

data class CustomFieldsItem(
    @SerializedName("label") val label: String,
    @SerializedName("type") val type: String,
    @SerializedName("value") val value: String
)
