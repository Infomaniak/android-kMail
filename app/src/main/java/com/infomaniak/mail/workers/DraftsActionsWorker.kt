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
import com.infomaniak.lib.core.utils.ApiController
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.setExpeditedWorkRequest
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DraftsActionsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val mailboxContentRealm by lazy { RealmDatabase.newMailboxContentInstance }
    private val mailboxInfoRealm by lazy { RealmDatabase.newMailboxInfoInstance }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (runAttemptCount > MAX_RETRIES) return@withContext Result.failure()
        runCatching {
            if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return@runCatching Result.failure()
            handleDraftsActions()
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

    private fun handleDraftsActions(): Result {
        return mailboxContentRealm.writeBlocking {

            fun getCurrentMailboxUuid(): String? {
                val mailboxObjectId = MainViewModel.currentMailboxObjectId.value
                return mailboxObjectId?.let { MailboxController.getMailbox(it, mailboxInfoRealm) }?.uuid
            }

            val drafts = DraftController.getDraftsWithActions(realm = this).ifEmpty { null }
                ?: return@writeBlocking Result.failure()
            val mailboxUuid = getCurrentMailboxUuid() ?: return@writeBlocking Result.failure()
            var hasRemoteException = false

            drafts.reversed().forEach { draft ->
                try {
                    DraftController.executeDraftAction(draft, mailboxUuid, realm = this)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    Sentry.captureException(exception)
                    if (exception is ApiController.NetworkException) return@writeBlocking Result.retry()
                    hasRemoteException = true
                }
            }

            if (hasRemoteException) Result.failure() else Result.success()
        }
    }

    companion object {
        private const val TAG = "DraftsActionsWorker"
        private const val WORK_NAME = "SaveDraftWithAttachments"
        private const val MAX_RETRIES = 3

        fun scheduleWork(context: Context, localDraftUuid: String? = null) {

            if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return
            if (DraftController.getDraftsWithActionsCount() == 0L) return

            val workRequest = OneTimeWorkRequestBuilder<DraftsActionsWorker>()
                .addTag(TAG)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpeditedWorkRequest()
                .build()

            val workManager = WorkManager.getInstance(context)

            if (localDraftUuid == null) {
                // Begin unique work, only start DraftActionsWorker
                workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
            } else {
                // Begin unique chain work, upload attachments before save current draft
                val uploadAttachmentsWorkRequest = UploadAttachmentsWorker.getWorkRequest(localDraftUuid) ?: return
                workManager.beginUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, uploadAttachmentsWorkRequest)
                    .then(workRequest)
                    .enqueue()
            }
        }

        fun getRunningWorkInfosLiveData(context: Context): LiveData<MutableList<WorkInfo>> {
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG)).addStates(listOf(WorkInfo.State.RUNNING)).build()
            return WorkManager.getInstance(context).getWorkInfosLiveData(workQuery)
        }
    }
}
