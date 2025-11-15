package com.munch.reddit.data.auth.network

import com.munch.reddit.data.auth.OAuthTokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class OAuthTokenAuthenticator(
    private val tokenManager: OAuthTokenManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization").isNullOrBlank()) {
            return null
        }

        val refreshedToken = runBlocking { tokenManager.forceRefresh() } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $refreshedToken")
            .build()
    }
}
