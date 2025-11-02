package com.munch.reddit.data.auth.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.munch.reddit.data.auth.storage.model.OAuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.oauthDataStore: DataStore<Preferences> by preferencesDataStore(name = "oauth_state")

class OAuthStorage(
    context: Context
) {
    private val store = context.oauthDataStore

    val state: Flow<OAuthState> = store.data.map { preferences ->
        OAuthState(
            clientId = preferences[CLIENT_ID].orEmpty(),
            accessToken = preferences[ACCESS_TOKEN].orEmpty(),
            refreshToken = preferences[REFRESH_TOKEN].orEmpty(),
            expiresAtEpochMillis = preferences[EXPIRES_AT]
        )
    }

    suspend fun persistClientId(clientId: String) {
        store.edit { prefs ->
            prefs[CLIENT_ID] = clientId.trim()
        }
    }

    suspend fun persistTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAtEpochMillis: Long
    ) {
        store.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) {
                prefs[REFRESH_TOKEN] = refreshToken
            }
            prefs[EXPIRES_AT] = expiresAtEpochMillis
        }
    }

    suspend fun clearTokens() {
        store.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
            prefs.remove(EXPIRES_AT)
        }
    }

    suspend fun clearAll() {
        store.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        private val CLIENT_ID = stringPreferencesKey("client_id")
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val EXPIRES_AT = longPreferencesKey("expires_at_epoch_millis")
    }
}
