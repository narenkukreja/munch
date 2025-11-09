package com.munch.reddit.feature.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.munch.reddit.feature.shared.LoadingIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TextButton
import androidx.compose.material3.FabPosition
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.domain.model.RedditPostMedia
import com.munch.reddit.domain.SubredditCatalog
import com.munch.reddit.R
import android.widget.Toast
import androidx.compose.ui.graphics.RectangleShape
import org.koin.androidx.compose.koinViewModel
import com.munch.reddit.feature.shared.FloatingToolbar
import com.munch.reddit.feature.shared.FloatingToolbarButton
import com.munch.reddit.feature.shared.InfoChip
import com.munch.reddit.activity.TableViewerActivity
import com.munch.reddit.feature.shared.RedditPostMediaContent
import com.munch.reddit.feature.shared.SubredditSideSheet
import com.munch.reddit.feature.shared.formatCount
import com.munch.reddit.feature.shared.formatRelativeTime
import com.munch.reddit.feature.shared.TableAttachmentList
import com.munch.reddit.feature.shared.LinkifiedText
import com.munch.reddit.feature.shared.parseHtmlText
import com.munch.reddit.feature.shared.parseTablesFromHtml
import com.munch.reddit.feature.shared.stripTablesFromHtml
import com.munch.reddit.feature.shared.openLinkInCustomTab
import com.munch.reddit.ui.theme.MaterialSpacing
import com.munch.reddit.ui.theme.MunchForRedditTheme
import com.munch.reddit.theme.PostCardStyle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOAD_MORE_THRESHOLD = 5

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedditFeedScreen(
    uiState: RedditFeedViewModel.RedditFeedUiState,
    postCardStyle: PostCardStyle,
    subredditOptions: List<String>,
    sortOptions: List<RedditFeedViewModel.FeedSortOption>,
    topTimeRangeOptions: List<RedditFeedViewModel.TopTimeRange>,
    onSelectSubreddit: (String) -> Unit,
    onSelectSort: (RedditFeedViewModel.FeedSortOption) -> Unit,
    onSelectTopTimeRange: (RedditFeedViewModel.TopTimeRange) -> Unit,
    onPostSelected: (RedditPost) -> Unit,
    onRetry: () -> Unit,
    onTitleTapped: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    listState: LazyListState? = null,
    onImageClick: (String) -> Unit = {},
    onGalleryPreview: (List<String>, Int) -> Unit = { _, _ -> },
    onYouTubeSelected: (String) -> Unit = {},
    onVideoFeedClick: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    isAppending: Boolean = false,
    canLoadMore: Boolean = true,
    viewModel: RedditFeedViewModel? = null,
    onPostDismissed: (RedditPost) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSubredditSheet by remember { mutableStateOf(false) }
    val displayTitle = formatToolbarTitle(uiState.selectedSubreddit)
    val feedListState = listState ?: rememberLazyListState()
    val selectedIndex = subredditOptions.indexOfFirst { it.equals(uiState.selectedSubreddit, ignoreCase = true) }
        .takeIf { it >= 0 } ?: 0
    val spacing = MaterialSpacing
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val sideSheetScrollState = remember(viewModel) {
        ScrollState(viewModel?.getSideSheetScroll() ?: 0)
    }

    LaunchedEffect(uiState.selectedSubreddit, uiState.scrollPosition) {
        val targetScroll = uiState.scrollPosition
        if (targetScroll != null) {
            val currentIndex = feedListState.firstVisibleItemIndex
            val currentOffset = feedListState.firstVisibleItemScrollOffset
            if (currentIndex != targetScroll.firstVisibleItemIndex ||
                currentOffset != targetScroll.firstVisibleItemScrollOffset
            ) {
                feedListState.scrollToItem(
                    index = targetScroll.firstVisibleItemIndex,
                    scrollOffset = targetScroll.firstVisibleItemScrollOffset
                )
            }
        } else if (feedListState.firstVisibleItemIndex != 0 || feedListState.firstVisibleItemScrollOffset != 0) {
            feedListState.scrollToItem(0, 0)
        }
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
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = SpacerBackgroundColor,
        topBar = {
            RedditTopBar(
                title = displayTitle,
                onTap = onTitleTapped,
                sortOptions = sortOptions,
                selectedSort = uiState.selectedSort,
                topTimeRangeOptions = topTimeRangeOptions,
                selectedTopTimeRange = uiState.selectedTopTimeRange,
                onSelectSort = onSelectSort,
                onSelectTopTimeRange = onSelectTopTimeRange,
                subredditOptions = subredditOptions,
                selectedIndex = selectedIndex,
                onSelectSubreddit = onSelectSubreddit,
                scrollBehavior = scrollBehavior,
                onVideoFeedClick = onVideoFeedClick
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Render previous subreddit underneath (if exists)
            val previousSubreddit = viewModel?.getPreviousSubreddit()
            val previousFeed = viewModel?.getPreviousSubredditFeed()
            if (viewModel != null && previousSubreddit != null && previousFeed != null) {
                val previousScrollPos = viewModel.getScrollPosition(previousSubreddit)
                val previousListState = remember(previousSubreddit) {
                    LazyListState(
                        firstVisibleItemIndex = previousScrollPos?.firstVisibleItemIndex ?: 0,
                        firstVisibleItemScrollOffset = previousScrollPos?.firstVisibleItemScrollOffset ?: 0
                    )
                }

                // Background layer - previous subreddit
                PostList(
                    posts = previousFeed,
                    isRefreshing = false,
                    onRefresh = {},
                    selectedSubreddit = previousSubreddit,
                    subredditOptions = subredditOptions,
                    onSubredditTapped = {},
                    onPostSelected = {},
                    readPostIds = uiState.readPostIds,
                    hideReadPosts = false,
                    modifier = Modifier.fillMaxSize(),
                    listState = previousListState,
                    onImageClick = {},
                    onGalleryPreview = onGalleryPreview,
                    onYouTubeSelected = {},
                    onLoadMore = {},
                    isAppending = false,
                    canLoadMore = false,
                    contentPadding = PaddingValues(vertical = spacing.lg),
                    postCardStyle = postCardStyle
                )
            }

            // Foreground layer - current subreddit
            when {
                uiState.posts.isEmpty() && uiState.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                uiState.posts.isEmpty() && uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize()
                )
                else -> {
                    PostList(
                        posts = uiState.posts,
                        isRefreshing = uiState.isLoading,
                        onRefresh = onRetry,
                        selectedSubreddit = uiState.selectedSubreddit,
                        subredditOptions = subredditOptions,
                        onSubredditTapped = { showSubredditSheet = true },
                        onPostSelected = onPostSelected,
                        readPostIds = uiState.readPostIds,
                        hideReadPosts = uiState.hideReadPosts,
                        modifier = Modifier.fillMaxSize(),
                        listState = feedListState,
                        onImageClick = onImageClick,
                        onGalleryPreview = onGalleryPreview,
                        onYouTubeSelected = onYouTubeSelected,
                        onSubredditSelected = onSelectSubreddit,
                        onLoadMore = onLoadMore,
                        isAppending = isAppending,
                        canLoadMore = canLoadMore,
                        contentPadding = PaddingValues(vertical = spacing.lg),
                        onPostDismissed = onPostDismissed,
                        postCardStyle = postCardStyle
                )

                    // Show loading overlay when switching subreddits
                    if (uiState.isLoading && previousSubreddit != null && previousSubreddit != uiState.selectedSubreddit) {
                        LoadingState(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            SubredditSideSheet(
                visible = showSubredditSheet,
                selectedSubreddit = uiState.selectedSubreddit,
                subreddits = subredditOptions,
                onSelectSubreddit = { subreddit ->
                    showSubredditSheet = false
                    onSelectSubreddit(subreddit)
                },
                onDismissRequest = { showSubredditSheet = false },
                onSearchClick = onSearchClick,
                subredditIcons = uiState.subredditIcons,
                exploreSubreddits = SubredditCatalog.exploreSubreddits,
                onSettingsClick = onSettingsClick,
                scrollState = sideSheetScrollState
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
                    FloatingToolbar(
                        buttons = listOf(
                            FloatingToolbarButton(
                                icon = ImageVector.vectorResource(id = R.drawable.scroll_to_top_arrow),
                                contentDescription = "Scroll to top",
                                onClick = {
                                    coroutineScope.launch {
                                        feedListState.animateScrollToItem(0)
                                    }
                                },
                                iconTint = SubredditColor,
                                iconSize = 20.dp
                            ),
                            FloatingToolbarButton(
                                icon = Icons.Default.Refresh,
                                contentDescription = "Refresh feed",
                                onClick = {
                                    onRetry()
                                    coroutineScope.launch {
                                        feedListState.animateScrollToItem(0)
                                    }
                                },
                                iconTint = SubredditColor,
                                iconSize = 20.dp
                            ),
                            FloatingToolbarButton(
                                icon = ImageVector.vectorResource(id = R.drawable.ic_read_posts),
                                contentDescription = if (uiState.hideReadPosts) "Show read posts" else "Hide read posts",
                                onClick = { viewModel?.toggleHideReadPosts() },
                                iconTint = SubredditColor,
                                iconSize = 20.dp
                            ),
                            FloatingToolbarButton(
                                icon = Icons.Default.Menu,
                                contentDescription = "Browse subreddits",
                                onClick = { showSubredditSheet = true },
                                iconTint = SubredditColor,
                                iconSize = 20.dp
                            )
                        )
                    )
                }
            }
        }
    }

    if (showSubredditSheet) {
        BackHandler { showSubredditSheet = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RedditTopBar(
    title: String,
    onTap: () -> Unit,
    sortOptions: List<RedditFeedViewModel.FeedSortOption>,
    selectedSort: RedditFeedViewModel.FeedSortOption,
    topTimeRangeOptions: List<RedditFeedViewModel.TopTimeRange>,
    selectedTopTimeRange: RedditFeedViewModel.TopTimeRange,
    onSelectSort: (RedditFeedViewModel.FeedSortOption) -> Unit,
    onSelectTopTimeRange: (RedditFeedViewModel.TopTimeRange) -> Unit,
    subredditOptions: List<String>,
    selectedIndex: Int,
    onSelectSubreddit: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onVideoFeedClick: () -> Unit = {}
) {
    var isFilterMenuExpanded by remember { mutableStateOf(false) }

    LargeTopAppBar(
        title = {
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 20)) + slideInVertically { it / 10 }) togetherWith
                        (fadeOut(animationSpec = tween(durationMillis = 140)) + slideOutVertically { -it / 10 })
                },
                label = "toolbar_title_transition"
            ) { animatedTitle ->
                Text(
                    text = animatedTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TitleColor,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onTap)
                )
            }
        },
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = PostBackgroundColor,
            scrolledContainerColor = PostBackgroundColor,
            titleContentColor = TitleColor,
            actionIconContentColor = TitleColor,
            navigationIconContentColor = TitleColor
        ),
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = { isFilterMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Filter posts",
                    tint = TitleColor
                )
            }
            FilterDropdownMenu(
                expanded = isFilterMenuExpanded,
                onDismissRequest = { isFilterMenuExpanded = false },
                sortOptions = sortOptions,
                selectedSort = selectedSort,
                topTimeRangeOptions = topTimeRangeOptions,
                selectedTopTimeRange = selectedTopTimeRange,
                onSelectSort = { option ->
                    onSelectSort(option)
                    if (option != RedditFeedViewModel.FeedSortOption.TOP) {
                        isFilterMenuExpanded = false
                    }
                },
                onSelectTopTimeRange = { range ->
                    onSelectTopTimeRange(range)
                    isFilterMenuExpanded = false
                }
            )
        }
    )
}

@Composable
private fun FilterDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    sortOptions: List<RedditFeedViewModel.FeedSortOption>,
    selectedSort: RedditFeedViewModel.FeedSortOption,
    topTimeRangeOptions: List<RedditFeedViewModel.TopTimeRange>,
    selectedTopTimeRange: RedditFeedViewModel.TopTimeRange,
    onSelectSort: (RedditFeedViewModel.FeedSortOption) -> Unit,
    onSelectTopTimeRange: (RedditFeedViewModel.TopTimeRange) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = PostBackgroundColor,
        tonalElevation = 0.dp
    ) {
        sortOptions.forEach { option ->
            DropdownMenuItem(
                colors = MenuDefaults.itemColors(
                    textColor = TitleColor,
                    leadingIconColor = SubredditColor,
                    trailingIconColor = SubredditColor,
                    disabledTextColor = TitleColor.copy(alpha = 0.4f)
                ),
                text = { Text(text = option.displayLabel) },
                leadingIcon = if (option == selectedSort) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = SubredditColor
                        )
                    }
                } else {
                    null
                },
                onClick = { onSelectSort(option) }
            )
        }
        if (selectedSort == RedditFeedViewModel.FeedSortOption.TOP && topTimeRangeOptions.isNotEmpty()) {
            Divider(color = SubredditColor.copy(alpha = 0.2f))
            Text(
                text = "Top time filter",
                color = TitleColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            topTimeRangeOptions.forEach { range ->
                DropdownMenuItem(
                    modifier = Modifier.padding(start = 8.dp),
                    colors = MenuDefaults.itemColors(
                        textColor = TitleColor,
                        leadingIconColor = SubredditColor,
                        trailingIconColor = SubredditColor,
                        disabledTextColor = TitleColor.copy(alpha = 0.4f)
                    ),
                    text = { Text(text = range.displayLabel) },
                    leadingIcon = if (range == selectedTopTimeRange) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = SubredditColor
                            )
                        }
                    } else {
                        null
                    },
                    onClick = { onSelectTopTimeRange(range) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpacerBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onRetry) {
            Text(text = "Retry")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterialApi::class)
@Composable
private fun PostList(
    posts: List<RedditPost>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    selectedSubreddit: String,
    subredditOptions: List<String>,
    onSubredditTapped: () -> Unit,
    onPostSelected: (RedditPost) -> Unit,
    readPostIds: Set<String>,
    hideReadPosts: Boolean,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    onImageClick: (String) -> Unit,
    onGalleryPreview: (List<String>, Int) -> Unit = { _, _ -> },
    onYouTubeSelected: (String) -> Unit,
    onSubredditSelected: (String) -> Unit = {},
    onLoadMore: () -> Unit,
    isAppending: Boolean,
    canLoadMore: Boolean,
    contentPadding: PaddingValues,
    onPostDismissed: (RedditPost) -> Unit = {},
    postCardStyle: PostCardStyle = PostCardStyle.CardV1
) {
    // Track dismissed posts for animation
    var dismissedPostIds by remember { mutableStateOf(setOf<String>()) }

    // Snapshot of read posts when hideReadPosts was enabled
    // This prevents newly-read posts from disappearing while hideReadPosts is active
    var hiddenReadPostIds by remember { mutableStateOf(setOf<String>()) }

    // Update the snapshot when hideReadPosts changes
    LaunchedEffect(hideReadPosts) {
        if (hideReadPosts) {
            // Capture current read posts
            hiddenReadPostIds = readPostIds
        } else {
            // Clear when showing all posts
            hiddenReadPostIds = emptySet()
        }
    }

    LaunchedEffect(posts, canLoadMore, isAppending) {
        if (!canLoadMore || posts.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex == null) return@collect
                val lastPostIndex = posts.lastIndex
                if (lastPostIndex < 0) return@collect
                val triggerIndex = (lastPostIndex - LOAD_MORE_THRESHOLD).coerceAtLeast(0)
                if (!isAppending && lastVisibleIndex >= triggerIndex) {
                    onLoadMore()
                }
            }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(SpacerBackgroundColor)
            .navigationBarsPadding()
    ) {
        val spacing = MaterialSpacing
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            items(
                items = posts.filter { post ->
                    val isDismissed = dismissedPostIds.contains(post.id)
                    val isHidden = hiddenReadPostIds.contains(post.id)
                    // Only include posts that should be visible
                    !isDismissed && !isHidden
                },
                key = { it.id },
                contentType = { post ->
                    when {
                        post.media is RedditPostMedia.Image -> "image_post"
                        post.media is RedditPostMedia.Video -> "video_post"
                        post.selfText.isNotBlank() -> "text_post"
                        else -> "link_post"
                    }
                }
            ) { post ->
                val isRead = readPostIds.contains(post.id)

                Box(
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(durationMillis = 300),
                        fadeOutSpec = tween(durationMillis = 300),
                        placementSpec = tween(durationMillis = 300)
                    )
                ) {
                    if (isRead) {
                        // For read posts, don't use swipe to dismiss - just show the post
                        RedditPostItem(
                            post = post,
                            selectedSubreddit = selectedSubreddit,
                            onSubredditTapped = onSubredditTapped,
                            onPostSelected = onPostSelected,
                            onImageClick = onImageClick,
                            onGalleryPreview = onGalleryPreview,
                            onYouTubeSelected = onYouTubeSelected,
                            onSubredditSelected = onSubredditSelected,
                            isRead = isRead,
                            postCardStyle = postCardStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0.55f)
                        )
                    } else {
                        // For unread posts, enable swipe to dismiss
                        val dismissState = rememberDismissState(
                            confirmStateChange = { value ->
                                if (value == DismissValue.DismissedToEnd) {
                                    // Add to dismissed set to trigger exit animation
                                    dismissedPostIds = dismissedPostIds + post.id
                                    // Delay the actual dismiss callback to allow animation to complete
                                    kotlinx.coroutines.MainScope().launch {
                                        kotlinx.coroutines.delay(300)
                                        onPostDismissed(post)
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        )
                        SwipeToDismiss(
                            state = dismissState,
                            modifier = Modifier.fillMaxWidth(),
                            directions = setOf(DismissDirection.StartToEnd),
                            dismissThresholds = { FractionalThreshold(0.35f) },
                            background = {
                                val isSwipeActive = dismissState.progress.fraction > 0f
                                val backgroundColor by animateColorAsState(
                                    targetValue = if (isSwipeActive) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        Color.Transparent
                                    },
                                    label = "dismiss_background_color"
                                )
                                val contentColor = MaterialTheme.colorScheme.onErrorContainer
                                val labelAlpha by animateFloatAsState(
                                    targetValue = if (isSwipeActive) 1f else 0f,
                                    label = "dismiss_label_alpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(backgroundColor)
                                        .padding(horizontal = spacing.lg),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VisibilityOff,
                                        contentDescription = "Hide & mark read",
                                        tint = contentColor,
                                        modifier = Modifier.alpha(labelAlpha)
                                    )
                                }
                            }
                        ) {
                            RedditPostItem(
                                post = post,
                                selectedSubreddit = selectedSubreddit,
                                onSubredditTapped = onSubredditTapped,
                                onPostSelected = onPostSelected,
                                onImageClick = onImageClick,
                                onGalleryPreview = onGalleryPreview,
                                onYouTubeSelected = onYouTubeSelected,
                                onSubredditSelected = onSubredditSelected,
                                isRead = isRead,
                                postCardStyle = postCardStyle,
                                modifier = Modifier.fillMaxWidth()
                            )
                    }
                }
            }
            }
            if (isAppending) {
                item("loading_footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            } else if (!canLoadMore && posts.isNotEmpty()) {
                item("end_of_feed_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RedditPostItem(
    post: RedditPost,
    selectedSubreddit: String,
    onSubredditTapped: () -> Unit,
    onPostSelected: (RedditPost) -> Unit,
    onImageClick: (String) -> Unit,
    onGalleryPreview: (List<String>, Int) -> Unit = { _, _ -> },
    onYouTubeSelected: (String) -> Unit,
    onSubredditSelected: (String) -> Unit = {},
    isRead: Boolean = false,
    postCardStyle: PostCardStyle = PostCardStyle.CardV1,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = MaterialSpacing

    val subredditLabel = remember(post.id) {
        val raw = post.subreddit.removePrefix("r/").removePrefix("R/")
        "r/${raw.lowercase()}"
    }
    val displayDomain = remember(post.id) {
        post.domain.removePrefix("www.")
    }
    val isGlobalFeed = remember(selectedSubreddit) {
        selectedSubreddit.equals("all", ignoreCase = true)
    }
    val domainLabel = remember(post.id, isGlobalFeed) {
        displayDomain.ifBlank {
            if (isGlobalFeed) subredditLabel else "u/${post.author}"
        }
    }
    val plainTitle = remember(post.id) { parseHtmlText(post.title) }
    val formattedTitle = remember(plainTitle) {
        buildAnnotatedString { append(plainTitle) }
    }
    val bottomLabel = remember(post.id, isGlobalFeed) {
        if (isGlobalFeed) subredditLabel else "u/${post.author}"
    }
    val authorLabel = remember(post.id) {
        post.author.removePrefix("u/").removePrefix("U/")
    }
    val hasSelfTextContent = remember(post.id, post.selfText, post.media) {
        post.selfText.isNotBlank() && post.media is RedditPostMedia.None
    }
    val hasMediaContent = remember(post.id, post.media) {
        post.media !is RedditPostMedia.None
    }
    val hasPrimaryContent = hasSelfTextContent || hasMediaContent
    val commentsLabel = remember(post.id, post.commentCount) { formatCount(post.commentCount) }
    val votesLabel = remember(post.id, post.score) { formatCount(post.score) }

    @Composable
    fun StatusBadge(label: String, color: Color) {
        Surface(
            color = color.copy(alpha = 0.18f),
            contentColor = color,
            shape = RoundedCornerShape(999.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    horizontal = spacing.sm,
                    vertical = spacing.xs * 0.75f
                )
            )
        }
    }

    @Composable
    fun SelfTextSection() {
        LinkifiedText(
            text = post.selfText,
            htmlText = post.selfTextHtml,
            style = MaterialTheme.typography.bodyMedium,
            color = TitleColor.copy(alpha = 0.9f),
            linkColor = SubredditColor,
            quoteColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            onLinkClick = { url -> openLinkInCustomTab(context, url) },
            onImageClick = onImageClick,
            onTextClick = { onPostSelected(post) }
        )
    }

    @Composable
    fun MediaSection() {
        RedditPostMediaContent(
            media = post.media,
            modifier = Modifier.fillMaxWidth(),
            onLinkClick = { url -> openLinkInCustomTab(context, url) },
            onImageClick = { url -> onImageClick(url) },
            onGalleryClick = onGalleryPreview,
            onYoutubeClick = { videoId, _ ->
                val youtube = post.media as? RedditPostMedia.YouTube
                val id = videoId.ifBlank { youtube?.videoId.orEmpty() }
                if (id.isNotBlank()) onYouTubeSelected(id)
            }
        )
    }

    fun Modifier.subredditClickable(): Modifier {
        return then(
            if (isGlobalFeed) {
                Modifier.clickable {
                    onSubredditTapped()
                    val subredditName = post.subreddit.removePrefix("r/").removePrefix("R/")
                    onSubredditSelected(subredditName)
                }
            } else {
                Modifier
            }
        )
    }

    Surface(
        modifier = modifier,
        onClick = { onPostSelected(post) },
        shape = RectangleShape,
        color = if (isRead) SpacerBackgroundColor else PostBackgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        contentColor = TitleColor
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Text(
                    text = formattedTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TitleColor
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    if (post.media !is RedditPostMedia.Link && domainLabel.isNotBlank()) {
                        Text(
                            text = domainLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (post.isNsfw || post.isStickied) {
                        if (post.isNsfw) {
                            StatusBadge(
                                label = "NSFW",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (post.isStickied) {
                            StatusBadge(
                                label = "PINNED",
                                color = PinnedLabelColor
                            )
                        }
                    }
                }
            }

            if (post.media is RedditPostMedia.None) {
                val parsedTables = remember(post.id, post.selfTextHtml) {
                    parseTablesFromHtml(post.selfTextHtml)
                }
                val sanitizedHtml = remember(post.id, post.selfTextHtml, parsedTables.size) {
                    if (parsedTables.isNotEmpty()) {
                        stripTablesFromHtml(post.selfTextHtml)
                    } else {
                        post.selfTextHtml
                    }
                }
                val bodyText = remember(post.id, sanitizedHtml, post.selfText) {
                    sanitizedHtml?.let { parseHtmlText(it).trim() } ?: post.selfText
                }
                val hasBodyText = bodyText.isNotBlank()
                if (hasBodyText) {
                    LinkifiedText(
                        text = bodyText,
                        htmlText = sanitizedHtml,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TitleColor.copy(alpha = 0.9f),
                        linkColor = SubredditColor,
                        quoteColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.sm),
                        onLinkClick = { url -> openLinkInCustomTab(context, url) },
                        onImageClick = onImageClick,
                        onTextClick = { onPostSelected(post) }
                    )
                }
                if (parsedTables.isNotEmpty()) {
                    TableAttachmentList(
                        tables = parsedTables,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = spacing.lg,
                                vertical = if (hasBodyText) spacing.xs else spacing.sm
                            ),
                        onViewTable = { tableIndex ->
                            val intent = TableViewerActivity.createIntent(
                                context = context,
                                postTitle = plainTitle,
                                tables = parsedTables,
                                startIndex = tableIndex
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }

            RedditPostMediaContent(
                media = post.media,
                modifier = Modifier.fillMaxWidth(),
                onLinkClick = { url -> openLinkInCustomTab(context, url) },
                onImageClick = { url -> onImageClick(url) },
                onGalleryClick = onGalleryPreview,
                onYoutubeClick = { videoId, url ->
                    val youtube = post.media as? RedditPostMedia.YouTube
                    val id = videoId.ifBlank { youtube?.videoId.orEmpty() }
                    if (id.isNotBlank()) onYouTubeSelected(id)
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .subredditClickable(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    if (bottomLabel.isNotBlank()) {
                        Text(
                            text = bottomLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = SubredditColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    InfoChip(
                        icon = Icons.Filled.AccessTime,
                        label = formatRelativeTime(post.createdUtc)
                    )
                    InfoChip(
                        icon = Icons.Filled.ChatBubble,
                        label = commentsLabel
                    )
                    InfoChip(
                        icon = Icons.Filled.ArrowUpward,
                        label = votesLabel
                    )
                }
            }
        }
    }
}

private fun formatToolbarTitle(subreddit: String): String {
    return if (subreddit.equals("all", ignoreCase = true)) {
        "All"
    } else {
        "r/${subreddit.lowercase()}"
    }
}

private val sampleImagePost = RedditPost(
    id = "sample",
    title = "Sample Reddit post title that demonstrates how the UI will wrap over multiple lines",
    author = "sample_user",
    subreddit = "r/sample",
    selfText = "",
    thumbnailUrl = "https://placekitten.com/400/400",
    url = "https://www.reddit.com",
    domain = "reddit.com",
    permalink = "https://www.reddit.com/r/sample",
    commentCount = 420,
    score = 1337,
    createdUtc = (System.currentTimeMillis() / 1000) - 3600,
    media = RedditPostMedia.Image(
        url = "https://placekitten.com/800/600",
        width = 800,
        height = 600
    ),
    isStickied = false,
    isNsfw = false
)

private val sampleTextPost = sampleImagePost.copy(
    id = "sample_text",
    title = "Text-only sample post without media",
    selfText = "Here is a sample self text content just to demonstrate layout spacing when there is no media attached to the post.",
    commentCount = 128,
    score = 512,
    media = RedditPostMedia.None,
    thumbnailUrl = null,
    isStickied = true,
    isNsfw = false
)

private val sampleVideoPost = sampleImagePost.copy(
    id = "sample_video",
    title = "Sample video post preview",
    media = RedditPostMedia.Video(
        url = "https://storage.googleapis.com/exoplayer-test-media-1/BigBuckBunny_320x180.m3u8",
        hasAudio = true,
        width = 1280,
        height = 720,
        durationSeconds = 10
    ),
    isStickied = false,
    isNsfw = false
)

@Preview(showBackground = true)
@Composable
private fun RedditPostItemPreview() {
    MunchForRedditTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RedditPostItem(
                post = sampleImagePost,
                selectedSubreddit = "all",
                onSubredditTapped = {},
                onPostSelected = {},
                onImageClick = {},
                onYouTubeSelected = {},
                postCardStyle = PostCardStyle.CardV1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RedditPostItemCardV2Preview() {
    MunchForRedditTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RedditPostItem(
                post = sampleImagePost,
                selectedSubreddit = "all",
                onSubredditTapped = {},
                onPostSelected = {},
                onImageClick = {},
                onYouTubeSelected = {},
                postCardStyle = PostCardStyle.CardV2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RedditFeedScreenPreview() {
    MunchForRedditTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RedditFeedScreen(
                uiState = RedditFeedViewModel.RedditFeedUiState(
                    posts = listOf(sampleImagePost, sampleTextPost, sampleVideoPost),
                    selectedSubreddit = "all"
                ),
                postCardStyle = PostCardStyle.CardV1,
                subredditOptions = listOf("all", "android", "apple"),
                sortOptions = RedditFeedViewModel.FeedSortOption.values().toList(),
                topTimeRangeOptions = RedditFeedViewModel.TopTimeRange.values().toList(),
                onSelectSubreddit = {},
                onSelectSort = {},
                onSelectTopTimeRange = {},
                onPostSelected = {},
                onRetry = {},
                onTitleTapped = {},
                onImageClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RedditFeedScreenLoadingPreview() {
    MunchForRedditTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RedditFeedScreen(
                uiState = RedditFeedViewModel.RedditFeedUiState(isLoading = true, selectedSubreddit = "android"),
                postCardStyle = PostCardStyle.CardV1,
                subredditOptions = listOf("all", "android"),
                sortOptions = RedditFeedViewModel.FeedSortOption.values().toList(),
                topTimeRangeOptions = RedditFeedViewModel.TopTimeRange.values().toList(),
                onSelectSubreddit = {},
                onSelectSort = {},
                onSelectTopTimeRange = {},
                onPostSelected = {},
                onRetry = {},
                onTitleTapped = {},
                onImageClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RedditFeedScreenErrorPreview() {
    MunchForRedditTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RedditFeedScreen(
                uiState = RedditFeedViewModel.RedditFeedUiState(errorMessage = "Network error", selectedSubreddit = "apple"),
                postCardStyle = PostCardStyle.CardV1,
                subredditOptions = listOf("all", "apple"),
                sortOptions = RedditFeedViewModel.FeedSortOption.values().toList(),
                topTimeRangeOptions = RedditFeedViewModel.TopTimeRange.values().toList(),
                onSelectSubreddit = {},
                onSelectSort = {},
                onSelectTopTimeRange = {},
                onPostSelected = {},
                onRetry = {},
                onTitleTapped = {},
                onImageClick = {}
            )
        }
    }
}
