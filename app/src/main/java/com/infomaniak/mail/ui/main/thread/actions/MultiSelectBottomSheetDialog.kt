/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackMultiSelectActionEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.BottomSheetMultiSelectBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.folder.ThreadListFragment
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.ui.main.folder.ThreadListMultiSelection
import com.infomaniak.mail.ui.main.folder.ThreadListMultiSelection.Companion.getArchiveIconAndShortText
import com.infomaniak.mail.ui.main.folder.ThreadListMultiSelection.Companion.getFavoriteIconAndShortText
import com.infomaniak.mail.ui.main.folder.ThreadListMultiSelection.Companion.getReadIconAndShortText
import com.infomaniak.mail.ui.main.folderPicker.FolderPickerAction
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.ui.main.thread.actions.ThreadActionsBottomSheetDialog.Companion.OPEN_SNOOZE_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.actions.ThreadActionsBottomSheetDialog.Companion.setBlockUserUi
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.archiveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.moveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.navigateToDownloadMessagesProgressDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject
import com.infomaniak.core.common.R as RCore

@AndroidEntryPoint
class MultiSelectBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetMultiSelectBinding by safeBinding()
    override val mainViewModel: MainViewModel by activityViewModels()
    private val junkMessagesViewModel: JunkMessagesViewModel by activityViewModels()

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    @Inject
    lateinit var globalCoroutineScope: CoroutineScope

    @Inject
    lateinit var folderRoleUtils: FolderRoleUtils

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var snackbarManager: SnackbarManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetMultiSelectBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(mainViewModel) {
        super.onViewCreated(view, savedInstanceState)

        // This `.toSet()` is used to make an immutable local copy of `selectedThreads`.
        val threads = selectedThreads.toSet()
        val threadsUids = threads.map { it.uid }
        val threadsCount = threadsUids.count()

        // Initialization of threadsUids to populate junkMessages and potentialUsersToBlock
        junkMessagesViewModel.threadsUids = threadsUids

        val (shouldRead, shouldFavorite) = ThreadListMultiSelection.computeReadFavoriteStatus(threads)
        val isFromArchive = mainViewModel.currentFolder.value?.role == FolderRole.ARCHIVE
        setStateDependentUi(shouldRead, shouldFavorite, isFromArchive, threads)
        observeReportPhishingResult()
        observePotentialBlockedSenders()

        binding.mainActions.setClosingOnClickListener(shouldCloseMultiSelection = true) { id: Int ->
            // This DialogFragment is already be dismissing since popBackStack was called by
            // `setClosingOnClickListener`, so we avoid using its (view) lifecycleScope.
            globalCoroutineScope.launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
                when (id) {
                    R.id.actionMove -> {
                        descriptionDialog.moveWithConfirmationPopup(
                            folderRole = folderRoleUtils.getActionFolderRole(threads),
                            count = threadsCount,
                        ) {
                            trackMultiSelectActionEvent(MatomoName.Move, threadsCount, isFromBottomSheet = true)
                            moveThreads(threadsUids)
                        }
                    }
                    R.id.actionReadUnread -> {
                        trackMultiSelectActionEvent(MatomoName.MarkAsSeen, threadsCount, isFromBottomSheet = true)
                        toggleThreadsSeenStatus(threadsUids, shouldRead)
                    }
                    R.id.actionArchive -> {
                        descriptionDialog.archiveWithConfirmationPopup(
                            folderRole = folderRoleUtils.getActionFolderRole(threads),
                            count = threadsCount,
                        ) {
                            trackMultiSelectActionEvent(MatomoName.Archive, threadsCount, isFromBottomSheet = true)
                            archiveThreads(threadsUids)
                        }
                    }
                    R.id.actionDelete -> {
                        descriptionDialog.deleteWithConfirmationPopup(
                            folderRole = folderRoleUtils.getActionFolderRole(threads),
                            count = threadsCount,
                        ) {
                            trackMultiSelectActionEvent(MatomoName.Delete, threadsCount, isFromBottomSheet = true)
                            deleteThreads(threadsUids)
                        }
                    }
                }
            }
        }

        binding.snooze.setOnClickListener {
            trackMultiSelectActionEvent(MatomoName.Snooze, threadsCount, isFromBottomSheet = true)
            isMultiSelectOn = false
            setBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET, SnoozeScheduleType.Snooze(threadsUids))
        }

        binding.modifySnooze.setOnClickListener {
            trackMultiSelectActionEvent(MatomoName.ModifySnooze, threadsCount, isFromBottomSheet = true)
            isMultiSelectOn = false
            setBackNavigationResult(OPEN_SNOOZE_BOTTOM_SHEET, SnoozeScheduleType.Modify(threadsUids))
        }

        binding.cancelSnooze.setClosingOnClickListener {
            trackMultiSelectActionEvent(MatomoName.CancelSnooze, threadsCount, isFromBottomSheet = true)
            lifecycleScope.launch { mainViewModel.unsnoozeThreads(threads) }
            isMultiSelectOn = false
        }

        binding.spam.setClosingOnClickListener {
            trackMultiSelectActionEvent(MatomoName.Spam, threadsCount, isFromBottomSheet = true)
            toggleThreadSpamStatus(threadsUids)
            isMultiSelectOn = false
        }

        binding.phishing.setOnClickListener {
            trackMultiSelectActionEvent(MatomoName.SignalPhishing, threadsCount, isFromBottomSheet = true)
            val messages = junkMessagesViewModel.junkMessages.value ?: emptyList()

            if (messages.isEmpty()) {
                //An error will be shown to the user in the reportPhishing function
                //This should never happen, that's why we add a SentryLog.
                SentryLog.e(TAG, getString(R.string.sentryErrorPhishingMessagesEmpty))
            }

            descriptionDialog.show(
                title = getString(R.string.reportPhishingTitle),
                description = resources.getQuantityString(R.plurals.reportPhishingDescription, messages.count()),
                onPositiveButtonClicked = { mainViewModel.reportPhishing(threadsUids, messages) },
            )
        }

        binding.blockSender.setClosingOnClickListener {
            trackMultiSelectActionEvent(MatomoName.BlockUser, threadsCount, isFromBottomSheet = true)
            val potentialUsersToBlock = junkMessagesViewModel.potentialBlockedUsers.value
            if (potentialUsersToBlock == null) {
                snackbarManager.postValue(getString(RCore.string.anErrorHasOccurred))
                SentryLog.e(TAG, getString(R.string.sentryErrorPotentialUsersToBlockNull))
                return@setClosingOnClickListener
            }

            if (potentialUsersToBlock.count() > 1) {
                safelyNavigate(
                    resId = R.id.userToBlockBottomSheetDialog,
                    substituteClassName = ThreadListFragment::class.java.name,
                )
            } else {
                potentialUsersToBlock.values.firstOrNull()?.let { message ->
                    junkMessagesViewModel.messageOfUserToBlock.value = message
                }
            }
            mainViewModel.isMultiSelectOn = false
        }

        binding.favorite.setClosingOnClickListener(shouldCloseMultiSelection = true) {
            trackMultiSelectActionEvent(MatomoName.Favorite, threadsCount, isFromBottomSheet = true)
            toggleThreadsFavoriteStatus(threadsUids, shouldFavorite)
            isMultiSelectOn = false
        }

        binding.saveKDrive.setClosingOnClickListener(shouldCloseMultiSelection = true) {
            trackMultiSelectActionEvent(MatomoName.SaveToKDrive, threadsCount, isFromBottomSheet = true)
            navigateToDownloadMessagesProgressDialog(
                messageUids = threads.flatMap { it.messages }.map { it.uid },
                currentClassName = MultiSelectBottomSheetDialog::class.java.name,
            )
            isMultiSelectOn = false
        }
    }

    private fun moveThreads(threadsUids: List<String>) {
        animatedNavigation(
            directions = ThreadListFragmentDirections.actionThreadListFragmentToFolderPickerFragment(
                threadsUids = threadsUids.toTypedArray(),
                action = FolderPickerAction.MOVE,
                sourceFolderId = mainViewModel.currentFolderId ?: Folder.DUMMY_FOLDER_ID
            ),
        )
    }

    private fun observeReportPhishingResult() {
        mainViewModel.reportPhishingTrigger.observe(viewLifecycleOwner) {
            descriptionDialog.resetLoadingAndDismiss()
            findNavController().popBackStack()
        }
    }

    private fun observePotentialBlockedSenders() {
        junkMessagesViewModel.potentialBlockedUsers.observe(viewLifecycleOwner) { potentialUsersToBlock ->
            val isFromSpam = mainViewModel.currentFolder.value?.role == FolderRole.SPAM
            setBlockUserUi(binding.blockSender, potentialUsersToBlock, isFromSpam)
        }
    }

    private fun setStateDependentUi(shouldRead: Boolean, shouldFavorite: Boolean, isFromArchive: Boolean, threads: Set<Thread>) {
        val (readIcon, readText) = getReadIconAndShortText(shouldRead)
        binding.mainActions.setAction(R.id.actionReadUnread, readIcon, readText)

        val (archiveIcon, archiveText) = getArchiveIconAndShortText(isFromArchive)
        binding.mainActions.setAction(R.id.actionArchive, archiveIcon, archiveText)

        val (favoriteIcon, favoriteText) = getFavoriteIconAndShortText(shouldFavorite)
        binding.favorite.apply {
            setIconResource(favoriteIcon)
            setTitle(favoriteText)
        }

        setSnoozeUi(threads)
        ThreadActionsBottomSheetDialog.setSpamPhishingUi(
            spam = binding.spam,
            phishing = binding.phishing,
            isFromSpam = mainViewModel.currentFolder.value?.role == FolderRole.SPAM
        )
        hideFirstActionItemDivider()
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

    @Parcelize
    data class JunkThreads(val threadUids: List<String>) : Parcelable

    companion object {
        const val TAG = "MultiSelectBottomSheetDialog"
    }
}
