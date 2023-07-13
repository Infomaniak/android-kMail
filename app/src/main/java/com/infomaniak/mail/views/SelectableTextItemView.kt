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
import android.graphics.Color
import android.util.AttributeSet
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.getDimensionPixelSizeOrThrow
import com.google.android.material.shape.ShapeAppearanceModel
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.getAttributeColor
import com.google.android.material.R as RMaterial

abstract class SelectableTextItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : DecoratedTextItemView(context, attrs, defStyleAttr) {

    private val regular by lazy { ResourcesCompat.getFont(context, com.infomaniak.lib.core.R.font.suisseintl_regular) }
    private val medium by lazy { ResourcesCompat.getFont(context, com.infomaniak.lib.core.R.font.suisseintl_medium) }

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
                    if (value == SelectionStyle.ACCOUNT) setContentPadding(0, 0, 0, 0)
                }
            }
        }

    var textWeight = TextWeight.MEDIUM
        set(fontFamily) {
            field = fontFamily
            binding.itemName.typeface = if (fontFamily == TextWeight.MEDIUM) medium else regular
        }

    private var isInSelectedState = false

    init {
        attrs?.getAttributes(context, R.styleable.MenuDrawerItemView) {
            itemStyle = SelectionStyle.values()[getInteger(R.styleable.MenuDrawerItemView_itemStyle, 0)]
            textWeight = TextWeight.values()[getInteger(R.styleable.MenuDrawerItemView_textWeight, 0)]
        }

        fun setSelectedState(isSelected: Boolean) = with(binding) {
            isInSelectedState = isSelected
            val (color, textAppearance) = if (isSelected && itemStyle == SelectionStyle.MENU_DRAWER) {
                context.getAttributeColor(RMaterial.attr.colorPrimaryContainer) to R.style.BodyMedium_Accent
            } else {
                Color.TRANSPARENT to if (textWeight == TextWeight.MEDIUM) R.style.BodyMedium else R.style.Body
            }

            root.setCardBackgroundColor(color)
            itemName.setTextAppearance(textAppearance)

        }
    }

    enum class SelectionStyle {
        MENU_DRAWER,
        ACCOUNT,
        OTHER,
    }

    enum class TextWeight {
        REGULAR,
        MEDIUM,
    }
}
