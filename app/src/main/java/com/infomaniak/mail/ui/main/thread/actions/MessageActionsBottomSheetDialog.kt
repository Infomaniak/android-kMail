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
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.ui.main.menu.MoveFragmentArgs
import com.infomaniak.mail.utils.animatedNavigation
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity

class MessageActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private val navigationArgs: MessageActionsBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel.getMessage(messageUid).observe(viewLifecycleOwner) { message ->

            if (message == null) return@observe

            setMarkAsReadUi(isSeen)
            setFavoriteUi(isFavorite)

            initOnClickListener(object : OnActionClick {
                //region Main actions
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
                //endregion

                //region Actions
                override fun onArchive() {
                    mainViewModel.archiveThreadOrMessage(threadUid, message)
                }

                override fun onReadUnread() {
                    mainViewModel.toggleSeenStatus(threadUid, message)
                }

                override fun onMove() {
                    animatedNavigation(
                        resId = R.id.moveFragment,
                        args = MoveFragmentArgs(message.folderId, threadUid, messageUid).toBundle(),
                        currentClassName = MessageActionsBottomSheetDialog::class.java.name,
                    )
                }

                override fun onPostpone() {
                    notYetImplemented()
                }

                override fun onFavorite() {
                    mainViewModel.toggleFavoriteStatus(threadUid, message)
                }

                override fun onSpam() = Unit

                override fun onReportJunk() {
                    safeNavigate(
                        R.id.junkBottomSheetDialog,
                        JunkBottomSheetDialogArgs(threadUid, messageUid).toBundle(),
                        currentClassName = MessageActionsBottomSheetDialog::class.java.name
                    )
                }

                override fun onPrint() {
                    notYetImplemented()
                }

                override fun onReportDisplayProblem() {
                    notYetImplemented()
                }
                //endregion
            })
        }
    }
}
