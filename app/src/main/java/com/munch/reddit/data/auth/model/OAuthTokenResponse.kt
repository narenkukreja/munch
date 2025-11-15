package com.munch.reddit.data.auth.model

import com.google.gson.annotations.SerializedName

data class OAuthTokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresInSeconds: Long? = null,
    @SerializedName("scope") val scope: String? = null,
    @SerializedName("token_type") val tokenType: String? = null
)
