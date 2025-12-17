package com.munch.reddit.feature.feed.recycler

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.munch.reddit.R

class GalleryImageAdapter(
    private val onImageClick: (index: Int) -> Unit
) : RecyclerView.Adapter<GalleryImageAdapter.GalleryImageViewHolder>() {

    var urls: List<String> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return GalleryImageViewHolder(view, onImageClick)
    }

    override fun onBindViewHolder(holder: GalleryImageViewHolder, position: Int) {
        holder.bind(urls[position])
    }

    override fun getItemCount(): Int = urls.size

    class GalleryImageViewHolder(
        itemView: View,
        private val onImageClick: (index: Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.gallery_image)

        init {
            imageView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onImageClick(position)
                }
            }
        }

        fun bind(url: String) {
            imageView.load(url) {
                crossfade(true)
            }
        }
    }
}

