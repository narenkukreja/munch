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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.derivedStateOf
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.launch

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

/**
 * Activity-compatible version of ImagePreviewScreen
 */
@Composable
fun ImagePreviewScreen(
    imageUrl: String,
    imageGallery: List<String>?,
    startIndex: Int,
    onBackClick: () -> Unit,
    enableEdgeSwipeBack: Boolean = true
) {
    if (!imageGallery.isNullOrEmpty()) {
        ImageGalleryPreviewActivity(imageUrls = imageGallery, startIndex = startIndex.coerceIn(0, imageGallery.lastIndex), onBackClick = onBackClick)
        return
    }

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
            .let { base ->
                if (enableEdgeSwipeBack) {
                    base.pointerInput(scale) {
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
                                    onBackClick()
                                }
                                eligible = false
                                totalDrag = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (!eligible || dragAmount <= 0f) return@detectHorizontalDragGestures
                                change.consume()
                                totalDrag += dragAmount
                                if (totalDrag > dragThreshold) {
                                    onBackClick()
                                    eligible = false
                                    totalDrag = 0f
                                }
                            }
                        )
                    }
                } else base
            }
            .onSizeChanged { containerSize = it }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Image preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 4f)
                        if (scale > 1f) {
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-containerSize.width * 0.5f, containerSize.width * 0.5f),
                                y = (offset.y + pan.y).coerceIn(-containerSize.height * 0.5f, containerSize.height * 0.5f)
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
                .pointerInput(scale) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                // If zoomed in, double tap zooms out
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                // If not zoomed, double tap dismisses
                                onBackClick()
                            }
                        }
                    )
                }
        )

        // Floating toolbar at the bottom
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
                        onClick = { onBackClick() },
                        iconTint = SubredditColor,
                        iconSize = 20.dp
                    )
                )
            )
        }
    }
}

@Composable
private fun ImageGalleryPreviewActivity(
    imageUrls: List<String>,
    startIndex: Int,
    onBackClick: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { imageUrls.size })
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val imageUrl = imageUrls[page]
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var containerSize by remember { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current
            val edgeThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }
            val dragThreshold = 120f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
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
                                    onBackClick()
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
                                eligible = false
                                totalDrag = 0f
                            }
                        )
                    }
                    .onSizeChanged { containerSize = it }
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Gallery image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 4f)
                                if (scale > 1f) {
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-containerSize.width * 0.5f, containerSize.width * 0.5f),
                                        y = (offset.y + pan.y).coerceIn(-containerSize.height * 0.5f, containerSize.height * 0.5f)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .pointerInput(scale) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.05f) {
                                        // If zoomed in, double tap zooms out
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        // If not zoomed, double tap dismisses
                                        onBackClick()
                                    }
                                }
                            )
                        }
                )
            }
        }

        // Page indicator and toolbar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Page counter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PostBackgroundColor)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(
                    text = "${currentPage + 1} / ${imageUrls.size}",
                    color = TitleColor,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Floating toolbar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(top = 48.dp)
            ) {
                val scope = rememberCoroutineScope()
                FloatingToolbar(
                    buttons = listOf(
                        FloatingToolbarButton(
                            icon = Icons.Filled.Share,
                            contentDescription = "Share",
                            onClick = {
                                scope.launch {
                                    shareImage(context, imageUrls[currentPage])
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
                                    downloadImage(context, imageUrls[currentPage])
                                }
                            },
                            iconTint = SubredditColor,
                            iconSize = 20.dp
                        ),
                        FloatingToolbarButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "Close",
                            onClick = { onBackClick() },
                            iconTint = SubredditColor,
                            iconSize = 20.dp
                        )
                    )
                )
            }
        }
    }
}
