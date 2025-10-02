/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackContactActionsEvent
import com.infomaniak.mail.databinding.BottomSheetDetailedContactBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.actions.ActionsBottomSheetDialog
import com.infomaniak.mail.ui.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.utils.extensions.copyRecipientEmailToClipboard
import com.infomaniak.mail.utils.extensions.safeNavigateToNewMessageActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DetailedContactBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetDetailedContactBinding by safeBinding()
    private val navigationArgs: DetailedContactBottomSheetDialogArgs by navArgs()
    override val mainViewModel: MainViewModel by activityViewModels()

    private val currentClassName: String by lazy { DetailedContactBottomSheetDialog::class.java.name }

    @Inject
    lateinit var snackbarManager: SnackbarManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetDetailedContactBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        val bimi = navigationArgs.bimi
        containerInfoCertified.isVisible = bimi?.isCertified == true
        contactDetails.setCorrespondent(navigationArgs.recipient, bimi)

        setupListeners()
    }

    private fun setupListeners() = with(binding) {

        writeMail.setClosingOnClickListener {
            trackContactActionsEvent(MatomoName.WriteEmail)
            safeNavigateToNewMessageActivity(
                args = NewMessageActivityArgs(recipient = navigationArgs.recipient).toBundle(),
                currentClassName = currentClassName,
            )
        }
        addToContacts.setClosingOnClickListener {
            trackContactActionsEvent(MatomoName.AddToContacts)
            mainViewModel.addContact(navigationArgs.recipient)
        }
        copyAddress.setClosingOnClickListener {
            trackContactActionsEvent(MatomoName.CopyEmailAddress)
            copyRecipientEmailToClipboard(navigationArgs.recipient, snackbarManager)
        }
    }
}
