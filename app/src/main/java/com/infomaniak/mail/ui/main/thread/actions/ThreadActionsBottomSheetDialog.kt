/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.common.observe
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackBottomSheetThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.folder.ThreadListFragment
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerAction
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerFragmentArgs
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_REACTION_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.SharedUtils
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
import com.infomaniak.core.common.R as RCore

@AndroidEntryPoint
class ThreadActionsBottomSheetDialog : MailActionsBottomSheetDialog() {

    private val navigationArgs: ThreadActionsBottomSheetDialogArgs by navArgs()
    private val threadActionsViewModel: ThreadActionsViewModel by viewModels()
    private val junkMessagesViewModel: JunkMessagesViewModel by activityViewModels()

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

    @Inject
    lateinit var snackbarManager: SnackbarManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        threadActionsViewModel.threadMessagesWithActionAndReaction
            .observe(viewLifecycleOwner) { (thread, messageUidToExecuteAction, messageUidToReactTo) ->
                // Initialization of threadsUids to populate junkMessages and potentialUsersToBlock
                junkMessagesViewModel.threadsUids = listOf(thread.uid)

                folderRole = folderRoleUtils.getActionFolderRole(thread)
                isFromArchive = folderRole == FolderRole.ARCHIVE
                isFromSpam = folderRole == FolderRole.SPAM

                setMarkAsReadUi(thread.isSeen)
                setArchiveUi(isFromArchive)
                setFavoriteUi(thread.isFavorite)
                setSnoozeUi(thread.isSnoozed())
                setReactionUi(canBeReactedTo = messageUidToReactTo != null)
                setSpamPhishingUi(binding.spam, binding.phishing, isFromSpam)

                initOnClickListener(onActionClick(thread, messageUidToExecuteAction, messageUidToReactTo))
            }

        observePotentialBlockedUsers()
        observeReportPhishingResult()
    }

    private fun setSnoozeUi(isThreadSnoozed: Boolean) = with(binding) {
        val shouldDisplaySnoozeActions = SharedUtils.shouldDisplaySnoozeActions(mainViewModel, localSettings, folderRole)

        snooze.isVisible = shouldDisplaySnoozeActions && isThreadSnoozed.not()
        modifySnooze.isVisible = shouldDisplaySnoozeActions && isThreadSnoozed
        cancelSnooze.isVisible = shouldDisplaySnoozeActions && isThreadSnoozed
    }

    private fun observePotentialBlockedUsers() {
        junkMessagesViewModel.potentialBlockedUsers.observe(viewLifecycleOwner) { potentialUsersToBlock ->
            setBlockUserUi(binding.blockSender, potentialUsersToBlock, isFromSpam)
        }
    }

    private fun observeReportPhishingResult() {
        mainViewModel.reportPhishingTrigger.observe(viewLifecycleOwner) {
            descriptionDialog.resetLoadingAndDismiss()
            findNavController().popBackStack()
        }
    }

    private fun onActionClick(
        thread: Thread,
        messageUidToExecuteAction: String,
        messageUidToReactTo: String?
    ) = object : OnActionClick {
        //region Main actions
        override fun onReply() {
            trackBottomSheetThreadActionsEvent(MatomoName.Reply)
            safeNavigateToNewMessageActivity(
                draftMode = DraftMode.REPLY,
                previousMessageUid = messageUidToExecuteAction,
                currentClassName = currentClassName,
                shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
            )
        }

        override fun onReplyAll() {
            trackBottomSheetThreadActionsEvent(MatomoName.ReplyAll)
            safeNavigateToNewMessageActivity(
                draftMode = DraftMode.REPLY_ALL,
                previousMessageUid = messageUidToExecuteAction,
                currentClassName = currentClassName,
                shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
            )
        }

        override fun onForward() {
            trackBottomSheetThreadActionsEvent(MatomoName.Forward)
            safeNavigateToNewMessageActivity(
                draftMode = DraftMode.FORWARD,
                previousMessageUid = messageUidToExecuteAction,
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
            trackBottomSheetThreadActionsEvent(MatomoName.MarkAsSeen, value = thread.isSeen)
            mainViewModel.toggleThreadSeenStatus(navigationArgs.threadUid)
            twoPaneViewModel.closeThread()
        }

        override fun onMove() {
            val navController = findNavController()
            descriptionDialog.moveWithConfirmationPopup(folderRole, count = 1) {
                trackBottomSheetThreadActionsEvent(MatomoName.Move)
                navController.animatedNavigation(
                    resId = R.id.folderPickerFragment,
                    args = FolderPickerFragmentArgs(
                        threadsUids = arrayOf(navigationArgs.threadUid),
                        action = FolderPickerAction.MOVE,
                        sourceFolderId = mainViewModel.currentFolderId ?: Folder.DUMMY_FOLDER_ID
                    ).toBundle(),
                )
            }
        }

        override fun onAddReaction() {
            trackBottomSheetThreadActionsEvent(MatomoName.OpenEmojiPicker)
            setBackNavigationResult(OPEN_REACTION_BOTTOM_SHEET, messageUidToReactTo)
        }

        override fun onSnooze() {
            trackBottomSheetThreadActionsEvent(MatomoName.Snooze)
            setBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET, SnoozeScheduleType.Snooze(thread.uid))
        }

        override fun onModifySnooze() {
            trackBottomSheetThreadActionsEvent(MatomoName.ModifySnooze)
            setBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET, SnoozeScheduleType.Modify(thread.uid))
        }

        override fun onCancelSnooze() {
            trackBottomSheetThreadActionsEvent(MatomoName.CancelSnooze)
            lifecycleScope.launch { mainViewModel.unsnoozeThreads(listOf(thread)) }
            twoPaneViewModel.closeThread()
        }

        override fun onFavorite() {
            trackBottomSheetThreadActionsEvent(MatomoName.Favorite, thread.isFavorite)
            mainViewModel.toggleThreadFavoriteStatus(navigationArgs.threadUid)
        }

        override fun onSpam() {
            if (isFromSpam) {
                trackBottomSheetThreadActionsEvent(MatomoName.Spam, value = true)
                mainViewModel.toggleThreadSpamStatus(listOf(thread.uid))
            } else {
                trackBottomSheetThreadActionsEvent(MatomoName.Spam, value = false)
                mainViewModel.toggleThreadSpamStatus(listOf(thread.uid))
            }
        }

        override fun onPhishing() {
            trackBottomSheetThreadActionsEvent(MatomoName.SignalPhishing)
            val junkMessages = junkMessagesViewModel.junkMessages.value ?: emptyList()

            if (junkMessages.isEmpty()) {
                //An error will be shown to the user in the reportPhishing function
                //This should never happen, that's why we add a SentryLog.
                SentryLog.e(TAG, getString(R.string.sentryErrorPhishingMessagesEmpty))
            }

            descriptionDialog.show(
                title = getString(R.string.reportPhishingTitle),
                description = resources.getQuantityString(R.plurals.reportPhishingDescription, thread.messages.count()),
                onPositiveButtonClicked = { mainViewModel.reportPhishing(junkMessagesViewModel.threadsUids, junkMessages) },
            )
        }

        override fun onBlockSender() {
            trackBottomSheetThreadActionsEvent(MatomoName.BlockUser)
            val potentialUsersToBlock = junkMessagesViewModel.potentialBlockedUsers.value
            if (potentialUsersToBlock == null) {
                snackbarManager.postValue(getString(RCore.string.anErrorHasOccurred))
                SentryLog.e(TAG, getString(R.string.sentryErrorPotentialUsersToBlockNull))
                return
            }

            if (potentialUsersToBlock.count() > 1) {
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

        override fun onPrint() {
            trackBottomSheetThreadActionsEvent(MatomoName.Print)
            notYetImplemented()
        }

        override fun onShare() {
            activity?.apply {
                trackBottomSheetThreadActionsEvent(MatomoName.ShareLink)
                mainViewModel.shareThreadUrl(messageUidToExecuteAction)
            }
        }

        override fun onSaveToKDrive() {
            trackBottomSheetThreadActionsEvent(MatomoName.SaveToKDrive)
            navigateToDownloadMessagesProgressDialog(
                messageUids = thread.messages.map { it.uid },
                currentClassName = ThreadActionsBottomSheetDialog::class.java.name,
            )
        }

        override fun onReportDisplayProblem() {
            descriptionDialog.show(
                title = getString(R.string.reportDisplayProblemTitle),
                description = getString(R.string.reportDisplayProblemDescription),
                onPositiveButtonClicked = { mainViewModel.reportDisplayProblem(messageUidToExecuteAction) },
            )
        }
        //endregion
    }

    companion object {
        const val TAG = "ThreadActionsBottomSheetDialog"
        const val OPEN_SNOOZE_BOTTOM_SHEET = "openSnoozeBottomSheet"

        fun setSpamPhishingUi(spam: ActionItemView, phishing: ActionItemView, isFromSpam: Boolean) {
            spam.apply {
                val (text, icon) = if (isFromSpam) {
                    R.string.actionNonSpam to R.drawable.ic_non_spam
                } else {
                    R.string.actionSpam to R.drawable.ic_spam
                }

                setTitle(text)
                setIconResource(icon)
                isVisible = true
            }

            phishing.isVisible = !isFromSpam
        }

        fun setBlockUserUi(blockSender: ActionItemView, potentialUsersToBlock: Map<Recipient, Message>, isFromSpam: Boolean) {
            blockSender.isGone = potentialUsersToBlock.count() == 0 || isFromSpam
        }
    }
}
