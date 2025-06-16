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
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.newMessage.NewMessageFragment
import com.infomaniak.mail.ui.newMessage.NewMessageManager
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel
import dagger.hilt.android.scopes.FragmentScoped
import javax.inject.Inject

@FragmentScoped
class EncryptionMessageManager @Inject constructor(
    private val localSettings: LocalSettings,
    private val snackbarManager: SnackbarManager,
) : NewMessageManager() {

    private var _encryptionViewModel: EncryptionViewModel? = null
    private val encryptionViewModel: EncryptionViewModel get() = _encryptionViewModel!!

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
    }

    fun observeEncryptionFeatureFlagUpdates() {
        newMessageViewModel.featureFlagsLive.observe(viewLifecycleOwner) { featureFlags ->
            val isEncryptionPossible = featureFlags.contains(FeatureFlag.ENCRYPTION)
            binding.encryptionButton.isVisible = isEncryptionPossible
        }
    }

    fun observeUncryptableRecipients() {
        encryptionViewModel.uncryptableRecipients.observe(viewLifecycleOwner) { recipients ->
            // TODO Replace this by the lock button with the number of uncryptable recipients
            if (recipients.isNotEmpty()) {
                snackbarManager.postValue(
                    fragment.getString(R.string.encryptedMessageAddPasswordDescription1) + recipients.joinToString(" "),
                )
            }
        }
    }

    fun toggleEncryption() {
        val recipients = newMessageViewModel.allRecipients
        if (recipients.isNotEmpty()) encryptionViewModel.checkIfEmailsCanBeEncrypted(recipients.map(Recipient::email))
        newMessageViewModel.toggleIsEncryptionActivated()
    }

    fun observeEncryptionActivation() {
        newMessageViewModel.isEncryptionActivated.observe(viewLifecycleOwner) { isEncrypted ->
            val (iconRes, tintRes) = if (isEncrypted) {
                navigateToDiscoveryBottomSheetIfFirstTime()
                snackbarManager.postValue(fragment.getString(R.string.encryptedMessageSnackbarEncryptionActivated))
                R.drawable.ic_lock_filled to R.color.encryptionIconColor
            } else {
                R.drawable.ic_lock_open_filled to R.color.iconColor
            }

            binding.encryptionButton.apply {
                setIconResource(iconRes)
                setIconTintResource(tintRes)
            }
        }
    }

    private fun navigateToDiscoveryBottomSheetIfFirstTime() = with(localSettings) {
        if (showEncryptionDiscoveryBottomSheet) {
            showEncryptionDiscoveryBottomSheet = false
            // TODO show discovery screen ?
        }
    }
}
