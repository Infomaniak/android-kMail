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
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Draft
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.KMailHttpClient
import io.realm.kotlin.UpdatePolicy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.File

object MailApi {

    fun fetchMailboxes(): List<Mailbox>? {
        return ApiRepository.getMailboxes().data?.map { it.initLocalValues() }
    }

    fun fetchFolders(mailbox: Mailbox): List<Folder>? {
        return ApiRepository.getFolders(mailbox.uuid).data
    }

    fun fetchThreads(folder: Folder, mailboxUuid: String): List<Thread>? {
        return ApiRepository.getThreads(mailboxUuid, folder.id).data?.threads?.map { it.initLocalValues() }
    }

    fun fetchMessages(thread: Thread): List<Message> {
        return thread.messages.map { realmMessage ->
            if (realmMessage.fullyDownloaded) {
                realmMessage
            } else {
                // TODO: Handle if this API call fails
                ApiRepository.getMessage(realmMessage.resource).data?.also { completedMessage ->
                    completedMessage.apply {
                        initLocalValues() // TODO: Remove this when we have EmbeddedObjects
                        fullyDownloaded = true
                        body?.initLocalValues(uid) // TODO: Remove this when we have EmbeddedObjects
                        // TODO: Remove this `forEachIndexed` when we have EmbeddedObjects
                        @Suppress("SAFE_CALL_WILL_CHANGE_NULLABILITY", "UNNECESSARY_SAFE_CALL")
                        attachments?.forEachIndexed { index, attachment ->
                            attachment.initLocalValues(index, uid)
                        }
                    }
// TODO: uncomment this when managing draft folder
//                if (completedMessage.isDraft && currentFolder.role = Folder.FolderRole.DRAFT) {
//                    Log.e("TAG", "fetchMessagesFromApi: ${completedMessage.subject} | ${completedMessage.body?.value}")
//                    val draft = fetchDraftFromApi(completedMessage.draftResource, completedMessage.uid)
//                    completedMessage.draftUuid = draft?.uuid
//                }
                }
            } ?: realmMessage
        }
    }

    private fun fetchDraft(draftResource: String, parentUid: String): Draft? {
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

    suspend fun fetchAttachment(attachment: Attachment, cacheDir: File) {

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
