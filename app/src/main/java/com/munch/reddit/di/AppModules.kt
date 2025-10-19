package com.munch.reddit.di

import android.content.Context
import com.munch.reddit.data.auth.OAuthApiService
import com.munch.reddit.data.auth.OAuthCallbackHandler
import com.munch.reddit.data.auth.OAuthRepository
import com.munch.reddit.data.auth.OAuthTokenManager
import com.munch.reddit.data.auth.network.OAuthAccessTokenInterceptor
import com.munch.reddit.data.auth.network.OAuthHostInterceptor
import com.munch.reddit.data.auth.network.OAuthTokenAuthenticator
import com.munch.reddit.data.auth.storage.OAuthStorage
import com.munch.reddit.data.remote.RedditApiService
import com.munch.reddit.data.repository.RedditRepositoryImpl
import com.munch.reddit.data.repository.RedditRepository
import com.munch.reddit.data.subreddit.SubredditIconStorage
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val API_BASE_URL = "https://www.reddit.com/"
private const val AUTH_BASE_URL = "https://www.reddit.com/"
private const val USER_AGENT = "MunchForReddit/1.0 (by u/anon)"

private fun userAgentInterceptor(): Interceptor = Interceptor { chain ->
    val request = chain.request()
        .newBuilder()
        .header("User-Agent", USER_AGENT)
        .build()
    chain.proceed(request)
}

val authModule = module {
    single { provideOAuthStorage(androidContext()) }
    single { OAuthCallbackHandler() }

    single {
        Retrofit.Builder()
            .baseUrl(AUTH_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(userAgentInterceptor())
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OAuthApiService::class.java)
    }

    single { OAuthRepository(api = get(), storage = get()) }
    single { OAuthTokenManager(repository = get()) }
}

val networkModule = module {
    single {
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor())
            .addInterceptor(OAuthAccessTokenInterceptor(tokenManager = get()))
            .addInterceptor(OAuthHostInterceptor())
            .authenticator(OAuthTokenAuthenticator(tokenManager = get()))
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    single<RedditApiService> {
        get<Retrofit>().create(RedditApiService::class.java)
    }
}

val repositoryModule = module {
    single { provideSubredditIconStorage(androidContext()) }
    single<RedditRepository> { RedditRepositoryImpl(get(), get()) }
    single { com.munch.reddit.data.repository.SubredditRepository(get()) }
}

private fun provideOAuthStorage(context: Context): OAuthStorage = OAuthStorage(context)
private fun provideSubredditIconStorage(context: Context): SubredditIconStorage = SubredditIconStorage(context)
