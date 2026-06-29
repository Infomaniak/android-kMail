/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogSelectVisibilityReminderBinding
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class SelectVisibilityReminderDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {
    private val binding: DialogSelectVisibilityReminderBinding by lazy {
        DialogSelectVisibilityReminderBinding.inflate(activity.layoutInflater)
    }

    override val alertDialog = initDialog()

    private var isRecipientsAndMeSelected: Boolean = true

    private var onVisibilitySelected: ((Boolean) -> Unit)? = null

    private fun initDialog() = with(binding) {
        MaterialAlertDialogBuilder(activityContext)
            .setTitle(R.string.reminderVisibilityTitle)
            .setView(root)
            .setPositiveButton(R.string.buttonConfirm, null)
            .setNegativeButton(com.infomaniak.core.legacy.R.string.buttonCancel, null)
            .create()
    }

    fun show(
        selectRecipientsAndMe: Boolean = true,
        onVisibilitySelected: (Boolean) -> Unit,
    ) {
        this.onVisibilitySelected = onVisibilitySelected
        alertDialog.show()
        setupRadioGroup(selectRecipientsAndMe)
        setupListeners()
    }

    override fun resetCallbacks() {
        onVisibilitySelected = null
    }

    private fun setupRadioGroup(selectRecipientsAndMe: Boolean) = with(binding) {
        val defaultSelection = if (selectRecipientsAndMe) {
            R.id.selectionReminderRecipientsAndMe
        } else {
            R.id.selectionReminderMeOnly
        }
        reminderVisibilityGroup.check(defaultSelection)
    }

    private fun setupListeners() = with(binding) {
        reminderVisibilityGroup.onItemCheckedListener { id, _, _ ->
            this@SelectVisibilityReminderDialog.isRecipientsAndMeSelected = (id == R.id.selectionReminderRecipientsAndMe)
        }

        positiveButton.setOnClickListener {
            onVisibilitySelected?.invoke(this@SelectVisibilityReminderDialog.isRecipientsAndMeSelected)
            alertDialog.dismiss()
        }

        negativeButton.setOnClickListener { alertDialog.cancel() }
    }
}
