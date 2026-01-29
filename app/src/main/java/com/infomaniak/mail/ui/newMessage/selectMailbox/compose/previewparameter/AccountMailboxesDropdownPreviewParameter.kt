/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage.selectMailbox.compose.previewparameter

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.MailboxUi
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UserMailboxesUi

class AccountMailboxesDropdownPreviewParameter : PreviewParameterProvider<UserMailboxesUi> {
    override val values: Sequence<UserMailboxesUi>
        get() = usersWithMailboxesPreviewData.asSequence()
}

val usersWithMailboxesPreviewData = listOf(
    UserMailboxesUi(
        userId = 0,
        userEmail = "chef@infomaniak.com",
        avatarUrl = "https://picsum.photos/id/120/200/200",
        initials = "CH",
        fullName = "Chef",
        mailboxesUi = listOf(
            MailboxUi(mailboxId = 0, emailIdn = "chef-1@infomaniak.com"),
            MailboxUi(mailboxId = 1, emailIdn = "chef-2@infomaniak.com"),
            MailboxUi(mailboxId = 2, emailIdn = "chef-3@infomaniak.com")
        )
    ),
    UserMailboxesUi(
        userId = 1,
        userEmail = "firstname.lastnameeeeeeeeeeeeee@infomaniak.com",
        avatarUrl = "https://picsum.photos/id/130/200/200",
        initials = "FL",
        fullName = "Firstname Listname",
        mailboxesUi = listOf(MailboxUi(mailboxId = 0, emailIdn = "firstname.lastnameeeeeeeeeeeeee@infomaniak.com"))
    ),
    UserMailboxesUi(
        userId = 2,
        userEmail = "android@infomaniak.com",
        avatarUrl = "https://picsum.photos/id/140/200/200",
        initials = "AD",
        fullName = "Android",
        mailboxesUi = listOf(MailboxUi(mailboxId = 0, emailIdn = "android@infomaniak.com"))
    ),
)
