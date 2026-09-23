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
package com.infomaniak.mail.utils

import android.content.Context
import android.content.Intent
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.legacy.utils.hasSupportedApplications
import com.infomaniak.mail.data.cache.mailboxContent.AttachmentController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.extensions.getCacheFile
import com.infomaniak.mail.data.models.extensions.getUploadLocalFile
import com.infomaniak.mail.data.models.extensions.hasUsableCache
import com.infomaniak.mail.data.models.extensions.isInlineCachedFile
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentViewModel
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.AttachmentExt
import com.infomaniak.mail.utils.extensions.AttachmentExt.getIntentOrGoToAppStore
import com.infomaniak.mail.utils.extensions.AttachmentExt.openWithIntent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import com.infomaniak.core.legacy.R as RCore

interface AttachmentOperations {
    suspend fun hasSupportedApp(attachment: Attachment): Boolean
    suspend fun isCached(attachment: Attachment): Boolean
    suspend fun getOpenIntent(attachment: Attachment): Intent?
    suspend fun download(attachment: Attachment): Boolean
    suspend fun deleteIncompleteCache(attachment: Attachment)
}

class DefaultAttachmentOperations @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentController: AttachmentController,
) : AttachmentOperations {

    override suspend fun hasSupportedApp(attachment: Attachment): Boolean {
        return attachment.openWithIntent(context)?.hasSupportedApplications(context) == true
    }

    override suspend fun isCached(attachment: Attachment): Boolean {
        return attachment.hasUsableCache(context, attachment.getUploadLocalFile()) || attachment.isInlineCachedFile(context)
    }

    override suspend fun getOpenIntent(attachment: Attachment): Intent? {
        return attachment.getIntentOrGoToAppStore(context, AttachmentExt.AttachmentIntentType.OPEN_WITH)
    }

    override suspend fun download(attachment: Attachment): Boolean {
        val localAttachment = attachmentController.getAttachment(attachment.localUuid)
        return LocalStorageUtils.downloadThenSaveAttachmentToCacheDir(context, localAttachment)
    }

    override suspend fun deleteIncompleteCache(attachment: Attachment) {
        runCatchingRealm {
            attachment.getCacheFile(context)?.apply { if (exists()) delete() }
        }
    }
}

class AttachmentDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkManager: NetworkManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val operations: AttachmentOperations,
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
        showSnackbar: (String) -> Unit,
        openIntent: (Intent) -> Unit,
    ): Job? {
        if (isDownloading(attachment.localUuid)) return null

        val job = scope.launch {
            val hasApp = withContext(ioDispatcher) { operations.hasSupportedApp(attachment) }
            if (!hasApp) {
                downloadingJobs.remove(attachment.localUuid)
                val errorMsg = runCatching { context.getString(RCore.string.errorNoSupportingAppFound) }.getOrDefault("")
                showSnackbar(errorMsg)
                return@launch
            }

            val isAlreadyCached = withContext(ioDispatcher) { operations.isCached(attachment) }
            if (isAlreadyCached) {
                downloadingJobs.remove(attachment.localUuid)
                val intent = withContext(ioDispatcher) { operations.getOpenIntent(attachment) }
                intent?.let {
                    isWaitingForFirstToOpen = false
                    openIntent(it)
                }
                return@launch
            }

            onDownloadStateChanged(attachment.localUuid, true)
            isWaitingForFirstToOpen = true

            var isSuccess = false
            try {
                isSuccess = withContext(ioDispatcher) {
                    withTimeoutOrNull(DownloadAttachmentViewModel.DOWNLOAD_TIMEOUT) {
                        runCatching { operations.download(attachment) }.cancellable().getOrDefault(false)
                    } ?: false
                }

                if (isSuccess) {
                    if (isWaitingForFirstToOpen) {
                        isWaitingForFirstToOpen = false
                        val openWithIntent = withContext(ioDispatcher) { operations.getOpenIntent(attachment) }
                        if (isActive) {
                            openWithIntent?.let(openIntent)
                        }
                    }
                } else if (isActive) {
                    val errorRes = if (networkManager.hasNetwork) RCore.string.anErrorHasOccurred else RCore.string.noConnection
                    val errorMsg = runCatching { context.getString(errorRes) }.getOrDefault("")
                    showSnackbar(errorMsg)
                }
            } finally {
                withContext(NonCancellable + ioDispatcher) {
                    if (!isSuccess) {
                        operations.deleteIncompleteCache(attachment)
                    }
                }
                downloadingJobs.remove(attachment.localUuid)
                onDownloadStateChanged(attachment.localUuid, false)
                if (downloadingJobs.isEmpty()) {
                    isWaitingForFirstToOpen = true
                }
            }
        }

        downloadingJobs[attachment.localUuid] = job
        return job
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AttachmentDownloadModule {
    @Binds
    abstract fun bindAttachmentOperations(impl: DefaultAttachmentOperations): AttachmentOperations
}
