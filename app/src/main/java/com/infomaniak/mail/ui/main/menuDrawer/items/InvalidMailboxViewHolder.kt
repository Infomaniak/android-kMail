/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.databinding.ItemInvalidMailboxBinding
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.MenuDrawerViewHolder
import com.infomaniak.mail.views.itemViews.DecoratedItemView

class InvalidMailboxViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
) : MenuDrawerViewHolder(ItemInvalidMailboxBinding.inflate(inflater, parent, false)) {

    override val binding = super.binding as ItemInvalidMailboxBinding

    fun displayInvalidMailbox(
        mailbox: Mailbox,
        onInvalidMailboxClicked: (String) -> Unit,
    ) = with(binding.root) {
        SentryLog.d("Bind", "Bind Invalid Mailbox (${mailbox.email})")

        text = mailbox.emailIdn
        itemStyle = DecoratedItemView.SelectionStyle.MENU_DRAWER
        hasNoValidMailboxes = false

        computeEndIconVisibility()

        initSetOnClickListener(onInvalidMailboxClicked = { onInvalidMailboxClicked(mailbox.emailIdn) })
    }
}
