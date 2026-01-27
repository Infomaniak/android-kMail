/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetActionsMenuBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.folder.ThreadListFragment
import com.infomaniak.mail.ui.main.folder.TwoPaneViewModel
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView.TrailingContent
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.openMyKSuiteUpgradeBottomSheet
import kotlinx.coroutines.runBlocking

abstract class MailActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    protected var binding: BottomSheetActionsMenuBinding by safeBinding()

    override val mainViewModel: MainViewModel by activityViewModels()
    protected val twoPaneViewModel: TwoPaneViewModel by activityViewModels()

    abstract val shouldCloseMultiSelection: Boolean

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
        override fun onAddReaction() = Unit
        override fun onSnooze() = Unit
        override fun onModifySnooze() = Unit
        override fun onCancelSnooze() = Unit
        override fun onFavorite() = Unit
        override fun onSpam() = Unit
        override fun onPhishing() = Unit
        override fun onBlockSender() = Unit
        override fun onPrint() = Unit
        override fun onShare() = Unit
        override fun onSaveToKDrive() = Unit
        override fun onReportDisplayProblem() = Unit
        //endregion
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetActionsMenuBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        computeReportDisplayProblemVisibility()
        setShareTrailingContent()

        archive.setClosingOnClickListener(shouldCloseMultiSelection) { onClickListener.onArchive() }
        markAsReadUnread.setClosingOnClickListener(shouldCloseMultiSelection) { onClickListener.onReadUnread() }
        move.setClosingOnClickListener(shouldCloseMultiSelection) { onClickListener.onMove() }
        // Not a setClosingOnClickListener because we need to send a setBackNavigationResult,
        // and setClosingOnClickListener closes before we had time to set the result
        addReaction.setOnClickListener { onClickListener.onAddReaction() }
        snooze.setOnClickListener { onClickListener.onSnooze() }
        modifySnooze.setOnClickListener { onClickListener.onModifySnooze() }
        cancelSnooze.setClosingOnClickListener(shouldCloseMultiSelection) { onClickListener.onCancelSnooze() }
        favorite.setClosingOnClickListener(shouldCloseMultiSelection) { onClickListener.onFavorite() }
        spam.setOnClickListener { onClickListener.onSpam() }
        phishing.setOnClickListener { onClickListener.onPhishing() }
        blockSender.setClosingOnClickListener(shouldCloseMultiSelection) { onClickListener.onBlockSender() }
        print.setClosingOnClickListener(shouldCloseMultiSelection) { onClickListener.onPrint() }
        share.setClosingOnClickListener(shouldCloseMultiSelection) {
            if (mainViewModel.currentMailbox.value?.kSuite is KSuite.Perso.Free) {
                openMyKSuiteUpgradeBottomSheet(MatomoName.ShareEmail.value, ThreadListFragment::class.java.name)
            } else {
                onClickListener.onShare()
            }
        }
        saveKDrive.setClosingOnClickListener(shouldCloseMultiSelection) { onClickListener.onSaveToKDrive() }
        reportDisplayProblem.setClosingOnClickListener(shouldCloseMultiSelection) { onClickListener.onReportDisplayProblem() }

        mainActions.setClosingOnClickListener(shouldCloseMultiSelection) { id: Int ->
            when (id) {
                R.id.actionReply -> onClickListener.onReply()
                R.id.actionReplyAll -> onClickListener.onReplyAll()
                R.id.actionForward -> onClickListener.onForward()
                R.id.actionDelete -> onClickListener.onDelete()
            }
        }
    }

    private fun setShareTrailingContent() {
        binding.share.trailingContent = if (mainViewModel.currentMailbox.value?.kSuite is KSuite.Perso.Free) {
            TrailingContent.KSuitePersoChip
        } else {
            TrailingContent.None
        }
    }

    fun initOnClickListener(listener: OnActionClick) {
        onClickListener = listener
    }

    private fun computeReportDisplayProblemVisibility() = runBlocking {
        binding.reportDisplayProblem.isVisible =
            mainViewModel.currentMailbox.value?.let { AccountUtils.getUserById(it.userId)?.isStaff } == true
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
        setTitle(readTextRes)
    }

    fun setFavoriteUi(isFavorite: Boolean) = with(binding.favorite) {
        val (favoriteIconRes, favoriteText) = computeFavoriteStyle(isFavorite)
        setIconResource(favoriteIconRes)
        setTitle(favoriteText)
    }

    fun setArchiveUi(isFromArchive: Boolean) = with(binding.archive) {
        if (isFromArchive) {
            setIconResource(R.drawable.ic_drawer_inbox)
            setTitle(R.string.actionMoveToInbox)
        }
    }

    fun setReactionUi(canBeReactedTo: Boolean) = with(binding.addReaction) {
        isVisible = canBeReactedTo
    }

    interface OnActionClick {
        fun onReply()
        fun onReplyAll()
        fun onForward()
        fun onDelete()
        fun onArchive()
        fun onReadUnread()
        fun onMove()
        fun onAddReaction()
        fun onSnooze()
        fun onModifySnooze()
        fun onCancelSnooze()
        fun onFavorite()
        fun onSpam()
        fun onPhishing()
        fun onBlockSender()
        fun onPrint()
        fun onShare()
        fun onSaveToKDrive()
        fun onReportDisplayProblem()
    }
}
