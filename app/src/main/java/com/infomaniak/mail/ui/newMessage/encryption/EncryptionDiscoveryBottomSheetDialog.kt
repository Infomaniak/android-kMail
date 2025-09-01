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
import android.view.WindowManager.LayoutParams
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.databinding.BottomSheetEncryptionDiscoveryBinding
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel
import com.infomaniak.lib.core.R as RCore

class EncryptionDiscoveryBottomSheetDialog : BottomSheetDialogFragment() {

    private var binding: BottomSheetEncryptionDiscoveryBinding by safeBinding()

    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    private val positiveButtonRes = RCore.string.androidActivateButton
    private val trackMatomoWithCategory: (MatomoName) -> Unit = MatomoMail::trackEncryptionEvent

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetEncryptionDiscoveryBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setFlags(LayoutParams.FLAG_NOT_FOCUSABLE, LayoutParams.FLAG_NOT_FOCUSABLE)
        binding.readMoreButton.setOnClickListener { EncryptionUtils.onReadMoreClicked() }

        actionButton.apply {
            setText(positiveButtonRes)
            setOnClickListener {
                trackMatomoWithCategory(MatomoName.DiscoverNow)
                newMessageViewModel.isEncryptionActivated.value = true
                dismiss()
            }
        }

        secondaryActionButton.setOnClickListener {
            trackMatomoWithCategory(MatomoName.DiscoverLater)
            newMessageViewModel.isEncryptionActivated.value = false
            dismiss()
        }
    }
}
