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
import androidx.viewbinding.ViewBinding
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.databinding.ItemMenuDrawerMailboxBinding

object MailboxItem {

    @Suppress("MayBeConstant")
    val viewType = R.layout.item_menu_drawer_mailbox

    fun binding(inflater: LayoutInflater, parent: ViewGroup): ViewBinding {
        return ItemMenuDrawerMailboxBinding.inflate(inflater, parent, false)
    }

    fun display(
        item: Any,
        binding: ViewBinding,
        onValidMailboxClicked: (Int) -> Unit,
    ) {
        item as Mailbox
        binding as ItemMenuDrawerMailboxBinding

        SentryLog.d("Bind", "Bind Mailbox (${item.email})")
        binding.displayMailbox(item, onValidMailboxClicked)
    }

    private fun ItemMenuDrawerMailboxBinding.displayMailbox(
        mailbox: Mailbox,
        onValidMailboxClicked: (Int) -> Unit,
    ) = with(root) {
        text = mailbox.email
        unreadCount = mailbox.unreadCountDisplay.count
        isPastilleDisplayed = mailbox.unreadCountDisplay.shouldDisplayPastille

        setOnClickListener {
            context.trackMenuDrawerEvent(MatomoMail.SWITCH_MAILBOX_NAME)
            onValidMailboxClicked(mailbox.mailboxId)
        }
    }
}
