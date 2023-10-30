/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.utils

import android.net.Uri
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter.NotificationType
import com.infomaniak.mail.ui.main.thread.DetailedContactBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.ThreadViewModel
import com.infomaniak.mail.ui.main.thread.actions.*
import io.sentry.Sentry
import kotlinx.coroutines.*
import okhttp3.internal.toHexString
import java.nio.charset.StandardCharsets

object Utils {

    val UTF_8: String = StandardCharsets.UTF_8.name()
    const val TEXT_HTML: String = "text/html"
    const val TEXT_PLAIN: String = "text/plain"
    /** The MIME type for data whose type is otherwise unknown. */
    const val MIMETYPE_UNKNOWN = "application/octet-stream"

    const val NUMBER_OF_OLD_MESSAGES_TO_FETCH = 500
    /** Beware: the API refuses a PAGE_SIZE bigger than 200. */
    const val PAGE_SIZE: Int = 50
    const val MAX_DELAY_BETWEEN_API_CALLS = 500L
    const val DELAY_BEFORE_FETCHING_ACTIVITIES_AGAIN = 500L

    const val TAG_SEPARATOR = " "

    const val SCHEME_SMSTO = "smsto:"

    fun colorToHexRepresentation(color: Int) = "#" + color.toHexString().substring(2 until 8)

    fun isPermanentDeleteFolder(role: FolderRole?): Boolean {
        return role == FolderRole.DRAFT || role == FolderRole.SPAM || role == FolderRole.TRASH
    }

    fun kSyncAccountUri(accountName: String): Uri = "content://com.infomaniak.sync.accounts/account/$accountName".toUri()

    inline fun <R> runCatchingRealm(block: () -> R): Result<R> {
        return runCatching { block() }.onFailure { exception ->
            if (!exception.shouldIgnoreRealmError()) Sentry.captureException(exception)
            exception.printStackTrace()
        }
    }

    suspend fun <T> executeWithTimeoutOrDefault(
        timeout: Long,
        defaultValue: T,
        block: CoroutineScope.() -> T,
        onTimeout: (() -> Unit)? = null,
    ): T = runCatching {
        coroutineWithTimeout(timeout, block)
    }.getOrElse {
        if (it is TimeoutCancellationException) onTimeout?.invoke() else Sentry.captureException(it)
        defaultValue
    }

    private suspend fun <T> coroutineWithTimeout(
        timeout: Long,
        block: CoroutineScope.() -> T,
    ): T = withTimeout(timeout) {
        var result: T? = null
        CoroutineScope(Dispatchers.Default).launch { result = block() }.join()
        result!!
    }

    fun Fragment.observeInTabletMode(
        mainViewModel: MainViewModel,
        threadViewModel: ThreadViewModel,
        threadListAdapter: ThreadListAdapter,
    ) = with(mainViewModel) {

        // Reset selected Thread UI when closing Thread
        threadViewModel.threadUid.observe(viewLifecycleOwner) { threadUid ->
            if (threadUid == null) threadListAdapter.apply {
                val position = clickedThreadPosition
                clickedThreadPosition = null
                clickedThreadUid = null
                position?.let { notifyItemChanged(it, NotificationType.SELECTED_STATE) }
            }
        }

        if (isTablet()) {

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
    }

    fun <T1, T2> waitInitMediator(liveData1: LiveData<T1>, liveData2: LiveData<T2>): MediatorLiveData<Pair<T1, T2>> {

        fun areLiveDataInitialized() = liveData1.isInitialized && liveData2.isInitialized

        fun MediatorLiveData<Pair<T1, T2>>.postIfInit() {
            @Suppress("UNCHECKED_CAST")
            if (areLiveDataInitialized()) postValue((liveData1.value as T1) to (liveData2.value as T2))
        }

        return MediatorLiveData<Pair<T1, T2>>().apply {
            addSource(liveData1) { postIfInit() }
            addSource(liveData2) { postIfInit() }
        }
    }

    enum class MailboxErrorCode {
        NO_MAILBOX,
        NO_VALID_MAILBOX,
    }
}
