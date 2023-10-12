/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.ui.newMessage

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.content.res.getColorOrThrow
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ItemToggleableTextFormatterBinding

class ToggleableTextFormatterItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ItemToggleableTextFormatterBinding.inflate(LayoutInflater.from(context), this, true) }

    @ColorInt
    private var unselectedIconColor: Int = -1
    @ColorInt
    private var selectedIconColor: Int = -1
    @ColorInt
    private var unselectedBackgroundColor: Int = -1
    @ColorInt
    private var selectedBackgroundColor: Int = -1

    var isToggled: Boolean = false
        set(value) = with(binding) {
            field = value
            if (value) {
                binding.icon.setColorFilter(selectedIconColor)
                background.setBackgroundColor(selectedBackgroundColor)
            } else {
                binding.icon.setColorFilter(unselectedIconColor)
                background.setBackgroundColor(unselectedBackgroundColor)
            }
        }

    init {
        attrs?.getAttributes(
            context,
            R.styleable.ToggleableTextFormatterItemView,
            defStyleAttr,
            R.style.ToggleableTextFormatterItemView
        ) {
            with(binding) {
                unselectedIconColor = getColorOrThrow(R.styleable.ToggleableTextFormatterItemView_iconColor)
                selectedIconColor = getColorOrThrow(R.styleable.ToggleableTextFormatterItemView_selectedIconColor)
                unselectedBackgroundColor = getColorOrThrow(R.styleable.ToggleableTextFormatterItemView_backgroundColor)
                selectedBackgroundColor = getColorOrThrow(R.styleable.ToggleableTextFormatterItemView_selectedBackgroundColor)

                icon.apply {
                    getString(R.styleable.ToggleableTextFormatterItemView_android_contentDescription)?.let {
                        contentDescription = it
                    }
                    setImageDrawable(getDrawable(R.styleable.ToggleableTextFormatterItemView_icon))
                    setColorFilter(unselectedIconColor)
                }

                background.setBackgroundColor(unselectedBackgroundColor)
            }
        }
    }

    override fun setOnClickListener(listener: OnClickListener?) {
        binding.root.setOnClickListener {
            isToggled = !isToggled
            listener?.onClick(it)
        }
    }
}
