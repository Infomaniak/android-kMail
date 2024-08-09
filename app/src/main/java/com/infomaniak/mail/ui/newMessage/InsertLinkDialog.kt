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
import android.content.DialogInterface
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogInsertLinkBinding
import com.infomaniak.mail.ui.alertDialogs.BaseAlertDialog
import com.infomaniak.mail.utils.extensions.trimmedText
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class InsertLinkDialog @Inject constructor(
    @ActivityContext private val activityContext: Context
) : BaseAlertDialog(activityContext) {

    val binding: DialogInsertLinkBinding by lazy { DialogInsertLinkBinding.inflate(activity.layoutInflater) }
    private var addLink: ((String, String) -> Unit)? = null

    private val defaultDisplayNameLabel by lazy { activityContext.getString(R.string.addLinkTextPlaceholder) }

    override val alertDialog: AlertDialog = with(binding) {
        showDisplayNamePreview()

        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(R.string.buttonConfirm, null)
            .setNegativeButton(com.infomaniak.lib.core.R.string.buttonCancel, null)
            .create()
            .also {
                it.setOnShowListener { dialog ->
                    displayNameEditText.showKeyboard()
                    resetDialogState()
                    setConfirmButtonListener(dialog)
                }
            }
    }

    private fun resetDialogState() {
        binding.urlLayout.setError(null)
        hidePlaceholder()
    }

    private fun setConfirmButtonListener(dialog: DialogInterface) = with(binding) {
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = addMissingHttpsProtocol(urlEditText.trimmedText)
            if (validate(url)) {
                val displayName = (displayNameEditText.text?.takeIf { it.isNotBlank() } ?: urlEditText.text).toString()
                addLink?.invoke(displayName, url)
                dialog.dismiss()
            } else {
                urlLayout.setError(activityContext.getString(R.string.snackbarInvalidUrl))
            }
        }

        urlEditText.doOnTextChanged { _, _, _, _ ->
            urlLayout.setError(null)
        }
    }

    override fun resetCallbacks() {
        addLink = null
    }

    fun show(defaultDisplayNameValue: String = "", defaultUrlValue: String = "", addLinkCallback: (String, String) -> Unit) {
        binding.apply {
            displayNameEditText.setText(defaultDisplayNameValue)
            urlEditText.setText(defaultUrlValue)
        }

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
            displayNameLayout.hint = if (hasFocus) defaultDisplayNameLabel else computeDisplayNamePreview()
            if (thereIsNoPreviewToDisplay()) hidePlaceholder()
        }
    }

    private fun computeDisplayNamePreview(): String {
        if (!displayNameIsEmpty()) return defaultDisplayNameLabel
        if (thereIsNoPreviewToDisplay()) return defaultDisplayNameLabel

        return binding.urlEditText.text.toString()
    }

    private fun hidePlaceholder() {
        binding.displayNameLayout.placeholderText = null
    }

    private fun displayNameIsEmpty() = binding.displayNameEditText.text.isNullOrEmpty()

    private fun thereIsNoPreviewToDisplay() = binding.urlEditText.text.isNullOrBlank()

    private fun addMissingHttpsProtocol(link: String): String {
        val protocolEndIndex = link.indexOf("://")
        val isProtocolSpecified = protocolEndIndex > 0 // If there is indeed a specified protocol of at least 1 char long

        if (isProtocolSpecified) return link

        val strippedUserInput = if (protocolEndIndex != -1) link.substring(protocolEndIndex + 3) else link

        return "https://$strippedUserInput"
    }

    private fun validate(userUrlInput: String): Boolean {
        return Patterns.WEB_URL.matcher(userUrlInput).matches()
    }
}
