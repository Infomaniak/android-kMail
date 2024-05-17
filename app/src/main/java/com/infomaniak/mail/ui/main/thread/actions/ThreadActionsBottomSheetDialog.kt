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
package com.infomaniak.mail.ui.main.thread.actions

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.ACTION_ARCHIVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_DELETE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FAVORITE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FORWARD_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MARK_AS_SEEN_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MOVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_POSTPONE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_PRINT_NAME
import com.infomaniak.mail.MatomoMail.ACTION_REPLY_ALL_NAME
import com.infomaniak.mail.MatomoMail.ACTION_REPLY_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SPAM_NAME
import com.infomaniak.mail.MatomoMail.trackBottomSheetThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.menu.MoveFragmentArgs
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.notYetImplemented
import com.infomaniak.mail.utils.extensions.safeNavigateToNewMessageActivity
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(threadActionsViewModel) {
        super.onViewCreated(view, savedInstanceState)

        threadLive.observe(viewLifecycleOwner) { thread ->

            folderRole = mainViewModel.getActionFolderRole(thread)
            isFromArchive = folderRole == FolderRole.ARCHIVE
            isFromSpam = folderRole == FolderRole.SPAM

            setMarkAsReadUi(thread.unseenMessagesCount == 0)
            setArchiveUi(isFromArchive)
            setFavoriteUi(thread.isFavorite)
            setJunkUi()
        }

        binding.postpone.isGone = true

        getThreadAndMessageUidToReplyTo().observe(viewLifecycleOwner) { result ->
            result?.let { (thread, messageUidToReply) ->
                setupListeners(thread, messageUidToReply)
            } ?: findNavController().popBackStack()
        }
    }

    private fun setJunkUi() = binding.reportJunk.apply {

        val (text, icon) = if (isFromSpam) {
            R.string.actionNonSpam to R.drawable.ic_non_spam
        } else {
            R.string.actionReportJunk to R.drawable.ic_report_junk
        }

        setText(text)
        setIconResource(icon)
        isVisible = true
    }

    private fun setupListeners(thread: Thread, messageUidToReply: String) = with(navigationArgs) {
        initOnClickListener(object : OnActionClick {
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
            override fun onArchive() = with(mainViewModel) {
                trackBottomSheetThreadActionsEvent(ACTION_ARCHIVE_NAME, isFromArchive)
                archiveThread(threadUid)
            }

            override fun onReadUnread() {
                trackBottomSheetThreadActionsEvent(ACTION_MARK_AS_SEEN_NAME, value = thread.unseenMessagesCount == 0)
                mainViewModel.toggleThreadSeenStatus(threadUid)
                twoPaneViewModel.closeThread()
            }

            override fun onMove() {
                trackBottomSheetThreadActionsEvent(ACTION_MOVE_NAME)
                animatedNavigation(R.id.moveFragment, MoveFragmentArgs(arrayOf(threadUid)).toBundle(), currentClassName)
            }

            override fun onPostpone() {
                trackBottomSheetThreadActionsEvent(ACTION_POSTPONE_NAME)
                notYetImplemented()
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

            override fun onReportDisplayProblem() {
                notYetImplemented()
            }
            //endregion
        })
    }
}
