package com.munch.reddit.feature.feed

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.munch.reddit.R

sealed class SubredditRow {
    data class Header(val title: String) : SubredditRow()
    data class Item(val name: String, val isSelected: Boolean, val iconUrl: String?) : SubredditRow()
}

class SubredditAdapter(
    private val onSelect: (String) -> Unit
) : ListAdapter<SubredditRow, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SubredditRow.Header -> VIEW_TYPE_HEADER
            is SubredditRow.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_subreddit_header, parent, false)
            )
            else -> SubredditViewHolder(
                inflater.inflate(R.layout.item_subreddit, parent, false),
                onSelect
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(getItem(position) as SubredditRow.Header)
            is SubredditViewHolder -> holder.bind(getItem(position) as SubredditRow.Item)
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<SubredditRow>() {
            override fun areItemsTheSame(oldItem: SubredditRow, newItem: SubredditRow): Boolean {
                return when {
                    oldItem is SubredditRow.Header && newItem is SubredditRow.Header -> oldItem.title == newItem.title
                    oldItem is SubredditRow.Item && newItem is SubredditRow.Item -> oldItem.name.equals(newItem.name, true)
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: SubredditRow, newItem: SubredditRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}

private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val title: TextView = itemView.findViewById(R.id.subredditHeader)
    fun bind(row: SubredditRow.Header) {
        title.text = row.title
    }
}

private class SubredditViewHolder(
    itemView: View,
    private val onSelect: (String) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val icon: ImageView = itemView.findViewById(R.id.subredditIcon)
    private val title: TextView = itemView.findViewById(R.id.subredditName)

    fun bind(row: SubredditRow.Item) {
        title.text = row.name
        title.setTypeface(null, if (row.isSelected) Typeface.BOLD else Typeface.NORMAL)
        if (!row.iconUrl.isNullOrBlank()) {
            icon.load(row.iconUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_globe)
            }
        } else {
            icon.setImageResource(R.drawable.ic_globe)
        }
        itemView.setOnClickListener { onSelect(row.name) }
    }
}
