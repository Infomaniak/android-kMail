/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.data.models.thread.Thread
import kotlin.math.abs

class SwipeToSelectTouchListener(
    recyclerView: RecyclerView,
    private val multiSelection: MultiSelectionListener<Thread>,
) : RecyclerView.OnItemTouchListener {

    private val touchSlop = ViewConfiguration.get(recyclerView.context).scaledTouchSlop
    private var isActive = false
    private var startX = 0f
    private var startY = 0f
    private var startPosition = RecyclerView.NO_POSITION
    private var lastSelectedPosition = RecyclerView.NO_POSITION
    private var selectionIntent: Boolean? = null // true = select, false = deselect

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.x
                startY = e.y
                startPosition = rv.findChildViewUnder(startX, startY)
                    ?.let(rv::getChildAdapterPosition)
                    ?: RecyclerView.NO_POSITION
                lastSelectedPosition = RecyclerView.NO_POSITION
                selectionIntent = null
                isActive = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isActive && multiSelection.isEnabled) {
                    val dy = abs(e.y - startY)
                    val dx = abs(e.x - startX)
                    if (dy > touchSlop && dy > dx) {
                        val startThread = (rv.adapter as? ThreadListAdapter)?.getThreadAt(startPosition)
                        selectionIntent = startThread != null && !multiSelection.selectedItems.contains(startThread)

                        if (startThread != null) {
                            applyIntent(startThread)
                            multiSelection.publishSelectedItems()
                            rv.adapter?.notifyItemChanged(startPosition, ThreadListAdapter.NotificationType.SELECTED_STATE)
                            lastSelectedPosition = startPosition
                        }

                        isActive = true
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> reset()
        }
        return isActive
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!isActive) return
                val view = rv.findChildViewUnder(e.x, e.y) ?: return
                val position = rv.getChildAdapterPosition(view)
                if (position == RecyclerView.NO_POSITION || position == lastSelectedPosition) return

                val thread = (rv.adapter as? ThreadListAdapter)?.getThreadAt(position) ?: return
                applyIntent(thread)
                multiSelection.publishSelectedItems()
                rv.adapter?.notifyItemChanged(position, ThreadListAdapter.NotificationType.SELECTED_STATE)
                rv.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                lastSelectedPosition = position
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> reset()
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) reset()
    }

    private fun applyIntent(thread: Thread) {
        val intent = selectionIntent ?: return
        if (intent) {
            multiSelection.selectedItems.add(thread)
        } else {
            multiSelection.selectedItems.remove(thread)
        }
    }

    private fun reset() {
        isActive = false
        startX = 0f
        startY = 0f
        startPosition = RecyclerView.NO_POSITION
        lastSelectedPosition = RecyclerView.NO_POSITION
        selectionIntent = null
    }
}
