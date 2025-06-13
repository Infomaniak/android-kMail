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
import com.infomaniak.mail.MatomoMail.ACTION_ARCHIVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_CANCEL_SNOOZE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_DELETE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FAVORITE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FORWARD_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MARK_AS_SEEN_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MODIFY_SNOOZE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MOVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_PRINT_NAME
import com.infomaniak.mail.MatomoMail.ACTION_REPLY_ALL_NAME
import com.infomaniak.mail.MatomoMail.ACTION_REPLY_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SAVE_TO_KDRIVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SHARE_LINK_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SNOOZE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SPAM_NAME
import com.infomaniak.mail.MatomoMail.trackBottomSheetThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.move.MoveFragmentArgs
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.extensions.*
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
    lateinit var localSettings: LocalSettings

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(threadActionsViewModel) {
        super.onViewCreated(view, savedInstanceState)

        threadLive.observe(viewLifecycleOwner) { thread ->

            folderRole = thread.folderRole
            isFromArchive = folderRole == FolderRole.ARCHIVE
            isFromSpam = folderRole == FolderRole.SPAM

            setMarkAsReadUi(thread.isSeen)
            setArchiveUi(isFromArchive)
            setFavoriteUi(thread.isFavorite)
            setJunkUi()
            setSnoozeUi(thread.isSnoozed())
        }

        getThreadAndMessageUidToReplyTo().observe(viewLifecycleOwner) { result ->
            result?.let { (thread, messageUidToReply) ->
                setupListeners(thread, messageUidToReply)
            } ?: findNavController().popBackStack()
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

    private fun setupListeners(thread: Thread, messageUidToReply: String) = with(navigationArgs) {
        initOnClickListener(
            listener = object : OnActionClick {
                //region Main actions
                override fun onReply() {
                    trackBottomSheetThreadActionsEvent(ACTION_REPLY_NAME)
                    safeNavigateToNewMessageActivity(
                        draftMode = DraftMode.REPLY,
                        previousMessageUid = messageUidToReply,
                        currentClassName = currentClassName,
                        shouldLoadDistantResources = shouldLoadDistantResources,
                    )
                }

                override fun onReplyAll() {
                    trackBottomSheetThreadActionsEvent(ACTION_REPLY_ALL_NAME)
                    safeNavigateToNewMessageActivity(
                        draftMode = DraftMode.REPLY_ALL,
                        previousMessageUid = messageUidToReply,
                        currentClassName = currentClassName,
                        shouldLoadDistantResources = shouldLoadDistantResources,
                    )
                }

                override fun onForward() {
                    trackBottomSheetThreadActionsEvent(ACTION_FORWARD_NAME)
                    safeNavigateToNewMessageActivity(
                        draftMode = DraftMode.FORWARD,
                        previousMessageUid = messageUidToReply,
                        currentClassName = currentClassName,
                        shouldLoadDistantResources = shouldLoadDistantResources,
                    )
                }

                override fun onDelete() {
                    descriptionDialog.deleteWithConfirmationPopup(folderRole, count = 1) {
                        trackBottomSheetThreadActionsEvent(ACTION_DELETE_NAME)
                        mainViewModel.deleteThread(threadUid)
                    }
                }
                //endregion

                //region Actions
                override fun onArchive() {
                    descriptionDialog.archiveWithConfirmationPopup(folderRole, count = 1) {
                        trackBottomSheetThreadActionsEvent(ACTION_ARCHIVE_NAME, isFromArchive)
                        mainViewModel.archiveThread(threadUid)
                    }
                }

                override fun onReadUnread() {
                    trackBottomSheetThreadActionsEvent(ACTION_MARK_AS_SEEN_NAME, value = thread.isSeen)
                    mainViewModel.toggleThreadSeenStatus(threadUid)
                    twoPaneViewModel.closeThread()
                }

                override fun onMove() {
                    val navController = findNavController()
                    descriptionDialog.moveWithConfirmationPopup(folderRole, count = 1) {
                        trackBottomSheetThreadActionsEvent(ACTION_MOVE_NAME)
                        navController.animatedNavigation(
                            resId = R.id.moveFragment,
                            args = MoveFragmentArgs(arrayOf(threadUid)).toBundle(),
                            currentClassName = currentClassName,
                        )
                    }
                }

                override fun onSnooze() {
                    trackBottomSheetThreadActionsEvent(ACTION_SNOOZE_NAME)
                    setBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET, SnoozeScheduleType.Snooze(thread.uid))
                }

                override fun onModifySnooze() {
                    trackBottomSheetThreadActionsEvent(ACTION_MODIFY_SNOOZE_NAME)
                    setBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET, SnoozeScheduleType.Modify(thread.uid))
                }

                override fun onCancelSnooze() {
                    trackBottomSheetThreadActionsEvent(ACTION_CANCEL_SNOOZE_NAME)
                    lifecycleScope.launch { mainViewModel.unsnoozeThreads(listOf(thread)) }
                    twoPaneViewModel.closeThread()
                }

                override fun onFavorite() {
                    trackBottomSheetThreadActionsEvent(ACTION_FAVORITE_NAME, thread.isFavorite)
                    mainViewModel.toggleThreadFavoriteStatus(threadUid)
                }

                override fun onReportJunk() {
                    if (isFromSpam) {
                        trackBottomSheetThreadActionsEvent(ACTION_SPAM_NAME, value = true)
                        mainViewModel.toggleThreadSpamStatus(threadUid)
                    } else {
                        safeNavigate(
                            resId = R.id.junkBottomSheetDialog,
                            args = JunkBottomSheetDialogArgs(threadUid, messageUidToReply).toBundle(),
                            currentClassName = currentClassName,
                        )
                    }
                }

                override fun onPrint() {
                    trackBottomSheetThreadActionsEvent(ACTION_PRINT_NAME)
                    notYetImplemented()
                }

                override fun onShare() {
                    activity?.apply {
                        trackBottomSheetThreadActionsEvent(ACTION_SHARE_LINK_NAME)
                        mainViewModel.shareThreadUrl(messageUidToReply)
                    }
                }

                override fun onSaveToKDrive() {
                    trackBottomSheetThreadActionsEvent(ACTION_SAVE_TO_KDRIVE_NAME)
                    navigateToDownloadMessagesProgressDialog(
                        messageUids = thread.messages.map { it.uid },
                        currentClassName = ThreadActionsBottomSheetDialog::class.java.name,
                    )
                }

                override fun onReportDisplayProblem() {
                    descriptionDialog.show(
                        title = getString(R.string.reportDisplayProblemTitle),
                        description = getString(R.string.reportDisplayProblemDescription),
                        onPositiveButtonClicked = { mainViewModel.reportDisplayProblem(messageUidToReply) },
                    )
                }
                //endregion
            },
        )
    }

    companion object {
        const val OPEN_SNOOZE_BOTTOM_SHEET = "openSnoozeBottomSheet"
    }
}
