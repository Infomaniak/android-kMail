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
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
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
) : BaseAlertDialog(activityContext), DefaultLifecycleObserver {

    override val binding: DialogDescriptionBinding by lazy { DialogDescriptionBinding.inflate(activity.layoutInflater) }

    override val alertDialog = initDialog()

    private var onPositiveButtonClicked: (() -> Unit)? = null
    private var onDismissed: (() -> Unit)? = null

    private var lifecycle: Lifecycle? = null

    final override fun initDialog() = with(binding) {
        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(R.string.buttonConfirm, null)
            .setNegativeButton(RCore.string.buttonCancel, null)
            .create()
    }

    /**
     * This method needs to be called to avoid memory leaks for any use of callback
     *
     * This object is activity scoped but the callback are given by fragments, hence the need to clear them at the end of the
     * fragments' viewLifecycle
     *
     * @param viewLifecycleOwner The viewLifecycle of the Fragment
     */
    fun bindAlertToLifecycle(viewLifecycleOwner: LifecycleOwner) {
        lifecycle = viewLifecycleOwner.lifecycle
        lifecycle?.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        resetCallbacks()
        lifecycle?.removeObserver(this)
    }

    final override fun resetCallbacks() {
        onPositiveButtonClicked = null
        onDismissed = null
    }

    fun show(
        title: String,
        description: CharSequence?,
        @StringRes confirmButtonText: Int? = R.string.buttonConfirm,
        displayCancelButton: Boolean = true,
        displayLoader: Boolean = true,
        onPositiveButtonClicked: () -> Unit,
        onDismiss: (() -> Unit)? = null,
    ) = with(alertDialog) {
        showDialogWithBasicInfo(title, description, confirmButtonText, displayCancelButton)

        if (displayLoader) initProgress()

        onDismiss?.let {
            onDismissed = it
            setOnDismissListener { onDismissed?.invoke() }
        }

        this@DescriptionAlertDialog.onPositiveButtonClicked = onPositiveButtonClicked

        positiveButton.setOnClickListener {
            this@DescriptionAlertDialog.onPositiveButtonClicked?.invoke()
            if (displayLoader) startLoading() else dismiss()
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
        @StringRes confirmButtonText: Int? = null,
        displayCancelButton: Boolean = true,
    ): Unit = with(binding) {
        alertDialog.show()

        title?.let(binding.dialogTitle::setText)
        description?.let(binding.dialogDescription::setText)
        confirmButtonText?.let(positiveButton::setText)
        negativeButton.isVisible = displayCancelButton
    }
}
