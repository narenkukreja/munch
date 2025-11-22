package com.munch.reddit.data

import android.content.Context
import android.content.SharedPreferences
import com.munch.reddit.theme.FeedThemePreset
import com.munch.reddit.theme.PostCardStyle
import com.munch.reddit.theme.TextSizeDefaults

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

    var commentTextSize: Float
        get() {
            val stored = prefs.getFloat(KEY_COMMENT_TEXT_SIZE, Float.NaN)
            if (!stored.isNaN() && stored > 0f) {
                val clamped = TextSizeDefaults.clamp(stored)
                if (clamped != stored) {
                    commentTextSize = clamped
                }
                return clamped
            }
            val legacyScale = prefs.getFloat(KEY_TEXT_SCALE, Float.NaN)
            if (!legacyScale.isNaN() && legacyScale > 0f) {
                val converted = TextSizeDefaults.fromLegacyScale(legacyScale)
                commentTextSize = converted
                prefs.edit().remove(KEY_TEXT_SCALE).apply()
                return converted
            }
            return TextSizeDefaults.DefaultSizeSp
        }
        set(value) = prefs.edit().putFloat(KEY_COMMENT_TEXT_SIZE, TextSizeDefaults.clamp(value)).apply()

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
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_COMMENT_TEXT_SIZE = "comment_text_size"
        private const val KEY_READ_POST_IDS = "read_post_ids"
    }
}
