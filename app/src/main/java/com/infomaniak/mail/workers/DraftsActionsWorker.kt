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
package com.infomaniak.mail.workers

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.ApiController
import com.infomaniak.lib.core.utils.isNetworkException
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.NotificationUtils.showDraftActionsNotification
import com.infomaniak.mail.utils.setExpeditedWorkRequest
import io.realm.kotlin.MutableRealm
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import kotlin.properties.Delegates

class DraftsActionsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val mailboxContentRealm by lazy { RealmDatabase.newMailboxContentInstance }
    private val mailboxInfoRealm by lazy { RealmDatabase.newMailboxInfoInstance }

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var mailboxObjectId: String
    private lateinit var mailbox: Mailbox
    private var userId: Int by Delegates.notNull()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (runAttemptCount > MAX_RETRIES) return@withContext Result.failure()

        runCatching {
            if (DraftController.getDraftsWithActionsCount() == 0L) return@runCatching Result.success()
            if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return@runCatching Result.failure()
            userId = inputData.getIntOrNull(USER_ID_KEY) ?: return@runCatching Result.failure()

            mailboxObjectId = inputData.getString(MAILBOX_OBJECT_ID_KEY) ?: return@runCatching Result.failure()
            mailbox = MailboxController.getMailbox(mailboxObjectId, mailboxInfoRealm) ?: return@runCatching Result.failure()
            okHttpClient = AccountUtils.getHttpClient(userId)

            moveServiceToForeground()

            handleDraftsActions()
        }.getOrElse { exception ->
            exception.printStackTrace()
            when (exception) {
                is CancellationException -> Result.failure()
                is ApiController.NetworkException -> Result.retry()
                else -> {
                    Sentry.captureException(exception)
                    Result.failure()
                }
            }
        }.also {
            mailboxContentRealm.close()
            mailboxInfoRealm.close()
        }
    }

    private suspend fun moveServiceToForeground() {
        applicationContext.showDraftActionsNotification().apply {
            setForeground(ForegroundInfo(NotificationUtils.DRAFT_ACTIONS_ID, build()))
        }
    }

    private fun handleDraftsActions(): Result {
        return mailboxContentRealm.writeBlocking {

            fun getCurrentMailboxUuid(): String? {
                return MailboxController.getMailbox(mailboxObjectId, mailboxInfoRealm)?.uuid
            }

            val mailboxUuid = getCurrentMailboxUuid() ?: return@writeBlocking Result.failure()
            val drafts = DraftController.getDraftsWithActions(realm = this).ifEmpty { null }
                ?: return@writeBlocking Result.failure()
            var hasRemoteException = false

            drafts.reversed().forEach { draft ->
                try {
                    draft.uploadAttachments(this)
                    DraftController.executeDraftAction(draft, mailboxUuid, realm = this, okHttpClient)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    if (exception is ApiController.NetworkException) throw exception
                    Sentry.captureException(exception)
                    hasRemoteException = true
                }
            }

            if (hasRemoteException) Result.failure() else Result.success()
        }
    }

    private fun Draft.uploadAttachments(realm: MutableRealm): Result {
        getNotUploadedAttachments(this).forEach { attachment ->
            try {
                attachment.startUpload(this.localUuid, realm)
            } catch (exception: Exception) {
                if (exception.isNetworkException()) throw ApiController.NetworkException()
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
        val request = Request.Builder().url(ApiRoutes.createAttachment(mailbox.uuid))
            .headers(HttpUtils.getHeaders(contentType = null))
            .addHeader("x-ws-attachment-filename", name)
            .addHeader("x-ws-attachment-mime-type", mimeType)
            .addHeader("x-ws-attachment-disposition", "attachment")
            .post(attachmentFile.asRequestBody(mimeType.toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()

        val apiResponse = ApiController.json.decodeFromString<ApiResponse<Attachment>>(response.body?.string() ?: "")
        if (apiResponse.isSuccess() && apiResponse.data != null) {
            attachmentFile.delete()
            LocalStorageUtils.deleteAttachmentsDirIfEmpty(applicationContext, localDraftUuid, userId, mailbox.mailboxId)
            updateLocalAttachment(localDraftUuid, apiResponse.data!!, realm)
        }
    }

    private fun Attachment.updateLocalAttachment(localDraftUuid: String, attachment: Attachment, realm: MutableRealm) {
        DraftController.updateDraft(localDraftUuid, realm) {
            realm.delete(it.attachments.first { attachment -> attachment.uuid == uuid })
            it.attachments.add(attachment)
        }
    }

    private fun Data.getIntOrNull(key: String) = getInt(key, 0).run { if (this == 0) null else this }

    companion object {
        private const val TAG = "DraftsActionsWorker"
        private const val MAX_RETRIES = 3

        private const val USER_ID_KEY = "userId"
        private const val MAILBOX_OBJECT_ID_KEY = "mailboxObjectIdKey"

        fun scheduleWork(context: Context) {

            if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return
            if (DraftController.getDraftsWithActionsCount() == 0L) return

            val currentMailboxObjectId = MainViewModel.currentMailboxObjectId.value ?: return
            val workData = workDataOf(USER_ID_KEY to AccountUtils.currentUserId, MAILBOX_OBJECT_ID_KEY to currentMailboxObjectId)
            val workRequest = OneTimeWorkRequestBuilder<DraftsActionsWorker>()
                .addTag(TAG)
                .setInputData(workData)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpeditedWorkRequest()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
        }

        fun getRunningWorkInfosLiveData(context: Context): LiveData<MutableList<WorkInfo>> {
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING))
                .build()
            return WorkManager.getInstance(context).getWorkInfosLiveData(workQuery)
        }
    }
}
