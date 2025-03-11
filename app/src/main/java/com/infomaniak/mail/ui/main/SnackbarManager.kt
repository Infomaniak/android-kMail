/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import javax.inject.Inject
import javax.inject.Singleton
import com.infomaniak.lib.core.R as RCore

@Singleton
class SnackbarManager @Inject constructor() {

    private val snackbarFeedback = SingleLiveEvent<SnackbarData>()
    private var previousSnackbar: Snackbar? = null

    // Give a CoordinatorLayout to `view` in order to support swipe to dismiss
    fun setup(
        view: View,
        activity: FragmentActivity,
        getAnchor: (() -> View?)? = null,
        onUndoData: ((data: UndoData) -> Unit)? = null,
    ) = with(snackbarFeedback) {
        removeObservers(activity)
        observe(activity) { (title, undoData, buttonTitleRes, customBehavior) ->
            val action: (() -> Unit)? = if (undoData != null) {
                { onUndoData?.invoke(undoData) }
            } else {
                customBehavior
            }
            val safeAction = getSafeAction(action)

            val buttonTitle = buttonTitleRes ?: RCore.string.buttonCancel

            previousSnackbar?.dismiss()
            previousSnackbar = showSnackbar(
                view = view,
                title = title,
                anchor = getAnchor?.invoke(),
                actionButtonTitle = buttonTitle,
                onActionClicked = safeAction,
            )
        }
    }

    private fun getSafeAction(action: (() -> Unit)?): (() -> Unit)? {
        return action?.let {
            var neverClicked = true
            {
                if (neverClicked) {
                    neverClicked = false
                    action()
                }
            }
        }
    }

    fun setValue(title: String, undoData: UndoData? = null, buttonTitle: Int? = null, customBehavior: (() -> Unit)? = null) {
        snackbarFeedback.value = SnackbarData(title, undoData, buttonTitle, customBehavior)
    }

    fun postValue(title: String, undoData: UndoData? = null, buttonTitle: Int? = null, customBehavior: (() -> Unit)? = null) {
        snackbarFeedback.postValue(SnackbarData(title, undoData, buttonTitle, customBehavior))
    }

    private data class SnackbarData(
        val title: String,
        val undoData: UndoData?,
        @StringRes val buttonTitle: Int?,
        val customBehavior: (() -> Unit)?,
    )

    data class UndoData(
        val resources: List<String>,
        val foldersIds: ImpactedFolders,
        val destinationFolderId: String?,
    )
}
