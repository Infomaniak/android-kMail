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

import com.infomaniak.lib.core.api.ApiRepositoryCore
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.ApiController
import com.infomaniak.lib.core.utils.ApiController.ApiMethod.*
import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.addressBook.AddressBooksResult
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.SignaturesResult
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.data.models.thread.ThreadsResult
import com.infomaniak.mail.data.models.user.UserResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

// TODO: Handle all API call responses
object ApiRepository : ApiRepositoryCore() {

    const val PER_PAGE = 50
    const val OFFSET_FIRST_PAGE = 0

    private inline fun <reified T> callApi(
        url: String,
        method: ApiController.ApiMethod,
        body: Any? = null,
        okHttpClient: OkHttpClient = HttpClient.okHttpClient,
    ): T {
        return ApiController.callApi(url, method, body, okHttpClient, useKotlinxSerialization = true)
    }

    fun getAddressBooks(): ApiResponse<AddressBooksResult> = callApi(ApiRoutes.addressBooks(), GET)

    fun getContacts(): ApiResponse<List<Contact>> = callApi(ApiRoutes.contacts(), GET)

    fun getUser(): ApiResponse<UserResult> = callApi(ApiRoutes.user(), GET)

    // fun getContactImage(path: String): ApiResponse<Data> = callKotlinxApi(ApiRoutes.resource(path), GET)

    fun getSignatures(mailboxHostingId: Int, mailboxMailbox: String): ApiResponse<SignaturesResult> {
        return callApi(ApiRoutes.signatures(mailboxHostingId, mailboxMailbox), GET)
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

    fun getThreads(
        mailboxUuid: String,
        folderId: String,
        threadMode: String,
        offset: Int,
        filter: ThreadFilter,
    ): ApiResponse<ThreadsResult> {
        return callApi(ApiRoutes.threads(mailboxUuid, folderId, threadMode, offset, filter), GET)
    }

    fun getMessage(messageResource: String): ApiResponse<Message> {
        return callApi(ApiRoutes.resource("$messageResource?name=prefered_format&value=html"), GET)
    }

    fun getQuotas(mailboxHostingId: Int, mailboxMailbox: String): ApiResponse<Quotas> {
        return callApi(ApiRoutes.quotas(mailboxMailbox, mailboxHostingId), GET)
    }

    fun updateMailboxSettings(
        mailboxHostingId: Int,
        mailboxMailbox: String,
        hasMailFiltering: Boolean,
        hasMoveSpam: Boolean
    ): ApiResponse<Boolean> {
        val body = mapOf("has_mail_filtering" to hasMailFiltering, "has_move_spam" to hasMoveSpam)
        return callApi(ApiRoutes.securedMailbox(mailboxHostingId, mailboxMailbox), PATCH, body)
    }

    fun updateFolderRoles(mailboxHostingId: Int, mailboxMailbox: String, body: Map<String, String>) {
        return callApi(ApiRoutes.securedFolders(mailboxHostingId, mailboxMailbox), PUT, body)
    }

    fun markMessagesAsSeen(mailboxUuid: String, messagesUids: List<String>): ApiResponse<Seen> {
        return callApi(ApiRoutes.messageSeen(mailboxUuid), POST, mapOf("uids" to messagesUids))
    }

    fun markMessagesAsUnseen(mailboxUuid: String, messagesUids: List<String>): ApiResponse<Seen> {
        return callApi(ApiRoutes.messageUnseen(mailboxUuid), POST, mapOf("uids" to messagesUids))
    }

    // fun markAsSafe(mailboxUuid: String, messagesUids: List<String>): ApiResponse<List<Seen>> = callKotlinxApi(ApiRoutes.messageSafe(mailboxUuid), POST, mapOf("uids" to messagesUids))

    // fun trustSender(messageResource: String): ApiResponse<EmptyResponse> = callKotlinxApi(ApiRoutes.resource("$messageResource/trustForm"), POST)

    fun saveDraft(mailboxUuid: String, draft: Draft): ApiResponse<Draft> {
        val body = Json.encodeToString(draft)
        fun postDraft(): ApiResponse<Draft> = callApi(ApiRoutes.draft(mailboxUuid), POST, body)
        fun putDraft(): ApiResponse<Draft> = callApi(ApiRoutes.draft(mailboxUuid, draft.uuid), PUT, body)

        return if (draft.hasLocalUuid()) postDraft() else putDraft()
    }

    fun sendDraft(mailboxUuid: String, draft: Draft): ApiResponse<Boolean> {
        val body = Json.encodeToString(draft)
        fun postDraft(): ApiResponse<Boolean> = callApi(ApiRoutes.draft(mailboxUuid), POST, body)
        fun putDraft(): ApiResponse<Boolean> = callApi(ApiRoutes.draft(mailboxUuid, draft.uuid), PUT, body)

        return if (draft.hasLocalUuid()) postDraft() else putDraft()
    }

    fun deleteDraft(draftResource: String): ApiResponse<EmptyResponse?> = callApi(ApiRoutes.resource(draftResource), DELETE)

    fun deleteMessages(mailboxUuid: String, messageUids: List<String>): ApiResponse<EmptyResponse?> {
        return callApi(ApiRoutes.deleteMessage(mailboxUuid), POST, mapOf("uids" to messageUids))
    }

    fun moveMessage(mailboxUuid: String, messagesUids: List<String>, destinationId: String): ApiResponse<MoveResult> {
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

    fun starMessage(star: Boolean, mailboxUuid: String, messageIds: List<String>): ApiResponse<StarMessageResult> {
        return callApi(ApiRoutes.starMessage(mailboxUuid, star), POST, mapOf("uids" to messageIds))
    }

    // fun search(mailboxUuid: String, folderId: String, searchText: String): ApiResponse<Thread> = callKotlinxApi(ApiRoutes.search(mailboxUuid, folderId, searchText), GET)
}
