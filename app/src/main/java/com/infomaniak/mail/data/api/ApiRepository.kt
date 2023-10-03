/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.PaginationInfo
import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.Attachment.AttachmentDisposition
import com.infomaniak.mail.data.models.FeatureFlag.FeatureFlagType
import com.infomaniak.mail.data.models.addressBook.AddressBooksResult
import com.infomaniak.mail.data.models.ai.AiMessage
import com.infomaniak.mail.data.models.ai.AiResult
import com.infomaniak.mail.data.models.ai.UserMessage
import com.infomaniak.mail.data.models.correspondent.Contact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.SaveDraftResult
import com.infomaniak.mail.data.models.draft.SendDraftResult
import com.infomaniak.mail.data.models.getMessages.ActivitiesResult
import com.infomaniak.mail.data.models.getMessages.GetMessagesByUidsResult
import com.infomaniak.mail.data.models.getMessages.NewMessagesResult
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.MailboxLinkedResult
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.data.models.signature.SignaturesResult
import com.infomaniak.mail.data.models.thread.ThreadResult
import com.infomaniak.mail.ui.main.newMessage.AiViewModel.Shortcut
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object ApiRepository : ApiRepositoryCore() {

    inline fun <reified T> callApi(
        url: String,
        method: ApiController.ApiMethod,
        body: Any? = null,
        okHttpClient: OkHttpClient = HttpClient.okHttpClient,
    ): T = ApiController.callApi(url, method, body, okHttpClient, true)

    fun ping(): ApiResponse<String> = callApi(ApiRoutes.ping(), GET)

    fun getAddressBooks(): ApiResponse<AddressBooksResult> = callApi(ApiRoutes.addressBooks(), GET)

    fun getContacts(): ApiResponse<List<Contact>> = callApi(ApiRoutes.contacts(), GET)

    fun addContact(addressBookId: Int, recipient: Recipient): ApiResponse<Int> {
        val (firstName, lastName) = recipient.computeFirstAndLastName()

        val body = mapOf(
            "addressbookId" to addressBookId,
            "firstname" to firstName,
            "lastname" to lastName,
            "emails" to arrayOf(mapOf("value" to recipient.email, "type" to "HOME")),
        )

        return callApi(ApiRoutes.contact(), POST, body)
    }

    fun getSignatures(mailboxHostingId: Int, mailboxName: String): ApiResponse<SignaturesResult> {
        return callApi(ApiRoutes.signatures(mailboxHostingId, mailboxName), GET)
    }

    fun setDefaultSignature(mailboxHostingId: Int, mailboxName: String, signature: Signature): ApiResponse<Boolean> {
        return callApi(ApiRoutes.signature(mailboxHostingId, mailboxName, signature.id), PUT, Json.encodeToString(signature))
    }

    fun getBackups(mailboxHostingId: Int, mailboxName: String): ApiResponse<BackupResult> {
        return callApi(ApiRoutes.backups(mailboxHostingId, mailboxName), GET)
    }

    fun restoreBackup(mailboxHostingId: Int, mailboxName: String, date: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.backups(mailboxHostingId, mailboxName), PUT, body = mapOf("date" to date))
    }

    fun getMailboxes(okHttpClient: OkHttpClient? = null): ApiResponse<List<Mailbox>> {
        return callApi(ApiRoutes.mailbox(), GET, okHttpClient = okHttpClient ?: HttpClient.okHttpClient)
    }

    fun addNewMailbox(mailAddress: String, password: String): ApiResponse<MailboxLinkedResult> {
        return callApi(ApiRoutes.manageMailboxes(), POST, mapOf("mail" to mailAddress, "password" to password))
    }

    fun detachMailbox(mailboxId: Int): ApiResponse<Boolean> = callApi(ApiRoutes.manageMailbox(mailboxId), DELETE)

    fun updateMailboxPassword(mailboxId: Int, password: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.updateMailboxPassword(mailboxId), PUT, mapOf("password" to password))
    }

    fun requestMailboxPassword(mailboxHostingId: Int, mailboxName: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.requestMailboxPassword(mailboxHostingId, mailboxName), POST)
    }

    fun getFolders(mailboxUuid: String): ApiResponse<List<Folder>> = callApi(ApiRoutes.folders(mailboxUuid), GET)

    fun createFolder(mailboxUuid: String, name: String): ApiResponse<Folder> {
        return callApi(ApiRoutes.folders(mailboxUuid), POST, mapOf("name" to name))
    }

    fun getMessage(messageResource: String, okHttpClient: OkHttpClient? = null): ApiResponse<Message> {
        return callApi(
            url = ApiRoutes.resource("$messageResource?name=prefered_format&value=html"),
            method = GET,
            okHttpClient = okHttpClient ?: HttpClient.okHttpClient,
        )
    }

    fun getQuotas(mailboxHostingId: Int, mailboxName: String): ApiResponse<Quotas> {
        return callApi(ApiRoutes.quotas(mailboxHostingId, mailboxName), GET)
    }

    fun getPermissions(mailboxLinkId: Int, mailboxHostingId: Int): ApiResponse<MailboxPermissions> {
        return callApi(ApiRoutes.permissions(mailboxLinkId, mailboxHostingId), GET)
    }

    fun markMessagesAsSeen(mailboxUuid: String, messagesUids: List<String>): ApiResponse<Unit> {
        return callApi(ApiRoutes.messagesSeen(mailboxUuid), POST, mapOf("uids" to messagesUids))
    }

    fun markMessagesAsUnseen(mailboxUuid: String, messagesUids: List<String>): ApiResponse<Unit> {
        return callApi(ApiRoutes.messagesUnseen(mailboxUuid), POST, mapOf("uids" to messagesUids))
    }

    fun saveDraft(mailboxUuid: String, draft: Draft, okHttpClient: OkHttpClient): ApiResponse<SaveDraftResult> {

        val body = Json.encodeToString(draft.getJsonRequestBody()).removeEmptyRealmLists()

        fun postDraft(): ApiResponse<SaveDraftResult> = callApi(ApiRoutes.draft(mailboxUuid), POST, body, okHttpClient)

        fun putDraft(uuid: String): ApiResponse<SaveDraftResult> =
            callApi(ApiRoutes.draft(mailboxUuid, uuid), PUT, body, okHttpClient)

        return draft.remoteUuid?.let(::putDraft) ?: run(::postDraft)
    }

    fun sendDraft(mailboxUuid: String, draft: Draft, okHttpClient: OkHttpClient): ApiResponse<SendDraftResult> {

        val body = Json.encodeToString(draft.getJsonRequestBody()).removeEmptyRealmLists()

        fun postDraft(): ApiResponse<SendDraftResult> = callApi(ApiRoutes.draft(mailboxUuid), POST, body, okHttpClient)

        fun putDraft(uuid: String): ApiResponse<SendDraftResult> =
            callApi(ApiRoutes.draft(mailboxUuid, uuid), PUT, body, okHttpClient)

        return draft.remoteUuid?.let(::putDraft) ?: run(::postDraft)
    }

    fun attachmentsToForward(mailboxUuid: String, message: Message): ApiResponse<AttachmentsToForwardResult> {

        val body = mapOf(
            "to_forward_uids" to arrayOf(message.uid),
            "mode" to AttachmentDisposition.INLINE.name.lowercase(),
        )

        return callApi(ApiRoutes.attachmentToForward(mailboxUuid), POST, body)
    }

    fun deleteMessages(mailboxUuid: String, messageUids: List<String>): ApiResponse<Unit> {
        return callApi(ApiRoutes.deleteMessages(mailboxUuid), POST, mapOf("uids" to messageUids))
    }

    fun moveMessages(
        mailboxUuid: String,
        messagesUids: List<String>,
        destinationId: String,
        okHttpClient: OkHttpClient = HttpClient.okHttpClient,
    ): ApiResponse<MoveResult> {
        return callApi(
            url = ApiRoutes.moveMessages(mailboxUuid),
            method = POST,
            body = mapOf("uids" to messagesUids, "to" to destinationId),
            okHttpClient = okHttpClient,
        )
    }

    fun deleteDraft(mailboxUuid: String, remoteDraftUuid: String): ApiResponse<Unit> {
        return callApi(ApiRoutes.draft(mailboxUuid, remoteDraftUuid), DELETE)
    }

    fun getDraft(messageDraftResource: String): ApiResponse<Draft> = callApi(ApiRoutes.resource(messageDraftResource), GET)

    fun addToFavorites(mailboxUuid: String, messageUids: List<String>): ApiResponse<Unit> {
        return starMessages(mailboxUuid, messageUids, true)
    }

    fun removeFromFavorites(mailboxUuid: String, messageUids: List<String>): ApiResponse<Unit> {
        return starMessages(mailboxUuid, messageUids, false)
    }

    private fun starMessages(mailboxUuid: String, messageIds: List<String>, star: Boolean): ApiResponse<Unit> {
        return callApi(ApiRoutes.starMessages(mailboxUuid, star), POST, mapOf("uids" to messageIds))
    }

    fun getMessagesUids(
        mailboxUuid: String,
        folderId: String,
        okHttpClient: OkHttpClient?,
        info: PaginationInfo?,
    ): ApiResponse<NewMessagesResult> {
        return callApi(
            url = ApiRoutes.getMessagesUids(mailboxUuid, folderId, info),
            method = GET,
            okHttpClient = okHttpClient ?: HttpClient.okHttpClient,
        )
    }

    fun getMessagesUidsDelta(
        mailboxUuid: String,
        folderId: String,
        cursor: String,
        okHttpClient: OkHttpClient?,
    ): ApiResponse<ActivitiesResult> {
        return callApi(
            url = ApiRoutes.getMessagesUidsDelta(mailboxUuid, folderId, cursor),
            method = GET,
            okHttpClient = okHttpClient ?: HttpClient.okHttpClient,
        )
    }

    fun getMessagesByUids(
        mailboxUuid: String,
        folderId: String,
        uids: List<Int>,
        okHttpClient: OkHttpClient?,
    ): ApiResponse<GetMessagesByUidsResult> {
        return callApi(
            url = ApiRoutes.getMessagesByUids(mailboxUuid, folderId, uids),
            method = GET,
            okHttpClient = okHttpClient ?: HttpClient.okHttpClient,
        )
    }

    fun undoAction(undoResources: String): ApiResponse<Boolean> {
        return callApi(url = ApiRoutes.resource(undoResources), method = POST)
    }

    fun reportPhishing(mailboxUuid: String, folderId: String, shortUid: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.reportPhishing(mailboxUuid, folderId, shortUid), POST, mapOf("type" to "phishing"))
    }

    fun blockUser(mailboxUuid: String, folderId: String, shortUid: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.blockUser(mailboxUuid, folderId, shortUid), POST)
    }

    fun searchThreads(mailboxUuid: String, folderId: String, filters: String, resource: String?): ApiResponse<ThreadResult> {

        val url = if (resource.isNullOrBlank()) {
            ApiRoutes.search(mailboxUuid, folderId, filters)
        } else {
            "${ApiRoutes.resource(resource)}&$filters"
        }

        return callApi(url, GET)
    }

    fun flushFolder(mailboxUuid: String, folderId: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.flushFolder(mailboxUuid, folderId), POST)
    }

    fun startNewConversation(message: UserMessage): ApiResponse<AiResult> {
        val body = getAiBodyFromMessages(listOf(message))
        return callApi(ApiRoutes.ai(), POST, body, HttpClient.okHttpClientLongTimeout)
    }

    fun aiShortcutWithContext(contextId: String, shortcut: Shortcut): ApiResponse<AiResult> {
        return callApi(
            url = ApiRoutes.aiShortcutWithContext(contextId, action = shortcut.apiRoute!!),
            method = PATCH,
            okHttpClient = HttpClient.okHttpClientLongTimeout,
        )
    }

    fun aiShortcutNoContext(shortcut: Shortcut, history: List<AiMessage>): ApiResponse<AiResult> {
        val body = getAiBodyFromMessages(history)
        return callApi(ApiRoutes.aiShortcutNoContext(shortcut.apiRoute!!), POST, body, HttpClient.okHttpClientLongTimeout)
    }

    private fun getAiBodyFromMessages(messages: List<AiMessage>) = mapOf(
        "messages" to messages,
        "output" to "mail",
    )

    fun checkFeatureFlag(featureFlagType: FeatureFlagType): ApiResponse<Map<String, Boolean>> {
        return callApi(ApiRoutes.featureFlag(featureFlagType.apiName), GET)
    }

    fun downloadAttachment(resource: String): Response {
        val httpRequest = Request.Builder()
            .url(ApiRoutes.resource(resource))
            .headers(HttpUtils.getHeaders(contentType = null))
            .build()
        return HttpClient.okHttpClient.newBuilder().build().newCall(httpRequest).execute()
    }

    /**
     * RealmLists cannot be null, so they have to be empty when there is no data.
     * But the Infomaniak Mail API doesn't support empty lists, so we have to replace them with a `null` value.
     */
    private fun String.removeEmptyRealmLists() = replace("[]", "null")
}
