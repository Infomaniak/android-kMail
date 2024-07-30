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

    fun displayMailboxesHeader(
        header: MailboxesHeader,
        binding: ItemMenuDrawerMailboxesHeaderBinding,
        onMailboxesHeaderClicked: () -> Unit,
    ) = with(binding) {
        SentryLog.d("Bind", "Bind Mailboxes header")

        val (mailbox, hasMoreThanOneMailbox, isExpanded) = header

        root.apply {
            if (hasMoreThanOneMailbox) {
                setOnClickListener { onMailboxesHeaderClicked() }
            } else {
                setOnClickListener(null)
            }
            isClickable = hasMoreThanOneMailbox
            isFocusable = hasMoreThanOneMailbox
            setOnClickListener { onMailboxesHeaderClicked() }
        }

        mailboxSwitcherText.text = mailbox?.email
        setMailboxSwitcherTextAppearance(isExpanded)

        mailboxExpandButton.isVisible = hasMoreThanOneMailbox
    }

    fun updateCollapseState(
        header: MailboxesHeader,
        binding: ItemMenuDrawerMailboxesHeaderBinding,
    ) = with(binding) {
        SentryLog.d("Bind", "Bind Mailboxes header because of collapse change")

        mailboxExpandButton.toggleChevron(!header.isExpanded)
        setMailboxSwitcherTextAppearance(header.isExpanded)
    }

    private fun ItemMenuDrawerMailboxesHeaderBinding.setMailboxSwitcherTextAppearance(isOpen: Boolean) {
        mailboxSwitcherText.setTextAppearance(if (isOpen) R.style.BodyMedium_Accent else R.style.BodyMedium)
    }

    data class MailboxesHeader(val mailbox: Mailbox?, val hasMoreThanOneMailbox: Boolean, val isExpanded: Boolean)
}
