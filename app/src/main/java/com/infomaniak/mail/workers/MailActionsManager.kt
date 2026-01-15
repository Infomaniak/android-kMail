/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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

import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.common.utils.FORMAT_DATE_WITH_TIMEZONE
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.exceptions.NetworkException
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.AttachmentUploadStatus
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.ApiErrorException
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.LocalStorageUtils.deleteDraftUploadDir
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.SharedUtils.Companion.updateSignatures
import com.infomaniak.mail.utils.WorkerUtils.UploadMissingLocalFileException
import com.infomaniak.mail.utils.extensions.throwErrorAsException
import com.infomaniak.mail.utils.uploadAttachmentsWithMutex
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.ALL_EMOJI_SENT_STATUS
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.ASSOCIATED_MAILBOX_UUID_KEY
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.BIGGEST_SCHEDULED_MESSAGES_ETOP_KEY
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.EMOJI_SENT_STATUS
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.ERROR_MESSAGE_RESID_KEY
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.IS_SUCCESS
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.REMOTE_DRAFT_UUID_KEY
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.RESULT_DRAFT_ACTION_KEY
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.RESULT_USER_ID_KEY
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.SCHEDULED_DRAFT_DATE_KEY
import com.infomaniak.mail.workers.DraftsActionsWorker.Companion.UNSCHEDULE_DRAFT_URL_KEY
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MailActionsManager(
    private val mailboxContentRealm: Realm = RealmDatabase.newMailboxContentInstance,
    private val userId: Int,
    private val mailboxId: Int,
    private val mailbox: Mailbox,
    private val isSnackbarFeedbackNeeded: Boolean = false,
    private val draftLocalUuid: String?,
    private val draftController: DraftController,
    private val okHttpClient: OkHttpClient,
    private val coroutineWorker: CoroutineWorker,
    private val notificationUtils: NotificationUtils,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val isAppInBackground: () -> Boolean,
) {

    private val dateFormatWithTimezone by lazy { SimpleDateFormat(FORMAT_DATE_WITH_TIMEZONE, Locale.ROOT) }

    suspend fun handleDraftsActions(): ListenableWorker.Result {

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
        if (drafts.isEmpty()) return ListenableWorker.Result.failure()

        var haveAllDraftsSucceeded = true

        // Keep a list of all results to send them with the worker's result so we are certain to not miss any. With setProgress we
        // could miss some if we stopped listening midway.
        // setProgress is still needed as it it lets us provide quick feedback to the user.
        val emojiSendResults = mutableListOf<EmojiSendResult>()

        /**
         * This list is reversed because we'll delete items while looping over it.
         * Doing so for managed Realm objects will lively update the list we're iterating through, making us skip the next item.
         * Looping in reverse enables us to not skip any item.
         */
        drafts.asReversed().forEach { draft ->

            val isTargetDraft = draft.localUuid == draftLocalUuid
            if (isTargetDraft) {
                trackedDraftAction = draft.action
                trackedScheduledDraftDate = draft.scheduleDate
            }

            runCatching {
                val updatedDraft = uploadAttachmentsWithMutex(draft.localUuid, mailbox, mailboxContentRealm)

                val executionResult = runCatching { executeDraftAction(updatedDraft, mailbox.uuid) }
                if (draft.action == DraftAction.SEND_REACTION) {
                    val isDraftActionSuccess = executionResult.isSuccess && executionResult.getOrThrow().isSuccess

                    draft.toEmojiSendResult(isDraftActionSuccess)?.let {
                        coroutineWorker.notifyOfEmojiProgress(it)
                        emojiSendResults.add(it)
                    }
                }

                with(executionResult.getOrThrow()) {
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
            }.cancellable().onFailure { exception ->
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
            val orphans = DraftController.getOrphanDraftsBlocking(realm = this)
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
            emojiSendResults,
        )
    }

    private fun computeResult(
        scheduledMessagesEtops: MutableList<String>,
        haveAllDraftsSucceeded: Boolean,
        isTrackedDraftSuccess: Boolean?,
        remoteUuidOfTrackedDraft: String?,
        trackedDraftAction: DraftAction?,
        trackedDraftErrorMessageResId: Int?,
        trackedScheduledDraftDate: String?,
        trackedUnscheduledDraftUrl: String?,
        emojiSendResults: List<EmojiSendResult>,
    ): ListenableWorker.Result {

        val biggestScheduledMessagesEtop = scheduledMessagesEtops.mapNotNull {
            dateFormatWithTimezone.parse(it)?.time
        }.maxOrNull()

        val isSuccess = haveAllDraftsSucceeded || isTrackedDraftSuccess == true

        // Common result values
        val etopPair = BIGGEST_SCHEDULED_MESSAGES_ETOP_KEY to biggestScheduledMessagesEtop
        val userIdPair = RESULT_USER_ID_KEY to userId
        val emojiPair = ALL_EMOJI_SENT_STATUS to Json.encodeToString(emojiSendResults)
        // We need an array to be able to read a nullable value on the other side
        val isSuccessPair = IS_SUCCESS to arrayOf(isSuccess)

        val emptyData = workDataOf(isSuccessPair)

        return if (isSuccess) {
            val outputData = if (isSnackbarFeedbackNeeded) {
                workDataOf(
                    REMOTE_DRAFT_UUID_KEY to draftLocalUuid?.let { remoteUuidOfTrackedDraft },
                    ASSOCIATED_MAILBOX_UUID_KEY to draftLocalUuid?.let { mailbox.uuid },
                    RESULT_DRAFT_ACTION_KEY to draftLocalUuid?.let { trackedDraftAction?.name },
                    etopPair,
                    userIdPair,
                    emojiPair,
                    isSuccessPair,
                    SCHEDULED_DRAFT_DATE_KEY to trackedScheduledDraftDate,
                    UNSCHEDULE_DRAFT_URL_KEY to trackedUnscheduledDraftUrl,
                )
            } else {
                emptyData
            }
            ListenableWorker.Result.success(outputData)
        } else {
            val outputData = if (isSnackbarFeedbackNeeded) {
                workDataOf(
                    ERROR_MESSAGE_RESID_KEY to trackedDraftErrorMessageResId,
                    etopPair,
                    userIdPair,
                    emojiPair,
                    isSuccessPair,
                )
            } else {
                emptyData
            }
            ListenableWorker.Result.success(outputData)
        }
    }

    private fun showDraftErrorNotification(
        isTrackedDraftSuccess: Boolean?,
        trackedDraftErrorMessageResId: Int?,
        trackedDraftAction: DraftAction?,
    ) {
        val needsToShowErrorNotification = isAppInBackground() && isTrackedDraftSuccess == false
        if (needsToShowErrorNotification) {
            val builder = notificationUtils.buildDraftErrorNotification(trackedDraftErrorMessageResId!!, trackedDraftAction!!)
            @Suppress("MissingPermission")
            notificationManagerCompat.notify(UUID.randomUUID().hashCode(), builder.build())
        }
    }

    /**
     * This function save the draft if the target draft failed to send due to a rate-limit exception.
     * As it's a side effect, it should fail silently to keep the original error flow.
     */
    private suspend fun saveDraftAfterRateLimitError(errorCode: String?, draft: Draft) = runCatching {
        if (errorCode == ErrorCode.SEND_LIMIT_EXCEEDED || errorCode == ErrorCode.SEND_DAILY_LIMIT_REACHED) {
            SentryLog.d(TAG, "Trying to save draft after a rate-limit error")
            mailboxContentRealm.write {
                DraftController.updateDraftBlocking(draft.localUuid, realm = this) {
                    it.action = DraftAction.SAVE
                }
            }
            executeDraftAction(Dispatchers.IO { draftController.getDraft(draft.localUuid) }!!, mailbox.uuid)
        }
    }.cancellable()

    private suspend fun executeDraftAction(draft: Draft, mailboxUuid: String, isFirstTime: Boolean = true): DraftActionResult {

        var realmActionOnDraft: ((MutableRealm) -> Unit)? = null
        var scheduledMessageEtop: String? = null
        var scheduleDraftAction: String? = null
        var savedDraftUuid: String? = null

        SentryDebug.addDraftBreadcrumbs(draft, step = "executeDraftAction (action = ${draft.action?.name.toString()})")

        // TODO: Remove this whole `draft.attachments.any { … }` + `addDraftBreadcrumbs()` when the Attachments issue is fixed.
        if (draft.attachments.any { it.attachmentUploadStatus != AttachmentUploadStatus.UPLOADED }) {

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
            DraftAction.SEND, DraftAction.SEND_REACTION -> executeSendAction()
            DraftAction.SCHEDULE -> executeScheduleAction()
            null -> Unit
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

    private fun deleteDraftCallback(draft: Draft): (MutableRealm) -> Unit = { realm ->
        realm.findLatest(draft)?.let { localDraft ->
            deleteDraftUploadDir(
                coroutineWorker.applicationContext,
                localDraft.localUuid,
                userId,
                mailboxId,
                mustForceDelete = true,
            )
            realm.delete(localDraft)
        }
    }

    private suspend fun Realm.executeRealmCallbacks(realmActionsOnDraft: List<(MutableRealm) -> Unit>) {
        write {
            realmActionsOnDraft.forEach { realmAction -> realmAction(this) }
        }
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
            DraftController.updateDraftBlocking(draft.localUuid, realm = this) { it.identityId = signature.id.toString() }
        }

        return executeDraftAction(
            draft = DraftController.getDraft(draft.localUuid, mailboxContentRealm)!!,
            mailboxUuid = mailboxUuid,
            isFirstTime = false,
        )
    }

    @Serializable
    data class EmojiSendResult(val previousMessageUid: String, val isSuccess: Boolean, val emoji: String)

    @Serializable
    @JvmInline
    value class EmojiSendResults(val results: List<EmojiSendResult>)

    private data class DraftActionResult(
        val realmActionOnDraft: ((MutableRealm) -> Unit)?,
        val scheduledMessageEtop: String?,
        val unscheduleDraftUrl: String?,
        val errorMessageResId: Int?,
        val savedDraftUuid: String?,
        val isSuccess: Boolean,
    )

    companion object Companion {
        private const val TAG = "DraftsActionsWorker"

        private suspend fun CoroutineWorker.notifyOfEmojiProgress(emojiSendResult: EmojiSendResult) {
            setProgress(workDataOf(EMOJI_SENT_STATUS to Json.encodeToString(emojiSendResult)))
        }

        private fun Draft.toEmojiSendResult(isSuccess: Boolean): EmojiSendResult? {
            val previousMessageUid = inReplyToUid ?: return null // inReplyToUid is always set in the case of an emoji reaction
            val emoji = emojiReaction ?: return null // we always have an emoji in the case of an emoji reaction
            return EmojiSendResult(previousMessageUid, isSuccess, emoji)
        }
    }
}
