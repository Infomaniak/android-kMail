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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.legacy.R
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager.DownloadResult
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager.DraftAttachment
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType.OPEN_WITH
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType.SAVE_TO_DRIVE
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    ) = requestOpen(localUuid, scope, intentType, onFinished) { null }

    @MainThread
    internal fun requestOpen(attachment: Attachment, scope: CoroutineScope, mailbox: suspend () -> Mailbox) {
        requestOpen(attachment.localUuid, scope, OPEN_WITH, onFinished = {}) { DraftAttachment(attachment, mailbox()) }
    }

    private fun requestOpen(
        localUuid: String,
        scope: CoroutineScope,
        intentType: AttachmentIntentType,
        onFinished: () -> Unit,
        draftAttachment: suspend () -> DraftAttachment?,
    ) {
        val download = scope.async(start = CoroutineStart.LAZY) {
            runCatching {
                val draft = draftAttachment()
                if (intentType == OPEN_WITH) {
                    val canOpen = withContext(ioDispatcher) {
                        val attachment = draft?.attachment ?: attachmentOperations.getAttachment(localUuid)
                        attachment?.let { attachmentOperations.hasSupportedApp(it, draft?.mailbox) }
                    }
                    if (canOpen == false) return@runCatching DownloadResult.Failed(R.string.errorNoSupportingAppFound)
                }

                downloadManager.downloadAttachment(localUuid, scope, draft).await()
            }.cancellable().getOrElse {
                SentryLog.e(TAG, "Could not prepare download for attachment $localUuid", it)
                DownloadResult.Failed(R.string.anErrorHasOccurred)
            }
        }
        requestedAttachment.value = OpenRequest(localUuid, download, intentType, onFinished)
        download.start()
    }

    @MainThread
    fun cancelPendingOpen(localUuid: String? = null, closeDialog: Boolean = false) {
        val request = requestedAttachment.value ?: return
        if (localUuid != null && request.localUuid != localUuid) return

        if (requestedAttachment.compareAndSet(request, null) && closeDialog) request.onFinished()
    }

    @MainThread
    fun observeOpening(owner: LifecycleOwner, startIntent: (Intent) -> Unit) {
        val previousJob = observationJob
        val lifecycle = owner.lifecycle
        val job = owner.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            lifecycle.repeatOnLifecycle(RESUMED) {
                requestedAttachment.collectLatest { request ->
                    if (request == null) return@collectLatest

                    try {
                        when (val result = request.download.await()) {
                            is DownloadResult.Ready -> openAttachment(request, result, lifecycle, startIntent)
                            is DownloadResult.Failed -> if (finishRequest(request, lifecycle)) showError(result.errorRes)
                        }
                    } catch (_: CancellationException) {
                        // Only consume an operation's cancellation, not a cancelled lifecycle waiter.
                        finishRequest(request, lifecycle)
                    }
                }
            }
        }
        observationJob = job
        previousJob?.cancel()
        job.invokeOnCompletion {
            if (observationJob === job) {
                cancelPendingOpen()
                observationJob = null
            }
        }
        job.start()
    }

    private suspend fun openAttachment(
        request: OpenRequest,
        result: DownloadResult.Ready,
        lifecycle: Lifecycle,
        startIntent: (Intent) -> Unit,
    ) {
        val openStore = request.intentType == SAVE_TO_DRIVE && !attachmentOperations.canSaveOnKDrive()
        val intent = runCatching {
            if (openStore) null else attachmentOperations.getOpenIntent(result.attachment, request.intentType, result.mailbox)
        }.cancellable().getOrElse {
            SentryLog.e(TAG, "Could not prepare attachment ${request.localUuid}", it)
            if (finishRequest(request, lifecycle)) showError(R.string.anErrorHasOccurred)
            return
        }
        if (!finishRequest(request, lifecycle)) return

        try {
            when {
                openStore -> attachmentOperations.openDriveAppStore()
                intent != null -> startIntent(intent)
                else -> {
                    SentryLog.e(TAG, "No intent for attachment ${request.localUuid}")
                    showError(R.string.anErrorHasOccurred)
                }
            }
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

    private suspend fun finishRequest(request: OpenRequest, lifecycle: Lifecycle): Boolean {
        currentCoroutineContext().ensureActive()
        if (lifecycle.currentState != RESUMED || !requestedAttachment.compareAndSet(request, null)) return false

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
