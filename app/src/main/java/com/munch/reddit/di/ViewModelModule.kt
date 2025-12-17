package com.munch.reddit.di

import androidx.lifecycle.SavedStateHandle
import com.munch.reddit.feature.auth.AuthViewModel
import com.munch.reddit.feature.detail.PostDetailViewModel
import com.munch.reddit.feature.feed.RedditFeedViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::AuthViewModel)
    viewModel { (handle: SavedStateHandle) ->
        RedditFeedViewModel(
            savedStateHandle = handle,
            repository = get(),
            subredditRepository = get(),
            appPreferences = get()
        )
    }
    viewModel { (permalink: String) -> PostDetailViewModel(permalink, get(), get(), get()) }
}
