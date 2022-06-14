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
import com.infomaniak.lib.core.utils.ApiController.ApiMethod.*
import com.infomaniak.lib.core.utils.ApiController.callApi
import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.addressBook.AddressBooksResult
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.SignaturesResult
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.data.models.thread.ThreadsResult
import com.infomaniak.mail.data.models.user.UserResult

object ApiRepository : ApiRepositoryCore() {

    fun getAddressBooks(): ApiResponse<AddressBooksResult> =
        callApi(ApiRoutes.addressBooks(), GET)

    fun getContacts(): ApiResponse<ArrayList<Contact>> =
        callApi(ApiRoutes.contacts(), GET)

    fun getUser(): ApiResponse<UserResult> =
        callApi(ApiRoutes.user(), GET)

//    fun getContactImage(path: String): ApiResponse<Data> =
//        callApi(ApiRoutes.resource(path), GET)

    fun getSignatures(mailbox: Mailbox): ApiResponse<SignaturesResult> =
        callApi(ApiRoutes.signatures(mailbox.hostingId, mailbox.mailbox), GET)

    fun getMailboxes(): ApiResponse<ArrayList<Mailbox>> =
        callApi(ApiRoutes.mailbox(), GET)

    fun getFolders(mailbox: Mailbox): ApiResponse<ArrayList<Folder>> =
        callApi(ApiRoutes.folders(mailbox.uuid), GET)

//    fun createFolder(mailbox: Mailbox, name: String, path: String?): ApiResponse<Folder> =
//        callApi(ApiRoutes.folders(mailbox.uuid), POST, mutableMapOf("name" to name).apply { path?.let { "path" to it } })

//    fun renameFolder(mailbox: Mailbox, folder: Folder, newName: String): ApiResponse<Folder> =
//        callApi(ApiRoutes.renameFolder(mailbox.uuid, folder.id), POST, mapOf("name" to newName))

//    fun favoriteFolder(mailbox: Mailbox, folder: Folder, favorite: Boolean): ApiResponse<Boolean> =
//        callApi(ApiRoutes.favoriteFolder(mailbox.uuid, folder.id, favorite), POST)

//    fun readFolder(mailbox: Mailbox, folder: Folder): ApiResponse<Boolean> =
//        callApi(ApiRoutes.readFolder(mailbox.uuid, folder.id), POST)

//    fun flushFolder(mailbox: Mailbox, folder: Folder): ApiResponse<Boolean> =
//        callApi(ApiRoutes.flushFolder(mailbox.uuid, folder.id), POST)

//    fun deleteFolder(mailbox: Mailbox, folder: Folder): ApiResponse<Boolean> =
//        callApi(ApiRoutes.folder(mailbox.uuid, folder.id), DELETE)

    fun getThreads(mailbox: Mailbox, folder: Folder, filter: ThreadFilter? = null): ApiResponse<ThreadsResult> =
        callApi(ApiRoutes.threads(mailbox.uuid, folder.id, filter?.name), GET)

    fun getMessage(message: Message): ApiResponse<Message> =
        callApi(ApiRoutes.resource("${message.resource}?name=prefered_format&value=html"), GET)

    fun getQuotas(mailbox: Mailbox): ApiResponse<Quotas> =
        callApi(ApiRoutes.quotas(mailbox.mailbox, mailbox.hostingId), GET)

//    fun markAsSeen(mailbox: Mailbox, messages: ArrayList<Message>): ApiResponse<ArrayList<Seen>> =
//        callApi(ApiRoutes.messageSeen(mailbox.uuid), POST, mapOf("uids" to messages.map { it.uid }))

//    fun markAsUnseen(mailbox: Mailbox, messages: ArrayList<Message>): ApiResponse<ArrayList<Seen>> =
//        callApi(ApiRoutes.messageUnseen(mailbox.uuid), POST, mapOf("uids" to messages.map { it.uid }))

//    fun markAsSafe(mailbox: Mailbox, messages: ArrayList<Message>): ApiResponse<ArrayList<Seen>> =
//        callApi(ApiRoutes.messageSafe(mailbox.uuid), POST, mapOf("uids" to messages.map { it.uid }))

//    fun trustSender(mailbox: Mailbox, message: Message): ApiResponse<EmptyResponse> =
//        callApi(ApiRoutes.resource("${message.resource}/trustForm"), POST)

    fun saveDraft(mailbox: Mailbox, draft: Draft): ApiResponse<Draft> {
        fun postDraft(): ApiResponse<Draft> = callApi(ApiRoutes.draft(mailbox.uuid), POST, draft)
        fun putDraft(): ApiResponse<Draft> = callApi(ApiRoutes.draft(mailbox.uuid, draft.uuid), PUT, draft)
        return if (draft.uuid.isEmpty()) postDraft() else putDraft()
    }

    fun send(mailbox: Mailbox, draft: Draft): ApiResponse<Boolean> {
        fun postDraft(): ApiResponse<Boolean> = callApi(ApiRoutes.draft(mailbox.uuid), POST, draft)
        fun putDraft(): ApiResponse<Boolean> = callApi(ApiRoutes.draft(mailbox.uuid, draft.uuid), PUT, draft)
        return if (draft.uuid.isEmpty()) postDraft() else putDraft()
    }

    fun getDraft(mailbox: Mailbox, draftUuid: String): ApiResponse<Draft> =
        callApi(ApiRoutes.draft(mailbox.uuid, draftUuid), GET)

    fun deleteDraft(mailbox: Mailbox, draftUuid: String): ApiResponse<EmptyResponse?> =
        callApi(ApiRoutes.draft(mailbox.uuid, draftUuid), DELETE)

    fun moveMessage(mailbox: Mailbox, messages: ArrayList<Message>, destinationId: String): ApiResponse<MoveResult> =
        callApi(ApiRoutes.moveMessage(mailbox.uuid), POST, mapOf("uids" to messages.map { it.uid }, "to" to destinationId))

//    fun createAttachment(
//        mailbox: Mailbox,
//        attachmentData: Data,
//        disposition: AttachmentDisposition,
//        attachmentName: String,
//        mimeType: String,
//    ): ApiResponse<Attachment> {
//        // TODO: Add headers
//        // let headers = ["x-ws-attachment-filename": attachmentName, "x-ws-attachment-mime-type": mimeType, "x-ws-attachment-disposition": disposition.rawValue]
//        return callApi(ApiRoutes.createAttachment(mailbox.uuid), POST, attachmentData)
//    }

    fun getDraft(message: Message): ApiResponse<Draft> =
        callApi(ApiRoutes.resource(message.draftResource), GET)

    fun starMessage(star: Boolean, mailbox: Mailbox, messageIds: ArrayList<String>): ApiResponse<StarMessageResult> =
        callApi(ApiRoutes.starMessage(mailbox.uuid, star), POST, mapOf("uids" to messageIds))

//    fun search(mailbox: Mailbox, folder: Folder, searchText: String): ApiResponse<Thread> =
//        callApi(ApiRoutes.search(mailbox.uuid, folder.id, searchText), GET)

    private fun pagination(page: Int, perPage: Int = PER_PAGE) = "page=$page&per_page=$perPage"
}
