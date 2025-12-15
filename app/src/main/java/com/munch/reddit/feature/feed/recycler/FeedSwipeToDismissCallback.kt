package com.munch.reddit.feature.feed.recycler

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.munch.reddit.R
import kotlin.math.abs
import kotlin.math.min

class FeedSwipeToDismissCallback(
    private val canSwipe: (adapterPosition: Int) -> Boolean,
    private val onDismiss: (adapterPosition: Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return 0
        if (!canSwipe(position)) return 0
        return makeMovementFlags(0, ItemTouchHelper.RIGHT)
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.35f

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        onDismiss(position)
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
            super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        val itemView = viewHolder.itemView
        val clampedDx = dX.coerceAtLeast(0f)
        if (clampedDx > 0f) {
            val top = itemView.top.toFloat()
            val bottom = itemView.bottom.toFloat()
            canvas.drawRect(itemView.left.toFloat(), top, itemView.left + clampedDx, bottom, backgroundPaint)

            val icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_read_posts)
            if (icon != null) {
                drawDismissIcon(canvas, recyclerView.resources, itemView, icon, clampedDx)
            }
        }

        super.onChildDraw(canvas, recyclerView, viewHolder, clampedDx, dY, actionState, isCurrentlyActive)
    }

    private fun drawDismissIcon(
        canvas: Canvas,
        resources: Resources,
        itemView: android.view.View,
        icon: Drawable,
        dX: Float
    ) {
        val iconSize = resources.getDimensionPixelSize(R.dimen.feed_dismiss_icon_size).coerceAtLeast(1)
        val margin = resources.getDimensionPixelSize(R.dimen.spacing_lg)
        val top = itemView.top + (itemView.height - iconSize) / 2
        val left = itemView.left + margin
        val bounds = Rect(left, top, left + iconSize, top + iconSize)
        icon.bounds = bounds

        val progress = min(1f, abs(dX) / itemView.width.toFloat())
        icon.alpha = (progress / 0.12f).coerceIn(0f, 1f).times(255).toInt()
        icon.setTint(Color.WHITE)
        icon.draw(canvas)
    }
}
