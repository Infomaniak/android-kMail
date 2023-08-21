/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.ui.main

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.lib.core.utils.showProgress
import com.infomaniak.mail.MatomoMail.trackInvalidPasswordMailboxEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentInvalidPasswordBinding
import com.infomaniak.mail.utils.createDescriptionDialog
import com.infomaniak.mail.utils.getStringWithBoldArg
import dagger.hilt.android.AndroidEntryPoint
import com.infomaniak.lib.core.R as RCore

@AndroidEntryPoint
class InvalidPasswordFragment : Fragment() {

    private lateinit var binding: FragmentInvalidPasswordBinding
    private val navigationArgs: InvalidPasswordFragmentArgs by navArgs()
    private val invalidPasswordViewModel: InvalidPasswordViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentInvalidPasswordBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        enterPasswordDescription2.text = getStringWithBoldArg(R.string.enterPasswordDescription2, navigationArgs.mailboxEmail)

        confirmButton.isEnabled = false

        passwordInput.apply {
            showKeyboard()
            doAfterTextChanged {
                confirmButton.isEnabled = false
                text?.let(::manageButtonState)
            }
        }

        observeResults()
        setupListeners()
    }

    private fun setupListeners() = with(binding) {

        confirmButton.apply {
            setOnClickListener {
                trackInvalidPasswordMailboxEvent("updatePassword")
                showProgress()
                invalidPasswordViewModel.updatePassword(
                    navigationArgs.mailboxId,
                    navigationArgs.mailboxObjectId,
                    passwordInput.text?.trim().toString(),
                )
            }
        }

        detachMailbox.setOnClickListener {
            trackInvalidPasswordMailboxEvent("detachMailbox")
            createDescriptionDialog(
                title = getString(R.string.popupDetachMailboxTitle),
                description = getStringWithBoldArg(R.string.popupDetachMailboxDescription, navigationArgs.mailboxEmail),
                onPositiveButtonClicked = {
                    trackInvalidPasswordMailboxEvent("detachMailboxConfirm")
                    invalidPasswordViewModel.detachMailbox(navigationArgs.mailboxId)
                },
            ).show()
        }

        requestPasswordButton.setOnClickListener { invalidPasswordViewModel.requestPassword() }
    }

    private fun observeResults() = with(binding) {

        invalidPasswordViewModel.updatePasswordResult.observe(viewLifecycleOwner) { error ->
            passwordInputLayout.error = getString(error)
            passwordInput.text = null
            confirmButton.hideProgress(R.string.buttonConfirm)
        }

        invalidPasswordViewModel.detachMailboxResult.observe(viewLifecycleOwner) { error ->
            showSnackbar(error)
        }

        invalidPasswordViewModel.requestPasswordResult.observe(viewLifecycleOwner) { isSuccess ->
            showSnackbar(if (isSuccess) R.string.snackbarMailboxPasswordRequested else RCore.string.anErrorHasOccurred)
        }
    }

    private fun manageButtonState(password: Editable) = with(binding) {
        if (password.count() in PASSWORD_LENGTH_RANGE) {
            passwordInputLayout.helperText = null
            confirmButton.isEnabled = true
        } else {
            passwordInputLayout.helperText = if (password.isEmpty()) null else getString(R.string.errorMailboxPasswordLength)
        }
    }

    private companion object {
        val PASSWORD_LENGTH_RANGE = 6..80
    }
}
