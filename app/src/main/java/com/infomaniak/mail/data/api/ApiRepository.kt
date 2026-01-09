/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import com.infomaniak.core.auth.api.ApiRepositoryCore
import com.infomaniak.core.auth.networking.HttpClient
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.ksuite.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.network.api.ApiController
import com.infomaniak.core.network.api.ApiController.ApiMethod.DELETE
import com.infomaniak.core.network.api.ApiController.ApiMethod.GET
import com.infomaniak.core.network.api.ApiController.ApiMethod.PATCH
import com.infomaniak.core.network.api.ApiController.ApiMethod.POST
import com.infomaniak.core.network.api.ApiController.ApiMethod.PUT
import com.infomaniak.core.network.api.ApiController.toApiError
import com.infomaniak.core.network.api.InternalTranslatedErrorCode
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.core.network.networking.HttpUtils
import com.infomaniak.core.network.networking.ManualAuthorizationRequired
import com.infomaniak.core.network.utils.await
import com.infomaniak.core.network.utils.bodyAsStringOrNull
import com.infomaniak.core.common.utils.FORMAT_FULL_DATE_WITH_HOUR
import com.infomaniak.core.common.utils.FORMAT_ISO_8601_WITH_TIMEZONE_SEPARATOR
import com.infomaniak.core.common.utils.format
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.AttachmentDisposition
import com.infomaniak.mail.data.models.AttachmentsToForwardResult
import com.infomaniak.mail.data.models.BackupResult
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.InfomaniakPassword
import com.infomaniak.mail.data.models.MoveResult
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.ShareThread
import com.infomaniak.mail.data.models.SwissTransferContainer
import com.infomaniak.mail.data.models.addressBook.AddressBooksResult
import com.infomaniak.mail.data.models.ai.AiMessage
import com.infomaniak.mail.data.models.ai.AiResult
import com.infomaniak.mail.data.models.ai.ContextMessage
import com.infomaniak.mail.data.models.ai.UserMessage
import com.infomaniak.mail.data.models.calendar.Attendee.AttendanceState
import com.infomaniak.mail.data.models.calendar.CalendarEvent
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.correspondent.Contact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.SaveDraftResult
import com.infomaniak.mail.data.models.draft.ScheduleDraftResult
import com.infomaniak.mail.data.models.draft.SendDraftResult
import com.infomaniak.mail.data.models.getMessages.ActivitiesResult
import com.infomaniak.mail.data.models.getMessages.GetMessagesByUidsResult
import com.infomaniak.mail.data.models.getMessages.MessageFlags
import com.infomaniak.mail.data.models.getMessages.NewMessagesResult
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.MailboxExternalMailInfo
import com.infomaniak.mail.data.models.mailbox.MailboxHostingStatus
import com.infomaniak.mail.data.models.mailbox.MailboxLinkedResult
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.data.models.mailbox.SendersRestrictions
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.data.models.signature.SignaturesResult
import com.infomaniak.mail.data.models.snooze.BatchSnoozeCancelResponse
import com.infomaniak.mail.data.models.snooze.BatchSnoozeUpdateResponse
import com.infomaniak.mail.data.models.thread.ThreadResult
import com.infomaniak.mail.ui.newMessage.AiViewModel.Shortcut
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.EML_CONTENT_TYPE
import io.realm.kotlin.ext.copyFromRealm
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.util.Date
import com.infomaniak.core.ksuite.myksuite.ui.network.ApiRoutes as MyKSuiteApiRoutes

object ApiRepository : ApiRepositoryCore() {

    suspend inline fun <reified T> callApi(
        url: String,
        method: ApiController.ApiMethod,
        body: Any? = null,
        okHttpClient: OkHttpClient = HttpClient.okHttpClientWithTokenInterceptor,
    ): T = ApiController.callApi(url, method, body, okHttpClient, useKotlinxSerialization = true)

    suspend fun ping(): ApiResponse<String> = callApi(ApiRoutes.ping(), GET)

    suspend fun getAddressBooks(): ApiResponse<AddressBooksResult> = callApi(ApiRoutes.addressBooks(), GET)

    suspend fun getContacts(): ApiResponse<List<Contact>> = callApi(ApiRoutes.contacts(), GET)

    suspend fun addContact(addressBookId: Int, recipient: Recipient): ApiResponse<Int> {
        val (firstName, lastName) = recipient.computeFirstAndLastName()

        val body = mapOf(
            "addressbookId" to addressBookId,
            "firstname" to firstName,
            "lastname" to lastName,
            "emails" to arrayOf(mapOf("value" to recipient.email, "type" to "HOME")),
        )

        return callApi(ApiRoutes.contact(), POST, body)
    }

    suspend fun getSignatures(mailboxHostingId: Int, mailboxName: String): ApiResponse<SignaturesResult> {
        return callApi(ApiRoutes.signatures(mailboxHostingId, mailboxName), GET)
    }

    suspend fun setDefaultSignature(mailboxHostingId: Int, mailboxName: String, signature: Signature?): ApiResponse<Boolean> {
        // If the signature is `null`, it means we want to have no default signature.
        // If we want to remove the default signature, we have to send `null` to the API call.
        val body = """{"default_signature_id":${signature?.id}}"""
        return callApi(ApiRoutes.signature(mailboxHostingId, mailboxName), POST, body)
    }

    suspend fun getBackups(mailboxHostingId: Int, mailboxName: String): ApiResponse<BackupResult> {
        return callApi(ApiRoutes.backups(mailboxHostingId, mailboxName), GET)
    }

    suspend fun restoreBackup(mailboxHostingId: Int, mailboxName: String, date: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.backups(mailboxHostingId, mailboxName), PUT, body = mapOf("date" to date))
    }

    suspend fun getMailboxes(okHttpClient: OkHttpClient? = null): ApiResponse<List<Mailbox>> {
        return callApi(ApiRoutes.mailboxes(), GET, okHttpClient = okHttpClient ?: HttpClient.okHttpClientWithTokenInterceptor)
    }

    suspend fun addNewMailbox(mailAddress: String, password: String): ApiResponse<MailboxLinkedResult> {
        return callApi(ApiRoutes.manageMailboxes(), POST, mapOf("mail" to mailAddress, "password" to password))
    }

    suspend fun isInfomaniakMailboxes(emails: Set<String>): ApiResponse<List<MailboxHostingStatus>> {
        return callApi(ApiRoutes.isInfomaniakMailboxes(emails), GET)
    }

    suspend fun getFolders(mailboxUuid: String): ApiResponse<List<Folder>> = callApi(ApiRoutes.folders(mailboxUuid), GET)

    suspend fun createFolder(mailboxUuid: String, name: String): ApiResponse<Folder> {
        return callApi(ApiRoutes.folders(mailboxUuid), POST, mapOf("name" to name))
    }

    suspend fun renameFolder(mailboxUuid: String, folderId: String, name: String): ApiResponse<Folder> {
        return callApi(ApiRoutes.renameFolder(mailboxUuid, folderId), POST, mapOf("name" to name))
    }

    suspend fun deleteFolder(mailboxUuid: String, folderId: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.folder(mailboxUuid, folderId), DELETE)
    }

    suspend fun getMessage(messageResource: String, okHttpClient: OkHttpClient? = null): ApiResponse<Message> {
        val encryptionWiths = "auto_uncrypt,recipient_provider_source"
        return callApi(
            url = ApiRoutes.resource("$messageResource?name=prefered_format&value=html&with=$encryptionWiths,emoji_reactions_per_message"),
            method = GET,
            okHttpClient = okHttpClient ?: HttpClient.okHttpClientWithTokenInterceptor,
        )
    }

    suspend fun getQuotas(mailboxHostingId: Int, mailboxName: String): ApiResponse<Quotas> {
        return callApi(ApiRoutes.quotas(mailboxHostingId, mailboxName), GET)
    }

    suspend fun getPermissions(mailboxLinkId: Int, mailboxHostingId: Int): ApiResponse<MailboxPermissions> {
        return callApi(ApiRoutes.permissions(mailboxLinkId, mailboxHostingId), GET)
    }

    //region Unsubscribe list diffusion
    suspend fun unsubscribe(messageResource: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.unsubscribe(messageResource), POST)
    }
    //endregion

    //region Spam
    suspend fun setSpamFilter(mailboxHostingId: Int, mailboxName: String, activateSpamFilter: Boolean): ApiResponse<Unit> {
        return callApi(ApiRoutes.mailboxInfo(mailboxHostingId, mailboxName), PATCH, mapOf("has_move_spam" to activateSpamFilter))
    }

    suspend fun getSendersRestrictions(mailboxHostingId: Int, mailboxName: String): ApiResponse<SendersRestrictions> {
        return callApi(ApiRoutes.getSendersRestrictions(mailboxHostingId, mailboxName), GET)
    }

    suspend fun updateBlockedSenders(
        mailboxHostingId: Int,
        mailboxName: String,
        updatedSendersRestrictions: SendersRestrictions,
    ): ApiResponse<Boolean> {
        val body = mapOf(
            "authorized_senders" to updatedSendersRestrictions.authorizedSenders.map { it.email },
            "blocked_senders" to updatedSendersRestrictions.blockedSenders.map { it.email },
        )
        return callApi(ApiRoutes.getSendersRestrictions(mailboxHostingId, mailboxName), PATCH, body)
    }
    //endregion

    suspend fun getExternalMailInfo(mailboxHostingId: Int, mailboxName: String): ApiResponse<MailboxExternalMailInfo> {
        return callApi(ApiRoutes.externalMailInfo(mailboxHostingId, mailboxName), GET)
    }

    suspend fun markMessagesAsSeen(mailboxUuid: String, messagesUids: List<String>): List<ApiResponse<Unit>> {
        return batchOver(messagesUids) {
            callApi(ApiRoutes.messagesSeen(mailboxUuid), POST, mapOf("uids" to it))
        }
    }

    suspend fun markMessagesAsUnseen(mailboxUuid: String, messagesUids: List<String>): List<ApiResponse<Unit>> {
        return batchOver(messagesUids) {
            callApi(ApiRoutes.messagesUnseen(mailboxUuid), POST, mapOf("uids" to it))
        }
    }

    suspend fun saveDraft(mailboxUuid: String, draft: Draft, okHttpClient: OkHttpClient): ApiResponse<SaveDraftResult> {
        return uploadDraft(mailboxUuid, draft, okHttpClient)
    }

    suspend fun sendDraft(mailboxUuid: String, draft: Draft, okHttpClient: OkHttpClient): ApiResponse<SendDraftResult> {
        return uploadDraft(mailboxUuid, draft, okHttpClient)
    }

    suspend fun scheduleDraft(mailboxUuid: String, draft: Draft, okHttpClient: OkHttpClient): ApiResponse<ScheduleDraftResult> {
        return uploadDraft(mailboxUuid, draft, okHttpClient)
    }

    private suspend inline fun <reified T> uploadDraft(
        mailboxUuid: String,
        draft: Draft,
        okHttpClient: OkHttpClient
    ): ApiResponse<T> {
        val body = getDraftBody(draft)
        return draft.remoteUuid?.let { putDraft(mailboxUuid, body, okHttpClient, it) }
            ?: run { postDraft(mailboxUuid, body, okHttpClient) }
    }

    private suspend inline fun <reified T> putDraft(
        mailboxUuid: String,
        body: String,
        okHttpClient: OkHttpClient,
        uuid: String,
    ): ApiResponse<T> = callApi(ApiRoutes.draft(mailboxUuid, uuid), PUT, body, okHttpClient)

    private suspend inline fun <reified T> postDraft(
        mailboxUuid: String,
        body: String,
        okHttpClient: OkHttpClient,
    ): ApiResponse<T> = callApi(ApiRoutes.draft(mailboxUuid), POST, body, okHttpClient)

    private fun getDraftBody(draft: Draft): String {
        val updatedDraft = if (draft.identityId == Draft.NO_IDENTITY.toString()) {
            // When we select no signature, we create a dummy signature with -1 (NO_IDENTITY) as identity ID.
            // But we can't send that to the API, instead we need to put the `null` value.
            draft.copyFromRealm().apply { identityId = null }
        } else {
            draft
        }
        return Json.encodeToString(updatedDraft.getJsonRequestBody()).removeEmptyRealmLists()
    }

    suspend fun attachmentsToForward(mailboxUuid: String, message: Message): ApiResponse<AttachmentsToForwardResult> {

        val body = mapOf(
            "to_forward_uids" to arrayOf(message.uid),
            "mode" to AttachmentDisposition.INLINE.name.lowercase(),
        )

        return callApi(ApiRoutes.attachmentToForward(mailboxUuid), POST, body)
    }

    suspend fun deleteMessages(
        mailboxUuid: String,
        messagesUids: List<String>,
        alsoMoveReactionMessages: Boolean,
    ): List<ApiResponse<Unit>> = batchOver(messagesUids) {
        callApi(ApiRoutes.deleteMessages(mailboxUuid).withMoveReactions(alsoMoveReactionMessages), POST, mapOf("uids" to it))
    }

    suspend fun moveMessages(
        mailboxUuid: String,
        messagesUids: List<String>,
        destinationId: String,
        alsoMoveReactionMessages: Boolean,
        okHttpClient: OkHttpClient = HttpClient.okHttpClientWithTokenInterceptor,
    ): List<ApiResponse<MoveResult>> = batchOver(messagesUids) {
        callApi(
            url = ApiRoutes.moveMessages(mailboxUuid).withMoveReactions(alsoMoveReactionMessages),
            method = POST,
            body = mapOf("uids" to it, "to" to destinationId),
            okHttpClient = okHttpClient,
        )
    }

    private fun String.withMoveReactions(alsoMoveReactions: Boolean): String {
        return this + if (alsoMoveReactions) "?move_reactions=1" else ""
    }

    suspend fun deleteDraft(mailboxUuid: String, remoteDraftUuid: String): ApiResponse<Unit> {
        return callApi(ApiRoutes.draft(mailboxUuid, remoteDraftUuid), DELETE)
    }

    suspend fun unscheduleDraft(unscheduleDraftUrl: String): ApiResponse<Unit> {
        return callApi(ApiRoutes.resource(unscheduleDraftUrl), DELETE)
    }

    suspend fun rescheduleDraft(draftResource: String, scheduleDate: Date): ApiResponse<Unit> {
        return callApi(ApiRoutes.rescheduleDraft(draftResource, scheduleDate), PUT)
    }

    suspend fun getDraft(messageDraftResource: String): ApiResponse<Draft> =
        callApi(ApiRoutes.resource(messageDraftResource), GET)

    suspend fun addToFavorites(mailboxUuid: String, messagesUids: List<String>): List<ApiResponse<Unit>> {
        return batchOver(messagesUids) {
            callApi(ApiRoutes.starMessages(mailboxUuid), POST, mapOf("uids" to it))
        }
    }

    suspend fun removeFromFavorites(mailboxUuid: String, messagesUids: List<String>): List<ApiResponse<Unit>> {
        return batchOver(messagesUids) {
            callApi(ApiRoutes.unstarMessages(mailboxUuid), POST, mapOf("uids" to it))
        }
    }

    suspend fun getDateOrderedMessagesUids(
        mailboxUuid: String,
        folderId: String,
        okHttpClient: OkHttpClient?,
    ): ApiResponse<NewMessagesResult> {
        return callApi(
            url = ApiRoutes.getDateOrderedMessagesUids(mailboxUuid, folderId),
            method = GET,
            okHttpClient = okHttpClient ?: HttpClient.okHttpClientWithTokenInterceptor,
        )
    }

    suspend inline fun <reified T : MessageFlags> getMessagesUidsDelta(
        mailboxUuid: String,
        folderId: String,
        cursor: String,
        uids: String?,
        okHttpClient: OkHttpClient?,
    ): ApiResponse<ActivitiesResult<T>> {
        return callApi(
            url = ApiRoutes.getMessagesUidsDelta(mailboxUuid, folderId, cursor),
            method = if (uids == null) GET else POST,
            body = if (uids == null) null else mapOf("uids" to uids),
            okHttpClient = okHttpClient ?: HttpClient.okHttpClientWithTokenInterceptor,
        )
    }

    suspend fun getMessagesByUids(
        mailboxUuid: String,
        folderId: String,
        uids: List<Int>,
        okHttpClient: OkHttpClient?,
    ): ApiResponse<GetMessagesByUidsResult> {
        return callApi(
            url = ApiRoutes.getMessagesByUids(mailboxUuid, folderId, uids),
            method = GET,
            okHttpClient = okHttpClient ?: HttpClient.okHttpClientWithTokenInterceptor,
        )
    }

    suspend fun undoAction(undoResources: String): ApiResponse<Boolean> {
        return callApi(url = ApiRoutes.resource(undoResources), method = POST)
    }

    suspend fun reportPhishing(mailboxUuid: String, uids: List<String>): ApiResponse<Boolean> {
        return callApi(ApiRoutes.reportPhishing(mailboxUuid), POST, mapOf("type" to "phishing", "uids" to uids))
    }

    suspend fun blockUser(mailboxUuid: String, folderId: String, shortUid: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.blockUser(mailboxUuid, folderId, shortUid), POST)
    }

    suspend fun snoozeMessages(mailboxUuid: String, messageUids: List<String>, date: Date): List<ApiResponse<Unit>> {
        return batchOver(messageUids, limit = Utils.MAX_UUIDS_PER_CALL_SNOOZE_POST) {
            callApi(
                url = ApiRoutes.snooze(mailboxUuid),
                method = POST,
                body = mapOf("uids" to it, "end_date" to date.format(FORMAT_ISO_8601_WITH_TIMEZONE_SEPARATOR)),
            )
        }
    }

    suspend fun rescheduleSnoozedThread(mailboxUuid: String, snoozeUuid: String, date: Date): ApiResponse<Boolean> {
        return callApi(
            url = ApiRoutes.snoozeAction(mailboxUuid, snoozeUuid),
            method = PUT,
            body = mapOf("end_date" to date.format(FORMAT_ISO_8601_WITH_TIMEZONE_SEPARATOR)),
        )
    }

    /**
     * Do not call directly, use the [SharedUtils.rescheduleSnoozedThreads] instead to correctly support api error messages.
     */
    suspend fun rescheduleSnoozedThreads(
        mailboxUuid: String,
        snoozeUuids: List<String>,
        newDate: Date,
    ): List<ApiResponse<BatchSnoozeUpdateResponse>> {
        return batchOver(snoozeUuids, limit = Utils.MAX_UUIDS_PER_CALL_SNOOZE) {
            callApi(
                url = ApiRoutes.snooze(mailboxUuid),
                method = PUT,
                body = mapOf("uuids" to it, "end_date" to newDate.format(FORMAT_ISO_8601_WITH_TIMEZONE_SEPARATOR)),
            )
        }
    }

    suspend fun unsnoozeThread(mailboxUuid: String, snoozeUuid: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.snoozeAction(mailboxUuid, snoozeUuid), DELETE)
    }

    /**
     * Do not call directly, use the [SharedUtils.unsnoozeThreads] instead to correctly support api error messages.
     */
    suspend fun unsnoozeThreads(mailboxUuid: String, snoozeUuids: List<String>): List<ApiResponse<BatchSnoozeCancelResponse>> {
        return batchOver(snoozeUuids, limit = Utils.MAX_UUIDS_PER_CALL_SNOOZE) {
            callApi(ApiRoutes.snooze(mailboxUuid), DELETE, mapOf("uuids" to it))
        }
    }

    suspend fun searchThreads(
        mailboxUuid: String,
        folderId: String,
        filters: String,
        hasDisplayModeThread: Boolean,
        resource: String?,
    ): ApiResponse<ThreadResult> {
        return callApi(ApiRoutes.search(mailboxUuid, folderId, hasDisplayModeThread, filters, resource), GET)
    }

    suspend fun flushFolder(mailboxUuid: String, folderId: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.flushFolder(mailboxUuid, folderId), POST)
    }

    suspend fun startNewConversation(
        contextMessage: ContextMessage?,
        message: UserMessage,
        currentMailboxUuid: String,
    ): ApiResponse<AiResult> {
        val messages = if (contextMessage == null) listOf(message) else listOf(contextMessage, message)

        val body = getAiBodyFromMessages(messages)
        return callApi(
            ApiRoutes.aiConversation(currentMailboxUuid),
            POST,
            body,
            HttpClient.okHttpClientLongTimeoutWithTokenInterceptor
        )
    }

    suspend fun aiShortcutWithContext(
        contextId: String,
        shortcut: Shortcut,
        currentMailboxUuid: String,
    ): ApiResponse<AiResult> {
        return callApi(
            url = ApiRoutes.aiShortcutWithContext(contextId, action = shortcut.apiRoute!!, currentMailboxUuid),
            method = PATCH,
            okHttpClient = HttpClient.okHttpClientLongTimeoutWithTokenInterceptor,
        )
    }

    suspend fun aiShortcutNoContext(
        shortcut: Shortcut,
        history: List<AiMessage>,
        currentMailboxUuid: String,
    ): ApiResponse<AiResult> {
        val body = getAiBodyFromMessages(history)
        return callApi(
            url = ApiRoutes.aiShortcutNoContext(shortcut.apiRoute!!, currentMailboxUuid),
            method = POST,
            body = body,
            okHttpClient = HttpClient.okHttpClientLongTimeoutWithTokenInterceptor
        )
    }

    private fun getAiBodyFromMessages(messages: List<AiMessage>) = mapOf("messages" to messages, "output" to "mail")

    suspend fun getFeatureFlags(currentMailboxUuid: String): ApiResponse<List<String>> {
        return callApi(ApiRoutes.featureFlags(currentMailboxUuid), GET)
    }

    suspend fun getCredentialsPassword(): ApiResponse<InfomaniakPassword> = runCatching {

        val formBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", "Infomaniak Sync - ${Date().format(FORMAT_FULL_DATE_WITH_HOUR)}")

        @OptIn(ManualAuthorizationRequired::class)
        val request = Request.Builder()
            .url(ApiRoutes.getCredentialsPassword())
            .headers(HttpUtils.getHeaders(contentType = null))
            .post(formBuilder.build())
            .build()

        val response = HttpClient.okHttpClientWithTokenInterceptor.newCall(request).await()

        return ApiController.json.decodeFromString(response.bodyAsStringOrNull() ?: "")

    }.cancellable().getOrElse {
        return ApiResponse(result = ApiResponseStatus.ERROR, error = InternalTranslatedErrorCode.UnknownError.toApiError())
    }

    suspend fun getSwissTransferContainer(containerUuid: String): ApiResponse<SwissTransferContainer> {
        return callApi(url = ApiRoutes.swissTransferContainer(containerUuid), method = GET)
    }

    suspend fun downloadAttachment(resource: String): Response {
        @OptIn(ManualAuthorizationRequired::class)
        val request = Request.Builder()
            .url(ApiRoutes.resource(resource))
            .headers(HttpUtils.getHeaders(contentType = null))
            .build()
        return HttpClient.okHttpClientWithTokenInterceptor.newBuilder().build().newCall(request).await()
    }

    suspend fun getAttachmentCalendarEvent(resource: String): ApiResponse<CalendarEventResponse> {
        return callApi(url = ApiRoutes.calendarEvent(resource), method = GET)
    }

    suspend fun replyToCalendarEvent(
        attendanceState: AttendanceState,
        useInfomaniakCalendarRoute: Boolean,
        calendarEventId: Int,
        attachmentResource: String,
    ): ApiResponse<*> {
        val body = mapOf("reply" to attendanceState.apiValue)

        return if (useInfomaniakCalendarRoute) {
            callApi<ApiResponse<Boolean>>(ApiRoutes.infomaniakCalendarEventReply(calendarEventId), POST, body)
        } else {
            callApi<ApiResponse<Map<String, CalendarEvent>>>(ApiRoutes.icsCalendarEventReply(attachmentResource), POST, body)
        }
    }

    suspend fun getShareLink(mailboxUuid: String, folderId: String, mailId: Int): ApiResponse<ShareThread> {
        return callApi(url = ApiRoutes.shareLink(mailboxUuid, folderId, mailId), method = POST)
    }

    suspend fun getDownloadedMessage(mailboxUuid: String, folderId: String, shortUid: Int): Response {
        @OptIn(ManualAuthorizationRequired::class)
        val request = Request.Builder().url(ApiRoutes.downloadMessage(mailboxUuid, folderId, shortUid))
            .headers(HttpUtils.getHeaders(EML_CONTENT_TYPE))
            .get()
            .build()

        return HttpClient.okHttpClientWithTokenInterceptor.newCall(request).await()
    }

    suspend fun getMyKSuiteData(okHttpClient: OkHttpClient): ApiResponse<MyKSuiteData> {
        return callApi(url = MyKSuiteApiRoutes.myKSuiteData(), method = GET, okHttpClient = okHttpClient)
    }

    suspend fun createAttachments(
        attachmentFile: File,
        attachment: Attachment,
        mailbox: Mailbox,
        userApiToken: String,
    ): ApiResponse<Attachment>? {
        @OptIn(ManualAuthorizationRequired::class)
        val headers = HttpUtils.getHeaders(contentType = null).newBuilder()
            .set("Authorization", "Bearer $userApiToken")
            .addUnsafeNonAscii("x-ws-attachment-filename", attachment.name)
            .add("x-ws-attachment-mime-type", attachment.mimeType)
            .add("x-ws-attachment-disposition", "attachment")
            .build()
        val request = Request.Builder().url(ApiRoutes.createAttachment(mailbox.uuid))
            .headers(headers)
            .post(attachmentFile.asRequestBody(attachment.mimeType.toMediaType()))
            .build()

        val response = AccountUtils.getHttpClient(mailbox.userId).newCall(request).await()

        return response.bodyAsStringOrNull()?.let { ApiController.json.decodeFromString<ApiResponse<Attachment>>(it) }
    }

    /**
     * Create batches of the given values to perform the given request
     * @param values Data to batch
     * @param limit Chunk size
     * @param perform Request to perform
     * @return Array of the perform return type
     */
    private suspend fun <T, R> batchOver(
        values: List<T>,
        limit: Int = Utils.MAX_UIDS_PER_CALL,
        perform: suspend (List<T>) -> ApiResponse<R>,
    ): List<ApiResponse<R>> {
        return values.chunked(limit).map { perform(it) }
    }

    /**
     * RealmLists cannot be null, so they have to be empty when there is no data.
     * But the Infomaniak Mail API doesn't support empty lists, so we have to replace them with a `null` value.
     */
    private fun String.removeEmptyRealmLists() = replace("[]", "null")
}
