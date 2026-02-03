/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import com.infomaniak.core.legacy.InfomaniakCore
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.AttachmentUploadStatus
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.SendDraftResult
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.dataset.DummyMailboxContent
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.extensions.AttachmentExt.startUpload
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class MailActionsManagerTest {

    private val context = mockk<Context>()
    private val notificationUtils = mockk<NotificationUtils>()
    private val notificationManagerCompat = mockk<NotificationManagerCompat>()
    private val okHttpClient = mockk<OkHttpClient>()
    private val coroutineWorker = mockk<CoroutineWorker>()

    private var draftController = DraftController(mailboxContentRealm)
    private val mailboxInfoRealm: Realm by lazy { RealmDatabase.mailboxInfo }

    @Before
    fun setup() {
        mockLogFunctions()
        mockApiRepository()
        mockLocalStorageUtils()

        every { coroutineWorker.applicationContext } returns context

        initializeConstants()
    }

    @Test
    fun `test sending email`() = runTest {
        launch {
            draftController.upsertDraft(getDraft())
            assert(draftController.getAllDrafts(mailboxContentRealm()).count() == 1) { "We should have one draft" }
            getMailActionManager(mailboxContentRealm()).handleDraftsActions()
            assert(draftController.getAllDrafts(mailboxContentRealm()).count() == 0) { "Drafts should be empty" }
        }
    }

    @Test
    fun `test attachments`() = runTest {
        launch {
            val file = mockk<File>()
            every { file.exists() } answers { true }

            val mockedAttachment = spyk(getAttachment())
            every { mockedAttachment.getUploadLocalFile() } answers { file }

            mockkObject(AccountUtils)
            coEvery { AccountUtils.getUserById(any())?.apiToken?.accessToken } answers { "token" }

            mockApiRepository()

            draftController.upsertDraft(getDraft(realmListOf(mockedAttachment)))

            val storedAttachment = draftController.getDraft(DRAFT_LOCAL_UUID)?.attachments?.first()
            assert(storedAttachment?.uuid?.isEmpty() == true)
            assert(storedAttachment?.attachmentUploadStatus == AttachmentUploadStatus.NOT_UPLOADED)

            mockedAttachment.startUpload(DRAFT_LOCAL_UUID, Mailbox(), mailboxContentRealm())

            val updatedStoredAttachment = draftController.getDraft(DRAFT_LOCAL_UUID)?.attachments?.first()
            assert(updatedStoredAttachment?.uuid == ATTACHMENT_REMOTE_UUID)
            assert(updatedStoredAttachment?.attachmentUploadStatus == AttachmentUploadStatus.UPLOADED)
        }
    }

    @After
    fun tearDown() {
        mailboxInfoRealm.close()
        unmockkStatic(Log::class)
        unmockkObject(ApiRepository)
        unmockkObject(LocalStorageUtils)
        unmockkObject(AccountUtils)
    }

    private fun mockLogFunctions() {
        mailboxContentRealm().close()
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    private fun mockApiRepository() {
        mockkObject(ApiRepository)
        coEvery { ApiRepository.sendDraft(any(), any(), any()) } coAnswers {
            ApiResponse(
                result = ApiResponseStatus.SUCCESS,
                data = SendDraftResult(scheduledMessageEtop = "2025-08-27T10:45:48+0200"),
            )
        }
        coEvery { ApiRepository.createAttachments(any(), any(), any(), any()) } coAnswers {
            ApiResponse(
                result = ApiResponseStatus.SUCCESS,
                data = getAttachment(remoteUUID = ATTACHMENT_REMOTE_UUID),
            )
        }
    }

    private fun mockLocalStorageUtils() {
        mockkObject(LocalStorageUtils)
        val mock = spyk<LocalStorageUtils>(recordPrivateCalls = true)
        every { mock["getDraftUploadDir"](any<Context>(), any<String>(), any<Int>(), any<Int>()) } returns File("")
        every { LocalStorageUtils.deleteDraftUploadDir(any(), any(), any(), any(), any()) } returns Unit
    }

    private fun initializeConstants() {
        InfomaniakCore.appVersionName = "0.0.0"
        InfomaniakCore.appId = "0"
    }

    private fun getMailActionManager(mailboxContentRealm: Realm?): MailActionsManager {
        return MailActionsManager(
            mailboxContentRealm = mailboxContentRealm!!,
            mailboxInfoRealm = mailboxInfoRealm,
            userId = 0,
            mailboxId = 0,
            mailbox = Mailbox(),
            isSnackbarFeedbackNeeded = false,
            draftLocalUuid = "",
            draftController = draftController,
            okHttpClient = okHttpClient,
            coroutineWorker = coroutineWorker,
            isAppInBackground = { false },
            notificationUtils = notificationUtils,
            notificationManagerCompat = notificationManagerCompat,
        )
    }

    private fun getDraft(attachments: RealmList<Attachment> = realmListOf()): Draft {
        return Draft().apply {
            localUuid = DRAFT_LOCAL_UUID
            action = DraftAction.SEND
            body = "This is a test email."
            subject = "This is a test subject."
            to = realmListOf(
                Recipient().apply {
                    email = "test@example.com"
                    name = "Test"
                },
            )
            cc = realmListOf()
            bcc = realmListOf()
            this.attachments = attachments
        }
    }

    private fun getAttachment(remoteUUID: String = ""): Attachment = Attachment().initLocalValues(
        name = "attachment",
        size = 1024L,
        mimeType = "image/png",
        uri = "file://path/to/attachment",
    ).apply {
        localUuid = ATTACHMENT_LOCAL_UUID
        uuid = remoteUUID
    }

    companion object {
        private const val DRAFT_LOCAL_UUID = "draft-local-uuid"
        private const val ATTACHMENT_LOCAL_UUID = "attachment-local-uuid"
        private const val ATTACHMENT_REMOTE_UUID = "attachment-remote-uuid"

        val mailboxContentRealm = DummyMailboxContent()
    }
}
