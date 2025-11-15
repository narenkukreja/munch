package com.munch.reddit.data.auth.network

import com.munch.reddit.data.auth.OAuthTokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class OAuthAccessTokenInterceptor(
    private val tokenManager: OAuthTokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val accessToken = runBlocking { tokenManager.getValidAccessToken() }
        val updated = accessToken?.let {
            request.newBuilder()
                .header("Authorization", "Bearer $it")
                .build()
        } ?: request
        return chain.proceed(updated)
    }
}
