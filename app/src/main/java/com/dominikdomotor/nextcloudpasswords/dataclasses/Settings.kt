package com.dominikdomotor.nextcloudpasswords.dataclasses

import com.google.gson.annotations.SerializedName

data class Settings(
    @SerializedName("loggedIn") var loggedIn: Boolean = false,
    @SerializedName("server") var server: String = "",
    @SerializedName("username") var username: String = "",
    @SerializedName("token") var token: String = "",
    @SerializedName("basicAuth") var basicAuth: String = "",
    @SerializedName("passwordLength") var passwordLength: Int = 20,
    @SerializedName("includeSymbols") var includedSymbolsQuantity: Int = 1,
    @SerializedName("excludeSimilarCharacters") var excludeSimilarCharacters: Boolean = true,
)
