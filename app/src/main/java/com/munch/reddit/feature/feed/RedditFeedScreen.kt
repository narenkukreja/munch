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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.runtime.rememberUpdatedState
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
import android.view.View
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import org.koin.androidx.compose.koinViewModel
import com.munch.reddit.feature.shared.FloatingToolbar
import com.munch.reddit.feature.shared.FloatingToolbarButton
import com.munch.reddit.activity.TableViewerActivity
import com.munch.reddit.feature.shared.RedditPostMediaContent
import android.content.Intent
import com.munch.reddit.activity.EditFavoritesActivity
import com.munch.reddit.feature.shared.SubredditSideSheet
import com.munch.reddit.feature.shared.SideSheetEdgeSwipeTarget
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
import com.munch.reddit.feature.feed.recycler.FeedColors
import com.munch.reddit.feature.feed.recycler.FeedRow
import com.munch.reddit.feature.feed.recycler.FeedSpacingItemDecoration
import com.munch.reddit.feature.feed.recycler.FeedSwipeToDismissCallback
import com.munch.reddit.feature.feed.recycler.RedditFeedAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper

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
    onImageClick: (String) -> Unit = {},
    onGalleryPreview: (List<String>, Int) -> Unit = { _, _ -> },
    onYouTubeSelected: (String) -> Unit = {},
    onVideoFeedClick: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    isAppending: Boolean = false,
    canLoadMore: Boolean = true,
    viewModel: RedditFeedViewModel? = null,
    onPostDismissed: (RedditPost) -> Unit = {},
    onSubredditFromPostClick: (String) -> Unit = onSelectSubreddit,
    modifier: Modifier = Modifier
) {
    var showSubredditSheet by remember { mutableStateOf(false) }
    val displayTitle = formatToolbarTitle(uiState.selectedSubreddit)
    val selectedIndex = subredditOptions.indexOfFirst { it.equals(uiState.selectedSubreddit, ignoreCase = true) }
        .takeIf { it >= 0 } ?: 0
    val spacing = MaterialSpacing
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val sideSheetScrollState = remember(viewModel) {
        ScrollState(viewModel?.getSideSheetScroll() ?: 0)
    }
    val context = LocalContext.current
    val feedRecyclerView = remember { mutableStateOf<RecyclerView?>(null) }

    LaunchedEffect(uiState.selectedSubreddit, uiState.scrollPosition, feedRecyclerView.value) {
        val recyclerView = feedRecyclerView.value ?: return@LaunchedEffect
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return@LaunchedEffect
        val targetScroll = uiState.scrollPosition
        if (targetScroll != null) {
            val currentIndex = layoutManager.findFirstVisibleItemPosition()
            val currentOffset = layoutManager.findViewByPosition(currentIndex)?.top?.let { -it } ?: 0
            if (currentIndex != targetScroll.firstVisibleItemIndex ||
                currentOffset != targetScroll.firstVisibleItemScrollOffset
            ) {
                val targetIndex = targetScroll.firstVisibleItemIndex.coerceAtLeast(0)
                layoutManager.scrollToPositionWithOffset(targetIndex, targetScroll.firstVisibleItemScrollOffset.coerceAtLeast(0))
            }
        } else {
            val currentIndex = layoutManager.findFirstVisibleItemPosition()
            if (currentIndex > 0) {
                recyclerView.scrollToPosition(0)
            }
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
                    onImageClick = {},
                    onGalleryPreview = onGalleryPreview,
                    onYouTubeSelected = {},
                    onLoadMore = {},
                    isAppending = false,
                    canLoadMore = false,
                    contentPadding = PaddingValues(vertical = spacing.lg),
                    initialScrollPosition = previousScrollPos
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
                        onImageClick = onImageClick,
                        onGalleryPreview = onGalleryPreview,
                        onYouTubeSelected = onYouTubeSelected,
                        onSubredditSelected = onSelectSubreddit,
                        onSubredditFromPostClick = onSubredditFromPostClick,
                        onLoadMore = onLoadMore,
                        isAppending = isAppending,
                        canLoadMore = canLoadMore,
                        contentPadding = PaddingValues(vertical = spacing.lg),
                        onPostDismissed = onPostDismissed,
                        initialScrollPosition = uiState.scrollPosition,
                        onRecyclerViewReady = { feedRecyclerView.value = it },
                        onSaveScrollPosition = { index, offset ->
                            viewModel?.saveScrollPosition(uiState.selectedSubreddit, index, offset)
                        }
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
                onEditFavoritesClick = {
                    showSubredditSheet = false
                    context.startActivity(Intent(context, EditFavoritesActivity::class.java))
                },
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
                                    feedRecyclerView.value?.smoothScrollToPosition(0)
                                },
                                iconTint = SubredditColor,
                                iconSize = 20.dp
                            ),
                            FloatingToolbarButton(
                                icon = Icons.Default.Refresh,
                                contentDescription = "Refresh feed",
                                onClick = {
                                    onRetry()
                                    feedRecyclerView.value?.smoothScrollToPosition(0)
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

            if (!showSubredditSheet) {
                SideSheetEdgeSwipeTarget(
                    onOpen = { showSubredditSheet = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
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
                    style = MaterialTheme.typography.headlineSmall,
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
    onImageClick: (String) -> Unit,
    onGalleryPreview: (List<String>, Int) -> Unit = { _, _ -> },
    onYouTubeSelected: (String) -> Unit,
    onSubredditSelected: (String) -> Unit = {},
    onSubredditFromPostClick: (String) -> Unit = {},
    onLoadMore: () -> Unit,
    isAppending: Boolean,
    canLoadMore: Boolean,
    contentPadding: PaddingValues,
    onPostDismissed: (RedditPost) -> Unit = {},
    initialScrollPosition: RedditFeedViewModel.ScrollPosition? = null,
    onRecyclerViewReady: (RecyclerView) -> Unit = {},
    onSaveScrollPosition: ((index: Int, offset: Int) -> Unit)? = null
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

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(SpacerBackgroundColor)
            .navigationBarsPadding()
    ) {
        val spacing = MaterialSpacing
        val context = LocalContext.current
        val density = LocalDensity.current
        val topPaddingPx = with(density) { contentPadding.calculateTopPadding().roundToPx() }
        val bottomPaddingPx = with(density) { contentPadding.calculateBottomPadding().roundToPx() }
        val verticalSpacingPx = with(density) { spacing.md.roundToPx() }
        val nestedScrollInterop = rememberNestedScrollInteropConnection()
        val coroutineScope = rememberCoroutineScope()

        val visiblePosts = remember(posts, dismissedPostIds, hiddenReadPostIds) {
            posts.filter { post ->
                val isDismissed = dismissedPostIds.contains(post.id)
                val isHidden = hiddenReadPostIds.contains(post.id)
                !isDismissed && !isHidden
            }
        }

        val onPostSelectedState = rememberUpdatedState(onPostSelected)
        val onImageClickState = rememberUpdatedState(onImageClick)
        val onGalleryPreviewState = rememberUpdatedState(onGalleryPreview)
        val onYouTubeSelectedState = rememberUpdatedState(onYouTubeSelected)
        val onSubredditFromPostClickState = rememberUpdatedState(onSubredditFromPostClick)
        val onPostDismissedState = rememberUpdatedState(onPostDismissed)
        val onLoadMoreState = rememberUpdatedState(onLoadMore)
        val canLoadMoreState = rememberUpdatedState(canLoadMore)
        val isAppendingState = rememberUpdatedState(isAppending)
        val postCountState = rememberUpdatedState(visiblePosts.size)
        val onSaveScrollPositionState = rememberUpdatedState(onSaveScrollPosition)

        val feedAdapter = remember {
            RedditFeedAdapter(
                onPostSelected = { post -> onPostSelectedState.value(post) },
                onPostDismissed = { post ->
                    dismissedPostIds = dismissedPostIds + post.id
                    coroutineScope.launch {
                        delay(300)
                        onPostDismissedState.value(post)
                    }
                },
                onImageClick = { url -> onImageClickState.value(url) },
                onGalleryPreview = { urls, index -> onGalleryPreviewState.value(urls, index) },
                onLinkClick = { url -> openLinkInCustomTab(context, url) },
                onYouTubeSelected = { id -> onYouTubeSelectedState.value(id) },
                onSubredditFromPostClick = { subreddit -> onSubredditFromPostClickState.value(subreddit) }
            )
        }

        val currentColors = FeedColors(
            postBackground = PostBackgroundColor.toArgb(),
            spacerBackground = SpacerBackgroundColor.toArgb(),
            title = TitleColor.toArgb(),
            subreddit = SubredditColor.toArgb(),
            metaInfo = MetaInfoColor.toArgb(),
            pinnedLabel = PinnedLabelColor.toArgb(),
            error = MaterialTheme.colorScheme.error.toArgb(),
            onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
            outlineVariant = MaterialTheme.colorScheme.outlineVariant.toArgb(),
            tableCardBackground = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).toArgb(),
            tableCardTitle = MaterialTheme.colorScheme.onSurface.toArgb(),
            tableChipBackground = MaterialTheme.colorScheme.secondaryContainer.toArgb(),
            tableChipContent = MaterialTheme.colorScheme.onSecondaryContainer.toArgb()
        )

        val rows = remember(visiblePosts, readPostIds, selectedSubreddit, isAppending, canLoadMore) {
            buildList {
                visiblePosts.forEach { post ->
                    add(
                        FeedRow.Post(
                            post = post,
                            isRead = readPostIds.contains(post.id),
                            selectedSubreddit = selectedSubreddit
                        )
                    )
                }
                if (isAppending) {
                    add(FeedRow.LoadingFooter)
                } else if (!canLoadMore && visiblePosts.isNotEmpty()) {
                    add(FeedRow.EndSpacer)
                }
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollInterop),
            factory = { viewContext ->
                RecyclerView(viewContext).apply {
                    layoutManager = LinearLayoutManager(viewContext)
                    this.adapter = feedAdapter
                    setPadding(0, topPaddingPx, 0, bottomPaddingPx)
                    clipToPadding = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    addItemDecoration(FeedSpacingItemDecoration(verticalSpacingPx))

                    ItemTouchHelper(
                        FeedSwipeToDismissCallback(
                            canSwipe = { position -> feedAdapter.isSwipeEnabled(position) },
                            onDismiss = { position -> feedAdapter.dismissAt(position) }
                        )
                    ).attachToRecyclerView(this)

                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                            val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                            val first = lm.findFirstVisibleItemPosition()
                            if (first == RecyclerView.NO_POSITION) return
                            val offset = lm.findViewByPosition(first)?.top?.let { -it } ?: 0
                            onSaveScrollPositionState.value?.invoke(first, offset)
                        }

                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                            val lastVisible = lm.findLastVisibleItemPosition()
                            if (lastVisible == RecyclerView.NO_POSITION) return
                            val postCount = postCountState.value
                            if (postCount <= 0) return
                            val triggerIndex = (postCount - LOAD_MORE_THRESHOLD).coerceAtLeast(0)
                            if (canLoadMoreState.value && !isAppendingState.value && lastVisible >= triggerIndex) {
                                onLoadMoreState.value()
                            }
                        }
                    })
                }
            },
            update = { recyclerView ->
                onRecyclerViewReady(recyclerView)
                if (feedAdapter.colors != currentColors) {
                    feedAdapter.colors = currentColors
                }
                feedAdapter.submitList(rows)

                val saved = initialScrollPosition
                if (saved != null) {
                    val lm = recyclerView.layoutManager as? LinearLayoutManager
                    if (lm != null) {
                        val currentIndex = lm.findFirstVisibleItemPosition()
                        val currentOffset = lm.findViewByPosition(currentIndex)?.top?.let { -it } ?: 0
                        if (currentIndex != saved.firstVisibleItemIndex ||
                            currentOffset != saved.firstVisibleItemScrollOffset
                        ) {
                            lm.scrollToPositionWithOffset(
                                saved.firstVisibleItemIndex.coerceAtLeast(0),
                                saved.firstVisibleItemScrollOffset.coerceAtLeast(0)
                            )
                        }
                    }
                }
            }
        )
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
    fun MetaInfoStat(icon: ImageVector, label: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MetaInfoColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MetaInfoColor,
                maxLines = 1
            )
        }
    }

    fun Modifier.subredditClickable(): Modifier {
        return then(
            if (isGlobalFeed) {
                Modifier.clickable {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    if (bottomLabel.isNotBlank()) {
                        Text(
                            text = bottomLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = SubredditColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.subredditClickable()
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    MetaInfoStat(
                        icon = Icons.Filled.AccessTime,
                        label = formatRelativeTime(post.createdUtc)
                    )
                    MetaInfoStat(
                        icon = Icons.Filled.ChatBubble,
                        label = commentsLabel
                    )
                    MetaInfoStat(
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
