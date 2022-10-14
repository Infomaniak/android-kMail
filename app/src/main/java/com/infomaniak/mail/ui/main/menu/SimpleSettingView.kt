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
package com.infomaniak.mail.ui.main.menu

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewSimpleSettingBinding

class SimpleSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewSimpleSettingBinding

    init {
        orientation = VERTICAL
        binding = ViewSimpleSettingBinding.inflate(LayoutInflater.from(context), this)

        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SimpleSettingView, 0, 0)
            typedArray.getString(R.styleable.SimpleSettingView_title)?.let { binding.toolbar.title = it }
            typedArray.recycle()
        }
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        @Suppress("SENSELESS_COMPARISON")
        if (binding == null) {
            super.addView(child, index, params)
        } else {
            binding.cardview.addView(child, index, params)
        }
    }

    fun setNavigationOnClickListener(listener: OnClickListener) {
        binding.toolbar.setNavigationOnClickListener(listener)
    }
}
