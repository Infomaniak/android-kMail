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
package com.infomaniak.mail.ui.main.thread.actions

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ItemBottomSheetActionBinding

class ActionItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var binding: ItemBottomSheetActionBinding

    init {
        binding = ItemBottomSheetActionBinding.inflate(LayoutInflater.from(context), this, true)

        with(binding) {
            attrs?.getAttributes(context, R.styleable.ActionItemView) {
                button.icon = getDrawable(R.styleable.ActionItemView_icon)
                button.text = getString(R.styleable.ActionItemView_text)
                divider.isVisible = getBoolean(R.styleable.ActionItemView_visibleDivider, true)
            }
        }
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) {
        binding.button.setOnClickListener(onClickListener)
    }

    fun setIconResource(@DrawableRes iconResourceId: Int) = binding.button.setIconResource(iconResourceId)

    fun setIconTint(@ColorInt color: Int) {
        binding.button.iconTint = ColorStateList.valueOf(color)
    }

    fun setText(@StringRes textResourceId: Int) = binding.button.setText(textResourceId)
}
