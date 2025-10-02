/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.infomaniak.core.legacy.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.Utils
import com.infomaniak.core.legacy.utils.hideProgressCatching
import com.infomaniak.core.legacy.utils.initProgress
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.showKeyboard
import com.infomaniak.core.legacy.utils.showProgressCatching
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentAttachMailboxBinding
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.extensions.isEmail
import com.infomaniak.mail.utils.extensions.trimmedText
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AttachMailboxFragment : Fragment() {

    private var binding: FragmentAttachMailboxBinding by safeBinding()
    private val attachMailboxViewModel: AttachMailboxViewModel by viewModels()

    private val attachMailboxButtonProgressTimer by lazy { Utils.createRefreshTimer(onTimerFinish = ::startProgress) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAttachMailboxBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        mailInput.apply {
            showKeyboard()
            manageErrorOnTextChange(mailInputLayout, attachMailboxButton)

            onFocusChangeListener = OnFocusChangeListener { _, hasFocus -> manageEmailErrorOnFocusChange(hasFocus) }
        }

        passwordInput.manageErrorOnTextChange(passwordInputLayout, attachMailboxButton)

        attachMailboxButton.apply {
            isEnabled = false
            initProgress(viewLifecycleOwner)

            setOnClickListener {
                trackAccountEvent(MatomoName.AddMailboxConfirm)
                attachMailboxButtonProgressTimer.start()
                attachMailbox()
            }
        }
    }

    override fun onDestroyView() {
        attachMailboxButtonProgressTimer.cancel()
        super.onDestroyView()
    }

    private fun TextInputEditText.manageEmailErrorOnFocusChange(hasFocus: Boolean) = with(binding) {
        if (!hasFocus) {
            when {
                text.isNullOrBlank() -> mailInputLayout.error = null
                !isEmail() -> mailInputLayout.error = getString(R.string.errorInvalidEmailAddress)
            }
        }
    }

    private fun TextInputEditText.manageErrorOnTextChange(inputLayout: TextInputLayout, sendButton: MaterialButton) {
        doAfterTextChanged {
            if (text?.isNotBlank() == true) inputLayout.error = null
            sendButton.isEnabled = shouldEnableButton()
        }
    }

    private fun TextInputEditText.isEmail() = trimmedText.isEmail()

    private fun shouldEnableButton() = with(binding) {
        passwordInput.text?.isNotBlank() == true && mailInput.isEmail()
    }

    private fun attachMailbox() = with(binding) {
        attachMailboxViewModel.attachNewMailbox(
            address = mailInput.trimmedText,
            password = passwordInput.trimmedText,
        ).observe(viewLifecycleOwner) { apiResponse ->
            when {
                apiResponse.isSuccess() -> {
                    apiResponse.data?.mailboxId?.let(attachMailboxViewModel::switchToNewMailbox)
                    return@observe
                }
                apiResponse.error?.code == ErrorCode.INVALID_CREDENTIALS -> {
                    val error = getString(apiResponse.translateError())
                    mailInputLayout.error = error
                    passwordInputLayout.error = error
                }
                else -> {
                    mailInputLayout.error = null
                    passwordInputLayout.error = null
                    showSnackbar(title = apiResponse.translateError(), anchor = attachMailboxButton)
                }
            }

            passwordInput.text = null
            attachMailboxButtonProgressTimer.cancel()
            attachMailboxButton.hideProgressCatching(R.string.buttonAttachMailbox)
        }
    }

    private fun startProgress() {
        binding.attachMailboxButton.showProgressCatching()
    }
}
