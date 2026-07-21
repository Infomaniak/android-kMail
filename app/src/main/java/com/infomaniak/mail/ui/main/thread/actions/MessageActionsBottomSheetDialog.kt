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
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.common.extensions.isNightModeEnabled
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackBottomSheetMessageActionsEvent
import com.infomaniak.mail.MatomoMail.trackBottomSheetThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.extensions.folder
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.ThreadFilter
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.folder.ThreadListFragment
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerAction
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerFragmentArgs
import com.infomaniak.mail.ui.main.search.SearchFragment
import com.infomaniak.mail.ui.main.search.SearchViewModel
import com.infomaniak.mail.ui.main.thread.PrintMailFragmentArgs
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_AI_ACTIONS_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_REACTION_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_REMINDER_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.actions.ThreadActionsBottomSheetDialog.Companion.setBlockUserUi
import com.infomaniak.mail.ui.main.thread.actions.ThreadActionsBottomSheetDialog.Companion.setSpamUi
import com.infomaniak.mail.ui.main.thread.actions.multiselection.MultiselectionViewModel
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.archiveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.moveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.navigateToDownloadMessagesProgressDialog
import com.infomaniak.mail.utils.extensions.replyWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.safeNavigateToNewMessageActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.infomaniak.core.common.R as RCore

@AndroidEntryPoint
class MessageActionsBottomSheetDialog : MailActionsBottomSheetDialog() {

    private val navigationArgs: MessageActionsBottomSheetDialogArgs by navArgs()

    override val multiselectionViewModel: MultiselectionViewModel by activityViewModels()
    private val actionsViewModel: ActionsViewModel by activityViewModels()
    private val searchViewModel: SearchViewModel by activityViewModels()
    private val junkMessagesViewModel: JunkMessagesViewModel by activityViewModels()

    private val currentClassName: String by lazy { MessageActionsBottomSheetDialog::class.java.name }

    override val shouldCloseMultiSelection: Boolean = false

    override val substituteClassName: String by lazy {
        if (navigationArgs.isFromSearch) {
            SearchFragment::class.java.name
        } else {
            ThreadListFragment::class.java.name
        }
    }

    private var isFromSpam: Boolean = false
    private var isFromArchive: Boolean = false
    private var isFromDraft: Boolean = false

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    @Inject
    lateinit var folderRoleUtils: FolderRoleUtils

    @Inject
    lateinit var snackbarManager: SnackbarManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)
        binding.print.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            val message = mainViewModel.getMessage(messageUid)
            if (message == null) {
                snackbarManager.postValue(requireContext().getString(RCore.string.anErrorHasOccurred))
                return@launch
            }

            // Initialization of message to populate junkMessages and potentialUsersToBlock
            junkMessagesViewModel.messages = listOf(message)

            val folderRole = folderRoleUtils.getActionFolderRole(message)
            isFromSpam = folderRole == FolderRole.SPAM
            isFromDraft = folderRole == FolderRole.DRAFT
            isFromArchive = folderRole == FolderRole.ARCHIVE

            setMarkAsReadUi(message.isSeen)
            setArchiveUi(isFromArchive, isFromDraft)
            setFavoriteUi(message.isFavorite, isFromDraft)
            setReactionUi(message.isValidReactionTarget)
            setSpamUi(binding.spam, isFromSpam, isFromDraft)
            setMainActionUi(isFromDraft)
            setMoveUi(isFromDraft)
            setMarkUnreadUi(isFromDraft)
            setReportPhishingUi(isFromDraft)
            setAskEuriaUi(isVisible = true)
            setReminderUi(
                isFromDraft = isFromDraft,
                from = message.from,
                aliases = mainViewModel.currentMailbox.value?.aliases,
                featureFlags = mainViewModel.currentMailbox.value?.featureFlags,
            )

            observeReportPhishingResult()
            observePotentialBlockedSenders()

            if (requireContext().isNightModeEnabled()) {
                binding.lightTheme.apply {
                    isVisible = true
                    setTitle(if (isThemeTheSame) R.string.actionViewInLight else R.string.actionViewInDark)
                    setClosingOnClickListener { mainViewModel.toggleLightThemeForMessage.value = message }
                }
            }

            val messageFolderRole = folderRoleUtils.getActionFolderRole(message)
            initActionClickListener(messageUid, message, threadUid, messageFolderRole)
        }
        Unit
    }

    private fun observeReportPhishingResult() {
        actionsViewModel.reportPhishingTrigger.observe(viewLifecycleOwner) {
            descriptionDialog.resetLoadingAndDismiss()
            findNavController().popBackStack()
        }
    }

    private fun observePotentialBlockedSenders() {
        junkMessagesViewModel.potentialBlockedUsers.observe(viewLifecycleOwner) { potentialUsersToBlock ->
            setBlockUserUi(binding.blockSender, potentialUsersToBlock, isFromSpam, isFromDraft)
        }
    }

    private fun handleReply(isReplyAll: Boolean, message: Message, messageUid: String) {
        val activity = requireActivity() as MainActivity
        val hasNoReplyRecipients = SharedUtils.hasNoReplyRecipients(message, isReplyAll)
        descriptionDialog.replyWithConfirmationPopup(
            hasNoReplyRecipients = hasNoReplyRecipients,
            onPositiveButtonClicked = {
                trackBottomSheetMessageActionsEvent(if (isReplyAll) MatomoName.ReplyAll else MatomoName.Reply)
                safeNavigateToNewMessageActivity(
                    currentActivity = activity,
                    draftMode = if (isReplyAll) DraftMode.REPLY_ALL else DraftMode.REPLY,
                    previousMessageUid = messageUid,
                    currentClassName = currentClassName,
                    shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
                )
            }
        )
    }

    private fun initActionClickListener(messageUid: String, message: Message, threadUid: String, messageFolderRole: FolderRole?) {
        initOnClickListener(object : OnActionClick {
            //region Main actions
            override fun onReply() {
                handleReply(isReplyAll = false, message, messageUid)
            }

            override fun onReplyAll() {
                handleReply(isReplyAll = true, message, messageUid)
            }

            override fun onForward() {
                trackBottomSheetMessageActionsEvent(MatomoName.Forward)
                safeNavigateToNewMessageActivity(
                    draftMode = DraftMode.FORWARD,
                    previousMessageUid = messageUid,
                    currentClassName = currentClassName,
                    shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
                )
            }

            override fun onDelete() {
                descriptionDialog.deleteWithConfirmationPopup(
                    listOfNotNull(messageFolderRole),
                    currentFolderRole = mainViewModel.currentFolder.value?.role,
                    count = 1
                ) {
                    trackBottomSheetMessageActionsEvent(MatomoName.Delete)
                    actionsViewModel.deleteMessages(
                        messages = listOf(message),
                        parentFolderId = message.folderId,
                        mailbox = mainViewModel.currentMailbox.value!!,
                    )
                }
            }
            //endregion

            //region Actions
            override fun onArchive() {
                descriptionDialog.archiveWithConfirmationPopup(messageFolderRole, count = 1) {
                    trackBottomSheetMessageActionsEvent(MatomoName.Archive, message.folder.role == FolderRole.ARCHIVE)
                    actionsViewModel.archiveMessages(
                        messages = listOf(message),
                        parentFolderId = message.folderId,
                        mailbox = mainViewModel.currentMailbox.value!!,
                    )
                }
            }

            override fun onReadUnread() {
                trackBottomSheetMessageActionsEvent(MatomoName.MarkAsSeen, message.isSeen)
                actionsViewModel.toggleMessagesSeenStatus(
                    messages = listOf(message),
                    parentFolderId = message.folderId,
                    mailbox = mainViewModel.currentMailbox.value!!,
                    shouldRefreshSearch = searchViewModel.currentFilters.contains(ThreadFilter.SEEN) || searchViewModel.currentFilters.contains(
                        ThreadFilter.UNSEEN
                    )
                )
                twoPaneViewModel.closeThread()
            }

            override fun onMove() {
                trackBottomSheetMessageActionsEvent(MatomoName.Move)
                val navController = findNavController()
                descriptionDialog.moveWithConfirmationPopup(folderRole = message.folder.role, count = 1) {
                    navController.animatedNavigation(
                        resId = R.id.folderPickerFragment,
                        args = FolderPickerFragmentArgs(
                            action = FolderPickerAction.MOVE,
                            threadsUids = arrayOf(threadUid),
                            messagesUids = arrayOf(messageUid),
                            sourceFolderId = message.folderId,
                        ).toBundle(),
                    )
                }
            }

            override fun onAddReaction() {
                trackBottomSheetMessageActionsEvent(MatomoName.OpenEmojiPicker)
                setBackNavigationResult(OPEN_REACTION_BOTTOM_SHEET, messageUid)
            }

            override fun onReminder() {
                // TODO: matomo
                setBackNavigationResult(OPEN_REMINDER_BOTTOM_SHEET, messageUid)
            }

            override fun onSnooze() = Unit

            override fun onModifySnooze() = Unit

            override fun onCancelSnooze() = Unit

            override fun onFavorite() {
                trackBottomSheetMessageActionsEvent(MatomoName.Favorite, message.isFavorite)
                actionsViewModel.toggleMessagesFavoriteStatus(
                    messages = listOf(message),
                    mailbox = mainViewModel.currentMailbox.value!!,
                    shouldRefreshSearch = searchViewModel.currentFilters.contains(ThreadFilter.STARRED)
                )
            }

            override fun onSpam() {
                trackBottomSheetMessageActionsEvent(MatomoName.Spam, value = isFromSpam)
                actionsViewModel.toggleMessagesSpamStatus(
                    messages = listOf(message),
                    parentFolderId = message.folderId,
                    mailbox = mainViewModel.currentMailbox.value!!,
                )
            }

            override fun onPhishing() {
                trackBottomSheetMessageActionsEvent(MatomoName.SignalPhishing)
                descriptionDialog.show(
                    title = getString(R.string.reportPhishingTitle),
                    description = resources.getQuantityString(R.plurals.reportPhishingDescription, 1),
                    onPositiveButtonClicked = {
                        actionsViewModel.reportPhishing(
                            messages = listOf(message),
                            parentFolderId = message.folderId,
                            mailbox = mainViewModel.currentMailbox.value!!,
                        )
                    },
                )
            }

            override fun onBlockSender() {
                trackBottomSheetMessageActionsEvent(MatomoName.BlockUser)
                val potentialUsersToBlock = junkMessagesViewModel.potentialBlockedUsers.value
                if (potentialUsersToBlock == null) {
                    snackbarManager.postValue(getString(RCore.string.anErrorHasOccurred))
                    SentryLog.e(TAG, getString(R.string.sentryErrorPotentialUsersToBlockNull))
                    return
                }

                potentialUsersToBlock.values.firstOrNull()?.let { message ->
                    junkMessagesViewModel.messageOfUserToBlock.value = message
                }
            }

            override fun onPrint() {
                trackBottomSheetMessageActionsEvent(MatomoName.Print)
                safeNavigate(
                    resId = R.id.printMailFragment,
                    args = PrintMailFragmentArgs(messageUid).toBundle(),
                    currentClassName = MessageActionsBottomSheetDialog::class.java.name,
                )
            }

            override fun onShare() {
                activity?.apply {
                    trackBottomSheetThreadActionsEvent(MatomoName.ShareLink)
                    mainViewModel.shareThreadUrl(message.uid)
                }
            }

            override fun onSaveToKDrive() {
                trackBottomSheetThreadActionsEvent(MatomoName.SaveToKDrive)
                navigateToDownloadMessagesProgressDialog(
                    messageUids = listOf(messageUid),
                    currentClassName = MessageActionsBottomSheetDialog::class.java.name,
                )
            }

            override fun onReportDisplayProblem() {
                descriptionDialog.show(
                    title = getString(R.string.reportDisplayProblemTitle),
                    description = getString(R.string.reportDisplayProblemDescription),
                    onPositiveButtonClicked = { mainViewModel.reportDisplayProblem(message.uid) },
                )
            }

            override fun onAskEuria() {
                trackBottomSheetThreadActionsEvent(MatomoName.AskEuria)
                setBackNavigationResult(OPEN_AI_ACTIONS_BOTTOM_SHEET, message.uid)
            }
            //endregion
        })
    }

    companion object {
        const val TAG = "MessageActionsBottomSheetDialog"
    }
}
