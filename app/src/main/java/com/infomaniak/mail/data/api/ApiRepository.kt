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
import com.infomaniak.lib.core.utils.ApiController
import com.infomaniak.lib.core.utils.ApiController.ApiMethod.*
import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.addressBook.AddressBooksResult
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.SignaturesResult
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.data.models.thread.ThreadsResult
import com.infomaniak.mail.data.models.user.UserResult

object ApiRepository : ApiRepositoryCore() {

    private inline fun <reified T> callKotlinxApi(url: String, method: ApiController.ApiMethod, body: Any? = null): T {
        return ApiController.callApi(url, method, body, useKotlinxSerialization = true)
    }

    fun getAddressBooks(): ApiResponse<AddressBooksResult> = callKotlinxApi(ApiRoutes.addressBooks(), GET)

    fun getContacts(): ApiResponse<ArrayList<Contact>> = callKotlinxApi(ApiRoutes.contacts(), GET)

    fun getUser(): ApiResponse<UserResult> = callKotlinxApi(ApiRoutes.user(), GET)

    // fun getContactImage(path: String): ApiResponse<Data> = callKotlinxApi(ApiRoutes.resource(path), GET)

    fun getSignatures(mailboxHostingId: Int, mailboxMailbox: String): ApiResponse<SignaturesResult> {
        return callKotlinxApi(ApiRoutes.signatures(mailboxHostingId, mailboxMailbox), GET)
    }

    fun getMailboxes(): ApiResponse<ArrayList<Mailbox>> = callKotlinxApi("${ApiRoutes.mailbox()}?with=unseen", GET)

    fun getFolders(mailboxUuid: String): ApiResponse<ArrayList<Folder>> = callKotlinxApi(ApiRoutes.folders(mailboxUuid), GET)

    // fun createFolder(mailboxUuid: String, name: String, path: String?): ApiResponse<Folder> = callKotlinxApi(ApiRoutes.folders(mailboxUuid), POST, mutableMapOf("name" to name).apply { path?.let { "path" to it } })

    // fun renameFolder(mailboxUuid: String, folderId: String, newName: String): ApiResponse<Folder> = callKotlinxApi(ApiRoutes.renameFolder(mailboxUuid, folderId), POST, mapOf("name" to newName))

    // fun favoriteFolder(mailboxUuid: String, folderId: String, favorite: Boolean): ApiResponse<Boolean> = callKotlinxApi(ApiRoutes.favoriteFolder(mailboxUuid, folderId, favorite), POST)

    // fun readFolder(mailboxUuid: String, folderId: String): ApiResponse<Boolean> = callKotlinxApi(ApiRoutes.readFolder(mailboxUuid, folderId), POST)

    // fun flushFolder(mailboxUuid: String, folderId: String): ApiResponse<Boolean> = callKotlinxApi(ApiRoutes.flushFolder(mailboxUuid, folderId), POST)

    // fun deleteFolder(mailboxUuid: String, folderId: String): ApiResponse<Boolean> = callKotlinxApi(ApiRoutes.folder(mailboxUuid, folderId), DELETE)

    fun getThreads(mailboxUuid: String, folderId: String, filter: ThreadFilter? = null): ApiResponse<ThreadsResult> {
        return callKotlinxApi(ApiRoutes.threads(mailboxUuid, folderId, filter?.name), GET)
    }

    fun getMessage(messageResource: String): ApiResponse<Message> {
        return callKotlinxApi(ApiRoutes.resource("$messageResource?name=prefered_format&value=html"), GET)
    }

    fun getQuotas(mailboxHostingId: Int, mailboxMailbox: String): ApiResponse<Quotas> {
        return callKotlinxApi(ApiRoutes.quotas(mailboxMailbox, mailboxHostingId), GET)
    }

    fun markMessagesAsSeen(mailboxUuid: String, messages: ArrayList<Message>): ApiResponse<Seen> {
        return callKotlinxApi(ApiRoutes.messageSeen(mailboxUuid), POST, mapOf("uids" to messages.map { it.uid }))
    }

    fun markMessagesAsUnseen(mailboxUuid: String, messages: ArrayList<Message>): ApiResponse<Seen> {
        return callKotlinxApi(ApiRoutes.messageUnseen(mailboxUuid), POST, mapOf("uids" to messages.map { it.uid }))
    }

    // fun markAsSafe(mailboxUuid: String, messages: ArrayList<Message>): ApiResponse<ArrayList<Seen>> = callKotlinxApi(ApiRoutes.messageSafe(mailboxUuid), POST, mapOf("uids" to messages.map { it.uid }))

    // fun trustSender(messageResource: String): ApiResponse<EmptyResponse> = callKotlinxApi(ApiRoutes.resource("$messageResource/trustForm"), POST)

    fun saveDraft(mailboxUuid: String, draft: Draft): ApiResponse<Draft> {
        fun postDraft(): ApiResponse<Draft> = callKotlinxApi(ApiRoutes.draft(mailboxUuid), POST, draft)
        fun putDraft(): ApiResponse<Draft> = callKotlinxApi(ApiRoutes.draft(mailboxUuid, draft.uuid), PUT, draft)
        return if (draft.uuid.isEmpty()) postDraft() else putDraft()
    }

    fun sendDraft(mailboxUuid: String, draft: Draft): ApiResponse<Boolean> {
        fun postDraft(): ApiResponse<Boolean> = callKotlinxApi(ApiRoutes.draft(mailboxUuid), POST, draft)
        fun putDraft(): ApiResponse<Boolean> = callKotlinxApi(ApiRoutes.draft(mailboxUuid, draft.uuid), PUT, draft)
        return if (draft.uuid.isEmpty()) postDraft() else putDraft()
    }

    fun deleteDraft(mailboxUuid: String, draftUuid: String): ApiResponse<EmptyResponse?> {
        return callKotlinxApi(ApiRoutes.draft(mailboxUuid, draftUuid), DELETE)
    }

    fun moveMessage(mailboxUuid: String, messages: ArrayList<Message>, destinationId: String): ApiResponse<MoveResult> {
        return callKotlinxApi(
            url = ApiRoutes.moveMessage(mailboxUuid),
            method = POST,
            body = mapOf("uids" to messages.map { it.uid }, "to" to destinationId),
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

    fun getDraft(messageDraftResource: String): ApiResponse<Draft> = callKotlinxApi(ApiRoutes.resource(messageDraftResource), GET)

    fun starMessage(star: Boolean, mailboxUuid: String, messageIds: ArrayList<String>): ApiResponse<StarMessageResult> {
        return callKotlinxApi(ApiRoutes.starMessage(mailboxUuid, star), POST, mapOf("uids" to messageIds))
    }

    // fun search(mailboxUuid: String, folderId: String, searchText: String): ApiResponse<Thread> = callKotlinxApi(ApiRoutes.search(mailboxUuid, folderId, searchText), GET)

    private fun pagination(page: Int, perPage: Int = PER_PAGE) = "page=$page&per_page=$perPage"
}
