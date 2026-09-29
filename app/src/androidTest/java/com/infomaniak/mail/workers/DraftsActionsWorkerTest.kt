/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.DraftAction
import com.infomaniak.mail.data.models.draft.SaveDraftResult
import com.infomaniak.mail.data.models.extensions.action
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.AccountUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftsActionsWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val okHttpClient = mockk<OkHttpClient>()
    private val mailboxController = mockk<MailboxController>()
    private val workManager = mockk<WorkManager>(relaxed = true)

    private val targetedMailboxRealm by lazy { RealmDatabase.newMailboxContentInstance(USER_ID, TARGETED_MAILBOX_ID) }
    private val currentMailboxRealm by lazy { RealmDatabase.newMailboxContentInstance(USER_ID, CURRENT_MAILBOX_ID) }

    // Subjects of the saved Drafts, paired with the uuid of the Mailbox they have been saved into
    private val savedDrafts = mutableListOf<Pair<String, String?>>()

    @Before
    fun setup() {
        mockkObject(AccountUtils, ApiRepository)
        every { AccountUtils.currentUserId } returns USER_ID
        every { AccountUtils.currentMailboxId } returns CURRENT_MAILBOX_ID
        coEvery { AccountUtils.getUserById(USER_ID)?.apiToken?.accessToken } returns "token"
        coEvery { AccountUtils.getHttpClient(any(), any(), any(), any()) } returns okHttpClient

        coEvery { mailboxController.getMailbox(USER_ID, TARGETED_MAILBOX_ID) } returns Mailbox().apply {
            userId = USER_ID
            mailboxId = TARGETED_MAILBOX_ID
            uuid = TARGETED_MAILBOX_UUID
        }

        coEvery { ApiRepository.saveDraft(any(), any(), any()) } answers {
            savedDrafts.add(firstArg<String>() to secondArg<Draft>().subject)
            ApiResponse(result = ApiResponseStatus.SUCCESS, data = SaveDraftResult("remoteUuid", "messageUid"))
        }
    }

    @After
    fun tearDown() {
        unmockkObject(AccountUtils, ApiRepository)
        targetedMailboxRealm.close()
        currentMailboxRealm.close()
        RealmDatabase.deleteMailboxContent(TARGETED_MAILBOX_ID, USER_ID)
        RealmDatabase.deleteMailboxContent(CURRENT_MAILBOX_ID, USER_ID)
    }

    @Test
    fun draftsOfTheCurrentMailboxAreNotSentToTheTargetedMailbox() = runTest {
        // A Draft was written in the targeted Mailbox, then the user switched to another Mailbox and wrote a Draft there too
        targetedMailboxRealm.write { copyToRealm(createDraft(TARGETED_MAILBOX_DRAFT_SUBJECT)) }
        currentMailboxRealm.write { copyToRealm(createDraft(CURRENT_MAILBOX_DRAFT_SUBJECT)) }

        val workRequest = scheduleWork(TARGETED_MAILBOX_ID)
        createWorker(workRequest).doWork()

        assertEquals(listOf(TARGETED_MAILBOX_UUID to TARGETED_MAILBOX_DRAFT_SUBJECT), savedDrafts)
        assertEquals(1L, DraftController.getDraftsWithActionsCount(currentMailboxRealm))
    }

    private suspend fun scheduleWork(mailboxId: Int): OneTimeWorkRequest {
        val workRequest = slot<OneTimeWorkRequest>()
        every { workManager.enqueueUniqueWork(any(), any(), capture(workRequest)) } returns mockk(relaxed = true)

        DraftsActionsWorker.Scheduler(workManager, Dispatchers.IO).scheduleWork(mailboxId = mailboxId, userId = USER_ID)

        assertTrue("The work should be scheduled for the Drafts of the targeted Mailbox", workRequest.isCaptured)
        return workRequest.captured
    }

    @SuppressLint("RestrictedApi")
    private fun createWorker(workRequest: OneTimeWorkRequest): DraftsActionsWorker {
        val workerParameters = mockk<WorkerParameters> {
            every { inputData } returns workRequest.workSpec.input
            every { runAttemptCount } returns 0
        }

        return DraftsActionsWorker(
            appContext = context,
            params = workerParameters,
            mailboxController = mailboxController,
            mainApplication = mockk(relaxed = true),
            notificationManagerCompat = mockk(relaxed = true),
            notificationUtils = mockk(relaxed = true),
            ioDispatcher = Dispatchers.IO,
            mailboxInfoRealm = mockk(relaxed = true),
        )
    }

    private fun createDraft(subject: String) = Draft().apply {
        this.subject = subject
        body = "body"
        action = DraftAction.SAVE
    }

    companion object {
        private const val USER_ID = 42
        private const val TARGETED_MAILBOX_ID = 1
        private const val CURRENT_MAILBOX_ID = 2
        private const val TARGETED_MAILBOX_UUID = "targetedMailboxUuid"
        private const val TARGETED_MAILBOX_DRAFT_SUBJECT = "Draft of the targeted Mailbox"
        private const val CURRENT_MAILBOX_DRAFT_SUBJECT = "Draft of the current Mailbox"
    }
}
