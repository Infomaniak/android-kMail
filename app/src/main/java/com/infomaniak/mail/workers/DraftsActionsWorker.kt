/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.work.*
import androidx.work.WorkInfo.State
import com.infomaniak.lib.core.api.ApiController.NetworkException
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.FORMAT_DATE_WITH_TIMEZONE
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.MainApplication
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.AttachmentUploadStatus
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.LocalStorageUtils.deleteDraftUploadDir
import com.infomaniak.mail.utils.SharedUtils.Companion.updateSignatures
import com.infomaniak.mail.utils.WorkerUtils.UploadMissingLocalFileException
import com.infomaniak.mail.utils.extensions.throwErrorAsException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.properties.Delegates

@HiltWorker
class DraftsActionsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val draftController: DraftController,
    private val mailboxController: MailboxController,
    private val mainApplication: MainApplication,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val notificationUtils: NotificationUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseCoroutineWorker(appContext, params) {

    private val mailboxContentRealm by lazy { RealmDatabase.newMailboxContentInstance }

    private lateinit var okHttpClient: OkHttpClient
    private var mailboxId: Int = AppSettings.DEFAULT_ID
    private lateinit var mailbox: Mailbox
    private var userId: Int by Delegates.notNull()
    private var draftLocalUuid: String? = null
    private lateinit var userApiToken: String
    private var isSnackbarFeedbackNeeded: Boolean = false

    private val dateFormatWithTimezone by lazy { SimpleDateFormat(FORMAT_DATE_WITH_TIMEZONE, Locale.ROOT) }

    override suspend fun launchWork(): Result = withContext(ioDispatcher) {
        SentryLog.d(TAG, "Work started")

        if (DraftController.getDraftsWithActionsCount(mailboxContentRealm) == 0L) return@withContext Result.success()
        if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return@withContext Result.failure()

        userId = inputData.getIntOrNull(USER_ID_KEY) ?: return@withContext Result.failure()
        mailboxId = inputData.getIntOrNull(MAILBOX_ID_KEY) ?: return@withContext Result.failure()
        draftLocalUuid = inputData.getString(DRAFT_LOCAL_UUID_KEY)

        userApiToken = AccountUtils.getUserById(userId)?.apiToken?.accessToken ?: return@withContext Result.failure()
        mailbox = mailboxController.getMailbox(userId, mailboxId) ?: return@withContext Result.failure()
        okHttpClient = AccountUtils.getHttpClient(userId)

        isSnackbarFeedbackNeeded = !mainApplication.isAppInBackground

        return@withContext handleDraftsActions()
    }

    override fun onFinish() {
        mailboxContentRealm.close()
        SentryLog.d(TAG, "Work finished")
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val builder = notificationUtils.buildDraftActionsNotification()
        return ForegroundInfo(NotificationUtils.DRAFT_ACTIONS_ID, builder.build())
    }

    private suspend fun handleDraftsActions(): Result {

        SentryDebug.addDraftsBreadcrumbs(
            drafts = draftController.getAllDrafts(mailboxContentRealm),
            step = "before handleDraftsActions",
        )

        // List containing the callback function to update/delete drafts in Realm
        // We keep these Realm changes in a List to execute them all in a unique Realm transaction at the end of the worker
        val realmActionsOnDraft = mutableListOf<(MutableRealm) -> Unit>()
        val scheduledMessagesEtops = mutableListOf<String>()
        var trackedDraftErrorMessageResId: Int? = null
        var remoteUuidOfTrackedDraft: String? = null
        var trackedDraftAction: DraftAction? = null
        var trackedScheduledDraftDate: String? = null
        var trackedUnscheduledDraftUrl: String? = null
        var isTrackedDraftSuccess: Boolean? = null

        val drafts = draftController.getDraftsWithActions(mailboxContentRealm)
        SentryLog.d(TAG, "handleDraftsActions: ${drafts.count()} drafts to handle")
        if (drafts.isEmpty()) return Result.failure()

        var haveAllDraftsSucceeded = true

        drafts.asReversed().forEach { draft ->

            val isTargetDraft = draft.localUuid == draftLocalUuid
            if (isTargetDraft) {
                trackedDraftAction = draft.action
                trackedScheduledDraftDate = draft.scheduleDate
            }

            runCatching {
                val updatedDraft = uploadAttachmentsWithMutex(draft, mailbox, draftController, mailboxContentRealm)
                with(executeDraftAction(updatedDraft, mailbox.uuid)) {
                    if (isSuccess) {
                        if (isTargetDraft) {
                            remoteUuidOfTrackedDraft = savedDraftUuid
                            trackedUnscheduledDraftUrl = unscheduleDraftUrl
                            isTrackedDraftSuccess = true
                        }
                        scheduledMessageEtop?.let(scheduledMessagesEtops::add)
                        realmActionOnDraft?.let(realmActionsOnDraft::add)

                    } else if (isTargetDraft) {
                        trackedDraftErrorMessageResId = errorMessageResId!!
                        isTrackedDraftSuccess = false
                        haveAllDraftsSucceeded = false
                    }
                    return@with
                }
            }.onFailure { exception ->
                when (exception) {
                    is NetworkException -> {
                        mailboxContentRealm.executeRealmCallbacks(realmActionsOnDraft)
                        throw exception
                    }
                    is ApiErrorException, is UploadMissingLocalFileException -> {
                        realmActionsOnDraft.add(deleteDraftCallback(draft))
                        if (isTargetDraft) {
                            isTrackedDraftSuccess = false
                            trackedDraftErrorMessageResId = if (exception is ApiErrorException) {
                                saveDraftAfterRateLimitError(exception.errorCode, draft)
                                ErrorCode.getTranslateResForDrafts(exception.errorCode)
                            } else {
                                (exception as UploadMissingLocalFileException).errorRes
                            }
                        }
                    }
                }
                exception.printStackTrace()

                if ((exception as? ApiErrorException)?.errorCode != ErrorCode.DRAFT_HAS_TOO_MANY_RECIPIENTS) {
                    Sentry.captureException(exception) { scope ->
                        if (exception is ApiErrorException) scope.setTag("Api error code", exception.errorCode ?: "")
                    }
                }

                haveAllDraftsSucceeded = false
            }
        }

        mailboxContentRealm.executeRealmCallbacks(realmActionsOnDraft)

        SentryDebug.addDraftsBreadcrumbs(
            drafts = draftController.getAllDrafts(mailboxContentRealm),
            step = "after handleDraftsActions",
        )

        mailboxContentRealm.write {
            val orphans = DraftController.getOrphanDrafts(realm = this)
            SentryDebug.sendOrphanDrafts(orphans)
            delete(orphans)
        }

        showDraftErrorNotification(isTrackedDraftSuccess, trackedDraftErrorMessageResId, trackedDraftAction)

        return computeResult(
            scheduledMessagesEtops,
            haveAllDraftsSucceeded,
            isTrackedDraftSuccess,
            remoteUuidOfTrackedDraft,
            trackedDraftAction,
            trackedDraftErrorMessageResId,
            trackedScheduledDraftDate,
            trackedUnscheduledDraftUrl,
        )
    }

    /** This function save the draft if the target draft failed to send due to a rate-limit exception
     *  As it's a side effect, it should fail silently to keep the original error flow */
    private suspend fun saveDraftAfterRateLimitError(errorCode: String?, draft: Draft) = runCatching {
        if (errorCode == ErrorCode.SEND_LIMIT_EXCEEDED || errorCode == ErrorCode.SEND_DAILY_LIMIT_REACHED) {
            SentryLog.d(TAG, "Trying to save draft after a rate-limit error")
            mailboxContentRealm.write {
                draftController.updateDraft(draft.localUuid, realm = this) {
                    it.action = DraftAction.SAVE
                }
            }
            executeDraftAction(draftController.getDraft(draft.localUuid)!!, mailbox.uuid)
        }
    }.onFailure { if (it is CancellationException) throw it }

    private suspend fun Realm.executeRealmCallbacks(realmActionsOnDraft: List<(MutableRealm) -> Unit>) {
        write {
            realmActionsOnDraft.forEach { realmAction -> realmAction(this) }
        }
    }

    private fun showDraftErrorNotification(
        isTrackedDraftSuccess: Boolean?,
        trackedDraftErrorMessageResId: Int?,
        trackedDraftAction: DraftAction?,
    ) {
        val needsToShowErrorNotification = mainApplication.isAppInBackground && isTrackedDraftSuccess == false
        if (needsToShowErrorNotification) {
            val builder = notificationUtils.buildDraftErrorNotification(trackedDraftErrorMessageResId!!, trackedDraftAction!!)
            @Suppress("MissingPermission")
            notificationManagerCompat.notify(UUID.randomUUID().hashCode(), builder.build())
        }
    }

    private fun computeResult(
        scheduledMessagesEtops: MutableList<String>,
        haveAllDraftsSucceeded: Boolean,
        isTrackedDraftSuccess: Boolean?,
        remoteUuidOfTrackedDraft: String?,
        trackedDraftAction: DraftAction?,
        trackedDraftErrorMessageResId: Int?,
        trackedScheduledDraftDate: String?,
        trackedUnscheduleDraftUrl: String?,
    ): Result {

        val biggestScheduledMessagesEtop = scheduledMessagesEtops.mapNotNull {
            dateFormatWithTimezone.parse(it)?.time
        }.maxOrNull()

        return if (haveAllDraftsSucceeded || isTrackedDraftSuccess == true) {
            val outputData = if (isSnackbarFeedbackNeeded) {
                workDataOf(
                    REMOTE_DRAFT_UUID_KEY to draftLocalUuid?.let { remoteUuidOfTrackedDraft },
                    ASSOCIATED_MAILBOX_UUID_KEY to draftLocalUuid?.let { mailbox.uuid },
                    RESULT_DRAFT_ACTION_KEY to draftLocalUuid?.let { trackedDraftAction?.name },
                    BIGGEST_SCHEDULED_MESSAGES_ETOP_KEY to biggestScheduledMessagesEtop,
                    RESULT_USER_ID_KEY to userId,
                    SCHEDULED_DRAFT_DATE_KEY to trackedScheduledDraftDate,
                    UNSCHEDULE_DRAFT_URL_KEY to trackedUnscheduleDraftUrl,
                )
            } else {
                Data.EMPTY
            }
            Result.success(outputData)
        } else {
            val outputData = if (isSnackbarFeedbackNeeded) {
                workDataOf(
                    ERROR_MESSAGE_RESID_KEY to trackedDraftErrorMessageResId,
                    BIGGEST_SCHEDULED_MESSAGES_ETOP_KEY to biggestScheduledMessagesEtop,
                    RESULT_USER_ID_KEY to userId,
                )
            } else {
                Data.EMPTY
            }
            Result.failure(outputData)
        }
    }

    data class DraftActionResult(
        val realmActionOnDraft: ((MutableRealm) -> Unit)?,
        val scheduledMessageEtop: String?,
        val unscheduleDraftUrl: String?,
        val errorMessageResId: Int?,
        val savedDraftUuid: String?,
        val isSuccess: Boolean,
    )

    private suspend fun executeDraftAction(draft: Draft, mailboxUuid: String, isFirstTime: Boolean = true): DraftActionResult {

        var realmActionOnDraft: ((MutableRealm) -> Unit)? = null
        var scheduledMessageEtop: String? = null
        var scheduleDraftAction: String? = null
        var savedDraftUuid: String? = null

        SentryDebug.addDraftBreadcrumbs(draft, step = "executeDraftAction (action = ${draft.action?.name.toString()})")

        // TODO: Remove this whole `draft.attachments.any { … }` + `addDraftBreadcrumbs()` when the Attachments issue is fixed.
        if (draft.attachments.any { it.attachmentUploadStatus != AttachmentUploadStatus.FINISHED }) {

            Sentry.captureMessage(
                "We tried to [${draft.action?.name}] a Draft, but an Attachment wasn't uploaded.",
                SentryLevel.ERROR,
            )

            // Remove the Draft if it's corrupted instead of sending a Sentry every time the worker starts again
            draftController.deleteDraft(draft)
            SentryLog.i("CorruptedAttachment", "Remove draft from realm due to corrupted attachment")

            return DraftActionResult(
                realmActionOnDraft = null,
                scheduledMessageEtop = null,
                unscheduleDraftUrl = null,
                errorMessageResId = R.string.errorCorruptAttachment,
                savedDraftUuid = null,
                isSuccess = false,
            )
        }

        suspend fun executeSaveAction() = with(ApiRepository.saveDraft(mailboxUuid, draft, okHttpClient)) {
            data?.let { data ->
                realmActionOnDraft = { realm ->
                    realm.findLatest(draft)?.apply {
                        remoteUuid = data.draftRemoteUuid
                        messageUid = data.messageUid
                        action = null
                    }
                }
                scheduledMessageEtop = dateFormatWithTimezone.format(Date())
                savedDraftUuid = data.draftRemoteUuid
            } ?: run {
                retryWithNewIdentityOrThrow(draft, mailboxUuid, isFirstTime)
            }
        }

        suspend fun executeSendAction() = with(ApiRepository.sendDraft(mailboxUuid, draft, okHttpClient)) {
            when {
                isSuccess() -> {
                    realmActionOnDraft = deleteDraftCallback(draft)
                    scheduledMessageEtop = data?.scheduledMessageEtop
                }
                error?.exception is SerializationException -> {
                    realmActionOnDraft = deleteDraftCallback(draft)
                    Sentry.captureMessage("Return JSON for SendDraft API call was modified", SentryLevel.ERROR) { scope ->
                        scope.setExtra("Is data null ?", "${data == null}")
                        scope.setExtra("Error code", error?.code.toString())
                        scope.setExtra("Error description", error?.description.toString())
                    }
                }
                else -> {
                    retryWithNewIdentityOrThrow(draft, mailboxUuid, isFirstTime)
                }
            }
        }

        suspend fun executeScheduleAction() = with(ApiRepository.scheduleDraft(mailboxUuid, draft, okHttpClient)) {
            when {
                isSuccess() -> {
                    realmActionOnDraft = deleteDraftCallback(draft)
                    scheduledMessageEtop = dateFormatWithTimezone.format(Date())
                    scheduleDraftAction = data?.unscheduleDraftUrl
                }
                error?.exception is SerializationException -> {
                    realmActionOnDraft = deleteDraftCallback(draft)
                    Sentry.captureMessage("Return JSON for SendScheduleDraft API call was modified", SentryLevel.ERROR) { scope ->
                        scope.setExtra("Is data null ?", "${data == null}")
                        scope.setExtra("Error code", error?.code.toString())
                        scope.setExtra("Error description", error?.description.toString())
                    }
                }
                else -> {
                    retryWithNewIdentityOrThrow(draft, mailboxUuid, isFirstTime)
                }
            }
        }

        when (draft.action) {
            DraftAction.SAVE -> executeSaveAction()
            DraftAction.SEND -> executeSendAction()
            DraftAction.SCHEDULE -> executeScheduleAction()
            else -> Unit
        }

        return DraftActionResult(
            realmActionOnDraft = realmActionOnDraft,
            scheduledMessageEtop = scheduledMessageEtop,
            unscheduleDraftUrl = scheduleDraftAction,
            errorMessageResId = null,
            savedDraftUuid = savedDraftUuid,
            isSuccess = true,
        )
    }

    private suspend inline fun <reified T> ApiResponse<T>.retryWithNewIdentityOrThrow(
        draft: Draft,
        mailboxUuid: String,
        isFirstTime: Boolean,
    ): DraftActionResult {
        if (isFirstTime && error?.code == ErrorCode.IDENTITY_NOT_FOUND) {
            return updateSignaturesThenRetry(draft, mailboxUuid)
        } else {
            throwErrorAsException()
        }
    }

    private suspend fun updateSignaturesThenRetry(draft: Draft, mailboxUuid: String): DraftActionResult {

        updateSignatures(mailbox, mailboxContentRealm)

        val signature = mailbox.getDefaultSignatureWithFallback()

        mailboxContentRealm.write {
            draftController.updateDraft(draft.localUuid, realm = this) { it.identityId = signature.id.toString() }
        }

        return executeDraftAction(
            draft = DraftController.getDraft(draft.localUuid, mailboxContentRealm)!!,
            mailboxUuid = mailboxUuid,
            isFirstTime = false,
        )
    }

    private fun deleteDraftCallback(draft: Draft): (MutableRealm) -> Unit = { realm ->
        realm.findLatest(draft)?.let { localDraft ->
            deleteDraftUploadDir(applicationContext, localDraft.localUuid, userId, mailboxId, mustForceDelete = true)
            realm.delete(localDraft)
        }
    }

    class Scheduler @Inject constructor(
        private val draftController: DraftController,
        private val workManager: WorkManager,
    ) {

        fun scheduleWork(draftLocalUuid: String? = null) {

            if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return
            if (draftController.getDraftsWithActionsCount() == 0L) return

            SentryLog.d(TAG, "Work scheduled")

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

        fun getCompletedWorkInfoLiveData(): LiveData<List<WorkInfo>> {
            return WorkerUtils.getWorkInfoLiveData(TAG, workManager, listOf(State.SUCCEEDED))
        }

        fun getFailedWorkInfoLiveData(): LiveData<List<WorkInfo>> {
            return WorkerUtils.getWorkInfoLiveData(TAG, workManager, listOf(State.FAILED))
        }

        fun getCompletedAndFailedInfoLiveData(): LiveData<List<WorkInfo>> {
            return WorkerUtils.getWorkInfoLiveData(TAG, workManager, listOf(State.SUCCEEDED, State.FAILED))
        }
    }

    companion object {
        private const val TAG = "DraftsActionsWorker"
        private const val USER_ID_KEY = "userId"
        private const val MAILBOX_ID_KEY = "mailboxIdKey"
        private const val DRAFT_LOCAL_UUID_KEY = "draftLocalUuidKey"
        const val ERROR_MESSAGE_RESID_KEY = "errorMessageResIdKey"
        const val REMOTE_DRAFT_UUID_KEY = "remoteDraftUuidKey"
        const val ASSOCIATED_MAILBOX_UUID_KEY = "associatedMailboxUuidKey"
        const val RESULT_DRAFT_ACTION_KEY = "resultDraftActionKey"
        const val BIGGEST_SCHEDULED_MESSAGES_ETOP_KEY = "biggestScheduledMessagesEtopKey"
        const val RESULT_USER_ID_KEY = "resultUserIdKey"
        const val SCHEDULED_DRAFT_DATE_KEY = "scheduledDraftDateKey"
        const val UNSCHEDULE_DRAFT_URL_KEY = "unscheduleDraftUrlKey"
    }
}
