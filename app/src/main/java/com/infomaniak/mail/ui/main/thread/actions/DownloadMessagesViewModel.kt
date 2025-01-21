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
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.LocalStorageUtils.getEmlCacheDir
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.appContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadMessagesViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val messageController: MessageController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private val messageLocalUids: Array<String>
        inline get() = savedStateHandle[DownloadMessagesProgressDialogArgs::messageUids.name]!!

    fun downloadThreads(currentMailbox: Mailbox?) = liveData(ioCoroutineContext) {
        val downloadedThreadUris: List<Uri>? = runCatching {
            val mailbox = currentMailbox ?: return@runCatching null
            val listUri = mutableListOf<Uri>()
            val listFileName = getAllFileNameInExportEmlDir(appContext)

            messageLocalUids.forEach { messageUid ->
                val message = messageController.getMessage(messageUid) ?: return@runCatching null
                val response = ApiRepository.getDownloadedMessage(
                    mailboxUuid = mailbox.uuid,
                    folderId = message.folderId,
                    shortUid = message.shortUid,
                )

                if (!response.isSuccessful || response.body == null) return@runCatching null

                val messageSubject: String = message.subject?.removeIllegalFileNameCharacter() ?: NO_SUBJECT_FILE

                val fileName = if (listFileName[messageSubject] == null) {
                    listFileName[messageSubject] = 0
                    messageSubject
                } else {
                    listFileName[messageSubject] = listFileName[messageSubject]!! + 1
                    "$messageSubject (${listFileName[messageSubject]!! + 1})"
                }

                saveEmlToFile(appContext, response.body!!.bytes(), fileName)?.let(listUri::add) ?: {
                    // TODO: Manage error case
                }
            }
            listUri
        }.getOrNull()

        emit(downloadedThreadUris)
    }

    private fun getAllFileNameInExportEmlDir(context: Context): HashMap<String, Int> {

        val fileNameMap = HashMap<String, Int>()
        val fileDir = getEmlCacheDir(context)
        if (!fileDir.exists()) fileDir.mkdirs()

        fileDir.listFiles()?.forEach { file ->
            val nameWithoutExtension = file.name.removeSuffix(".eml")

            val regex = Regex("\\((\\d+)\\)$")
            val match = regex.find(nameWithoutExtension)
            val numberInParentheses = match?.groupValues?.last()

            var fileNumber = numberInParentheses?.toInt() ?: 0
            val fileName = nameWithoutExtension.replace(regex, "").trim()

            fileNameMap[fileName]?.let {
                fileNumber = if (it > fileNumber) it else fileNumber
            }

            fileNameMap[fileName] = fileNumber
        }

        return fileNameMap
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

    private fun String.removeIllegalFileNameCharacter(): String = this.replace(DownloadManagerUtils.regexInvalidSystemChar, "")

    companion object {
        private const val NO_SUBJECT_FILE = "message"
    }
}
