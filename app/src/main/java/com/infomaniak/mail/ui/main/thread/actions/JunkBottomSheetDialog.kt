/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.ACTION_SPAM_NAME
import com.infomaniak.mail.MatomoMail.trackBottomSheetThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.BottomSheetJunkBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class JunkBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetJunkBinding by safeBinding()
    private val navigationArgs: JunkBottomSheetDialogArgs by navArgs()

    private var messageOfUserToBlock: Message? = null

    override val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetJunkBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel.getMessage(messageUid).observe(viewLifecycleOwner) { message ->
            this@JunkBottomSheetDialog.messageOfUserToBlock = message
            handleButtons(threadUid, message)
        }

        observeReportPhishingResult()
        observeExpeditorsResult(threadUid)
    }

    private fun observeReportPhishingResult() {
        mainViewModel.reportPhishingTrigger.observe(viewLifecycleOwner) {
            descriptionDialog.resetLoadingAndDismiss()
            findNavController().popBackStack()
        }
    }

    private fun observeHasMoreThanOneExpeditor(threadUid: String) {
        mainViewModel.hasMoreThanOneExpeditors(threadUid).observe(viewLifecycleOwner) { hasMoreThanOneExpeditor ->
            binding.blockSender.setClosingOnClickListener {
                if (hasMoreThanOneExpeditor) {
                    safeNavigate(
                        resId = R.id.userToBlockBottomSheetDialog,
                        args = UserToBlockBottomSheetDialogArgs(threadUid).toBundle(),
                        currentClassName = JunkBottomSheetDialog::class.java.name,
                    )
                } else {
                    messageOfUserToBlock?.let {
                        mainViewModel.messageOfUserToBlock.value = messageOfUserToBlock
                    }
                }
            }
        }
    }

    private fun observeExpeditorsResult(threadUid: String) {
        mainViewModel.hasOtherExpeditors(threadUid).observe(viewLifecycleOwner) { hasOtherExpeditors ->
            if (hasOtherExpeditors) {
                observeHasMoreThanOneExpeditor(threadUid)
            } else {
                binding.blockSender.isGone = true
            }
        }
    }

    private fun handleButtons(threadUid: String, message: Message) = with(binding) {
        spam.setClosingOnClickListener {
            trackBottomSheetThreadActionsEvent(ACTION_SPAM_NAME, value = message.folder.role == FolderRole.SPAM)
            mainViewModel.toggleThreadSpamStatus(threadUid)
        }

        phishing.setOnClickListener {
            trackBottomSheetThreadActionsEvent("signalPhishing")
            descriptionDialog.show(
                title = getString(R.string.reportPhishingTitle),
                description = getString(R.string.reportPhishingDescription),
                onPositiveButtonClicked = { mainViewModel.reportPhishing(threadUid, message) },
            )
        }
    }
}
