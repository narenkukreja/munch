package com.munch.reddit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SubredditAboutResponse(
    @SerializedName("data") val data: SubredditAboutData = SubredditAboutData()
)

data class SubredditAboutData(
    @SerializedName("display_name") val displayName: String = "",
    @SerializedName("display_name_prefixed") val displayNamePrefixed: String = "",
    @SerializedName("icon_img") val iconImg: String? = null,
    @SerializedName("header_img") val headerImg: String? = null
)
