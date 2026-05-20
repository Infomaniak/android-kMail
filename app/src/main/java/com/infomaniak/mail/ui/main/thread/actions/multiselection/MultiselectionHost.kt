/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions.multiselection

import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavDirections
import com.infomaniak.dragdropswiperecyclerview.DragDropSwipeRecyclerView.ListOrientation.DirectionFlag
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerAction

interface MultiSelectionHost : LifecycleOwner {
    val multiSelectionBinding: MultiSelectionBinding
    val folderRoleUtils: com.infomaniak.mail.utils.FolderRoleUtils
    val descriptionDialog: com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
    val threadListAdapter: ThreadListAdapter
    fun safeNavigation(directions: NavDirections)
    fun disableSwipeDirection(direction: DirectionFlag)
    fun unlockSwipeActionsIfSet()
    fun directionToThreadActionsBottomSheetDialog(
        threadUid: String,
        shouldLoadDistantResources: Boolean,
        shouldCloseMultiSelection: Boolean,
        isFromSearch: Boolean,
    ): NavDirections

    fun directionsToFolderPickerFragment(
        threadsUids: Array<String>,
        messagesUids: Array<String>? = null,
        action: FolderPickerAction = FolderPickerAction.MOVE,
        sourceFolderId: String? = null
    ): NavDirections

    fun directionsToMultiSelectBottomSheetDialog(isFromSearch: Boolean): NavDirections
}
