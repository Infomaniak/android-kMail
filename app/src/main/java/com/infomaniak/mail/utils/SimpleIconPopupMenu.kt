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

import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.view.ContextThemeWrapper
import android.view.View
import androidx.annotation.MenuRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.PopupMenu
import com.infomaniak.mail.R
import com.infomaniak.lib.core.R as RCore

class SimpleIconPopupMenu(val context: Context, @MenuRes menuRes: Int, anchor: View, onMenuItemClicked: (Int) -> Unit) {

    private val horizontalIconMargin by lazy {
        context.resources.getDimensionPixelSize(RCore.dimen.marginStandardVerySmall)
    }

    private val popupMenu: PopupMenu

    init {
        val refineMenuThemedContext = ContextThemeWrapper(context, R.style.AiRefineMenu)
        popupMenu = PopupMenu(refineMenuThemedContext, anchor).also {
            it.menuInflater.inflate(menuRes, it.menu)
            showIconsAndDividers(it)
            it.setOnMenuItemClickListener { menuItem ->
                onMenuItemClicked(menuItem.itemId)
                true
            }
        }
    }

    private fun showIconsAndDividers(popupMenu: PopupMenu) {
        @Suppress("RestrictedApi")
        (popupMenu.menu as? MenuBuilder)?.apply {
            setOptionalIconsVisible(true)
            isGroupDividerEnabled = true

            visibleItems.forEach { item ->
                item.setIconStyle(DEFAULT_ICON_COLOR, horizontalIconMargin)
            }
        }
    }

    private fun MenuItemImpl.setIconStyle(iconColorRes: Int, margin: Int) {
        @Suppress("RestrictedApi")
        if (icon != null) {
            icon = InsetDrawable(icon, margin, 0, margin, 0).also {
                it.setTint(context.getColor(iconColorRes))
            }
        }
    }

    fun show() {
        popupMenu.show()
    }

    fun disableAllItemButModify() {
        setEnabledItems(setOf(R.id.modify))
    }

    fun enableAllItems() {
        setEnabledItems()
    }

    private fun setEnabledItems(enabledItems: Set<Int>? = null) {
        @Suppress("RestrictedApi")
        (popupMenu.menu as? MenuBuilder)?.visibleItems?.forEach { item ->
            val shouldStayEnabled = enabledItems?.contains(item.itemId) ?: true
            item.isEnabled = shouldStayEnabled
            item.setIconStyle(if (shouldStayEnabled) DEFAULT_ICON_COLOR else DISABLED_ICON_COLOR, 0)
        }
    }

    companion object {
        private val DEFAULT_ICON_COLOR = R.color.iconColorPrimaryText
        private val DISABLED_ICON_COLOR = R.color.iconColorTertiaryText
    }
}
