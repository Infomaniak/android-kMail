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
package com.infomaniak.mail.ui.newMessage

import android.app.Activity
import android.content.Context
import androidx.core.view.isVisible
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.FragmentScoped
import javax.inject.Inject

@FragmentScoped
class EncryptionMessageManager @Inject constructor(
    @ActivityContext private val activityContext: Context,
    private val localSettings: LocalSettings,
    private val snackbarManager: SnackbarManager,
) : NewMessageManager() {

    private inline val activity get() = activityContext as Activity
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
            if (isEncryptionPossible) navigateToDiscoveryBottomSheetIfFirstTime()
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
            if (isEncrypted) {
                snackbarManager.postValue(fragment.getString(R.string.encryptedMessageSnackbarEncryptionActivated))
            } else {
                // TODO
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
