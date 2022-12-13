/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity

class ThreadActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private val navigationArgs: ThreadActionsBottomSheetDialogArgs by navArgs()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val threadActionsViewModel: ThreadActionsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        threadActionsViewModel.threadLive(navigationArgs.threadUid).observe(viewLifecycleOwner) { thread ->
            setMarkAsReadUi(thread.unseenMessagesCount == 0)
            setFavoriteUi(thread.isFavorite)
        }

        setSpamUi()

        postpone.isGone = true
        blockSender.isGone = true
        phishing.isGone = true
        rule.isGone = true

        archive.setClosingOnClickListener { notYetImplemented() }
        markAsRead.setClosingOnClickListener { mainViewModel.toggleSeenStatus(navigationArgs.threadUid) }
        move.setClosingOnClickListener { notYetImplemented() }
        favorite.setClosingOnClickListener { notYetImplemented() }
        spam.setClosingOnClickListener { notYetImplemented() }
        print.setClosingOnClickListener { notYetImplemented() }
        saveAsPdf.setClosingOnClickListener { notYetImplemented() }
        reportDisplayProblem.setClosingOnClickListener { notYetImplemented() }

        mainActions.setClosingOnClickListener { id: Int ->
            when (id) {
                R.id.actionReply -> safeNavigateToNewMessageActivity(DraftMode.REPLY, navigationArgs.messageUid)
                R.id.actionReplyAll -> safeNavigateToNewMessageActivity(DraftMode.REPLY_ALL, navigationArgs.messageUid)
                R.id.actionForward -> notYetImplemented()
                R.id.actionDelete -> mainViewModel.deleteThread(navigationArgs.threadUid)
            }
        }
    }
}
