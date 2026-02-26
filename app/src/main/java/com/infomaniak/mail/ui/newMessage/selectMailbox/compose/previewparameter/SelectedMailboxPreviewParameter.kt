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
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.SelectedMailboxUi

class SelectedMailboxPreviewParameter : PreviewParameterProvider<SelectedMailboxUi> {
    override val values: Sequence<SelectedMailboxUi>
        get() = sequenceOf(selectedMailboxPreviewData)
}

val selectedMailboxPreviewData = SelectedMailboxUi(
    userId = 0,
    avatarUrl = "https://picsum.photos/id/110/200/200",
    initials = "CH",
    mailboxUi = MailboxUi(mailboxId = 0, emailIdn = "chef@infomaniak.com")
)
