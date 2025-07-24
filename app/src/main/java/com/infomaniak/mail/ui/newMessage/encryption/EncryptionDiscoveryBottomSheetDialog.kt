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

import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.bottomSheetDialogs.DiscoveryBottomSheetDialog
import com.infomaniak.lib.core.R as RCore

class EncryptionDiscoveryBottomSheetDialog : DiscoveryBottomSheetDialog() {

    override val titleRes = R.string.encryptedProtectionAdTitle
    override val descriptionRes = R.string.encryptedProtectionAdDescription
    override val illustrationRes = R.drawable.illu_encrypted_mail
    override val positiveButtonRes = RCore.string.androidActivateButton
    override val trackMatomoWithCategory: (MatomoMail.MatomoName) -> Unit = MatomoMail::trackEncryptionEvent

    override fun onPositiveButtonClicked() {
        findNavController().popBackStack()
    }
}
