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
import androidx.annotation.StringRes
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.common.dynamicLazyMapOfSharedFlow
import com.infomaniak.core.common.flowForKey
import com.infomaniak.core.legacy.R
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.NetworkManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@ActivityScoped
class AttachmentDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkManager: NetworkManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val attachmentOperations: AttachmentOperations,
    private val snackbarManager: SnackbarManager,
) {

    private val downloadScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val downloads = downloadScope.dynamicLazyMapOfSharedFlow { localUuid: String ->
        createDownloadStates(localUuid)
    }

    private val downloadJobs = ConcurrentHashMap<String, Deferred<DownloadState>>()
    private val _downloadingUuids = MutableStateFlow(emptySet<String>())
    val downloadingUuids = _downloadingUuids.asStateFlow()

    /**
     * Keeps a download alive independently of the opening request. Re-selecting another attachment
     * only cancels the opening waiter, not this observer.
     */
    internal fun downloadAttachment(localUuid: String, scope: CoroutineScope): Deferred<DownloadState> {
        val job = scope.async(start = CoroutineStart.LAZY) {
            downloads.flowForKey(localUuid)
                .onEach { state ->
                    if (state is DownloadState.Downloading) _downloadingUuids.update { it + localUuid }
                }
                .first { it !is DownloadState.Downloading }
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
        val state = runCatching {
            val attachment = attachmentOperations.getAttachment(localUuid)
                ?: return@runCatching DownloadState.Failed(downloadErrorRes())

            if (attachmentOperations.isCached(attachment)) return@runCatching DownloadState.Ready(attachment)

            emit(DownloadState.Downloading)
            download(attachment)
        }.cancellable().getOrElse {
            SentryLog.e(TAG, "Attachment download failed for $localUuid", it)
            DownloadState.Failed(downloadErrorRes())
        }
        if (state is DownloadState.Failed) snackbarManager.postValue(context.getString(state.errorRes))
        emit(state)
    }

    private suspend fun download(attachment: Attachment): DownloadState {
        var isDownloadSuccess = false
        try {
            isDownloadSuccess = attachmentOperations.download(attachment)
            currentCoroutineContext().ensureActive()
            return if (isDownloadSuccess) DownloadState.Ready(attachment) else DownloadState.Failed(downloadErrorRes())
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

    internal sealed interface DownloadState {
        data object Downloading : DownloadState
        data class Ready(val attachment: Attachment) : DownloadState
        data class Failed(@StringRes val errorRes: Int) : DownloadState
    }

    private companion object {
        const val TAG = "AttachmentDownloadManager"
    }
}
