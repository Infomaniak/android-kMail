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
package com.infomaniak.mail.ui.newMessage.mailbox.compose

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.infomaniak.mail.ui.newMessage.mailbox.MailboxUi
import com.infomaniak.mail.ui.newMessage.mailbox.UserMailboxesUi
import java.util.UUID

class SelectMailboxPreviewParameter : PreviewParameterProvider<List<UserMailboxesUi>> {
    override val values: Sequence<List<UserMailboxesUi>>
        get() = sequenceOf(usersWithMailboxesPreviewData)
}

private val usersWithMailboxesPreviewData = listOf(
    UserMailboxesUi(
        userId = 0,
        userEmail = "chef@infomaniak.com",
        avatarUrl = "https://picsum.photos/id/140/200/200",
        initials = "CH",
        fullName = "Chef",
        mailboxes = listOf(
            MailboxUi(
                mailUuid = UUID.randomUUID().toString(),
                email = "chef@infomaniak.com"
            )
        )
    ),
    UserMailboxesUi(
        userId = 1,
        userEmail = "firstname.lastname@infomaniak.com",
        avatarUrl = "https://picsum.photos/id/140/200/200",
        initials = "FL",
        fullName = "Firstname Listname",
        mailboxes = listOf(
            MailboxUi(
                mailUuid = UUID.randomUUID().toString(),
                email = "firstname.lastname@infomaniak.com"
            )
        )
    ),
    UserMailboxesUi(
        userId = 2,
        userEmail = "android@infomaniak.com",
        avatarUrl = "https://picsum.photos/id/140/200/200",
        initials = "AD",
        fullName = "Android",
        mailboxes = listOf(
            MailboxUi(
                mailUuid = UUID.randomUUID().toString(),
                email = "android@infomaniak.com"
            )
        )
    ),
)
