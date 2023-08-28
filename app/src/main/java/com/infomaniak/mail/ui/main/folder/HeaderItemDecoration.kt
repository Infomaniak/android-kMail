/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.mail.ui.main.folder

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.forEachIndexed
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.ui.main.folder.HeaderItemDecoration.Intersection.*

class HeaderItemDecoration(
    parent: RecyclerView,
    private val shouldFadeOutHeader: Boolean = false,
    private val isHeader: (itemPosition: Int) -> Boolean,
) : RecyclerView.ItemDecoration() {

    private var currentHeader: Pair<Int, RecyclerView.ViewHolder>? = null

    private inline fun View.doOnEachNextLayout(crossinline action: (view: View) -> Unit) {
        addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            action(view)
        }
    }

    init {
        parent.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                // Clear saved header as it can be outdated now
                currentHeader = null
            }
        })

        parent.doOnEachNextLayout {
            // Clear saved layout as it may need layout update
            currentHeader = null
        }

        // Handle click on sticky header
        parent.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(recyclerView: RecyclerView, motionEvent: MotionEvent): Boolean {
                return if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    motionEvent.y <= (currentHeader?.second?.itemView?.bottom ?: 0)
                } else {
                    false
                }
            }
        })
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        val topChildAdapterPosition = getPositionInAdapterOfFirstChild(parent) ?: return
        val headerView = getHeaderViewForItem(topChildAdapterPosition, parent) ?: return

        val headerContactPoint = headerView.bottom + parent.paddingTop
        val childInContact = getChildInContact(parent, headerContactPoint) ?: return

        if (isHeader(parent.getChildAdapterPosition(childInContact))) {
            moveHeader(c, headerView, childInContact, parent.paddingTop)
            return
        }

        drawHeader(c, headerView, parent.paddingTop)
    }

    private fun getPositionInAdapterOfFirstChild(parent: RecyclerView): Int? {
        val topMostChild = parent.getChildAt(0)
        val adapterIndex = parent.getChildAdapterPosition(topMostChild)

        return when (topMostChild.intersects(parent, parent.paddingTop)) {
            INSET_TOP -> adapterIndex - 1
            CENTER -> adapterIndex
            INSET_BOTTOM -> adapterIndex + 1
            null -> null
        }
    }

    private fun View?.intersects(parent: RecyclerView, height: Int): Intersection? {
        if (this == null) return null
        val boundsWithInsets = Rect()
        parent.getDecoratedBoundsWithMargins(this, boundsWithInsets)
        return when {
            top > height && boundsWithInsets.top <= height -> INSET_TOP
            height in top until bottom -> CENTER
            bottom <= height && boundsWithInsets.bottom > height -> INSET_BOTTOM
            else -> null
        }
    }

    private fun getHeaderViewForItem(itemPosition: Int, parent: RecyclerView): View? {
        if (parent.adapter == null) {
            return null
        }

        val headerPosition = getHeaderPositionForItem(itemPosition)
        if (headerPosition == RecyclerView.NO_POSITION) return null
        val headerType = parent.adapter?.getItemViewType(headerPosition) ?: return null
        // If match, reuse viewHolder
        if (currentHeader?.first == headerPosition && currentHeader?.second?.itemViewType == headerType) {
            return currentHeader?.second?.itemView
        }

        val headerHolder = parent.adapter?.createViewHolder(parent, headerType)
        if (headerHolder != null) {
            parent.adapter?.onBindViewHolder(headerHolder, headerPosition)
            fixLayoutSize(parent, headerHolder.itemView)
            // Save for next draw
            currentHeader = headerPosition to headerHolder
        }
        return headerHolder?.itemView
    }

    private fun getHeaderPositionForItem(itemPosition: Int): Int {
        var headerPosition = RecyclerView.NO_POSITION
        var currentPosition = itemPosition
        do {
            if (isHeader(currentPosition)) {
                headerPosition = currentPosition
                break
            }
            currentPosition -= 1
        } while (currentPosition >= 0)
        return headerPosition
    }

    /**
     * Properly measures and layouts the top sticky header.
     *
     * @param parent ViewGroup: RecyclerView in this case.
     */
    private fun fixLayoutSize(parent: ViewGroup, view: View) = with(view) {

        // Specs for parent (RecyclerView)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        // Specs for children (headers)
        val childWidthPadding = parent.paddingLeft + parent.paddingRight
        val childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec, childWidthPadding, layoutParams.width)
        val childHeightPadding = parent.paddingTop + parent.paddingBottom
        val childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec, childHeightPadding, layoutParams.height)

        measure(childWidthSpec, childHeightSpec)
        layout(0, 0, measuredWidth, measuredHeight)
    }

    /**
     * Returns the previous cardview if in the top inset of a header.
     * Returns the header if in top..bottom of the header view (in other words, without taking into account insets).
     * Returns the next cardview if in the bottom inset of a header.
     */
    private fun getChildInContact(parent: RecyclerView, contactPoint: Int): View? {
        parent.forEachIndexed { index, child ->
            val childInContact = when (child.intersects(parent, contactPoint)) {
                INSET_TOP -> if (index - 1 >= 0) parent.getChildAt(index - 1) else null
                CENTER -> child
                INSET_BOTTOM -> if (index + 1 < parent.childCount) parent.getChildAt(index + 1) else null
                null -> null
            }

            if (childInContact != null) return childInContact
        }
        return null
    }

    private fun moveHeader(c: Canvas, currentHeader: View, nextHeader: View, paddingTop: Int) = with(c) {
        save()
        if (!shouldFadeOutHeader) {
            clipRect(0, paddingTop, width, paddingTop + currentHeader.height)
        } else {
            saveLayerAlpha(
                RectF(0.0f, 0.0f, width.toFloat(), height.toFloat()),
                (((nextHeader.top - paddingTop) / nextHeader.height.toFloat()) * 255).toInt()
            )
        }
        translate(0.0f, (nextHeader.top - currentHeader.height).toFloat())

        currentHeader.draw(this)
        if (shouldFadeOutHeader) {
            restore()
        }
        restore()
    }

    private fun drawHeader(c: Canvas, header: View, paddingTop: Int) = with(c) {
        save()
        translate(0.0f, paddingTop.toFloat())
        header.draw(this)
        restore()
    }

    enum class Intersection {
        INSET_TOP,
        CENTER,
        INSET_BOTTOM,
    }

    private companion object {
        val TAG = HeaderItemDecoration::class.simpleName
    }
}
