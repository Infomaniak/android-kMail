/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.data.api

import android.util.Log
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.KMailHttpClient
import io.realm.Realm
import io.realm.UpdatePolicy
import io.realm.toRealmList
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.use
import java.io.BufferedInputStream
import java.io.File

object MailApi {

    fun fetchMailboxesFromApi(): List<Mailbox> {
        // Get current data
        Log.d("API", "Mailboxes: Get current data")
        val mailboxesFromRealm = MailboxInfoController.getMailboxes()
        val mailboxesFromApi = with(ApiRepository.getMailboxes()) {
            if (!isSuccess()) return mailboxesFromRealm
            data?.map { it.initLocalValues() } ?: emptyList()
        }

        // Get outdated data
        Log.d("API", "Mailboxes: Get outdated data")
        val deletableMailboxes = MailboxInfoController.getDeletableMailboxes(mailboxesFromApi)

        // Save new data
        Log.i("API", "Mailboxes: Save new data")
        MailboxInfoController.upsertMailboxes(mailboxesFromApi)

        // Delete outdated data
        Log.e("API", "Mailboxes: Delete outdated data")
        val isCurrentMailboxDeleted = deletableMailboxes.any { it.mailboxId == AccountUtils.currentMailboxId }
        if (isCurrentMailboxDeleted) {
            MailRealm.closeMailboxContent()
            AccountUtils.currentMailboxId = AppSettings.DEFAULT_ID
        }
        MailboxInfoController.deleteMailboxes(deletableMailboxes)
        deletableMailboxes.forEach { Realm.deleteRealm(MailRealm.getMailboxConfiguration(it.mailboxId)) }

        return if (isCurrentMailboxDeleted) {
            AccountUtils.reloadApp()
            emptyList()
        } else {
            mailboxesFromApi
        }
    }

    fun fetchFoldersFromApi(mailboxUuid: String): List<Folder> {

        // Get current data
        Log.d("API", "Folders: Get current data")
        val foldersFromRealm = MailboxContentController.getFolders()
        val foldersFromApi = with(ApiRepository.getFolders(mailboxUuid)) {
            if (!isSuccess()) return foldersFromRealm
            data ?: emptyList()
        }

        // Get outdated data
        Log.d("API", "Folders: Get outdated data")
        // val deletableFolders = MailboxContentController.getDeletableFolders(foldersFromApi)
        val deletableFolders = foldersFromRealm.filter { fromRealm ->
            !foldersFromApi.any { fromApi -> fromApi.id == fromRealm.id }
        }
        val possiblyDeletableThreads = deletableFolders.flatMap { it.threads }
        val deletableMessages = possiblyDeletableThreads.flatMap { it.messages }.filter { message ->
            deletableFolders.any { folder -> folder.id == message.folderId }
        }
        val deletableThreads = possiblyDeletableThreads.filter { thread ->
            thread.messages.all { message -> deletableMessages.any { it.uid == message.uid } }
        }

        // Save new data
        Log.i("API", "Folders: Save new data")
        MailRealm.mailboxContent.writeBlocking {
            foldersFromApi.forEach { folderFromApi ->
                val folder = copyToRealm(folderFromApi, UpdatePolicy.ALL)
                foldersFromRealm.find { it.id == folderFromApi.id }?.threads
                    ?.mapNotNull(::findLatest)
                    ?.let { folder.threads = it.toRealmList() }
            }
        }

        // Delete outdated data
        Log.e("API", "Folders: Delete outdated data")
        MailboxContentController.deleteMessages(deletableMessages)
        MailboxContentController.deleteThreads(deletableThreads)
        MailboxContentController.deleteFolders(deletableFolders)

        return foldersFromApi
    }

    fun fetchThreadsFromApi(folder: Folder, mailboxUuid: String) {
        // Get current data
        Log.d("API", "Threads: Get current data")
        val threadsFromRealm = folder.threads
        val threadsFromApi = with(ApiRepository.getThreads(mailboxUuid, folder.id)) {
            if (!isSuccess()) return
            data?.threads
                ?.map { threadFromApi ->
                    threadFromApi.initLocalValues()
                    // TODO: Put this back (and make it work) when we have EmbeddedObjects
                    // threadsFromRealm.find { it.uid == threadFromApi.uid }?.let { threadFromRealm ->
                    //     threadFromApi.messages.forEach { messageFromApi ->
                    //         threadFromRealm.messages.find { it.uid == messageFromApi.uid }?.let { messageFromRealm ->
                    //             messageFromApi.apply {
                    //                 fullyDownloaded = messageFromRealm.fullyDownloaded
                    //                 body = messageFromRealm.body
                    //                 attachments = messageFromRealm.attachments
                    //             }
                    //         }
                    //     }
                    // }
                    // threadFromApi
                }
                ?: emptyList()
        }

        // Get outdated data
        Log.d("API", "Threads: Get outdated data")
        // val deletableThreads = MailboxContentController.getDeletableThreads(threadsFromApi)
        val deletableThreads = threadsFromRealm.filter { fromRealm ->
            !threadsFromApi.any { fromApi -> fromApi.uid == fromRealm.uid }
        }
        val deletableMessages = deletableThreads.flatMap { thread -> thread.messages.filter { it.folderId == folder.id } }

        // Save new data
        Log.i("API", "Threads: Save new data")
        folder.threads = threadsFromApi.toRealmList()
        MailboxContentController.upsertFolder(folder)

        // Delete outdated data
        Log.e("API", "Threads: Delete outdated data")
        MailboxContentController.deleteMessages(deletableMessages)
        MailboxContentController.deleteThreads(deletableThreads)
    }

    fun fetchMessagesFromApi(thread: Thread) {
        // Get current data
        Log.d("API", "Messages: Get current data")
        val messagesFromRealm = thread.messages
        val messagesFromApi = messagesFromRealm.mapNotNull {
            // TODO: Handle if this API call fails
            ApiRepository.getMessage(it.resource).data?.also { completedMessage ->
                completedMessage.initLocalValues() // TODO: Remove this when we have EmbeddedObjects
                completedMessage.fullyDownloaded = true
                completedMessage.body?.initLocalValues(completedMessage.uid) // TODO: Remove this when we have EmbeddedObjects
                // TODO: Remove this `forEachIndexed` when we have EmbeddedObjects
                @Suppress("SAFE_CALL_WILL_CHANGE_NULLABILITY", "UNNECESSARY_SAFE_CALL")
                completedMessage.attachments?.forEachIndexed { index, attachment ->
                    attachment.initLocalValues(index, completedMessage.uid)
                }
            }
        }

        // Get outdated data
        Log.d("API", "Messages: Get outdated data")
        // val deletableMessages = MailboxContentController.getDeletableMessages(messagesFromApi)
        val deletableMessages = messagesFromRealm.filter { fromRealm ->
            !messagesFromApi.any { fromApi -> fromApi.uid == fromRealm.uid }
        }

        // Save new data
        Log.i("API", "Messages: Save new data")
        MailboxContentController.upsertMessages(messagesFromApi)

        // Delete outdated data
        Log.e("API", "Messages: Delete outdated data")
        MailboxContentController.deleteMessages(deletableMessages)
    }

    private fun fetchDraftFromApi(draftResource: String, parentUid: String): Draft? {
        val apiDraft = ApiRepository.getDraft(draftResource).data
        apiDraft?.let { draft ->
            draft.apply {
                initLocalValues(parentUid)
                // TODO: Remove this `forEachIndexed` when we have EmbeddedObjects
                attachments.forEachIndexed { index, attachment -> attachment.initLocalValues(index, parentUid) }
            }
            MailboxContentController.upsertDraft(draft)
        }

        return apiDraft
    }

    suspend fun fetchAttachmentsFromApi(attachment: Attachment, cacheDir: File) {

        fun downloadAttachmentData(fileUrl: String, okHttpClient: OkHttpClient): Response {
            val request = Request.Builder().url(fileUrl).headers(HttpUtils.getHeaders(contentType = null)).get().build()
            return okHttpClient.newBuilder().build().newCall(request).execute()
        }

        fun saveAttachmentData(response: Response, outputFile: File, onFinish: (() -> Unit)) {
            Log.d("TAG", "Save remote data to ${outputFile.path}")
            BufferedInputStream(response.body?.byteStream()).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                    onFinish()
                }
            }
        }

        val response = downloadAttachmentData(
            fileUrl = ApiRoutes.resource(attachment.resource),
            okHttpClient = KMailHttpClient.getHttpClient(AccountUtils.currentUserId),
        )

        val file = File(cacheDir, "${attachment.uuid}_${attachment.name}")

        saveAttachmentData(response, file) {
            attachment.localUri = file.toURI().toString()
            MailRealm.mailboxContent.writeBlocking { copyToRealm(attachment, UpdatePolicy.ALL) }
        }
    }
}
