/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.search.SearchFragment
import com.infomaniak.mail.ui.main.thread.DetailedContactBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.ThreadFragment
import com.infomaniak.mail.ui.main.thread.actions.*
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity

abstract class TwoPaneFragment : Fragment() {

    val mainViewModel: MainViewModel by activityViewModels()

    private val slidingPaneLayout: SlidingPaneLayout
        get() = when (this) {
            is ThreadListFragment -> binding.threadListSlidingPaneLayout
            is SearchFragment -> binding.searchSlidingPaneLayout
            else -> throw IllegalStateException()
        }

    fun areBothShown() = !slidingPaneLayout.isSlideable
    fun isOnlyLeftShown() = slidingPaneLayout.let { it.isSlideable && !it.isOpen }
    fun isOnlyRightShown() = slidingPaneLayout.let { it.isSlideable && it.isOpen }

    private val searchFolder by lazy {
        Folder().apply {
            id = FolderController.SEARCH_FOLDER_ID
            name = getString(R.string.searchFolderName)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSlidingPane()
        observeCurrentFolder()
        observeThreadEvents()
    }

    private fun setupSlidingPane() {
        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
    }

    private fun observeCurrentFolder() = with(mainViewModel) {
        currentFolder.observe(viewLifecycleOwner) { folder ->

            val currentFolder = if (this@TwoPaneFragment is SearchFragment) searchFolder else folder ?: return@observe

            rightPaneFolderName.value = currentFolder.getLocalizedName(context)

            if (currentFolder.id != previousFolderId) {
                previousFolderId = currentFolder.id
                if (isThreadOpen) closeThread()
            }
        }
    }

    private fun observeThreadEvents() = with(mainViewModel) {

        val threadListAdapter = when (this@TwoPaneFragment) {
            is ThreadListFragment -> this@TwoPaneFragment.threadListAdapter
            is SearchFragment -> this@TwoPaneFragment.threadListAdapter
            else -> null
        }

        // Reset selected Thread UI when closing Thread
        currentThreadUid.observe(viewLifecycleOwner) { threadUid ->
            if (threadUid == null) threadListAdapter?.selectNewThread(newPosition = null, threadUid = null)
        }

        closeThreadTrigger.observe(viewLifecycleOwner) { resetPanes() }

        getBackNavigationResult(DownloadAttachmentProgressDialog.OPEN_WITH, ::startActivity)

        downloadAttachmentsArgs.observe(viewLifecycleOwner) { (resource, name, fileType) ->
            safeNavigate(
                resId = R.id.downloadAttachmentProgressDialog,
                args = DownloadAttachmentProgressDialogArgs(
                    attachmentResource = resource,
                    attachmentName = name,
                    attachmentType = fileType,
                ).toBundle(),
            )
        }

        newMessageArgs.observe(viewLifecycleOwner) {
            safeNavigateToNewMessageActivity(args = it.toBundle())
        }

        replyBottomSheetArgs.observe(viewLifecycleOwner) { (messageUid, shouldLoadDistantResources) ->
            safeNavigate(
                resId = R.id.replyBottomSheetDialog,
                args = ReplyBottomSheetDialogArgs(
                    messageUid = messageUid,
                    shouldLoadDistantResources = shouldLoadDistantResources,
                ).toBundle(),
            )
        }

        threadActionsBottomSheetArgs.observe(viewLifecycleOwner) {
            val (threadUid, lastMessageToReplyToUid, shouldLoadDistantResources) = it
            safeNavigate(
                resId = R.id.threadActionsBottomSheetDialog,
                args = ThreadActionsBottomSheetDialogArgs(
                    threadUid = threadUid,
                    messageUidToReplyTo = lastMessageToReplyToUid,
                    shouldLoadDistantResources = shouldLoadDistantResources,
                ).toBundle(),
            )
        }

        messageActionsBottomSheetArgs.observe(viewLifecycleOwner) {
            safeNavigate(
                resId = R.id.messageActionsBottomSheetDialog,
                args = MessageActionsBottomSheetDialogArgs(
                    messageUid = it.messageUid,
                    threadUid = it.threadUid,
                    isThemeTheSame = it.isThemeTheSame,
                    shouldLoadDistantResources = it.shouldLoadDistantResources,
                ).toBundle(),
            )
        }

        detailedContactArgs.observe(viewLifecycleOwner) { contact ->
            safeNavigate(
                resId = R.id.detailedContactBottomSheetDialog,
                args = DetailedContactBottomSheetDialogArgs(
                    recipient = contact,
                ).toBundle(),
            )
        }
    }

    fun handleOnBackPressed() {
        when {
            isOnlyRightShown() -> resetPanes()
            this is ThreadListFragment -> requireActivity().finish()
            else -> findNavController().popBackStack()
        }
    }

    fun openThread(uid: String) {
        mainViewModel.currentThreadUid.value = uid
        slidingPaneLayout.open()
    }

    private fun resetPanes() {
        slidingPaneLayout.close()
        mainViewModel.currentThreadUid.value = null
        // TODO: We can see that the ThreadFragment's content is changing, while the pane is closing.
        //  Maybe we need to delay the transaction? Or better: start it when the pane is fully closed?
        childFragmentManager.beginTransaction().replace(R.id.threadHostFragment, ThreadFragment()).commit()
    }
}
