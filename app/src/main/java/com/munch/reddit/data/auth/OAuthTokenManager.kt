package com.munch.reddit.data.auth

import com.munch.reddit.data.auth.storage.model.OAuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OAuthTokenManager(
    private val repository: OAuthRepository
) {

    private val mutex = Mutex()

    val authState: Flow<OAuthState> = repository.state

    suspend fun getValidAccessToken(): String? = mutex.withLock {
        val currentState = repository.state.first()
        if (!currentState.hasClientId) return null
        val now = System.currentTimeMillis()
        if (currentState.isAuthorized && currentState.expiresAtEpochMillis?.let { it - now } ?: 0L > REFRESH_WINDOW_MS) {
            return currentState.accessToken
        }
        // Access token missing or close to expiry, attempt refresh if possible
        val refreshToken = currentState.refreshToken.takeIf { it.isNotBlank() }
        if (refreshToken != null) {
            val response = repository.refreshAccessToken(
                clientId = currentState.clientId,
                refreshToken = refreshToken
            )
            if (!response.accessToken.isNullOrBlank()) {
                repository.persistTokens(response, currentState.clientId)
                val refreshed = repository.state.first()
                return refreshed.accessToken.takeIf { it.isNotBlank() }
            }
        }
        // If refresh token unavailable, no valid access token
        return currentState.accessToken.takeIf { it.isNotBlank() }
    }

    suspend fun forceRefresh(): String? = mutex.withLock {
        val currentState = repository.state.first()
        val refreshToken = currentState.refreshToken.takeIf { it.isNotBlank() } ?: return null
        val response = repository.refreshAccessToken(
            clientId = currentState.clientId,
            refreshToken = refreshToken
        )
        if (!response.accessToken.isNullOrBlank()) {
            repository.persistTokens(response, currentState.clientId)
        }
        return repository.state.first().accessToken.takeIf { it.isNotBlank() }
    }

    suspend fun persistAuthorizationResult(
        clientId: String,
        tokens: com.munch.reddit.data.auth.model.OAuthTokenResponse
    ) = mutex.withLock {
        repository.persistTokens(tokens, clientId)
    }

    suspend fun exchangeAuthorizationCode(
        clientId: String,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): Boolean = mutex.withLock {
        val response = repository.exchangeAuthorizationCode(
            clientId = clientId,
            code = code,
            codeVerifier = codeVerifier,
            redirectUri = redirectUri
        )
        if (response.accessToken.isNullOrBlank()) {
            return false
        }
        repository.persistTokens(response, clientId)
        true
    }

    suspend fun saveClientId(clientId: String) = mutex.withLock {
        repository.saveClientId(clientId)
    }

    suspend fun clear() = mutex.withLock {
        repository.clearAll()
    }

    companion object {
        private const val REFRESH_WINDOW_MS = 60_000L // refresh if less than 60s remaining
    }
}
