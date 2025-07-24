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
import android.view.View
import android.view.WindowManager.LayoutParams
import androidx.fragment.app.activityViewModels
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.bottomSheetDialogs.DiscoveryBottomSheetDialog
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel
import com.infomaniak.lib.core.R as RCore

class EncryptionDiscoveryBottomSheetDialog : DiscoveryBottomSheetDialog() {

    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    override val titleRes = R.string.encryptedProtectionAdTitle
    override val descriptionRes = R.string.encryptedProtectionAdDescription
    override val illustration = Illustration.Static(R.drawable.illu_encrypted_mail)
    override val positiveButtonRes = RCore.string.androidActivateButton
    override val trackMatomoWithCategory: (MatomoMail.MatomoName) -> Unit = MatomoMail::trackEncryptionEvent

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setFlags(LayoutParams.FLAG_NOT_FOCUSABLE, LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    override fun onPositiveButtonClicked() {
        newMessageViewModel.isEncryptionActivated.value = true
    }
}
