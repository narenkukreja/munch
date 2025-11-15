package com.munch.reddit.data.auth

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class OAuthCallbackHandler {
    private val _callbacks = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val callbacks: SharedFlow<Uri> = _callbacks

    fun notify(uri: Uri) {
        _callbacks.tryEmit(uri)
    }
}
