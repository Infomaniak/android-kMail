/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage.encryption

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentEncryptionPasswordBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.extensions.copyStringToClipboard
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EncryptionPasswordFragment : Fragment() {

    private var binding: FragmentEncryptionPasswordBinding by safeBinding()

    private val encryptionViewModel: EncryptionViewModel by activityViewModels()

    @Inject
    lateinit var snackbarManager: SnackbarManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentEncryptionPasswordBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPasswordTextField()
        setupListeners()
    }

    private fun setupPasswordTextField() = with(binding) {
        passwordInputLayout.setEndIconOnClickListener {
            passwordInput.setText(encryptionViewModel.generatePassword())
        }
        passwordInput.apply {
            doOnTextChanged { password, _, _, _ -> encryptionViewModel.password.value = password.toString() }
            val initialPassword = encryptionViewModel.password.value.takeUnless { it.isNullOrBlank() }
            setText(initialPassword ?: encryptionViewModel.generatePassword())
        }
    }

    private fun setupListeners() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        readMoreButton.setOnClickListener { context?.openUrl(ENCRYPTION_FAQ_URL) }
        copyPasswordButton.setOnClickListener {
            val password = passwordInput.text
            requireContext().copyStringToClipboard(password.toString(), R.string.snackbarPasswordCopied, snackbarManager)
            findNavController().popBackStack()
        }
    }

    companion object {
        private const val ENCRYPTION_FAQ_URL = "https://faq.infomaniak.com/1582"
    }
}
