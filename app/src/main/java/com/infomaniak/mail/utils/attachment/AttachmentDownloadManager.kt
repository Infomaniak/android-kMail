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

import androidx.annotation.StringRes
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.common.dynamicLazyMap
import com.infomaniak.core.common.dynamicLazyMapOfSharedFlow
import com.infomaniak.core.common.flowForKey
import com.infomaniak.core.legacy.R
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.NetworkManager
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@ActivityScoped
class AttachmentDownloadManager @Inject constructor(
    private val networkManager: NetworkManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val attachmentOperations: AttachmentOperations,
) {

    private val downloadScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // The lock stays shared until the producer, including cancellation cleanup, has finished.
    private val downloadLocks = downloadScope.dynamicLazyMap { _: String -> Mutex() }

    private val downloads = downloadScope.dynamicLazyMapOfSharedFlow { localUuid: String ->
        createDownloadStates(localUuid)
    }

    private val downloadJobs = ConcurrentHashMap<String, Deferred<DownloadResult>>()
    private val _downloadingUuids = MutableStateFlow(emptySet<String>())
    val downloadingUuids = _downloadingUuids.asStateFlow()

    /**
     * Keeps a download alive independently of the opening request. Re-selecting another attachment
     * only cancels the opening waiter, not this observer. Failures are returned to the opening layer,
     * which decides whether the request is still relevant before displaying feedback.
     */
    internal fun downloadAttachment(localUuid: String, scope: CoroutineScope): Deferred<DownloadResult> {
        val job = scope.async(start = CoroutineStart.LAZY) {
            downloads.flowForKey(localUuid)
                .onEach { state ->
                    if (state is DownloadState.Downloading) _downloadingUuids.update { it + localUuid }
                }
                .filterIsInstance<DownloadState.Finished>()
                .first().result
        }
        val existingJob = downloadJobs.putIfAbsent(localUuid, job)
        if (existingJob != null) {
            job.cancel()
            return existingJob
        }

        job.invokeOnCompletion {
            if (downloadJobs.remove(localUuid, job)) _downloadingUuids.update { it - localUuid }
        }
        job.start()
        return job
    }

    fun cancelDownload(localUuid: String) {
        downloadJobs[localUuid]?.cancel()
    }

    private fun createDownloadStates(localUuid: String): Flow<DownloadState> = flow {
        downloadLocks.useElement(localUuid) { lock ->
            lock.withLock {
                val result = runCatching {
                    val attachment = attachmentOperations.getAttachment(localUuid)
                        ?: return@runCatching DownloadResult.Failed(downloadErrorRes())

                    if (attachmentOperations.isCached(attachment)) return@runCatching DownloadResult.Ready(attachment)

                    emit(DownloadState.Downloading)
                    withTimeoutOrNull(DOWNLOAD_TIMEOUT) { download(attachment) } ?: DownloadResult.Failed(downloadErrorRes())
                }.cancellable().getOrElse {
                    SentryLog.e(TAG, "Attachment download failed for $localUuid", it)
                    DownloadResult.Failed(downloadErrorRes())
                }
                emit(DownloadState.Finished(result))
            }
        }
    }

    private suspend fun download(attachment: Attachment): DownloadResult {
        var isDownloadSuccess = false
        try {
            isDownloadSuccess = attachmentOperations.download(attachment)
            currentCoroutineContext().ensureActive()
            return if (isDownloadSuccess) DownloadResult.Ready(attachment) else DownloadResult.Failed(downloadErrorRes())
        } finally {
            if (!isDownloadSuccess) {
                withContext(NonCancellable) {
                    runCatching { attachmentOperations.deleteIncompleteCache(attachment) }.cancellable().onFailure {
                        SentryLog.e(TAG, "Could not delete incomplete attachment ${attachment.localUuid}", it)
                    }
                }
            }
        }
    }

    @StringRes
    private fun downloadErrorRes() = if (networkManager.hasNetwork) R.string.anErrorHasOccurred else R.string.noConnection

    private sealed interface DownloadState {
        data object Downloading : DownloadState
        data class Finished(val result: DownloadResult) : DownloadState
    }

    internal sealed interface DownloadResult {
        data class Ready(val attachment: Attachment) : DownloadResult
        data class Failed(@StringRes val errorRes: Int) : DownloadResult
    }

    companion object {
        internal val DOWNLOAD_TIMEOUT = 2.minutes
        private const val TAG = "AttachmentDownloadManager"
    }
}
