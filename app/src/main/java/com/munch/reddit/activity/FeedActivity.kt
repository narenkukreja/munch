package com.munch.reddit.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.munch.reddit.R
import com.munch.reddit.data.AppPreferences
import com.munch.reddit.domain.SubredditCatalog
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.feature.feed.FeedAdapter
import com.munch.reddit.feature.feed.FeedRow
import com.munch.reddit.feature.feed.RedditFeedViewModel
import com.munch.reddit.feature.feed.SubredditAdapter
import com.munch.reddit.feature.feed.SubredditRow
import com.munch.reddit.theme.FeedColorPalette
import com.munch.reddit.theme.FeedThemePreset
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import com.munch.reddit.feature.shared.RedditHtmlTable
import com.munch.reddit.feature.shared.parseHtmlText


class FeedActivity : AppCompatActivity() {

    private val savedStateHandle by lazy { SavedStateHandle() }
    private val viewModel: RedditFeedViewModel by viewModel { parametersOf(savedStateHandle) }
    private lateinit var appPreferences: AppPreferences
    private lateinit var palette: FeedColorPalette
    private var currentThemeId: String = ""

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: ProgressBar
    private lateinit var errorContainer: View
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: MaterialButton
    private lateinit var floatingToolbar: MaterialCardView
    private lateinit var sortButton: MaterialButton
    private lateinit var timeRangeButton: MaterialButton
    private lateinit var subredditButton: MaterialButton
    private lateinit var searchButton: View
    private lateinit var settingsButton: View
    private lateinit var videoFeedButton: View
    private lateinit var hideReadButton: View
    private lateinit var refreshButton: View
    private lateinit var scrollTopButton: View
    private lateinit var openDrawerButton: View
    private lateinit var subredditRecyclerView: RecyclerView
    private lateinit var searchDrawerButton: MaterialButton
    private lateinit var settingsDrawerButton: MaterialButton

    private lateinit var feedAdapter: FeedAdapter
    private lateinit var subredditAdapter: SubredditAdapter

    private var hiddenReadSnapshot: Set<String> = emptySet()
    private var lastUiState: RedditFeedViewModel.RedditFeedUiState? = null
    private val appliedScrollRestorations = mutableSetOf<String>()
    private var lastSubreddit: String? = null

    private val postDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("SELECTED_SUBREDDIT")?.let { selectSubreddit(it) }
        }
    }

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val searchQuery = data?.getStringExtra("SEARCH_QUERY")
            val selectedSubredditResult = data?.getStringExtra("SELECTED_SUBREDDIT")
            when {
                !searchQuery.isNullOrBlank() -> viewModel.search(searchQuery)
                !selectedSubredditResult.isNullOrBlank() -> selectSubreddit(selectedSubredditResult)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed)
        appPreferences = AppPreferences(this)
        palette = FeedThemePreset.fromId(appPreferences.selectedTheme).palette
        currentThemeId = palette.themeId

        bindViews()
        setupPalette()
        setupRecycler()
        setupDrawer()
        setupToolbarActions()
        setupFloatingToolbar()

        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                finish()
            }
        }

        // Handle deep link subreddit selection
        intent.getStringExtra("SELECTED_SUBREDDIT")?.let { selectSubreddit(it) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    lastUiState = state
                    renderState(state)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val updatedTheme = appPreferences.selectedTheme
        if (updatedTheme != currentThemeId) {
            palette = FeedThemePreset.fromId(updatedTheme).palette
            currentThemeId = palette.themeId
            setupPalette()
            rebuildAdapterForPalette()
            lastUiState?.let { renderState(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerView.adapter = null
    }

    private fun bindViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.feedToolbar)
        swipeRefreshLayout = findViewById(R.id.swipeRefresh)
        recyclerView = findViewById(R.id.feedRecyclerView)
        loadingView = findViewById(R.id.loadingIndicator)
        errorContainer = findViewById(R.id.errorContainer)
        errorMessage = findViewById(R.id.errorMessage)
        retryButton = findViewById(R.id.retryButton)
        floatingToolbar = findViewById(R.id.floatingToolbar)
        sortButton = findViewById(R.id.sortButton)
        timeRangeButton = findViewById(R.id.timeRangeButton)
        subredditButton = findViewById(R.id.subredditButton)
        searchButton = findViewById(R.id.searchButton)
        settingsButton = findViewById(R.id.settingsButton)
        videoFeedButton = findViewById(R.id.videoFeedButton)
        hideReadButton = findViewById(R.id.hideReadButton)
        refreshButton = findViewById(R.id.refreshButton)
        scrollTopButton = findViewById(R.id.scrollTopButton)
        openDrawerButton = findViewById(R.id.openDrawerButton)
        subredditRecyclerView = findViewById(R.id.subredditRecyclerView)
        searchDrawerButton = findViewById(R.id.searchDrawerButton)
        settingsDrawerButton = findViewById(R.id.settingsDrawerButton)
    }

    private fun setupPalette() {
        val background = palette.spacerBackground.toArgb()
        val cardColor = palette.postBackground.toArgb()
        val textColor = palette.title.toArgb()

        toolbar.setBackgroundColor(cardColor)
        toolbar.setTitleTextColor(textColor)
        findViewById<View>(R.id.filterRow)?.setBackgroundColor(cardColor)
        findViewById<View>(R.id.subredditDrawer)?.setBackgroundColor(cardColor)
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(cardColor)
        swipeRefreshLayout.setColorSchemeColors(palette.subreddit.toArgb())
        recyclerView.setBackgroundColor(background)
        floatingToolbar.setCardBackgroundColor(cardColor)
        floatingToolbar.setStrokeColor(palette.postBorder?.toArgb() ?: cardColor)
        errorMessage.setTextColor(textColor)
        findViewById<TextView>(R.id.subredditDrawerTitle)?.setTextColor(textColor)
        sortButton.setTextColor(textColor)
        timeRangeButton.setTextColor(textColor)
        subredditButton.setTextColor(textColor)
        searchDrawerButton.setTextColor(textColor)
        settingsDrawerButton.setTextColor(textColor)
        val iconTint = palette.subreddit.toArgb()
        listOf(searchButton, settingsButton, videoFeedButton, hideReadButton, refreshButton, scrollTopButton, openDrawerButton).forEach { view ->
            (view as? android.widget.ImageView)?.setColorFilter(iconTint)
        }
    }

    private fun setupRecycler() {
        feedAdapter = FeedAdapter(
            palette = palette,
            onPostClick = { post -> openPostDetail(post) },
            onImageClick = { url -> openImage(url) },
            onGalleryClick = { urls, index -> openGallery(urls, index) },
            onYoutubeClick = { videoId -> openYouTube(videoId) },
            onTablesClick = { post, tables, startIndex -> openTables(post, tables, startIndex) },
            onSubredditClick = { subreddit -> selectSubreddit(subreddit) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = feedAdapter

        swipeRefreshLayout.setOnRefreshListener { viewModel.refresh() }
        retryButton.setOnClickListener { viewModel.refresh() }

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val row = feedAdapter.currentList.getOrNull(position) as? FeedRow.PostRow
                if (row != null) {
                    viewModel.dismissPost(row.post.id)
                } else {
                    feedAdapter.notifyItemChanged(position)
                }
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                maybeLoadMore()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    saveScrollPosition()
                }
            }
        })
    }

    private fun rebuildAdapterForPalette() {
        if (!::feedAdapter.isInitialized) return
        val existingRows = feedAdapter.currentList.toList()
        feedAdapter = FeedAdapter(
            palette = palette,
            onPostClick = { post -> openPostDetail(post) },
            onImageClick = { url -> openImage(url) },
            onGalleryClick = { urls, index -> openGallery(urls, index) },
            onYoutubeClick = { videoId -> openYouTube(videoId) },
            onTablesClick = { post, tables, startIndex -> openTables(post, tables, startIndex) },
            onSubredditClick = { subreddit -> selectSubreddit(subreddit) }
        )
        recyclerView.adapter = feedAdapter
        feedAdapter.submitList(existingRows)
    }

    private fun setupDrawer() {
        subredditAdapter = SubredditAdapter { subreddit ->
            selectSubreddit(subreddit)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        subredditRecyclerView.layoutManager = LinearLayoutManager(this)
        subredditRecyclerView.adapter = subredditAdapter

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                restoreDrawerScroll()
            }

            override fun onDrawerClosed(drawerView: View) {
                viewModel.updateSideSheetScroll(subredditRecyclerView.computeVerticalScrollOffset())
            }
        })

        searchDrawerButton.setOnClickListener { openSearch() }
        settingsDrawerButton.setOnClickListener { openSettings() }
    }

    private fun setupToolbarActions() {
        setSupportActionBar(toolbar)
        sortButton.setOnClickListener { showSortMenu() }
        timeRangeButton.setOnClickListener { showTopRangeMenu() }
        subredditButton.setOnClickListener { drawerLayout.openDrawer(GravityCompat.END) }
        searchButton.setOnClickListener { openSearch() }
        settingsButton.setOnClickListener { openSettings() }
        videoFeedButton.setOnClickListener { openVideoFeed() }
    }

    private fun setupFloatingToolbar() {
        scrollTopButton.setOnClickListener { recyclerView.smoothScrollToPosition(0) }
        refreshButton.setOnClickListener {
            viewModel.refresh()
            recyclerView.smoothScrollToPosition(0)
        }
        hideReadButton.setOnClickListener { viewModel.toggleHideReadPosts() }
        openDrawerButton.setOnClickListener { drawerLayout.openDrawer(GravityCompat.END) }
    }

    private fun renderState(state: RedditFeedViewModel.RedditFeedUiState) {
        if (lastSubreddit != state.selectedSubreddit) {
            appliedScrollRestorations.remove(state.selectedSubreddit)
            lastSubreddit = state.selectedSubreddit
        }
        toolbar.title = formatToolbarTitle(state.selectedSubreddit)
        feedAdapter.selectedSubreddit = state.selectedSubreddit

        // Snapshot hide-read set
        if (state.hideReadPosts && hiddenReadSnapshot.isEmpty()) {
            hiddenReadSnapshot = state.readPostIds
        } else if (!state.hideReadPosts) {
            hiddenReadSnapshot = emptySet()
        }

        val visiblePosts = state.posts.filter { post ->
            !(state.hideReadPosts && hiddenReadSnapshot.contains(post.id))
        }

        val rows = buildList {
            addAll(visiblePosts.map { FeedRow.PostRow(it, state.readPostIds.contains(it.id)) })
            if (state.isAppending) add(FeedRow.LoadingRow)
        }
        feedAdapter.submitList(rows)

        swipeRefreshLayout.isRefreshing = state.isLoading && state.posts.isNotEmpty()
        loadingView.isVisible = state.isLoading && state.posts.isEmpty()
        errorContainer.isVisible = state.errorMessage != null && state.posts.isEmpty()
        errorMessage.text = state.errorMessage.orEmpty()

        sortButton.text = state.selectedSort.displayLabel
        timeRangeButton.isVisible = state.selectedSort == RedditFeedViewModel.FeedSortOption.TOP
        timeRangeButton.text = state.selectedTopTimeRange.displayLabel
        subredditButton.text = formatToolbarTitle(state.selectedSubreddit)
        hideReadButton.alpha = if (state.hideReadPosts) 1f else 0.55f
        hideReadButton.contentDescription = if (state.hideReadPosts) {
            "Show read posts"
        } else {
            "Hide read posts"
        }

        // Restore scroll if we have one cached for this subreddit
        state.scrollPosition?.let { scroll ->
            if (!appliedScrollRestorations.contains(state.selectedSubreddit)) {
                val manager = recyclerView.layoutManager as? LinearLayoutManager
                manager?.scrollToPositionWithOffset(
                    scroll.firstVisibleItemIndex,
                    -scroll.firstVisibleItemScrollOffset
                )
                appliedScrollRestorations.add(state.selectedSubreddit)
            }
        }

        // Update drawer list
        val rowsForDrawer = mutableListOf<SubredditRow>()
        if (viewModel.subredditOptions.isNotEmpty()) {
            rowsForDrawer += SubredditRow.Header("My subreddits")
            rowsForDrawer += viewModel.subredditOptions.map { name ->
                SubredditRow.Item(
                    name = name,
                    isSelected = name.equals(state.selectedSubreddit, true),
                    iconUrl = state.subredditIcons[name.lowercase()]
                )
            }
        }
        if (SubredditCatalog.exploreSubreddits.isNotEmpty()) {
            rowsForDrawer += SubredditRow.Header("Explore")
            rowsForDrawer += SubredditCatalog.exploreSubreddits.map { name ->
                SubredditRow.Item(
                    name = name,
                    isSelected = name.equals(state.selectedSubreddit, true),
                    iconUrl = state.subredditIcons[name.lowercase()]
                )
            }
        }
        subredditAdapter.submitList(rowsForDrawer)
    }

    private fun maybeLoadMore() {
        val state = lastUiState ?: return
        if (!state.hasMore || state.isAppending) return
        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val lastVisible = manager.findLastVisibleItemPosition()
        if (lastVisible >= feedAdapter.itemCount - LOAD_MORE_THRESHOLD) {
            viewModel.loadMore()
        }
    }

    private fun saveScrollPosition() {
        val state = lastUiState ?: return
        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = manager.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return
        val firstView = manager.findViewByPosition(first)
        val offset = firstView?.top?.unaryMinus() ?: 0
        viewModel.saveScrollPosition(
            subreddit = state.selectedSubreddit,
            firstVisibleItemIndex = first,
            firstVisibleItemScrollOffset = offset
        )
    }

    private fun showSortMenu() {
        val popup = androidx.appcompat.widget.PopupMenu(this, sortButton)
        RedditFeedViewModel.FeedSortOption.values().forEachIndexed { index, option ->
            popup.menu.add(0, index, index, option.displayLabel)
        }
        popup.setOnMenuItemClickListener { item ->
            RedditFeedViewModel.FeedSortOption.values().getOrNull(item.itemId)?.let {
                viewModel.selectSort(it)
            }
            true
        }
        popup.show()
    }

    private fun showTopRangeMenu() {
        val popup = androidx.appcompat.widget.PopupMenu(this, timeRangeButton)
        RedditFeedViewModel.TopTimeRange.values().forEachIndexed { index, option ->
            popup.menu.add(0, index, index, option.displayLabel)
        }
        popup.setOnMenuItemClickListener { item ->
            RedditFeedViewModel.TopTimeRange.values().getOrNull(item.itemId)?.let {
                viewModel.selectTopTimeRange(it)
            }
            true
        }
        popup.show()
    }

    private fun formatToolbarTitle(subreddit: String): String {
        return if (subreddit.equals("all", ignoreCase = true)) {
            "All"
        } else {
            "r/${subreddit.lowercase()}"
        }
    }

    private fun openPostDetail(post: RedditPost) {
        viewModel.markPostRead(post.id)
        val intent = Intent(this, PostDetailActivity::class.java).apply {
            putExtra("PERMALINK", post.permalink)
        }
        postDetailLauncher.launch(intent)
        overridePendingTransition(R.anim.slide_in_right, 0)
    }

    private fun openImage(url: String) {
        val intent = Intent(this, ImagePreviewActivity::class.java).apply {
            putExtra("IMAGE_URL", url)
        }
        startActivity(intent)
    }

    private fun openGallery(urls: List<String>, startIndex: Int) {
        val intent = Intent(this, ImagePreviewActivity::class.java).apply {
            putExtra("IMAGE_URL", urls.getOrNull(startIndex) ?: urls.firstOrNull())
            putStringArrayListExtra("IMAGE_GALLERY", ArrayList(urls))
            putExtra("IMAGE_GALLERY_START_INDEX", startIndex)
        }
        startActivity(intent)
    }

    private fun openYouTube(videoId: String) {
        val intent = Intent(this, YouTubePlayerActivity::class.java).apply {
            putExtra("VIDEO_ID", videoId)
        }
        startActivity(intent)
    }

    private fun openTables(post: RedditPost, tables: List<RedditHtmlTable>, startIndex: Int) {
        val intent = TableViewerActivity.createIntent(
            context = this,
            postTitle = parseTitleForTables(post),
            tables = tables,
            startIndex = startIndex
        )
        startActivity(intent)
    }

    private fun parseTitleForTables(post: RedditPost): String {
        val stripped = parseHtmlText(post.title)
        return stripped.ifBlank { "Tables" }
    }

    private fun openSearch() {
        val intent = Intent(this, SearchActivity::class.java)
        searchLauncher.launch(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openVideoFeed() {
        val intent = Intent(this, VideoFeedActivity::class.java)
        startActivity(intent)
    }

    private fun restoreDrawerScroll() {
        val saved = viewModel.getSideSheetScroll()
        if (saved > 0) {
            subredditRecyclerView.post {
                subredditRecyclerView.scrollBy(0, saved)
            }
        }
    }

    private fun selectSubreddit(subreddit: String) {
        viewModel.selectSubreddit(subreddit)
        drawerLayout.closeDrawer(GravityCompat.END)
        recyclerView.smoothScrollToPosition(0)
    }

    companion object {
        private const val LOAD_MORE_THRESHOLD = 5
    }
}
