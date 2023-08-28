/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.view.ScaleGestureDetector
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

class MessageBodyScaleListener(
    private val webViewScrollView: RecyclerView,
    private val messageBodyWebView: WebView,
    private val directParent: FrameLayout,
) : ScaleGestureDetector.SimpleOnScaleGestureListener() {

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        webViewScrollView.requestDisallowInterceptTouchEvent(true)

        messageBodyWebView.scrollTo(0, 0)
        directParent.scrollTo(0, 0)

        return true
    }
}
