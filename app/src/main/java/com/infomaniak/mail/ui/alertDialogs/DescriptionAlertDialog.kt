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
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogDescriptionBinding
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@ActivityScoped
open class DescriptionAlertDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    val binding: DialogDescriptionBinding by lazy { DialogDescriptionBinding.inflate(activity.layoutInflater) }

    override val alertDialog = initDialog()

    private var onPositiveButtonClicked: (() -> Unit)? = null
    private var onNegativeButtonClicked: (() -> Unit)? = null
    private var onDismissed: (() -> Unit)? = null

    protected fun initDialog(customThemeRes: Int? = null) = with(binding) {
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
        description: CharSequence?,
        displayLoader: Boolean = true,
        displayCancelButton: Boolean = true,
        @StringRes positiveButtonText: Int? = R.string.buttonConfirm,
        @StringRes negativeButtonText: Int? = null,
        onPositiveButtonClicked: () -> Unit,
        onNegativeButtonClicked: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ) {
        showDialogWithBasicInfo(title, description, displayCancelButton, positiveButtonText, negativeButtonText)
        if (displayLoader) initProgress()
        setupListeners(displayLoader, onPositiveButtonClicked, onNegativeButtonClicked, onDismiss)
    }

    private fun setupListeners(
        displayLoader: Boolean,
        onPositiveButtonClicked: () -> Unit,
        onNegativeButtonClicked: (() -> Unit)?,
        onDismiss: (() -> Unit)?,
    ) = with(alertDialog) {

        this@DescriptionAlertDialog.onPositiveButtonClicked = onPositiveButtonClicked
        this@DescriptionAlertDialog.onNegativeButtonClicked = onNegativeButtonClicked

        positiveButton.setOnClickListener {
            this@DescriptionAlertDialog.onPositiveButtonClicked?.invoke()
            if (displayLoader) startLoading() else dismiss()
        }
        negativeButton.setOnClickListener {
            this@DescriptionAlertDialog.onNegativeButtonClicked?.invoke()
            dismiss()
        }

        onDismiss?.let {
            onDismissed = it
            setOnDismissListener { onDismissed?.invoke() }
        }
    }

    fun showDeletePermanentlyDialog(
        deletedCount: Int,
        displayLoader: Boolean,
        onPositiveButtonClicked: () -> Unit,
        onDismissed: (() -> Unit)? = null,
    ) = show(
        title = activityContext.resources.getQuantityString(
            R.plurals.threadListDeletionConfirmationAlertTitle,
            deletedCount,
            deletedCount,
        ),
        description = activityContext.resources.getQuantityString(
            R.plurals.threadListDeletionConfirmationAlertDescription,
            deletedCount,
        ),
        displayLoader = displayLoader,
        onPositiveButtonClicked = onPositiveButtonClicked,
        onDismiss = onDismissed,
    )

    protected fun showDialogWithBasicInfo(
        title: String? = null,
        description: CharSequence? = null,
        displayCancelButton: Boolean = true,
        @StringRes positiveButtonText: Int? = null,
        @StringRes negativeButtonText: Int? = null,
    ) = with(binding) {

        alertDialog.show()

        title?.let(dialogTitle::setText)
        dialogDescription.apply {
            isGone = description == null
            text = description
        }

        negativeButton.isVisible = displayCancelButton
        positiveButtonText?.let(positiveButton::setText)
        negativeButtonText?.let(negativeButton::setText)
    }
}
