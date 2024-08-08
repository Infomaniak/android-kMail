/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogInsertLinkBinding
import com.infomaniak.mail.ui.alertDialogs.BaseAlertDialog
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class InsertLinkDialog @Inject constructor(
    @ActivityContext private val activityContext: Context
) : BaseAlertDialog(activityContext) {

    val binding: DialogInsertLinkBinding by lazy { DialogInsertLinkBinding.inflate(activity.layoutInflater) }
    private var addLink: ((String, String) -> Unit)? = null

    private val defaultDisplayName by lazy { activityContext.getString(R.string.addLinkTextPlaceholder) }

    override val alertDialog: AlertDialog = with(binding) {
        showDisplayNamePreview()

        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(R.string.buttonConfirm) { _, _ ->
                addLink?.invoke(displayNameEditText.text.toString(), urlEditText.text.toString())
            }
            .setNegativeButton(com.infomaniak.lib.core.R.string.buttonCancel, null)
            .create()
    }

    override fun resetCallbacks() {
        addLink = null
    }

    // TODO: Add a default display name value with the selection's text when the user selects some text before inserting a link
    fun show(addLinkCallback: (String, String) -> Unit) {
        addLink = addLinkCallback
        alertDialog.show()
    }

    // We need to play with the display text's hint and placeholder to only show the correct one in each situation. Placeholder is
    // only visible when the EditText is focused and hint takes the place of the placeholder when the EditText is not focused.
    private fun showDisplayNamePreview() = with(binding) {
        urlEditText.doOnTextChanged { _, _, _, _ ->
            if (displayNameIsEmpty()) {
                displayNameLayout.apply {
                    val preview = computeDisplayNamePreview()

                    placeholderText = preview
                    hint = preview
                    invalidate() // Required to update the hint, else it never updates visually
                }
            }
        }

        displayNameEditText.doOnTextChanged { _, _, _, _ ->
            if (displayNameIsEmpty()) {
                if (thereIsNoPreviewToDisplay()) {
                    hidePlaceholder()
                } else {
                    displayNameLayout.placeholderText = computeDisplayNamePreview()
                }
            }
        }

        displayNameEditText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            displayNameLayout.hint = if (hasFocus) defaultDisplayName else computeDisplayNamePreview()
            if (thereIsNoPreviewToDisplay()) hidePlaceholder()
        }
    }

    private fun computeDisplayNamePreview(): String {
        if (!displayNameIsEmpty()) return defaultDisplayName
        if (thereIsNoPreviewToDisplay()) return defaultDisplayName

        return binding.urlEditText.text.toString()
    }

    private fun hidePlaceholder() {
        binding.displayNameLayout.placeholderText = null
    }

    private fun displayNameIsEmpty() = binding.displayNameEditText.text.isNullOrEmpty()

    private fun thereIsNoPreviewToDisplay() = binding.urlEditText.text.isNullOrBlank()
}
