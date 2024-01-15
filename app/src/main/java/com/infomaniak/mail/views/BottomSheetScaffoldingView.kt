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
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewBottomSheetScaffoldingBinding
import com.infomaniak.mail.ui.main.menu.SimpleSettingView
import com.infomaniak.lib.core.R as RCore

class BottomSheetScaffoldingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewBottomSheetScaffoldingBinding

    /**
     * We can receive 2 types of children:
     * - Children that come from the binding
     * - Children of [SimpleSettingView] that are defined in the xml
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
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            val linearLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.VERTICAL
                if (centerHorizontally) gravity = Gravity.CENTER_HORIZONTAL
                setMarginsRelative(bottom = context.resources.getDimensionPixelSize(RCore.dimen.marginStandardMedium))
            }

            nestedScrollView.addView(linearLayout)
            root.addView(nestedScrollView)

            linearLayout
        } else {
            val frameLayout = FrameLayout(context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT,
                )
            }

            root.addView(frameLayout)

            frameLayout
        }
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (isBindingInflated) {
            childContainer.addView(child, index, params)
        } else {
            super.addView(child, index, params)
        }
    }
}
