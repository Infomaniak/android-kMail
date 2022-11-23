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
import androidx.work.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.ApiController.json
import com.infomaniak.lib.core.utils.isNetworkException
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.KMailHttpClient
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.setExpeditedWorkRequest
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

class UploadAttachmentsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val mailboxContentRealm by lazy { RealmDatabase.newMailboxContentInstance }
    private val mailboxInfoRealm by lazy { RealmDatabase.newMailboxInfoInstance }
    private lateinit var okHttpClient: OkHttpClient

    private var userId: Int by Delegates.notNull()
    private lateinit var mailbox: Mailbox

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        if (runAttemptCount > MAX_RETRY) return@withContext Result.failure()

        runCatching {
            // Get input data
            val localDraftUuid = inputData.getString(LOCAL_DRAFT_UUID_KEY) ?: return@withContext Result.failure()
            val mailboxObjectId = inputData.getString(MAILBOX_OBJECT_ID_KEY) ?: return@withContext Result.failure()
            userId = inputData.getIntOrNull(USER_ID_KEY) ?: return@withContext Result.failure()

            // Get needed realm data
            val draft = DraftController.getDraft(localDraftUuid, mailboxContentRealm) ?: return@runCatching Result.failure()
            mailbox = MailboxController.getMailbox(mailboxObjectId, mailboxInfoRealm) ?: return@runCatching Result.failure()

            // Start upload
            okHttpClient = KMailHttpClient.getHttpClient(userId)
            startUploads(draft, localDraftUuid)

        }.getOrElse { exception ->
            exception.printStackTrace()
            when (exception) {
                is CancellationException -> Result.failure()
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

    private fun startUploads(draft: Draft, localDraftUuid: String): Result {
        getNotUploadedAttachments(draft).forEach { attachment ->
            try {
                attachment.startUpload(localDraftUuid)
            } catch (exception: Exception) {
                exception.printStackTrace()
                if (exception.isNetworkException()) return Result.retry()
                Sentry.captureException(exception)
            }
        }
        return Result.success()
    }

    private fun getNotUploadedAttachments(draft: Draft): List<Attachment> {
        return draft.attachments.filter { attachment -> attachment.uploadLocalUri != null }
    }

    private fun Attachment.startUpload(localDraftUuid: String) {
        val attachmentFile = getUploadLocalFile(applicationContext, localDraftUuid).also { if (!it.exists()) return }
        val request = Request.Builder().url(ApiRoutes.createAttachment(mailbox.uuid))
            .headers(HttpUtils.getHeaders(contentType = null))
            .addHeader("x-ws-attachment-filename", name)
            .addHeader("x-ws-attachment-mime-type", mimeType)
            .addHeader("x-ws-attachment-disposition", "attachment")
            .post(attachmentFile.asRequestBody(mimeType.toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()

        val apiResponse = json.decodeFromString<ApiResponse<Attachment>>(response.body?.string() ?: "")
        if (apiResponse.isSuccess() && apiResponse.data != null) {
            attachmentFile.delete()
            LocalStorageUtils.deleteAttachmentsDirIfEmpty(applicationContext, localDraftUuid, userId, mailbox.mailboxId)
            updateLocalAttachment(localDraftUuid, apiResponse.data!!)
        }
    }

    private fun Attachment.updateLocalAttachment(localDraftUuid: String, attachment: Attachment) {
        mailboxContentRealm.writeBlocking {
            DraftController.updateDraft(localDraftUuid, this) {
                it.attachments.removeIf { attachment -> attachment.uuid == uuid }
                it.attachments.add(attachment)
            }
        }
    }

    private fun Data.getIntOrNull(key: String) = getInt(key, 0).run { if (this == 0) null else this }

    companion object {
        private const val TAG = "UploadAttachmentsWorker"
        private const val MAX_RETRY = 3

        private const val LOCAL_DRAFT_UUID_KEY = "localDraftUuid"
        private const val MAILBOX_OBJECT_ID_KEY = "mailboxObjectId"
        private const val USER_ID_KEY = "userId"

        fun getWorkRequest(localDraftUuid: String): OneTimeWorkRequest? {
            val mailboxObjectId = MainViewModel.currentMailboxObjectId.value ?: return null

            val inputData = workDataOf(
                MAILBOX_OBJECT_ID_KEY to mailboxObjectId,
                USER_ID_KEY to AccountUtils.currentUserId,
                LOCAL_DRAFT_UUID_KEY to localDraftUuid
            )

            return OneTimeWorkRequestBuilder<UploadAttachmentsWorker>()
                .addTag(TAG)
                .addTag(localDraftUuid)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(inputData)
                .setExpeditedWorkRequest()
                .build()
        }

        fun cancelWork(context: Context, localDraftUuid: String? = null) {
            with(WorkManager.getInstance(context)) {
                if (localDraftUuid == null) cancelAllWorkByTag(TAG) else cancelAllWorkByTag(localDraftUuid)
            }
        }
    }
}