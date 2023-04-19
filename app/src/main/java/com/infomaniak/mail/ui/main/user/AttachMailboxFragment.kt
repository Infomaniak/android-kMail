/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.ui.main.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.showProgress
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentAttachMailboxBinding
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.isEmail
import com.infomaniak.lib.core.R as RCore

class AttachMailboxFragment : Fragment() {

    private lateinit var binding: FragmentAttachMailboxBinding
    private val attachMailboxViewModel: AttachMailboxViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAttachMailboxBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        attachMailboxButton.apply {
            isEnabled = false

            mailInput.doAfterTextChanged { isEnabled = shouldEnableButton() }
            passwordInput.doAfterTextChanged { isEnabled = shouldEnableButton() }

            setOnClickListener {
                showProgress()
                attachMailbox()
            }
        }
    }

    private fun shouldEnableButton() = with(binding) {
        passwordInput.text?.isNotBlank() == true && mailInput.text?.trim().toString().isEmail()
    }

    private fun attachMailbox() = with(binding) {
        attachMailboxViewModel.attachNewMailbox(
            mailInput.text?.trim().toString(),
            passwordInput.text?.trim().toString(),
        ).observe(viewLifecycleOwner) { apiError ->
            apiError?.let {
                if (it.code == ErrorCode.INVALID_CREDENTIALS) {
                    mailInputLayout.error = getString(R.string.errorAttachAddressInput)
                    passwordInputLayout.error = getString(R.string.errorInvalidCredentials)
                } else {
                    mailInputLayout.error = null
                    passwordInputLayout.error = null
                    showSnackbar(RCore.string.anErrorHasOccurred, anchor = attachMailboxButton)
                }
                passwordInput.text = null
                attachMailboxButton.hideProgress(R.string.buttonAttachEmailAddress)
            } ?: findNavController().popBackStack()
        }
    }
}
