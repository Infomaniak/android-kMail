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
package com.infomaniak.mail.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.view.ContextThemeWrapper
import android.view.View
import androidx.annotation.MenuRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.mail.R
import com.infomaniak.lib.core.R as RCore

class SimpleIconPopupMenu(val context: Context, @MenuRes menuRes: Int, anchor: View) {

    private val horizontalIconMargin by lazy {
        context.resources.getDimensionPixelSize(RCore.dimen.marginStandardVerySmall).toPx()
    }

    private val popupMenu: PopupMenu

    init {
        val refineMenuThemedContext = ContextThemeWrapper(context, R.style.AiRefineMenu)
        popupMenu = PopupMenu(refineMenuThemedContext, anchor).also {
            it.menuInflater.inflate(menuRes, it.menu)
            showIconsAndDividers(it)
        }
    }

    private fun showIconsAndDividers(it: PopupMenu) {
        @SuppressLint("RestrictedApi")
        if (it.menu is MenuBuilder) {
            val menuBuilder = (it.menu as MenuBuilder).apply {
                setOptionalIconsVisible(true)
                isGroupDividerEnabled = true
            }

            for (item in menuBuilder.visibleItems) {
                if (item.icon != null) {
                    item.icon = InsetDrawable(item.icon, horizontalIconMargin, 0, horizontalIconMargin, 0).also {
                        it.setTint(context.getColor(R.color.iconColorPrimaryText))
                    }
                }
            }
        }
    }

    fun show() {
        popupMenu.show()
    }
}
