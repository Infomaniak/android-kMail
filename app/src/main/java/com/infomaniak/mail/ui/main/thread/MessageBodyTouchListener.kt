/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewParent
import android.webkit.WebView
import kotlin.math.abs

class MessageBodyTouchListener(
    private val webViewScrollView: ViewParent,
    private val scaleDetector: ScaleGestureDetector,
    private val scaledTouchSlop: Int,
) : View.OnTouchListener {

    private var lastX = 0.0f
    private var lastY = 0.0f

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        if (view is WebView) {
            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = motionEvent.rawX
                    lastY = motionEvent.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = motionEvent.rawX - lastX
                    val deltaY = motionEvent.rawY - lastY

                    if (abs(deltaX) > scaledTouchSlop && abs(deltaY) < scaledTouchSlop || motionEvent.pointerCount > 1) {
                        webViewScrollView.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
        }

        scaleDetector.onTouchEvent(motionEvent)
        return false
    }
}
