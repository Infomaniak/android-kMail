/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.ui.alertDialogs

import android.content.Context
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.extensions.copyStringToClipboard
import com.infomaniak.mail.utils.extensions.shareString
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class LinkContextualMenuAlertDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : ContextualMenuAlertDialog(activityContext) {

    override val items = listOf(
        ContextualItem(R.string.contextMenuLinkOpen) { url, _ ->
            activityContext.openUrl(url)
        },
        ContextualItem(R.string.contextMenuLinkCopy) { url, snackbarManager ->
            activityContext.copyStringToClipboard(url, R.string.snackbarLinkCopiedToClipboard, snackbarManager)
        },
        ContextualItem(R.string.contextMenuLinkShare) { url, _ ->
            activityContext.shareString(url)
        },
    )
}
