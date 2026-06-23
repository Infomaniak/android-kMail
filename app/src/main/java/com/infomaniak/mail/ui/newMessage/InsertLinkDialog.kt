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
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doOnTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.showKeyboard
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogInsertLinkBinding
import com.infomaniak.mail.ui.alertDialogs.BaseAlertDialog
import com.infomaniak.mail.utils.extensions.trimmedText
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import com.infomaniak.core.legacy.R as RCore

@ActivityScoped
class InsertLinkDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : BaseAlertDialog(activityContext) {

    val binding: DialogInsertLinkBinding by lazy { DialogInsertLinkBinding.inflate(activity.layoutInflater) }
    private var addLink: ((String, String) -> Unit)? = null

    override val alertDialog: AlertDialog = with(binding) {
        showDisplayNamePreview()

        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(R.string.buttonConfirm, null)
            .setNegativeButton(RCore.string.buttonCancel, null)
            .create()
            .also {
                it.setOnShowListener { dialog ->
                    urlEditText.showKeyboard()
                    resetDialogState()
                    setConfirmButtonListener(dialog)
                }
                urlEditText.doOnTextChanged { _, _, _, _ ->
                    urlLayout.setError(null)
                }
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

    // Pre-fills the display name with the url's content if the fields contain the same value.
    private fun showDisplayNamePreview() = with(binding) {
        displayNameEditText.setTextColor(activityContext.getColor(R.color.tertiaryTextColor))

        var areInputsSynced = false
        urlEditText.addTextChangedListener(
            beforeTextChanged = { text, _, _, _ ->
                areInputsSynced = text.toString() == displayNameEditText.text.toString()
            },
            onTextChanged = { text, _, _, _ ->
                if (areInputsSynced || displayNameEditText.text.isNullOrBlank()) displayNameEditText.setText(text)
            }
        )

        displayNameEditText.setOnFocusChangeListener { _, hasFocus ->
            val textColor = activityContext.getColor(if (hasFocus) R.color.primaryTextColor else R.color.tertiaryTextColor)
            displayNameEditText.setTextColor(textColor)
        }
    }

    private fun resetDialogState() = with(binding) {
        urlLayout.setError(null)
        displayNameLayout.placeholderText = null
    }

    private fun setConfirmButtonListener(dialog: DialogInterface) = with(binding) {
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = addMissingHttpsProtocol(urlEditText.trimmedText)

            if (validate(url)) {
                val displayName = (displayNameEditText.text?.takeIf { it.isNotBlank() } ?: urlEditText.text).toString().trim()
                addLink?.invoke(displayName, url)

                dialog.dismiss()
            } else {
                urlLayout.setError(activityContext.getString(R.string.snackbarInvalidUrl))
            }
        }
    }

    private fun addMissingHttpsProtocol(link: String): String {
        val protocolEndIndex = link.indexOf(PROTOCOL_SEPARATOR)
        val isProtocolSpecified = protocolEndIndex > 0 // If there is a specified protocol and it is at least 1 char long

        if (isProtocolSpecified) return link

        val strippedUserInput = if (protocolEndIndex == -1) link else link.substring(PROTOCOL_SEPARATOR.length)

        return "https://$strippedUserInput"
    }

    private fun validate(userUrlInput: String): Boolean = Patterns.WEB_URL.matcher(userUrlInput).matches()

    companion object {
        private const val PROTOCOL_SEPARATOR = "://"
    }
}
