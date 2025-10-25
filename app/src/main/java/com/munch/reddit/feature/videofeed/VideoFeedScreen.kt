package com.munch.reddit.feature.videofeed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.domain.model.RedditPostMedia
import com.munch.reddit.feature.feed.RedditFeedViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFeedRoute(
    navController: NavController,
    feedBackStackEntry: NavBackStackEntry
) {
    val viewModel: RedditFeedViewModel = koinViewModel(viewModelStoreOwner = feedBackStackEntry)
    val uiState by viewModel.uiState.collectAsState()

    // Filter only video posts
    val videoPosts = remember(uiState.posts) {
        uiState.posts.filter { post ->
            post.media is RedditPostMedia.Video
        }
    }
    var isMuted by remember { mutableStateOf(false) }

    BackHandler { navController.popBackStack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isMuted = !isMuted }) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.3f),
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        VideoFeedContent(
            videoPosts = videoPosts,
            isMuted = isMuted,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
fun VideoFeedContent(
    videoPosts: List<RedditPost>,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { videoPosts.size })

    // Create ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (isMuted) 0f else 1f
        }
    }

    // Create PlayerView once and reuse it
    val playerView = remember {
        PlayerView(context).apply {
            player = exoPlayer
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
    }

    // Update mute state
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Handle page changes and load appropriate video
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page < videoPosts.size && !pagerState.isScrollInProgress) {
                val post = videoPosts[page]
                val videoUrl = when (val media = post.media) {
                    is RedditPostMedia.Video -> media.url
                    else -> null
                }
                if (videoUrl != null) {
                    exoPlayer.apply {
                        stop()
                        clearMediaItems()
                        setMediaItem(MediaItem.fromUri(videoUrl))
                        prepare()
                        playWhenReady = true
                    }
                }
            }
        }
    }

    // Clean up player on dispose
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier,
        key = { videoPosts[it].id }
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Only show the player on the current page
            if (page == pagerState.currentPage) {
                AndroidView(
                    factory = { playerView },
                    update = { view ->
                        view.player = exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
