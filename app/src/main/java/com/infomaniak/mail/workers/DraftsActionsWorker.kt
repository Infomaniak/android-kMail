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
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.AttachmentUploadStatus
import com.infomaniak.mail.data.models.Folder.FolderRole
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
    private val folderController: FolderController,
    private val refreshController: RefreshController,
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

    private var scheduleDate: Long? = null
    private var scheduleAction: String? = null

    private val dateFormatWithTimezone by lazy { SimpleDateFormat(FORMAT_DATE_WITH_TIMEZONE, Locale.ROOT) }

    override suspend fun launchWork(): Result = withContext(ioDispatcher) {
        SentryLog.d(TAG, "Work started")

        if (DraftController.getDraftsWithActionsCount(mailboxContentRealm) == 0L) return@withContext Result.success()
        if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return@withContext Result.failure()

        userId = inputData.getIntOrNull(USER_ID_KEY) ?: return@withContext Result.failure()
        mailboxId = inputData.getIntOrNull(MAILBOX_ID_KEY) ?: return@withContext Result.failure()
        draftLocalUuid = inputData.getString(DRAFT_LOCAL_UUID_KEY)

        scheduleDate = inputData.getLongOrNull(SCHEDULE_DATE_KEY)
        scheduleAction = inputData.getString(SCHEDULE_ACTION_KEY)

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
        val etopScheduledDates = mutableListOf<String>()
        var trackedDraftErrorMessageResId: Int? = null
        var remoteUuidOfTrackedDraft: String? = null
        var trackedDraftAction: DraftAction? = null
        var isTrackedDraftSuccess: Boolean? = null

        val drafts = draftController.getDraftsWithActions(mailboxContentRealm)
        SentryLog.d(TAG, "handleDraftsActions: ${drafts.count()} drafts to handle")
        if (drafts.isEmpty()) return Result.failure()

        var haveAllDraftsSucceeded = true

        drafts.asReversed().forEach { draft ->

            val isTargetDraft = draft.localUuid == draftLocalUuid
            if (isTargetDraft) trackedDraftAction = draft.action

            runCatching {
                val updatedDraft = uploadAttachmentsWithMutex(draft, mailbox, draftController, mailboxContentRealm)
                with(executeDraftAction(updatedDraft, mailbox.uuid)) {
                    if (isSuccess) {
                        if (isTargetDraft) {
                            remoteUuidOfTrackedDraft = savedDraftUuid
                            isTrackedDraftSuccess = true
                        }
                        etopScheduledDate?.let(etopScheduledDates::add)
                        realmActionOnDraft?.let(realmActionsOnDraft::add)

                        this@DraftsActionsWorker.scheduleAction = scheduleAction
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
            etopScheduledDates,
            haveAllDraftsSucceeded,
            isTrackedDraftSuccess,
            remoteUuidOfTrackedDraft,
            trackedDraftAction,
            trackedDraftErrorMessageResId,
        )
    }

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

    private suspend fun refreshScheduleDraftFolder() {
        val currentMailbox = mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId) ?: return
        val folder = folderController.getFolder(FolderRole.SCHEDULED_DRAFTS)

        if (folder?.cursor != null) {
            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER_WITH_ROLE,
                mailbox = currentMailbox,
                folderId = folder.id,
                realm = mailboxContentRealm,
            )
        }
    }

    private fun computeResult(
        etopScheduledDates: MutableList<String>,
        haveAllDraftsSucceeded: Boolean,
        isTrackedDraftSuccess: Boolean?,
        remoteUuidOfTrackedDraft: String?,
        trackedDraftAction: DraftAction?,
        trackedDraftErrorMessageResId: Int?,
    ): Result {

        val biggestEtopScheduledDate = etopScheduledDates.mapNotNull { dateFormatWithTimezone.parse(it)?.time }.maxOrNull()

        return if (haveAllDraftsSucceeded || isTrackedDraftSuccess == true) {
            val outputData = if (isSnackbarFeedbackNeeded) {
                workDataOf(
                    REMOTE_DRAFT_UUID_KEY to draftLocalUuid?.let { remoteUuidOfTrackedDraft },
                    ASSOCIATED_MAILBOX_UUID_KEY to draftLocalUuid?.let { mailbox.uuid },
                    RESULT_DRAFT_ACTION_KEY to draftLocalUuid?.let { trackedDraftAction?.name },
                    BIGGEST_ETOP_SCHEDULED_DATE_KEY to biggestEtopScheduledDate,
                    RESULT_USER_ID_KEY to userId,
                    SCHEDULE_DATE_KEY to scheduleDate,
                    SCHEDULE_ACTION_KEY to scheduleAction,
                )
            } else {
                Data.EMPTY
            }
            Result.success(outputData)
        } else {
            val outputData = if (isSnackbarFeedbackNeeded) {
                workDataOf(
                    ERROR_MESSAGE_RESID_KEY to trackedDraftErrorMessageResId,
                    BIGGEST_ETOP_SCHEDULED_DATE_KEY to biggestEtopScheduledDate,
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
        val etopScheduledDate: String?,
        val scheduleAction: String?,
        val errorMessageResId: Int?,
        val savedDraftUuid: String?,
        val isSuccess: Boolean,
    )

    private suspend fun executeDraftAction(draft: Draft, mailboxUuid: String, isFirstTime: Boolean = true): DraftActionResult {

        var realmActionOnDraft: ((MutableRealm) -> Unit)? = null
        var etopScheduledDate: String? = null
        var scheduleAction: String? = null
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
                etopScheduledDate = null,
                scheduleAction = null,
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
                etopScheduledDate = dateFormatWithTimezone.format(Date())
                savedDraftUuid = data.draftRemoteUuid
            } ?: run {
                retryWithNewIdentityOrThrow(draft, mailboxUuid, isFirstTime)
            }
        }

        suspend fun executeSendAction() = with(ApiRepository.sendDraft(mailboxUuid, draft, okHttpClient)) {
            when {
                isSuccess() -> {
                    etopScheduledDate = data?.etopScheduledDate
                    realmActionOnDraft = deleteDraftCallback(draft)
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

        suspend fun executeScheduleSendAction() = with(ApiRepository.sendScheduleDraft(mailboxUuid, draft, okHttpClient)) {
            when {
                isSuccess() -> {
                    scheduleAction = data?.scheduleAction
                    realmActionOnDraft = deleteDraftCallback(draft)
                    refreshScheduleDraftFolder()
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
            DraftAction.SCHEDULE -> executeScheduleSendAction()
            else -> Unit
        }

        return DraftActionResult(
            realmActionOnDraft = realmActionOnDraft,
            etopScheduledDate = etopScheduledDate,
            scheduleAction = scheduleAction,
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

        fun scheduleWork(draftLocalUuid: String? = null, scheduleDate: Date? = null) {

            if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return
            if (draftController.getDraftsWithActionsCount() == 0L) return

            SentryLog.d(TAG, "Work scheduled")

            val workData = workDataOf(
                USER_ID_KEY to AccountUtils.currentUserId,
                MAILBOX_ID_KEY to AccountUtils.currentMailboxId,
                DRAFT_LOCAL_UUID_KEY to draftLocalUuid,
                SCHEDULE_DATE_KEY to scheduleDate?.time,
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
        const val BIGGEST_ETOP_SCHEDULED_DATE_KEY = "biggestEtopScheduledDateKey"
        const val RESULT_USER_ID_KEY = "resultUserIdKey"
        const val SCHEDULE_DATE_KEY = "scheduleDateKey"
        const val SCHEDULE_ACTION_KEY = "scheduleActionKey"
    }
}
