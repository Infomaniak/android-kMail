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
import androidx.appcompat.content.res.AppCompatResources
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.getAttributeColor
import com.google.android.material.R as RMaterial

abstract class SelectableTextItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : DecoratedTextItemView(context, attrs, defStyleAttr) {

    private var isInSelectedState = false

    private val checkIcon by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_check)?.apply {
            setTint(context.getAttributeColor(RMaterial.attr.colorPrimary))
        }
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

        setEndIcon(if (isSelected) checkIcon else null, R.string.contentDescriptionSelectedItem)
    }
}
