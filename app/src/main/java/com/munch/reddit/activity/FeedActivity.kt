package com.munch.reddit.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.munch.reddit.R
import com.munch.reddit.data.AppPreferences
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.feature.feed.FeedTheme
import com.munch.reddit.theme.FeedThemePreset
import com.munch.reddit.theme.PostCardStyle
import com.munch.reddit.feature.feed.RedditFeedViewModel
import com.munch.reddit.feature.feed.RedditFeedScreen
import com.munch.reddit.ui.theme.MunchForRedditTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

class FeedActivity : ComponentActivity() {
    private var intentSubreddit by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        // Get the selected subreddit from intent if provided
        intentSubreddit = intent.getStringExtra("SELECTED_SUBREDDIT")

        setContent {
            val window = this@FeedActivity.window
            val context = LocalContext.current
            val appPreferences = remember { AppPreferences(context) }
            var feedThemeId by remember { mutableStateOf(appPreferences.selectedTheme) }
            val feedThemePreset = remember(feedThemeId) { FeedThemePreset.fromId(feedThemeId) }
            val savedStateHandle = remember { SavedStateHandle() }
            val viewModel: RedditFeedViewModel = koinViewModel(parameters = { parametersOf(savedStateHandle) })
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner, appPreferences) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        val updatedThemeId = appPreferences.selectedTheme
                        if (updatedThemeId != feedThemeId) {
                            feedThemeId = updatedThemeId
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Select the subreddit if provided in intent
            LaunchedEffect(intentSubreddit) {
                intentSubreddit?.let {
                    viewModel.selectSubreddit(it)
                }
            }

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
                        val uiState by viewModel.uiState.collectAsState()

                        // Activity result launcher for PostDetailActivity
                        val postDetailLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            if (result.resultCode == Activity.RESULT_OK) {
                                result.data?.getStringExtra("SELECTED_SUBREDDIT")?.let { subreddit ->
                                    viewModel.selectSubreddit(subreddit)
                                }
                            }
                        }

                        RedditFeedScreen(
                            uiState = uiState,
                            subredditOptions = viewModel.subredditOptions,
                            sortOptions = viewModel.sortOptions,
                            topTimeRangeOptions = viewModel.topTimeRangeOptions,
                            onSelectSubreddit = { viewModel.selectSubreddit(it) },
                            onSelectSort = { viewModel.selectSort(it) },
                            onSelectTopTimeRange = { viewModel.selectTopTimeRange(it) },
                            onPostSelected = { post ->
                                viewModel.markPostRead(post.id)
                                val intent = Intent(this@FeedActivity, PostDetailActivity::class.java)
                                intent.putExtra("PERMALINK", post.permalink)
                                postDetailLauncher.launch(intent)
                                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                            },
                            onRetry = viewModel::refresh,
                            onTitleTapped = {},
                            onSearchClick = {
                                val intent = Intent(this@FeedActivity, SearchActivity::class.java)
                                startActivity(intent)
                            },
                            onSettingsClick = {
                                val intent = Intent(this@FeedActivity, SettingsActivity::class.java)
                                startActivity(intent)
                            },
                            onImageClick = { imageUrl ->
                                val intent = Intent(this@FeedActivity, ImagePreviewActivity::class.java)
                                intent.putExtra("IMAGE_URL", imageUrl)
                                startActivity(intent)
                            },
                            onGalleryPreview = { urls, index ->
                                val intent = Intent(this@FeedActivity, ImagePreviewActivity::class.java)
                                intent.putExtra("IMAGE_URL", urls.getOrNull(index) ?: urls.firstOrNull())
                                intent.putStringArrayListExtra("IMAGE_GALLERY", ArrayList(urls))
                                intent.putExtra("IMAGE_GALLERY_START_INDEX", index)
                                startActivity(intent)
                            },
                            onYouTubeSelected = { videoId ->
                                val intent = Intent(this@FeedActivity, YouTubePlayerActivity::class.java)
                                intent.putExtra("VIDEO_ID", videoId)
                                startActivity(intent)
                            },
                            onVideoFeedClick = {
                                val intent = Intent(this@FeedActivity, VideoFeedActivity::class.java)
                                startActivity(intent)
                            },
                            onLoadMore = viewModel::loadMore,
                            isAppending = uiState.isAppending,
                            canLoadMore = uiState.hasMore,
                            viewModel = viewModel,
                            onPostDismissed = { post -> viewModel.dismissPost(post.id) },
                            onSubredditFromPostClick = { subreddit ->
                                val intent = Intent(this@FeedActivity, FeedActivity::class.java)
                                intent.putExtra("SELECTED_SUBREDDIT", subreddit)
                                startActivity(intent)
                                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh theme when returning from settings
        val context = this
        val appPreferences = AppPreferences(context)
        // Theme will be updated through the state in setContent
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Update the intentSubreddit state to trigger LaunchedEffect
        intentSubreddit = intent.getStringExtra("SELECTED_SUBREDDIT")
    }
}
