package com.munch.reddit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FlairRichTextDto(
    @SerializedName("e") val type: String? = null, // "emoji" or "text"
    @SerializedName("t") val text: String? = null,
    @SerializedName("a") val alias: String? = null,
    @SerializedName("u") val url: String? = null
)

data class UserFlairV2ItemDto(
    @SerializedName("text") val text: String? = null,
    @SerializedName("richtext") val richtext: List<FlairRichTextDto>? = null
)

