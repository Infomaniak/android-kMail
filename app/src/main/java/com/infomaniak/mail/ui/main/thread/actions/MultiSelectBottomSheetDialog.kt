/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail.ACTION_ARCHIVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_DELETE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FAVORITE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MARK_AS_SEEN_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MOVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SPAM_NAME
import com.infomaniak.mail.MatomoMail.trackMultiSelectActionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.BottomSheetMultiSelectBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.ui.main.folder.ThreadListMultiSelection
import com.infomaniak.mail.ui.main.folder.ThreadListMultiSelection.Companion.getReadIconAndShortText
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MultiSelectBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetMultiSelectBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val currentClassName: String by lazy { MultiSelectBottomSheetDialog::class.java.name }

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetMultiSelectBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(mainViewModel) {
        super.onViewCreated(view, savedInstanceState)

        val selectedThreadsUids = selectedThreads.map { it.uid }
        val selectedThreadsCount = selectedThreadsUids.count()
        val (shouldRead, shouldFavorite) = ThreadListMultiSelection.computeReadFavoriteStatus(selectedThreads)

        setStateDependentUi(shouldRead, shouldFavorite)

        binding.mainActions.setClosingOnClickListener { id: Int ->
            when (id) {
                R.id.actionMove -> {
                    trackMultiSelectActionEvent(ACTION_MOVE_NAME, selectedThreadsCount, isFromBottomSheet = true)
                    animatedNavigation(
                        ThreadListFragmentDirections.actionThreadListFragmentToMoveFragment(
                            threadsUids = selectedThreadsUids.toTypedArray(),
                        ),
                        currentClassName = currentClassName,
                    )
                }
                R.id.actionReadUnread -> {
                    trackMultiSelectActionEvent(ACTION_MARK_AS_SEEN_NAME, selectedThreadsCount, isFromBottomSheet = true)
                    toggleThreadsSeenStatus(selectedThreadsUids, shouldRead)
                }
                R.id.actionArchive -> {
                    trackMultiSelectActionEvent(ACTION_ARCHIVE_NAME, selectedThreadsCount, isFromBottomSheet = true)
                    archiveThreads(selectedThreadsUids)
                }
                R.id.actionDelete -> {
                    descriptionDialog.deleteWithConfirmationPopup(
                        folderRole = getActionFolderRole(selectedThreads.firstOrNull()),
                        count = selectedThreadsCount,
                    ) {
                        trackMultiSelectActionEvent(ACTION_DELETE_NAME, selectedThreadsCount, isFromBottomSheet = true)
                        deleteThreads(selectedThreadsUids)
                    }
                }
            }
            isMultiSelectOn = false
        }

        // binding.postpone.setClosingOnClickListener {
        //     trackMultiSelectActionEvent(ACTION_POSTPONE_NAME, selectedThreadsCount, isFromBottomSheet = true)
        //     notYetImplemented()
        //     isMultiSelectOn = false
        // }

        binding.spam.setClosingOnClickListener {
            trackMultiSelectActionEvent(ACTION_SPAM_NAME, selectedThreadsCount, isFromBottomSheet = true)
            toggleThreadsSpamStatus(selectedThreadsUids)
            isMultiSelectOn = false
        }

        binding.favorite.setClosingOnClickListener {
            trackMultiSelectActionEvent(ACTION_FAVORITE_NAME, selectedThreadsCount, isFromBottomSheet = true)
            toggleThreadsFavoriteStatus(selectedThreadsUids, shouldFavorite)
            isMultiSelectOn = false
        }
    }

    private fun setStateDependentUi(shouldRead: Boolean, shouldFavorite: Boolean) {
        val (readIcon, readText) = getReadIconAndShortText(shouldRead)
        binding.mainActions.setAction(R.id.actionReadUnread, readIcon, readText)

        // val isFromArchive = mainViewModel.currentFolder.value?.role == FolderRole.ARCHIVE
        // TODO: When decided by UI/UX, change how the icon is displayed (when trying to archive from inside the Archive folder).

        val isFromSpam = mainViewModel.currentFolder.value?.role == FolderRole.SPAM
        val (spamIcon, spamText) = getSpamIconAndText(isFromSpam)
        binding.spam.apply {
            setIconResource(spamIcon)
            setText(spamText)
        }

        val favoriteIcon = if (shouldFavorite) R.drawable.ic_star else R.drawable.ic_unstar
        val favoriteText = if (shouldFavorite) R.string.actionStar else R.string.actionUnstar
        binding.favorite.apply {
            setIconResource(favoriteIcon)
            setText(favoriteText)
        }
    }

    private fun getSpamIconAndText(isFromSpam: Boolean): Pair<Int, Int> {
        return if (isFromSpam) R.drawable.ic_non_spam to R.string.actionNonSpam else R.drawable.ic_spam to R.string.actionSpam
    }
}
