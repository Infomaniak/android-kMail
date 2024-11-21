/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.appContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadThreadsViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val messageController: MessageController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private val messageLocalUuids
        inline get() = savedStateHandle.get<Array<String>>(DownloadThreadsProgressDialogArgs::messageUuids.name)!!

    fun downloadThreads(currentMailbox: Mailbox?) = liveData(ioCoroutineContext) {
        val downloadedThreadUris: List<Uri>? = runCatching {
            val mailbox = currentMailbox ?: return@runCatching null
            val listUri = mutableListOf<Uri>()
            val listFileName = mutableSetOf<String>().also { it.addAll(getAllFileNameInExportEmlDir(appContext)) }

            messageLocalUuids.forEach { messageUid ->
                val message = messageController.getMessage(messageUid) ?: return@runCatching null
                val response = ApiRepository.getDownloadedAttachment(mailbox.uuid, message.folderId, message.shortUid)

                if (!response.isSuccessful || response.body == null) return@runCatching null

                val messageSubject: String = message.subject?.removeIllegalFileNameCharacter() ?: NO_SUBJECT_FILE
                createOriginalFileName(messageSubject, listFileName.toList()).let { fileName ->
                    listFileName.add(fileName)
                    saveEmlToFile(appContext, response.body!!.bytes(), fileName)?.let { listUri.add(it) }
                }
            }
            listUri
        }.getOrNull()

        emit(downloadedThreadUris)
    }

    // TODO Extract this code in core2
    private fun createOriginalFileName(originalFileName: String, listFileName: List<String>): String {
        var postfix = 1
        var fileName = originalFileName

        while (listFileName.contains(fileName)) {
            fileName = "$originalFileName (${postfix++})"
        }

        return fileName
    }

    private fun getAllFileNameInExportEmlDir(context: Context): List<String> {
        val fileDir = File(context.cacheDir, context.getString(com.infomaniak.mail.R.string.EXPOSED_EML_PATH))

        if (!fileDir.exists()) fileDir.mkdirs()

        return fileDir.listFiles()?.map { it.name.removeSuffix(".eml") } ?: emptyList()
    }

    private fun saveEmlToFile(context: Context, emlByteArray: ByteArray, fileName: String): Uri? {
        val fileNameWithExtension = "${fileName.removeIllegalFileNameCharacter()}.eml"
        val fileDir = File(context.cacheDir, context.getString(com.infomaniak.mail.R.string.EXPOSED_EML_PATH))

        if (!fileDir.exists()) fileDir.mkdirs()

        runCatching {
            val file = File(fileDir, fileNameWithExtension)
            file.outputStream().use { it.write(emlByteArray) }
            return FileProvider.getUriForFile(context, context.getString(com.infomaniak.mail.R.string.EML_AUTHORITY), file)
        }.onFailure { exception ->
            exception.printStackTrace()
        }
        return null
    }

    private fun String.removeIllegalFileNameCharacter(): String = this.replace(DownloadManagerUtils.regexInvalidSystemChar, "")

    companion object {
        private const val EML_CONTENT_TYPE = "message/rfc822"
        private const val NO_SUBJECT_FILE = "message"
    }
}
