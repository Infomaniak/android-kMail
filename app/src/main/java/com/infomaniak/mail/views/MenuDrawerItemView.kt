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
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.getDimensionPixelSizeOrThrow
import androidx.core.view.isVisible
import com.google.android.material.shape.ShapeAppearanceModel
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ItemMenuDrawerBinding
import com.infomaniak.mail.utils.UiUtils.formatUnreadCount
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.getAttributeColor
import com.google.android.material.R as RMaterial
import com.infomaniak.lib.core.R as RCore

class MenuDrawerItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    val binding by lazy { ItemMenuDrawerBinding.inflate(LayoutInflater.from(context), this, true) }

    private val regular by lazy { ResourcesCompat.getFont(context, RCore.font.suisseintl_regular) }
    private val medium by lazy { ResourcesCompat.getFont(context, RCore.font.suisseintl_medium) }

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

    var itemStyle = SelectionStyle.MENU_DRAWER
        set(value) {
            field = value
            if (value == SelectionStyle.MENU_DRAWER) {
                binding.root.apply {
                    context.obtainStyledAttributes(R.style.MenuDrawerItem, intArrayOf(android.R.attr.layout_marginStart)).let {
                        setMarginsRelative(it.getDimensionPixelSizeOrThrow(0))
                        it.recycle()
                    }
                    ShapeAppearanceModel.builder(context, 0, R.style.MenuDrawerItemShapeAppearance).build()
                }
            } else {
                binding.root.apply {
                    setMarginsRelative(0)
                    shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(0.0f).build()
                }
            }
        }

    var text: CharSequence? = null
        set(value) {
            field = value
            binding.itemName.text = value
        }

    var textWeight = TextWeight.MEDIUM
        set(fontFamily) {
            field = fontFamily
            binding.itemName.typeface = if (fontFamily == TextWeight.MEDIUM) medium else regular
        }

    init {
        attrs?.getAttributes(context, R.styleable.MenuDrawerItemView) {
            badge = getInteger(R.styleable.MenuDrawerItemView_badge, badge)
            icon = getDrawable(R.styleable.MenuDrawerItemView_icon)
            indent = getDimensionPixelSize(R.styleable.MenuDrawerItemView_indent, indent)
            itemStyle = SelectionStyle.values()[getInteger(R.styleable.MenuDrawerItemView_itemStyle, 0)]
            text = getString(R.styleable.MenuDrawerItemView_text)
            textWeight = TextWeight.values()[getInteger(R.styleable.MenuDrawerItemView_textWeight, 0)]
        }
    }

    fun setSelectedState(isSelected: Boolean) = with(binding) {
        val (color, textAppearance) = if (isSelected && itemStyle == SelectionStyle.MENU_DRAWER) {
            context.getAttributeColor(RMaterial.attr.colorPrimaryContainer) to R.style.BodyMedium_Accent
        } else {
            Color.TRANSPARENT to if (textWeight == TextWeight.MEDIUM) R.style.BodyMedium else R.style.Body
        }

        checkmark.isVisible = isSelected && itemStyle == SelectionStyle.OTHER

        root.setCardBackgroundColor(color)
        itemName.setTextAppearance(textAppearance)
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) {
        binding.root.setOnClickListener(onClickListener)
    }

    enum class SelectionStyle {
        MENU_DRAWER, OTHER
    }

    enum class TextWeight {
        REGULAR, MEDIUM
    }
}
