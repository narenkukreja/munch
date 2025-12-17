package com.munch.reddit.data.auth

import com.munch.reddit.data.auth.model.OAuthTokenResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface OAuthApiService {
    @FormUrlEncoded
    @POST("api/v1/access_token")
    suspend fun exchangeAuthorizationCode(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") codeVerifier: String
    ): OAuthTokenResponse

    @FormUrlEncoded
    @POST("api/v1/access_token")
    suspend fun refreshAccessToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): OAuthTokenResponse
}
