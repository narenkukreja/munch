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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.munch.reddit.data.AppPreferences
import com.munch.reddit.feature.detail.PostDetailActivityContent
import com.munch.reddit.feature.feed.FeedTheme
import com.munch.reddit.theme.FeedThemePreset
import com.munch.reddit.ui.theme.MunchForRedditTheme

class PostDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        val permalink = intent.getStringExtra("PERMALINK") ?: run {
            finish()
            return
        }

        setContent {
            val window = this@PostDetailActivity.window
            val context = LocalContext.current
            val appPreferences = remember { AppPreferences(context) }
            var feedThemeId by remember { mutableStateOf(appPreferences.selectedTheme) }
            val feedThemePreset = remember(feedThemeId) { FeedThemePreset.fromId(feedThemeId) }

            MunchForRedditTheme {
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

                FeedTheme(feedThemePreset) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PostDetailActivityContent(
                            permalink = permalink,
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }
}
