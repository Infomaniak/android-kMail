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
package com.infomaniak.mail.workers

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.MainApplication
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.WorkerUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
    private var mailboxId: Int = -7 // AppSettings.DEFAULT_ID
    private lateinit var mailbox: Mailbox
    private var userId: Int by Delegates.notNull()
    private var draftLocalUuid: String? = null
    private lateinit var userApiToken: String
    private var isSnackbarFeedbackNeeded: Boolean = false

    private var mailActionsManager: MailActionsManager? = null

    override suspend fun launchWork(): Result = withContext(ioDispatcher) {
        SentryLog.d(TAG, "Work started")

        if (DraftController.getDraftsWithActionsCount(mailboxContentRealm) == 0L) return@withContext Result.success()
        if (AccountUtils.currentMailboxId <= -1) return@withContext Result.failure() // AppSettings.DEFAULT_ID

        userId = inputData.getIntOrNull(USER_ID_KEY) ?: return@withContext Result.failure()
        mailboxId = inputData.getIntOrNull(MAILBOX_ID_KEY) ?: return@withContext Result.failure()
        draftLocalUuid = inputData.getString(DRAFT_LOCAL_UUID_KEY)

        userApiToken = AccountUtils.getUserById(userId)?.apiToken?.accessToken ?: return@withContext Result.failure()
        mailbox = mailboxController.getMailbox(userId, mailboxId) ?: return@withContext Result.failure()
        okHttpClient = AccountUtils.getHttpClient(userId)

        isSnackbarFeedbackNeeded = !mainApplication.isAppInBackground

        mailActionsManager = MailActionsManager(
            userId = userId,
            mailboxId = mailboxId,
            mailbox = mailbox,
            isSnackbarFeedbackNeeded = isSnackbarFeedbackNeeded,
            draftLocalUuid = draftLocalUuid,
            draftController = draftController,
            okHttpClient = okHttpClient,
            coroutineWorker = this@DraftsActionsWorker,
            notificationUtils = notificationUtils,
            notificationManagerCompat = notificationManagerCompat,
            isAppInBackground = { mainApplication.isAppInBackground },
        )

        return@withContext mailActionsManager!!.handleDraftsActions()
    }

    override fun onFinish() {
        mailboxContentRealm.close()
        SentryLog.d(TAG, "Work finished")
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = notificationUtils.buildDraftActionsNotification()
        return ForegroundInfo(NotificationUtils.DRAFT_ACTIONS_ID, notification)
    }

    class Scheduler @Inject constructor(
        private val draftController: DraftController,
        private val workManager: WorkManager,
    ) {

        suspend fun scheduleWork(draftLocalUuid: String? = null, mailboxId: Int, userId: Int) {

            if (mailboxId <= -1) return // AppSettings.DEFAULT_ID
            if (draftController.getDraftsWithActionsCount() == 0L) return

            SentryLog.d(TAG, "Work scheduled")

            val workData = workDataOf(
                USER_ID_KEY to userId,
                MAILBOX_ID_KEY to mailboxId,
                DRAFT_LOCAL_UUID_KEY to draftLocalUuid,
            )
            val workRequest = OneTimeWorkRequestBuilder<DraftsActionsWorker>()
                .addTag(TAG)
                .setInputData(workData)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            workManager.enqueueUniqueWork("${TAG}_${mailboxId}", ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
        }

        fun getRunningWorkInfoLiveData(): LiveData<List<WorkInfo>> {
            return WorkerUtils.getWorkInfoLiveData(TAG, workManager, listOf(State.RUNNING))
        }

        fun getCompletedInfoLiveData(): LiveData<List<WorkInfo>> {
            return WorkerUtils.getWorkInfoLiveData(TAG, workManager, listOf(State.SUCCEEDED, State.FAILED))
        }
    }

    companion object {
        const val ERROR_MESSAGE_RESID_KEY = "errorMessageResIdKey"
        const val REMOTE_DRAFT_UUID_KEY = "remoteDraftUuidKey"
        const val ASSOCIATED_MAILBOX_UUID_KEY = "associatedMailboxUuidKey"
        const val RESULT_DRAFT_ACTION_KEY = "resultDraftActionKey"
        const val BIGGEST_SCHEDULED_MESSAGES_ETOP_KEY = "biggestScheduledMessagesEtopKey"
        const val RESULT_USER_ID_KEY = "resultUserIdKey"
        const val SCHEDULED_DRAFT_DATE_KEY = "scheduledDraftDateKey"
        const val UNSCHEDULE_DRAFT_URL_KEY = "unscheduleDraftUrlKey"
        const val IS_SUCCESS = "isSuccess"

        const val EMOJI_SENT_STATUS = "emojiSentStatusKey"
        const val ALL_EMOJI_SENT_STATUS = "allEmojiSentStatusKey"

        private const val TAG = "DraftsActionsWorker"
        private const val USER_ID_KEY = "userId"
        private const val MAILBOX_ID_KEY = "mailboxIdKey"
        private const val DRAFT_LOCAL_UUID_KEY = "draftLocalUuidKey"
    }
}
