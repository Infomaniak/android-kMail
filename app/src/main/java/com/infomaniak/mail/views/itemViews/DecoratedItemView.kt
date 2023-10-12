/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.views.itemViews

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.getDimensionPixelSizeOrThrow
import androidx.core.view.isGone
import com.google.android.material.shape.ShapeAppearanceModel
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewDecoratedTextItemBinding
import com.infomaniak.lib.core.R as RCore

sealed class DecoratedItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    val binding by lazy { ViewDecoratedTextItemBinding.inflate(LayoutInflater.from(context), this, true) }

    private val regular by lazy { ResourcesCompat.getFont(context, RCore.font.suisseintl_regular) }
    private val medium by lazy { ResourcesCompat.getFont(context, RCore.font.suisseintl_medium) }

    var icon: Drawable? = null
        set(value) {
            field = value
            binding.itemName.setCompoundDrawablesWithIntrinsicBounds(value, null, null, null)
        }

    var itemStyle = SelectionStyle.OTHER
        set(value) {
            field = value
            if (value == SelectionStyle.MENU_DRAWER) {
                binding.root.apply {
                    context.obtainStyledAttributes(
                        R.style.RoundedDecoratedTextItem,
                        intArrayOf(android.R.attr.layout_marginStart),
                    ).let {
                        setMarginsRelative(it.getDimensionPixelSizeOrThrow(0))
                        it.recycle()
                    }
                    ShapeAppearanceModel.builder(context, 0, R.style.RoundedDecoratedTextItemShapeAppearance).build()
                }
            } else {
                binding.root.apply {
                    setMarginsRelative(0)
                    shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(0.0f).build()
                    setContentPadding(0, 0, 0, 0)
                }
            }
        }

    var maxLines: Int = DEFAULT_MAX_LINES
        set(value) {
            field = value
            binding.itemName.maxLines = value
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
        attrs?.getAttributes(context, R.styleable.DecoratedItemView) {
            icon = getDrawable(R.styleable.DecoratedItemView_icon)
            itemStyle = SelectionStyle.values()[getInteger(R.styleable.DecoratedItemView_itemStyle, 0)]
            maxLines = getInteger(R.styleable.DecoratedItemView_maxLines, DEFAULT_MAX_LINES)
            text = getString(R.styleable.DecoratedItemView_text)
            textWeight = TextWeight.values()[getInteger(R.styleable.DecoratedItemView_textWeight, 0)]
        }
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) {
        binding.root.setOnClickListener(onClickListener)
    }

    open fun setEndIcon(icon: Drawable?, @StringRes contentDescriptionRes: Int?) {
        binding.endIcon.apply {
            isGone = icon == null
            setImageDrawable(icon)
            contentDescription = contentDescriptionRes?.let(context::getString)
        }
    }

    enum class SelectionStyle {
        MENU_DRAWER,
        OTHER,
    }

    enum class TextWeight {
        REGULAR,
        MEDIUM,
    }

    private companion object {
        const val DEFAULT_MAX_LINES = 1
    }
}
