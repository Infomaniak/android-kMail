/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.workers

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.work.*
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.FORMAT_DATE_WITH_TIMEZONE
import com.infomaniak.lib.core.utils.isNetworkException
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ErrorCode.DRAFT_ALREADY_SCHEDULED_OR_SENT
import com.infomaniak.mail.utils.ErrorCode.DRAFT_DOES_NOT_EXIST
import com.infomaniak.mail.utils.ErrorCode.DRAFT_HAS_TOO_MANY_RECIPIENTS
import com.infomaniak.mail.utils.ErrorCode.DRAFT_NEED_AT_LEAST_ONE_RECIPIENT
import com.infomaniak.mail.utils.ErrorCode.IDENTITY_NOT_FOUND
import com.infomaniak.mail.utils.ErrorCode.MAILBOX_LOCKED
import com.infomaniak.mail.utils.ErrorCode.SEND_LIMIT_EXCEEDED
import com.infomaniak.mail.utils.ErrorCode.SEND_RECIPIENTS_REFUSED
import com.infomaniak.mail.utils.NotificationUtils.showDraftActionsNotification
import io.realm.kotlin.MutableRealm
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

class DraftsActionsWorker(appContext: Context, params: WorkerParameters) : BaseCoroutineWorker(appContext, params) {

    private val mailboxContentRealm by lazy { RealmDatabase.newMailboxContentInstance }
    private val mailboxInfoRealm by lazy { RealmDatabase.newMailboxInfoInstance }

    private lateinit var okHttpClient: OkHttpClient
    private var mailboxId: Int = AppSettings.DEFAULT_ID
    private lateinit var mailbox: Mailbox
    private var userId: Int by Delegates.notNull()
    private lateinit var userApiToken: String

    private val dateFormatWithTimezone by lazy { SimpleDateFormat(FORMAT_DATE_WITH_TIMEZONE, Locale.ROOT) }

    override suspend fun launchWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Work started")

        if (DraftController.getDraftsWithActionsCount(mailboxContentRealm) == 0L) return@withContext Result.success()
        if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return@withContext Result.failure()

        userId = inputData.getIntOrNull(USER_ID_KEY) ?: return@withContext Result.failure()
        mailboxId = inputData.getIntOrNull(MAILBOX_ID_KEY) ?: return@withContext Result.failure()

        userApiToken = AccountUtils.getUserById(userId)?.apiToken?.accessToken ?: return@withContext Result.failure()
        mailbox = MailboxController.getMailbox(userId, mailboxId, mailboxInfoRealm) ?: return@withContext Result.failure()
        okHttpClient = AccountUtils.getHttpClient(userId)

        handleDraftsActions()
    }

    override fun onFinish() {
        mailboxContentRealm.close()
        mailboxInfoRealm.close()
        Log.d(TAG, "Work finished")
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return applicationContext.showDraftActionsNotification().run {
            ForegroundInfo(NotificationUtils.DRAFT_ACTIONS_ID, build())
        }
    }

    private suspend fun handleDraftsActions(): Result {

        val scheduledDates = mutableListOf<String>()

        val isFailure = mailboxContentRealm.writeBlocking {

            val drafts = DraftController.getDraftsWithActions(realm = this).ifEmpty { return@writeBlocking false }

            Log.d(TAG, "handleDraftsActions: ${drafts.count()} drafts to handle")

            var hasRemoteException = false

            drafts.reversed().forEach { draft ->
                runCatching {
                    draft.uploadAttachments(realm = this)
                    executeDraftAction(draft, mailbox.uuid, realm = this, okHttpClient)?.also(scheduledDates::add)
                }.onFailure { exception ->
                    when (exception) {
                        is ApiController.NetworkException -> throw exception
                        is ApiErrorException -> exception.handleApiErrors(draft = draft, realm = this)
                    }
                    exception.printStackTrace()
                    Sentry.captureException(exception)
                    hasRemoteException = true
                }
            }

            return@writeBlocking hasRemoteException
        }

        if (scheduledDates.isNotEmpty()) updateFolderAfterDelay(scheduledDates)

        SentryDebug.sendOrphanDrafts(mailboxContentRealm)

        return if (isFailure) Result.failure() else Result.success()
    }

    private fun ApiErrorException.handleApiErrors(draft: Draft, realm: MutableRealm) {

        when (errorCode) {
            DRAFT_DOES_NOT_EXIST, DRAFT_HAS_TOO_MANY_RECIPIENTS -> realm.delete(draft)
        }

        when (errorCode) {
            MAILBOX_LOCKED,
            DRAFT_HAS_TOO_MANY_RECIPIENTS,
            DRAFT_NEED_AT_LEAST_ONE_RECIPIENT,
            DRAFT_ALREADY_SCHEDULED_OR_SENT,
            IDENTITY_NOT_FOUND,
            SEND_RECIPIENTS_REFUSED,
            SEND_LIMIT_EXCEEDED -> {
                applicationContext.showToast(ErrorCode.getTranslateRes(errorCode!!))
            }
        }
    }

    private suspend fun updateFolderAfterDelay(scheduledDates: MutableList<String>) {

        val folder = FolderController.getFolder(FolderRole.DRAFT, realm = mailboxContentRealm)

        if (folder?.cursor != null) {

            val timeNow = Date().time
            val times = scheduledDates.mapNotNull { dateFormatWithTimezone.parse(it)?.time }
            var delay = REFRESH_DELAY
            if (times.isNotEmpty()) delay += max(times.maxOf { it } - timeNow, 0L)
            delay(min(delay, MAX_REFRESH_DELAY))

            MessageController.fetchCurrentFolderMessages(mailbox, folder, okHttpClient, mailboxContentRealm)
        }
    }

    private fun Draft.uploadAttachments(realm: MutableRealm): Result {
        getNotUploadedAttachments(draft = this).forEach { attachment ->
            runCatching {
                attachment.startUpload(localUuid, realm)
            }.onFailure { exception ->
                if ((exception as Exception).isNetworkException()) throw ApiController.NetworkException()
                throw exception
            }
        }
        return Result.success()
    }

    private fun getNotUploadedAttachments(draft: Draft): List<Attachment> {
        return draft.attachments.filter { attachment -> attachment.uploadLocalUri != null }
    }

    private fun Attachment.startUpload(localDraftUuid: String, realm: MutableRealm) {
        val attachmentFile = getUploadLocalFile(applicationContext, localDraftUuid).also { if (!it.exists()) return }
        val headers = HttpUtils.getHeaders(contentType = null).newBuilder()
            .set("Authorization", "Bearer $userApiToken")
            .addUnsafeNonAscii("x-ws-attachment-filename", name)
            .add("x-ws-attachment-mime-type", mimeType)
            .add("x-ws-attachment-disposition", "attachment")
            .build()
        val request = Request.Builder().url(ApiRoutes.createAttachment(mailbox.uuid))
            .headers(headers)
            .post(attachmentFile.asRequestBody(mimeType.toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()

        val apiResponse = ApiController.json.decodeFromString<ApiResponse<Attachment>>(response.body?.string() ?: "")
        if (apiResponse.isSuccess() && apiResponse.data != null) {
            attachmentFile.delete()
            LocalStorageUtils.deleteAttachmentsUploadsDirIfEmpty(applicationContext, localDraftUuid, userId, mailbox.mailboxId)
            updateLocalAttachment(localDraftUuid, apiResponse.data!!, realm)
        }
    }

    private fun Attachment.updateLocalAttachment(localDraftUuid: String, remoteAttachment: Attachment, realm: MutableRealm) {
        DraftController.updateDraft(localDraftUuid, realm) { draft ->
            realm.delete(draft.attachments.first { localAttachment -> localAttachment.uploadLocalUri == uploadLocalUri })
            draft.attachments.add(remoteAttachment)
        }
    }

    private fun executeDraftAction(draft: Draft, mailboxUuid: String, realm: MutableRealm, okHttpClient: OkHttpClient): String? {

        var scheduledDate: String? = null

        // TODO: This is a temporary fix to avoid crashes, it should be removed when the issue is fixed.
        draft.attachments.forEach { attachment ->
            if (attachment.uuid.isBlank()) {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    scope.setExtra("attachmentUuid", attachment.uuid)
                    scope.setExtra("attachmentsCount", "${draft.attachments.count()}")
                    scope.setExtra("attachmentsUuids", "${draft.attachments.map { it.uuid }}")
                    scope.setExtra("draftUuid", "${draft.remoteUuid}")
                    scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                    Sentry.captureMessage("We tried to [${draft.action?.name}] a Draft, but an Attachment didn't have its `uuid`.")
                }
                return scheduledDate
            }
        }

        when (draft.action) {
            DraftAction.SAVE -> with(ApiRepository.saveDraft(mailboxUuid, draft, okHttpClient)) {
                if (data == null) {
                    throwErrorAsException()
                } else {
                    draft.remoteUuid = data?.draftRemoteUuid
                    draft.messageUid = data?.messageUid
                    draft.action = null
                    scheduledDate = dateFormatWithTimezone.format(Date())
                }
            }
            DraftAction.SEND -> with(ApiRepository.sendDraft(mailboxUuid, draft, okHttpClient)) {
                if (isSuccess()) {
                    scheduledDate = data?.scheduledDate
                    realm.delete(draft)
                } else {
                    throwErrorAsException()
                }
            }
            else -> Unit
        }

        return scheduledDate
    }

    companion object {
        private const val TAG = "DraftsActionsWorker"
        private const val USER_ID_KEY = "userId"
        private const val MAILBOX_ID_KEY = "mailboxIdKey"
        // We add this delay because for now, it doesn't always work if we just use the `etop`.
        private const val REFRESH_DELAY = 2_000L
        private const val MAX_REFRESH_DELAY = 6_000L

        fun scheduleWork(context: Context) {

            if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return
            if (DraftController.getDraftsWithActionsCount() == 0L) return

            Log.d(TAG, "Work scheduled")

            val workData = workDataOf(USER_ID_KEY to AccountUtils.currentUserId, MAILBOX_ID_KEY to AccountUtils.currentMailboxId)
            val workRequest = OneTimeWorkRequestBuilder<DraftsActionsWorker>()
                .addTag(TAG)
                .setInputData(workData)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
        }

        fun getCompletedWorkInfosLiveData(context: Context): LiveData<MutableList<WorkInfo>> {
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG)).addStates(listOf(WorkInfo.State.SUCCEEDED)).build()
            return WorkManager.getInstance(context).getWorkInfosLiveData(workQuery)
        }
    }
}
