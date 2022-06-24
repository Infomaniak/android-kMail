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
import com.infomaniak.lib.core.utils.day
import com.infomaniak.lib.core.utils.month
import com.infomaniak.lib.core.utils.year
import com.infomaniak.mail.R
import io.realm.kotlin.types.RealmInstant
import java.util.*

// TODO Put these methods in Core

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

fun RealmInstant.toDate(): Date = Date(epochSeconds * 1_000L + nanosecondsOfSecond / 1_000L)

fun Date.toRealmInstant(): RealmInstant {
    val seconds = time / 1_000L
    val nanoseconds = (time - seconds * 1_000L).toInt()
    return RealmInstant.from(seconds, nanoseconds)
}

fun Date.isToday(): Boolean = Date().let { now -> year() == now.year() && month() == now.month() && day() == now.day() }
