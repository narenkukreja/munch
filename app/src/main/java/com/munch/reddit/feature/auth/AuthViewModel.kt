package com.munch.reddit.feature.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.munch.reddit.data.auth.OAuthTokenManager
import com.munch.reddit.data.auth.storage.model.OAuthState
import com.munch.reddit.data.auth.util.PkceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val AUTHORIZATION_URL = "https://www.reddit.com/api/v1/authorize.compact"
private const val REDIRECT_URI = "com.munch.reddit://oauth"
private const val REQUESTED_SCOPES = "read"

data class AuthUiState(
    val clientIdInput: String = "",
    val storedClientId: String = "",
    val isAuthorized: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val shouldShowAuth: Boolean get() = !isAuthorized
}

data class AuthorizationRequest(
    val uri: Uri
)

private data class PendingAuthorization(
    val clientId: String,
    val codeVerifier: String,
    val state: String
)

class AuthViewModel(
    private val tokenManager: OAuthTokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var pendingAuthorization: PendingAuthorization? = null

    init {
        viewModelScope.launch {
            tokenManager.authState.collect { state ->
                handleAuthState(state)
            }
        }
    }

    fun onClientIdChanged(value: String) {
        _uiState.update { it.copy(clientIdInput = value, errorMessage = null) }
    }

    fun prepareAuthorization(): AuthorizationRequest? {
        val clientId = _uiState.value.clientIdInput.trim()
        if (clientId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Client ID is required") }
            return null
        }

        val codeVerifier = PkceUtils.generateCodeVerifier()
        val codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier)
        val state = PkceUtils.generateState()
        pendingAuthorization = PendingAuthorization(clientId, codeVerifier, state)

        viewModelScope.launch {
            tokenManager.saveClientId(clientId)
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }

        val authorizationUri = Uri.parse(AUTHORIZATION_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", state)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("duration", "permanent")
            .appendQueryParameter("scope", REQUESTED_SCOPES)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        return AuthorizationRequest(uri = authorizationUri)
    }

    fun handleAuthorizationRedirect(uri: Uri) {
        val pending = pendingAuthorization ?: return
        if (uri.toString().startsWith(REDIRECT_URI).not()) {
            return
        }
        val returnedState = uri.getQueryParameter("state")
        if (returnedState.isNullOrBlank() || returnedState != pending.state) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Authorization cancelled or mismatched state") }
            pendingAuthorization = null
            return
        }

        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Authorization error: $error") }
            pendingAuthorization = null
            return
        }

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Authorization code missing") }
            pendingAuthorization = null
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val success = tokenManager.exchangeAuthorizationCode(
                clientId = pending.clientId,
                code = code,
                codeVerifier = pending.codeVerifier,
                redirectUri = REDIRECT_URI
            )
            if (!success) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to obtain access token") }
            }
            pendingAuthorization = null
        }
    }

    fun resetError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun handleAuthState(state: OAuthState) {
        _uiState.update { current ->
            current.copy(
                storedClientId = state.clientId,
                clientIdInput = if (current.clientIdInput.isBlank() && state.clientId.isNotBlank()) state.clientId else current.clientIdInput,
                isAuthorized = state.isAuthorized,
                isLoading = current.isLoading && !state.isAuthorized
            )
        }
    }
}
