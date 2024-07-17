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
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.databinding.ItemMenuDrawerMailboxesHeaderBinding
import com.infomaniak.mail.utils.extensions.toggleChevron

object MailboxesHeaderItem {

    @Suppress("MayBeConstant")
    val viewType = R.layout.item_menu_drawer_mailboxes_header

    fun binding(inflater: LayoutInflater, parent: ViewGroup): ViewBinding {
        return ItemMenuDrawerMailboxesHeaderBinding.inflate(inflater, parent, false)
    }

    fun display(
        item: Any,
        binding: ViewBinding,
        onMailboxesHeaderClicked: () -> Unit,
    ) {
        item as MailboxesHeader
        binding as ItemMenuDrawerMailboxesHeaderBinding

        SentryLog.d("Bind", "Bind Mailboxes header")
        binding.displayMailboxesHeader(item, onMailboxesHeaderClicked)
    }

    fun displayWithPayload(
        item: Any,
        binding: ViewBinding,
    ) {
        item as MailboxesHeader
        binding as ItemMenuDrawerMailboxesHeaderBinding

        SentryLog.d("Bind", "Bind Mailboxes header because of collapse change")
        binding.updateCollapseState(item)
    }

    private fun ItemMenuDrawerMailboxesHeaderBinding.displayMailboxesHeader(
        header: MailboxesHeader,
        onMailboxesHeaderClicked: () -> Unit,
    ) = with(header) {
        mailboxSwitcherText.text = mailbox?.email

        mailboxSwitcher.apply {
            isClickable = hasMoreThanOneMailbox
            isFocusable = hasMoreThanOneMailbox
        }

        mailboxExpandButton.isVisible = hasMoreThanOneMailbox

        setMailboxSwitcherTextAppearance(isExpanded)

        root.setOnClickListener { onMailboxesHeaderClicked() }
    }

    private fun ItemMenuDrawerMailboxesHeaderBinding.updateCollapseState(header: MailboxesHeader) = with(header) {
        mailboxExpandButton.toggleChevron(!isExpanded)
        setMailboxSwitcherTextAppearance(isExpanded)
    }

    private fun ItemMenuDrawerMailboxesHeaderBinding.setMailboxSwitcherTextAppearance(isOpen: Boolean) {
        mailboxSwitcherText.setTextAppearance(if (isOpen) R.style.BodyMedium_Accent else R.style.BodyMedium)
    }

    data class MailboxesHeader(val mailbox: Mailbox?, val hasMoreThanOneMailbox: Boolean, val isExpanded: Boolean)
}
