package com.munch.reddit.data.auth.network

import okhttp3.Interceptor
import okhttp3.Response

private const val OAUTH_HOST = "oauth.reddit.com"

class OAuthHostInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val authHeader = request.header("Authorization")
        if (authHeader.isNullOrBlank()) {
            return chain.proceed(request)
        }
        val currentUrl = request.url
        if (currentUrl.host == OAUTH_HOST) {
            return chain.proceed(request)
        }
        val redirectedUrl = currentUrl.newBuilder()
            .scheme("https")
            .host(OAUTH_HOST)
            .build()
        val updatedRequest = request.newBuilder()
            .url(redirectedUrl)
            .build()
        return chain.proceed(updatedRequest)
    }
}
