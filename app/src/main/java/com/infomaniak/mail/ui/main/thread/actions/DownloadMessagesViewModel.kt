/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2026 Infomaniak Network SA
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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.legacy.utils.DownloadManagerUtils
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.LocalStorageUtils.getEmlCacheDir
import com.infomaniak.mail.utils.extensions.appContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadMessagesViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val messageController: MessageController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val messageLocalUids: Array<String>?
        inline get() = savedStateHandle[DownloadMessagesProgressDialogArgs::messageUids.name]

    val downloadMessagesLiveData = MutableLiveData<List<Uri>?>()

    private suspend fun getAllMessages(): Set<Message> {
        val messages = mutableSetOf<Message>()
        messageLocalUids?.mapNotNull { messageController.getMessage(it) }?.let(messages::addAll)
        return messages
    }

    private fun createUniqueFileName(listFileName: HashMap<String, Int>, truncatedSubject: String): String {
        val fileName = if (listFileName[truncatedSubject] == null) {
            listFileName[truncatedSubject] = 0
            truncatedSubject
        } else {
            listFileName[truncatedSubject] = listFileName[truncatedSubject]!! + 1
            "$truncatedSubject (${listFileName[truncatedSubject]!! + 1})"
        }
        return fileName
    }

    private fun saveEmlToFile(context: Context, emlByteArray: ByteArray, fileName: String): Uri {
        val fileNameWithExtension = "${fileName.removeIllegalFileNameCharacter()}.eml"
        val fileDir = getEmlCacheDir(context)

        if (!fileDir.exists()) fileDir.mkdirs()

        val file = File(fileDir, fileNameWithExtension)
        file.outputStream().use { it.write(emlByteArray) }
        return FileProvider.getUriForFile(context, context.getString(R.string.EML_AUTHORITY), file)
    }

    private suspend fun getFirstMessageSubject(): String? = getAllMessages().firstOrNull()?.subject

    private fun numberOfMessagesToDownloads(): Int = messageLocalUids?.size ?: 0

    fun downloadMessages(currentMailbox: Mailbox?) = viewModelScope.launch(ioDispatcher) {
        val mailbox = currentMailbox ?: return@launch

        val downloadedThreadUris = runCatching {
            val listFileName = HashMap<String, Int>()

            val deferredResponses = getAllMessages().map { message ->
                async {
                    val apiResponse = ApiRepository.getDownloadedMessage(
                        mailboxUuid = mailbox.uuid,
                        folderId = message.folderId,
                        shortUid = message.shortUid,
                    )

                    if (apiResponse.body == null || !apiResponse.isSuccessful) {
                        throw ByteArrayNetworkException(apiResponse.body.toString(), apiResponse.code)
                    }

                    val messageSubject = message.subject ?: NO_SUBJECT_FILE
                    val truncatedSubject = messageSubject.take(MAX_FILE_NAME_LENGTH)
                    val fileName = createUniqueFileName(listFileName, truncatedSubject)

                    saveEmlToFile(appContext, apiResponse.body?.bytes()!!, fileName)
                }
            }

            deferredResponses.awaitAll()
        }.cancellable().getOrElse {
            if (it is ByteArrayNetworkException) SentryLog.e(TAG, "Error while sharing messages to kDrive:", it)
            null
        }

        downloadMessagesLiveData.postValue(downloadedThreadUris)
    }

    suspend fun getDialogName(): String {
        val numberOfMessagesToDownload = numberOfMessagesToDownloads()

        return if (numberOfMessagesToDownload == 1) {
            getFirstMessageSubject() ?: appContext.getString(R.string.noSubjectTitle)
        } else {
            appContext.resources.getString(R.string.downloadingEmailsTitle, numberOfMessagesToDownload)
        }
    }

    private fun String.removeIllegalFileNameCharacter(): String = replace(DownloadManagerUtils.regexInvalidSystemChar, "")

    companion object {
        private const val NO_SUBJECT_FILE = "message"
        private const val MAX_FILE_NAME_LENGTH = 256
        private val TAG = DownloadMessagesViewModel::class.simpleName.toString()

        private data class ByteArrayNetworkException(val responseBody: String, val responseCode: Int) :
            Exception("Failed to get EML $responseCode: $responseBody")
    }
}
