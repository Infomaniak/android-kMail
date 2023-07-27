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
package com.infomaniak.mail.views.decoratedTextItemView

import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewDecoratedTextItemBinding
import com.infomaniak.mail.utils.getAttributeColor
import com.infomaniak.mail.views.decoratedTextItemView.DecoratedTextItemView.SelectionStyle
import com.infomaniak.mail.views.decoratedTextItemView.DecoratedTextItemView.TextWeight

interface SelectableItem {

    val binding: ViewDecoratedTextItemBinding

    fun DecoratedTextItemView.setSelectedState(isSelected: Boolean) = with(binding) {
        val (color, textAppearance) = if (isSelected && itemStyle == SelectionStyle.MENU_DRAWER) {
            context.getAttributeColor(com.google.android.material.R.attr.colorPrimaryContainer) to R.style.BodyMedium_Accent
        } else {
            android.graphics.Color.TRANSPARENT to if (textWeight == TextWeight.MEDIUM) R.style.BodyMedium else R.style.Body
        }

        root.setCardBackgroundColor(color)
        itemName.setTextAppearance(textAppearance)

        setEndIcon(if (isSelected) getCheckmarkIcon() else null, R.string.contentDescriptionSelectedItem)
    }

    fun getCheckmarkIcon(): Drawable? = with(binding) {
        return AppCompatResources.getDrawable(context, R.drawable.ic_check)?.apply {
            setTint(context.getAttributeColor(com.google.android.material.R.attr.colorPrimary))
        }
    }
}
