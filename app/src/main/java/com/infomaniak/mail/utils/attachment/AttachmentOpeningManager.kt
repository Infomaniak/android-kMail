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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.legacy.R
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Shares the latest opening request between the host fragment and the attachment actions sheet. */
@ActivityScoped
class AttachmentOpeningManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: AttachmentDownloadManager,
    private val attachmentOperations: AttachmentOperations,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val snackbarManager: SnackbarManager,
) {

    private val requestedAttachment = MutableStateFlow<OpenRequest?>(null)
    private var observationJob: Job? = null

    @MainThread
    fun requestOpen(localUuid: String, scope: CoroutineScope) {
        requestedAttachment.value = OpenRequest(localUuid, downloadManager.downloadAttachment(localUuid, scope))
    }

    @MainThread
    fun cancelPendingOpen(localUuid: String? = null) {
        val request = requestedAttachment.value ?: return
        if (localUuid == null || request.localUuid == localUuid) requestedAttachment.compareAndSet(request, null)
    }

    @MainThread
    fun observeOpening(scope: CoroutineScope, startIntent: (Intent) -> Unit) {
        observationJob?.cancel()
        cancelPendingOpen()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            requestedAttachment.collectLatest { request ->
                if (request == null) return@collectLatest

                // Cancelling this waiter does not cancel the independently scoped download.
                val state = request.download.await()
                if (state is DownloadState.Ready) {
                    openAttachment(request, state, startIntent)
                } else {
                    requestedAttachment.compareAndSet(request, null)
                }
            }
        }
        observationJob = job
        job.invokeOnCompletion {
            if (observationJob === job) {
                cancelPendingOpen()
                observationJob = null
            }
        }
        job.start()
    }

    private suspend fun openAttachment(request: OpenRequest, state: DownloadState.Ready, startIntent: (Intent) -> Unit) {
        val intent = runCatching {
            withContext(ioDispatcher) {
                requireNotNull(attachmentOperations.getOpenIntent(state.attachment)) {
                    "No intent for attachment ${request.localUuid}"
                }
            }
        }.cancellable().getOrElse {
            SentryLog.e(TAG, "Could not prepare attachment ${request.localUuid}", it)
            if (requestedAttachment.compareAndSet(request, null)) showError(R.string.anErrorHasOccurred)
            return
        }
        if (!requestedAttachment.compareAndSet(request, null)) return

        try {
            startIntent(intent)
        } catch (exception: ActivityNotFoundException) {
            SentryLog.e(TAG, "No application could open attachment ${request.localUuid}", exception)
            showError(R.string.errorNoSupportingAppFound)
        } catch (exception: SecurityException) {
            SentryLog.e(TAG, "Could not open attachment ${request.localUuid}", exception)
            showError(R.string.anErrorHasOccurred)
        }
    }

    private fun showError(@StringRes errorRes: Int) {
        snackbarManager.postValue(context.getString(errorRes))
    }

    // Identity distinguishes a new click on the same attachment from an already consumed request.
    private class OpenRequest(val localUuid: String, val download: Deferred<DownloadState>)

    private companion object {
        const val TAG = "AttachmentOpeningManager"
    }
}
