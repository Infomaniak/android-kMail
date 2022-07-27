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
package com.infomaniak.mail.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ItemMenuDrawerBinding
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.getAttributeColor
import com.infomaniak.mail.utils.setMargins

class MenuDrawerItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var binding: ItemMenuDrawerBinding

    var badge: CharSequence? = null
        set(value) {
            field = value
            binding.itemBadge.text = value
        }

    var icon: Drawable? = null
        set(value) {
            field = value
            binding.itemName.setCompoundDrawablesWithIntrinsicBounds(value, null, null, null)
        }

    var indent: Int = 0
        set(value) {
            field = value
            binding.itemName.setMargins(left = value)
        }

    var text: CharSequence? = null
        set(value) {
            field = value
            binding.itemName.text = value
        }

    var textSize: Int? = null
        set(value) {
            field = value
            value?.let { binding.itemName.setTextSize(TypedValue.COMPLEX_UNIT_PX, it.toFloat()) }
        }

    init {
        binding = ItemMenuDrawerBinding.inflate(LayoutInflater.from(context), this, true)

        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MenuDrawerItemView, 0, 0)

            val iconDrawable = typedArray.getDrawable(R.styleable.MenuDrawerItemView_icon)
            val nameText = typedArray.getString(R.styleable.MenuDrawerItemView_text)
            val badgeText = typedArray.getString(R.styleable.MenuDrawerItemView_badge)
            val indentValue = typedArray.getDimensionPixelSize(R.styleable.MenuDrawerItemView_indent, 0)
            val textSizeValue = typedArray.getDimensionPixelSize(R.styleable.MenuDrawerItemView_textSize, 0)

            icon = iconDrawable
            text = nameText
            badge = badgeText
            indent = indentValue
            textSize = textSizeValue

            typedArray.recycle()
        }
    }

    fun setSelectedState(isSelected: Boolean) = with(binding) {
        val (color, textColor, textAppearance) = if (isSelected) {
            Triple(
                context.getAttributeColor(com.google.android.material.R.attr.colorPrimaryContainer),
                context.getAttributeColor(com.google.android.material.R.attr.colorPrimary),
                R.style.Body_Highlighted
            )
        } else {
            Triple(
                ContextCompat.getColor(context, R.color.backgroundColor),
                ContextCompat.getColor(context, R.color.primaryTextColor),
                R.style.Body
            )
        }

        root.setCardBackgroundColor(color)
        itemName.setTextColor(textColor)
        itemName.setTextAppearance(textAppearance)
    }

    fun setBadgeVisibility(isVisible: Boolean) {
        binding.itemBadge.isGone = isVisible
    }

    override fun setOnClickListener(l: OnClickListener?) {
        binding.root.setOnClickListener(l)
    }
}
