package com.munch.reddit.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.munch.reddit.data.AppPreferences
import com.munch.reddit.feature.feed.FeedTheme
import com.munch.reddit.theme.FeedThemePreset
import com.munch.reddit.feature.onboarding.SelectThemeScreen
import com.munch.reddit.ui.theme.MunchForRedditTheme

class SelectThemeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        val fromSettings = intent.getBooleanExtra("FROM_SETTINGS", false)

        setContent {
            val window = this@SelectThemeActivity.window
            val context = LocalContext.current
            val appPreferences = remember { AppPreferences(context) }
            var previewTextSize by rememberSaveable { mutableStateOf(appPreferences.commentTextSize) }

            MunchForRedditTheme(commentTextSize = previewTextSize) {
                val view = LocalView.current
                val colorScheme = MaterialTheme.colorScheme
                SideEffect {
                    WindowInsetsControllerCompat(window, view).apply {
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                    }
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()
                }

                val feedThemePreset = FeedThemePreset.fromId(appPreferences.selectedTheme)
                FeedTheme(feedThemePreset) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SelectThemeScreen(
                            initialThemeId = appPreferences.selectedTheme,
                            initialPostCardStyleId = appPreferences.selectedPostCardStyle,
                            initialTextSize = previewTextSize,
                            onSelectionSaved = { themeId, postCardStyleId, textSize ->
                                appPreferences.selectedTheme = themeId
                                appPreferences.selectedPostCardStyle = postCardStyleId
                                appPreferences.commentTextSize = textSize
                                previewTextSize = textSize
                                if (fromSettings) {
                                    finish()
                                } else {
                                    appPreferences.hasCompletedOnboarding = true
                                    val intent = Intent(this@SelectThemeActivity, FeedActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                }
                            },
                            onTextSizeChanged = { updatedSize ->
                                previewTextSize = updatedSize
                            },
                            onBack = if (fromSettings) {
                                { finish() }
                            } else null
                        )
                    }
                }
            }
        }
    }
}
