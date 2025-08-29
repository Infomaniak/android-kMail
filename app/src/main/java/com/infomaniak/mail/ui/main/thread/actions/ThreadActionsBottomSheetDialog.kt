/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setBackNavigationResult
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackBottomSheetThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.move.MoveFragmentArgs
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.ThreadMessageToExecuteAction
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.archiveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.moveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.navigateToDownloadMessagesProgressDialog
import com.infomaniak.mail.utils.extensions.notYetImplemented
import com.infomaniak.mail.utils.extensions.safeNavigateToNewMessageActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ThreadActionsBottomSheetDialog : MailActionsBottomSheetDialog() {

    private val navigationArgs: ThreadActionsBottomSheetDialogArgs by navArgs()
    private val threadActionsViewModel: ThreadActionsViewModel by viewModels()

    private val currentClassName: String by lazy { ThreadActionsBottomSheetDialog::class.java.name }
    override val shouldCloseMultiSelection by lazy { navigationArgs.shouldCloseMultiSelection }

    private var folderRole: FolderRole? = null
    private var isFromArchive: Boolean = false
    private var isFromSpam: Boolean = false

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    @Inject
    lateinit var folderRoleUtils: FolderRoleUtils

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var threadController: ThreadController

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        threadActionsViewModel.threadMessageToExecuteAction.observe(viewLifecycleOwner) { threadMessageToExecuteAction ->
            if (threadMessageToExecuteAction == null) {
                findNavController().popBackStack()
                return@observe
            }

            val thread = threadMessageToExecuteAction.thread

            folderRole = folderRoleUtils.getActionFolderRole(thread)
            isFromArchive = folderRole == FolderRole.ARCHIVE
            isFromSpam = folderRole == FolderRole.SPAM

            setMarkAsReadUi(thread.isSeen)
            setArchiveUi(isFromArchive)
            setFavoriteUi(thread.isFavorite)
            setJunkUi()
            setSnoozeUi(thread.isSnoozed())

            initOnClickListener(onActionClick(threadMessageToExecuteAction))
        }
    }

    private fun setSnoozeUi(isThreadSnoozed: Boolean) = with(binding) {
        val shouldDisplaySnoozeActions = SharedUtils.shouldDisplaySnoozeActions(mainViewModel, localSettings, folderRole)

        snooze.isVisible = shouldDisplaySnoozeActions && isThreadSnoozed.not()
        modifySnooze.isVisible = shouldDisplaySnoozeActions && isThreadSnoozed
        cancelSnooze.isVisible = shouldDisplaySnoozeActions && isThreadSnoozed
    }

    private fun setJunkUi() = binding.reportJunk.apply {

        val (text, icon) = if (isFromSpam) {
            R.string.actionNonSpam to R.drawable.ic_non_spam
        } else {
            R.string.actionReportJunk to R.drawable.ic_report_junk
        }

        setTitle(text)
        setIconResource(icon)
        isVisible = true
    }

    private fun onActionClick(threadMessageToExecuteAction: ThreadMessageToExecuteAction) = object : OnActionClick {
        //region Main actions
        override fun onReply() {
            trackBottomSheetThreadActionsEvent(MatomoName.Reply)
            safeNavigateToNewMessageActivity(
                draftMode = DraftMode.REPLY,
                previousMessageUid = threadMessageToExecuteAction.messageUid,
                currentClassName = currentClassName,
                shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
            )
        }

        override fun onReplyAll() {
            trackBottomSheetThreadActionsEvent(MatomoName.ReplyAll)
            safeNavigateToNewMessageActivity(
                draftMode = DraftMode.REPLY_ALL,
                previousMessageUid = threadMessageToExecuteAction.messageUid,
                currentClassName = currentClassName,
                shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
            )
        }

        override fun onForward() {
            trackBottomSheetThreadActionsEvent(MatomoName.Forward)
            safeNavigateToNewMessageActivity(
                draftMode = DraftMode.FORWARD,
                previousMessageUid = threadMessageToExecuteAction.messageUid,
                currentClassName = currentClassName,
                shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
            )
        }

        override fun onDelete() {
            descriptionDialog.deleteWithConfirmationPopup(folderRole, count = 1) {
                trackBottomSheetThreadActionsEvent(MatomoName.Delete)
                mainViewModel.deleteThread(navigationArgs.threadUid)
            }
        }
        //endregion

        //region Actions
        override fun onArchive() {
            descriptionDialog.archiveWithConfirmationPopup(folderRole, count = 1) {
                trackBottomSheetThreadActionsEvent(MatomoName.Archive, isFromArchive)
                mainViewModel.archiveThread(navigationArgs.threadUid)
            }
        }

        override fun onReadUnread() {
            trackBottomSheetThreadActionsEvent(MatomoName.MarkAsSeen, value = threadMessageToExecuteAction.thread.isSeen)
            mainViewModel.toggleThreadSeenStatus(navigationArgs.threadUid)
            twoPaneViewModel.closeThread()
        }

        override fun onMove() {
            val navController = findNavController()
            descriptionDialog.moveWithConfirmationPopup(folderRole, count = 1) {
                trackBottomSheetThreadActionsEvent(MatomoName.Move)
                navController.animatedNavigation(
                    resId = R.id.moveFragment,
                    args = MoveFragmentArgs(arrayOf(navigationArgs.threadUid)).toBundle(),
                    currentClassName = currentClassName,
                )
            }
        }

        override fun onSnooze() {
            trackBottomSheetThreadActionsEvent(MatomoName.Snooze)
            setBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET, SnoozeScheduleType.Snooze(threadMessageToExecuteAction.thread.uid))
        }

        override fun onModifySnooze() {
            trackBottomSheetThreadActionsEvent(MatomoName.ModifySnooze)
            setBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET, SnoozeScheduleType.Modify(threadMessageToExecuteAction.thread.uid))
        }

        override fun onCancelSnooze() {
            trackBottomSheetThreadActionsEvent(MatomoName.CancelSnooze)
            lifecycleScope.launch { mainViewModel.unsnoozeThreads(listOf(threadMessageToExecuteAction.thread)) }
            twoPaneViewModel.closeThread()
        }

        override fun onFavorite() {
            trackBottomSheetThreadActionsEvent(MatomoName.Favorite, threadMessageToExecuteAction.thread.isFavorite)
            mainViewModel.toggleThreadFavoriteStatus(navigationArgs.threadUid)
        }

        override fun onReportJunk() {
            if (isFromSpam) {
                trackBottomSheetThreadActionsEvent(MatomoName.Spam, value = true)
                mainViewModel.toggleThreadSpamStatus(listOf(navigationArgs.threadUid))
            } else {
                safeNavigate(
                    resId = R.id.junkBottomSheetDialog,
                    args = JunkBottomSheetDialogArgs(arrayOf(threadMessageToExecuteAction.thread.uid)).toBundle(),
                    currentClassName = currentClassName,
                )
            }
        }

        override fun onPrint() {
            trackBottomSheetThreadActionsEvent(MatomoName.Print)
            notYetImplemented()
        }

        override fun onShare() {
            activity?.apply {
                trackBottomSheetThreadActionsEvent(MatomoName.ShareLink)
                mainViewModel.shareThreadUrl(threadMessageToExecuteAction.messageUid)
            }
        }

        override fun onSaveToKDrive() {
            trackBottomSheetThreadActionsEvent(MatomoName.SaveToKDrive)
            navigateToDownloadMessagesProgressDialog(
                messageUids = threadMessageToExecuteAction.thread.messages.map { it.uid },
                currentClassName = ThreadActionsBottomSheetDialog::class.java.name,
            )
        }

        override fun onReportDisplayProblem() {
            descriptionDialog.show(
                title = getString(R.string.reportDisplayProblemTitle),
                description = getString(R.string.reportDisplayProblemDescription),
                onPositiveButtonClicked = {
                    mainViewModel.reportDisplayProblem(threadMessageToExecuteAction.messageUid)
                },
            )
        }
        //endregion
    }

    companion object {
        const val OPEN_SNOOZE_BOTTOM_SHEET = "openSnoozeBottomSheet"
    }
}
