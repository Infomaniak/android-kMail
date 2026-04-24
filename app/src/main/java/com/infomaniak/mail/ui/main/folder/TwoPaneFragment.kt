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
package com.infomaniak.mail.ui.main.folder

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.getBackNavigationResult
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AutoAdvanceMode
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.bottomSheetDialogs.SnoozeBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.search.SearchFragment
import com.infomaniak.mail.ui.main.thread.ThreadFragment
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.ui.main.thread.actions.DownloadMessagesProgressDialog
import com.infomaniak.mail.utils.LocalStorageUtils.clearEmlCacheDir
import com.infomaniak.mail.utils.extensions.AttachmentExt
import com.infomaniak.mail.utils.extensions.isPhone
import com.infomaniak.mail.utils.extensions.isTabletOrFoldable
import com.infomaniak.mail.utils.extensions.safeNavigateToNewMessageActivity
import io.realm.kotlin.types.RealmInstant
import javax.inject.Inject

abstract class TwoPaneFragment : Fragment() {

    val mainViewModel: MainViewModel by activityViewModels()
    private val twoPaneViewModel: TwoPaneViewModel by activityViewModels()

    private var dragStartX = 0f
    private var dragStartLeftWidth = 0
    private val separatorWidthPx by lazy { resources.getDimensionPixelSize(R.dimen.dragSeparatorWidth) }
    val minLeftWidthPx by lazy { resources.getDimensionPixelSize(R.dimen.minLeftPaneWidth) }
    val minRightWidthPx by lazy { resources.getDimensionPixelSize(R.dimen.minRightPaneWidth) }

    // TODO: When we'll update DragDropSwipeRecyclerViewLib, we'll need to make the adapter nullable.
    //  For now it causes a memory leak, because we can't remove the strong reference
    //  between the ThreadList's RecyclerView and its Adapter as it throws an NPE.
    @Inject
    lateinit var threadListAdapter: ThreadListAdapter

    @Inject
    lateinit var localSettings: LocalSettings

    abstract val substituteClassName: String
    abstract fun getLeftPane(): View?
    abstract fun getRightPane(): FragmentContainerView?
    abstract fun getDragSeparator(): View?
    abstract fun getAnchor(): View?
    open fun doAfterFolderChanged() = Unit

    fun isOnlyLeftShown(): Boolean = isPhone() && !twoPaneViewModel.isThreadOpen
    fun isOnlyRightShown(): Boolean = isPhone() && twoPaneViewModel.isThreadOpen

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateTwoPaneVisibilities()
        observeCurrentFolder()
        observeThreadUid()
        observeThreadNavigation()
        observeDragSeparator()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTwoPaneVisibilities()
        updateDrawerLockMode()
    }

    private fun observeCurrentFolder() = with(twoPaneViewModel) {
        mainViewModel.currentFolder.observe(viewLifecycleOwner) { folder ->

            val (folderId, name) = if (this@TwoPaneFragment is SearchFragment) {
                FolderController.SEARCH_FOLDER_ID to getString(R.string.searchFolderName)
            } else {
                if (folder == null) return@observe
                folder.id to folder.getLocalizedName(requireContext())
            }

            rightPaneFolderName.value = name

            if (folderId != previousFolderId) {
                if (isThreadOpen && previousFolderId != null) closeThread()
                previousFolderId = folderId
            }

            doAfterFolderChanged()
        }
    }

    private fun observeThreadUid() {
        twoPaneViewModel.currentThreadUid.observe(viewLifecycleOwner) { threadUid ->
            updateTwoPaneVisibilities()
            updateDrawerLockMode()
            if (threadUid == null) resetPanes()
        }
    }

    private val resultActivityResultLauncher = registerForActivityResult(StartActivityForResult()) { _ ->
        clearEmlCacheDir(requireContext())
    }

    private fun observeThreadNavigation() {
        getBackNavigationResult(AttachmentExt.DOWNLOAD_ATTACHMENT_RESULT, ::startActivity)
        getBackNavigationResult(DownloadMessagesProgressDialog.DOWNLOAD_MESSAGES_RESULT, resultActivityResultLauncher::launch)

        twoPaneViewModel.newMessageArgs.observe(viewLifecycleOwner) {
            safeNavigateToNewMessageActivity(args = it.toBundle())
        }

        twoPaneViewModel.navArgs.observe(viewLifecycleOwner) { (resId, args) ->
            safelyNavigate(resId, args)
        }
    }

    override fun onStop() {
        twoPaneViewModel.saveLeftPaneRatio()
        super.onStop()
    }

    private fun observeDragSeparator() {
        val separator = getDragSeparator() ?: return
        val leftPane = getLeftPane() ?: return
        val rightPane = getRightPane() ?: return
        val lineSeparator = separator.findViewById<View>(R.id.lineDragSeparator)
        val parentGroup = view as? ViewGroup ?: return

        val widthSelected = resources.getDimensionPixelSize(R.dimen.dragSeparatorWidthSelected)
        val widthNormal = resources.getDimensionPixelSize(R.dimen.dragSeparatorWidth)

        separator.setOnTouchListener { separatorView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartLeftWidth = leftPane.width
                    updateSeparatorAppearance(separatorView, lineSeparator, true, widthSelected)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    handleDragMove(event.rawX, leftPane, rightPane, parentGroup)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    updateSeparatorAppearance(separatorView, lineSeparator, false, widthNormal)
                    separatorView.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    updateSeparatorAppearance(separatorView, lineSeparator, false, widthNormal)
                    true
                }

                else -> false
            }
        }
    }

    private fun updateSeparatorAppearance(
        separatorView: View,
        lineSeparator: View?,
        isPressed: Boolean,
        lineWidth: Int
    ) {
        separatorView.isPressed = isPressed
        lineSeparator?.layoutParams = lineSeparator.layoutParams?.apply {
            width = lineWidth
        }
    }

    private fun handleDragMove(
        currentRawX: Float,
        leftPane: View,
        rightPane: View,
        parentGroup: ViewGroup
    ) {
        val deltaX = (currentRawX - dragStartX).toInt()
        val parentWidth = parentGroup.width

        val maxLeftWidth = parentWidth - minRightWidthPx
        val safeMaxLeftWidth = maxOf(minLeftWidthPx, maxLeftWidth)

        val newLeftWidth = (dragStartLeftWidth + deltaX).coerceIn(
            minLeftWidthPx,
            safeMaxLeftWidth
        )

        leftPane.layoutParams.width = newLeftWidth
        rightPane.layoutParams.width = parentWidth - newLeftWidth - separatorWidthPx
        parentGroup.requestLayout()

        twoPaneViewModel.leftPaneRatio = newLeftWidth.toFloat() / parentWidth
    }

    fun handleOnBackPressed() {
        when {
            isOnlyRightShown() -> {
                if (SDK_INT >= 29) requireActivity().window.isNavigationBarContrastEnforced = true
                twoPaneViewModel.closeThread()
            }
            this is ThreadListFragment -> requireActivity().finish()
            else -> findNavController().popBackStack()
        }
    }

    fun navigateToThread(thread: Thread) {
        if (thread.isOnlyOneDraft) {
            trackNewMessageEvent(MatomoName.OpenFromDraft)
            twoPaneViewModel.openDraft(thread)
        } else {
            openThreadAndResetItsState(thread.uid)
        }
    }

    fun openThreadAndResetItsState(threadUid: String) {
        if (SDK_INT >= 29) requireActivity().window.isNavigationBarContrastEnforced = false
        getRightPane()?.getFragment<ThreadFragment?>()?.resetThreadState()
        twoPaneViewModel.openThread(threadUid)
    }

    private fun resetPanes() {
        threadListAdapter.selectNewThread(newPosition = null, threadUid = null)

        childFragmentManager.beginTransaction().replace(R.id.threadHostFragment, ThreadFragment()).commit()
    }

    private fun updateTwoPaneVisibilities() {
        val (leftWidth, rightWidth) = computeTwoPaneWidths(
            widthPixels = requireActivity().application.resources.displayMetrics.widthPixels,
            isThreadOpen = twoPaneViewModel.isThreadOpen,
        )

        getLeftPane()?.apply {
            isVisible = leftWidth != 0
            if (leftWidth != 0 && width != leftWidth) {
                layoutParams?.width = leftWidth
            }
        }

        getRightPane()?.apply {
            isVisible = rightWidth != 0
            if (rightWidth != 0 && width != rightWidth) {
                layoutParams?.width = rightWidth
            }
        }

        getDragSeparator()?.isVisible = isTabletOrFoldable() && rightWidth != 0
    }


    private fun computeTwoPaneWidths(widthPixels: Int, isThreadOpen: Boolean): Pair<Int, Int> {
        return if (isTabletOrFoldable()) {
            val ratio = twoPaneViewModel.leftPaneRatio

            val availableWidth = (widthPixels - separatorWidthPx).coerceAtLeast(0)
            val leftWidth = if (availableWidth < minLeftWidthPx + minRightWidthPx) {
                (ratio * availableWidth).toInt().coerceIn(0, availableWidth)
            } else {
                (ratio * availableWidth).toInt().coerceIn(minLeftWidthPx, availableWidth - minRightWidthPx)
            }
            leftWidth to (availableWidth - leftWidth)

        } else {
            if (isThreadOpen) 0 to widthPixels else widthPixels to 0
        }
    }

    // TODO: When we'll add the feature of swiping between Threads, we'll need to check if this function is still needed.
    private fun updateDrawerLockMode() {
        if (this is ThreadListFragment) {
            (requireActivity() as MainActivity).setDrawerLockMode(isLocked = isOnlyRightShown())
        }
    }

    protected fun updateAutoAdvanceNaturalThread(currentPosition: Int, previousPosition: Int) {
        localSettings.autoAdvanceNaturalThread = if (currentPosition > previousPosition) {
            AutoAdvanceMode.FOLLOWING_THREAD
        } else {
            AutoAdvanceMode.PREVIOUS_THREAD
        }
    }

    fun navigateToSnoozeBottomSheet(snoozeScheduleType: SnoozeScheduleType?, snoozeEndDate: RealmInstant?) {
        val mailbox = mainViewModel.currentMailbox.value ?: return
        twoPaneViewModel.snoozeScheduleType = snoozeScheduleType
        twoPaneViewModel.safelyNavigate(
            resId = R.id.snoozeBottomSheetDialog,
            args = SnoozeBottomSheetDialogArgs(
                lastSelectedScheduleEpochMillis = localSettings.lastSelectedSnoozeEpochMillis ?: 0L,
                currentlyScheduledEpochMillis = snoozeEndDate?.epochSeconds?.times(1_000) ?: 0L,
                currentKSuite = mailbox.kSuite,
                isAdmin = mailbox.isAdmin,
            ).toBundle(),
        )
    }
}
