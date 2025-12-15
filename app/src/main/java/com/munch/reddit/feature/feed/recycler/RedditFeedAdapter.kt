package com.munch.reddit.feature.feed.recycler

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.core.text.HtmlCompat
import coil.load
import com.munch.reddit.data.remote.StreamableApiService
import com.munch.reddit.R
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.domain.model.RedditPostMedia
import com.munch.reddit.feature.shared.formatCount
import com.munch.reddit.feature.shared.formatRelativeTime
import com.munch.reddit.feature.shared.parseHtmlText
import com.munch.reddit.feature.shared.parseTablesFromHtml
import com.munch.reddit.feature.shared.stripTablesFromHtml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import kotlin.math.roundToInt

class RedditFeedAdapter(
    private val onPostSelected: (RedditPost) -> Unit,
    private val onPostDismissed: (RedditPost) -> Unit,
    private val onImageClick: (String) -> Unit,
    private val onGalleryPreview: (List<String>, Int) -> Unit,
    private val onLinkClick: (String) -> Unit,
    private val onYouTubeSelected: (String) -> Unit,
    private val onSubredditFromPostClick: (String) -> Unit
) : ListAdapter<FeedRow, RecyclerView.ViewHolder>(DIFF) {

    var colors: FeedColors? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FeedRow.Post -> VIEW_TYPE_POST
            FeedRow.LoadingFooter -> VIEW_TYPE_LOADING
            FeedRow.EndSpacer -> VIEW_TYPE_SPACER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_POST -> {
                val view = inflater.inflate(R.layout.item_reddit_post, parent, false)
                PostViewHolder(
                    view = view,
                    getColors = { colors },
                    onPostSelected = onPostSelected,
                    onImageClick = onImageClick,
                    onGalleryPreview = onGalleryPreview,
                    onLinkClick = onLinkClick,
                    onYouTubeSelected = onYouTubeSelected,
                    onSubredditFromPostClick = onSubredditFromPostClick
                )
            }
            VIEW_TYPE_LOADING -> LoadingViewHolder(inflater.inflate(R.layout.item_feed_footer_loading, parent, false))
            VIEW_TYPE_SPACER -> SpacerViewHolder(inflater.inflate(R.layout.item_feed_footer_spacer, parent, false))
            else -> error("Unknown viewType=$viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is FeedRow.Post -> (holder as PostViewHolder).bind(item)
            FeedRow.LoadingFooter -> (holder as LoadingViewHolder).bind(colors)
            FeedRow.EndSpacer -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? PostViewHolder)?.recycle()
    }

    fun getPostAt(adapterPosition: Int): RedditPost? {
        val item = currentList.getOrNull(adapterPosition)
        return (item as? FeedRow.Post)?.post
    }

    fun isSwipeEnabled(adapterPosition: Int): Boolean {
        val item = currentList.getOrNull(adapterPosition) as? FeedRow.Post ?: return false
        return !item.isRead
    }

    fun dismissAt(adapterPosition: Int) {
        val post = getPostAt(adapterPosition) ?: return
        onPostDismissed(post)
    }

    private class LoadingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val indicator: ProgressBar = view.findViewById(R.id.feed_loading_indicator)

        fun bind(colors: FeedColors?) {
            val tint = colors?.subreddit ?: Color.WHITE
            indicator.indeterminateTintList = ColorStateList.valueOf(tint)
        }
    }

    private class SpacerViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class PostViewHolder(
        private val view: View,
        private val getColors: () -> FeedColors?,
        private val onPostSelected: (RedditPost) -> Unit,
        private val onImageClick: (String) -> Unit,
        private val onGalleryPreview: (List<String>, Int) -> Unit,
        private val onLinkClick: (String) -> Unit,
        private val onYouTubeSelected: (String) -> Unit,
        private val onSubredditFromPostClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val root: View = view.findViewById(R.id.post_root)
        private val title: TextView = view.findViewById(R.id.post_title)
        private val domainRow: View = view.findViewById(R.id.post_domain_row)
        private val domain: TextView = view.findViewById(R.id.post_domain)
        private val badgeNsfw: TextView = view.findViewById(R.id.badge_nsfw)
        private val badgePinned: TextView = view.findViewById(R.id.badge_pinned)
        private val body: TextView = view.findViewById(R.id.post_body)

        private val bottomLabel: TextView = view.findViewById(R.id.post_bottom_label)
        private val metaTimeText: TextView = view.findViewById(R.id.meta_time_text)
        private val metaCommentsText: TextView = view.findViewById(R.id.meta_comments_text)
        private val metaVotesText: TextView = view.findViewById(R.id.meta_votes_text)
        private val metaTimeIcon: ImageView = view.findViewById(R.id.meta_time_icon)
        private val metaCommentsIcon: ImageView = view.findViewById(R.id.meta_comments_icon)
        private val metaVotesIcon: ImageView = view.findViewById(R.id.meta_votes_icon)

        private val mediaImage: ImageView = view.findViewById(R.id.media_image)
        private val mediaLinkContainer: LinearLayout = view.findViewById(R.id.media_link_container)
        private val linkPreviewImage: ImageView = view.findViewById(R.id.link_preview_image)
        private val linkBottomCard: LinearLayout = view.findViewById(R.id.link_bottom_card)
        private val linkPlaceholder: FrameLayout = view.findViewById(R.id.link_placeholder)
        private val linkPlaceholderText: TextView = view.findViewById(R.id.link_placeholder_text)
        private val linkDomain: TextView = view.findViewById(R.id.link_domain)
        private val linkIcon: ImageView = view.findViewById(R.id.link_icon)

        private val youtubeContainer: AspectRatioFrameLayout = view.findViewById(R.id.media_youtube_container)
        private val youtubeThumbnail: ImageView = view.findViewById(R.id.youtube_thumbnail)
        private val youtubePlayButton: View = view.findViewById(R.id.youtube_play_button)
        private val youtubePlayIcon: ImageView = view.findViewById(R.id.youtube_play_icon)

        private val videoContainer: AspectRatioFrameLayout = view.findViewById(R.id.media_video_container)
        private val videoPlayerView: PlayerView = view.findViewById(R.id.video_player)
        private val videoControls: View = view.findViewById(R.id.video_controls)
        private val videoStatusOverlay: FrameLayout = view.findViewById(R.id.video_status_overlay)
        private val videoStatusThumbnail: ImageView = view.findViewById(R.id.video_status_thumbnail)
        private val videoStatusText: TextView = view.findViewById(R.id.video_status_text)
        private val videoMuteButton: ImageButton = view.findViewById(R.id.video_mute_button)
        private val videoSeekBar: SeekBar = view.findViewById(R.id.video_seekbar)

        private val webContainer: AspectRatioFrameLayout = view.findViewById(R.id.media_web_container)
        private val webHost: FrameLayout = view.findViewById(R.id.webview_host)
        private var webView: WebView? = null

        private val galleryContainer: FrameLayout = view.findViewById(R.id.media_gallery_container)
        private val galleryRecycler: RecyclerView = view.findViewById(R.id.gallery_recycler)
        private val galleryDots: LinearLayout = view.findViewById(R.id.gallery_dots_container)

        private val handler = Handler(Looper.getMainLooper())
        private var boundPostId: String? = null

        private var mediaJob: Job? = null
        private var videoPlayer: ExoPlayer? = null
        private var videoDurationMs: Long = 0L
        private var isMuted: Boolean = true
        private var isScrubbing: Boolean = false
        private var pendingSeekMs: Long = 0L
        private var progressRunnable: Runnable? = null

        private val gallerySnapHelper = PagerSnapHelper()
        private var galleryUrls: List<String> = emptyList()
        private val galleryAdapter = GalleryImageAdapter { index ->
            onGalleryPreview(galleryUrls, index)
        }
        private var galleryScrollListener: RecyclerView.OnScrollListener? = null

        init {
            videoSeekBar.max = 1000
            body.movementMethod = FeedLinkMovementMethod
            body.highlightColor = Color.TRANSPARENT
            galleryRecycler.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = galleryAdapter
                gallerySnapHelper.attachToRecyclerView(this)
                isNestedScrollingEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }

            videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val duration = videoDurationMs
                    if (duration <= 0L) return
                    isScrubbing = true
                    pendingSeekMs = ((progress / 1000f) * duration).roundToInt().toLong()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isScrubbing = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val duration = videoDurationMs
                    val player = videoPlayer
                    if (duration > 0L && player != null) {
                        player.seekTo(pendingSeekMs.coerceIn(0L, duration))
                    }
                    isScrubbing = false
                }
            })
        }

        fun bind(item: FeedRow.Post) {
            val colors = getColors()
            val post = item.post
            boundPostId = post.id

            root.setOnClickListener { onPostSelected(post) }

            val rawSub = post.subreddit.removePrefix("r/").removePrefix("R/")
            val subredditLabel = "r/${rawSub.lowercase()}"
            val isGlobalFeed = item.selectedSubreddit.equals("all", ignoreCase = true)
            val displayDomain = post.domain.removePrefix("www.")
            val domainLabel = displayDomain.ifBlank {
                if (isGlobalFeed) subredditLabel else "u/${post.author}"
            }
            val bottomText = if (isGlobalFeed) subredditLabel else "u/${post.author}"

            title.text = parseHtmlText(post.title)

            val titleColor = colors?.title ?: Color.WHITE
            title.setTextColor(titleColor)

            val backgroundColor = if (item.isRead) colors?.spacerBackground ?: Color.BLACK else colors?.postBackground ?: Color.BLACK
            root.setBackgroundColor(backgroundColor)
            root.alpha = if (item.isRead) 0.55f else 1f

            val showDomain = post.media !is RedditPostMedia.Link && domainLabel.isNotBlank()
            domain.isVisible = showDomain
            domain.text = domainLabel
            domain.setTextColor(colors?.onSurfaceVariant ?: Color.LTGRAY)

            badgeNsfw.isVisible = post.isNsfw
            badgePinned.isVisible = post.isStickied
            if (post.isNsfw) {
                val errorColor = colors?.error ?: Color.RED
                badgeNsfw.setTextColor(errorColor)
                badgeNsfw.backgroundTintList = ColorStateList.valueOf(withAlpha(errorColor, 0.18f))
            }
            if (post.isStickied) {
                val pinnedColor = colors?.pinnedLabel ?: Color.GREEN
                badgePinned.setTextColor(pinnedColor)
                badgePinned.backgroundTintList = ColorStateList.valueOf(withAlpha(pinnedColor, 0.18f))
            }

            bottomLabel.text = bottomText
            bottomLabel.setTextColor(colors?.subreddit ?: Color.CYAN)
            bottomLabel.setOnClickListener(null)
            bottomLabel.isClickable = false
            if (isGlobalFeed) {
                bottomLabel.isClickable = true
                bottomLabel.setOnClickListener {
                    val subredditName = post.subreddit.removePrefix("r/").removePrefix("R/")
                    onSubredditFromPostClick(subredditName)
                }
            }

            val metaColor = colors?.metaInfo ?: Color.LTGRAY
            metaTimeIcon.setColorFilter(metaColor)
            metaCommentsIcon.setColorFilter(metaColor)
            metaVotesIcon.setColorFilter(metaColor)
            metaTimeText.setTextColor(metaColor)
            metaCommentsText.setTextColor(metaColor)
            metaVotesText.setTextColor(metaColor)
            metaTimeText.text = formatRelativeTime(post.createdUtc)
            metaCommentsText.text = formatCount(post.commentCount)
            metaVotesText.text = formatCount(post.score)

            bindBody(post, colors, titleColor)
            bindMedia(item, colors, isGlobalFeed, subredditLabel, displayDomain)
        }

        private fun bindBody(post: RedditPost, colors: FeedColors?, titleColor: Int) {
            if (post.media !is RedditPostMedia.None) {
                body.isVisible = false
                body.text = null
                return
            }

            val parsedTables = parseTablesFromHtml(post.selfTextHtml)
            val sanitizedHtml = if (parsedTables.isNotEmpty()) stripTablesFromHtml(post.selfTextHtml) else post.selfTextHtml
            val bodyText = sanitizedHtml?.let { parseHtmlText(it).trim() } ?: post.selfText
            if (bodyText.isBlank()) {
                body.isVisible = false
                body.text = null
                return
            }

            body.isVisible = true
            body.setTextColor(withAlpha(titleColor, 0.9f))
            body.text = buildBodyPreviewText(
                plainText = bodyText,
                htmlText = sanitizedHtml,
                linkColor = colors?.subreddit ?: Color.CYAN
            )
        }

        private fun buildBodyPreviewText(
            plainText: String,
            htmlText: String?,
            linkColor: Int
        ): CharSequence {
            val initial = htmlText?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY) }.getOrNull()
            }

            val builder = SpannableStringBuilder(initial ?: plainText)
            if (builder.isEmpty()) return builder

            replaceImageSpans(builder, linkColor)
            replaceUrlSpans(builder, linkColor)
            linkifyRawUrls(builder, linkColor)
            styleSubredditMentions(builder, linkColor)
            styleUserMentions(builder, linkColor)

            return builder
        }

        private fun replaceImageSpans(builder: SpannableStringBuilder, linkColor: Int) {
            val spans = builder.getSpans(0, builder.length, ImageSpan::class.java)
                .mapNotNull { span ->
                    val start = builder.getSpanStart(span)
                    val end = builder.getSpanEnd(span)
                    if (start >= 0 && end > start) Triple(span, start, end) else null
                }
                .sortedByDescending { it.second }

            spans.forEach { (span, start, end) ->
                val url = span.source.orEmpty().ifBlank {
                    builder.getSpans(start, end, URLSpan::class.java).firstOrNull()?.url.orEmpty()
                }
                val label = if (url.lowercase().contains(".gif")) "View GIF" else "View Image"
                builder.removeSpan(span)
                builder.replace(start, end, label)
                val spanEnd = start + label.length
                if (url.isBlank() || spanEnd <= start) return@forEach
                applyClickableLink(builder, start, spanEnd, sanitizeLinkValue(url), linkColor)
            }
        }

        private fun replaceUrlSpans(builder: SpannableStringBuilder, linkColor: Int) {
            val urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
            urlSpans.forEach { span ->
                val start = builder.getSpanStart(span)
                val end = builder.getSpanEnd(span)
                val url = sanitizeLinkValue(span.url.orEmpty())
                builder.removeSpan(span)
                if (start < 0 || end <= start || url.isBlank()) return@forEach
                applyClickableLink(builder, start, end, url, linkColor)
            }
        }

        private fun linkifyRawUrls(builder: SpannableStringBuilder, linkColor: Int) {
            val content = builder.toString()
            LinkRegex.findAll(content).forEach { match ->
                val sanitized = sanitizeLinkValue(match.value)
                if (sanitized.isBlank()) return@forEach
                val start = match.range.first
                val end = (start + sanitized.length).coerceAtMost(builder.length)
                if (start < 0 || end <= start) return@forEach
                if (builder.getSpans(start, end, ClickableSpan::class.java).isNotEmpty()) return@forEach
                applyClickableLink(builder, start, end, sanitized, linkColor)
            }
        }

        private fun styleSubredditMentions(builder: SpannableStringBuilder, linkColor: Int) {
            val content = builder.toString()
            SubredditRegex.findAll(content).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                if (start < 0 || end <= start || end > builder.length) return@forEach
                if (builder.getSpans(start, end, ClickableSpan::class.java).isNotEmpty()) return@forEach
                builder.setSpan(ForegroundColorSpan(linkColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private fun styleUserMentions(builder: SpannableStringBuilder, linkColor: Int) {
            val content = builder.toString()
            UserRegex.findAll(content).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                if (start < 0 || end <= start || end > builder.length) return@forEach
                if (builder.getSpans(start, end, ClickableSpan::class.java).isNotEmpty()) return@forEach
                builder.setSpan(ForegroundColorSpan(linkColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private fun applyClickableLink(
            builder: SpannableStringBuilder,
            start: Int,
            end: Int,
            url: String,
            linkColor: Int
        ) {
            val resolved = sanitizeLinkValue(url)
            if (resolved.isBlank()) return
            builder.setSpan(
                FeedClickableSpan(
                    url = resolved,
                    linkColor = linkColor,
                    onLinkClick = onLinkClick,
                    onImageClick = onImageClick
                ),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        private fun bindMedia(
            item: FeedRow.Post,
            colors: FeedColors?,
            isGlobalFeed: Boolean,
            subredditLabel: String,
            displayDomain: String
        ) {
            val post = item.post
            resetMedia()

            when (val media = post.media) {
                RedditPostMedia.None -> Unit
                is RedditPostMedia.Image -> {
                    mediaImage.isVisible = true
                    mediaImage.load(media.url) { crossfade(true) }
                    mediaImage.setOnClickListener { onImageClick(media.url) }
                }
                is RedditPostMedia.Link -> bindLinkMedia(media, colors, displayDomain)
                is RedditPostMedia.YouTube -> bindYouTubeMedia(media, colors)
                is RedditPostMedia.Gallery -> bindGalleryMedia(media, colors)
                is RedditPostMedia.Video -> bindVideoMedia(media, colors)
                is RedditPostMedia.RedGifs -> bindRedGifsMedia(media)
                is RedditPostMedia.Streamable -> bindStreamableMedia(media, colors)
                is RedditPostMedia.StreamFF -> bindStreamFFMedia(media)
                is RedditPostMedia.StreamIn -> bindStreamInMedia(media)
            }
        }

        private fun bindLinkMedia(media: RedditPostMedia.Link, colors: FeedColors?, displayDomain: String) {
            if (media.url.isBlank()) return

            val resolvedDomain = media.domain.ifBlank {
                runCatching { Uri.parse(media.url).host.orEmpty() }.getOrDefault("")
            }.removePrefix("www.")

            mediaLinkContainer.isVisible = true
            mediaLinkContainer.setOnClickListener { onLinkClick(media.url) }

            val titleColor = colors?.title ?: Color.WHITE
            val subredditColor = colors?.subreddit ?: Color.CYAN
            val postBackground = colors?.postBackground ?: Color.BLACK

            linkBottomCard.backgroundTintList = ColorStateList.valueOf(postBackground)

            val previewUrl = media.previewImageUrl
            linkPreviewImage.isVisible = !previewUrl.isNullOrBlank()
            if (!previewUrl.isNullOrBlank()) {
                linkPreviewImage.load(previewUrl) { crossfade(true) }
            }

            linkPlaceholder.isVisible = previewUrl.isNullOrBlank()
            linkPlaceholder.setBackgroundColor(withAlpha(postBackground, 0.6f))
            linkPlaceholderText.text = resolvedDomain.ifBlank { displayDomain }.ifBlank { "External link" }
            linkPlaceholderText.setTextColor(withAlpha(titleColor, 0.8f))

            linkDomain.text = resolvedDomain.ifBlank { media.url }
            linkDomain.setTextColor(titleColor)
            linkIcon.setColorFilter(subredditColor)
        }

        private fun bindYouTubeMedia(media: RedditPostMedia.YouTube, colors: FeedColors?) {
            youtubeContainer.isVisible = true
            youtubeContainer.setAspectRatio(16f / 9f)
            youtubeContainer.setBackgroundColor(colors?.spacerBackground ?: Color.BLACK)

            youtubeThumbnail.load(media.thumbnailUrl) { crossfade(true) }

            youtubePlayButton.backgroundTintList = ColorStateList.valueOf(withAlpha(Color.BLACK, 0.6f))
            youtubePlayIcon.setColorFilter(Color.WHITE)

            youtubeContainer.setOnClickListener {
                val id = media.videoId.trim()
                if (id.isNotBlank()) onYouTubeSelected(id)
            }
        }

        private fun bindRedGifsMedia(media: RedditPostMedia.RedGifs) {
            if (media.embedUrl.isBlank()) return
            val ratio = media.width?.takeIf { it > 0 }?.let { width ->
                media.height?.takeIf { it > 0 }?.let { height ->
                    width.toFloat() / height.toFloat()
                }
            } ?: (16f / 9f)
            bindWebMedia(url = media.embedUrl, ratio = ratio) { webView ->
                webView.settings.javaScriptEnabled = true
                webView.settings.loadWithOverviewMode = true
                webView.settings.useWideViewPort = true
                webView.webViewClient = WebViewClient()
            }
        }

        private fun bindStreamFFMedia(media: RedditPostMedia.StreamFF) {
            if (media.embedUrl.isBlank()) return
            bindWebMedia(url = media.embedUrl, ratio = 16f / 9f) { webView ->
                webView.settings.javaScriptEnabled = true
                webView.settings.loadWithOverviewMode = true
                webView.settings.useWideViewPort = true
                webView.settings.mediaPlaybackRequiresUserGesture = false
                webView.settings.domStorageEnabled = true
                webView.webViewClient = WebViewClient()
            }
        }

        private fun bindStreamInMedia(media: RedditPostMedia.StreamIn) {
            if (media.embedUrl.isBlank()) return
            bindWebMedia(url = media.embedUrl, ratio = 16f / 9f) { webView ->
                webView.settings.javaScriptEnabled = true
                webView.settings.loadWithOverviewMode = true
                webView.settings.useWideViewPort = true
                webView.settings.mediaPlaybackRequiresUserGesture = false
                webView.settings.domStorageEnabled = true
                webView.webViewClient = WebViewClient()
            }
        }

        private fun bindStreamableMedia(media: RedditPostMedia.Streamable, colors: FeedColors?) {
            val expectedPostId = boundPostId
            if (expectedPostId.isNullOrBlank()) return

            val titleColor = colors?.title ?: Color.WHITE
            val metaColor = colors?.metaInfo ?: Color.LTGRAY
            val background = colors?.spacerBackground ?: Color.BLACK

            videoContainer.isVisible = true
            videoControls.isVisible = false
            setVideoStatus(
                isVisible = true,
                backgroundColor = background,
                text = "Loading video...",
                textColor = withAlpha(titleColor, 0.6f),
                thumbnailUrl = null
            )

            val layoutParams = videoContainer.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            videoContainer.layoutParams = layoutParams
            videoContainer.setAspectRatio(16f / 9f)

            mediaJob?.cancel()
            mediaJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                val videoUrl = runCatching {
                    withContext(Dispatchers.IO) { fetchStreamableVideoUrl(media.shortcode) }
                }.getOrNull()

                if (boundPostId != expectedPostId) return@launch

                if (videoUrl.isNullOrBlank()) {
                    setVideoStatus(
                        isVisible = true,
                        backgroundColor = background,
                        text = "Failed to load video",
                        textColor = metaColor,
                        thumbnailUrl = media.thumbnailUrl
                    )
                    return@launch
                }

                setVideoStatus(
                    isVisible = false,
                    backgroundColor = background,
                    text = "",
                    textColor = titleColor,
                    thumbnailUrl = null
                )
                videoControls.isVisible = true
                bindVideoMedia(
                    RedditPostMedia.Video(
                        url = videoUrl,
                        hasAudio = true,
                        width = 1280,
                        height = 720,
                        durationSeconds = null
                    ),
                    colors
                )
            }
        }

        private fun bindWebMedia(url: String, ratio: Float, configure: (WebView) -> Unit) {
            webContainer.isVisible = true
            val layoutParams = webContainer.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            webContainer.layoutParams = layoutParams
            webContainer.setAspectRatio(ratio)

            val webView = ensureWebView()
            webView.onResume()
            webView.resumeTimers()
            webView.stopLoading()
            configure(webView)
            webView.loadUrl(url)
        }

        private fun ensureWebView(): WebView {
            val existing = webView
            if (existing != null) return existing

            return WebView(view.context).also { created ->
                created.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                created.isHorizontalScrollBarEnabled = false
                created.isVerticalScrollBarEnabled = false
                webHost.addView(created)
                webView = created
            }
        }

        private fun pauseWebView() {
            val webView = webView ?: return
            webView.stopLoading()
            webView.onPause()
            webView.pauseTimers()
            webView.loadUrl("about:blank")
        }

        private fun destroyWebView() {
            val webView = webView ?: return
            this.webView = null
            webHost.removeView(webView)
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.pauseTimers()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }

        private fun setVideoStatus(
            isVisible: Boolean,
            backgroundColor: Int,
            text: String,
            textColor: Int,
            thumbnailUrl: String?
        ) {
            videoStatusOverlay.isVisible = isVisible
            if (!isVisible) {
                videoStatusText.text = ""
                videoStatusThumbnail.setImageDrawable(null)
                videoStatusThumbnail.isVisible = false
                return
            }
            videoStatusOverlay.setBackgroundColor(backgroundColor)
            videoStatusText.text = text
            videoStatusText.setTextColor(textColor)

            if (!thumbnailUrl.isNullOrBlank()) {
                videoStatusThumbnail.isVisible = true
                videoStatusThumbnail.load(thumbnailUrl) { crossfade(true) }
            } else {
                videoStatusThumbnail.setImageDrawable(null)
                videoStatusThumbnail.isVisible = false
            }
        }

        private fun bindGalleryMedia(media: RedditPostMedia.Gallery, colors: FeedColors?) {
            val images = media.images
            if (images.isEmpty()) return

            galleryContainer.isVisible = true

            val urls = images.map { it.url }
            galleryUrls = urls
            galleryAdapter.urls = urls

            val postBackground = colors?.postBackground ?: Color.BLACK
            val titleColor = colors?.title ?: Color.WHITE
            galleryDots.backgroundTintList = ColorStateList.valueOf(withAlpha(postBackground, 0.7f))

            galleryScrollListener?.let { galleryRecycler.removeOnScrollListener(it) }
            galleryScrollListener = object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    updateGalleryDots(titleColor)
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateGalleryDots(titleColor)
                }
            }.also { galleryRecycler.addOnScrollListener(it) }

            buildGalleryDots(urls.size, titleColor)
            galleryRecycler.post { updateGalleryDots(titleColor) }
        }

        private fun buildGalleryDots(count: Int, titleColor: Int) {
            galleryDots.removeAllViews()
            repeat(count) { index ->
                val dot = View(view.context)
                val inactiveSize = view.resources.getDimensionPixelSize(R.dimen.spacing_sm) - view.resources.getDimensionPixelSize(R.dimen.spacing_xs) / 2
                val activeSize = view.resources.getDimensionPixelSize(R.dimen.spacing_sm)
                val params = LinearLayout.LayoutParams(inactiveSize, inactiveSize)
                if (index > 0) {
                    params.marginStart = view.resources.getDimensionPixelSize(R.dimen.spacing_xs) + 2
                }
                dot.layoutParams = params
                dot.background = dotBackground(withAlpha(titleColor, 0.4f))
                dot.tag = Pair(activeSize, inactiveSize)
                galleryDots.addView(dot)
            }
        }

        private fun updateGalleryDots(titleColor: Int) {
            val layoutManager = galleryRecycler.layoutManager as? LinearLayoutManager ?: return
            val snapView = gallerySnapHelper.findSnapView(layoutManager) ?: return
            val position = layoutManager.getPosition(snapView).coerceAtLeast(0)
            for (i in 0 until galleryDots.childCount) {
                val dot = galleryDots.getChildAt(i)
                val (activeSize, inactiveSize) = (dot.tag as? Pair<Int, Int>) ?: continue
                val isActive = i == position
                val size = if (isActive) activeSize else inactiveSize
                dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
                    width = size
                    height = size
                }
                dot.background = dotBackground(if (isActive) titleColor else withAlpha(titleColor, 0.4f))
            }
        }

        private fun dotBackground(color: Int): android.graphics.drawable.Drawable {
            return android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
            }
        }

        private fun bindVideoMedia(media: RedditPostMedia.Video, colors: FeedColors?) {
            videoContainer.isVisible = true
            videoControls.isVisible = true
            setVideoStatus(
                isVisible = false,
                backgroundColor = colors?.spacerBackground ?: Color.BLACK,
                text = "",
                textColor = colors?.title ?: Color.WHITE,
                thumbnailUrl = null
            )

            val ratio = media.width?.takeIf { it > 0 }?.let { width ->
                media.height?.takeIf { it > 0 }?.let { height ->
                    width.toFloat() / height.toFloat()
                }
            }

            val layoutParams = videoContainer.layoutParams
            if (ratio != null && ratio > 0f) {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                videoContainer.layoutParams = layoutParams
                videoContainer.setAspectRatio(ratio)
            } else {
                layoutParams.height = view.context.dpToPx(220f)
                videoContainer.layoutParams = layoutParams
                videoContainer.setAspectRatio(0f)
            }

            val player = ensureVideoPlayer(media)
            videoPlayerView.player = player
            player.playWhenReady = true
            player.play()

            isMuted = true
            player.volume = 0f

            val white = ColorStateList.valueOf(Color.WHITE)
            videoSeekBar.progressTintList = white
            videoSeekBar.thumbTintList = white
            videoSeekBar.progressBackgroundTintList = ColorStateList.valueOf(Color.DKGRAY)

            videoSeekBar.isVisible = false
            videoDurationMs = 0L

            videoMuteButton.backgroundTintList = ColorStateList.valueOf(Color.BLACK)
            videoMuteButton.imageTintList = white
            videoMuteButton.isEnabled = media.hasAudio
            videoMuteButton.alpha = if (media.hasAudio) 1f else 0.4f
            updateMuteIcon(media.hasAudio)
            videoMuteButton.setOnClickListener {
                if (!media.hasAudio) return@setOnClickListener
                isMuted = !isMuted
                player.volume = if (isMuted) 0f else 1f
                updateMuteIcon(media.hasAudio)
            }

            player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    val duration = player.duration.takeIf { it > 0 } ?: return
                    if (duration == videoDurationMs) return
                    videoDurationMs = duration
                    videoSeekBar.isVisible = true
                }
            })

            startVideoProgressUpdates()
        }

        private fun ensureVideoPlayer(media: RedditPostMedia.Video): ExoPlayer {
            val existing = videoPlayer
            if (existing != null) return existing

            val context = view.context
            return ExoPlayer.Builder(context).build().also { player ->
                val itemBuilder = MediaItem.Builder().setUri(media.url)
                when {
                    media.url.endsWith(".m3u8", ignoreCase = true) -> itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    media.url.endsWith(".mpd", ignoreCase = true) -> itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                }
                player.setMediaItem(itemBuilder.build())
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.playWhenReady = true
                player.prepare()
                videoPlayer = player
            }
        }

        private fun startVideoProgressUpdates() {
            progressRunnable?.let { handler.removeCallbacks(it) }
            val runnable = object : Runnable {
                override fun run() {
                    val duration = videoDurationMs
                    val player = videoPlayer
                    if (duration > 0L && player != null && !isScrubbing) {
                        val progress = (player.currentPosition.coerceAtMost(duration).toFloat() / duration.toFloat())
                            .coerceIn(0f, 1f)
                        videoSeekBar.progress = (progress * 1000f).roundToInt()
                    }
                    handler.postDelayed(this, 250L)
                }
            }
            progressRunnable = runnable
            handler.post(runnable)
        }

        private fun updateMuteIcon(hasAudio: Boolean) {
            val resId = if (isMuted || !hasAudio) R.drawable.ic_mute else R.drawable.ic_volume
            videoMuteButton.setImageResource(resId)
            videoMuteButton.contentDescription = if (isMuted) "Unmute" else "Mute"
        }

        private fun resetMedia() {
            mediaJob?.cancel()
            mediaJob = null

            mediaImage.isVisible = false
            mediaLinkContainer.isVisible = false
            youtubeContainer.isVisible = false
            videoContainer.isVisible = false
            webContainer.isVisible = false
            galleryContainer.isVisible = false

            mediaImage.setOnClickListener(null)
            mediaLinkContainer.setOnClickListener(null)
            youtubeContainer.setOnClickListener(null)

            recycleVideo()
            pauseWebView()
            recycleGallery()
        }

        private fun recycleVideo() {
            progressRunnable?.let { handler.removeCallbacks(it) }
            progressRunnable = null
            videoPlayerView.player = null
            videoPlayer?.release()
            videoPlayer = null
            videoDurationMs = 0L
            videoControls.isVisible = true
            setVideoStatus(
                isVisible = false,
                backgroundColor = Color.BLACK,
                text = "",
                textColor = Color.WHITE,
                thumbnailUrl = null
            )
        }

        private fun recycleGallery() {
            galleryAdapter.urls = emptyList()
            galleryDots.removeAllViews()
            galleryScrollListener?.let { galleryRecycler.removeOnScrollListener(it) }
            galleryScrollListener = null
        }

        fun recycle() {
            resetMedia()
            destroyWebView()
        }
    }

    companion object {
        private const val VIEW_TYPE_POST = 1
        private const val VIEW_TYPE_LOADING = 2
        private const val VIEW_TYPE_SPACER = 3

        private val streamableUrlCache = android.util.LruCache<String, String>(64)

        private val LinkRegex = Regex("https?://[^\\s]+")
        private val SubredditRegex = Regex("(?<![A-Za-z0-9_])r/[A-Za-z0-9_]+", RegexOption.IGNORE_CASE)
        private val UserRegex = Regex("(?<![A-Za-z0-9_])u/[A-Za-z0-9_-]+", RegexOption.IGNORE_CASE)
        private val TrailingUrlDelimiters = charArrayOf(')', ']', '}', '>', ',', '.', ';', ':', '\"', '\'')

        private fun sanitizeLinkValue(raw: String): String {
            var end = raw.length
            while (end > 0) {
                val lastChar = raw[end - 1]
                if (lastChar !in TrailingUrlDelimiters) break
                if (lastChar == ')') {
                    val prefix = raw.substring(0, end - 1)
                    val openCount = prefix.count { it == '(' }
                    val closeCount = prefix.count { it == ')' }
                    if (openCount > closeCount) {
                        break
                    }
                }
                end--
            }
            return raw.substring(0, end)
        }

        private fun isLikelyImageUrl(raw: String): Boolean {
            val lower = raw.lowercase()
            return lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".png") ||
                lower.endsWith(".gif") ||
                lower.endsWith(".webp") ||
                "preview.redd.it" in lower ||
                "i.redd.it" in lower
        }

        private object FeedLinkMovementMethod : LinkMovementMethod() {
            override fun onTouchEvent(widget: android.widget.TextView, buffer: Spannable, event: MotionEvent): Boolean {
                val action = event.action
                if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_DOWN) {
                    return super.onTouchEvent(widget, buffer, event)
                }

                val layout = widget.layout ?: return false
                val x = (event.x - widget.totalPaddingLeft + widget.scrollX).toInt()
                val y = (event.y - widget.totalPaddingTop + widget.scrollY).toInt()
                val line = layout.getLineForVertical(y)
                val offset = layout.getOffsetForHorizontal(line, x.toFloat())

                val link = buffer.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
                if (link == null) {
                    Selection.removeSelection(buffer)
                    return false
                }

                if (action == MotionEvent.ACTION_UP) {
                    link.onClick(widget)
                    Selection.removeSelection(buffer)
                } else {
                    Selection.setSelection(buffer, buffer.getSpanStart(link), buffer.getSpanEnd(link))
                }
                return true
            }
        }

        private class FeedClickableSpan(
            private val url: String,
            private val linkColor: Int,
            private val onLinkClick: (String) -> Unit,
            private val onImageClick: (String) -> Unit
        ) : ClickableSpan() {
            override fun onClick(widget: View) {
                if (isLikelyImageUrl(url)) {
                    onImageClick(url)
                } else {
                    onLinkClick(url)
                }
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = linkColor
                ds.isUnderlineText = false
            }
        }

        private suspend fun fetchStreamableVideoUrl(shortcode: String): String? {
            synchronized(streamableUrlCache) {
                streamableUrlCache.get(shortcode)
            }?.let { return it }

            val api = runCatching { GlobalContext.get().get<StreamableApiService>() }.getOrNull() ?: return null
            val response = runCatching { api.getVideo(shortcode) }.getOrNull() ?: return null
            val rawUrl = when {
                response.files?.get("mp4")?.url?.isNotBlank() == true -> response.files["mp4"]?.url
                response.files?.get("mp4-mobile")?.url?.isNotBlank() == true -> response.files["mp4-mobile"]?.url
                response.status != null && response.status == 2 -> response.url?.takeIf { it.isNotBlank() }
                else -> null
            }

            val videoUrl = rawUrl?.let { url ->
                when {
                    url.startsWith("https://") -> url
                    url.startsWith("http://") -> url.replace("http://", "https://")
                    url.startsWith("//") -> "https:$url"
                    else -> url
                }
            }?.takeIf { it.isNotBlank() }

            if (videoUrl == null) {
                Log.e("StreamableVideo", "Failed to get video URL for $shortcode. status=${response.status} files=${response.files?.keys}")
                return null
            }

            synchronized(streamableUrlCache) {
                streamableUrlCache.put(shortcode, videoUrl)
            }
            return videoUrl
        }

        private val DIFF = object : DiffUtil.ItemCallback<FeedRow>() {
            override fun areItemsTheSame(oldItem: FeedRow, newItem: FeedRow): Boolean {
                return when {
                    oldItem is FeedRow.Post && newItem is FeedRow.Post -> oldItem.post.id == newItem.post.id
                    oldItem is FeedRow.LoadingFooter && newItem is FeedRow.LoadingFooter -> true
                    oldItem is FeedRow.EndSpacer && newItem is FeedRow.EndSpacer -> true
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: FeedRow, newItem: FeedRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}
