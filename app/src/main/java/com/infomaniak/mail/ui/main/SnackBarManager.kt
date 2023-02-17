/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar

class SnackBarManager {
    private val snackBarFeedback = SingleLiveEvent<Pair<String, UndoData?>>()

    fun setup(
        activity: FragmentActivity,
        getAnchor: (() -> View?)? = null,
        onActionClicked: ((data: UndoData) -> Unit)? = null
    ) {
        snackBarFeedback.observe(activity) { (title, undoData) ->
            activity.showSnackbar(
                title,
                getAnchor?.invoke(),
                onActionClicked = undoData?.let { data -> { onActionClicked?.invoke(data) } },
            )
        }
    }

    fun setValue(title: String, undoData: UndoData? = null) {
        snackBarFeedback.value = title to undoData
    }

    fun postValue(title: String, undoData: UndoData? = null) {
        snackBarFeedback.postValue(title to undoData)
    }

    data class UndoData(
        val resource: String,
        val foldersIds: List<String>,
        val destinationFolderId: String?,
    )
}
