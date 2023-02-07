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
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.setBackNavigationResult
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.utils.refreshObserve
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity

class ThreadActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private val navigationArgs: ThreadActionsBottomSheetDialogArgs by navArgs()
    private val threadActionsViewModel: ThreadActionsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        threadActionsViewModel.threadLive(threadUid).refreshObserve(viewLifecycleOwner) { thread ->
            setMarkAsReadUi(thread.unseenMessagesCount == 0)
            setFavoriteUi(thread.isFavorite)
        }

        binding.postpone.isGone = true

        threadActionsViewModel.getMessageUidToReplyTo(
            threadUid,
            navigationArgs.messageUidToReplyTo,
        ).observe(viewLifecycleOwner) { messageUidToReplyTo ->

            initOnClickListener(object : OnActionClick {
                override fun onArchive() {
                    mainViewModel.archiveThreadOrMessage(threadUid)
                }

                override fun onReadUnread() {
                    mainViewModel.toggleSeenStatus(threadUid)
                    findNavController().popBackStack(R.id.threadListFragment, false)
                }

                override fun onMove() {
                    notYetImplemented()
                }

                override fun onPostpone() {
                    TODO("Not yet implemented")
                }

                override fun onFavorite() {
                    mainViewModel.toggleFavoriteStatus(threadUid)
                }

                override fun onReportJunk() {
                    setBackNavigationResult(JUNK_BOTTOM_SHEET_NAV_KEY, Bundle().apply {
                        putBoolean(SHOULD_OPEN_JUNK, true)
                        putString(THREAD_UID, threadUid)
                    })
                }

                override fun onPrint() {
                    notYetImplemented()
                }

                override fun onReportDisplayProblem() {
                    notYetImplemented()
                }


                override fun onReply() {
                    safeNavigateToNewMessageActivity(DraftMode.REPLY, messageUidToReplyTo)
                }

                override fun onReplyAll() {
                    safeNavigateToNewMessageActivity(DraftMode.REPLY_ALL, messageUidToReplyTo)
                }

                override fun onForward() {
                    notYetImplemented()
                }

                override fun onDelete() {
                    mainViewModel.deleteThreadOrMessage(threadUid)
                }
            })
        }
    }
}
