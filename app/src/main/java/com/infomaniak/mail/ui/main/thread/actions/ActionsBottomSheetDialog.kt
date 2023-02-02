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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.BottomSheetActionsMenuBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.getAttributeColor
import com.google.android.material.R as RMaterial

abstract class ActionsBottomSheetDialog : BottomSheetDialogFragment() {

    lateinit var binding: BottomSheetActionsMenuBinding
    val mainViewModel: MainViewModel by activityViewModels()
    val actionsViewModel: ActionsViewModel by viewModels()

    private var onClickListener: OnActionClick = object : OnActionClick {
        override fun onArchive() = Unit
        override fun onReadUnread() = Unit
        override fun onMove() = Unit
        override fun onPostpone() = Unit
        override fun onFavorite() = Unit
        override fun onReportJunk() = Unit
        override fun onPrint() = Unit
        override fun onReportDisplayProblem() = Unit
        override fun onReply() = Unit
        override fun onReplyAll() = Unit
        override fun onForward() = Unit
        override fun onDelete() = Unit
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetActionsMenuBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        archive.isGone = mainViewModel.isCurrentFolderRole(FolderRole.ARCHIVE)

        archive.setClosingOnClickListener { onClickListener.onArchive() }
        markAsReadUnread.setOnClickListener { onClickListener.onReadUnread() }
        move.setClosingOnClickListener { onClickListener.onMove() }
        postpone.setClosingOnClickListener { onClickListener.onPostpone() }
        favorite.setClosingOnClickListener { onClickListener.onFavorite() }
        reportJunk.setOnClickListener { onClickListener.onReportJunk() }
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

    private fun computeFavoriteStyle(context: Context, isFavorite: Boolean): Triple<Int, Int, Int> {
        return if (isFavorite) {
            Triple(R.drawable.ic_star_filled, context.getColor(R.color.favoriteYellow), R.string.actionUnstar)
        } else {
            Triple(R.drawable.ic_star, context.getAttributeColor(RMaterial.attr.colorPrimary), R.string.actionStar)
        }
    }

    fun setMarkAsReadUi(isSeen: Boolean) = with(binding.markAsReadUnread) {
        val (readIconRes, readTextRes) = computeUnreadStyle(isSeen)
        setIconResource(readIconRes)
        setText(readTextRes)
    }

    fun setFavoriteUi(isFavorite: Boolean) = with(binding.favorite) {
        val (favoriteIconRes, favoriteTint, favoriteText) = computeFavoriteStyle(context, isFavorite)
        setIconResource(favoriteIconRes)
        setIconTint(favoriteTint)
        setText(favoriteText)
    }

    private fun ActionItemView.setClosingOnClickListener(forceQuit: Boolean = false, callback: (() -> Unit)) {
        setOnClickListener {
            callback()
            with(findNavController()) { if (forceQuit) popBackStack(R.id.threadListFragment, false) else popBackStack() }
        }
    }

    private fun MainActionsView.setClosingOnClickListener(callback: ((Int) -> Unit)) {
        setOnItemClickListener { id ->
            callback(id)
            findNavController().popBackStack()
        }
    }

    companion object {
        const val JUNK_BOTTOM_SHEET_NAV_KEY = "junk_bottom_sheet_nav_key"
        const val SHOULD_OPEN_JUNK = "should_open_junk"
        const val MESSAGE_UID = "message_uid"
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
