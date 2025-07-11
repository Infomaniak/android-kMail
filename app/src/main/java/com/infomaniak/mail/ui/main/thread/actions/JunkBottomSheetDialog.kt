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
import android.util.Log
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

    private var messagesOfUserToBlock: List<Message?> = emptyList()

    override val mainViewModel: MainViewModel by activityViewModels()

    private var messageUids = emptyList<String>()
    private var threadUids = emptyList<String>()


    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetJunkBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        threadUids = navigationArgs.arrayOfThreadAndMessageUids.map { data -> data.threadUid }
        messageUids = navigationArgs.arrayOfThreadAndMessageUids.map { data -> data.messageUid }


        Log.e("TOTO", "onViewCreated: $threadUids et $messageUids")
        mainViewModel.getMessages(messageUids).observe(viewLifecycleOwner) { messages ->
            this@JunkBottomSheetDialog.messagesOfUserToBlock = messages
            handleButtons(threadUids, messages)
        }

        observeReportPhishingResult()
        observeExpeditorsResult(threadUids)
    }

    private fun observeReportPhishingResult() {
        mainViewModel.reportPhishingTrigger.observe(viewLifecycleOwner) {
            descriptionDialog.resetLoadingAndDismiss()
            findNavController().popBackStack()
        }
    }

    private fun observeHasMoreThanOneExpeditor(threadUids: List<String>) {
        mainViewModel.hasMoreThanOneExpeditor(threadUids).observe(viewLifecycleOwner) { hasMoreThanOneExpeditor ->
            binding.blockSender.setClosingOnClickListener {
                trackBottomSheetThreadActionsEvent("blockUser")
                if (hasMoreThanOneExpeditor) {
                    safeNavigate(
                        resId = R.id.userToBlockBottomSheetDialog,
                        args = UserToBlockBottomSheetDialogArgs(threadUids.toTypedArray()).toBundle(),
                        currentClassName = JunkBottomSheetDialog::class.java.name,
                    )
                } else {
                    if (messagesOfUserToBlock.isNotEmpty()) {
                        mainViewModel.messageOfUserToBlock.value = messagesOfUserToBlock
                    }
                }
            }
        }
    }

    private fun observeExpeditorsResult(threadUids: List<String>) {
        mainViewModel.hasOtherExpeditors(threadUids).observe(viewLifecycleOwner) { hasOtherExpeditors ->
            if (hasOtherExpeditors) {
                observeHasMoreThanOneExpeditor(threadUids)
            } else {
                binding.blockSender.isGone = true
            }
        }
    }

    private fun handleButtons(threadUids: List<String>, messages: List<Message>) = with(binding) {
        spam.setClosingOnClickListener {
            Log.e("TOTO", "handleButtons: cliquer sur le button SPAM \n avec $threadUids ,$messageUids")
            trackBottomSheetThreadActionsEvent(ACTION_SPAM_NAME, value = messages[0].folder.role == FolderRole.SPAM)
            mainViewModel.toggleThreadSpamStatus(threadUids)
        }

        phishing.setOnClickListener {
            Log.e("TOTO", "handleButtons: cliquer sur le button PHISHING \n avec $threadUids ,$messageUids")
            trackBottomSheetThreadActionsEvent("signalPhishing")
            descriptionDialog.show(
                title = getString(R.string.reportPhishingTitle),
                description = getString(R.string.reportPhishingDescription),
                onPositiveButtonClicked = { mainViewModel.reportPhishing(threadUids, messages) },
            )
        }
    }
}
