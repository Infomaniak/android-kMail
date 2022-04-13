/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import android.app.Activity
import android.view.View
import androidx.fragment.app.Fragment
import com.infomaniak.mail.R

fun Activity.showSnackbar(
    title: Int,
    anchorView: View? = null,
    actionButtonTitle: Int = R.string.buttonCancel,
    onActionClicked: (() -> Unit)? = null,
) {
    Utils.showSnackbar(
        view = window.decorView.findViewById(android.R.id.content),
        title = title,
        anchorView = anchorView,
        actionButtonTitle = actionButtonTitle,
        onActionClicked = onActionClicked
    )
}

fun Activity.showSnackbar(
    title: String,
    anchorView: View? = null,
    actionButtonTitle: Int = R.string.buttonCancel,
    onActionClicked: (() -> Unit)? = null,
) {
    Utils.showSnackbar(
        view = window.decorView.findViewById(android.R.id.content),
        title = title,
        anchorView = anchorView,
        actionButtonTitle = actionButtonTitle,
        onActionClicked = onActionClicked
    )
}

//fun Fragment.showSnackbar(
//    titleId: Int,
//    showAboveFab: Boolean = false,
//    actionButtonTitle: Int = R.string.buttonCancel,
//    onActionClicked: (() -> Unit)? = null,
//) {
//    showSnackbar(getString(titleId), showAboveFab, actionButtonTitle, onActionClicked)
//}

//fun Fragment.showSnackbar(
//    title: String,
//    showAboveFab: Boolean = false,
//    actionButtonTitle: Int = R.string.buttonCancel,
//    onActionClicked: (() -> Unit)? = null,
//) {
//    activity?.let { it.showSnackbar(title, if (showAboveFab) it.mainFab else null, actionButtonTitle, onActionClicked) }
//}