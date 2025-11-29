package com.munch.reddit.feature.feed

import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.munch.reddit.R
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.domain.model.RedditPostMedia
import com.munch.reddit.feature.shared.RedditHtmlTable
import com.munch.reddit.feature.shared.formatCount
import com.munch.reddit.feature.shared.formatRelativeTime
import com.munch.reddit.feature.shared.openLinkInCustomTab
import com.munch.reddit.feature.shared.parseHtmlText
import com.munch.reddit.feature.shared.parseTablesFromHtml
import com.munch.reddit.feature.shared.stripTablesFromHtml
import com.munch.reddit.theme.FeedColorPalette

sealed class FeedRow {
    data class PostRow(val post: RedditPost, val isRead: Boolean) : FeedRow()
    data object LoadingRow : FeedRow()
}

class FeedAdapter(
    private val palette: FeedColorPalette,
    private val onPostClick: (RedditPost) -> Unit,
    private val onImageClick: (String) -> Unit,
    private val onGalleryClick: (List<String>, Int) -> Unit,
    private val onYoutubeClick: (String) -> Unit,
    private val onTablesClick: (RedditPost, List<RedditHtmlTable>, Int) -> Unit,
    private val onSubredditClick: (String) -> Unit
) : ListAdapter<FeedRow, RecyclerView.ViewHolder>(DiffCallback) {

    var selectedSubreddit: String = "all"

    private val postBackground = palette.postBackground.toArgb()
    private val spacerBackground = palette.spacerBackground.toArgb()
    private val titleColor = palette.title.toArgb()
    private val subredditColor = palette.subreddit.toArgb()
    private val metaColor = palette.metaInfo.toArgb()
    private val pinnedColor = palette.pinnedLabel.toArgb()
    private val borderColor = palette.postBorder?.toArgb() ?: 0

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is FeedRow.PostRow -> item.post.id.hashCode().toLong()
            FeedRow.LoadingRow -> Long.MAX_VALUE
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FeedRow.PostRow -> VIEW_TYPE_POST
            FeedRow.LoadingRow -> VIEW_TYPE_LOADING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_LOADING -> LoadingViewHolder(
                inflater.inflate(R.layout.item_loading_footer, parent, false)
            )
            else -> PostViewHolder(
                inflater.inflate(R.layout.item_reddit_post, parent, false),
                onPostClick,
                onImageClick,
                onGalleryClick,
                onYoutubeClick,
                onTablesClick,
                onSubredditClick,
                ::isGlobalFeed,
                titleColor,
                subredditColor,
                metaColor,
                postBackground,
                spacerBackground,
                pinnedColor,
                borderColor
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PostViewHolder -> {
                val row = getItem(position) as FeedRow.PostRow
                holder.bind(row)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is PostViewHolder) {
            holder.releasePlayer()
        }
    }

    private fun isGlobalFeed(): Boolean = selectedSubreddit.equals("all", ignoreCase = true)

    companion object {
        private const val VIEW_TYPE_POST = 1
        private const val VIEW_TYPE_LOADING = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<FeedRow>() {
            override fun areItemsTheSame(oldItem: FeedRow, newItem: FeedRow): Boolean {
                return when {
                    oldItem is FeedRow.PostRow && newItem is FeedRow.PostRow -> oldItem.post.id == newItem.post.id
                    oldItem is FeedRow.LoadingRow && newItem is FeedRow.LoadingRow -> true
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: FeedRow, newItem: FeedRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}

private class LoadingViewHolder(view: View) : RecyclerView.ViewHolder(view)

private class PostViewHolder(
    itemView: View,
    private val onPostClick: (RedditPost) -> Unit,
    private val onImageClick: (String) -> Unit,
    private val onGalleryClick: (List<String>, Int) -> Unit,
    private val onYoutubeClick: (String) -> Unit,
    private val onTablesClick: (RedditPost, List<RedditHtmlTable>, Int) -> Unit,
    private val onSubredditClick: (String) -> Unit,
    private val isGlobalFeed: () -> Boolean,
    private val titleColor: Int,
    private val subredditColor: Int,
    private val metaColor: Int,
    private val postBackground: Int,
    private val spacerBackground: Int,
    private val pinnedColor: Int,
    private val borderColor: Int
) : RecyclerView.ViewHolder(itemView) {

    private val card = itemView as MaterialCardView
    private val title: TextView = itemView.findViewById(R.id.postTitle)
    private val domain: TextView = itemView.findViewById(R.id.domainLabel)
    private val nsfwBadge: TextView = itemView.findViewById(R.id.nsfwBadge)
    private val pinnedBadge: TextView = itemView.findViewById(R.id.pinnedBadge)
    private val body: TextView = itemView.findViewById(R.id.postBody)
    private val viewTablesButton: MaterialButton = itemView.findViewById(R.id.viewTablesButton)
    private val mediaContainer: View = itemView.findViewById(R.id.mediaContainer)
    private val mediaImage: ImageView = itemView.findViewById(R.id.mediaImage)
    private val videoPlayer: androidx.media3.ui.PlayerView = itemView.findViewById(R.id.videoPlayerView)
    private val galleryBadge: TextView = itemView.findViewById(R.id.galleryBadge)
    private val playOverlay: ImageView = itemView.findViewById(R.id.playOverlay)
    private val subredditLabel: TextView = itemView.findViewById(R.id.subredditLabel)
    private val timeLabel: TextView = itemView.findViewById(R.id.timeLabel)
    private val commentsLabel: TextView = itemView.findViewById(R.id.commentsLabel)
    private val scoreLabel: TextView = itemView.findViewById(R.id.scoreLabel)

    private var player: ExoPlayer? = null

    fun bind(row: FeedRow.PostRow) {
        val post = row.post

        card.setCardBackgroundColor(if (row.isRead) spacerBackground else postBackground)
        if (borderColor != 0) {
            card.strokeColor = borderColor
        }
        card.alpha = if (row.isRead) 0.55f else 1f
        title.setTextColor(titleColor)
        body.setTextColor(titleColor)

        title.text = parseHtmlText(post.title)
        val displayDomain = post.domain.removePrefix("www.")
        domain.text = displayDomain.ifBlank { if (isGlobalFeed()) post.subreddit else "u/${post.author}" }
        domain.setTextColor(metaColor)

        nsfwBadge.isVisible = post.isNsfw
        pinnedBadge.isVisible = post.isStickied
        pinnedBadge.setBackgroundColor(pinnedColor)

        bindBody(post)
        bindMedia(post)

        val subredditLabelText = if (isGlobalFeed()) post.subreddit.lowercase().let { if (it.startsWith("r/")) it else "r/$it" } else "u/${post.author}"
        subredditLabel.text = subredditLabelText
        subredditLabel.setTextColor(subredditColor)
        subredditLabel.isClickable = isGlobalFeed()
        subredditLabel.setOnClickListener {
            if (isGlobalFeed()) {
                val normalized = post.subreddit.removePrefix("r/").removePrefix("R/")
                onSubredditClick(normalized)
            }
        }

        timeLabel.text = formatRelativeTime(post.createdUtc)
        commentsLabel.text = "${formatCount(post.commentCount)} comments"
        scoreLabel.text = "${formatCount(post.score)} votes"
        timeLabel.setTextColor(metaColor)
        commentsLabel.setTextColor(metaColor)
        scoreLabel.setTextColor(metaColor)

        itemView.setOnClickListener { onPostClick(post) }
    }

    private fun bindBody(post: RedditPost) {
        val parsedTables = parseTablesFromHtml(post.selfTextHtml)
        val sanitizedHtml = if (parsedTables.isNotEmpty()) stripTablesFromHtml(post.selfTextHtml) else post.selfTextHtml
        val bodyText = (sanitizedHtml?.let { parseHtmlText(it) } ?: post.selfText).trim()
        body.isVisible = post.media is RedditPostMedia.None && bodyText.isNotBlank()
        body.text = bodyText

        if (parsedTables.isNotEmpty()) {
            viewTablesButton.isVisible = true
            viewTablesButton.text = "View tables (${parsedTables.size})"
            viewTablesButton.setOnClickListener {
                onTablesClick(post, parsedTables, 0)
            }
        } else {
            viewTablesButton.isVisible = false
        }
    }

    private fun bindMedia(post: RedditPost) {
        releasePlayer()
        mediaContainer.isVisible = false
        mediaImage.isVisible = false
        videoPlayer.isVisible = false
        galleryBadge.isVisible = false
        playOverlay.isVisible = false

        when (val media = post.media) {
            RedditPostMedia.None -> Unit
            is RedditPostMedia.Image -> {
                mediaContainer.isVisible = true
                mediaImage.isVisible = true
                mediaImage.load(media.url) {
                    placeholder(ColorDrawable(spacerBackground))
                    crossfade(true)
                }
                mediaImage.setOnClickListener { onImageClick(media.url) }
            }
            is RedditPostMedia.Gallery -> {
                val images = media.images.map { it.url }
                val preview = images.firstOrNull() ?: post.thumbnailUrl
                if (!preview.isNullOrBlank()) {
                    mediaContainer.isVisible = true
                    mediaImage.isVisible = true
                    galleryBadge.isVisible = true
                    galleryBadge.text = "Gallery (${images.size})"
                    mediaImage.load(preview) {
                        placeholder(ColorDrawable(spacerBackground))
                        crossfade(true)
                    }
                    mediaImage.setOnClickListener { onGalleryClick(images, 0) }
                }
            }
            is RedditPostMedia.Video -> {
                mediaContainer.isVisible = true
                videoPlayer.isVisible = true
                playOverlay.isVisible = false
                val context = itemView.context
                player = ExoPlayer.Builder(context).build().apply {
                    val builder = MediaItem.Builder().setUri(media.url)
                    if (media.url.endsWith(".m3u8", ignoreCase = true)) {
                        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (media.url.endsWith(".mpd", ignoreCase = true)) {
                        builder.setMimeType(MimeTypes.APPLICATION_MPD)
                    }
                    setMediaItem(builder.build())
                    repeatMode = Player.REPEAT_MODE_ALL
                    playWhenReady = true
                    prepare()
                }
                videoPlayer.player = player
            }
            is RedditPostMedia.Link -> {
                val preview = media.previewImageUrl ?: post.thumbnailUrl
                if (!preview.isNullOrBlank()) {
                    mediaContainer.isVisible = true
                    mediaImage.isVisible = true
                    mediaImage.load(preview) {
                        placeholder(ColorDrawable(spacerBackground))
                        crossfade(true)
                    }
                    mediaImage.setOnClickListener { openLinkInCustomTab(itemView.context, media.url) }
                }
            }
            is RedditPostMedia.YouTube -> {
                val preview = media.thumbnailUrl ?: post.thumbnailUrl
                if (!preview.isNullOrBlank()) {
                    mediaContainer.isVisible = true
                    mediaImage.isVisible = true
                    playOverlay.isVisible = true
                    mediaImage.load(preview) {
                        placeholder(ColorDrawable(spacerBackground))
                        crossfade(true)
                    }
                    mediaImage.setOnClickListener { onYoutubeClick(media.videoId) }
                    playOverlay.setOnClickListener { onYoutubeClick(media.videoId) }
                }
            }
            is RedditPostMedia.RedGifs -> bindPreviewMedia(media.thumbnailUrl ?: media.embedUrl) {
                openLinkInCustomTab(itemView.context, media.embedUrl)
            }
            is RedditPostMedia.Streamable -> bindPreviewMedia(media.thumbnailUrl ?: media.url) {
                openLinkInCustomTab(itemView.context, media.url)
            }
            is RedditPostMedia.StreamFF -> bindPreviewMedia(media.thumbnailUrl ?: media.url) {
                openLinkInCustomTab(itemView.context, media.url)
            }
            is RedditPostMedia.StreamIn -> bindPreviewMedia(media.thumbnailUrl ?: media.url) {
                openLinkInCustomTab(itemView.context, media.url)
            }
        }
    }

    private fun bindPreviewMedia(url: String?, onClick: () -> Unit) {
        if (url.isNullOrBlank()) return
        mediaContainer.isVisible = true
        mediaImage.isVisible = true
        playOverlay.isVisible = true
        mediaImage.load(url) {
            placeholder(ColorDrawable(spacerBackground))
            crossfade(true)
        }
        mediaImage.setOnClickListener { onClick() }
        playOverlay.setOnClickListener { onClick() }
    }

    fun releasePlayer() {
        player?.release()
        player = null
        videoPlayer.player = null
    }
}
