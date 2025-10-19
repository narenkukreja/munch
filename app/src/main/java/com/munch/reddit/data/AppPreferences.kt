package com.munch.reddit.data

import android.content.Context
import android.content.SharedPreferences
import com.munch.reddit.theme.FeedThemePreset

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

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SELECTED_THEME = "selected_theme"
    }
}