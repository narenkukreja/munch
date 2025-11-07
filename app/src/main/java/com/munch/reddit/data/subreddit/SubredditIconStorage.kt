package com.munch.reddit.data.subreddit

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.subredditIconDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "subreddit_icons"
)

data class CachedSubredditIcon(
    val url: String?,
    val fetchedAtEpochMillis: Long
)

class SubredditIconStorage(
    context: Context,
    private val gson: Gson = Gson()
) {
    private val store = context.subredditIconDataStore
    private val cacheType = object : TypeToken<Map<String, CachedSubredditIcon>>() {}.type

    suspend fun read(): Map<String, CachedSubredditIcon> {
        val json = store.data
            .map { prefs -> prefs[CACHED_ICONS_JSON].orEmpty() }
            .first()
        if (json.isBlank()) return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, CachedSubredditIcon>>(json, cacheType)
        }.getOrElse { emptyMap() }
    }

    suspend fun write(cache: Map<String, CachedSubredditIcon>) {
        store.edit { prefs ->
            if (cache.isEmpty()) {
                prefs.remove(CACHED_ICONS_JSON)
            } else {
                prefs[CACHED_ICONS_JSON] = gson.toJson(cache)
            }
        }
    }

    companion object {
        private val CACHED_ICONS_JSON = stringPreferencesKey("cached_icons_json")
    }
}
