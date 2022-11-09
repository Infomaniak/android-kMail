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
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class DraftsActionsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return@runCatching Result.failure()
            handleDraftsActions()
            Result.success()
        }.getOrElse { exception ->
            exception.printStackTrace()
            if (exception is CancellationException) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    private fun handleDraftsActions(): Result {
        return RealmDatabase.mailboxContent().writeBlocking {

            fun getCurrentMailboxUuid(): String? {
                return MainViewModel.currentMailboxObjectId.value?.let(MailboxController::getMailbox)?.uuid
            }

            val drafts = DraftController.getDraftsWithActions(this).ifEmpty { null } ?: return@writeBlocking Result.failure()
            val mailboxUuid = getCurrentMailboxUuid() ?: return@writeBlocking Result.failure()

            drafts.reversed().forEach { draft ->
                DraftController.executeDraftAction(draft, mailboxUuid, this)
            }

            Result.success()
        }
    }

    companion object {
        const val TAG = "DraftsActionsWorker"

        fun scheduleWork(context: Context) {
            if (AccountUtils.currentMailboxId == AppSettings.DEFAULT_ID) return

            val workRequest = OneTimeWorkRequestBuilder<DraftsActionsWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }
    }
}