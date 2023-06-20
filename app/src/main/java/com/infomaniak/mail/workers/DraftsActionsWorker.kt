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

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.work.*
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.FORMAT_DATE_WITH_TIMEZONE
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.lib.core.utils.isNetworkException
import com.infomaniak.mail.MainApplication
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.NotificationUtils.showDraftActionsNotification
import com.infomaniak.mail.utils.NotificationUtils.showDraftErrorNotification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.realm.kotlin.MutableRealm
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.properties.Delegates

@HiltWorker
class DraftsActionsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val mainApplication: MainApplication,
    private val notificationManagerCompat: NotificationManagerCompat,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseCoroutineWorker(appContext, params) {

    private val mailboxContentRealm by lazy { RealmDatabase.newMailboxContentInstance }
    private val mailboxInfoRealm by lazy { RealmDatabase.newMailboxInfoInstance }

    private lateinit var okHttpClient: OkHttpClient
    private var mailboxId: Int = AppSettings.DEFAULT_ID
    private lateinit var mailbox: Mailbox
    private var userId: Int by Delegates.notNull()
    private var draftLocalUuid: String? = null
    private lateinit var userApiToken: String
    private var isSnackBarFeedbackNeeded: Boolean = false

    private val dateFormatWithTimezone by lazy { SimpleDateFormat(FORMAT_DATE_WITH_TIMEZONE, Locale.ROOT) }

    override suspend fun launchWork(): Result = withContext(ioDispatcher) {
        Log.d(TAG, "Work started")

        if (DraftController.getDraftsWithActionsCount(mailboxContentRealm) == 0L) return@withContext Result.success()
        if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return@withContext Result.failure()

        userId = inputData.getIntOrNull(USER_ID_KEY) ?: return@withContext Result.failure()
        mailboxId = inputData.getIntOrNull(MAILBOX_ID_KEY) ?: return@withContext Result.failure()
        draftLocalUuid = inputData.getString(DRAFT_LOCAL_UUID_KEY)

        userApiToken = AccountUtils.getUserById(userId)?.apiToken?.accessToken ?: return@withContext Result.failure()
        mailbox = MailboxController.getMailbox(userId, mailboxId, mailboxInfoRealm) ?: return@withContext Result.failure()
        okHttpClient = AccountUtils.getHttpClient(userId)

        isSnackBarFeedbackNeeded = !mainApplication.isAppInBackground

        notifyNewDraftDetected()

        return@withContext handleDraftsActions()
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

    private suspend fun notifyNewDraftDetected() {
        draftLocalUuid?.let { localUuid ->
            val draft = DraftController.getDraft(localUuid) ?: return@let
            if (draft.action == DraftAction.SEND && isSnackBarFeedbackNeeded) setProgress(workDataOf(PROGRESS_DRAFT_ACTION_KEY to DraftAction.SEND.name))
        }
    }

    private fun handleDraftsActions(): Result {

        val scheduledDates = mutableListOf<String>()
        var trackedDraftErrorMessageResId: Int? = null
        var remoteUuidOfTrackedDraft: String? = null
        var trackedDraftAction: DraftAction? = null
        var isTrackedDraftSuccess: Boolean? = null

        val haveAllDraftSucceeded = mailboxContentRealm.writeBlocking {

            val drafts = DraftController.getDraftsWithActions(realm = this).ifEmpty { return@writeBlocking false }

            Log.d(TAG, "handleDraftsActions: ${drafts.count()} drafts to handle")

            var hasNoRemoteException = true

            drafts.reversed().forEach { draft ->
                val isTargetDraft = draftLocalUuid == draft.localUuid
                if (isTargetDraft) trackedDraftAction = draft.action

                runCatching {
                    draft.uploadAttachments(realm = this)
                    executeDraftAction(
                        draft,
                        mailbox.uuid,
                        realm = this,
                        okHttpClient,
                    ).also { (scheduledDate, errorMessageResId, savedDraftUuid, isSuccess) ->
                        if (isSuccess) {
                            if (isTargetDraft) {
                                remoteUuidOfTrackedDraft = savedDraftUuid
                                isTrackedDraftSuccess = true
                            }
                            scheduledDates.add(scheduledDate!!)
                        } else if (isTargetDraft) {
                            trackedDraftErrorMessageResId = errorMessageResId!!
                            isTrackedDraftSuccess = false
                        }
                    }

                }.onFailure { exception ->
                    when (exception) {
                        is ApiController.NetworkException -> throw exception
                        is ApiErrorException -> {
                            exception.handleApiErrors(draft = draft, realm = this)?.also {
                                if (isTargetDraft) {
                                    trackedDraftErrorMessageResId = it
                                    isTrackedDraftSuccess = false
                                }
                            }
                        }
                    }
                    exception.printStackTrace()
                    Sentry.captureException(exception)
                    hasNoRemoteException = false
                }
            }

            return@writeBlocking hasNoRemoteException
        }

        val biggestScheduledDate = scheduledDates.mapNotNull { dateFormatWithTimezone.parse(it)?.time }.maxOrNull()

        SentryDebug.sendOrphanDrafts(mailboxContentRealm)

        if (mainApplication.isAppInBackground && isTrackedDraftSuccess == false) {
            if (applicationContext.hasPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))) {
                applicationContext.showDraftErrorNotification(trackedDraftErrorMessageResId!!, trackedDraftAction!!).apply {
                    @Suppress("MissingPermission")
                    notificationManagerCompat.notify(UUID.randomUUID().hashCode(), build())
                }
            } else {
                // TODO : ??
            }
        }

        return if (haveAllDraftSucceeded || isTrackedDraftSuccess == true) {
            val outputData = if (isSnackBarFeedbackNeeded) workDataOf(
                REMOTE_DRAFT_UUID_KEY to draftLocalUuid?.let { remoteUuidOfTrackedDraft },
                ASSOCIATED_MAILBOX_UUID_KEY to draftLocalUuid?.let { mailbox.uuid },
                RESULT_DRAFT_ACTION_KEY to draftLocalUuid?.let { trackedDraftAction?.name },
                BIGGEST_SCHEDULED_DATE_KEY to biggestScheduledDate,
                RESULT_USER_ID_KEY to userId,
            ) else Data.EMPTY
            Result.success(outputData)
        } else {
            val outputData = if (isSnackBarFeedbackNeeded) workDataOf(
                ERROR_MESSAGE_RESID_KEY to trackedDraftErrorMessageResId,
                BIGGEST_SCHEDULED_DATE_KEY to biggestScheduledDate,
                RESULT_USER_ID_KEY to userId,
            ) else Data.EMPTY
            Result.failure(outputData)
        }
    }

    private fun ApiErrorException.handleApiErrors(draft: Draft, realm: MutableRealm): Int? {
        realm.delete(draft)
        return ErrorCode.getTranslateResForDrafts(errorCode)
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
            updateLocalAttachment(localDraftUuid, apiResponse.data!!, realm)
            attachmentFile.delete()
            LocalStorageUtils.deleteAttachmentsUploadsDirIfEmpty(applicationContext, localDraftUuid, userId, mailbox.mailboxId)
        }
    }

    private fun Attachment.updateLocalAttachment(localDraftUuid: String, remoteAttachment: Attachment, realm: MutableRealm) {
        DraftController.updateDraft(localDraftUuid, realm) { draft ->
            realm.delete(draft.attachments.first { localAttachment -> localAttachment.uploadLocalUri == uploadLocalUri })
            draft.attachments.add(remoteAttachment)
        }
    }

    data class DraftActionResult(
        val scheduledDate: String?,
        val errorMessageResId: Int?,
        val savedDraftUuid: String?,
        val isSuccess: Boolean,
    )

    private fun executeDraftAction(
        draft: Draft,
        mailboxUuid: String,
        realm: MutableRealm,
        okHttpClient: OkHttpClient,
    ): DraftActionResult {

        var scheduledDate: String? = null
        var savedDraftUuid: String? = null

        // TODO: Remove this whole `draft.attachments.forEach { … }` when the Attachment issue is fixed.
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

                return DraftActionResult(
                    scheduledDate = null,
                    errorMessageResId = R.string.errorCorruptAttachment,
                    savedDraftUuid = null,
                    isSuccess = false,
                )
            }
        }

        when (draft.action) {
            DraftAction.SAVE -> with(ApiRepository.saveDraft(mailboxUuid, draft, okHttpClient)) {
                data?.let { data ->
                    draft.remoteUuid = data.draftRemoteUuid
                    draft.messageUid = data.messageUid
                    draft.action = null
                    scheduledDate = dateFormatWithTimezone.format(Date())
                    savedDraftUuid = data.draftRemoteUuid
                } ?: throwErrorAsException()
            }
            DraftAction.SEND -> with(ApiRepository.sendDraft(mailboxUuid, draft, okHttpClient)) {
                when {
                    isSuccess() -> {
                        scheduledDate = data?.scheduledDate
                        realm.delete(draft)
                    }
                    error?.exception is SerializationException -> {
                        realm.delete(draft)
                        Sentry.withScope { scope ->
                            scope.level = SentryLevel.ERROR
                            Sentry.captureMessage("Return JSON for SendDraft API call was modified")
                        }
                    }
                    else -> {
                        throwErrorAsException()
                    }
                }
            }
            else -> Unit
        }

        return DraftActionResult(
            scheduledDate = scheduledDate,
            errorMessageResId = null,
            savedDraftUuid = savedDraftUuid,
            isSuccess = true,
        )
    }

    @Singleton
    class Scheduler @Inject constructor(private val workManager: WorkManager) {

        fun scheduleWork(draftLocalUuid: String? = null) {

            if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return
            if (DraftController.getDraftsWithActionsCount() == 0L) return

            Log.d(TAG, "Work scheduled")

            val workData = workDataOf(
                USER_ID_KEY to AccountUtils.currentUserId,
                MAILBOX_ID_KEY to AccountUtils.currentMailboxId,
                DRAFT_LOCAL_UUID_KEY to draftLocalUuid,
            )
            val workRequest = OneTimeWorkRequestBuilder<DraftsActionsWorker>()
                .addTag(TAG)
                .setInputData(workData)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
        }

        fun getRunningWorkInfoLiveData(): LiveData<MutableList<WorkInfo>> {
            return WorkerUtils.getWorkInfoLiveData(TAG, workManager, listOf(WorkInfo.State.RUNNING))
        }

        fun getCompletedWorkInfoLiveData(): LiveData<MutableList<WorkInfo>> {
            return WorkerUtils.getWorkInfoLiveData(TAG, workManager, listOf(WorkInfo.State.SUCCEEDED))
        }

        fun getFailedWorkInfoLiveData(): LiveData<MutableList<WorkInfo>> {
            return WorkerUtils.getWorkInfoLiveData(TAG, workManager, listOf(WorkInfo.State.FAILED))
        }

        fun getCompletedAndFailedInfoLiveData(): LiveData<MutableList<WorkInfo>> {
            return WorkerUtils.getWorkInfoLiveData(TAG, workManager, listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED))
        }
    }

    companion object {
        private const val TAG = "DraftsActionsWorker"
        private const val USER_ID_KEY = "userId"
        private const val MAILBOX_ID_KEY = "mailboxIdKey"
        private const val DRAFT_LOCAL_UUID_KEY = "draftLocalUuidKey"
        const val PROGRESS_DRAFT_ACTION_KEY = "progressDraftActionKey"
        const val ERROR_MESSAGE_RESID_KEY = "errorMessageResIdKey"
        const val REMOTE_DRAFT_UUID_KEY = "remoteDraftUuidKey"
        const val ASSOCIATED_MAILBOX_UUID_KEY = "associatedMailboxUuidKey"
        const val RESULT_DRAFT_ACTION_KEY = "resultDraftActionKey"
        const val BIGGEST_SCHEDULED_DATE_KEY = "biggestScheduledDateKey"
        const val RESULT_USER_ID_KEY = "resultUserIdKey"
    }
}
