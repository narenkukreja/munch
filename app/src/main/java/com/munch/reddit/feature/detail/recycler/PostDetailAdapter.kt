package com.munch.reddit.feature.detail.recycler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.MetricAffectingSpan
import android.text.style.QuoteSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.TypedValue
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
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.munch.reddit.R
import com.munch.reddit.activity.TableViewerActivity
import com.munch.reddit.data.remote.StreamableApiService
import com.munch.reddit.domain.model.FlairRichText
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.domain.model.RedditPostMedia
import com.munch.reddit.feature.feed.recycler.GalleryImageAdapter
import com.munch.reddit.feature.feed.recycler.dpToPx
import com.munch.reddit.feature.feed.recycler.withAlpha
import com.munch.reddit.feature.shared.RedditHtmlTable
import com.munch.reddit.feature.shared.formatCount
import com.munch.reddit.feature.shared.formatRelativeTime
import com.munch.reddit.feature.shared.parseHtmlText
import com.munch.reddit.feature.shared.parseTablesFromHtml
import com.munch.reddit.feature.shared.stripTablesFromHtml
import com.munch.reddit.theme.TextSizeDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import kotlin.math.roundToInt

data class PostDetailColors(
    val postBackground: Int,
    val spacerBackground: Int,
    val title: Int,
    val subreddit: Int,
    val metaInfo: Int,
    val pinnedLabel: Int,
    val modLabel: Int,
    val opLabel: Int,
    val error: Int,
    val onSurfaceVariant: Int,
    val outlineVariant: Int,
    val tableCardBackground: Int,
    val tableCardTitle: Int,
    val tableChipBackground: Int,
    val tableChipContent: Int
)

class PostDetailAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val onTogglePostBody: () -> Unit,
    private val onSelectSort: (String) -> Unit,
    private val onToggleComment: (String) -> Unit,
    private val onShowCollapsedHistory: () -> Unit,
    private val onUserLoadMoreComments: () -> Unit,
    private val onLoadRemoteReplies: (String) -> Unit,
    private val onLoadMoreReplies: (String) -> Unit,
    private val onOpenImage: (String) -> Unit,
    private val onOpenGallery: (List<String>, Int) -> Unit,
    private val onOpenYouTube: (String) -> Unit,
    private val onOpenLink: (String) -> Unit
) : ListAdapter<PostDetailRow, RecyclerView.ViewHolder>(DIFF) {

    var colors: PostDetailColors? = null
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var flairEmojiLookup: Map<String, String> = emptyMap()
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var commentTextSizeSp: Float = TextSizeDefaults.DefaultSizeSp
        set(value) {
            val clamped = TextSizeDefaults.clamp(value)
            if (field == clamped) return
            field = clamped
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is PostDetailRow.Header -> VIEW_TYPE_HEADER
        is PostDetailRow.SortBar -> VIEW_TYPE_SORT_BAR
        PostDetailRow.RefreshingComments -> VIEW_TYPE_REFRESHING
        PostDetailRow.EmptyComments -> VIEW_TYPE_EMPTY
        is PostDetailRow.CommentNode -> VIEW_TYPE_COMMENT
        is PostDetailRow.LoadMoreReplies -> VIEW_TYPE_LOAD_MORE_REPLIES
        is PostDetailRow.RemoteReplies -> VIEW_TYPE_REMOTE_REPLIES
        is PostDetailRow.CollapsedHistory -> VIEW_TYPE_COLLAPSED_HISTORY
        is PostDetailRow.LoadMoreComments -> VIEW_TYPE_LOAD_MORE_COMMENTS
        PostDetailRow.EndSpacer -> VIEW_TYPE_END_SPACER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_post_detail_header, parent, false),
                lifecycleOwner = lifecycleOwner,
                getColors = { colors },
                onToggleBody = onTogglePostBody,
                onImageClick = onOpenImage,
                onGalleryPreview = onOpenGallery,
                onLinkClick = onOpenLink,
                onYouTubeSelected = onOpenYouTube
            )
            VIEW_TYPE_SORT_BAR -> SortBarViewHolder(
                inflater.inflate(R.layout.item_post_detail_sort_bar, parent, false)
            )
            VIEW_TYPE_REFRESHING -> LoadingViewHolder(
                inflater.inflate(R.layout.item_feed_footer_loading, parent, false)
            )
            VIEW_TYPE_EMPTY -> EmptyViewHolder(
                inflater.inflate(R.layout.item_post_detail_empty, parent, false)
            )
            VIEW_TYPE_COMMENT -> CommentViewHolder(
                inflater.inflate(R.layout.item_post_detail_comment, parent, false)
            )
            VIEW_TYPE_LOAD_MORE_REPLIES -> LoadMoreRepliesViewHolder(
                inflater.inflate(R.layout.item_post_detail_load_more_replies, parent, false)
            )
            VIEW_TYPE_REMOTE_REPLIES -> RemoteRepliesViewHolder(
                inflater.inflate(R.layout.item_post_detail_remote_replies, parent, false)
            )
            VIEW_TYPE_COLLAPSED_HISTORY -> CollapsedHistoryViewHolder(
                inflater.inflate(R.layout.item_post_detail_collapsed_history, parent, false)
            )
            VIEW_TYPE_LOAD_MORE_COMMENTS -> LoadMoreCommentsViewHolder(
                inflater.inflate(R.layout.item_post_detail_load_more_comments, parent, false)
            )
            VIEW_TYPE_END_SPACER -> SpacerViewHolder(
                inflater.inflate(R.layout.item_feed_footer_spacer, parent, false)
            )
            else -> error("Unknown viewType=$viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is PostDetailRow.Header -> (holder as HeaderViewHolder).bind(row)
            is PostDetailRow.SortBar -> (holder as SortBarViewHolder).bind(row, colors, onSelectSort)
            PostDetailRow.RefreshingComments -> (holder as LoadingViewHolder).bind(colors)
            PostDetailRow.EmptyComments -> (holder as EmptyViewHolder).bind(colors)
            is PostDetailRow.CommentNode -> (holder as CommentViewHolder).bind(
                row.item,
                colors,
                commentTextSizeSp,
                flairEmojiLookup,
                onToggleComment = onToggleComment,
                onOpenLink = onOpenLink,
                onOpenImage = onOpenImage
            )
            is PostDetailRow.LoadMoreReplies -> (holder as LoadMoreRepliesViewHolder).bind(
                row.item,
                colors,
                onLoadMoreReplies
            )
            is PostDetailRow.RemoteReplies -> (holder as RemoteRepliesViewHolder).bind(
                row.item,
                colors,
                onLoadRemoteReplies
            )
            is PostDetailRow.CollapsedHistory -> (holder as CollapsedHistoryViewHolder).bind(
                row.item,
                colors,
                onShowCollapsedHistory
            )
            is PostDetailRow.LoadMoreComments -> (holder as LoadMoreCommentsViewHolder).bind(
                row,
                colors,
                onUserLoadMoreComments
            )
            PostDetailRow.EndSpacer -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? HeaderViewHolder)?.recycle()
    }

    private class LoadingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val indicator: ProgressBar = view.findViewById(R.id.feed_loading_indicator)

        fun bind(colors: PostDetailColors?) {
            val tint = colors?.subreddit ?: Color.WHITE
            indicator.indeterminateTintList = ColorStateList.valueOf(tint)
        }
    }

    private class SpacerViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.post_detail_empty_text)

        fun bind(colors: PostDetailColors?) {
            label.setTextColor(colors?.metaInfo ?: Color.LTGRAY)
        }
    }

    private class SortBarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val surface: View = view.findViewById(R.id.sort_surface)
        private val chipContainer: LinearLayout = view.findViewById(R.id.sort_chip_container)

        fun bind(row: PostDetailRow.SortBar, colors: PostDetailColors?, onSelectSort: (String) -> Unit) {
            val surfaceBackground = colors?.postBackground ?: Color.BLACK
            ViewCompat.setBackgroundTintList(surface, ColorStateList.valueOf(surfaceBackground))
            chipContainer.removeAllViews()

            if (row.sortOptions.isEmpty()) return

            val spacingSm = surface.resources.getDimensionPixelSize(R.dimen.spacing_sm)
            val spacingXs = surface.resources.getDimensionPixelSize(R.dimen.spacing_xs)
            val cornerRadius = surface.resources.getDimension(R.dimen.spacing_xs)
            val strokeWidthPx = (surface.resources.displayMetrics.density * 1f).roundToInt().coerceAtLeast(1)

            val titleColor = colors?.title ?: Color.WHITE
            val metaColor = colors?.metaInfo ?: Color.LTGRAY
            val subredditColor = colors?.subreddit ?: Color.CYAN

            row.sortOptions.forEachIndexed { index, option ->
                val normalized = option.lowercase()
                val isSelected = normalized == row.selectedSort.lowercase()

                val chip = TextView(surface.context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { params ->
                        if (index > 0) {
                            params.marginStart = spacingSm
                        }
                    }
                    setPadding(spacingSm, spacingXs, spacingSm, spacingXs)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    text = normalized.replaceFirstChar { it.uppercaseChar() }
                    setTextColor(if (isSelected) titleColor else metaColor)

                    this.background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        this.cornerRadius = cornerRadius
                        setColor(if (isSelected) withAlpha(subredditColor, 0.22f) else surfaceBackground)
                        setStroke(strokeWidthPx, if (isSelected) subredditColor else metaColor)
                    }

                    setOnClickListener { onSelectSort(normalized) }
                }
                chipContainer.addView(chip)
            }
        }
    }

    private class CollapsedHistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val surface: View = view.findViewById(R.id.collapsed_history_surface)
        private val label: TextView = view.findViewById(R.id.collapsed_history_text)
        private val icon: ImageView = view.findViewById(R.id.collapsed_history_icon)

        fun bind(
            item: com.munch.reddit.feature.detail.PostDetailViewModel.CommentListItem.CollapsedHistoryDivider,
            colors: PostDetailColors?,
            onShowCollapsedHistory: () -> Unit
        ) {
            val background = colors?.postBackground ?: Color.BLACK
            val accent = colors?.subreddit ?: Color.CYAN
            ViewCompat.setBackgroundTintList(surface, ColorStateList.valueOf(background))
            label.text = when (val count = item.hiddenCount) {
                1 -> "View older comments (1 hidden)"
                0 -> "View older comments"
                else -> "View older comments (${formatCount(count)} hidden)"
            }
            label.setTextColor(accent)
            icon.setColorFilter(accent)

            itemView.setOnClickListener { onShowCollapsedHistory() }
        }
    }

    private class LoadMoreRepliesViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val stripe: View = view.findViewById(R.id.load_more_replies_indent_stripe)
        private val label: TextView = view.findViewById(R.id.load_more_replies_text)

        fun bind(
            item: com.munch.reddit.feature.detail.PostDetailViewModel.CommentListItem.LoadMoreRepliesNode,
            colors: PostDetailColors?,
            onLoadMoreReplies: (String) -> Unit
        ) {
            val accent = colors?.subreddit ?: Color.CYAN
            applyIndent(stripe = stripe, content = label, depth = item.depth, accentColor = accent)

            val remaining = item.remainingCount
            label.text = if (remaining == 1) {
                "load more comments... (1 reply)"
            } else {
                "load more comments... ($remaining replies)"
            }
            label.setTextColor(accent)

            itemView.setOnClickListener { onLoadMoreReplies(item.parentId) }
        }
    }

    private class RemoteRepliesViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val stripe: View = view.findViewById(R.id.remote_replies_indent_stripe)
        private val content: View = view.findViewById(R.id.remote_replies_content)

        private val loadingRow: View = view.findViewById(R.id.remote_replies_loading_row)
        private val progress: ProgressBar = view.findViewById(R.id.remote_replies_progress)
        private val loadingText: TextView = view.findViewById(R.id.remote_replies_loading_text)

        private val errorColumn: View = view.findViewById(R.id.remote_replies_error_column)
        private val errorText: TextView = view.findViewById(R.id.remote_replies_error_text)
        private val retry: TextView = view.findViewById(R.id.remote_replies_retry)

        private val label: TextView = view.findViewById(R.id.remote_replies_label)

        fun bind(
            item: com.munch.reddit.feature.detail.PostDetailViewModel.CommentListItem.RemoteRepliesNode,
            colors: PostDetailColors?,
            onLoadRemoteReplies: (String) -> Unit
        ) {
            val accent = colors?.subreddit ?: Color.CYAN
            val meta = colors?.metaInfo ?: Color.LTGRAY
            val error = colors?.error ?: Color.RED

            applyIndent(stripe = stripe, content = content, depth = item.depth, accentColor = accent)

            progress.indeterminateTintList = ColorStateList.valueOf(accent)
            loadingText.setTextColor(meta)
            errorText.setTextColor(error)
            retry.setTextColor(accent)

            loadingRow.isVisible = item.isLoading
            errorColumn.isVisible = item.hasError
            label.isVisible = !item.isLoading && !item.hasError

            itemView.setOnClickListener(null)
            retry.setOnClickListener(null)

            when {
                item.isLoading -> Unit
                item.hasError -> {
                    retry.setOnClickListener { onLoadRemoteReplies(item.parentId) }
                }
                else -> {
                    val labelText = when (item.pendingCount) {
                        0 -> "load more comments..."
                        1 -> "load more comments... (1 reply)"
                        else -> "load more comments... (${formatCount(item.pendingCount)} replies)"
                    }
                    label.text = labelText
                    label.setTextColor(accent)
                    itemView.setOnClickListener { onLoadRemoteReplies(item.parentId) }
                }
            }
        }
    }

    private class LoadMoreCommentsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val progress: ProgressBar = view.findViewById(R.id.load_more_comments_progress)
        private val button: TextView = view.findViewById(R.id.load_more_comments_button)

        fun bind(
            row: PostDetailRow.LoadMoreComments,
            colors: PostDetailColors?,
            onUserLoadMoreComments: () -> Unit
        ) {
            val accent = colors?.subreddit ?: Color.CYAN
            progress.indeterminateTintList = ColorStateList.valueOf(accent)
            button.setTextColor(accent)

            progress.isVisible = row.isAppending
            button.isVisible = !row.isAppending

            button.setOnClickListener(null)
            if (!row.isAppending) {
                button.text = if (row.pendingRemoteReplyCount > 0) {
                    val unit = if (row.pendingRemoteReplyCount == 1) "reply" else "replies"
                    "Load more comments (${formatCount(row.pendingRemoteReplyCount)} $unit)"
                } else {
                    "Load more comments"
                }
                button.setOnClickListener { onUserLoadMoreComments() }
            }
        }
    }

    private class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root: View = view.findViewById(R.id.comment_root)
        private val stripe: View = view.findViewById(R.id.comment_indent_stripe)
        private val content: ViewGroup = view.findViewById(R.id.comment_content)

        private val author: TextView = view.findViewById(R.id.comment_author)
        private val flairBadge: View = view.findViewById(R.id.comment_flair_badge)
        private val flairEmoji: ImageView = view.findViewById(R.id.comment_flair_emoji)
        private val flairText: TextView = view.findViewById(R.id.comment_flair_text)
        private val modBadge: TextView = view.findViewById(R.id.comment_mod_badge)
        private val opBadge: TextView = view.findViewById(R.id.comment_op_badge)

        private val timeIcon: ImageView = view.findViewById(R.id.comment_time_icon)
        private val timeText: TextView = view.findViewById(R.id.comment_time_text)
        private val scoreIcon: ImageView = view.findViewById(R.id.comment_score_icon)
        private val scoreText: TextView = view.findViewById(R.id.comment_score_text)

        private val body: TextView = view.findViewById(R.id.comment_body)
        private val collapsedLabel: TextView = view.findViewById(R.id.comment_collapsed_label)

        init {
            body.movementMethod = null
            body.highlightColor = Color.TRANSPARENT
            body.setOnTouchListener(DetailLinkTouchListener)
        }

        fun bind(
            node: com.munch.reddit.feature.detail.PostDetailViewModel.CommentListItem.CommentNode,
            colors: PostDetailColors?,
            commentTextSizeSp: Float,
            flairEmojiLookup: Map<String, String>,
            onToggleComment: (String) -> Unit,
            onOpenLink: (String) -> Unit,
            onOpenImage: (String) -> Unit
        ) {
            val comment = node.comment
            val accent = colors?.subreddit ?: Color.CYAN
            val meta = colors?.metaInfo ?: Color.LTGRAY
            val title = colors?.title ?: Color.WHITE

            val depth = node.depth
            val parentVerticalPaddingPx = (root.resources.getDimension(R.dimen.spacing_sm) * 0.5f).roundToInt()
            val nestedVerticalPaddingPx = (root.resources.getDimension(R.dimen.spacing_xs) * 0.5f).roundToInt()
            val verticalPaddingPx = if (depth > 0) nestedVerticalPaddingPx else parentVerticalPaddingPx
            val startPadding = root.resources.getDimensionPixelSize(R.dimen.spacing_lg)
            val endPadding = root.resources.getDimensionPixelSize(R.dimen.spacing_lg)
            root.setPaddingRelative(startPadding, verticalPaddingPx, endPadding, verticalPaddingPx)

            applyIndent(stripe = stripe, content = content, depth = depth, accentColor = withAlpha(accent, 0.6f))

            author.text = comment.author
            author.setTextColor(accent)

            val (emojiUrl, flairLabel) = resolveFlair(
                flairText = comment.authorFlairText,
                flairRichtext = comment.authorFlairRichtext,
                flairEmojiLookup = flairEmojiLookup
            )
            val showFlair = !flairLabel.isNullOrBlank() && !node.isOp
            flairBadge.isVisible = showFlair
            if (showFlair) {
                flairText.text = flairLabel
                flairEmoji.isVisible = !emojiUrl.isNullOrBlank()
                if (!emojiUrl.isNullOrBlank()) {
                    flairEmoji.load(emojiUrl) { crossfade(true) }
                } else {
                    flairEmoji.setImageDrawable(null)
                }
                flairBadge.setOnClickListener {
                    showFlairDialog(
                        context = itemView.context,
                        emojiUrl = emojiUrl,
                        label = flairLabel ?: ""
                    )
                }
            } else {
                flairBadge.setOnClickListener(null)
                flairEmoji.setImageDrawable(null)
            }

            modBadge.isVisible = node.isAutoModerator
            if (node.isAutoModerator) {
                val labelColor = colors?.modLabel ?: Color.GREEN
                modBadge.setTextColor(labelColor)
                modBadge.backgroundTintList = ColorStateList.valueOf(withAlpha(labelColor, 0.18f))
            }

            opBadge.isVisible = node.isOp
            if (node.isOp) {
                val labelColor = colors?.opLabel ?: Color.RED
                opBadge.setTextColor(labelColor)
                opBadge.backgroundTintList = ColorStateList.valueOf(withAlpha(labelColor, 0.18f))
            }

            timeIcon.setColorFilter(meta)
            scoreIcon.setColorFilter(meta)
            timeText.setTextColor(meta)
            scoreText.setTextColor(meta)
            timeText.text = formatRelativeTime(comment.createdUtc)
            scoreText.text = formatCount(comment.score)

            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, commentTextSizeSp)

            val repliesLabel = when (node.replyCount) {
                0 -> "Replies hidden (0 replies)"
                1 -> "Replies hidden (1 reply)"
                else -> "Replies hidden (${formatCount(node.replyCount)} replies)"
            }

            body.isVisible = !node.isCollapsed
            collapsedLabel.isVisible = node.isCollapsed

            if (node.isCollapsed) {
                collapsedLabel.text = repliesLabel
                collapsedLabel.setTextColor(meta)
                body.text = null
            } else {
                val builder = buildLinkSpannable(
                    plainText = comment.body,
                    htmlText = comment.bodyHtml,
                    linkColor = accent,
                    onLinkClick = onOpenLink,
                    onImageClick = onOpenImage,
                    density = itemView.resources.displayMetrics.density
                )
                body.text = builder
                body.setTextColor(withAlpha(title, 0.9f))
            }

            itemView.setOnClickListener { onToggleComment(comment.id) }
            itemView.setOnLongClickListener {
                itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showCommentOptionsDialog(itemView.context, comment)
                true
            }
        }

        private fun showCommentOptionsDialog(context: Context, comment: com.munch.reddit.domain.model.RedditComment) {
            val items = arrayOf(
                "View u/${comment.author}",
                "Copy text",
                "Share comment"
            )
            android.app.AlertDialog.Builder(context)
                .setTitle("Comment Options")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> {
                            val profileUrl = "https://www.reddit.com/user/${comment.author}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
                        }
                        1 -> {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Comment", comment.body))
                        }
                        2 -> {
                            val shareText = "${comment.body}\n\n- u/${comment.author}"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share comment"))
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun showFlairDialog(context: Context, emojiUrl: String?, label: String) {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val pad = (context.resources.displayMetrics.density * 16f).roundToInt()
                setPadding(pad, pad, pad, pad)
            }
            val image = ImageView(context).apply {
                val size = (context.resources.displayMetrics.density * 24f).roundToInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (context.resources.displayMetrics.density * 8f).roundToInt()
                }
                isVisible = !emojiUrl.isNullOrBlank()
            }
            if (!emojiUrl.isNullOrBlank()) {
                image.load(emojiUrl) { crossfade(true) }
            }
            val text = TextView(context).apply {
                this.text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            container.addView(image)
            container.addView(text)
            android.app.AlertDialog.Builder(context)
                .setTitle("User flair")
                .setView(container)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private class HeaderViewHolder(
        private val view: View,
        private val lifecycleOwner: LifecycleOwner,
        private val getColors: () -> PostDetailColors?,
        private val onToggleBody: () -> Unit,
        private val onImageClick: (String) -> Unit,
        private val onGalleryPreview: (List<String>, Int) -> Unit,
        private val onLinkClick: (String) -> Unit,
        private val onYouTubeSelected: (String) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val root: View = view.findViewById(R.id.post_root)
        private val title: TextView = view.findViewById(R.id.post_title)
        private val domainRow: View = view.findViewById(R.id.post_domain_row)
        private val domain: TextView = view.findViewById(R.id.post_domain)
        private val badgeNsfw: TextView = view.findViewById(R.id.badge_nsfw)
        private val badgePinned: TextView = view.findViewById(R.id.badge_pinned)
        private val body: TextView = view.findViewById(R.id.post_body)
        private val tablesSection: LinearLayout = view.findViewById(R.id.post_tables_section)
        private val tablesHeader: TextView = view.findViewById(R.id.post_tables_header)
        private val tablesList: LinearLayout = view.findViewById(R.id.post_tables_list)

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
        private var shouldResumeOnResume: Boolean = true

        private val gallerySnapHelper = PagerSnapHelper()
        private var galleryUrls: List<String> = emptyList()
        private val galleryAdapter = GalleryImageAdapter { index ->
            onGalleryPreview(galleryUrls, index)
        }
        private var galleryScrollListener: RecyclerView.OnScrollListener? = null

        private var videoLifecycleObserver: LifecycleEventObserver? = null

        init {
            body.movementMethod = null
            body.highlightColor = Color.TRANSPARENT
            body.setOnTouchListener(DetailLinkTouchListener)

            videoSeekBar.max = 1000
            galleryRecycler.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = galleryAdapter
                gallerySnapHelper.attachToRecyclerView(this)
                isNestedScrollingEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }

            disallowParentInterceptOnTouch(videoSeekBar)
            disallowParentInterceptOnTouch(galleryRecycler)

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

        fun bind(row: PostDetailRow.Header) {
            val colors = getColors()
            val post = row.post
            boundPostId = post.id

            root.setOnClickListener(null)

            val plainTitle = parseHtmlText(post.title)
            title.text = plainTitle

            val titleColor = colors?.title ?: Color.WHITE
            title.setTextColor(titleColor)

            val backgroundColor = colors?.postBackground ?: Color.BLACK
            root.setBackgroundColor(backgroundColor)
            root.alpha = 1f

            val displayDomain = post.domain.removePrefix("www.")
            val showDomain = post.media !is RedditPostMedia.Link && displayDomain.isNotBlank()
            domainRow.isVisible = showDomain || post.isNsfw || post.isStickied
            domain.isVisible = showDomain
            domain.text = displayDomain
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

            bottomLabel.text = "u/${post.author}"
            bottomLabel.setTextColor(colors?.subreddit ?: Color.CYAN)
            bottomLabel.setOnClickListener(null)
            bottomLabel.isClickable = false

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

            bindBody(post, colors, titleColor, plainTitle, isExpanded = row.isBodyExpanded)
            bindMedia(post, colors, displayDomain)
        }

        fun recycle() {
            resetMedia()
            destroyWebView()
        }

        private fun bindBody(
            post: RedditPost,
            colors: PostDetailColors?,
            titleColor: Int,
            plainTitle: String,
            isExpanded: Boolean
        ) {
            val parsedTables = parseTablesFromHtml(post.selfTextHtml)
            val sanitizedHtml = if (parsedTables.isNotEmpty()) stripTablesFromHtml(post.selfTextHtml) else post.selfTextHtml
            val bodyText = sanitizedHtml?.let { parseHtmlText(it).trim() } ?: post.selfText
            val hasBodyText = bodyText.isNotBlank()

            body.isVisible = hasBodyText
            if (hasBodyText) {
                if (isExpanded) {
                    body.setTextColor(withAlpha(titleColor, 0.9f))
                    body.text = buildLinkSpannable(
                        plainText = bodyText,
                        htmlText = sanitizedHtml,
                        linkColor = colors?.subreddit ?: Color.CYAN,
                        onLinkClick = onLinkClick,
                        onImageClick = onImageClick,
                        density = view.resources.displayMetrics.density
                    )
                } else {
                    body.setTextColor(colors?.metaInfo ?: Color.LTGRAY)
                    body.text = "Post text hidden - tap to show"
                }
                body.scrollTo(0, 0)
                body.setOnClickListener { onToggleBody() }
            } else {
                body.text = null
                body.setOnClickListener(null)
            }

            bindTables(parsedTables, plainTitle, colors, hasBodyText)
        }

        private fun bindTables(
            tables: List<RedditHtmlTable>,
            plainTitle: String,
            colors: PostDetailColors?,
            hasBodyText: Boolean
        ) {
            tablesList.removeAllViews()
            tablesSection.isVisible = tables.isNotEmpty()
            if (tables.isEmpty()) return

            val headerText = if (tables.size == 1) "Contains a table" else "Contains tables"
            tablesHeader.text = headerText
            tablesHeader.setTextColor(colors?.metaInfo ?: Color.LTGRAY)

            val horizontalPadding = view.resources.getDimensionPixelSize(R.dimen.spacing_lg)
            val verticalPaddingRes = if (hasBodyText) R.dimen.spacing_xs else R.dimen.spacing_sm
            val verticalPadding = view.resources.getDimensionPixelSize(verticalPaddingRes)
            tablesSection.setPaddingRelative(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

            val inflater = LayoutInflater.from(view.context)
            val cardBackground = colors?.tableCardBackground ?: Color.DKGRAY
            val cardTitleColor = colors?.tableCardTitle ?: (colors?.title ?: Color.WHITE)
            val previewColor = colors?.metaInfo ?: Color.LTGRAY
            val chipBackground = colors?.tableChipBackground ?: (colors?.subreddit ?: Color.CYAN)
            val chipContent = colors?.tableChipContent ?: cardTitleColor
            val chipOutline = colors?.outlineVariant ?: previewColor
            val chipStrokeWidthPx = (view.resources.displayMetrics.density * 1f).roundToInt().coerceAtLeast(1)

            tables.forEachIndexed { index, table ->
                val row = inflater.inflate(R.layout.item_post_table_attachment, tablesList, false)
                val title = row.findViewById<TextView>(R.id.table_attachment_title)
                val preview = row.findViewById<TextView>(R.id.table_attachment_preview)
                val chip = row.findViewById<View>(R.id.table_attachment_chip)
                val chipIcon = row.findViewById<ImageView>(R.id.table_attachment_chip_icon)
                val chipLabel = row.findViewById<TextView>(R.id.table_attachment_chip_label)

                row.backgroundTintList = ColorStateList.valueOf(cardBackground)
                title.text = table.displayName(index)
                title.setTextColor(cardTitleColor)

                val previewText = buildTablePreview(table)
                preview.isVisible = previewText.isNotEmpty()
                preview.text = previewText
                preview.setTextColor(previewColor)

                (chip.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.let { drawable ->
                    drawable.setColor(chipBackground)
                    drawable.setStroke(chipStrokeWidthPx, chipOutline)
                } ?: run {
                    chip.backgroundTintList = ColorStateList.valueOf(chipBackground)
                }
                ImageViewCompat.setImageTintList(chipIcon, ColorStateList.valueOf(chipContent))
                chipLabel.setTextColor(chipContent)

                val click: (View) -> Unit = {
                    val intent = TableViewerActivity.createIntent(
                        context = view.context,
                        postTitle = plainTitle,
                        tables = tables,
                        startIndex = index
                    )
                    view.context.startActivity(intent)
                }
                row.setOnClickListener(click)
                chip.setOnClickListener(click)

                if (row.layoutParams is ViewGroup.MarginLayoutParams) {
                    val margin = view.resources.getDimensionPixelSize(R.dimen.spacing_sm)
                    (row.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                        if (index == tables.lastIndex) 0 else margin
                }
                tablesList.addView(row)
            }
        }

        private fun buildTablePreview(table: RedditHtmlTable): String {
            val headerPreview = table.header.filter { it.isNotBlank() }.take(4)
            if (headerPreview.isNotEmpty()) {
                return headerPreview.joinToString(" • ")
            }
            val firstRow = table.rows.firstOrNull { row -> row.any { it.isNotBlank() } } ?: return ""
            return firstRow.filter { it.isNotBlank() }.take(4).joinToString(" • ")
        }

        private fun bindMedia(post: RedditPost, colors: PostDetailColors?, displayDomain: String) {
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

        private fun bindLinkMedia(media: RedditPostMedia.Link, colors: PostDetailColors?, displayDomain: String) {
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

        private fun bindYouTubeMedia(media: RedditPostMedia.YouTube, colors: PostDetailColors?) {
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

        private fun bindStreamableMedia(media: RedditPostMedia.Streamable, colors: PostDetailColors?) {
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

        private fun bindGalleryMedia(media: RedditPostMedia.Gallery, colors: PostDetailColors?) {
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
                val inactiveSize = view.resources.getDimensionPixelSize(R.dimen.spacing_sm) -
                    view.resources.getDimensionPixelSize(R.dimen.spacing_xs) / 2
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

        private fun bindVideoMedia(media: RedditPostMedia.Video, colors: PostDetailColors?) {
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
            shouldResumeOnResume = true

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

            registerVideoLifecycleObserver(player)
            startVideoProgressUpdates()
        }

        private fun registerVideoLifecycleObserver(player: ExoPlayer) {
            videoLifecycleObserver?.let { previous ->
                lifecycleOwner.lifecycle.removeObserver(previous)
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        shouldResumeOnResume = player.playWhenReady || player.isPlaying
                        isMuted = true
                        player.volume = 0f
                        player.pause()
                    }
                    Lifecycle.Event.ON_STOP -> player.pause()
                    Lifecycle.Event.ON_RESUME -> {
                        if (shouldResumeOnResume) {
                            player.playWhenReady = true
                            player.play()
                        }
                    }
                    else -> Unit
                }
            }
            videoLifecycleObserver = observer
            lifecycleOwner.lifecycle.addObserver(observer)
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
            videoLifecycleObserver?.let { observer ->
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
            videoLifecycleObserver = null
        }

        private fun recycleGallery() {
            galleryAdapter.urls = emptyList()
            galleryDots.removeAllViews()
            galleryScrollListener?.let { galleryRecycler.removeOnScrollListener(it) }
            galleryScrollListener = null
        }

        private fun disallowParentInterceptOnTouch(target: View) {
            target.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_SORT_BAR = 2
        private const val VIEW_TYPE_REFRESHING = 3
        private const val VIEW_TYPE_EMPTY = 4
        private const val VIEW_TYPE_COMMENT = 5
        private const val VIEW_TYPE_LOAD_MORE_REPLIES = 6
        private const val VIEW_TYPE_REMOTE_REPLIES = 7
        private const val VIEW_TYPE_COLLAPSED_HISTORY = 8
        private const val VIEW_TYPE_LOAD_MORE_COMMENTS = 9
        private const val VIEW_TYPE_END_SPACER = 10

        private val streamableUrlCache = android.util.LruCache<String, String>(64)

        private val LinkRegex = Regex("https?://[^\\s]+")
        private val SubredditRegex = Regex("(?<![A-Za-z0-9_])r/[A-Za-z0-9_]+", RegexOption.IGNORE_CASE)
        private val UserRegex = Regex("(?<![A-Za-z0-9_])u/[A-Za-z0-9_-]+", RegexOption.IGNORE_CASE)
        private val TrailingUrlDelimiters = charArrayOf(')', ']', '}', '>', ',', '.', ';', ':', '\"', '\'')
        private val QuoteStripeColorInt = 0xFFFFD54F.toInt()
        private const val QuoteStripeWidthDp = 3f
        private const val QuoteStripeGapDp = 8f
        private const val QuoteItalicSpanFlags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or (255 shl Spanned.SPAN_PRIORITY_SHIFT)

        private fun applyIndent(stripe: View, content: View, depth: Int, accentColor: Int) {
            val resources = stripe.resources
            val indentStep = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            val gap = resources.getDimensionPixelSize(R.dimen.spacing_xs)
            val stripeWidth = resources.displayMetrics.density * 3f
            val stripeWidthPx = stripeWidth.roundToInt().coerceAtLeast(1)
            val indentPx = (indentStep * depth).coerceAtLeast(0)

            stripe.isVisible = depth > 0
            if (depth > 0) {
                val stripeParams = stripe.layoutParams as ViewGroup.MarginLayoutParams
                stripeParams.marginStart = indentPx
                stripe.layoutParams = stripeParams
                stripe.setBackgroundColor(accentColor)
            }

            val contentParams = content.layoutParams as? ViewGroup.MarginLayoutParams
            contentParams?.let { params ->
                params.marginStart = if (depth > 0) indentPx + stripeWidthPx + gap else 0
                content.layoutParams = params
            }
        }

        private fun resolveFlair(
            flairText: String?,
            flairRichtext: List<FlairRichText>?,
            flairEmojiLookup: Map<String, String>
        ): Pair<String?, String?> {
            val rawText = flairText?.takeIf { it.isNotBlank() } ?: return null to null

            if (!flairRichtext.isNullOrEmpty()) {
                val emojiUrl = flairRichtext.firstOrNull { it.type == "emoji" }?.url
                val displayText = flairRichtext
                    .filter { it.type == "text" }
                    .mapNotNull { it.text }
                    .joinToString("")
                    .trim()
                    .ifBlank { rawText }
                return emojiUrl to displayText
            }

            val cleaned = rawText.replace(Regex(":[^:\\s]+:"), "").trim()
            val alias = Regex(":[^\\s]+:").find(rawText)?.value?.lowercase()
            val key = rawText.lowercase()
            val altKey = cleaned.lowercase()
            val url = alias?.let { flairEmojiLookup[it] }
                ?: flairEmojiLookup[altKey]
                ?: flairEmojiLookup[key]
            val label = cleaned.ifBlank { rawText }
            return url to label
        }

        private fun buildLinkSpannable(
            plainText: String,
            htmlText: String?,
            linkColor: Int,
            onLinkClick: (String) -> Unit,
            onImageClick: (String) -> Unit,
            density: Float
        ): CharSequence {
            val initial = htmlText?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY) }.getOrNull()
            }

            val builder = SpannableStringBuilder(initial ?: plainText)
            if (builder.isEmpty()) return builder

            replaceImageSpans(builder, linkColor, onLinkClick, onImageClick)
            replaceUrlSpans(builder, linkColor, onLinkClick, onImageClick)
            linkifyRawUrls(builder, linkColor, onLinkClick, onImageClick)
            styleSubredditMentions(builder, linkColor)
            styleUserMentions(builder, linkColor)
            styleQuoteSpans(builder, QuoteStripeColorInt, density)

            return builder
        }

        private fun replaceImageSpans(
            builder: SpannableStringBuilder,
            linkColor: Int,
            onLinkClick: (String) -> Unit,
            onImageClick: (String) -> Unit
        ) {
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
                applyClickableLink(builder, start, spanEnd, sanitizeLinkValue(url), linkColor, onLinkClick, onImageClick)
            }
        }

        private fun replaceUrlSpans(
            builder: SpannableStringBuilder,
            linkColor: Int,
            onLinkClick: (String) -> Unit,
            onImageClick: (String) -> Unit
        ) {
            val urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
            urlSpans.forEach { span ->
                val start = builder.getSpanStart(span)
                val end = builder.getSpanEnd(span)
                val url = sanitizeLinkValue(span.url.orEmpty())
                builder.removeSpan(span)
                if (start < 0 || end <= start || url.isBlank()) return@forEach
                applyClickableLink(builder, start, end, url, linkColor, onLinkClick, onImageClick)
            }
        }

        private fun linkifyRawUrls(
            builder: SpannableStringBuilder,
            linkColor: Int,
            onLinkClick: (String) -> Unit,
            onImageClick: (String) -> Unit
        ) {
            val content = builder.toString()
            LinkRegex.findAll(content).forEach { match ->
                val sanitized = sanitizeLinkValue(match.value)
                if (sanitized.isBlank()) return@forEach
                val start = match.range.first
                val end = (start + sanitized.length).coerceAtMost(builder.length)
                if (start < 0 || end <= start) return@forEach
                if (builder.getSpans(start, end, ClickableSpan::class.java).isNotEmpty()) return@forEach
                applyClickableLink(builder, start, end, sanitized, linkColor, onLinkClick, onImageClick)
            }
        }

        private fun styleSubredditMentions(builder: SpannableStringBuilder, linkColor: Int) {
            val content = builder.toString()
            SubredditRegex.findAll(content).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                if (start < 0 || end <= start || end > builder.length) return@forEach
                if (builder.getSpans(start, end, ClickableSpan::class.java).isNotEmpty()) return@forEach
                builder.setSpan(android.text.style.ForegroundColorSpan(linkColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                builder.setSpan(android.text.style.ForegroundColorSpan(linkColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private fun applyClickableLink(
            builder: SpannableStringBuilder,
            start: Int,
            end: Int,
            url: String,
            linkColor: Int,
            onLinkClick: (String) -> Unit,
            onImageClick: (String) -> Unit
        ) {
            val resolved = sanitizeLinkValue(url)
            if (resolved.isBlank()) return
            builder.setSpan(
                DetailClickableSpan(
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

        private fun styleQuoteSpans(builder: SpannableStringBuilder, stripeColor: Int, density: Float) {
            val quoteSpans = builder.getSpans(0, builder.length, QuoteSpan::class.java)
            if (quoteSpans.isEmpty()) return

            val stripeWidthPx = (QuoteStripeWidthDp * density).roundToInt().coerceAtLeast(1)
            val gapWidthPx = (QuoteStripeGapDp * density).roundToInt().coerceAtLeast(0)

            quoteSpans.forEach { span ->
                val start = builder.getSpanStart(span)
                val end = builder.getSpanEnd(span)
                builder.removeSpan(span)
                if (start < 0 || end <= start) return@forEach

                builder.setSpan(
                    QuoteStripeSpan(color = stripeColor, stripeWidthPx = stripeWidthPx, gapWidthPx = gapWidthPx),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(MergeItalicSpan(), start, end, QuoteItalicSpanFlags)
            }
        }

        private class QuoteStripeSpan(
            private val color: Int,
            private val stripeWidthPx: Int,
            private val gapWidthPx: Int
        ) : android.text.style.LeadingMarginSpan {
            override fun getLeadingMargin(first: Boolean): Int = stripeWidthPx + gapWidthPx

            override fun drawLeadingMargin(
                c: Canvas,
                p: Paint,
                x: Int,
                dir: Int,
                top: Int,
                baseline: Int,
                bottom: Int,
                text: CharSequence,
                start: Int,
                end: Int,
                first: Boolean,
                layout: Layout
            ) {
                val oldStyle = p.style
                val oldColor = p.color
                p.style = Paint.Style.FILL
                p.color = color

                val left = x.toFloat()
                val right = (x + dir * stripeWidthPx).toFloat()
                c.drawRect(minOf(left, right), top.toFloat(), maxOf(left, right), bottom.toFloat(), p)

                p.style = oldStyle
                p.color = oldColor
            }
        }

        private class MergeItalicSpan : MetricAffectingSpan() {
            override fun updateDrawState(tp: TextPaint) = apply(tp)
            override fun updateMeasureState(tp: TextPaint) = apply(tp)

            private fun apply(tp: TextPaint) {
                val current = tp.typeface
                val style = current?.style ?: 0
                tp.typeface = Typeface.create(current, style or Typeface.ITALIC)
            }
        }

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

        private object DetailLinkTouchListener : View.OnTouchListener {
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val widget = view as? TextView ?: return false
                val buffer = widget.text as? Spannable ?: return false
                val layout = widget.layout ?: return false

                val action = event.actionMasked
                if (action == MotionEvent.ACTION_CANCEL) {
                    Selection.removeSelection(buffer)
                    widget.scrollTo(0, 0)
                    return false
                }
                if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_DOWN) {
                    return false
                }

                if (widget.scrollX != 0 || widget.scrollY != 0) {
                    widget.scrollTo(0, 0)
                }

                val x = (event.x - widget.totalPaddingLeft).toInt()
                val y = (event.y - widget.totalPaddingTop).toInt()
                val line = layout.getLineForVertical(y)
                val offset = layout.getOffsetForHorizontal(line, x.toFloat())

                val link = buffer.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
                if (link == null) {
                    Selection.removeSelection(buffer)
                    return false
                }

                if (action == MotionEvent.ACTION_UP) {
                    link.onClick(widget)
                    widget.scrollTo(0, 0)
                    Selection.removeSelection(buffer)
                }
                return true
            }
        }

        private class DetailClickableSpan(
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
                return null
            }

            synchronized(streamableUrlCache) {
                streamableUrlCache.put(shortcode, videoUrl)
            }
            return videoUrl
        }

        private val DIFF = object : DiffUtil.ItemCallback<PostDetailRow>() {
            override fun areItemsTheSame(oldItem: PostDetailRow, newItem: PostDetailRow): Boolean {
                return oldItem.key == newItem.key
            }

            override fun areContentsTheSame(oldItem: PostDetailRow, newItem: PostDetailRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}
