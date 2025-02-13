/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.setPaddingRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ItemBottomSheetActionBinding
import com.infomaniak.mail.utils.AccountUtils

class ActionItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ItemBottomSheetActionBinding.inflate(LayoutInflater.from(context), this, true) }

    private var isInIconMode: Boolean = false

    init {
        attrs?.getAttributes(context, R.styleable.ActionItemView) {
            with(binding) {
                icon.setImageDrawable(getDrawable(R.styleable.ActionItemView_icon))
                getColorStateList(R.styleable.ActionItemView_iconColor)?.let(::setIconTint)

                title.text = getString(R.styleable.ActionItemView_title)
                getColorStateList(R.styleable.ActionItemView_titleColor)?.let(::setTitleColor)

                val iconHorizontalPadding = getDimenOrNull(R.styleable.ActionItemView_iconPaddingHorizontal)
                val iconPaddingStart = iconHorizontalPadding ?: getDimenOrNull(R.styleable.ActionItemView_iconPaddingStart)
                val iconPaddingEnd = iconHorizontalPadding ?: getDimenOrNull(R.styleable.ActionItemView_iconPaddingEnd)
                container.setPaddingRelative(start = iconPaddingStart, end = iconPaddingEnd)

                divider.apply {
                    isVisible = getBoolean(R.styleable.ActionItemView_visibleDivider, true)
                    dividerColor = getColor(R.styleable.ActionItemView_dividerColor, context.getColor(R.color.dividerColor))
                }

                if (getBoolean(R.styleable.ActionItemView_staffOnly, false)) {
                    if (isInEditMode || AccountUtils.currentUser?.isStaff == true) {
                        icon.imageTintList = AppCompatResources.getColorStateList(context, R.color.staffOnlyColor)
                        title.setTextColor(AppCompatResources.getColorStateList(context, R.color.staffOnlyColor))
                    } else {
                        isGone = true
                    }
                }

                if (getBoolean(R.styleable.ActionItemView_keepIconTint, false)) icon.imageTintList = null

                isInIconMode = getBoolean(R.styleable.ActionItemView_showActionIcon, isInIconMode)
                if (isInIconMode) {
                    description.isGone = true
                    actionIcon.isVisible = true
                }
            }
        }
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) {
        binding.root.setOnClickListener(onClickListener)
    }

    fun setIconResource(@DrawableRes iconResourceId: Int) = binding.icon.setImageResource(iconResourceId)

    private fun setIconTint(color: ColorStateList) {
        binding.icon.imageTintList = color
    }

    fun setTitle(@StringRes textResourceId: Int) = binding.title.setText(textResourceId)

    private fun setTitleColor(color: ColorStateList) = binding.title.setTextColor(color)

    fun setDescription(text: String) = with(binding) {
        if (isInIconMode) return@with

        actionIcon.isGone = true

        description.text = text
        description.isVisible = true
    }

    fun setDividerVisibility(isVisible: Boolean) {
        binding.divider.isVisible = isVisible
    }

    private fun TypedArray.getDimenOrNull(@StyleableRes index: Int): Int? {
        return getDimensionPixelSize(index, NOT_SET).takeIf { it != NOT_SET }
    }

    companion object {
        private const val NOT_SET = -1
    }
}
