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
package com.infomaniak.mail.ui.main.menuDrawer

import android.view.View
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.ui.main.menuDrawer.items.ActionViewHolder.MenuDrawerAction.ActionType

interface MenuDrawerAdapterCallbacks {

    var onMailboxesHeaderClicked: () -> Unit

    var onValidMailboxClicked: (Int) -> Unit
    var onLockedMailboxClicked: (String) -> Unit
    var onInvalidPasswordMailboxClicked: (Mailbox) -> Unit

    var onFoldersHeaderClicked: (Boolean) -> Unit
    var onCreateFolderClicked: () -> Unit

    var onFolderClicked: (folderId: String) -> Unit
    var onFolderLongClicked: (folderId: String, folderName: String, view: View) -> Unit
    var onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit

    var onActionsHeaderClicked: () -> Unit
    var onActionClicked: (ActionType) -> Unit

    var onFeedbackClicked: () -> Unit
    var onHelpClicked: () -> Unit
    var onAppVersionClicked: () -> Unit
}
