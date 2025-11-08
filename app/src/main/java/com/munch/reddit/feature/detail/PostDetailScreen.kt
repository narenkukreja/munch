package com.munch.reddit.feature.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.munch.reddit.feature.shared.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.munch.reddit.domain.SubredditCatalog
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.domain.model.RedditPostMedia
import com.munch.reddit.R
import com.munch.reddit.domain.model.RedditComment
import com.munch.reddit.feature.feed.MetaInfoColor
import com.munch.reddit.feature.feed.ModLabelColor
import com.munch.reddit.feature.feed.OpLabelColor
import com.munch.reddit.feature.feed.PinnedLabelColor
import com.munch.reddit.feature.feed.PostBackgroundColor
import com.munch.reddit.feature.feed.SpacerBackgroundColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import com.munch.reddit.feature.feed.VisualModColor
import com.munch.reddit.feature.shared.FloatingToolbar
import com.munch.reddit.feature.shared.FloatingToolbarButton
import com.munch.reddit.feature.shared.InfoChip
import coil.compose.AsyncImage
import com.munch.reddit.feature.shared.LinkifiedText
import com.munch.reddit.feature.shared.RedditPostMediaContent
import com.munch.reddit.feature.shared.SubredditSideSheet
import com.munch.reddit.feature.shared.formatCount
import com.munch.reddit.feature.shared.formatRelativeTime
import com.munch.reddit.feature.shared.isImageUrl
import com.munch.reddit.feature.shared.openLinkInCustomTab
import com.munch.reddit.feature.shared.openYouTubeVideo
import com.munch.reddit.feature.shared.parseHtmlText
import com.munch.reddit.ui.theme.MaterialSpacing
import com.munch.reddit.ui.theme.MunchForRedditTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private const val COMMENT_LOAD_MORE_THRESHOLD = 5
private val FlairBackgroundColor = Color(0xFF5E5E5E)

/**
 * PostDetailActivityContent for Activity-based navigation.
 * Displays PostDetailScreen and handles navigation through Activities.
 */
@Composable
fun PostDetailActivityContent(
    permalink: String,
    onBack: () -> Unit
) {
    val viewModel: PostDetailViewModel = koinViewModel(parameters = { parametersOf(permalink) })
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    PostDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onSelectSort = viewModel::selectSort,
        onToggleComment = viewModel::toggleComment,
        onUserLoadMoreComments = viewModel::userLoadMoreComments,
        onAutoLoadMoreComments = viewModel::loadMoreComments,
        onLoadRemoteReplies = viewModel::loadMoreRemoteReplies,
        onLoadMoreReplies = viewModel::loadMoreReplies,
        onOpenSubreddit = { subreddit ->
            // Navigate back to FeedActivity with the selected subreddit
            activity?.let {
                val intent = Intent(context, com.munch.reddit.activity.FeedActivity::class.java).apply {
                    putExtra("SELECTED_SUBREDDIT", subreddit)
                    // Clear the back stack and start fresh with the new subreddit
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                it.startActivity(intent)
                it.finish()
            }
        },
        onOpenImage = { imageUrl ->
            // Open image links in the in-app ImagePreviewActivity instead of a custom tab
            activity?.let {
                val intent = Intent(context, com.munch.reddit.activity.ImagePreviewActivity::class.java).apply {
                    putExtra("IMAGE_URL", imageUrl)
                }
                it.startActivity(intent)
            }
        },
        onOpenGallery = { urls, index ->
            // Support gallery preview in the same activity
            activity?.let {
                val intent = Intent(context, com.munch.reddit.activity.ImagePreviewActivity::class.java).apply {
                    putExtra("IMAGE_URL", urls.getOrNull(index) ?: urls.firstOrNull())
                    putStringArrayListExtra("IMAGE_GALLERY", ArrayList(urls))
                    putExtra("IMAGE_GALLERY_START_INDEX", index)
                }
                it.startActivity(intent)
            }
        },
        onOpenYouTube = { videoId ->
            // Fallback for standalone activity: open in browser/app
            openYouTubeVideo(context, videoId, "https://www.youtube.com/watch?v=$videoId")
        },
        onOpenLink = { url -> openLinkInCustomTab(context, url) },
        onSearchClick = { /* no-op in standalone activity */ },
        onSettingsClick = { /* no-op */ },
        subredditOptions = SubredditCatalog.defaultSubreddits,
        viewModel = viewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PostDetailScreen(
    uiState: PostDetailViewModel.PostDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectSort: (String) -> Unit,
    onToggleComment: (String) -> Unit,
    onUserLoadMoreComments: () -> Unit,
    onAutoLoadMoreComments: () -> Unit,
    onLoadRemoteReplies: (String) -> Unit,
    onLoadMoreReplies: (String) -> Unit,
    onOpenSubreddit: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenGallery: (List<String>, Int) -> Unit,
    onOpenYouTube: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    subredditOptions: List<String>,
    viewModel: PostDetailViewModel? = null
) {
    var showSubredditSheet by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val post = uiState.post
    val mediaShareUrl = remember(post) { post?.media?.shareableUrl() }
    val redditShareUrl = remember(post?.permalink) { post?.permalinkUrl() }
    val shareIcon = ImageVector.vectorResource(id = R.drawable.atr_24px)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val topBarTitle = uiState.post?.subreddit?.removePrefix("r/")?.removePrefix("R/")?.lowercase()
        ?.let { "r/$it" } ?: "Post"
    val menuIcon = ImageVector.vectorResource(id = R.drawable.atr_24px)
    val currentSubreddit = uiState.post?.subreddit
        ?.removePrefix("r/")
        ?.removePrefix("R/")
        ?.lowercase()
        ?: "all"
    val sideSheetScrollState = remember(viewModel) {
        ScrollState(viewModel?.getSideSheetScroll() ?: 0)
    }

    LaunchedEffect(showSubredditSheet) {
        if (showSubredditSheet) {
            viewModel?.getSideSheetScroll()?.let { saved ->
                if (sideSheetScrollState.value != saved) {
                    sideSheetScrollState.scrollTo(saved)
                }
            }
        } else {
            viewModel?.updateSideSheetScroll(sideSheetScrollState.value)
        }
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel?.updateSideSheetScroll(sideSheetScrollState.value)
        }
    }

    Scaffold(
        containerColor = SpacerBackgroundColor,
        modifier = Modifier
            .swipeToGoBack(
                onBack = onBack,
                restrictToEdge = false
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = SpacerBackgroundColor,
                    scrolledContainerColor = SpacerBackgroundColor,
                    titleContentColor = TitleColor,
                    navigationIconContentColor = TitleColor,
                    actionIconContentColor = TitleColor
                ),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TitleColor)
                    }
                },
                title = {
                    AnimatedContent(
                        targetState = topBarTitle,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(200, delayMillis = 20)) + slideInVertically { it / 8 }) togetherWith
                                (fadeOut(animationSpec = tween(150)) + slideOutVertically { -it / 8 })
                        },
                        label = "detail_toolbar_title"
                    ) { animatedTitle ->
                        Text(
                            text = animatedTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TitleColor
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize()
                )
                uiState.post != null -> {
                    val listState = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()

                    Box(modifier = Modifier.fillMaxSize()) {
                        PostDetailContent(
                            post = uiState.post,
                            comments = uiState.comments,
                            paddingValues = paddingValues,
                            selectedSort = uiState.selectedSort,
                            sortOptions = uiState.sortOptions,
                            onSelectSort = onSelectSort,
                            onToggleComment = onToggleComment,
                            onUserLoadMoreComments = onUserLoadMoreComments,
                            onAutoLoadMoreComments = onAutoLoadMoreComments,
                            onLoadRemoteReplies = onLoadRemoteReplies,
                            onLoadMoreReplies = onLoadMoreReplies,
                            onOpenSubreddit = onOpenSubreddit,
                            onOpenImage = onOpenImage,
                            onOpenGallery = onOpenGallery,
                            onOpenYouTube = onOpenYouTube,
                            onOpenLink = onOpenLink,
                            isAppendingComments = uiState.isAppending,
                            canLoadMoreComments = uiState.hasMoreComments,
                            isRefreshingComments = uiState.isRefreshingComments,
                            pendingRemoteReplyCount = uiState.pendingRemoteReplyCount,
                            autoFetchRemaining = uiState.autoFetchRemaining,
                            listState = listState,
                            flairEmojiLookup = uiState.flairEmojiLookup
                        )

                        // Floating Toolbar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        ) {
                            AnimatedVisibility(
                                visible = !showSubredditSheet,
                                enter = fadeIn(animationSpec = tween(durationMillis = 150)),
                                exit = fadeOut(animationSpec = tween(durationMillis = 150)),
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                val buttons = buildList {
                                    add(FloatingToolbarButton(
                                        icon = Icons.Default.ArrowDownward,
                                        contentDescription = "Next comment",
                                        onClick = {
                                            coroutineScope.launch {
                                                // Find next top-level comment
                                                val currentIndex = listState.firstVisibleItemIndex
                                                // First item is the post, second is sort bar
                                                // Comments start from index 2
                                                val commentStartIndex = 2
                                                var nextTopLevelIndex = -1

                                                for (i in (currentIndex + 1) until uiState.comments.size + commentStartIndex) {
                                                    val commentIndex = i - commentStartIndex
                                                    if (commentIndex >= 0 && commentIndex < uiState.comments.size) {
                                                        val item = uiState.comments[commentIndex]
                                                        if (item is PostDetailViewModel.CommentListItem.CommentNode && item.depth == 0) {
                                                            nextTopLevelIndex = i
                                                            break
                                                        }
                                                    }
                                                }

                                                if (nextTopLevelIndex != -1) {
                                                    listState.animateScrollToItem(nextTopLevelIndex)
                                                }
                                            }
                                        },
                                        iconTint = SubredditColor,
                                        iconSize = 20.dp
                                    ))

                                    add(FloatingToolbarButton(
                                        icon = shareIcon,
                                        contentDescription = "Share post",
                                        onClick = {
                                            if (uiState.post != null) {
                                                showShareDialog = true
                                            }
                                        },
                                        iconTint = SubredditColor,
                                        iconSize = 20.dp
                                    ))

                                    // Add globe button if post has external link
                                    if (uiState.post?.media is RedditPostMedia.Link) {
                                        val linkMedia = uiState.post.media as RedditPostMedia.Link
                                        add(FloatingToolbarButton(
                                            icon = ImageVector.vectorResource(id = R.drawable.ic_globe),
                                            contentDescription = "Open link",
                                            onClick = { onOpenLink(linkMedia.url) },
                                            iconTint = SubredditColor,
                                            iconSize = 20.dp
                                        ))
                                    }

                                    add(FloatingToolbarButton(
                                        icon = Icons.Default.Menu,
                                        contentDescription = "Browse subreddits",
                                        onClick = { showSubredditSheet = true },
                                        iconTint = SubredditColor,
                                        iconSize = 20.dp
                                    ))
                                }

                                FloatingToolbar(buttons = buttons)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareDialog && post != null) {
        val spacing = MaterialSpacing
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text(text = "Share post") },
            text = {
                val hasMediaOption = mediaShareUrl != null
                val hasRedditOption = redditShareUrl != null
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    if (hasMediaOption) {
                        val mediaUrl = mediaShareUrl!!
                        TextButton(
                            onClick = {
                                shareText(context, mediaUrl, "Share media")
                                showShareDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Share media link",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                                color = SubredditColor
                            )
                        }
                    }
                    if (hasRedditOption) {
                        val redditUrl = redditShareUrl!!
                        TextButton(
                            onClick = {
                                shareText(context, redditUrl, "Share Reddit post")
                                showShareDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Share Reddit link",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                                color = SubredditColor
                            )
                        }
                    }
                    if (!hasMediaOption && !hasRedditOption) {
                        Text(
                            text = "No shareable links available for this post.",
                            color = MetaInfoColor
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareDialog = false }) {
                    Text(text = "Close", color = SubredditColor)
                }
            }
        )
    }

    if (showShareDialog) {
        BackHandler { showShareDialog = false }
    }

    if (showSubredditSheet) {
        BackHandler { showSubredditSheet = false }
    }

    SubredditSideSheet(
        visible = showSubredditSheet,
        selectedSubreddit = currentSubreddit,
        subreddits = subredditOptions,
        onSelectSubreddit = { selectedSub ->
            showSubredditSheet = false
            onOpenSubreddit(selectedSub)
        },
        onDismissRequest = { showSubredditSheet = false },
        onSearchClick = onSearchClick,
        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
        subredditIcons = uiState.subredditIcons,
        exploreSubreddits = SubredditCatalog.exploreSubreddits,
        onSettingsClick = onSettingsClick,
        scrollState = sideSheetScrollState
    )
}

private fun Modifier.swipeToGoBack(
    onBack: () -> Unit,
    edgeWidth: Dp = 24.dp,
    triggerDistance: Dp = 96.dp,
    restrictToEdge: Boolean = true
): Modifier = composed {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    val dragOffset = remember { Animatable(0f) }
    val edgeWidthPx = if (restrictToEdge) with(density) { edgeWidth.toPx() } else Float.POSITIVE_INFINITY
    val triggerDistancePx = with(density) { triggerDistance.toPx() }
    var dragStartedOnEdge by remember { mutableStateOf(false) }
    var dragJob by remember { mutableStateOf<Job?>(null) }

    fun resetOffset() {
        dragJob?.cancel()
        dragJob = null
        scope.launch {
            dragOffset.stop()
            dragOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    this
        .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
        .pointerInput(edgeWidthPx, triggerDistancePx) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragStartedOnEdge = offset.x <= edgeWidthPx
                },
                onHorizontalDrag = { change, dragAmount ->
                    if (change.isConsumed) return@detectHorizontalDragGestures
                    if (!dragStartedOnEdge || dragAmount <= 0f) return@detectHorizontalDragGestures
                    change.consumePositionChange()
                    dragJob?.cancel()
                    val job = scope.launch {
                        val newOffset = (dragOffset.value + dragAmount).coerceAtLeast(0f)
                        dragOffset.snapTo(newOffset)
                    }
                    dragJob = job
                },
                onDragEnd = {
                    val shouldNavigateBack = dragStartedOnEdge && dragOffset.value >= triggerDistancePx
                    dragJob?.cancel()
                    dragJob = null
                    scope.launch {
                        dragOffset.stop()
                        if (shouldNavigateBack) {
                            dragOffset.animateTo(
                                targetValue = triggerDistancePx * 1.1f,
                                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                            )
                            currentOnBack()
                            dragOffset.snapTo(0f)
                        } else {
                            dragOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                    dragStartedOnEdge = false
                },
                onDragCancel = {
                    dragStartedOnEdge = false
                    dragJob?.cancel()
                    dragJob = null
                    resetOffset()
                }
            )
        }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PostDetailContent(
    post: RedditPost,
    comments: List<PostDetailViewModel.CommentListItem>,
    paddingValues: PaddingValues,
    selectedSort: String,
    sortOptions: List<String>,
    onSelectSort: (String) -> Unit,
    onToggleComment: (String) -> Unit,
    onUserLoadMoreComments: () -> Unit,
    onAutoLoadMoreComments: () -> Unit,
    onLoadRemoteReplies: (String) -> Unit,
    onLoadMoreReplies: (String) -> Unit,
    onOpenSubreddit: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenGallery: (List<String>, Int) -> Unit,
    onOpenYouTube: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    isAppendingComments: Boolean,
    canLoadMoreComments: Boolean,
    isRefreshingComments: Boolean,
    pendingRemoteReplyCount: Int,
    autoFetchRemaining: Int,
    listState: LazyListState,
    flairEmojiLookup: Map<String, String>
) {
    val context = LocalContext.current

    LaunchedEffect(comments, canLoadMoreComments, isAppendingComments, isRefreshingComments, autoFetchRemaining) {
        if (!canLoadMoreComments || comments.isEmpty() || isRefreshingComments) return@LaunchedEffect
        if (autoFetchRemaining <= 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collectLatest { index ->
                if (index == null) return@collectLatest
                val triggerIndex = (comments.lastIndex - COMMENT_LOAD_MORE_THRESHOLD).coerceAtLeast(0)
                if (!isAppendingComments && index >= triggerIndex) {
                    onAutoLoadMoreComments()
                }
            }
    }

    val spacing = MaterialSpacing
    val insetTop = paddingValues.calculateTopPadding()
    val insetBottom = paddingValues.calculateBottomPadding()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SpacerBackgroundColor)
            .padding(top = insetTop, bottom = insetBottom),
        state = listState,
        contentPadding = PaddingValues(top = spacing.lg, bottom = 80.dp)
    ) {
        item {
            Column {
                Surface(
                    shape = RectangleShape,
                    color = PostBackgroundColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        RedditPostMediaContent(
                            media = post.media,
                            modifier = Modifier.fillMaxWidth(),
                            onLinkClick = onOpenLink,
                            onImageClick = onOpenImage,
                            onGalleryClick = onOpenGallery,
                            onYoutubeClick = { videoId, url ->
                                val id = videoId.ifBlank { Uri.parse(url).getQueryParameter("v") ?: "" }
                                if (id.isNotBlank()) onOpenYouTube(id)
                            }
                        )
                        PostHeader(
                            post = post,
                            onOpenLink = onOpenLink,
                            onOpenImage = onOpenImage
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.lg))
            }
        }
        item {
            CommentSortBar(
                selectedSort = selectedSort,
                sortOptions = sortOptions,
                onSelectSort = onSelectSort
            )
        }
        if (isRefreshingComments) {
            item("comments_refreshing_loader") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }
        if (comments.isEmpty() && !isRefreshingComments) {
            item {
                Text(
                    text = "No comments yet",
                    color = MetaInfoColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.lg),
                    textAlign = TextAlign.Center
                )
            }
        }
        items(
            items = comments,
            key = { it.key },
            contentType = { item ->
                when (item) {
                    is PostDetailViewModel.CommentListItem.CommentNode -> "comment"
                    is PostDetailViewModel.CommentListItem.LoadMoreRepliesNode -> "load_more"
                    is PostDetailViewModel.CommentListItem.RemoteRepliesNode -> "remote_replies"
                }
            }
        ) { item ->
            Box(
                modifier = Modifier.padding(vertical = spacing.xs)
            ) {
                when (item) {
                    is PostDetailViewModel.CommentListItem.CommentNode -> CommentItem(
                        node = item,
                        onToggleComment = onToggleComment,
                        onOpenSubreddit = onOpenSubreddit,
                        onOpenImage = onOpenImage,
                        onOpenLink = onOpenLink,
                        flairEmojiLookup = flairEmojiLookup
                    )
                    is PostDetailViewModel.CommentListItem.LoadMoreRepliesNode -> LoadMoreRepliesItem(
                        node = item,
                        onLoadMoreReplies = onLoadMoreReplies
                    )
                    is PostDetailViewModel.CommentListItem.RemoteRepliesNode -> RemoteRepliesItem(
                        node = item,
                        onLoadMoreComments = onLoadRemoteReplies
                    )
                }
            }
        }
        if (!isRefreshingComments && (canLoadMoreComments || isAppendingComments)) {
            item("comments_load_more_control") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAppendingComments) {
                        LoadingIndicator(modifier = Modifier.size(28.dp))
                    } else {
                        val buttonLabel = if (pendingRemoteReplyCount > 0) {
                            val unit = if (pendingRemoteReplyCount == 1) "reply" else "replies"
                            "Load more comments (${formatCount(pendingRemoteReplyCount)} $unit)"
                        } else {
                            "Load more comments"
                        }
                        TextButton(onClick = onUserLoadMoreComments) {
                            Text(text = buttonLabel, color = SubredditColor)
                        }
                    }
                }
            }
        } else if (comments.isNotEmpty()) {
            item("comments_end_spacer") {
                Spacer(modifier = Modifier.height(spacing.xl))
            }
        }
    }
}

@Composable
private fun CommentSortBar(
    selectedSort: String,
    sortOptions: List<String>,
    onSelectSort: (String) -> Unit
) {
    if (sortOptions.isEmpty()) return
    val spacing = MaterialSpacing
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg)
            .padding(bottom = spacing.sm),
        shape = RoundedCornerShape(spacing.sm),
        color = PostBackgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            sortOptions.forEach { option ->
                val normalized = option.lowercase()
                val isSelected = normalized == selectedSort.lowercase()
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = isSelected,
                    onClick = { onSelectSort(normalized) },
                    shape = RoundedCornerShape(spacing.xs),
                    label = {
                        Text(
                            text = normalized.replaceFirstChar { it.uppercaseChar() },
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = PostBackgroundColor,
                        labelColor = if (isSelected) TitleColor else MetaInfoColor,
                        selectedContainerColor = SubredditColor.copy(alpha = 0.22f),
                        selectedLabelColor = TitleColor
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MetaInfoColor,
                        selectedBorderColor = SubredditColor
                    )
                )
            }
        }
    }
}

@Composable
private fun PostHeader(
    post: RedditPost,
    onOpenLink: (String) -> Unit,
    onOpenImage: (String) -> Unit
) {
    val formattedTitle = remember(post.title) {
        buildAnnotatedString { append(parseHtmlText(post.title)) }
    }
    val spacing = MaterialSpacing
    val displayDomain = remember(post.domain) { post.domain.removePrefix("www.") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        AnimatedContent(
            targetState = formattedTitle,
            transitionSpec = {
                (fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 20)) + slideInVertically { it / 8 }) togetherWith
                    (fadeOut(animationSpec = tween(durationMillis = 160)) + slideOutVertically { -it / 8 })
            },
            label = "detail_title_transition"
        ) { title ->
            Text(
                text = title,
                color = TitleColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if ((displayDomain.isNotBlank() && post.media !is RedditPostMedia.Link) || post.isStickied) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                if (displayDomain.isNotBlank() && post.media !is RedditPostMedia.Link) {
                    Text(
                        text = displayDomain,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (post.isNsfw) {
                    val nsfwLabelColor = MaterialTheme.colorScheme.error
                    Surface(
                        color = nsfwLabelColor.copy(alpha = 0.18f),
                        contentColor = nsfwLabelColor,
                        shape = RoundedCornerShape(999.dp),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Text(
                            text = "NSFW",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(
                                horizontal = spacing.sm,
                                vertical = spacing.xs * 0.75f
                            )
                        )
                    }
                }
                AnimatedVisibility(
                    visible = post.isStickied,
                    enter = fadeIn(animationSpec = tween(200)) + scaleIn(
                        initialScale = 0.8f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    exit = fadeOut(animationSpec = tween(150)) + scaleOut(
                        targetScale = 0.8f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                ) {
                    Surface(
                        color = PinnedLabelColor.copy(alpha = 0.18f),
                        contentColor = PinnedLabelColor,
                        shape = RoundedCornerShape(999.dp),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Text(
                            text = "PINNED",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(
                                horizontal = spacing.sm,
                                vertical = spacing.xs * 0.75f
                            )
                        )
                    }
                }
            }
        }

        if (post.selfText.isNotBlank()) {
            var isBodyExpanded by rememberSaveable(post.id) { mutableStateOf(true) }
            val collapsedInteraction = remember { MutableInteractionSource() }
            if (isBodyExpanded) {
                LinkifiedText(
                    text = post.selfText,
                    htmlText = post.selfTextHtml,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TitleColor.copy(alpha = 0.9f),
                    linkColor = SubredditColor,
                    quoteColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm),
                    onLinkClick = onOpenLink,
                    onImageClick = onOpenImage,
                    onTextClick = { isBodyExpanded = false }
                )
            } else {
                Text(
                    text = "Post text hidden - tap to show",
                    color = MetaInfoColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm)
                        .clickable(
                            interactionSource = collapsedInteraction,
                            indication = null
                        ) { isBodyExpanded = true }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "u/${post.author}",
                    color = SubredditColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                InfoChip(icon = Icons.Filled.AccessTime, label = formatRelativeTime(post.createdUtc))
                InfoChip(icon = Icons.Filled.ChatBubble, label = formatCount(post.commentCount))
                InfoChip(icon = Icons.Filled.ArrowUpward, label = formatCount(post.score))
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentItem(
    node: PostDetailViewModel.CommentListItem.CommentNode,
    onToggleComment: (String) -> Unit,
    onOpenSubreddit: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    flairEmojiLookup: Map<String, String>
) {
    val comment = node.comment
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val flairText = comment.authorFlairText?.takeIf { it.isNotBlank() }
    var showFlairDialog by remember { mutableStateOf(false) }
    var showCommentOptionsDialog by remember { mutableStateOf(false) }
    val headerInteraction = remember { MutableInteractionSource() }
    val spacing = MaterialSpacing
    val parentVerticalPadding = spacing.sm * 0.5f
    val nestedVerticalPadding = spacing.xs * 0.5f
    val verticalPadding = if (node.depth > 0) nestedVerticalPadding else parentVerticalPadding
    val contentBottomPadding = if (node.depth > 0) nestedVerticalPadding else spacing.xs
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = verticalPadding)
            .height(IntrinsicSize.Min)
            .pointerInput(comment.id) {
                detectTapGestures(
                    onTap = { onToggleComment(comment.id) },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCommentOptionsDialog = true
                    }
                )
            },
        verticalAlignment = Alignment.Top
    ) {
        if (node.depth > 0) {
            Spacer(modifier = Modifier.width(spacing.sm * node.depth.toFloat()))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(SubredditColor.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.width(spacing.xs))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (node.depth > 0) spacing.xs else 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val authorColor = SubredditColor
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Text(
                        text = comment.author,
                        color = authorColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    // Hide flair if user is OP
                    if (flairText != null && !node.isOp) {
                        Surface(
                            color = FlairBackgroundColor,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(6.dp),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showFlairDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Extract emoji URL and display text from richtext if available
                                val (emojiUrl, displayText) = remember(comment.authorFlairRichtext, flairText, flairEmojiLookup) {
                                    val richtext = comment.authorFlairRichtext
                                    if (!richtext.isNullOrEmpty()) {
                                        // Get emoji URL directly from richtext
                                        val emoji = richtext.firstOrNull { it.type == "emoji" }
                                        val url = emoji?.url

                                        // Get display text from text parts in richtext
                                        val text = richtext
                                            .filter { it.type == "text" }
                                            .mapNotNull { it.text }
                                            .joinToString("")
                                            .trim()

                                        Pair(url, text.ifBlank { flairText })
                                    } else {
                                        // Fallback to old method using lookup
                                        val cleaned = flairText.replace(Regex(":[^:\\s]+:"), "").trim()
                                        val alias = Regex(":[^\\s]+:").find(flairText)?.value?.lowercase()
                                        val key = flairText.lowercase()
                                        val altKey = cleaned.lowercase()
                                        val url = alias?.let { flairEmojiLookup[it] }
                                            ?: flairEmojiLookup[altKey]
                                            ?: flairEmojiLookup[key]
                                        Pair(url, cleaned.ifBlank { flairText })
                                    }
                                }

                                if (!emojiUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = emojiUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = displayText,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (node.isAutoModerator) {
                        Surface(
                            color = ModLabelColor.copy(alpha = 0.18f),
                            contentColor = ModLabelColor,
                            shape = RoundedCornerShape(999.dp),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = "MOD",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(
                                    horizontal = spacing.sm,
                                    vertical = spacing.xs * 0.75f
                                )
                            )
                        }
                    }
                    if (node.isOp) {
                        Surface(
                            color = OpLabelColor.copy(alpha = 0.18f),
                            contentColor = OpLabelColor,
                            shape = RoundedCornerShape(999.dp),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = "OP",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(
                                    horizontal = spacing.sm,
                                    vertical = spacing.xs * 0.75f
                                )
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = MetaInfoColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatRelativeTime(comment.createdUtc),
                        color = MetaInfoColor,
                        fontSize = 12.sp
                    )
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = MetaInfoColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatCount(comment.score),
                        color = MetaInfoColor,
                        fontSize = 12.sp
                    )
                }
            }
            if (!node.isCollapsed) {
                LinkifiedText(
                    text = comment.body,
                    htmlText = comment.bodyHtml,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TitleColor.copy(alpha = 0.9f),
                    linkColor = SubredditColor,
                    quoteColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = spacing.xs, bottom = contentBottomPadding),
                    onLinkClick = onOpenLink,
                    onImageClick = onOpenImage,
                    onTextClick = { onToggleComment(comment.id) },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCommentOptionsDialog = true
                    }
                )
            } else {
                val collapsedInteraction = remember { MutableInteractionSource() }
                val repliesLabel = when (node.replyCount) {
                    0 -> "Replies hidden (0 replies)"
                    1 -> "Replies hidden (1 reply)"
                    else -> "Replies hidden (${formatCount(node.replyCount)} replies)"
                }
                Text(
                    text = repliesLabel,
                    color = MetaInfoColor,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = spacing.xs, bottom = contentBottomPadding)
                        .combinedClickable(
                            interactionSource = collapsedInteraction,
                            indication = null,
                            onClick = { onToggleComment(comment.id) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showCommentOptionsDialog = true
                            }
                        )
                )
            }
        }
    }
    if (showFlairDialog && flairText != null) {
        AlertDialog(
            onDismissRequest = { showFlairDialog = false },
            title = { Text(text = "User flair", color = TitleColor) },
            text = {
                // Extract emoji URL and display text from richtext if available
                val (emojiUrl, displayText) = remember(comment.authorFlairRichtext, flairText, flairEmojiLookup) {
                    val richtext = comment.authorFlairRichtext
                    if (!richtext.isNullOrEmpty()) {
                        // Get emoji URL directly from richtext
                        val emoji = richtext.firstOrNull { it.type == "emoji" }
                        val url = emoji?.url

                        // Get display text from text parts in richtext
                        val text = richtext
                            .filter { it.type == "text" }
                            .mapNotNull { it.text }
                            .joinToString("")
                            .trim()

                        Pair(url, text.ifBlank { flairText })
                    } else {
                        // Fallback to old method using lookup
                        val cleaned = flairText.replace(Regex(":[^:\\s]+:"), "").trim()
                        val alias = Regex(":[^\\s]+:").find(flairText)?.value?.lowercase()
                        val key = flairText.lowercase()
                        val altKey = cleaned.lowercase()
                        val url = alias?.let { flairEmojiLookup[it] }
                            ?: flairEmojiLookup[altKey]
                            ?: flairEmojiLookup[key]
                        Pair(url, cleaned.ifBlank { flairText })
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!emojiUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = emojiUrl,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = displayText,
                        color = TitleColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFlairDialog = false }) {
                    Text(text = "Close", color = SubredditColor)
                }
            }
        )
    }

    if (showCommentOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showCommentOptionsDialog = false },
            title = { Text(text = "Comment Options", color = TitleColor) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    // Option 1: View Profile
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCommentOptionsDialog = false
                                val profileUrl = "https://www.reddit.com/user/${comment.author}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl))
                                context.startActivity(intent)
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.md)
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = SubredditColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "View u/${comment.author}",
                                color = TitleColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Option 2: Copy Text
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCommentOptionsDialog = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Comment", comment.body)
                                clipboard.setPrimaryClip(clip)
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.md)
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = null,
                                tint = SubredditColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Copy text",
                                color = TitleColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Option 3: Share Comment
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCommentOptionsDialog = false
                                val shareText = "${comment.body}\n\n- u/${comment.author}"
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share comment"))
                            },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.md)
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = null,
                                tint = SubredditColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Share comment",
                                color = TitleColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCommentOptionsDialog = false }) {
                    Text(text = "Cancel", color = SubredditColor)
                }
            }
        )
    }
}

@Composable
private fun LoadMoreRepliesItem(
    node: PostDetailViewModel.CommentListItem.LoadMoreRepliesNode,
    onLoadMoreReplies: (String) -> Unit
) {
    val spacing = MaterialSpacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onLoadMoreReplies(node.parentId) }
            )
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.depth > 0) {
            Spacer(modifier = Modifier.width(spacing.sm * node.depth.toFloat()))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(spacing.lg)
                    .background(SubredditColor.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.width(spacing.xs))
        }
        val remaining = node.remainingCount
        val label = if (remaining == 1) {
            "load more comments... (1 reply)"
        } else {
            "load more comments... ($remaining replies)"
        }
        val paddedLabel = label + "\u00A0\u00A0\u00A0"
        Text(
            text = paddedLabel,
            color = SubredditColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoteRepliesItem(
    node: PostDetailViewModel.CommentListItem.RemoteRepliesNode,
    onLoadMoreComments: (String) -> Unit
) {
    val spacing = MaterialSpacing
    val isClickable = !node.isLoading && !node.hasError
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isClickable) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onLoadMoreComments(node.parentId) }
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.depth > 0) {
            Spacer(modifier = Modifier.width(spacing.sm * node.depth.toFloat()))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(spacing.lg)
                    .background(SubredditColor.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.width(spacing.xs))
        }
        when {
            node.isLoading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                    Text(text = "Loading replies…", color = MetaInfoColor, fontSize = 12.sp)
                }
            }
            node.hasError -> {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "Failed to load replies",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                    TextButton(onClick = { onLoadMoreComments(node.parentId) }) {
                        Text(text = "Retry", color = SubredditColor)
                    }
                }
            }
            else -> {
                val label = when (node.pendingCount) {
                    0 -> "load more comments..."
                    1 -> "load more comments... (1 reply)"
                    else -> "load more comments... (${formatCount(node.pendingCount)} replies)"
                }
                val paddedLabel = label + "\u00A0\u00A0\u00A0"
                Text(
                    text = paddedLabel,
                    color = SubredditColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(SpacerBackgroundColor).fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}

private fun shareText(context: Context, text: String, chooserTitle: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooserIntent) }
}

private fun RedditPost.permalinkUrl(): String {
    val trimmed = permalink.trim()
    if (trimmed.startsWith("http", ignoreCase = true)) {
        return trimmed
    }
    val path = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return "https://www.reddit.com$path"
}

private fun RedditPostMedia.shareableUrl(): String? = when (this) {
    RedditPostMedia.None -> null
    is RedditPostMedia.Image -> url.takeIf { it.isNotBlank() }
    is RedditPostMedia.Video -> url.takeIf { it.isNotBlank() }
    is RedditPostMedia.Link -> url.takeIf { it.isNotBlank() }
    is RedditPostMedia.Gallery -> images.firstOrNull()?.url?.takeIf { it.isNotBlank() }
    is RedditPostMedia.YouTube -> watchUrl.takeIf { it.isNotBlank() }
    is RedditPostMedia.RedGifs -> embedUrl.takeIf { it.isNotBlank() }
    is RedditPostMedia.Streamable -> url.takeIf { it.isNotBlank() }
    is RedditPostMedia.StreamFF -> url.takeIf { it.isNotBlank() }
    is RedditPostMedia.StreamIn -> url.takeIf { it.isNotBlank() }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, color = TitleColor, modifier = Modifier.padding(bottom = 16.dp))
        Button(onClick = onRetry) {
            Text(text = "Retry")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostDetailScreenPreview() {
    MunchForRedditTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
        val samplePost = RedditPost(
            id = "t3_preview",
            title = "Compose preview of a post detail screen",
            author = "preview_author",
            subreddit = "r/androiddev",
            selfText = "This is a sample self post used for previews.",
            thumbnailUrl = null,
            url = "https://www.reddit.com",
            domain = "reddit.com",
            permalink = "/r/androiddev/comments/t3_preview",
            commentCount = 56,
            score = 1234,
            createdUtc = System.currentTimeMillis() / 1000,
            media = RedditPostMedia.Image(
                url = "https://placekitten.com/800/600",
                width = 800,
                height = 600
            ),
            isStickied = false,
            isNsfw = false
        )

        val comment1 = RedditComment(
            id = "c1",
            parentId = "t3_t3_preview",
            author = "commenter1",
            body = "This is a great post!",
            score = 120,
            createdUtc = System.currentTimeMillis() / 1000 - 3600
        )
        val comment2 = RedditComment(
            id = "c2",
            parentId = "t3_t3_preview",
            author = "AutoModerator",
            body = "Be nice and follow the rules.",
            score = 1,
            createdUtc = System.currentTimeMillis() / 1000 - 7200
        )

        val previewComments = listOf(
            PostDetailViewModel.CommentListItem.CommentNode(
                comment = comment1,
                depth = 0,
                isCollapsed = false,
                isAutoModerator = false,
                isVisualModerator = false,
                isOp = false,
                replyCount = 0
            ),
            PostDetailViewModel.CommentListItem.CommentNode(
                comment = comment2,
                depth = 0,
                isCollapsed = false,
                isAutoModerator = true,
                isVisualModerator = false,
                isOp = false,
                replyCount = 0
            )
        )

        PostDetailScreen(
            uiState = PostDetailViewModel.PostDetailUiState(
                isLoading = false,
                isAppending = false,
                post = samplePost,
                comments = previewComments,
                errorMessage = null,
                selectedSort = "top",
                sortOptions = listOf("top", "best", "new"),
                hasMoreComments = false
            ),
            onBack = {},
            onRetry = {},
            onSelectSort = {},
            onToggleComment = {},
            onUserLoadMoreComments = {},
            onAutoLoadMoreComments = {},
            onLoadRemoteReplies = {},
            onLoadMoreReplies = {},
            onOpenSubreddit = {},
            onOpenImage = {},
            onOpenGallery = { _, _ -> },
            onOpenYouTube = {},
            onOpenLink = {},
            onSearchClick = {},
            subredditOptions = SubredditCatalog.defaultSubreddits,
            onSettingsClick = {}
        )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostDetailScreenLoadingPreview() {
    MunchForRedditTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PostDetailScreen(
                uiState = PostDetailViewModel.PostDetailUiState(isLoading = true),
                onBack = {},
                onRetry = {},
                onSelectSort = {},
                onToggleComment = {},
                onUserLoadMoreComments = {},
                onAutoLoadMoreComments = {},
                onLoadRemoteReplies = {},
                onLoadMoreReplies = {},
                onOpenSubreddit = {},
                onOpenImage = {},
                onOpenGallery = { _, _ -> },
                onOpenYouTube = {},
                onOpenLink = {},
                onSearchClick = {},
                subredditOptions = SubredditCatalog.defaultSubreddits,
                onSettingsClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostDetailScreenErrorPreview() {
    MaterialTheme {
        PostDetailScreen(
            uiState = PostDetailViewModel.PostDetailUiState(errorMessage = "Unable to load post"),
            onBack = {},
            onRetry = {},
            onSelectSort = {},
            onToggleComment = {},
            onUserLoadMoreComments = {},
            onAutoLoadMoreComments = {},
            onLoadRemoteReplies = {},
            onLoadMoreReplies = {},
            onOpenSubreddit = {},
            onOpenImage = {},
            onOpenGallery = { _, _ -> },
            onOpenYouTube = {},
            onOpenLink = {},
            onSearchClick = {},
            subredditOptions = SubredditCatalog.defaultSubreddits,
            onSettingsClick = {}
        )
    }
}
