package com.munch.reddit.feature.shared

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.size.Size
import com.munch.reddit.feature.feed.SubredditColor
import kotlinx.coroutines.launch

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
    onBackClick: () -> Unit
) {
    val galleryImages = imageGallery?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
    val images = galleryImages ?: listOf(imageUrl)
    val startPage = startIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { images.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentImage by remember {
        derivedStateOf { images.getOrNull(pagerState.currentPage) ?: images.first() }
    }
    val pageLabel by remember {
        derivedStateOf { "${pagerState.currentPage + 1} / ${images.size}" }
    }
    val showPageLabel = images.size > 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            PreviewImageItem(
                imageUrl = images[page],
                modifier = Modifier.fillMaxSize()
            )
        }

        ImagePreviewTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            onBackClick = onBackClick,
            pageLabel = if (showPageLabel) pageLabel else null
        )

        if (galleryImages != null && images.size > 1) {
            GalleryIndicators(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 160.dp),
                count = images.size,
                currentIndex = pagerState.currentPage
            )
        }

        FloatingToolbar(
            modifier = Modifier.align(Alignment.BottomCenter),
            buttons = listOf(
                FloatingToolbarButton(
                    icon = Icons.Filled.Share,
                    contentDescription = "Share",
                    onClick = {
                        scope.launch {
                            shareImage(context, currentImage)
                        }
                    },
                    iconTint = SubredditColor,
                    iconSize = 24.dp
                ),
                FloatingToolbarButton(
                    icon = Icons.Filled.Download,
                    contentDescription = "Download",
                    onClick = {
                        scope.launch {
                            downloadImage(context, currentImage)
                        }
                    },
                    iconTint = SubredditColor,
                    iconSize = 24.dp
                ),
                FloatingToolbarButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close",
                    onClick = { onBackClick() },
                    iconTint = SubredditColor,
                    iconSize = 24.dp
                )
            )
        )
    }
}

@Composable
private fun ImagePreviewTopBar(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    pageLabel: String?
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .padding(top = 4.dp)
    ) {
        IconButton(
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = onBackClick
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        pageLabel?.let {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = it,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun GalleryIndicators(
    modifier: Modifier = Modifier,
    count: Int,
    currentIndex: Int
) {
    if (count <= 1) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val isSelected = index == currentIndex
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isSelected) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.4f)
                    )
            )
        }
    }
}

@Composable
private fun PreviewImageItem(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    AsyncImage(
        model = imageRequest,
        contentDescription = "Image preview",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
