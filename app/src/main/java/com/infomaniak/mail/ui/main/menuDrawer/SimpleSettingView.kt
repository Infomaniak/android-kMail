/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.menuDrawer

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.navigation.findNavController
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewSimpleSettingBinding

class SimpleSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewSimpleSettingBinding

    /**
     * We can receive 2 types of children:
     * - Children that come from the binding
     * - Children of [SimpleSettingView] that are defined in the xml
     *
     * We need to only add the second type of children to the `binding.cardView` to be able to display them
     */
    private var isBindingInflated: Boolean = false

    init {
        orientation = VERTICAL
        binding = ViewSimpleSettingBinding.inflate(LayoutInflater.from(context), this)
        isBindingInflated = true

        attrs?.getAttributes(context, R.styleable.SimpleSettingView) {
            getString(R.styleable.SimpleSettingView_title)?.let { binding.toolbar.title = it }
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (isBindingInflated) {
            binding.cardView.addView(child, index, params)
        } else {
            super.addView(child, index, params)
        }
    }

    fun setTitle(@StringRes title: Int) {
        binding.toolbar.setTitle(title)
    }

    fun setTitle(title: String) {
        binding.toolbar.title = title
    }
}
