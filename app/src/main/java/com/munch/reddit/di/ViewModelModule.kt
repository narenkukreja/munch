package com.munch.reddit.di

import com.munch.reddit.feature.auth.AuthViewModel
import com.munch.reddit.feature.detail.PostDetailViewModel
import com.munch.reddit.feature.feed.RedditFeedViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::AuthViewModel)
    viewModelOf(::RedditFeedViewModel)
    viewModel { (permalink: String) -> PostDetailViewModel(permalink, get(), get()) }
}
