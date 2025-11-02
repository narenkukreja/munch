package com.munch.reddit.feature.shared

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.munch.reddit.feature.feed.PostBackgroundColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.derivedStateOf
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.launch

@Composable
fun ImagePreviewRoute(navController: NavController, imageUrl: String) {
    // If a gallery was provided via SavedStateHandle, render a swipable pager
    // Note: Gallery data is saved to currentBackStackEntry before navigation,
    // which becomes previousBackStackEntry after we navigate to this screen
    val gallery: List<String>? = runCatching {
        navController.previousBackStackEntry?.savedStateHandle?.get<List<String>>("image_gallery")
    }.getOrNull()
    val startIndex: Int = runCatching {
        navController.previousBackStackEntry?.savedStateHandle?.get<Int>("image_gallery_start_index") ?: 0
    }.getOrNull() ?: 0

    // Debug logging
    android.util.Log.d("ImagePreviewRoute", "Gallery data - size: ${gallery?.size}, startIndex: $startIndex, imageUrl: $imageUrl")

    // Clear the saved state after reading to prevent stale data on subsequent navigations
    navController.previousBackStackEntry?.savedStateHandle?.remove<List<String>>("image_gallery")
    navController.previousBackStackEntry?.savedStateHandle?.remove<Int>("image_gallery_start_index")

    if (!gallery.isNullOrEmpty()) {
        android.util.Log.d("ImagePreviewRoute", "Showing ImageGalleryPreview with ${gallery.size} images")
        ImageGalleryPreview(navController = navController, imageUrls = gallery, startIndex = startIndex.coerceIn(0, gallery.lastIndex))
        return
    }
    android.util.Log.d("ImagePreviewRoute", "Showing single image preview (gallery was null or empty)")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val animatedScale by animateFloatAsState(targetValue = scale, label = "imageScale")
    val density = LocalDensity.current
    val edgeThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }
    val dragThreshold = 120f

    val isGif = remember(imageUrl) { imageUrl.lowercase().contains(".gif") }
    val imageRequest = remember(imageUrl, isGif) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .size(Size.ORIGINAL)
            .apply {
                if (isGif) {
                    allowHardware(false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        decoderFactory(ImageDecoderDecoder.Factory())
                    } else {
                        decoderFactory(GifDecoder.Factory())
                    }
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(scale) {
                var totalDrag = 0f
                var eligible = false
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        if (scale > 1.05f) {
                            eligible = false
                            totalDrag = 0f
                            return@detectHorizontalDragGestures
                        }
                        eligible = startOffset.x <= edgeThresholdPx
                        totalDrag = 0f
                    },
                    onDragEnd = {
                        if (eligible && totalDrag > dragThreshold) {
                            navController.popBackStack()
                        }
                        eligible = false
                        totalDrag = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!eligible || dragAmount <= 0f) return@detectHorizontalDragGestures
                        change.consume()
                        totalDrag += dragAmount
                        if (totalDrag > dragThreshold) {
                            navController.popBackStack()
                            eligible = false
                            totalDrag = 0f
                        }
                    }
                )
            }
            .pointerInput(scale) {
                var totalDrag = 0f
                var eligible = false
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (!eligible) return@detectVerticalDragGestures
                        if (dragAmount < 0f) {
                            totalDrag = 0f
                            return@detectVerticalDragGestures
                        }
                        if (dragAmount == 0f) return@detectVerticalDragGestures
                        change.consume()
                        totalDrag += dragAmount
                        if (totalDrag > dragThreshold) {
                            navController.popBackStack()
                            eligible = false
                            totalDrag = 0f
                        }
                    },
                    onDragStart = {
                        if (scale > 1.05f) {
                            eligible = false
                            totalDrag = 0f
                            return@detectVerticalDragGestures
                        }
                        eligible = true
                        totalDrag = 0f
                    },
                    onDragEnd = {
                        if (eligible && totalDrag > dragThreshold) {
                            navController.popBackStack()
                        }
                        eligible = false
                        totalDrag = 0f
                    }
                )
            }
            .pointerInput(imageUrl) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    val maxOffset = computeMaxOffset(newScale, containerSize)
                    val adjustedPan = if (newScale > 1f) pan else Offset.Zero
                    val newOffset = (offset + adjustedPan).coerceWithin(maxOffset)
                    scale = newScale
                    offset = newOffset
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        navController.popBackStack()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = animatedScale,
                    scaleY = animatedScale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .onSizeChanged { containerSize = it }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingToolbar(
            buttons = listOf(
                FloatingToolbarButton(
                    icon = Icons.Filled.Share,
                    contentDescription = "Share",
                    onClick = {
                        scope.launch {
                            shareImage(context, imageUrl)
                        }
                    },
                    iconTint = SubredditColor,
                    iconSize = 20.dp
                ),
                FloatingToolbarButton(
                    icon = Icons.Filled.Download,
                    contentDescription = "Download",
                    onClick = {
                        scope.launch {
                            downloadImage(context, imageUrl)
                        }
                    },
                    iconTint = SubredditColor,
                    iconSize = 20.dp
                ),
                FloatingToolbarButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close",
                    onClick = { navController.popBackStack() },
                    iconTint = SubredditColor,
                    iconSize = 20.dp
                )
            )
        )
    }
}

@Composable
private fun ImageGalleryPreview(navController: NavController, imageUrls: List<String>, startIndex: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { imageUrls.size })
    val currentIndex by remember(pagerState) { derivedStateOf { pagerState.currentPage } }
    val currentUrl by remember(imageUrls, currentIndex) { mutableStateOf(imageUrls.getOrNull(currentIndex) ?: imageUrls.first()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Horizontal pager for swiping between images
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImagePage(navController = navController, imageUrl = imageUrls[page])
        }

        // Pagination indicator at the top
        if (imageUrls.size > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PostBackgroundColor.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentIndex + 1} / ${imageUrls.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = TitleColor
                    )
                }
            }
        }

        // Bottom toolbar with share/download/close buttons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            FloatingToolbar(
                buttons = listOf(
                    FloatingToolbarButton(
                        icon = Icons.Filled.Share,
                        contentDescription = "Share",
                        onClick = { scope.launch { shareImage(context, currentUrl) } },
                        iconTint = SubredditColor,
                        iconSize = 20.dp
                    ),
                    FloatingToolbarButton(
                        icon = Icons.Filled.Download,
                        contentDescription = "Download",
                        onClick = { scope.launch { downloadImage(context, currentUrl) } },
                        iconTint = SubredditColor,
                        iconSize = 20.dp
                    ),
                    FloatingToolbarButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Close",
                        onClick = { navController.popBackStack() },
                        iconTint = SubredditColor,
                        iconSize = 20.dp
                    )
                )
            )
        }
    }
}

@Composable
private fun ZoomableImagePage(navController: NavController, imageUrl: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val animatedScale by animateFloatAsState(targetValue = scale, label = "imageScale")

    val isGif = remember(imageUrl) { imageUrl.lowercase().contains(".gif") }
    val imageRequest = remember(imageUrl, isGif) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .size(Size.ORIGINAL)
            .apply {
                if (isGif) {
                    allowHardware(false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        decoderFactory(ImageDecoderDecoder.Factory())
                    } else {
                        decoderFactory(GifDecoder.Factory())
                    }
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                // Only handle transform gestures when zoomed in
                // When not zoomed (scale == 1f), let the HorizontalPager handle swipes
                if (scale > 1.05f) {
                    Modifier.pointerInput(scale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            val maxOffset = computeMaxOffset(newScale, containerSize)
                            val newOffset = (offset + pan).coerceWithin(maxOffset)
                            scale = newScale
                            offset = newOffset
                        }
                    }
                } else {
                    Modifier.pointerInput(scale) {
                        detectTransformGestures { _, _, zoom, _ ->
                            // When not zoomed, only respond to pinch-to-zoom, not pan
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale <= 1f) {
                                offset = Offset.Zero
                            }
                        }
                    }
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { navController.popBackStack() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = animatedScale,
                    scaleY = animatedScale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .onSizeChanged { containerSize = it }
        )
    }
}

private fun computeMaxOffset(scale: Float, containerSize: IntSize): Offset {
    if (scale <= 1f || containerSize == IntSize.Zero) return Offset.Zero
    val maxX = (containerSize.width * (scale - 1f) / 2f)
    val maxY = (containerSize.height * (scale - 1f) / 2f)
    return Offset(maxX, maxY)
}

private fun Offset.coerceWithin(maxOffset: Offset): Offset {
    if (maxOffset == Offset.Zero) return Offset.Zero
    val clampedX = x.coerceIn(-maxOffset.x, maxOffset.x)
    val clampedY = y.coerceIn(-maxOffset.y, maxOffset.y)
    return Offset(clampedX, clampedY)
}

private fun shareImage(context: Context, imageUrl: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, imageUrl)
    }
    val chooser = Intent.createChooser(shareIntent, null)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

private fun downloadImage(context: Context, imageUrl: String) {
    runCatching {
        val request = DownloadManager.Request(Uri.parse(imageUrl))
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_PICTURES,
                "Munch_${System.currentTimeMillis()}.jpg"
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("image/*")
            .setAllowedOverRoaming(true)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Downloading image", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Unable to download image", Toast.LENGTH_SHORT).show()
    }
}
