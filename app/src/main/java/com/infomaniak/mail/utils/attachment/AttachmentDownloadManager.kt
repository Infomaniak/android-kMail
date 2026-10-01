/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.utils.attachment

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.common.dynamicLazyMapOfSharedFlow
import com.infomaniak.core.common.flowForKey
import com.infomaniak.core.legacy.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.NetworkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class AttachmentDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkManager: NetworkManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val attachmentOperations: AttachmentOperations,
    private val snackbarManager: SnackbarManager,
) {

    private val downloadScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /**
     * One [SharedFlow] of [DownloadEvent] per attachment, created lazily by the first request and
     * shared by every concurrent observer of the same attachment. The flow is removed and cancelled
     * when its last observer leaves, which aborts a still-running download.
     */
    private val downloadEvents = downloadScope.dynamicLazyMapOfSharedFlow { localUuid: String ->
        createDownloadEvents(localUuid)
    }

    /** [Job]s of active observers, per attachment. Only used to cancel downloads and query [isDownloading]. */
    private val downloadAndOpenJobs = ConcurrentHashMap<String, MutableSet<Job>>()

    /**
     * [Attachment.localUuid] of the download that should open when it completes. Updated at every
     * click, so only the most recently requested download is opened; earlier ones just finish
     * downloading in the background.
     */
    private val lastRequestedDownloadUuid = AtomicReference<String?>(null)
    private val openMutex = Mutex()

    fun isDownloading(localUuid: String): Boolean = downloadAndOpenJobs.containsKey(localUuid)

    /**
     * Records [localUuid] as the download that should open when it completes. Must be called at
     * every user click requesting an attachment to open, including clicks on attachments which are
     * not downloaded by this manager (e.g. an open from the actions bottom sheet which downloads
     * through the progress dialog).
     */
    fun setLastRequestedDownload(localUuid: String) {
        lastRequestedDownloadUuid.set(localUuid)
    }

    fun cancelDownload(localUuid: String) {
        consumePendingOpen(localUuid)
        downloadAndOpenJobs.remove(localUuid)?.forEach { job -> job.cancel() }
    }

    fun downloadAndOpenAttachment(
        attachment: Attachment,
        scope: CoroutineScope,
        onDownloadStateChanged: (localUuid: String, isDownloading: Boolean) -> Unit,
        startIntent: (Intent) -> Unit,
    ) {
        setLastRequestedDownload(attachment.localUuid)
        scope.launch {
            try {
                val terminalEvent = downloadEvents.flowForKey(attachment.localUuid)
                    .onEach { event ->
                        if (event is DownloadEvent.DownloadStarted) {
                            onDownloadStateChanged(attachment.localUuid, true)
                        }
                    }
                    .first { it.isTerminal }

                when (terminalEvent) {
                    is DownloadEvent.ReadyToOpen -> openAttachment(attachment, startIntent)
                    is DownloadEvent.DownloadSucceeded -> openLastRequestedDownloadedAttachment(attachment, startIntent)
                    // DownloadFailed is terminal with nothing to open; DownloadStarted never satisfies the predicate
                    is DownloadEvent.DownloadFailed -> consumePendingOpen(attachment.localUuid)
                    is DownloadEvent.DownloadStarted -> Unit
                }
            } finally {
                withContext(NonCancellable) { onDownloadStateChanged(attachment.localUuid, false) }
            }
        }.trackDownloadAndOpenJob(attachment.localUuid)
    }

    private fun createDownloadEvents(localUuid: String): Flow<DownloadEvent> = flow {
        val attachment = attachmentOperations.getAttachment(localUuid) ?: return@flow emitDownloadFailure()

        if (!attachmentOperations.hasSupportedApp(attachment)) {
            emitDownloadFailure(R.string.errorNoSupportingAppFound)
            return@flow
        }

        if (attachmentOperations.isCached(attachment)) {
            emit(DownloadEvent.ReadyToOpen)
            return@flow
        }

        emit(DownloadEvent.DownloadStarted)

        var isDownloadSuccess = false
        try {
            isDownloadSuccess = runCatching { attachmentOperations.download(attachment) }.cancellable().getOrDefault(false)
            currentCoroutineContext().ensureActive()

            if (isDownloadSuccess) {
                emit(DownloadEvent.DownloadSucceeded)
            } else {
                emitDownloadFailure()
            }
        } finally {
            if (!isDownloadSuccess) withContext(NonCancellable) { attachmentOperations.deleteIncompleteCache(attachment) }
        }
    }

    private suspend fun FlowCollector<DownloadEvent>.emitDownloadFailure(@StringRes customErrorRes: Int? = null) {
        val errorRes = customErrorRes ?: if (networkManager.hasNetwork) R.string.anErrorHasOccurred else R.string.noConnection
        snackbarManager.postValue(context.getString(errorRes))
        emit(DownloadEvent.DownloadFailed)
    }

    private suspend fun openAttachment(attachment: Attachment, startIntent: (Intent) -> Unit) {
        val intent = withContext(ioDispatcher) { attachmentOperations.getOpenIntent(attachment) } ?: return
        startIntent(intent)
    }

    private suspend fun openLastRequestedDownloadedAttachment(attachment: Attachment, startIntent: (Intent) -> Unit) {
        openMutex.withLock {
            if (!consumePendingOpen(attachment.localUuid)) return
            val intent = withContext(ioDispatcher) { attachmentOperations.getOpenIntent(attachment) } ?: return
            startIntent(intent)
        }
    }

    /** Consumes the pending open request if it still points to [localUuid]; returns whether it was consumed. */
    private fun consumePendingOpen(localUuid: String): Boolean =
        lastRequestedDownloadUuid.compareAndSet(localUuid, null)

    private fun Job.trackDownloadAndOpenJob(localUuid: String) {
        downloadAndOpenJobs.computeIfAbsent(localUuid) { ConcurrentHashMap.newKeySet() }.add(this)
        invokeOnCompletion {
            downloadAndOpenJobs.compute(localUuid) { _, jobs ->
                jobs?.also { it.remove(this) }?.takeIf { it.isNotEmpty() }
            }
        }
    }

    private sealed interface DownloadEvent {
        data object DownloadStarted : DownloadEvent
        data object ReadyToOpen : DownloadEvent
        data object DownloadSucceeded : DownloadEvent
        data object DownloadFailed : DownloadEvent

        val isTerminal: Boolean get() = this !is DownloadStarted
    }
}
