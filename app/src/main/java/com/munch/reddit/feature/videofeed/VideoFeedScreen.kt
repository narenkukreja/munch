package com.munch.reddit.feature.videofeed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay

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
            onLoadMore = { viewModel.loadMore() },
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
    onLoadMore: () -> Unit = {},
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

    // Tap overlay state for play/pause hint
    var overlayVisible by remember { mutableStateOf(false) }
    var overlayShowPlayIcon by remember { mutableStateOf(false) }
    var overlayTapSeq by remember { mutableStateOf(0) }

    // Update mute state
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Handle page changes and load the corresponding video
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
            if (page < videoPosts.size) {
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

    // Auto-hide overlay after a brief moment
    LaunchedEffect(overlayTapSeq) {
        if (overlayTapSeq > 0) {
            overlayVisible = true
            delay(650)
            overlayVisible = false
        }
    }

    // Trigger loading more posts when nearing the end
    LaunchedEffect(pagerState, videoPosts.size) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val threshold = (videoPosts.size - 3).coerceAtMost(videoPosts.size - 1)
                if (videoPosts.isEmpty() || page >= threshold) {
                    onLoadMore()
                }
            }
    }

    // Clean up player on dispose
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    if (videoPosts.isEmpty()) {
        // Nothing to show yet; request more and render a blank screen
        LaunchedEffect(Unit) { onLoadMore() }
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {}
    } else {
        VerticalPager(
            state = pagerState,
            modifier = modifier,
            key = { videoPosts[it].id }
        ) { page ->
            val post = videoPosts[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Only show the player on the current page
                if (page == pagerState.currentPage) {
                    AndroidView(
                        factory = {
                            PlayerView(context).apply {
                                player = exoPlayer
                                useController = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = { view ->
                            view.player = exoPlayer
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay click target and icon hint
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = interaction, indication = null) {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                    overlayShowPlayIcon = true
                                } else {
                                    exoPlayer.playWhenReady = true
                                    exoPlayer.play()
                                    overlayShowPlayIcon = false
                                }
                                overlayTapSeq += 1
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedVisibility(visible = overlayVisible, enter = fadeIn(), exit = fadeOut()) {
                            Icon(
                                imageVector = if (overlayShowPlayIcon) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                        // Bottom overlay with post title
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxSize()
                        ) {
                            // Gradient background at the bottom for readability
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.55f)
                                            )
                                        )
                                    )
                            )
                            Text(
                                text = post.title,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
