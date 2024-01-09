/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import java.lang.reflect.Field
import java.lang.reflect.Method


class NoAnimSlidingPaneLayout(context: Context, attrs: AttributeSet?, defStyle: Int) :
    SlidingPaneLayout(context, attrs, defStyle) {

    private var slideOffsetField: Field? = null
    private var slideableViewField: Field? = null
    private var updateObscuredViewsVisibilityMethod: Method? = null
    private var dispatchOnPanelOpenedMethod: Method? = null
    private var dispatchOnPanelClosedMethod: Method? = null
    private var preservedOpenStateField: Field? = null
    private var parallaxOtherViewsMethod: Method? = null

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    init {
        try {
            slideOffsetField = SlidingPaneLayout::class.java.getDeclaredField("mSlideOffset")
            slideableViewField = SlidingPaneLayout::class.java.getDeclaredField("mSlideableView")
            preservedOpenStateField = SlidingPaneLayout::class.java.getDeclaredField("mPreservedOpenState")

            updateObscuredViewsVisibilityMethod = SlidingPaneLayout::class.java.getDeclaredMethod(
                "updateObscuredViewsVisibility",
                View::class.java
            )
            dispatchOnPanelClosedMethod = SlidingPaneLayout::class.java.getDeclaredMethod(
                "dispatchOnPanelClosed",
                View::class.java
            )
            dispatchOnPanelOpenedMethod = SlidingPaneLayout::class.java.getDeclaredMethod(
                "dispatchOnPanelOpened",
                View::class.java
            )
            parallaxOtherViewsMethod = SlidingPaneLayout::class.java.getDeclaredMethod(
                "parallaxOtherViews",
                Float::class.javaPrimitiveType
            )

            slideOffsetField?.isAccessible = true
            slideableViewField?.isAccessible = true
            updateObscuredViewsVisibilityMethod?.isAccessible = true
            dispatchOnPanelOpenedMethod?.isAccessible = true
            dispatchOnPanelClosedMethod?.isAccessible = true
            preservedOpenStateField?.isAccessible = true
            parallaxOtherViewsMethod?.isAccessible = true
        } catch (e: Exception) {
            Log.w(this.javaClass.simpleName, "Failed to set up animation-less sliding pane layout.")
        }
    }

    fun openPaneNoAnimation(): Boolean {
        return try {
            slideOffsetField?.set(this, 0f)
            parallaxOtherViewsMethod?.invoke(this, 0f)
            requestLayout()
            invalidate()
            dispatchOnPanelOpenedMethod?.invoke(this, slideableViewField?.get(this) as View)
            preservedOpenStateField?.set(this, true)
            isOpen
        } catch (e: Exception) {
            openPane()
        }
    }

    fun closePaneNoAnimation(): Boolean {
        return try {
            val slideableView = slideableViewField?.get(this) as View
            slideOffsetField?.set(this, 1f)
            parallaxOtherViewsMethod?.invoke(this, 1f)
            requestLayout()
            invalidate()
            updateObscuredViewsVisibilityMethod?.invoke(this, slideableView)
            dispatchOnPanelClosedMethod?.invoke(this, slideableView)
            preservedOpenStateField?.set(this, false)
            !isOpen
        } catch (e: Exception) {
            closePane()
        }
    }
}