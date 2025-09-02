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

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetEncryptionDiscoveryBinding
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel

class EncryptionDiscoveryBottomSheetDialog : BottomSheetDialogFragment() {

    private var binding: BottomSheetEncryptionDiscoveryBinding by safeBinding()

    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetEncryptionDiscoveryBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setFlags(LayoutParams.FLAG_NOT_FOCUSABLE, LayoutParams.FLAG_NOT_FOCUSABLE)

        setBoldDescriptions()
        setupListeners()
    }

    private fun setBoldDescriptions() {
        binding.description1.text = computeBoldDescription(
            completeDescriptionRes = R.string.encryptedProtectionAdDescription1,
            boldSubstringRes = R.string.encryptedProtectionAdDescription1Bold,
        )
        binding.description2.text = computeBoldDescription(
            completeDescriptionRes = R.string.encryptedProtectionAdDescription2,
            boldSubstringRes = R.string.encryptedProtectionAdDescription2Bold,
        )
    }

    private fun computeBoldDescription(
        @StringRes completeDescriptionRes: Int,
        @StringRes boldSubstringRes: Int,
    ): Spannable {
        val completeDescription = getString(completeDescriptionRes)
        val boldDescription = SpannableString(completeDescription)

        getString(boldSubstringRes).toRegex().find(completeDescription)?.range?.let { range ->
            boldDescription.setSpan(
                StyleSpan(Typeface.BOLD),
                range.start,
                range.endInclusive,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE,
            )
        }

        return boldDescription
    }

    private fun setupListeners() = with(binding) {
        readMoreButton.setOnClickListener { EncryptionUtils.onReadMoreClicked() }

        actionButton.setOnClickListener {
            MatomoMail.trackEncryptionEvent(MatomoName.DiscoverNow)
            newMessageViewModel.isEncryptionActivated.value = true
            dismiss()
        }

        secondaryActionButton.setOnClickListener {
            MatomoMail.trackEncryptionEvent(MatomoName.DiscoverLater)
            dismiss()
        }
    }
}
