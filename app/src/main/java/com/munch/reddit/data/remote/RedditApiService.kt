package com.munch.reddit.data.remote

import com.google.gson.JsonElement
import com.munch.reddit.data.remote.dto.RedditListingResponse
import com.munch.reddit.data.remote.dto.RedditMoreChildrenResponse
import com.munch.reddit.data.remote.dto.SubredditAboutResponse
import com.munch.reddit.data.remote.dto.UserFlairV2ItemDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RedditApiService {
    @GET("{path}.json")
    suspend fun getListing(
        @Path(value = "path", encoded = true) path: String,
        @Query("limit") limit: Int = 50,
        @Query("sort") sort: String? = null,
        @Query("t") timeRange: String? = null,
        @Query("after") after: String? = null,
        @Query("raw_json") rawJson: Int = 1
    ): RedditListingResponse

    @GET("r/{subreddit}/about.json")
    suspend fun getSubredditAbout(
        @Path("subreddit") subreddit: String,
        @Query("raw_json") rawJson: Int = 1
    ): SubredditAboutResponse

    @GET("{path}.json")
    suspend fun getComments(
        @Path(value = "path", encoded = true) path: String,
        @Query("limit") limit: Int = 100,
        @Query("depth") depth: Int = 3,
        @Query("raw_json") rawJson: Int = 1,
        @Query("sort") sort: String? = null,
        @Query("after") after: String? = null,
        @Query("comment") comment: String? = null
    ): List<JsonElement>

    @GET("api/morechildren.json")
    suspend fun getMoreChildren(
        @Query("link_id") linkId: String,
        @Query(value = "children", encoded = true) childrenCsv: String,
        @Query("sort") sort: String? = null,
        @Query("depth") depth: Int = 3,
        @Query("raw_json") rawJson: Int = 1,
        @Query("api_type") apiType: String = "json"
    ): RedditMoreChildrenResponse

    @GET("search.json")
    suspend fun searchPosts(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
        @Query("sort") sort: String? = "relevance",
        @Query("t") timeRange: String? = null,
        @Query("after") after: String? = null,
        @Query("raw_json") rawJson: Int = 1
    ): RedditListingResponse

    @GET("r/{subreddit}/api/user_flair_v2.json")
    suspend fun getUserFlairV2(
        @Path("subreddit") subreddit: String,
        @Query("raw_json") rawJson: Int = 1
    ): List<UserFlairV2ItemDto>
}
