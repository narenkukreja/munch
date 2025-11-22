package com.munch.reddit.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.munch.reddit.feature.feed.FeedTheme
import com.munch.reddit.theme.FeedThemePreset
import com.munch.reddit.feature.shared.ImagePreviewScreen
import com.munch.reddit.feature.shared.SwipeBackWrapper
import com.munch.reddit.ui.theme.MunchForRedditTheme

class ImagePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window for translucent effect and edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val imageUrl = intent.getStringExtra("IMAGE_URL") ?: run {
            finish()
            return
        }

        val imageGallery = intent.getStringArrayListExtra("IMAGE_GALLERY")
        val startIndex = intent.getIntExtra("IMAGE_GALLERY_START_INDEX", 0)
        val usesGalleryViewer = !imageGallery.isNullOrEmpty()

        setContent {
            val window = this@ImagePreviewActivity.window
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
                    val useSwipeBackWrapper = !usesGalleryViewer
                    val finishPreviewWithTransition: () -> Unit = {
                        finish()
                        overridePendingTransition(
                            com.munch.reddit.R.anim.slide_in_left,
                            com.munch.reddit.R.anim.slide_out_right
                        )
                    }
                    val finishPreviewAfterSwipe: () -> Unit = {
                        finish()
                        // Let the swipe translation handle the exit instead of the default animation
                        overridePendingTransition(0, 0)
                    }
                    val previewContent: @Composable () -> Unit = {
                        // Do not draw an opaque background at the root; allow reveal of the detail screen beneath
                        ImagePreviewScreen(
                            imageUrl = imageUrl,
                            imageGallery = imageGallery,
                            startIndex = startIndex,
                            onBackClick = finishPreviewWithTransition
                        )
                    }

                    if (useSwipeBackWrapper) {
                        SwipeBackWrapper(
                            onSwipeBackFinished = finishPreviewAfterSwipe,
                            modifier = Modifier.fillMaxSize(),
                            swipeThreshold = 0.4f,
                            edgeWidth = 50f
                        ) {
                            previewContent()
                        }
                    } else {
                        previewContent()
                    }
                }
            }
        }
    }
}
