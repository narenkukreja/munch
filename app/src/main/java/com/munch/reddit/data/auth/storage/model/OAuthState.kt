package com.munch.reddit.data.auth.storage.model

data class OAuthState(
    val clientId: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtEpochMillis: Long? = null
) {
    val hasClientId: Boolean get() = clientId.isNotBlank()
    val isAuthorized: Boolean
        get() = accessToken.isNotBlank() && expiresAtEpochMillis?.let { it > System.currentTimeMillis() } == true
}
