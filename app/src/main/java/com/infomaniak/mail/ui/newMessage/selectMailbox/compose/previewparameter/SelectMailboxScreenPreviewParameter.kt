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
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UiState
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UserMailboxesUi

class SelectMailboxScreenPreviewParameter : PreviewParameterProvider<SelectMailboxScreenDataPreview> {
    override val values: Sequence<SelectMailboxScreenDataPreview>
        get() = selectMailboxScreenDataPreview.asSequence()
}

data class SelectMailboxScreenDataPreview(
    val usersWithMailboxes: List<UserMailboxesUi>,
    val uiState: UiState,
)

val selectMailboxScreenDataPreview = listOf(
    SelectMailboxScreenDataPreview(
        usersWithMailboxes = usersWithMailboxesPreviewData,
        uiState = UiState.DefaultScreen.Idle(selectedMailboxPreviewData)
    ),
    SelectMailboxScreenDataPreview(
        usersWithMailboxesPreviewData,
        uiState = UiState.SelectionScreen.NoSelection
    ),
    SelectMailboxScreenDataPreview(
        usersWithMailboxesPreviewData,
        uiState = UiState.SelectionScreen.Selected(selectedMailboxPreviewData)
    ),
)
