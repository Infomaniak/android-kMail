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
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.getAttributeColor
import com.google.android.material.R as RMaterial

abstract class UnreadItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SelectableItemView(context, attrs, defStyleAttr) {

    private val pastille by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_pastille)?.apply {
            setTint(context.getAttributeColor(RMaterial.attr.colorPrimary))
        }
    }

    var unreadCount: Int = 0
        set(value) {
            field = value
            binding.unreadCountChip.apply {
                text = UiUtils.formatUnreadCount(unreadCount)
                isVisible = !isPastilleDisplayed && unreadCount > 0
            }
        }

    var isPastilleDisplayed = false
        set(isDisplayed) {
            field = isDisplayed
            setEndIcon(if (isDisplayed) pastille else null, R.string.contentDescriptionUnreadPastille)
        }

    init {
        attrs?.getAttributes(context, R.styleable.DecoratedItemView) {
            unreadCount = getInteger(R.styleable.DecoratedItemView_badge, unreadCount)
        }
    }
}
