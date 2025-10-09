/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.TextViewCompat
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.utils.extensions.getAttributeColor
import androidx.appcompat.R as RAndroid
import com.google.android.material.R as RMaterial

sealed class SelectableItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : DecoratedItemView(context, attrs, defStyleAttr) {

    private val checkIcon by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_check)?.apply {
            setTint(context.getAttributeColor(RAndroid.attr.colorPrimary))
        }
    }

    fun setSelectedState(isSelected: Boolean) = with(binding) {
        val (backgroundColor, decoratorsColor, textColor, textAppearance) = if (isSelected && itemStyle == SelectionStyle.MENU_DRAWER) {
            ColorState(
                backgroundColor = context.getAttributeColor(RMaterial.attr.colorPrimaryContainer),
                decoratorsColor = context.getAttributeColor(RMaterial.attr.colorOnPrimaryContainer),
                textColor = context.getAttributeColor(RMaterial.attr.colorOnPrimaryContainer),
                textAppearance = R.style.BodyMedium,
            )
        } else {
            ColorState(
                backgroundColor = Color.TRANSPARENT,
                decoratorsColor = context.getAttributeColor(RAndroid.attr.colorPrimary),
                textColor = context.getColor(R.color.primaryTextColor),
                textAppearance = if (textWeight == TextWeight.MEDIUM) R.style.BodyMedium else R.style.Body,
            )
        }

        root.setCardBackgroundColor(backgroundColor)
        itemName.setTextAppearance(textAppearance)
        itemName.setTextColor(textColor)
        val decoratorStateList = ColorStateList.valueOf(decoratorsColor)
        TextViewCompat.setCompoundDrawableTintList(itemName, decoratorStateList)
        endIcon.imageTintList = decoratorStateList
        unreadCountChip.setTextColor(decoratorsColor)

        setEndIcon(if (isSelected) checkIcon else null, R.string.contentDescriptionSelectedItem)
    }
}

fun SelectableItemView.setFolderUi(folder: Folder, @DrawableRes iconId: Int, isSelected: Boolean) {
    text = folder.getLocalizedName(context)
    icon = AppCompatResources.getDrawable(context, iconId)
    setSelectedState(isSelected)
}

private data class ColorState(
    @ColorInt val backgroundColor: Int,
    @ColorInt val decoratorsColor: Int,
    @ColorInt val textColor: Int,
    @StyleRes val textAppearance: Int,
)
