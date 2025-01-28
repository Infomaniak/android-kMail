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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.LocalStorageUtils.getEmlCacheDir
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.appContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import kotlin.collections.set

@HiltViewModel
class DownloadMessagesViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val messageController: MessageController,
    private val threadController: ThreadController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private val messageLocalUids: Array<String>?
        inline get() = savedStateHandle[DownloadMessagesProgressDialogArgs::messageUids.name]

    private val threadLocalUids: Array<String>?
        inline get() = savedStateHandle[DownloadMessagesProgressDialogArgs::threadUids.name]

    val downloadMessagesLiveData = MutableLiveData<List<Uri>?>()

    private fun getAllMessages(): Set<Message> {
        val messages = mutableSetOf<Message>()
        messageLocalUids?.mapNotNull { messageController.getMessage(it) }?.let { messages.addAll(it) }

        threadLocalUids?.forEach { threadUid ->
            threadController.getThread(threadUid)?.let { thread ->
                messages.addAll(thread.messages)
            }
        }
        return messages
    }

    fun downloadThreads(currentMailbox: Mailbox?) {
        viewModelScope.launch(ioCoroutineContext) {
            val mailbox = currentMailbox ?: return@launch
            val messages = getAllMessages()

            runCatching {
                coroutineScope {
                    val listFileName = HashMap<String, Int>()

                    val deferredResponses = getAllMessages().map { message ->
                        async {
                            val response = ApiRepository.getDownloadedMessage(
                                mailboxUuid = mailbox.uuid,
                                folderId = message.folderId,
                                shortUid = message.shortUid,
                            )

                            if (!response.isSuccessful || response.body == null) return@async null

                            val messageSubject = message.subject?.removeIllegalFileNameCharacter() ?: NO_SUBJECT_FILE
                            val truncatedSubject = messageSubject.take(MAX_FILE_NAME_LENGTH)
                            val fileName = createUniqueFileName(listFileName, truncatedSubject)

                            saveEmlToFile(appContext, response.body!!.bytes(), fileName)
                        }
                    }

                    deferredResponses.awaitAll().filterNotNull()
                }
            }.onSuccess { downloadedThreadUris ->
                if (downloadedThreadUris.size != messages.size) {
                    // TODO: Manage error
                }
                downloadMessagesLiveData.postValue(downloadedThreadUris)
            }.onFailure { _ ->
                downloadMessagesLiveData.postValue(null)
            }
        }
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

    private fun saveEmlToFile(context: Context, emlByteArray: ByteArray, fileName: String): Uri? {
        val fileNameWithExtension = "${fileName.removeIllegalFileNameCharacter()}.eml"
        val fileDir = getEmlCacheDir(context)

        if (!fileDir.exists()) fileDir.mkdirs()

        return runCatching {
            val file = File(fileDir, fileNameWithExtension)
            file.outputStream().use { it.write(emlByteArray) }
            return FileProvider.getUriForFile(context, context.getString(R.string.EML_AUTHORITY), file)
        }.getOrNull()
    }

    fun getSubject(): String? {
        val messages = getAllMessages()
        return messages.firstOrNull()?.subject
    }

    fun numberOfMessagesToDownloads(): Int {
        return getAllMessages().size
    }

    private fun String.removeIllegalFileNameCharacter(): String = this.replace(DownloadManagerUtils.regexInvalidSystemChar, "")

    companion object {
        private const val NO_SUBJECT_FILE = "message"
        private const val MAX_FILE_NAME_LENGTH = 256
    }
}
