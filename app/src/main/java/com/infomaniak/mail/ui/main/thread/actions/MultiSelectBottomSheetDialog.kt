/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.setBackNavigationResult
import com.infomaniak.mail.MatomoMail.ACTION_ARCHIVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_CANCEL_SNOOZE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_DELETE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FAVORITE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MARK_AS_SEEN_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MODIFY_SNOOZE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MOVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SAVE_TO_KDRIVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SNOOZE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SPAM_NAME
import com.infomaniak.mail.MatomoMail.trackMultiSelectActionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.BottomSheetMultiSelectBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.ui.main.folder.ThreadListMultiSelection
import com.infomaniak.mail.ui.main.folder.ThreadListMultiSelection.Companion.getReadIconAndShortText
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.ui.main.thread.actions.ThreadActionsBottomSheetDialog.Companion.OPEN_SNOOZE_BOTTOM_SHEET
import com.infomaniak.mail.utils.FolderRoleUtils.getActionFolderRole
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.extensions.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MultiSelectBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetMultiSelectBinding by safeBinding()
    override val mainViewModel: MainViewModel by activityViewModels()

    private val currentClassName: String by lazy { MultiSelectBottomSheetDialog::class.java.name }

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetMultiSelectBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(mainViewModel) {
        super.onViewCreated(view, savedInstanceState)

        // This `.toSet()` is used to make an immutable local copy of `selectedThreads`.
        val threads = selectedThreads.toSet()
        val threadsUids = threads.map { it.uid }
        val threadsCount = threadsUids.count()
        val (shouldRead, shouldFavorite) = ThreadListMultiSelection.computeReadFavoriteStatus(threads)

        setStateDependentUi(shouldRead, shouldFavorite, threads)

        binding.mainActions.setClosingOnClickListener(shouldCloseMultiSelection = true) { id: Int ->
            when (id) {
                R.id.actionMove -> {
                    val navController = findNavController()
                    descriptionDialog.moveWithConfirmationPopup(
                        folderRole = getActionFolderRole(threads, folderController),
                        count = threadsCount,
                    ) {
                        trackMultiSelectActionEvent(ACTION_MOVE_NAME, threadsCount, isFromBottomSheet = true)
                        navController.animatedNavigation(
                            directions = ThreadListFragmentDirections.actionThreadListFragmentToMoveFragment(
                                threadsUids = threadsUids.toTypedArray(),
                            ),
                            currentClassName = currentClassName,
                        )
                    }
                }
                R.id.actionReadUnread -> {
                    trackMultiSelectActionEvent(ACTION_MARK_AS_SEEN_NAME, threadsCount, isFromBottomSheet = true)
                    toggleThreadsSeenStatus(threadsUids, shouldRead)
                }
                R.id.actionArchive -> {
                    descriptionDialog.archiveWithConfirmationPopup(
                        folderRole = getActionFolderRole(threads, folderController),
                        count = threadsCount,
                    ) {
                        trackMultiSelectActionEvent(ACTION_ARCHIVE_NAME, threadsCount, isFromBottomSheet = true)
                        archiveThreads(threadsUids)
                    }
                }
                R.id.actionDelete -> {
                    descriptionDialog.deleteWithConfirmationPopup(
                        folderRole = getActionFolderRole(threads, folderController),
                        count = threadsCount,
                    ) {
                        trackMultiSelectActionEvent(ACTION_DELETE_NAME, threadsCount, isFromBottomSheet = true)
                        deleteThreads(threadsUids)
                    }
                }
            }
        }

        binding.snooze.setOnClickListener {
            trackMultiSelectActionEvent(ACTION_SNOOZE_NAME, threadsCount, isFromBottomSheet = true)
            isMultiSelectOn = false
            setBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET, SnoozeScheduleType.Snooze(threadsUids))
        }

        binding.modifySnooze.setOnClickListener {
            trackMultiSelectActionEvent(ACTION_MODIFY_SNOOZE_NAME, threadsCount, isFromBottomSheet = true)
            isMultiSelectOn = false
            setBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET, SnoozeScheduleType.Modify(threadsUids))
        }

        binding.cancelSnooze.setClosingOnClickListener {
            trackMultiSelectActionEvent(ACTION_CANCEL_SNOOZE_NAME, threadsCount, isFromBottomSheet = true)
            lifecycleScope.launch { mainViewModel.unsnoozeThreads(threads) }
            isMultiSelectOn = false
        }

        binding.spam.setClosingOnClickListener(shouldCloseMultiSelection = true) {
            trackMultiSelectActionEvent(ACTION_SPAM_NAME, threadsCount, isFromBottomSheet = true)
            toggleThreadsSpamStatus(threadsUids)
            isMultiSelectOn = false
        }

        binding.favorite.setClosingOnClickListener(shouldCloseMultiSelection = true) {
            trackMultiSelectActionEvent(ACTION_FAVORITE_NAME, threadsCount, isFromBottomSheet = true)
            toggleThreadsFavoriteStatus(threadsUids, shouldFavorite)
            isMultiSelectOn = false
        }

        binding.saveKDrive.setClosingOnClickListener(shouldCloseMultiSelection = true) {
            trackMultiSelectActionEvent(ACTION_SAVE_TO_KDRIVE_NAME, threadsCount, isFromBottomSheet = true)
            navigateToDownloadMessagesProgressDialog(
                messageUids = threads.flatMap { it.messages }.map { it.uid },
                currentClassName = MultiSelectBottomSheetDialog::class.java.name,
            )
            isMultiSelectOn = false
        }
    }

    private fun setStateDependentUi(shouldRead: Boolean, shouldFavorite: Boolean, threads: Set<Thread>) {
        val (readIcon, readText) = getReadIconAndShortText(shouldRead)
        binding.mainActions.setAction(R.id.actionReadUnread, readIcon, readText)

        // val isFromArchive = mainViewModel.currentFolder.value?.role == FolderRole.ARCHIVE
        // TODO: When decided by UI/UX, change how the icon is displayed (when trying to archive from inside the Archive folder).

        val isFromSpam = mainViewModel.currentFolder.value?.role == FolderRole.SPAM
        val (spamIcon, spamText) = getSpamIconAndText(isFromSpam)
        binding.spam.apply {
            setIconResource(spamIcon)
            setTitle(spamText)
        }

        val favoriteIcon = if (shouldFavorite) R.drawable.ic_star else R.drawable.ic_unstar
        val favoriteText = if (shouldFavorite) R.string.actionStar else R.string.actionUnstar
        binding.favorite.apply {
            setIconResource(favoriteIcon)
            setTitle(favoriteText)
        }

        setSnoozeUi(threads)

        hideFirstActionItemDivider()
    }

    private fun getSpamIconAndText(isFromSpam: Boolean): Pair<Int, Int> {
        return if (isFromSpam) R.drawable.ic_non_spam to R.string.actionNonSpam else R.drawable.ic_spam to R.string.actionSpam
    }

    private fun setSnoozeUi(threads: Set<Thread>) = with(binding) {
        fun hasMixedSnoozeState(): Boolean {
            val isFirstThreadSnoozed = threads.first().isSnoozed()
            return threads.any { it.isSnoozed() != isFirstThreadSnoozed }
        }

        val currentFolderRole = mainViewModel.currentFolder.value?.role
        val shouldDisplaySnoozeActions = SharedUtils.shouldDisplaySnoozeActions(mainViewModel, localSettings, currentFolderRole)
        if (shouldDisplaySnoozeActions.not() || hasMixedSnoozeState()) {
            snooze.isGone = true
            modifySnooze.isGone = true
            cancelSnooze.isGone = true
        } else {
            val areThreadSnoozed = threads.any { it.isSnoozed() }

            snooze.isVisible = areThreadSnoozed.not()
            modifySnooze.isVisible = areThreadSnoozed
            cancelSnooze.isVisible = areThreadSnoozed
        }
    }

    private fun hideFirstActionItemDivider() {
        getFirstVisibleActionItemView()?.setDividerVisibility(false)
    }

    private fun getFirstVisibleActionItemView(): ActionItemView? {
        return (binding.actionsLayout.children.firstOrNull { it is ActionItemView && it.isVisible } as ActionItemView?)
    }
}
