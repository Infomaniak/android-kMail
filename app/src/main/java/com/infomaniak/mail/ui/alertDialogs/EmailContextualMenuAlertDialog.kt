/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
import android.net.Uri
import android.webkit.WebView
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.utils.extensions.copyStringToClipboard
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class EmailContextualMenuAlertDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : ContextualMenuAlertDialog(activityContext) {

    override val items = listOf(
        ContextualItem(R.string.contextMenuEmailOpen) { email, _ ->
            (activityContext as MainActivity).navigateToNewMessageActivity(
                NewMessageActivityArgs(
                    mailToUri = Uri.parse(WebView.SCHEME_MAILTO + email),
                ).toBundle(),
            )
        },
        ContextualItem(R.string.contextMenuEmailCopy) { email, snackbarManager ->
            activityContext.copyStringToClipboard(email, R.string.snackbarEmailCopiedToClipboard, snackbarManager)
        },
    )
}
