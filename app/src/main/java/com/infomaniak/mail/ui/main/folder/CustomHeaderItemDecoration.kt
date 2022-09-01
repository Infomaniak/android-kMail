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
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.databinding.ItemThreadStickyHeaderBinding

class CustomHeaderItemDecoration(
    parent: RecyclerView,
    private val isHeader: (itemPosition: Int) -> Boolean
) : RecyclerView.ItemDecoration() {

    // private var currentHeader: Pair<Int, RecyclerView.ViewHolder>? = null

    private var currentHeader2: Pair<Int, RecyclerView.ViewHolder>? = null
    private var currentHeader: Pair<Int, String>? = null
    // private var previousTitle: String = ""
    // private var previousIntersectionType: IntersectionType = IntersectionType.NONE
    // private var wasIntersectingBottom: Boolean = false
    // private var lastHeader = ""
    private var binding: ItemThreadStickyHeaderBinding

    init {
        binding = ItemThreadStickyHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        // parent.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
        //     override fun onChanged() {
        //         // clear saved header as it can be outdated now
        //         currentHeader = null
        //     }
        // })
        //
        // parent.doOnEachNextLayout {
        //     // clear saved layout as it may need layout update
        //     currentHeader = null
        // }
        //
        // // handle click on sticky header
        // parent.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
        //     override fun onInterceptTouchEvent(recyclerView: RecyclerView, motionEvent: MotionEvent): Boolean {
        //         return if (motionEvent.action == MotionEvent.ACTION_DOWN) {
        //             motionEvent.y <= (currentHeader?.second?.itemView?.bottom ?: 0)
        //         } else {
        //             false
        //         }
        //     }
        // })
    }

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

    private fun getHeaderTitleForScrollPosition(parent: RecyclerView): String? {
        val topChild = parent.findChildViewUnder(parent.paddingLeft.toFloat(), parent.paddingTop.toFloat()) ?: return null
        val topChildPosition = parent.getChildAdapterPosition(topChild)
        if (topChildPosition == RecyclerView.NO_POSITION) return null
        return getHeaderStringForItem(topChildPosition, parent)
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        // val headerTitle = getHeaderTitleForScrollPosition(parent)

        fixLayoutSize(parent, binding.root)
        // headerTitle?.let { binding.sectionTitle.text = it }

        run breaking@{
            parent.children.forEachIndexed { index, child ->
                if (!isHeader(index)) return@forEachIndexed

                // val isAboveTrigger = child.top > parent.paddingTop
                // if (isAboveTrigger) {
                //
                //     return@breaking
                // }
                // else {
                //
                // }

                // val isBelowTrigger = child.top < parent.paddingTop
                // if (isBelowTrigger)

        //         val intersectionType = child.getIntersectionWithHeader()
        // //         if (previousIntersectionType == IntersectionType.NONE && intersectionType != IntersectionType.NONE) {
        // //             if (intersectionType == IntersectionType.TOP) binding.sectionTitle.text = previousTitle
        // //             else binding.sectionTitle.text = (child as TextView).text
        // //             binding.sectionTitle.text = (child as TextView).text
        // //             return@breaking
        // //         }
        //         if (intersectionType != IntersectionType.NONE) {
        //             when (intersectionType) {
        //                 IntersectionType.TOP -> binding.sectionTitle.text = getHeaderTitleForScrollPosition(parent)
        //                 // IntersectionType.BOTTOM -> binding.sectionTitle.text = (child as TextView).text
        //                 else -> Unit
        //             }
        //             return@breaking
        //         }
        //         previousIntersectionType = intersectionType
            }
        }

        if (binding.sectionTitle.text.isBlank()) return

        c.save()
        c.translate(0f, parent.paddingTop.toFloat())
        binding.root.draw(c)
        c.restore()


        displayHeadersDiagonals(c, parent)


        // val topChild = parent.findChildViewUnder(parent.paddingLeft.toFloat(), parent.paddingTop.toFloat()) ?: return
        // val topChildPosition = parent.getChildAdapterPosition(topChild)
        // if (topChildPosition == RecyclerView.NO_POSITION) return
        //
        // val headerView = getHeaderViewForItem(topChildPosition, parent) ?: return
        //
        // val contactPoint = headerView.bottom + parent.paddingTop
        // drawLines(c, contactPoint, parent)
        // val childInContact = getChildInContact(parent, contactPoint) ?: return
        // Log.e("gibran", "onDrawOver - childInContact: ${(childInContact as? TextView)?.text}")
        //
        // if (isHeader(parent.getChildAdapterPosition(childInContact))) {
        //     moveHeader(c, headerView, childInContact, parent.paddingTop)
        //     return
        // }
        //
        // drawHeader(c, headerView, parent.paddingTop)
        // drawLines(c, contactPoint, parent)
    }

    private fun getHeaderStringForItem(itemPosition: Int, parent: RecyclerView): String? {
        if (parent.adapter == null) {
            return null
        }
        val headerPosition = getHeaderPositionForItem(itemPosition)
        Log.e("gibran", "getHeaderViewForItem - headerPosition($itemPosition): ${headerPosition}")
        if (headerPosition == RecyclerView.NO_POSITION) return null
        val headerType = parent.adapter?.getItemViewType(headerPosition) ?: return null
        // if match reuse viewHolder
        if (currentHeader?.first == headerPosition) {
            return currentHeader?.second
        }

        val headerHolder = parent.adapter?.createViewHolder(parent, headerType)
        if (headerHolder != null) {
            parent.adapter?.onBindViewHolder(headerHolder, headerPosition)
            Log.e("gibran", "getHeaderViewForItem: binding new header holder");
            // Log.e("gibran", "getHeaderViewForItem: binding a new headerHolder ${(headerHolder.itemView as TextView).text}");
            fixLayoutSize(parent, headerHolder.itemView)
            // save for next draw
            currentHeader = headerPosition to (headerHolder.itemView as TextView).text.toString()
        }
        return (headerHolder?.itemView as TextView).text.toString()
    }

    private fun getHeaderViewForItem2(itemPosition: Int, parent: RecyclerView): View? {
        if (parent.adapter == null) {
            return null
        }
        val headerPosition = getHeaderPositionForItem(itemPosition)
        Log.e("gibran", "getHeaderViewForItem - headerPosition($itemPosition): ${headerPosition}")
        if (headerPosition == RecyclerView.NO_POSITION) return null
        val headerType = parent.adapter?.getItemViewType(headerPosition) ?: return null
        // if match reuse viewHolder
        if (currentHeader2?.first == headerPosition && currentHeader2?.second?.itemViewType == headerType) {
            return currentHeader2?.second?.itemView
        }

        val headerHolder = parent.adapter?.createViewHolder(parent, headerType)
        if (headerHolder != null) {
            parent.adapter?.onBindViewHolder(headerHolder, headerPosition)
            Log.e("gibran", "getHeaderViewForItem: binding a new headerHolder ${(headerHolder.itemView as TextView).text}");
            fixLayoutSize(parent, headerHolder.itemView)
            // save for next draw
            currentHeader2 = headerPosition to headerHolder
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


    private fun View.getIntersectionWithHeader(): IntersectionType {
        val intersectsBottom = binding.root.bottom in top..bottom
        val intersectsTop = binding.root.top in top..bottom
        return when {
            // intersectsBottom && intersectsTop -> IntersectionType.BOTH
            // intersectsBottom -> IntersectionType.BOTTOM
            intersectsTop -> IntersectionType.TOP
            else -> IntersectionType.NONE
        }
    }

    enum class IntersectionType {
        TOP,
        BOTTOM,
        NONE,
        BOTH
    }

    private fun View.isIntersectingHeader(): Boolean {
        return top <= binding.root.bottom || bottom >= binding.root.top
    }

    private fun View.isIntersecting(limit: Int): Boolean {
        return limit in (top + 1)..bottom
    }

    private fun displayHeadersDiagonals(c: Canvas, parent: RecyclerView) {
        c.save()
        Log.e("gibran", "onDrawOver - parent.childCount: ${parent.childCount}")
        parent.children.forEach { child ->

            if (child !is TextView) return@forEach

            val paintRed = Paint().apply {
                color = Color.RED
                strokeWidth = 1F
            }
            val paintGreen = Paint().apply {
                color = Color.GREEN
                strokeWidth = 1F
            }
            val startx = 0f
            val starty = child.top.toFloat()
            val endx = parent.width.toFloat()
            val endy = child.bottom.toFloat()
            c.drawLine(startx, starty, endx, endy, paintRed)

            val mBounds = Rect()
            parent.getDecoratedBoundsWithMargins(child, mBounds)
            val boundY = mBounds.top.toFloat()
            val boundEndY = mBounds.bottom.toFloat()
            c.drawLine(startx, boundEndY, endx, boundY, paintGreen)
        }
        c.restore()
    }
}
