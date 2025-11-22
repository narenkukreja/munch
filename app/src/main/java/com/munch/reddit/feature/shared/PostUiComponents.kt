package com.munch.reddit.feature.shared

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.ui.res.vectorResource
import com.munch.reddit.R
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.Role
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.munch.reddit.domain.model.RedditPostMedia
import com.munch.reddit.feature.feed.MetaInfoColor
import com.munch.reddit.feature.feed.PostBackgroundColor
import com.munch.reddit.feature.feed.SpacerBackgroundColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.munch.reddit.ui.theme.MaterialSpacing

@Composable
fun RedditPostMediaContent(
    media: RedditPostMedia,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    onGalleryClick: ((List<String>, Int) -> Unit)? = null,
    onYoutubeClick: ((String, String) -> Unit)? = null
) {
    when (media) {
        RedditPostMedia.None -> Unit
        is RedditPostMedia.Image -> RedditPostImage(media, modifier, onImageClick)
        is RedditPostMedia.Video -> RedditPostVideo(media, modifier)
        is RedditPostMedia.Link -> RedditPostLink(media, modifier, onLinkClick)
        is RedditPostMedia.Gallery -> RedditPostGallery(media, modifier, onImageClick, onGalleryClick)
        is RedditPostMedia.YouTube -> RedditPostYouTube(media, modifier, onYoutubeClick)
        is RedditPostMedia.RedGifs -> RedditPostRedGifs(media, modifier)
        is RedditPostMedia.Streamable -> RedditPostStreamable(media, modifier)
        is RedditPostMedia.StreamFF -> RedditPostStreamFF(media, modifier)
        is RedditPostMedia.StreamIn -> RedditPostStreamIn(media, modifier)
    }
}

@Composable
fun RedditPostImage(
    media: RedditPostMedia.Image,
    modifier: Modifier = Modifier,
    onClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val aspectRatio = media.width?.let { width ->
        media.height?.takeIf { it > 0 }?.let { height ->
            if (width > 0) width.toFloat() / height else null
        }
    }
    var resolvedAspectRatio by remember(media.url) { mutableStateOf(aspectRatio) }
    val baseModifier = modifier
        .fillMaxWidth()
        .let { base ->
            val ratio = resolvedAspectRatio
            if (ratio != null && ratio > 0f) {
                base.aspectRatio(ratio)
            } else {
                base.wrapContentHeight()
            }
        }
    val clickableModifier = if (onClick != null) {
        baseModifier.clickable { onClick(media.url) }
    } else {
        baseModifier
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(media.url)
            .crossfade(true)
            .memoryCacheKey(media.url)
            .diskCacheKey(media.url)
            .allowHardware(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = clickableModifier,
        placeholder = ColorPainter(Color(0xFF1E1E1E)),
        error = ColorPainter(Color(0xFF1E1E1E)),
        onSuccess = { success ->
            if (resolvedAspectRatio == null) {
                val drawable = success.result.drawable
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                if (width > 0 && height > 0) {
                    resolvedAspectRatio = width.toFloat() / height.toFloat()
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedditPostVideo(
    media: RedditPostMedia.Video,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val exoPlayer = remember(media.url) {
        ExoPlayer.Builder(context).build().apply {
            val builder = MediaItem.Builder().setUri(media.url)
            if (media.url.endsWith(".m3u8", ignoreCase = true)) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            val mediaItem = builder.build()
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            volume = 0f
            prepare()
        }
    }

    var isMuted by remember(media.url) { mutableStateOf(true) }
    var durationMs by remember(media.url) { mutableStateOf(0L) }
    var playbackPositionMs by remember(media.url) { mutableStateOf(0L) }
    var scrubPositionMs by remember(media.url) { mutableStateOf(0L) }
    var isScrubbing by remember(media.url) { mutableStateOf(false) }
    var shouldResumeOnResume by remember(media.url) { mutableStateOf(true) }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                durationMs = player.duration.takeIf { it > 0 } ?: durationMs
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Track whether the user intended playback, even if the player was buffering
                    shouldResumeOnResume = exoPlayer.playWhenReady || exoPlayer.isPlaying
                    // Reset audio state when leaving the screen so it comes back muted
                    isMuted = true
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (shouldResumeOnResume) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                }
                else -> Unit
            }
        }
        exoPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            exoPlayer.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, isScrubbing) {
        while (isActive) {
            if (!isScrubbing) {
                playbackPositionMs = exoPlayer.currentPosition
                durationMs = exoPlayer.duration.takeIf { it > 0 } ?: durationMs
            }
            delay(250)
        }
    }

    val ratio = media.width?.let { width ->
        media.height?.takeIf { it > 0 }?.let { height ->
            if (width > 0) width.toFloat() / height else null
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .let { if (ratio != null) it.aspectRatio(ratio) else it.height(220.dp) }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            val darkerColor = Color(0xFF7B8FB8)  // Darker version of SubredditColor
            IconButton(
                onClick = { if (media.hasAudio) isMuted = !isMuted },
                enabled = media.hasAudio,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
            ) {
                val icon = if (isMuted || !media.hasAudio) {
                    ImageVector.vectorResource(id = R.drawable.ic_mute)
                } else {
                    ImageVector.vectorResource(id = R.drawable.ic_volume)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    modifier = Modifier.size(18.dp)
                )
            }

            if (durationMs > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val sliderValue = (playbackPositionMs.coerceAtMost(durationMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val sliderColors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.DarkGray
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { value ->
                        if (durationMs > 0) {
                            isScrubbing = true
                            scrubPositionMs = (value * durationMs).toLong()
                            playbackPositionMs = scrubPositionMs
                        }
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0) {
                            exoPlayer.seekTo(scrubPositionMs.coerceIn(0L, durationMs))
                        }
                        isScrubbing = false
                    },
                    valueRange = 0f..1f,
                    colors = sliderColors,
                    thumb = {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_scrub),
                            contentDescription = "Scrub",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = sliderColors,
                            modifier = Modifier.height(3.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
            }
        }
    }
}

@Composable
fun RedditPostLink(
    media: RedditPostMedia.Link,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    if (media.url.isBlank()) return
    val context = LocalContext.current
    val displayDomain = remember(media.domain, media.url) {
        val raw = media.domain.ifBlank {
            runCatching { Uri.parse(media.url).host.orEmpty() }.getOrDefault("")
        }
        raw.removePrefix("www.")
    }
    val baseModifier = modifier
        .fillMaxWidth()
    val handler = onLinkClick
    val clickableModifier = if (handler != null) {
        baseModifier.clickable(
            role = Role.Button
        ) { handler(media.url) }
    } else {
        baseModifier
    }

    Column(
        modifier = clickableModifier
    ) {
        if (media.previewImageUrl != null) {
            val defaultAspectRatio = media.previewWidth?.let { width ->
                media.previewHeight?.takeIf { it > 0 }?.let { height ->
                    if (width > 0) width.toFloat() / height else null
                }
            }
            var resolvedAspectRatio by remember(media.previewImageUrl) { mutableStateOf(defaultAspectRatio) }
            val previewModifier = Modifier
                .fillMaxWidth()
                .let { base ->
                    val ratio = resolvedAspectRatio
                    if (ratio != null && ratio > 0f) {
                        base.aspectRatio(ratio)
                    } else {
                        base.wrapContentHeight()
                    }
                }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(media.previewImageUrl)
                    .crossfade(true)
                    .size(Size.ORIGINAL)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = previewModifier,
                onSuccess = { success ->
                    if (resolvedAspectRatio == null) {
                        val drawable = success.result.drawable
                        val width = drawable.intrinsicWidth
                        val height = drawable.intrinsicHeight
                        if (width > 0 && height > 0) {
                            resolvedAspectRatio = width.toFloat() / height.toFloat()
                        }
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 0.dp,
                        bottomEnd = 12.dp,
                        bottomStart = 12.dp
                    )
                )
                .background(PostBackgroundColor)
        ) {
            if (media.previewImageUrl == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(PostBackgroundColor.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayDomain.ifBlank { "External link" },
                        color = TitleColor.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayDomain.ifBlank { media.url },
                        color = TitleColor,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_globe),
                    contentDescription = null,
                    tint = SubredditColor
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RedditPostGallery(
    media: RedditPostMedia.Gallery,
    modifier: Modifier = Modifier,
    onImageClick: ((String) -> Unit)? = null,
    onGalleryClick: ((List<String>, Int) -> Unit)? = null
) {
    if (media.images.isEmpty()) return
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val imageCount = media.images.size
    val currentIndex by remember(listState, imageCount) {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val layoutInfo = listState.layoutInfo
            val firstItem = layoutInfo.visibleItemsInfo.firstOrNull()
            if (firstItem == null || firstItem.size == 0) {
                firstVisible
            } else {
                val fraction = listState.firstVisibleItemScrollOffset / firstItem.size.toFloat()
                (firstVisible + if (fraction > 0.5f) 1 else 0).coerceIn(0, imageCount - 1)
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            flingBehavior = flingBehavior
        ) {
            itemsIndexed(media.images, key = { index, image -> "${image.url}-$index" }) { idx, image ->
                val click: ((String) -> Unit)? = when {
                    onGalleryClick != null -> { url: String ->
                        val urls = media.images.map { it.url }
                        onGalleryClick.invoke(urls, idx)
                    }
                    else -> onImageClick
                }
                RedditPostImage(
                    media = image,
                    modifier = Modifier.fillParentMaxWidth(),
                    onClick = click
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PostBackgroundColor.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(imageCount) { index ->
                val isActive = index == currentIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) TitleColor else TitleColor.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

@Composable
fun RedditPostYouTube(
    media: RedditPostMedia.YouTube,
    modifier: Modifier = Modifier,
    onYoutubeClick: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val aspectRatio = 16f / 9f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(SpacerBackgroundColor)
            .clickable(enabled = onYoutubeClick != null) {
                onYoutubeClick?.invoke(media.videoId, media.watchUrl)
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(media.thumbnailUrl)
                .crossfade(true)
                .size(Size.ORIGINAL)
                .build(),
            contentDescription = media.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play YouTube video",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun RedditPostRedGifs(
    media: RedditPostMedia.RedGifs,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(
                ratio = if (media.width != null && media.height != null && media.height > 0) {
                    media.width.toFloat() / media.height.toFloat()
                } else {
                    16f / 9f
                },
                matchHeightConstraintsFirst = false
            ),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                loadUrl(media.embedUrl)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedditPostStreamable(
    media: RedditPostMedia.Streamable,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var videoUrl by remember(media.shortcode) { mutableStateOf<String?>(null) }
    var isLoading by remember(media.shortcode) { mutableStateOf(true) }
    var hasError by remember(media.shortcode) { mutableStateOf(false) }
    val streamableApi = remember { org.koin.core.context.GlobalContext.get().get<com.munch.reddit.data.remote.StreamableApiService>() }

    LaunchedEffect(media.shortcode) {
        isLoading = true
        hasError = false
        try {
            val response = streamableApi.getVideo(media.shortcode)
            // Try multiple approaches to get the video URL
            val rawUrl = when {
                // First try the files map with mp4
                response.files?.get("mp4")?.url?.isNotBlank() == true -> response.files["mp4"]?.url
                // Fallback to mp4-mobile
                response.files?.get("mp4-mobile")?.url?.isNotBlank() == true -> response.files["mp4-mobile"]?.url
                // Check if status indicates the video is still processing
                response.status != null && response.status == 2 -> {
                    // Status 2 means ready, try url field
                    response.url?.takeIf { it.isNotBlank() }
                }
                else -> null
            }

            // Ensure URL uses HTTPS protocol
            videoUrl = rawUrl?.let { url ->
                when {
                    url.startsWith("https://") -> url
                    url.startsWith("http://") -> url.replace("http://", "https://")
                    url.startsWith("//") -> "https:$url"
                    else -> url
                }
            }

            if (videoUrl == null) {
                hasError = true
                android.util.Log.e("StreamableVideo", "Failed to get video URL for ${media.shortcode}. Response: status=${response.status}, files=${response.files?.keys}")
            } else {
                android.util.Log.d("StreamableVideo", "Successfully got video URL for ${media.shortcode}: $videoUrl")
            }
            isLoading = false
        } catch (e: Exception) {
            android.util.Log.e("StreamableVideo", "Error loading video ${media.shortcode}", e)
            hasError = true
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SpacerBackgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading video...",
                        color = TitleColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            hasError || videoUrl == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SpacerBackgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Failed to load video",
                            color = MetaInfoColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (media.thumbnailUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(media.thumbnailUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            )
                        }
                    }
                }
            }
            else -> {
                // Play the video using the RedditPostVideo component
                RedditPostVideo(
                    media = RedditPostMedia.Video(
                        url = videoUrl!!,
                        hasAudio = true,
                        width = 1280,
                        height = 720,
                        durationSeconds = null
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun RedditPostStreamFF(
    media: RedditPostMedia.StreamFF,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(media.embedUrl)
            }
        }
    )
}

@Composable
fun RedditPostStreamIn(
    media: RedditPostMedia.StreamIn,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(media.embedUrl)
            }
        }
    )
}

@Composable
fun InfoChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialSpacing
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = PostBackgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = spacing.sm,
                vertical = spacing.xs * 0.75f
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MetaInfoColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MetaInfoColor
            )
        }
    }
}

fun formatRelativeTime(createdUtc: Long, nowUtcSeconds: Long = System.currentTimeMillis() / 1000): String {
    val elapsedSeconds = kotlin.math.max(0, nowUtcSeconds - createdUtc)
    val minutes = elapsedSeconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}d"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "${elapsedSeconds}s"
    }
}

fun formatCount(count: Int): String {
    val absCount = kotlin.math.abs(count)
    val sign = if (count < 0) "-" else ""
    return when {
        absCount >= 1_000_000 -> sign + formatWithSuffix(absCount / 1_000_000f, "m")
        absCount >= 1_000 -> sign + formatWithSuffix(absCount / 1_000f, "k")
        else -> count.toString()
    }
}

fun formatWithSuffix(value: Float, suffix: String): String {
    val formatted = String.format(java.util.Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
    return formatted + suffix
}
