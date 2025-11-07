package com.munch.reddit.data.auth

import com.munch.reddit.data.auth.model.OAuthTokenResponse
import com.munch.reddit.data.auth.storage.OAuthStorage
import com.munch.reddit.data.auth.storage.model.OAuthState
import kotlinx.coroutines.flow.Flow
import okhttp3.Credentials

class OAuthRepository(
    private val api: OAuthApiService,
    private val storage: OAuthStorage
) {

    val state: Flow<OAuthState> = storage.state

    suspend fun saveClientId(clientId: String) {
        storage.persistClientId(clientId)
    }

    suspend fun clearAll() {
        storage.clearAll()
    }

    suspend fun persistTokens(
        tokens: OAuthTokenResponse,
        clientId: String
    ) {
        val expiresInMillis = ((tokens.expiresInSeconds ?: 0L) * 1_000L).coerceAtLeast(0L)
        val expiresAt = System.currentTimeMillis() + expiresInMillis
        storage.persistClientId(clientId)
        storage.persistTokens(
            accessToken = tokens.accessToken.orEmpty(),
            refreshToken = tokens.refreshToken,
            expiresAtEpochMillis = expiresAt
        )
    }

    suspend fun exchangeAuthorizationCode(
        clientId: String,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OAuthTokenResponse {
        val authHeader = Credentials.basic(clientId, "")
        return api.exchangeAuthorizationCode(
            authorization = authHeader,
            code = code,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier
        )
    }

    suspend fun refreshAccessToken(
        clientId: String,
        refreshToken: String
    ): OAuthTokenResponse {
        val authHeader = Credentials.basic(clientId, "")
        return api.refreshAccessToken(
            authorization = authHeader,
            refreshToken = refreshToken
        )
    }
}
