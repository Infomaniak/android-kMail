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
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.databinding.DialogLinkContextualMenuBinding
import com.infomaniak.mail.ui.main.SnackBarManager
import dagger.hilt.android.qualifiers.ActivityContext

abstract class ContextualMenuAlertDialog(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    protected abstract val items: List<Pair<Int, (String, SnackBarManager) -> Unit>>

    val binding: DialogLinkContextualMenuBinding by lazy { DialogLinkContextualMenuBinding.inflate(activity.layoutInflater) }
    override val alertDialog: AlertDialog by lazy { initDialog() }

    private lateinit var snackBarManager: SnackBarManager

    private var lastData = ""

    fun initValues(snackBarManager: SnackBarManager) {
        this.snackBarManager = snackBarManager
    }

    private fun initDialog(): AlertDialog = with(binding) {
        val stringItems = items.map { context.getString(it.first) }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setCustomTitle(binding.root)
            // TODO : Name items correctly and simplify map/toTypedArray
            .setItems(stringItems) { _, index -> items[index].second(lastData, snackBarManager) }
            .create()
    }

    override fun resetCallbacks() = Unit

    fun show(data: String) {
        binding.title.text = data
        lastData = data
        alertDialog.show()
    }
}
