/*
 * Infomaniak ikMail - Android
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.BottomSheetActionsMenuBinding
import com.infomaniak.mail.ui.MainViewModel

abstract class MailActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    protected lateinit var binding: BottomSheetActionsMenuBinding
    protected val mainViewModel: MainViewModel by activityViewModels()

    private var onClickListener: OnActionClick = object : OnActionClick {

        //region Main actions
        override fun onReply() = Unit
        override fun onReplyAll() = Unit
        override fun onForward() = Unit
        override fun onDelete() = Unit
        //endregion

        //region Actions
        override fun onArchive() = Unit
        override fun onReadUnread() = Unit
        override fun onMove() = Unit
        override fun onPostpone() = Unit
        override fun onFavorite() = Unit
        override fun onReportJunk() = Unit
        override fun onPrint() = Unit
        override fun onReportDisplayProblem() = Unit
        //endregion
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetActionsMenuBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setArchiveUi()

        archive.setClosingOnClickListener { onClickListener.onArchive() }
        markAsReadUnread.setClosingOnClickListener { onClickListener.onReadUnread() }
        move.setClosingOnClickListener { onClickListener.onMove() }
        postpone.setClosingOnClickListener { onClickListener.onPostpone() }
        favorite.setClosingOnClickListener { onClickListener.onFavorite() }
        reportJunk.setClosingOnClickListener { onClickListener.onReportJunk() }
        print.setClosingOnClickListener { onClickListener.onPrint() }
        reportDisplayProblem.setClosingOnClickListener { onClickListener.onReportDisplayProblem() }

        mainActions.setClosingOnClickListener { id: Int ->
            when (id) {
                R.id.actionReply -> onClickListener.onReply()
                R.id.actionReplyAll -> onClickListener.onReplyAll()
                R.id.actionForward -> onClickListener.onForward()
                R.id.actionDelete -> onClickListener.onDelete()
            }
        }
    }

    fun initOnClickListener(listener: OnActionClick) {
        onClickListener = listener
    }

    private fun computeUnreadStyle(isSeen: Boolean) = if (isSeen) {
        R.drawable.ic_envelope to R.string.actionMarkAsUnread
    } else {
        R.drawable.ic_envelope_open to R.string.actionMarkAsRead
    }

    private fun computeFavoriteStyle(isFavorite: Boolean): Pair<Int, Int> {
        return if (isFavorite) R.drawable.ic_unstar to R.string.actionUnstar else R.drawable.ic_star to R.string.actionStar
    }

    fun setMarkAsReadUi(isSeen: Boolean) = with(binding.markAsReadUnread) {
        val (readIconRes, readTextRes) = computeUnreadStyle(isSeen)
        setIconResource(readIconRes)
        setText(readTextRes)
    }

    fun setFavoriteUi(isFavorite: Boolean) = with(binding.favorite) {
        val (favoriteIconRes, favoriteText) = computeFavoriteStyle(isFavorite)
        setIconResource(favoriteIconRes)
        setText(favoriteText)
    }

    private fun setArchiveUi() = with(binding.archive) {
        if (mainViewModel.isCurrentFolderRole(FolderRole.ARCHIVE)) {
            setIconResource(R.drawable.ic_drawer_inbox)
            setText(R.string.actionMoveToInbox)
        }
    }

    interface OnActionClick {
        fun onArchive()
        fun onReadUnread()
        fun onMove()
        fun onPostpone()
        fun onFavorite()
        fun onReportJunk()
        fun onPrint()
        fun onReportDisplayProblem()

        fun onReply()
        fun onReplyAll()
        fun onForward()
        fun onDelete()
    }
}
