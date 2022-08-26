/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView

class HeaderItemDecoration(
    parent: RecyclerView,
    private val shouldFadeOutHeader: Boolean = false,
    private val isHeader: (itemPosition: Int) -> Boolean
) : RecyclerView.ItemDecoration() {

    private var currentHeader: Pair<Int, RecyclerView.ViewHolder>? = null

    init {
        parent.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                // clear saved header as it can be outdated now
                currentHeader = null
            }
        })

        parent.doOnEachNextLayout {
            // clear saved layout as it may need layout update
            currentHeader = null
        }

        // handle click on sticky header
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
        val topChild = parent.findChildViewUnder(parent.paddingLeft.toFloat(), parent.paddingTop.toFloat()) ?: return
        val topChildPosition = parent.getChildAdapterPosition(topChild)
        if (topChildPosition == RecyclerView.NO_POSITION) return

        val headerView = getHeaderViewForItem(topChildPosition, parent) ?: return

        val contactPoint = headerView.bottom + parent.paddingTop
        val childInContact = getChildInContact(parent, contactPoint) ?: return

        if (isHeader(parent.getChildAdapterPosition(childInContact))) {
            moveHeader(c, headerView, childInContact, parent.paddingTop)
            return
        }

        drawHeader(c, headerView, parent.paddingTop)
    }

    private fun getHeaderViewForItem(itemPosition: Int, parent: RecyclerView): View? {
        if (parent.adapter == null) {
            return null
        }
        val headerPosition = getHeaderPositionForItem(itemPosition)
        if (headerPosition == RecyclerView.NO_POSITION) return null
        val headerType = parent.adapter?.getItemViewType(headerPosition) ?: return null
        // if match reuse viewHolder
        if (currentHeader?.first == headerPosition && currentHeader?.second?.itemViewType == headerType) {
            return currentHeader?.second?.itemView
        }

        val headerHolder = parent.adapter?.createViewHolder(parent, headerType)
        if (headerHolder != null) {
            parent.adapter?.onBindViewHolder(headerHolder, headerPosition)
            fixLayoutSize(parent, headerHolder.itemView)
            // save for next draw
            currentHeader = headerPosition to headerHolder
        }
        return headerHolder?.itemView
    }

    private fun drawHeader(c: Canvas, header: View, paddingTop: Int) = with(c) {
        save()
        translate(0f, paddingTop.toFloat())
        header.draw(this)
        restore()
    }

    private fun moveHeader(c: Canvas, currentHeader: View, nextHeader: View, paddingTop: Int) = with(c) {
        save()
        if (!shouldFadeOutHeader) {
            clipRect(0, paddingTop, width, paddingTop + currentHeader.height)
        } else {
            saveLayerAlpha(
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                (((nextHeader.top - paddingTop) / nextHeader.height.toFloat()) * 255).toInt()
            )
        }
        translate(0f, (nextHeader.top - currentHeader.height).toFloat())

        currentHeader.draw(this)
        if (shouldFadeOutHeader) {
            restore()
        }
        restore()
    }

    private fun getChildInContact(parent: RecyclerView, contactPoint: Int): View? {
        var childInContact: View? = null
        parent.forEach { child ->
            val mBounds = Rect()
            parent.getDecoratedBoundsWithMargins(child, mBounds)
            if (mBounds.bottom > contactPoint) {
                if (mBounds.top <= contactPoint) {
                    // This child overlaps the contactPoint
                    childInContact = child
                    return@forEach
                }
            }
        }
        return childInContact
    }

    /**
     * Properly measures and layouts the top sticky header.
     *
     * @param parent ViewGroup: RecyclerView in this case.
     */
    private fun fixLayoutSize(parent: ViewGroup, view: View) {

        // Specs for parent (RecyclerView)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        // Specs for children (headers)
        val childWidthSpec = ViewGroup.getChildMeasureSpec(
            widthSpec,
            parent.paddingLeft + parent.paddingRight,
            view.layoutParams.width
        )
        val childHeightSpec = ViewGroup.getChildMeasureSpec(
            heightSpec,
            parent.paddingTop + parent.paddingBottom,
            view.layoutParams.height
        )

        view.measure(childWidthSpec, childHeightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
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
}

inline fun View.doOnEachNextLayout(crossinline action: (view: View) -> Unit) {
    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        action(view)
    }
}
