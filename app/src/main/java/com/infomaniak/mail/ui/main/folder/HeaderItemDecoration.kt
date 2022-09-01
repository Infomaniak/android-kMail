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

import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.forEachIndexed
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.ui.main.folder.HeaderItemDecoration.Intersection.*

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
        // val topChild = parent.findChildViewUnder(parent.paddingLeft.toFloat(), parent.paddingTop.toFloat()) ?: run {
        //     // currentHeader?.second?.let {
        //     //     drawHeader(c, it.itemView, parent.paddingTop)
        //     // }
        //     Log.e("gibran", "onDrawOver: can't find view at the start of the recyclerview and returned", );
        //     return
        // }
        // val topChild = parent.getChildAt(0)
        // // val topChild = getChildInContact(parent, parent.paddingTop) ?: run {
        // //     Log.e("gibran", "onDrawOver: getChildInContact can't find view at the start of the recyclerview and returned");
        // //     return
        // // }
        //
        //
        // // if (topChild == null) { // continuer de redraw le header sauvegardé et ne rien faire d'autre // TODO
        // //     Log.e("gibran", "onDrawOver: AUCUNE vue dessous");
        // //     currentHeader?.second?.let {
        // //         Log.e("gibran", "onDrawOver: on draw ce qu'il y avait avant", );
        // //         drawHeader(c, it.itemView, parent.paddingTop)
        // //     } ?: run {
        // //         Log.e("gibran", "onDrawOver: ce qu'il y avait avant est null", );
        // //     }
        // //     return
        // // }
        //
        // // top child position *in the adapter* !!
        // val topChildPosition = parent.getChildAdapterPosition(topChild)
        // // Log.e("gibran", "onDrawOver - topChildPosition: ${topChildPosition}")
        // if (topChildPosition == RecyclerView.NO_POSITION) run {
        //     Log.e("gibran", "onDrawOver: topChildPosition is NO_POSITION and returned");
        //     return
        // }

        // top child position *in the adapter* !!
        val topChildPosition = getPositionInAdapterOfFirstChild(parent) ?: return

        // val topChildPosition = 0

        val headerView = getHeaderViewForItem(topChildPosition, parent) ?: run {
            Log.e("gibran", "onDrawOver: headerView returned because is null");
            return
        }
        Log.e("gibran", "onDrawOver - headerView for item #$topChildPosition: ${(headerView as TextView).text}")

        val contactPoint = headerView.bottom + parent.paddingTop
        // drawLines(c, contactPoint, parent)
        val childInContact = getChildInContact(parent, contactPoint) ?: run {
            Log.e("gibran", "onDrawOver: child in contact return");
            return
        }
        // Log.e("gibran", "onDrawOver - childInContact: ${childInContact}")
        Log.e("gibran", "onDrawOver - parent.getChildItemId(childInContact): ${parent.getChildItemId(childInContact)}")
        // Log.e("gibran", "onDrawOver - childInContact: ${(childInContact as? TextView)?.text}")

        if (isHeader(parent.getChildAdapterPosition(childInContact))) {
            moveHeader(c, headerView, childInContact, parent.paddingTop)
            Log.e("gibran", "onDrawOver: moveHeader returned");
            return
        }

        drawHeader(c, headerView, parent.paddingTop)
        // drawLines(c, contactPoint, parent)
        Log.e("gibran", "onDrawOver: drawing header and returning normally");
    }

    private fun getPositionInAdapterOfFirstChild(parent: RecyclerView): Int? {
        val topMostChild = parent.getChildAt(0)
        val adapterIndex = parent.getChildAdapterPosition(topMostChild)
        return when (topMostChild.intersects(parent, parent.paddingTop)) {
            INSET_TOP -> adapterIndex - 1
            CENTER -> adapterIndex
            INSET_BOTTOM -> adapterIndex + 1
            null -> {
                Log.wtf(TAG, "getPositionInAdapterOfFirstChild: The top most view in the recyclerview does not intersects parent.paddingTop (${parent.paddingTop})", )
                null
            }
        }
    }

    private fun drawLines(c: Canvas, contactPoint: Int, parent: RecyclerView) {
        c.save()
        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 1F
        }
        val paintChild = Paint().apply {
            color = Color.GREEN
            strokeWidth = 1F
        }
        val startx = 0f
        val starty = contactPoint.toFloat()
        val endx = parent.width.toFloat()
        val endy = contactPoint.toFloat()
        parent.forEachIndexed { index, child ->
            if (index <= 2) {
                val mBounds = Rect()
                parent.getDecoratedBoundsWithMargins(child, mBounds)
                c.drawLine(startx, child.top.toFloat(), endx, child.bottom.toFloat(), paintChild)
                c.drawLine(startx, mBounds.bottom.toFloat(), endx, mBounds.top.toFloat(), paint)
            }
        }
        c.drawLine(startx, starty, endx, endy, paint)
        c.restore()
    }

    private fun getHeaderViewForItem(itemPosition: Int, parent: RecyclerView): View? {
        if (parent.adapter == null) {
            return null
        }
        val headerPosition = getHeaderPositionForItem(itemPosition)
        Log.e("gibran", "getHeaderViewForItem - headerPosition($itemPosition): ${headerPosition}")
        if (headerPosition == RecyclerView.NO_POSITION) return null
        val headerType = parent.adapter?.getItemViewType(headerPosition) ?: return null
        // if match reuse viewHolder
        if (currentHeader?.first == headerPosition && currentHeader?.second?.itemViewType == headerType) {
            return currentHeader?.second?.itemView
        }

        val headerHolder = parent.adapter?.createViewHolder(parent, headerType)
        if (headerHolder != null) {
            parent.adapter?.onBindViewHolder(headerHolder, headerPosition)
            // Log.e("gibran", "getHeaderViewForItem: binding a new headerHolder ${(headerHolder.itemView as TextView).text}", );
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

        /**
         * Retourne la cardview précédante si on est dans l'inset top d'un header
         * Retourne le header si on est dans le  top..bottom de la view du header (sans compter les insets)
         * Retourne la prochaine cardview si on est dans l'inset bottom d'un header
         */

        // parent.addOnChildAttachStateChangeListener(
        //     object : View.OnAttachStateChangeListener, RecyclerView.OnChildAttachStateChangeListener {
        //         override fun onViewAttachedToWindow(v: View?) {}
        //         override fun onViewDetachedFromWindow(v: View?) {}
        //         override fun onChildViewAttachedToWindow(view: View) {}
        //
        //         override fun onChildViewDetachedFromWindow(view: View) {
        //             lastDetachedView = view
        //         }
        //     }
        // )

        var childInContact: View? = null
        parent.forEachIndexed { index, child ->
            val mBounds = Rect()
            parent.getDecoratedBoundsWithMargins(child, mBounds)

            childInContact = when (child.intersects(parent, contactPoint)) {
                INSET_TOP -> {
                    Log.e("gibran", "getChildInContact:       intersecting top inset of index $index");
                    if (index - 1 >= 0) parent.getChildAt(index - 1)
                    else null
                }
                CENTER -> {
                    Log.e("gibran", "getChildInContact:       intersecting center content of index $index");
                    child
                }
                INSET_BOTTOM -> {
                    Log.e("gibran", "getChildInContact:       intersecting bottom inset of index $index");
                    if (index + 1 < parent.childCount) parent.getChildAt(index + 1)
                    else null
                }
                null -> null
            }

            // childInContact = when {
            //     child.top > contactPoint && mBounds.top <= contactPoint -> {
            //         Log.e("gibran", "getChildInContact:       intersecting top inset of index $index");
            //         if (index - 1 >= 0) parent.getChildAt(index - 1)
            //         else null
            //     }
            //     child.bottom > contactPoint && child.top <= contactPoint -> {
            //         Log.e("gibran", "getChildInContact:       intersecting center content of index $index");
            //         child
            //     }
            //     child.bottom <= contactPoint && mBounds.bottom > contactPoint -> {
            //         Log.e("gibran", "getChildInContact:       intersecting bottom inset of index $index");
            //         if (index + 1 < parent.childCount) parent.getChildAt(index + 1)
            //         else null
            //     }
            //     else -> null
            // }

            if (childInContact != null) {
                return childInContact
            }
        }
        return null
    }

    fun View.intersects(parent: RecyclerView, height: Int): Intersection? {
        val boundsWithInsets = Rect()
        parent.getDecoratedBoundsWithMargins(this, boundsWithInsets)
        return when {
            top > height && boundsWithInsets.top <= height -> INSET_TOP
            height in top until bottom -> CENTER
            bottom <= height && boundsWithInsets.bottom > height -> INSET_BOTTOM
            else -> null
        }
    }

    enum class Intersection {
        INSET_TOP,
        CENTER,
        INSET_BOTTOM
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

    companion object {
        private const val TAG = "HeaderItemDecoration"
    }
}

inline fun View.doOnEachNextLayout(crossinline action: (view: View) -> Unit) {
    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        action(view)
    }
}
