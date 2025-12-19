package com.munch.reddit.feature.shared.sidesheet

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.munch.reddit.R

data class SubredditSideSheetColors(
    val title: Int,
    val subreddit: Int
)

class SubredditSideSheetAdapter :
    ListAdapter<SubredditSideSheetRow, RecyclerView.ViewHolder>(DIFF) {

    var colors: SubredditSideSheetColors? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var onSubredditClick: ((String) -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null
    var onEditFavoritesClick: (() -> Unit)? = null

    private var regularTypeface: Typeface? = null
    private var boldTypeface: Typeface? = null

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SubredditSideSheetRow.SectionHeader -> VIEW_TYPE_HEADER
            else -> VIEW_TYPE_ROW
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        ensureTypefaces(parent)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                HeaderViewHolder(inflater.inflate(R.layout.item_subreddit_side_sheet_header, parent, false))
            }
            VIEW_TYPE_ROW -> {
                RowViewHolder(inflater.inflate(R.layout.item_subreddit_side_sheet_row, parent, false))
            }
            else -> error("Unknown viewType=$viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = getItem(position)
        val currentColors = colors
        when (holder) {
            is HeaderViewHolder -> holder.bind(row as SubredditSideSheetRow.SectionHeader, currentColors)
            is RowViewHolder -> holder.bind(
                row = row,
                colors = currentColors,
                regularTypeface = regularTypeface,
                boldTypeface = boldTypeface,
                onSubredditClick = onSubredditClick,
                onSettingsClick = onSettingsClick,
                onEditFavoritesClick = onEditFavoritesClick
            )
        }
    }

    private fun ensureTypefaces(parent: ViewGroup) {
        if (regularTypeface != null && boldTypeface != null) return
        val context = parent.context
        regularTypeface = regularTypeface ?: ResourcesCompat.getFont(context, R.font.sfpro)
        boldTypeface = boldTypeface ?: ResourcesCompat.getFont(context, R.font.sfprobold)
    }

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.side_sheet_header_text)
        private val extraTopPx: Int = itemView.resources.getDimensionPixelSize(R.dimen.spacing_sm)

        fun bind(row: SubredditSideSheetRow.SectionHeader, colors: SubredditSideSheetColors?) {
            text.text = row.title
            text.setTextColor(withAlpha(colors?.title ?: Color.WHITE, 0.7f))

            val lp = itemView.layoutParams as? RecyclerView.LayoutParams
                ?: RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            lp.topMargin = if (row.extraTopSpacing) extraTopPx else 0
            itemView.layoutParams = lp
        }
    }

    private class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView.findViewById(R.id.side_sheet_row_root)
        private val avatar: FrameLayout = itemView.findViewById(R.id.side_sheet_avatar)
        private val avatarImage: ImageView = itemView.findViewById(R.id.side_sheet_avatar_image)
        private val avatarIcon: ImageView = itemView.findViewById(R.id.side_sheet_avatar_icon)
        private val avatarText: TextView = itemView.findViewById(R.id.side_sheet_avatar_text)
        private val label: TextView = itemView.findViewById(R.id.side_sheet_label)

        fun bind(
            row: SubredditSideSheetRow,
            colors: SubredditSideSheetColors?,
            regularTypeface: Typeface?,
            boldTypeface: Typeface?,
            onSubredditClick: ((String) -> Unit)?,
            onSettingsClick: (() -> Unit)?,
            onEditFavoritesClick: (() -> Unit)?
        ) {
            val titleColor = colors?.title ?: Color.WHITE
            val subredditColor = colors?.subreddit ?: titleColor

            when (row) {
                is SubredditSideSheetRow.Subreddit -> {
                    root.setOnClickListener { onSubredditClick?.invoke(row.subreddit) }
                    label.text = row.display
                    label.setTextColor(if (row.selected) subredditColor else titleColor)
                    label.typeface = if (row.selected) boldTypeface ?: label.typeface else regularTypeface ?: label.typeface
                    bindAvatarForSubreddit(row, subredditColor)
                }
                SubredditSideSheetRow.Settings -> {
                    root.setOnClickListener { onSettingsClick?.invoke() }
                    label.text = "settings"
                    label.setTextColor(titleColor)
                    label.typeface = regularTypeface ?: label.typeface
                    bindAvatarIcon(
                        drawableRes = R.drawable.ic_settings,
                        tint = Color.WHITE
                    )
                }
                SubredditSideSheetRow.EditFavorites -> {
                    root.setOnClickListener { onEditFavoritesClick?.invoke() }
                    label.text = "Edit Favorites"
                    label.setTextColor(titleColor)
                    label.typeface = regularTypeface ?: label.typeface
                    bindAvatarIcon(
                        drawableRes = R.drawable.ic_edit,
                        tint = subredditColor
                    )
                }
                is SubredditSideSheetRow.SectionHeader -> Unit
            }
        }

        private fun bindAvatarForSubreddit(row: SubredditSideSheetRow.Subreddit, subredditColor: Int) {
            val url = row.iconUrl
            when {
                row.isAll -> {
                    bindAvatarIcon(drawableRes = R.drawable.ic_stack, tint = Color.WHITE)
                }
                !url.isNullOrBlank() -> {
                    avatar.background = circleBackground(Color.TRANSPARENT)
                    avatar.clipToOutline = true

                    avatarImage.isVisible = true
                    avatarIcon.isVisible = false
                    avatarText.isVisible = false

                    ImageViewCompat.setImageTintList(avatarImage, null)
                    avatarImage.load(url) { crossfade(true) }
                }
                else -> {
                    val background = row.fallbackColor ?: fallbackColorForSubreddit(row.subreddit)
                    avatar.background = circleBackground(background)
                    avatar.clipToOutline = true

                    avatarImage.isVisible = false
                    avatarIcon.isVisible = false
                    avatarText.isVisible = true

                    avatarImage.setImageDrawable(null)
                    avatarIcon.setImageDrawable(null)
                    avatarText.setTextColor(Color.WHITE)
                }
            }
        }

        private fun bindAvatarIcon(drawableRes: Int, tint: Int) {
            avatar.background = circleBackground(Color.TRANSPARENT)
            avatar.clipToOutline = true

            avatarImage.isVisible = false
            avatarText.isVisible = false
            avatarIcon.isVisible = true

            avatarImage.setImageDrawable(null)
            avatarIcon.setImageResource(drawableRes)
            ImageViewCompat.setImageTintList(avatarIcon, ColorStateList.valueOf(tint))
        }

        private fun circleBackground(color: Int): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_ROW = 2

        private val DIFF = object : DiffUtil.ItemCallback<SubredditSideSheetRow>() {
            override fun areItemsTheSame(
                oldItem: SubredditSideSheetRow,
                newItem: SubredditSideSheetRow
            ): Boolean {
                return oldItem.stableId == newItem.stableId
            }

            override fun areContentsTheSame(
                oldItem: SubredditSideSheetRow,
                newItem: SubredditSideSheetRow
            ): Boolean {
                return oldItem == newItem
            }
        }

        private fun withAlpha(color: Int, alpha: Float): Int {
            val a = (alpha * 255f).toInt().coerceIn(0, 255)
            return (color and 0x00FFFFFF) or (a shl 24)
        }
    }
}

