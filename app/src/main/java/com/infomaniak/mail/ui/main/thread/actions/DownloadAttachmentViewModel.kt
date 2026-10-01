/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.common.cancellable
import com.infomaniak.mail.data.cache.mailboxContent.AttachmentController
import com.infomaniak.mail.data.models.Attachable
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.extensions.getCacheFile
import com.infomaniak.mail.data.models.extensions.getUploadLocalFile
import com.infomaniak.mail.data.models.extensions.hasUsableCache
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.appContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class DownloadAttachmentViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val attachmentController: AttachmentController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val cleanupScope = CoroutineScope(ioDispatcher)

    private val attachmentLocalUuid
        inline get() = savedStateHandle.get<String>(DownloadAttachmentProgressDialogArgs::attachmentLocalUuid.name)!!

    /**
     * We keep the Attachment, in case the ViewModel is destroyed before it finishes downloading
     */
    private var attachment: Attachable? = null

    private val downloadAttachmentFlow: SharedFlow<Attachment?> = flow {
        val downloadedAttachment = withTimeoutOrNull(DOWNLOAD_TIMEOUT) {
            runCatching {
                val localAttachment = attachmentController.getAttachment(attachmentLocalUuid).also { attachment = it }

                var isAttachmentCached = localAttachment.hasUsableCache(appContext, localAttachment.getUploadLocalFile())
                if (!isAttachmentCached) {
                    isAttachmentCached = LocalStorageUtils.downloadThenSaveAttachmentToCacheDir(appContext, localAttachment)
                }

                return@runCatching if (isAttachmentCached) {
                    attachment = null
                    localAttachment
                } else {
                    null
                }
            }.cancellable().getOrNull()
        }

        if (downloadedAttachment == null) deleteIncompleteCacheFile()

        emit(downloadedAttachment)
    }.flowOn(ioDispatcher).shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    fun downloadAttachment(): SharedFlow<Attachment?> = downloadAttachmentFlow

    private suspend fun deleteIncompleteCacheFile() {
        runCatchingRealm { attachment?.getCacheFile(appContext)?.apply { if (exists()) delete() } }
        attachment = null
    }

    override fun onCleared() {
        // If we end up with an incomplete cached Attachment, we delete it
        cleanupScope.launch {
            deleteIncompleteCacheFile()
        }
        super.onCleared()
    }

    companion object {
        val DOWNLOAD_TIMEOUT = 2.minutes
    }
}
