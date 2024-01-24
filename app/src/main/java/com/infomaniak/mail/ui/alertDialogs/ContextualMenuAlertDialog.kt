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
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.databinding.DialogLinkContextualMenuBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import dagger.hilt.android.qualifiers.ActivityContext

abstract class ContextualMenuAlertDialog(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    protected abstract val items: List<ContextualItem>

    val binding: DialogLinkContextualMenuBinding by lazy { DialogLinkContextualMenuBinding.inflate(activity.layoutInflater) }
    override val alertDialog: AlertDialog by lazy { initDialog() }

    private lateinit var snackbarManager: SnackbarManager

    private var dataToProcess = ""

    fun initValues(snackbarManager: SnackbarManager) {
        this.snackbarManager = snackbarManager
    }

    private fun initDialog(): AlertDialog = with(binding) {
        val stringItems = items.map { context.getString(it.titleRes) }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setCustomTitle(binding.root)
            .setItems(stringItems) { _, index -> items[index].onClick(dataToProcess, snackbarManager) }
            .create()
    }

    override fun resetCallbacks() = Unit

    fun show(data: String) {
        binding.title.text = data
        dataToProcess = data
        alertDialog.show()
    }

    data class ContextualItem(
        @StringRes val titleRes: Int,
        val onClick: (data: String, snackbarManager: SnackbarManager) -> Unit
    )
}
