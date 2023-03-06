/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.ui.main.thread.actions

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.MatomoMail.toMailActionValue
import com.infomaniak.mail.MatomoMail.trackMailActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.ui.main.menu.MoveFragmentArgs
import com.infomaniak.mail.utils.animatedNavigation
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity

class ThreadActionsBottomSheetDialog : MailActionsBottomSheetDialog() {

    private val navigationArgs: ThreadActionsBottomSheetDialogArgs by navArgs()
    private val threadActionsViewModel: ThreadActionsViewModel by viewModels()

    override val currentClassName: String by lazy { ThreadActionsBottomSheetDialog::class.java.name }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        threadActionsViewModel.threadLive(threadUid).observe(viewLifecycleOwner) { thread ->
            setMarkAsReadUi(thread.unseenMessagesCount == 0)
            setFavoriteUi(thread.isFavorite)
        }

        binding.postpone.isGone = true
        setSpamUi()

        threadActionsViewModel.getMessageUidToReplyTo(
            threadUid,
            messageUidToReplyTo,
        ).observe(viewLifecycleOwner) { (thread, messageUidToReply) ->

            initOnClickListener(object : OnActionClick {
                //region Main actions
                override fun onReply() {
                    trackMailActionsEvent("replyThread")
                    safeNavigateToNewMessageActivity(DraftMode.REPLY, messageUidToReply, currentClassName)
                }

                override fun onReplyAll() {
                    trackMailActionsEvent("replyAllThread")
                    safeNavigateToNewMessageActivity(DraftMode.REPLY_ALL, messageUidToReply, currentClassName)
                }

                override fun onForward() {
                    trackMailActionsEvent("forwardThread")
                    safeNavigateToNewMessageActivity(DraftMode.FORWARD, messageUidToReply, currentClassName)
                }

                override fun onDelete() {
                    trackMailActionsEvent("trashThread")
                    mainViewModel.deleteThreadOrMessage(threadUid)
                }
                //endregion

                //region Actions
                override fun onArchive(): Unit = with(mainViewModel) {
                    trackMailActionsEvent("archiveThread", isCurrentFolderRole(FolderRole.ARCHIVE).toMailActionValue())
                    mainViewModel.archiveThreadOrMessage(threadUid)
                }

                override fun onReadUnread() {
                    trackMailActionsEvent("markAsSeenThread", (thread.unseenMessagesCount == 0).toMailActionValue())
                    mainViewModel.toggleSeenStatus(threadUid)
                    findNavController().popBackStack(R.id.threadFragment, inclusive = true)
                }

                override fun onMove() {
                    trackMailActionsEvent("moveThread")
                    animatedNavigation(
                        resId = R.id.moveFragment,
                        args = MoveFragmentArgs(threadUid).toBundle(),
                        currentClassName = currentClassName,
                    )
                }

                override fun onPostpone() {
                    trackMailActionsEvent("postponeThread")
                    TODO("Not yet implemented")
                }

                override fun onFavorite() {
                    trackMailActionsEvent("favoriteThread", thread.isFavorite.toMailActionValue())
                    mainViewModel.toggleFavoriteStatus(threadUid)
                }

                override fun onSpam(): Unit = with(mainViewModel) {
                    trackMailActionsEvent("spamThread", isCurrentFolderRole(FolderRole.SPAM).toMailActionValue())
                    toggleSpamOrHam(threadUid)
                }

                override fun onReportJunk() = Unit

                override fun onPrint() {
                    trackMailActionsEvent("printThread")
                    notYetImplemented()
                }

                override fun onReportDisplayProblem() {
                    notYetImplemented()
                }
                //endregion
            })
        }
    }

    private fun setSpamUi() = with(binding) {
        reportJunk.isGone = true
        spam.apply {
            isVisible = true
            setText(if (mainViewModel.isCurrentFolderRole(FolderRole.SPAM)) R.string.actionNonSpam else R.string.actionSpam)
        }
    }
}
