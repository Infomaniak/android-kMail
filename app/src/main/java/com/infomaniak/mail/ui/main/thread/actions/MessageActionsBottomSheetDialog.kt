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
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.notYetImplemented

class MessageActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private val navigationArgs: MessageActionsBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setMarkAsReadUi(navigationArgs.isSeen)
        setFavoriteUi(navigationArgs.isFavorite)
        setSpamUi()

        archive.setClosingOnClickListener { notYetImplemented() }
        markAsRead.setClosingOnClickListener { notYetImplemented() }
        move.setClosingOnClickListener { notYetImplemented() }
        postpone.setClosingOnClickListener { notYetImplemented() }
        favorite.setClosingOnClickListener { notYetImplemented() }
        spam.setClosingOnClickListener { notYetImplemented() }
        blockSender.setClosingOnClickListener { notYetImplemented() }
        phishing.setClosingOnClickListener { notYetImplemented() }
        print.setClosingOnClickListener { notYetImplemented() }
        saveAsPdf.setClosingOnClickListener { notYetImplemented() }
        rule.setClosingOnClickListener { notYetImplemented() }
        reportDisplayProblem.setClosingOnClickListener { notYetImplemented() }

        mainActions.setClosingOnClickListener { id: Int ->
            when (id) {
                R.id.actionReply -> notYetImplemented()
                R.id.actionReplyAll -> notYetImplemented()
                R.id.actionForward -> notYetImplemented()
                R.id.actionDelete -> notYetImplemented()
            }
        }
    }
}
