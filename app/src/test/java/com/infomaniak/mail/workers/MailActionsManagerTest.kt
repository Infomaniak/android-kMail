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
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.ApiResponseStatus
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.SendDraftResult
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.NotificationUtils
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

    private var draftController: DraftController? = null
    private var mailboxContent: RealmDatabase.MailboxContent? = null
    private val mailboxContentRealm: Realm?
        get() = mailboxContent?.invoke()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkObject(ApiRepository)
        coEvery { ApiRepository.sendDraft(any(), any(), any()) } coAnswers {
            ApiResponse(
                result = ApiResponseStatus.SUCCESS,
                data = SendDraftResult(scheduledMessageEtop = "2025-08-27T10:45:48+0200")
            )
        }

        mockkObject(LocalStorageUtils)
        val mock = spyk<LocalStorageUtils>(recordPrivateCalls = true)
        every { mock["getDraftUploadDir"](any<Context>(), any<String>(), any<Int>(), any<Int>()) } returns File("")
        every { LocalStorageUtils.deleteDraftUploadDir(any(), any(), any(), any(), any()) } returns Unit

        every { coroutineWorker.applicationContext } returns context

        InfomaniakCore.appVersionName = "0.0.0"
        InfomaniakCore.appId = "0"

        AccountUtils.currentUserId = 0
        AccountUtils.currentMailboxId = 0
        AccountUtils.loadInMemory = true

        mailboxContent = RealmDatabase.MailboxContent()
        draftController = DraftController(mailboxContent!!)
    }

    @Test
    fun `test mail actions manager`() = runTest {
        mailboxContentRealm?.let { realm ->
            realm.write {
                launch {
                    val draft = Draft().apply {
                        action = Draft.DraftAction.SEND
                        body = "This is a test email."
                        subject = "This is a test subject."
                        to = realmListOf(
                            Recipient().apply {
                                email = "test@example.com"
                                name = "Test"
                            }
                        )
                        cc = realmListOf()
                        bcc = realmListOf()
                    }
                    draftController?.upsertDraft(draft)

                    assert(draftController?.getAllDrafts(realm)?.count() == 1) { "We should have one draft" }

                    val mailActionsManager = MailActionsManager(
                        mailboxContentRealm = mailboxContentRealm!!,
                        userId = 0,
                        mailboxId = 0,
                        mailbox = Mailbox(),
                        isSnackbarFeedbackNeeded = false,
                        draftLocalUuid = "",
                        draftController = draftController!!,
                        okHttpClient = okHttpClient,
                        coroutineWorker = coroutineWorker,
                        isAppInBackground = { false },
                        notificationUtils = notificationUtils,
                        notificationManagerCompat = notificationManagerCompat,
                    )

                    mailActionsManager.handleDraftsActions()

                    assert(draftController?.getAllDrafts(realm)?.count() == 0) { "Drafts should be empty" }
                }
            }
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkObject(ApiRepository)
        unmockkObject(LocalStorageUtils)
    }
}
