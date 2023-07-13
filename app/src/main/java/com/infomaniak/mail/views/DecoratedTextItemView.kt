/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.res.getDimensionPixelSizeOrThrow
import com.google.android.material.shape.ShapeAppearanceModel
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.databinding.ViewDecoratedTextItemBinding

abstract class DecoratedTextItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    val binding by lazy { ViewDecoratedTextItemBinding.inflate(LayoutInflater.from(context), this, true) }

    var icon: Drawable? = null
        set(value) {
            field = value
            binding.itemName.setCompoundDrawablesWithIntrinsicBounds(value, null, null, null)
        }

    var text: CharSequence? = null
        set(value) {
            field = value
            binding.itemName.text = value
        }

    init {
        attrs?.getAttributes(context, com.infomaniak.mail.R.styleable.DecoratedTextItemView) {
            icon = getDrawable(com.infomaniak.mail.R.styleable.DecoratedTextItemView_icon)
            text = getString(com.infomaniak.mail.R.styleable.DecoratedTextItemView_text)
        }

        binding.root.apply {
            context.obtainStyledAttributes(
                com.infomaniak.mail.R.style.MenuDrawerItem,
                intArrayOf(android.R.attr.layout_marginStart)
            ).let {
                setMarginsRelative(it.getDimensionPixelSizeOrThrow(0))
                it.recycle()
            }
            ShapeAppearanceModel.builder(context, 0, com.infomaniak.mail.R.style.MenuDrawerItemShapeAppearance).build()
        }
    }
}
