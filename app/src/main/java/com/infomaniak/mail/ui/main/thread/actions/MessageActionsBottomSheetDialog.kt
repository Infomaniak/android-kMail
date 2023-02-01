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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.menu.MoveFragmentArgs
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity

class MessageActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private val navigationArgs: MessageActionsBottomSheetDialogArgs by navArgs()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val messageActionsViewModel: MessageActionsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        val threadUid = navigationArgs.threadUid
        val messageUid = navigationArgs.messageUid

        messageActionsViewModel.getMessage(messageUid).observe(viewLifecycleOwner) { message ->

            setMarkAsReadUi(navigationArgs.isSeen)
            setFavoriteUi(navigationArgs.isFavorite)
            setSpamUi(message)

            archive.setClosingOnClickListener { mainViewModel.archiveThreadOrMessage(threadUid, message) }
            markAsReadUnread.setClosingOnClickListener { mainViewModel.toggleSeenStatus(threadUid, message) }
            move.setClosingOnClickListener {
                safeNavigate(
                    resId = R.id.moveFragment,
                    args = MoveFragmentArgs(threadUid, messageUid).toBundle(),
                    currentClassName = MessageActionsBottomSheetDialog::class.java.name
                )
            }
            postpone.setClosingOnClickListener { notYetImplemented() }
            favorite.setClosingOnClickListener { mainViewModel.toggleFavoriteStatus(threadUid, message) }
            spam.setClosingOnClickListener { mainViewModel.toggleSpamOrHam(threadUid, message) }
            blockSender.setClosingOnClickListener { notYetImplemented() }
            phishing.setClosingOnClickListener { notYetImplemented() }
            print.setClosingOnClickListener { notYetImplemented() }
            saveAsPdf.setClosingOnClickListener { notYetImplemented() }
            rule.setClosingOnClickListener { notYetImplemented() }
            reportDisplayProblem.setClosingOnClickListener { notYetImplemented() }

            mainActions.setClosingOnClickListener { id: Int ->
                when (id) {
                    R.id.actionReply -> safeNavigateToNewMessageActivity(DraftMode.REPLY, messageUid)
                    R.id.actionReplyAll -> safeNavigateToNewMessageActivity(DraftMode.REPLY_ALL, messageUid)
                    R.id.actionForward -> notYetImplemented()
                    R.id.actionDelete -> mainViewModel.deleteThreadOrMessage(threadUid, message)
                }
            }
        }
    }
}
