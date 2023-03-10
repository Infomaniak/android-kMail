/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetDetailedContactBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.ui.main.thread.actions.ActionsBottomSheetDialog
import com.infomaniak.mail.utils.UiUtils.fillInUserNameAndEmail
import com.infomaniak.mail.utils.observeNotNull

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

        userAvatar.loadAvatar(navigationArgs.recipient, mainViewModel.mergedContacts.value ?: emptyMap())
        fillInUserNameAndEmail(navigationArgs.recipient, name, email)

        setupListeners()

        observeContacts()
    }

    private fun setupListeners() = with(binding) {

        writeMail.setClosingOnClickListener {
            trackContactActionsEvent("writeEmail")
            safeNavigate(
                resId = R.id.newMessageActivity,
                args = NewMessageActivityArgs(recipient = navigationArgs.recipient).toBundle(),
                currentClassName = currentClassName,
            )
        }
        addToContacts.setClosingOnClickListener {
            trackContactActionsEvent("addToContacts")
            mainViewModel.addContact(navigationArgs.recipient)
        }
        copyAddress.setClosingOnClickListener {
            trackContactActionsEvent("copyEmailAddress")
            copyToClipboard()
        }
    }

    private fun copyToClipboard() = with(navigationArgs.recipient) {
        val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText(email, email))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            showSnackbar(R.string.snackbarEmailCopiedToClipboard, anchor = activity?.findViewById(R.id.quickActionBar))
        }
    }

    private fun observeContacts() {
        mainViewModel.mergedContacts.observeNotNull(viewLifecycleOwner) {
            binding.userAvatar.loadAvatar(navigationArgs.recipient, it)
        }
    }

    private fun trackContactActionsEvent(name: String) {
        trackEvent("contactActions", name)
    }
}
