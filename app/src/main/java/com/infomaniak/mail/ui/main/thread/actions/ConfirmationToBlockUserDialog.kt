/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.DialogConfirmationToBlockUserBinding
import com.infomaniak.mail.ui.alertDialogs.BaseAlertDialog
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import splitties.init.appCtx
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@ActivityScoped
class ConfirmationToBlockUserDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    private val binding: DialogConfirmationToBlockUserBinding by lazy {
        DialogConfirmationToBlockUserBinding.inflate(activity.layoutInflater)
    }

    private var onPositiveButtonClick: ((Message) -> Unit)? = null
    private var messageOfUserToBlock: Message? = null

    override val alertDialog: AlertDialog = with(binding) {
        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(R.string.buttonConfirm) { _, _ ->
                messageOfUserToBlock?.let { message -> onPositiveButtonClick?.invoke(message) }
            }
            .setNegativeButton(RCore.string.buttonCancel, null)
            .create()
    }

    override fun resetCallbacks() {
        onPositiveButtonClick = null
    }

    fun show(message: Message) = with(binding) {
        messageOfUserToBlock = message
        val recipient = message.from[0]
        val title = recipient.name.ifBlank { recipient.email }
        blockExpeditorTitle.text = activityContext.getString(R.string.blockExpeditorTitle, title)
        blockExpeditorDescription.text = appCtx.getString(R.string.confirmationToBlockAnExpeditorText, recipient.email)
        alertDialog.show()
    }

    fun setPositiveButtonCallback(onPositiveButtonClick: (Message) -> Unit) {
        this.onPositiveButtonClick = onPositiveButtonClick
    }
}
