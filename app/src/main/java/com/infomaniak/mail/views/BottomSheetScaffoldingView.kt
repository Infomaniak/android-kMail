/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
package com.infomaniak.mail.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewBottomSheetScaffoldingBinding

class BottomSheetScaffoldingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewBottomSheetScaffoldingBinding

    /**
     * We can receive 2 types of children:
     * - Children that come from the binding
     * - Children of [BottomSheetScaffoldingView] that are defined in the xml
     *
     * We need to only add the second type of children to the `binding.cardView` to be able to display them
     */
    private var isBindingInflated: Boolean = false

    private var useDefaultLayout: Boolean = true
    private var centerHorizontally: Boolean = false
    private var childContainer: ViewGroup

    var title: CharSequence?
        get() = binding.titleTextView.text
        set(value) {
            binding.titleTextView.apply {
                text = value
                isVisible = value != null
            }
        }

    init {
        binding = ViewBottomSheetScaffoldingBinding.inflate(LayoutInflater.from(context), this, true)
        isBindingInflated = true

        with(binding) {
            attrs?.getAttributes(context, R.styleable.BottomSheetScaffoldingView) {
                title = getString(R.styleable.BottomSheetScaffoldingView_title)
                useDefaultLayout = getBoolean(R.styleable.BottomSheetScaffoldingView_useDefaultLayout, useDefaultLayout)
                centerHorizontally = getBoolean(R.styleable.BottomSheetScaffoldingView_centerHorizontally, centerHorizontally)
            }

            childContainer = initChildContainer(context)
        }
    }

    private fun ViewBottomSheetScaffoldingBinding.initChildContainer(context: Context): ViewGroup {
        return if (useDefaultLayout) {
            val nestedScrollView = NestedScrollView(context).apply {
                layoutParams = createMatchWrapLayoutParams()
            }

            val linearLayout = LinearLayout(context).apply {
                layoutParams = createMatchWrapLayoutParams()
                orientation = LinearLayout.VERTICAL
                if (centerHorizontally) gravity = Gravity.CENTER_HORIZONTAL
            }

            nestedScrollView.addView(linearLayout)
            root.addView(nestedScrollView)

            linearLayout
        } else {
            FrameLayout(context).apply { layoutParams = createMatchWrapLayoutParams() }.also(root::addView)
        }
    }

    private fun createMatchWrapLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (isBindingInflated) {
            childContainer.addView(child, index, params)
        } else {
            super.addView(child, index, params)
        }
    }
}
