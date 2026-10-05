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
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager.DownloadResult
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType.OPEN_WITH
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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

    /** [onFinished] closes an action dialog before launching the intent, or when the request fails. */
    @MainThread
    fun requestOpen(
        localUuid: String,
        scope: CoroutineScope,
        intentType: AttachmentIntentType = OPEN_WITH,
        onFinished: () -> Unit = {},
    ) {
        val download = scope.async {
            if (intentType == OPEN_WITH) {
                val canOpen = runCatching {
                    withContext(ioDispatcher) {
                        attachmentOperations.getAttachment(localUuid)?.let { attachmentOperations.hasSupportedApp(it) }
                    }
                }.cancellable().getOrElse {
                    SentryLog.e(TAG, "Could not check supporting applications for $localUuid", it)
                    return@async DownloadResult.Failed(R.string.anErrorHasOccurred)
                }
                if (canOpen == false) {
                    return@async DownloadResult.Failed(R.string.errorNoSupportingAppFound)
                }
            }

            downloadManager.downloadAttachment(localUuid, scope).await()
        }
        requestedAttachment.value = OpenRequest(localUuid, download, intentType, onFinished)
    }

    @MainThread
    fun cancelPendingOpen(localUuid: String? = null) {
        val request = requestedAttachment.value ?: return
        if (localUuid == null || request.localUuid == localUuid) requestedAttachment.compareAndSet(request, null)
    }

    @MainThread
    fun observeOpening(scope: CoroutineScope, startIntent: (Intent) -> Unit) {
        observationJob?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            requestedAttachment.collectLatest { request ->
                if (request == null) return@collectLatest

                try {
                    // Cancelling this waiter does not cancel the independently scoped download.
                    when (val result = request.download.await()) {
                        is DownloadResult.Ready -> openAttachment(request, result, startIntent)
                        is DownloadResult.Failed -> if (finishRequest(request)) showError(result.errorRes)
                    }
                } finally {
                    finishRequest(request)
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

    private suspend fun openAttachment(request: OpenRequest, result: DownloadResult.Ready, startIntent: (Intent) -> Unit) {
        val intent = runCatching {
            attachmentOperations.getOpenIntent(result.attachment, request.intentType)
        }.cancellable().getOrElse {
            SentryLog.e(TAG, "Could not prepare attachment ${request.localUuid}", it)
            if (finishRequest(request)) showError(R.string.anErrorHasOccurred)
            return
        }
        if (!finishRequest(request)) return

        if (intent == null) {
            // SAVE_TO_DRIVE can return null after redirecting to the store when kDrive is not installed.
            if (request.intentType == OPEN_WITH) {
                SentryLog.e(TAG, "No intent for attachment ${request.localUuid}")
                showError(R.string.anErrorHasOccurred)
            }
            return
        }

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

    private fun finishRequest(request: OpenRequest): Boolean {
        if (!requestedAttachment.compareAndSet(request, null)) return false

        request.onFinished()
        return true
    }

    // Identity distinguishes a new click on the same attachment from an already consumed request.
    private class OpenRequest(
        val localUuid: String,
        val download: Deferred<DownloadResult>,
        val intentType: AttachmentIntentType,
        val onFinished: () -> Unit,
    )

    private companion object {
        const val TAG = "AttachmentOpeningManager"
    }
}
