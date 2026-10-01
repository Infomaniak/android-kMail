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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class AttachmentDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkManager: NetworkManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val operations: AttachmentOperations,
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

    private val canOpenNextDownloadedAttachment = AtomicBoolean(true)
    private val openMutex = Mutex()

    fun isDownloading(localUuid: String): Boolean = downloadAndOpenJobs.containsKey(localUuid)

    fun cancelDownload(localUuid: String) {
        downloadAndOpenJobs.remove(localUuid)?.forEach { job -> job.cancel() }
    }

    fun downloadAndOpenAttachment(
        attachment: Attachment,
        scope: CoroutineScope,
        onDownloadStateChanged: (localUuid: String, isDownloading: Boolean) -> Unit,
        startIntent: (Intent) -> Unit,
    ) {
        scope.launch {
            var hasDownloadStarted = false
            try {
                val terminalEvent = downloadEvents.flowForKey(attachment.localUuid)
                    .onEach { event ->
                        if (event is DownloadEvent.DownloadStarted) {
                            hasDownloadStarted = true
                            onDownloadStateChanged(attachment.localUuid, true)
                        }
                    }
                    .first { it.isTerminal }

                when (terminalEvent) {
                    is DownloadEvent.ReadyToOpen -> openAttachment(attachment, startIntent)
                    is DownloadEvent.DownloadSucceeded -> openFirstDownloadedAttachment(attachment, startIntent)
                    // DownloadFailed is terminal with nothing to open; DownloadStarted never satisfies the predicate
                    else -> Unit
                }
            } finally {
                if (hasDownloadStarted) {
                    withContext(NonCancellable) { onDownloadStateChanged(attachment.localUuid, false) }
                }
            }
        }.trackDownloadAndOpenJob(attachment.localUuid)
    }

    private fun createDownloadEvents(localUuid: String): Flow<DownloadEvent> = flow {
        val attachment = operations.getAttachment(localUuid)
            ?: return@flow emitDownloadFailure()

        if (!operations.hasSupportedApp(attachment)) {
            snackbarManager.postValue(context.getString(R.string.errorNoSupportingAppFound))
            emit(DownloadEvent.DownloadFailed)
            return@flow
        }

        if (operations.isCached(attachment)) {
            emit(DownloadEvent.ReadyToOpen)
            return@flow
        }

        canOpenNextDownloadedAttachment.set(true)
        emit(DownloadEvent.DownloadStarted)

        var isDownloadSuccess = false
        try {
            isDownloadSuccess = runCatching { operations.download(attachment) }.cancellable().getOrDefault(false)
            if (isDownloadSuccess) {
                emit(DownloadEvent.DownloadSucceeded)
            } else {
                currentCoroutineContext().ensureActive()
                emitDownloadFailure()
            }
        } finally {
            if (!isDownloadSuccess) withContext(NonCancellable) { operations.deleteIncompleteCache(attachment) }
        }
    }

    private suspend fun FlowCollector<DownloadEvent>.emitDownloadFailure() {
        val errorRes = if (networkManager.hasNetwork) R.string.anErrorHasOccurred else R.string.noConnection
        snackbarManager.postValue(context.getString(errorRes))
        emit(DownloadEvent.DownloadFailed)
    }

    private suspend fun openAttachment(attachment: Attachment, startIntent: (Intent) -> Unit) {
        val intent = withContext(ioDispatcher) { operations.getOpenIntent(attachment) } ?: return
        startIntent(intent)
    }

    private suspend fun openFirstDownloadedAttachment(attachment: Attachment, startIntent: (Intent) -> Unit) {
        openMutex.withLock {
            if (!canOpenNextDownloadedAttachment.get()) return
            val intent = withContext(ioDispatcher) { operations.getOpenIntent(attachment) } ?: return
            canOpenNextDownloadedAttachment.set(false)
            startIntent(intent)
        }
    }

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
