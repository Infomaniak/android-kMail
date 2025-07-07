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
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.FragmentEncryptionPasswordBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.newMessage.ContactChipAdapter
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel
import com.infomaniak.mail.utils.extensions.copyStringToClipboard
import dagger.hilt.android.AndroidEntryPoint
import java.security.SecureRandom
import javax.inject.Inject

@AndroidEntryPoint
class EncryptionPasswordFragment : Fragment() {

    private var binding: FragmentEncryptionPasswordBinding by safeBinding()

    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val encryptionViewModel: EncryptionViewModel by activityViewModels()

    private val contactChipAdapter: ContactChipAdapter by lazy {
        ContactChipAdapter(openContextMenu = { _, _ -> }, onBackspace = {})
    }

    @Inject
    lateinit var snackbarManager: SnackbarManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentEncryptionPasswordBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUnencryptableRecipientsChips()
        setupPasswordTextField()
        setupListeners()
    }

    private fun setupUnencryptableRecipientsChips() {
        with(contactChipAdapter) {
            binding.userChipsRecyclerView.adapter = this
            unencryptableRecipients = encryptionViewModel.unencryptableRecipients.value
            isEncryptionActivated = true
            unencryptableRecipients?.forEach { addChip(Recipient().initLocalValues(email = it)) }
        }
    }

    private fun setupPasswordTextField() = with(binding) {
        passwordInputLayout.setEndIconOnClickListener {
            passwordInput.setText(generatePassword())
        }
        passwordInput.apply {
            doOnTextChanged { password, _, _, _ -> newMessageViewModel.encryptionPassword.value = password.toString() }
            val initialPassword = newMessageViewModel.encryptionPassword.value.takeUnless { it.isNullOrBlank() }
            setText(initialPassword ?: generatePassword())
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

    private fun generatePassword(): String {
        val generator = SecureRandom.getInstanceStrong()
        var generatedPassword = ""
        val charactersSetCount = PASSWORD_CHARACTERS_SET.count()
        (0..<PASSWORD_MIN_LENGTH).forEach {
            generatedPassword += PASSWORD_CHARACTERS_SET[generator.nextInt(charactersSetCount)]
        }

        return generatedPassword
    }

    companion object {
        private const val ENCRYPTION_FAQ_URL = "https://faq.infomaniak.com/1582"
        private const val PASSWORD_MIN_LENGTH = 16
        private const val PASSWORD_CHARACTERS_SET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+[]{}|;:,.<>?"
    }
}
