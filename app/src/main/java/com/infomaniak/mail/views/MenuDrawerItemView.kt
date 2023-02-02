/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ItemMenuDrawerBinding
import com.infomaniak.mail.utils.UiUtils.formatUnreadCount
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.getAttributeColor
import com.google.android.material.R as RMaterial

class MenuDrawerItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    val binding by lazy { ItemMenuDrawerBinding.inflate(LayoutInflater.from(context), this, true) }

    var badge: Int = 0
        set(value) {
            field = value
            binding.itemBadge.apply {
                isVisible = value > 0
                text = formatUnreadCount(value)
            }
        }

    var icon: Drawable? = null
        set(value) {
            field = value
            binding.itemName.setCompoundDrawablesWithIntrinsicBounds(value, null, null, null)
        }

    var indent: Int = 0
        set(value) {
            field = value
            binding.itemName.setMarginsRelative(start = value)
        }

    var selectionStyle = SelectionStyle.MENU_DRAWER

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

    var textWeight: Int? = null
        set(value) {
            field = value
            value?.let { binding.itemName.typeface = ResourcesCompat.getFont(context, value) }
        }

    init {
        attrs?.getAttributes(context, R.styleable.MenuDrawerItemView) {
            val defaultTextSize = binding.itemName.textSize.toInt()

            badge = getInteger(R.styleable.MenuDrawerItemView_badge, badge)
            icon = getDrawable(R.styleable.MenuDrawerItemView_icon)
            indent = getDimensionPixelSize(R.styleable.MenuDrawerItemView_indent, indent)
            selectionStyle = SelectionStyle.values()[getInteger(R.styleable.MenuDrawerItemView_selectionStyle, 0)]
            text = getString(R.styleable.MenuDrawerItemView_text)
            textSize = getDimensionPixelSize(R.styleable.MenuDrawerItemView_textSize, defaultTextSize)
            getResourceId(R.styleable.MenuDrawerItemView_textWeight, 0).also { fontFamily ->
                if (fontFamily > 0) textWeight = fontFamily
            }
        }
    }

    fun setSelectedState(isSelected: Boolean) = with(binding) {
        val (color, textAppearance) = if (isSelected && selectionStyle == SelectionStyle.MENU_DRAWER) {
            context.getAttributeColor(RMaterial.attr.colorPrimaryContainer) to R.style.BodyMedium_Accent
        } else {
            ContextCompat.getColor(context, R.color.backgroundColor) to R.style.BodyMedium
        }

        itemTick.isVisible = isSelected && selectionStyle == SelectionStyle.MOVE_FRAGMENT

        root.setCardBackgroundColor(color)
        itemName.setTextAppearance(textAppearance)
    }

    fun setBadgeVisibility(isVisible: Boolean) {
        binding.itemBadge.isVisible = isVisible
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) {
        binding.root.setOnClickListener(onClickListener)
    }

    enum class SelectionStyle {
        MENU_DRAWER, MOVE_FRAGMENT
    }
}
