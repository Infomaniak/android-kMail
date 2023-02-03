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
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.setBackNavigationResult
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity

class MessageActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private val navigationArgs: MessageActionsBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val threadUid = navigationArgs.threadUid
        val messageUid = navigationArgs.messageUid

        mainViewModel.getMessage(messageUid).observe(viewLifecycleOwner) { message ->

            setMarkAsReadUi(navigationArgs.isSeen)
            setFavoriteUi(navigationArgs.isFavorite)

            initOnClickListener(object : OnActionClick {
                override fun onArchive() {
                    mainViewModel.archiveThreadOrMessage(threadUid, message)
                }

                override fun onReadUnread() {
                    mainViewModel.toggleSeenStatus(threadUid, message)
                }

                override fun onMove() {
                    notYetImplemented()
                }

                override fun onPostpone() {
                    notYetImplemented()
                }

                override fun onFavorite() {
                    mainViewModel.toggleFavoriteStatus(threadUid, message)
                }

                override fun onReportJunk() {
                    setBackNavigationResult(JUNK_BOTTOM_SHEET_NAV_KEY, Bundle().apply {
                        putBoolean(SHOULD_OPEN_JUNK, true)
                        putString(MESSAGE_UID, messageUid)
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
                    safeNavigateToNewMessageActivity(DraftMode.REPLY, messageUid)
                }

                override fun onReplyAll() {
                    safeNavigateToNewMessageActivity(DraftMode.REPLY_ALL, messageUid)
                }

                override fun onForward() {
                    notYetImplemented()
                }

                override fun onDelete() {
                    mainViewModel.deleteThreadOrMessage(threadUid, message)
                }
            })
        }
    }
}
