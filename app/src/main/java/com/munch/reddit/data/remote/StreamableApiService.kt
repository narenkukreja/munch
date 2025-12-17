package com.munch.reddit.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

interface StreamableApiService {
    @GET("videos/{shortcode}")
    suspend fun getVideo(@Path("shortcode") shortcode: String): StreamableVideoResponse
}

data class StreamableVideoResponse(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("embed_code") val embedCode: String? = null,
    @SerializedName("files") val files: Map<String, StreamableVideoFile>? = null
)

data class StreamableVideoFile(
    @SerializedName("url") val url: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("bitrate") val bitrate: Int? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("framerate") val framerate: Double? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("duration") val duration: Double? = null
)
