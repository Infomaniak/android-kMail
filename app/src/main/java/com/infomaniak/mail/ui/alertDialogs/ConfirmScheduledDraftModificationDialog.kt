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
package com.infomaniak.mail.ui.alertDialogs

import android.content.Context
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogConfirmScheduledDraftModificationBinding
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@ActivityScoped
open class ConfirmScheduledDraftModificationDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    val binding: DialogConfirmScheduledDraftModificationBinding by lazy {
        DialogConfirmScheduledDraftModificationBinding.inflate(
            activity.layoutInflater
        )
    }

    override val alertDialog = initDialog()

    private var onPositiveButtonClicked: (() -> Unit)? = null
    private var onNegativeButtonClicked: (() -> Unit)? = null
    private var onDismissed: (() -> Unit)? = null

    private fun initDialog(customThemeRes: Int? = null) = with(binding) {

        val builder = customThemeRes?.let { MaterialAlertDialogBuilder(context, it) } ?: MaterialAlertDialogBuilder(context)

        builder
            .setView(root)
            .setPositiveButton(R.string.buttonConfirm, null)
            .setNegativeButton(RCore.string.buttonCancel, null)
            .create()
    }

    final override fun resetCallbacks() {
        onPositiveButtonClicked = null
        onNegativeButtonClicked = null
        onDismissed = null
    }

    fun show(
        title: String,
        description: String,
        onPositiveButtonClicked: () -> Unit,
        onNegativeButtonClicked: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ) {
        showDialogWithBasicInfo(title, description, R.string.buttonModify)
        setupListeners(onPositiveButtonClicked, onNegativeButtonClicked, onDismiss)
    }

    private fun setupListeners(
        onPositiveButtonClicked: () -> Unit,
        onNegativeButtonClicked: (() -> Unit)?,
        onDismiss: (() -> Unit)?,
    ) = with(alertDialog) {

        this@ConfirmScheduledDraftModificationDialog.onPositiveButtonClicked = onPositiveButtonClicked
        this@ConfirmScheduledDraftModificationDialog.onNegativeButtonClicked = onNegativeButtonClicked

        positiveButton.setOnClickListener {
            this@ConfirmScheduledDraftModificationDialog.onPositiveButtonClicked?.invoke()
            dismiss()
        }

        negativeButton.setOnClickListener {
            this@ConfirmScheduledDraftModificationDialog.onNegativeButtonClicked?.invoke()
            dismiss()
        }

        onDismiss.let {
            onDismissed = it
            setOnDismissListener { onDismissed?.invoke() }
        }
    }

    private fun showDialogWithBasicInfo(
        title: String? = null,
        description: String? = null,
        @StringRes positiveButtonText: Int? = null,
        @StringRes negativeButtonText: Int? = null,
    ) = with(binding) {

        alertDialog.show()

        title?.let(dialogDescriptionLayout.dialogTitle::setText)
        description?.let(dialogDescriptionLayout.dialogDescription::setText)

        positiveButtonText?.let(positiveButton::setText)
        negativeButtonText?.let(negativeButton::setText)
    }
}
