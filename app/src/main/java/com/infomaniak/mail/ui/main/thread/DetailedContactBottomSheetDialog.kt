/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.trackContactActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetDetailedContactBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.ui.main.thread.actions.ActionsBottomSheetDialog
import com.infomaniak.mail.utils.copyRecipientEmailToClipboard
import com.infomaniak.mail.utils.observeNotNull
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DetailedContactBottomSheetDialog : ActionsBottomSheetDialog() {

    private lateinit var binding: BottomSheetDetailedContactBinding
    private val navigationArgs: DetailedContactBottomSheetDialogArgs by navArgs()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val currentClassName: String by lazy { DetailedContactBottomSheetDialog::class.java.name }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetDetailedContactBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        contactDetails.setRecipient(navigationArgs.recipient, mainViewModel.mergedContacts.value ?: emptyMap())
        setupListeners()
        observeContacts()
    }

    private fun setupListeners() = with(binding) {

        writeMail.setClosingOnClickListener {
            trackContactActionsEvent("writeEmail")
            val args = NewMessageActivityArgs(recipient = navigationArgs.recipient).toBundle()
            safeNavigateToNewMessageActivity(args, currentClassName)
        }
        addToContacts.setClosingOnClickListener {
            trackContactActionsEvent("addToContacts")
            mainViewModel.addContact(navigationArgs.recipient)
        }
        copyAddress.setClosingOnClickListener {
            trackContactActionsEvent("copyEmailAddress")
            copyRecipientEmailToClipboard(navigationArgs.recipient, activity?.findViewById(R.id.quickActionBar))
        }
    }

    private fun observeContacts() {
        mainViewModel.mergedContacts.observeNotNull(viewLifecycleOwner) {
            binding.contactDetails.updateAvatar(navigationArgs.recipient, it)
        }
    }
}
