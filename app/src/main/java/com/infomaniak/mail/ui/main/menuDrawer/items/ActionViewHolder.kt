/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.menuDrawer.items

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.databinding.ItemMenuDrawerActionBinding
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.MenuDrawerViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.items.ActionViewHolder.MenuDrawerAction.ActionType

class ActionViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
) : MenuDrawerViewHolder(ItemMenuDrawerActionBinding.inflate(inflater, parent, false)) {

    fun displayAction(
        action: MenuDrawerAction,
        onActionClicked: (ActionType) -> Unit,
    ) {
        SentryLog.d("Bind", "Bind Action : ${action.type.name}")

        (binding as ItemMenuDrawerActionBinding).root.apply {
            icon = AppCompatResources.getDrawable(context, action.icon)
            text = context.getString(action.text)
            maxLines = action.maxLines
            setOnClickListener { onActionClicked(action.type) }
        }
    }

    data class MenuDrawerAction(
        val type: ActionType,
        @DrawableRes val icon: Int,
        @StringRes val text: Int,
        val maxLines: Int,
    ) {

        enum class ActionType {
            SYNC_AUTO_CONFIG,
            IMPORT_MAILS,
            RESTORE_MAILS,
        }
    }
}
