/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackBottomSheetThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.BottomSheetJunkBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.folder.ThreadListFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class JunkBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetJunkBinding by safeBinding()
    private val navigationArgs: JunkBottomSheetDialogArgs by navArgs()

    override val mainViewModel: MainViewModel by activityViewModels()
    private val junkMessagesViewModel: JunkMessagesViewModel by activityViewModels()

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetJunkBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        junkMessagesViewModel.threadsUids = navigationArgs.threadUids.toList()

        val isFromSpam = mainViewModel.currentFolder.value?.role == FolderRole.SPAM
        val (spamIcon, spamText) = getSpamIconAndText(isFromSpam)
        binding.spam.apply {
            setIconResource(spamIcon)
            setTitle(spamText)
        }

        junkMessagesViewModel.junkMessages.observe(viewLifecycleOwner) { junkMessages ->
            handleButtons(junkMessagesViewModel.threadsUids, junkMessages)
        }
        junkMessagesViewModel.potentialBlockedUsers.observe(viewLifecycleOwner) { potentialUsersToBlock ->
            manageBlockUserUi(potentialUsersToBlock)
        }

        observeReportPhishingResult()
    }

    private fun getSpamIconAndText(isFromSpam: Boolean): Pair<Int, Int> {
        return if (isFromSpam) R.drawable.ic_non_spam to R.string.actionNonSpam else R.drawable.ic_spam to R.string.actionSpam
    }

    private fun observeReportPhishingResult() {
        mainViewModel.reportPhishingTrigger.observe(viewLifecycleOwner) {
            descriptionDialog.resetLoadingAndDismiss()
            findNavController().popBackStack()
        }
    }

    private fun manageBlockUserUi(potentialUsersToBlock: Map<Recipient, Message>) {
        val expeditorsCount = potentialUsersToBlock.count()
        if (expeditorsCount == 0) {
            binding.blockSender.isGone = true
            return
        }

        binding.blockSender.setClosingOnClickListener {
            trackBottomSheetThreadActionsEvent(MatomoName.BlockUser)
            if (expeditorsCount > 1) {
                safelyNavigate(
                    resId = R.id.userToBlockBottomSheetDialog,
                    substituteClassName = ThreadListFragment::class.java.name,
                )
            } else {
                potentialUsersToBlock.values.firstOrNull()?.let { message ->
                    junkMessagesViewModel.messageOfUserToBlock.value = message
                }
            }
            mainViewModel.isMultiSelectOn = false
        }
    }

    private fun handleButtons(threadsUids: List<String>, messages: List<Message>) = with(binding) {
        spam.setClosingOnClickListener {
            // Check the first message, because it is not possible to select messages from multiple folders,
            // so you won't have both SPAM and non-SPAM messages.
            trackBottomSheetThreadActionsEvent(MatomoName.Spam, value = messages.firstOrNull()?.folder?.role == FolderRole.SPAM)
            mainViewModel.toggleThreadSpamStatus(threadsUids)
        }

        phishing.setOnClickListener {
            trackBottomSheetThreadActionsEvent(MatomoName.SignalPhishing)
            descriptionDialog.show(
                title = getString(R.string.reportPhishingTitle),
                description = resources.getQuantityString(R.plurals.reportPhishingDescription, messages.count()),
                onPositiveButtonClicked = { mainViewModel.reportPhishing(threadsUids, messages) },
            )
        }
    }
}
