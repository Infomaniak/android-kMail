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

import androidx.core.view.isVisible
import androidx.lifecycle.distinctUntilChanged
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.newMessage.NewMessageFragment
import com.infomaniak.mail.ui.newMessage.NewMessageFragmentDirections
import com.infomaniak.mail.ui.newMessage.NewMessageManager
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionLockButtonView.EncryptionStatus
import dagger.hilt.android.scopes.FragmentScoped
import javax.inject.Inject

@FragmentScoped
class EncryptionMessageManager @Inject constructor(
    private val localSettings: LocalSettings,
    private val snackbarManager: SnackbarManager,
) : NewMessageManager() {

    private var _encryptionViewModel: EncryptionViewModel? = null
    private val encryptionViewModel: EncryptionViewModel get() = _encryptionViewModel!!

    private var hasAlreadyAddedUnencryptableRecipients = false

    fun init(
        newMessageViewModel: NewMessageViewModel,
        encryptionViewModel: EncryptionViewModel,
        binding: FragmentNewMessageBinding,
        fragment: NewMessageFragment,
    ) {
        super.initValues(
            newMessageViewModel = newMessageViewModel,
            binding = binding,
            fragment = fragment,
            freeReferences = { _encryptionViewModel = null },
        )

        _encryptionViewModel = encryptionViewModel
        observeDraftPassword()
    }

    fun observeEncryptionFeatureFlagUpdates() {
        newMessageViewModel.featureFlagsLive.observe(viewLifecycleOwner) { featureFlags ->
            val isEncryptionPossible = featureFlags.contains(FeatureFlag.ENCRYPTION)
            binding.encryptionLockButtonView.isVisible = isEncryptionPossible
        }
    }

    fun observeDraftPassword() {
        newMessageViewModel.encryptionPassword.distinctUntilChanged().observe(viewLifecycleOwner) { draftPassword ->
            if (draftPassword.isNullOrBlank() || encryptionViewModel.password.value != null) return@observe

            // We initialize the encryption password to the value of the already existing password stored in the draft
            encryptionViewModel.password.value = draftPassword
        }
    }

    fun observeEncryptionActivation() {
        newMessageViewModel.isEncryptionActivated.observe(viewLifecycleOwner) { isEncrypted ->
            newMessageViewModel.updateIsSendingAllowed(isEncryptionValid = checkEncryptionCanBeSend())

            val recipients = newMessageViewModel.allRecipients

            val currentUnencryptableRecipients = encryptionViewModel.unencryptableRecipients.value ?: emptySet()
            val unknownEncryptionStatusRecipients = recipients.filter {
                currentUnencryptableRecipients.contains(it.email) != true
            }
            if (isEncrypted && unknownEncryptionStatusRecipients.isNotEmpty()) {
                encryptionViewModel.checkIfEmailsCanBeEncrypted(unknownEncryptionStatusRecipients.map(Recipient::email))
            }

            val isPartialEncryption =
                isEncrypted && currentUnencryptableRecipients.isNotEmpty() // TODO : put a loader when unencryptable is null
            val encryptionStatus = when {
                isPartialEncryption && encryptionViewModel.password.value.isNullOrBlank() -> EncryptionStatus.PartiallyEncrypted
                isEncrypted -> {
                    navigateToDiscoveryBottomSheetIfFirstTime()
                    snackbarManager.postValue(fragment.getString(R.string.encryptedMessageSnackbarEncryptionActivated))
                    EncryptionStatus.Encrypted
                }
                else -> {
                    encryptionViewModel.password.value = ""
                    EncryptionStatus.Unencrypted
                }
            }

            binding.encryptionLockButtonView.encryptionStatus = encryptionStatus
        }
    }

    fun observeUnencryptableRecipients() {
        encryptionViewModel.unencryptableRecipients.observe(viewLifecycleOwner) { recipients ->
            newMessageViewModel.updateIsSendingAllowed(isEncryptionValid = checkEncryptionCanBeSend())

            if (newMessageViewModel.isEncryptionActivated.value != true) return@observe

            val recipientsCount = recipients?.count() ?: 0
            binding.encryptionLockButtonView.apply {
                unencryptableRecipientsCount = recipientsCount
                encryptionStatus = if (recipientsCount > 0 && encryptionViewModel.password.value.isNullOrBlank()) {
                    EncryptionStatus.PartiallyEncrypted
                } else {
                    EncryptionStatus.Encrypted
                }
            }

            if (recipientsCount > 0 && !hasAlreadyAddedUnencryptableRecipients) {
                hasAlreadyAddedUnencryptableRecipients = true
                snackbarManager.postValue(
                    fragment.resources.getQuantityString(
                        R.plurals.encryptedMessageIncompleteUser,
                        recipientsCount,
                        recipientsCount,
                    ),
                )
            }
        }
    }

    fun observeEncryptionPassword() {
        encryptionViewModel.password.distinctUntilChanged().observe(viewLifecycleOwner) { password ->
            val encryptionStatus = if (password.isNullOrBlank()) {
                EncryptionStatus.PartiallyEncrypted
            } else {
                EncryptionStatus.Encrypted
            }
            binding.encryptionLockButtonView.encryptionStatus = encryptionStatus

            password?.let(newMessageViewModel.encryptionPassword::postValue)
        }
    }

    fun toggleEncryption() {
        if (newMessageViewModel.isEncryptionActivated.value == true) {
            fragment.safelyNavigate(
                NewMessageFragmentDirections.actionNewMessageFragmentToEncryptionActionsBottomSheetDialog(
                    password = newMessageViewModel.encryptionPassword.value ?: "",
                )
            )
        } else {
            newMessageViewModel.isEncryptionActivated.value = true
        }
    }

    fun checkRecipientEncryptionStatus(recipient: Recipient) {
        encryptionViewModel.checkIfEmailsCanBeEncrypted(listOf(recipient.email))
    }

    fun removeUnencryptableRecipient(recipient: Recipient) {
        val unencryptableRecipients = encryptionViewModel.unencryptableRecipients.value?.toMutableSet()
        if (unencryptableRecipients?.contains(recipient.email) == true) {
            encryptionViewModel.unencryptableRecipients.value = unencryptableRecipients.apply { remove(recipient.email) }
        }
    }

    /**
     * @return true if the encryption is either disabled or initialized with values allowing it to be sent
     * E.G. Only auto-encryptable recipients, or some unencryptable recipients but with a password provided
     *
     * @return false if the encryption is in a state that doesn't allow the sending.
     * E.G. Activated but without information if recipient can be auto encrypted or not, or with unencryptable recipients but
     * without having a password provided
     */
    fun checkEncryptionCanBeSend(): Boolean {
        val currentUnencryptableRecipients = encryptionViewModel.unencryptableRecipients.value
        val isEncryptionActivated = newMessageViewModel.isEncryptionActivated.value == true

        return when {
            !isEncryptionActivated -> true // Encryption is not activated, doesn't need to block the draft sending
            currentUnencryptableRecipients == null -> false // The call to know if recipient can be encrypted is still processing
            currentUnencryptableRecipients.isEmpty() -> true // Only auto-encryptable recipients
            encryptionViewModel.password.value?.isNotBlank() == true -> true // A password has been provided
            else -> false // Some unencryptable recipients without password
        }
    }

    private fun navigateToDiscoveryBottomSheetIfFirstTime() = with(localSettings) {
        if (showEncryptionDiscoveryBottomSheet) {
            showEncryptionDiscoveryBottomSheet = false
            // TODO show discovery screen ?
        }
    }
}
