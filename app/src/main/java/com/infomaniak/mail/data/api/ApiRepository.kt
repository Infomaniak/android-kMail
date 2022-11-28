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

import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.api.ApiController.ApiMethod.*
import com.infomaniak.lib.core.api.ApiRepositoryCore
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.addressBook.AddressBooksResult
import com.infomaniak.mail.data.models.correspondent.Contact
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.SaveDraftResult
import com.infomaniak.mail.data.models.getMessages.GetMessagesByUidsResult
import com.infomaniak.mail.data.models.getMessages.GetMessagesUidsDeltaResult
import com.infomaniak.mail.data.models.getMessages.GetMessagesUidsResult
import com.infomaniak.mail.data.models.message.DeleteMessageResult
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.SignaturesResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

object ApiRepository : ApiRepositoryCore() {

    const val PER_PAGE = 50

    private inline fun <reified T> callApi(
        url: String,
        method: ApiController.ApiMethod,
        body: Any? = null,
        okHttpClient: OkHttpClient = HttpClient.okHttpClient,
    ): T = ApiController.callApi(url, method, body, okHttpClient, true)

    fun getAddressBooks(): ApiResponse<AddressBooksResult> = callApi(ApiRoutes.addressBooks(), GET)

    fun getContacts(): ApiResponse<List<Contact>> = callApi(ApiRoutes.contacts(), GET)

    // fun getContactImage(path: String): ApiResponse<Data> = callKotlinxApi(ApiRoutes.resource(path), GET)

    fun getSignatures(mailboxHostingId: Int, mailboxName: String): ApiResponse<SignaturesResult> {
        return callApi(ApiRoutes.signatures(mailboxHostingId, mailboxName), GET)
    }

    fun getMailboxes(okHttpClient: OkHttpClient = HttpClient.okHttpClient): ApiResponse<List<Mailbox>> {
        return callApi(ApiRoutes.mailbox(), GET, okHttpClient = okHttpClient)
    }

    fun getFolders(mailboxUuid: String): ApiResponse<List<Folder>> = callApi(ApiRoutes.folders(mailboxUuid), GET)

    // fun createFolder(mailboxUuid: String, name: String, path: String?): ApiResponse<Folder> = callKotlinxApi(ApiRoutes.folders(mailboxUuid), POST, mutableMapOf("name" to name).apply { path?.let { "path" to it } })

    // fun renameFolder(mailboxUuid: String, folderId: String, newName: String): ApiResponse<Folder> = callKotlinxApi(ApiRoutes.renameFolder(mailboxUuid, folderId), POST, mapOf("name" to newName))

    // fun favoriteFolder(mailboxUuid: String, folderId: String, favorite: Boolean): ApiResponse<Boolean> = callKotlinxApi(ApiRoutes.favoriteFolder(mailboxUuid, folderId, favorite), POST)

    // fun readFolder(mailboxUuid: String, folderId: String): ApiResponse<Boolean> = callKotlinxApi(ApiRoutes.readFolder(mailboxUuid, folderId), POST)

    // fun flushFolder(mailboxUuid: String, folderId: String): ApiResponse<Boolean> = callKotlinxApi(ApiRoutes.flushFolder(mailboxUuid, folderId), POST)

    // fun deleteFolder(mailboxUuid: String, folderId: String): ApiResponse<Boolean> = callKotlinxApi(ApiRoutes.folder(mailboxUuid, folderId), DELETE)

    fun getMessage(messageResource: String): ApiResponse<Message> {
        return callApi(ApiRoutes.resource("$messageResource?name=prefered_format&value=html"), GET)
    }

    fun getQuotas(mailboxHostingId: Int, mailboxName: String): ApiResponse<Quotas> {
        return callApi(ApiRoutes.quotas(mailboxHostingId, mailboxName), GET)
    }

    fun markMessagesAsSeen(mailboxUuid: String, messagesUids: List<String>): ApiResponse<Seen> {
        return callApi(ApiRoutes.messageSeen(mailboxUuid), POST, mapOf("uids" to messagesUids))
    }

    fun markMessagesAsUnseen(mailboxUuid: String, messagesUids: List<String>): ApiResponse<Seen> {
        return callApi(ApiRoutes.messageUnseen(mailboxUuid), POST, mapOf("uids" to messagesUids))
    }

    // fun markAsSafe(mailboxUuid: String, messagesUids: List<String>): ApiResponse<List<Seen>> = callKotlinxApi(ApiRoutes.messageSafe(mailboxUuid), POST, mapOf("uids" to messagesUids))

    // fun trustSender(messageResource: String): ApiResponse<EmptyResponse> = callKotlinxApi(ApiRoutes.resource("$messageResource/trustForm"), POST)

    fun saveDraft(mailboxUuid: String, draft: Draft, okHttpClient: OkHttpClient): ApiResponse<SaveDraftResult> {
        val body = Json.encodeToString(draft.getJsonRequestBody()).removeEmptyRealmLists()

        fun postDraft(): ApiResponse<SaveDraftResult> = callApi(ApiRoutes.draft(mailboxUuid), POST, body, okHttpClient)
        fun putDraft(uuid: String): ApiResponse<SaveDraftResult> =
            callApi(ApiRoutes.draft(mailboxUuid, uuid), PUT, body, okHttpClient)

        return draft.remoteUuid?.let(::putDraft) ?: run(::postDraft)
    }

    fun sendDraft(mailboxUuid: String, draft: Draft, okHttpClient: OkHttpClient): ApiResponse<Boolean> {
        val body = Json.encodeToString(draft.getJsonRequestBody()).removeEmptyRealmLists()

        fun postDraft(): ApiResponse<Boolean> = callApi(ApiRoutes.draft(mailboxUuid), POST, body, okHttpClient)
        fun putDraft(uuid: String): ApiResponse<Boolean> = callApi(ApiRoutes.draft(mailboxUuid, uuid), PUT, body, okHttpClient)

        return draft.remoteUuid?.let(::putDraft) ?: run(::postDraft)
    }

    // fun deleteDraft(draftResource: String): ApiResponse<EmptyResponse?> = callApi(ApiRoutes.resource(draftResource), DELETE)

    fun deleteMessages(mailboxUuid: String, messageUids: List<String>): ApiResponse<DeleteMessageResult?> {
        return callApi(ApiRoutes.deleteMessage(mailboxUuid), POST, mapOf("uids" to messageUids))
    }

    fun moveMessages(mailboxUuid: String, messagesUids: List<String>, destinationId: String): ApiResponse<MoveResult> {
        return callApi(
            url = ApiRoutes.moveMessage(mailboxUuid),
            method = POST,
            body = mapOf("uids" to messagesUids, "to" to destinationId),
        )
    }

    // fun createAttachment(
    //     mailboxUuid: String,
    //     attachmentData: Data,
    //     disposition: Attachment.AttachmentDisposition,
    //     attachmentName: String,
    //     mimeType: String,
    // ): ApiResponse<Attachment> {
    //     // TODO: Add headers
    //     // let headers = ["x-ws-attachment-filename": attachmentName, "x-ws-attachment-mime-type": mimeType, "x-ws-attachment-disposition": disposition.rawValue]
    //     return callKotlinxApi(ApiRoutes.createAttachment(mailboxUuid), POST, attachmentData)
    // }

    fun getDraft(messageDraftResource: String): ApiResponse<Draft> = callApi(ApiRoutes.resource(messageDraftResource), GET)

    // fun starMessage(star: Boolean, mailboxUuid: String, messageIds: List<String>): ApiResponse<StarMessageResult> = callApi(ApiRoutes.starMessage(mailboxUuid, star), POST, mapOf("uids" to messageIds))

    // fun search(mailboxUuid: String, folderId: String, searchText: String): ApiResponse<Thread> = callKotlinxApi(ApiRoutes.search(mailboxUuid, folderId, searchText), GET)

    fun getMessagesUids(mailboxUuid: String, folderId: String, dateSince: String): ApiResponse<GetMessagesUidsResult> {
        return callApi(url = ApiRoutes.getMessagesUids(mailboxUuid, folderId, dateSince), method = GET)
    }

    fun getMessagesUidsDelta(mailboxUuid: String, folderId: String, signature: String): ApiResponse<GetMessagesUidsDeltaResult> {
        return callApi(url = ApiRoutes.getMessagesUidsDelta(mailboxUuid, folderId, signature), method = GET)
    }

    fun getMessagesByUids(
        mailboxUuid: String,
        folderId: String,
        messagesUids: List<String>,
    ): ApiResponse<GetMessagesByUidsResult> {
        return callApi(url = ApiRoutes.getMessagesByUids(mailboxUuid, folderId, messagesUids), method = GET)
    }

    /**
     * RealmLists cannot be null, so they have to be empty when there is no data.
     * But the kMail API doesn't support empty lists, so we have to replace them with a `null` value.
     */
    private fun String.removeEmptyRealmLists() = replace("[]", "null")
}
