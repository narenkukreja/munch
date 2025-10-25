package com.munch.reddit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RedditMoreChildrenResponse(
    @SerializedName("json") val json: RedditMoreChildrenJson? = null
)

data class RedditMoreChildrenJson(
    @SerializedName("data") val data: RedditMoreChildrenData = RedditMoreChildrenData()
)

data class RedditMoreChildrenData(
    @SerializedName("things") val things: List<RedditCommentContainerDto> = emptyList()
)
