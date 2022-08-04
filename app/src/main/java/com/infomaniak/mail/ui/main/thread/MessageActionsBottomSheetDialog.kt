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
package com.infomaniak.mail.ui.main.thread

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.utils.getAttributeColor
import com.infomaniak.mail.utils.notYetImplemented

class MessageActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private val navigationArgs: MessageActionsBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        adaptUiToThread()

        archive.setOnClickListener { notYetImplemented() }
        markAsRead.setOnClickListener { notYetImplemented() }
        move.setOnClickListener { notYetImplemented() }
        postpone.setOnClickListener { notYetImplemented() }
        favorite.setOnClickListener { notYetImplemented() }
        spam.setOnClickListener { notYetImplemented() }
        blockSender.setOnClickListener { notYetImplemented() }
        phishing.setOnClickListener { notYetImplemented() }
        print.setOnClickListener { notYetImplemented() }
        saveAsPdf.setOnClickListener { notYetImplemented() }
        openIn.setOnClickListener { notYetImplemented() }
        rule.setOnClickListener { notYetImplemented() }
        reportDisplayProblem.setOnClickListener { notYetImplemented() }

        mainActions.setOnItemClickListener { index: Int ->
            val action = MainActions.values()[index]
            when (action) {
                MainActions.REPLY -> notYetImplemented()
                MainActions.REPLAY_TO_ALL -> notYetImplemented()
                MainActions.FORWARD -> notYetImplemented()
                MainActions.DELETE -> notYetImplemented()
            }
        }
    }

    private fun adaptUiToThread() = with(binding) {
        val (readIconRes, readTextRes) = if (navigationArgs.isSeen) {
            R.drawable.ic_envelope to R.string.actionMarkAsUnread
        } else {
            R.drawable.ic_envelope_open to R.string.actionMarkAsRead
        }
        markAsRead.setIconResource(readIconRes)
        markAsRead.setText(readTextRes)

        val (favoriteIconRes, favoriteTint) = if (navigationArgs.isFavorite) {
            R.drawable.ic_star_filled to Color.parseColor("#F1BE41")
        } else {
            R.drawable.ic_star to root.context.getAttributeColor(androidx.appcompat.R.attr.colorPrimary)
        }
        favorite.setIconResource(favoriteIconRes)
        favorite.setIconTint(favoriteTint)

        val currentFolderIsSpam = MailData.currentFolderFlow.value?.role == Folder.FolderRole.SPAM
        spam.setText(if (currentFolderIsSpam) R.string.actionNonSpam else R.string.actionSpam)
    }
}
