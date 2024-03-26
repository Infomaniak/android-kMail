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
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.MatomoMail.trackInvalidPasswordMailboxEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentInvalidPasswordBinding
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.getStringWithBoldArg
import com.infomaniak.mail.utils.extensions.trimmedText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class InvalidPasswordFragment : Fragment() {

    private var binding: FragmentInvalidPasswordBinding by safeBinding()
    private val navigationArgs: InvalidPasswordFragmentArgs by navArgs()
    private val invalidPasswordViewModel: InvalidPasswordViewModel by viewModels()

    private val updatePasswordButtonProgressTimer by lazy { Utils.createRefreshTimer(onTimerFinish = ::startProgress) }

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentInvalidPasswordBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
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

        bindAlertToViewLifecycle(descriptionDialog)

        observeResults()
        setupListeners()
    }

    private fun setupListeners() = with(binding) {

        confirmButton.apply {
            initProgress(viewLifecycleOwner)
            setOnClickListener {
                trackInvalidPasswordMailboxEvent("updatePassword")
                updatePasswordButtonProgressTimer.start()
                invalidPasswordViewModel.updatePassword(passwordInput.trimmedText)
            }
        }

        detachMailbox.setOnClickListener {
            trackInvalidPasswordMailboxEvent("detachMailbox")
            descriptionDialog.show(
                title = getString(R.string.popupDetachMailboxTitle),
                description = getStringWithBoldArg(R.string.popupDetachMailboxDescription, navigationArgs.mailboxEmail),
                onPositiveButtonClicked = {
                    trackInvalidPasswordMailboxEvent("detachMailboxConfirm")
                    invalidPasswordViewModel.detachMailbox()
                },
            )
        }

        requestPasswordButton.setOnClickListener {
            trackInvalidPasswordMailboxEvent("requestPassword")
            invalidPasswordViewModel.requestPassword()
        }
    }

    private fun observeResults() = with(binding) {

        invalidPasswordViewModel.updatePasswordResult.observe(viewLifecycleOwner) { error ->
            passwordInputLayout.error = getString(error)
            passwordInput.text = null
            updatePasswordButtonProgressTimer.cancel()
            confirmButton.hideProgress(R.string.buttonConfirm)
        }

        invalidPasswordViewModel.detachMailboxResult.observe(viewLifecycleOwner) { error ->
            descriptionDialog.resetLoadingAndDismiss()
            showSnackbar(error)
        }

        invalidPasswordViewModel.requestPasswordResult.observe(viewLifecycleOwner) { apiResponse ->
            showSnackbar(if (apiResponse.isSuccess()) R.string.snackbarMailboxPasswordRequested else apiResponse.translatedError)
        }
    }

    override fun onDestroyView() {
        updatePasswordButtonProgressTimer.cancel()
        super.onDestroyView()
    }

    private fun manageButtonState(password: Editable) = with(binding) {
        if (password.count() in PASSWORD_LENGTH_RANGE) {
            passwordInputLayout.helperText = null
            confirmButton.isEnabled = true
        } else {
            passwordInputLayout.helperText = if (password.isEmpty()) null else getString(R.string.errorMailboxPasswordLength)
        }
    }

    companion object {
        private val PASSWORD_LENGTH_RANGE = 6..80
    }

    private fun startProgress() {
        binding.confirmButton.showProgress()
    }
}
