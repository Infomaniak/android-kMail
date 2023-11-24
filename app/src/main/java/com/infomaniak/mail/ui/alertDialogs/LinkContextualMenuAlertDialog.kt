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
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogLinkContextualMenuBinding
import com.infomaniak.mail.ui.main.SnackBarManager
import com.infomaniak.mail.utils.copyStringToClipboard
import com.infomaniak.mail.utils.shareString
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class LinkContextualMenuAlertDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    private val items = arrayOf(
        activityContext.getString(R.string.linkContextMenuOpen),
        activityContext.getString(R.string.linkContextMenuCopy),
        activityContext.getString(R.string.linkContextMenuShare),
    )

    val binding: DialogLinkContextualMenuBinding by lazy { DialogLinkContextualMenuBinding.inflate(activity.layoutInflater) }
    override val alertDialog: AlertDialog = initDialog()

    private lateinit var snackBarManager: SnackBarManager

    private var lastUrl = ""

    fun initValues(snackBarManager: SnackBarManager) {
        this.snackBarManager = snackBarManager
    }

    override fun initDialog(): AlertDialog = with(binding) {
        MaterialAlertDialogBuilder(context)
            .setCustomTitle(binding.root)
            .setItems(items) { _, index ->
                when (index) {
                    0 -> context.openUrl(lastUrl)
                    1 -> context.copyStringToClipboard(lastUrl, R.string.snackbarLinkCopiedToClipboard, snackBarManager)
                    2 -> context.shareString(lastUrl)
                }
            }
            .create()
    }

    override fun resetCallbacks() = Unit

    fun show(url: String) {
        binding.url.text = url
        lastUrl = url
        alertDialog.show()
    }
}
