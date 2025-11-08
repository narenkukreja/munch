package com.munch.reddit.data

import android.content.Context
import android.content.SharedPreferences
import com.munch.reddit.theme.FeedThemePreset
import com.munch.reddit.theme.PostCardStyle

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "wormi_preferences",
        Context.MODE_PRIVATE
    )

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var selectedTheme: String
        get() = prefs.getString(KEY_SELECTED_THEME, FeedThemePreset.Wormi.id) ?: FeedThemePreset.Wormi.id
        set(value) = prefs.edit().putString(KEY_SELECTED_THEME, value).apply()

    var selectedPostCardStyle: String
        get() = prefs.getString(KEY_SELECTED_POST_CARD_STYLE, PostCardStyle.CardV1.id)
            ?: PostCardStyle.CardV1.id
        set(value) = prefs.edit().putString(KEY_SELECTED_POST_CARD_STYLE, value).apply()

    // Read posts tracking
    fun getReadPostIds(): Set<String> =
        prefs.getStringSet(KEY_READ_POST_IDS, emptySet()) ?: emptySet()

    fun setReadPostIds(ids: Set<String>) {
        // SharedPreferences stores a mutable set reference; write a copy to avoid accidental mutation
        prefs.edit().putStringSet(KEY_READ_POST_IDS, ids.toSet()).apply()
    }

    fun markPostRead(id: String) {
        val current = getReadPostIds().toMutableSet()
        if (current.add(id)) {
            setReadPostIds(current)
        }
    }


    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SELECTED_THEME = "selected_theme"
        private const val KEY_SELECTED_POST_CARD_STYLE = "selected_post_card_style"
        private const val KEY_READ_POST_IDS = "read_post_ids"
    }
}
