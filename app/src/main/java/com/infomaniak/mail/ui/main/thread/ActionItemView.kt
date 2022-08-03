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
package com.infomaniak.mail.ui.main.thread

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewActionItemBinding

class ActionItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var binding: ViewActionItemBinding

    init {
        binding = ViewActionItemBinding.inflate(LayoutInflater.from(context), this, true)

        with(binding) {
            if (attrs != null) {
                val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ActionItemView, 0, 0)

                val icon = typedArray.getDrawable(R.styleable.ActionItemView_icon)
                val text = typedArray.getString(R.styleable.ActionItemView_text)

                button.icon = icon
                button.text = text

                typedArray.recycle()
            }
        }
    }
}
