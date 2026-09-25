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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AttachmentDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkManager: NetworkManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val operations: AttachmentOperations,
    private val snackbarManager: SnackbarManager,
) {

    private val downloadingJobs = mutableMapOf<String, Job>()
    private var isWaitingForFirstToOpen = true

    fun isDownloading(localUuid: String): Boolean = downloadingJobs.containsKey(localUuid)

    fun cancelDownload(localUuid: String) {
        downloadingJobs.remove(localUuid)?.cancel()
    }

    fun downloadAndOpenAttachment(
        attachment: Attachment,
        scope: CoroutineScope,
        onDownloadStateChanged: (localUuid: String, isDownloading: Boolean) -> Unit,
        startIntent: (Intent) -> Unit,
    ) {
        if (isDownloading(attachment.localUuid)) return

        val job = scope.launch {
            val hasApp = withContext(ioDispatcher) { operations.hasSupportedApp(attachment) }
            if (!hasApp) {
                downloadingJobs.remove(attachment.localUuid)
                snackbarManager.postValue(context.getString(R.string.errorNoSupportingAppFound))
                return@launch
            }

            val isAlreadyCached = withContext(ioDispatcher) { operations.isCached(attachment) }
            if (isAlreadyCached) {
                downloadingJobs.remove(attachment.localUuid)
                openAttachment(attachment, startIntent)
                return@launch
            }

            onDownloadStateChanged(attachment.localUuid, true)
            isWaitingForFirstToOpen = true

            var isDownloadSuccess = false
            try {
                isDownloadSuccess = withContext(ioDispatcher) {
                    runCatching { operations.download(attachment) }
                        .cancellable()
                        .getOrDefault(false)
                }

                if (isDownloadSuccess) {
                    if (isWaitingForFirstToOpen) openAttachment(attachment, startIntent)
                } else {
                    ensureActive()
                    val errorRes = if (networkManager.hasNetwork) R.string.anErrorHasOccurred else R.string.noConnection
                    snackbarManager.postValue(context.getString(errorRes))
                }
            } finally {
                cleanUpAttachmentDownload(isDownloadSuccess, attachment, onDownloadStateChanged)
            }
        }

        downloadingJobs[attachment.localUuid] = job
    }

    private suspend fun openAttachment(attachment: Attachment, startIntent: (Intent) -> Unit) {
        withContext(ioDispatcher) {
            operations.getOpenIntent(attachment)?.let { intent ->
                isWaitingForFirstToOpen = false
                startIntent(intent)
            }
        }
    }

    private suspend fun cleanUpAttachmentDownload(
        isSuccess: Boolean,
        attachment: Attachment,
        onDownloadStateChanged: (String, Boolean) -> Unit
    ) {
        withContext(NonCancellable + ioDispatcher) {
            if (!isSuccess) operations.deleteIncompleteCache(attachment)
        }
        downloadingJobs.remove(attachment.localUuid)
        onDownloadStateChanged(attachment.localUuid, false)
        if (downloadingJobs.isEmpty()) isWaitingForFirstToOpen = true
    }
}
