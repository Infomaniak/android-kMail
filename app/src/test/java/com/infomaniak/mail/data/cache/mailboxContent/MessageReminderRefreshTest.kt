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
package com.infomaniak.mail.data.cache.mailboxContent

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.extensions.ReminderAudience
import com.infomaniak.mail.data.models.extensions.ThreadReminders
import com.infomaniak.mail.data.models.extensions.updateFlags
import com.infomaniak.mail.data.models.getMessages.DefaultMessageFlags
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.message.ReminderMessageInfo
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.dataset.DummyMailboxContent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageReminderRefreshTest {

    private val mailboxContent = DummyMailboxContent()
    private val messageController = MessageController(mailboxContent, mockk())
    private val mailbox = Mailbox()
    private lateinit var realm: Realm

    @Before
    fun setUp() {
        mockkObject(ApiRepository)
        realm = mailboxContent()
        realm.writeBlocking {
            val message = copyToRealm(messageWithReminder(100L).apply { areHeavyDataFetched = true })
            copyToRealm(Thread().apply {
                uid = THREAD_UID
                messages.add(message)
                hasReminders = true
            })
        }
    }

    @After
    fun tearDown() {
        realm.close()
        unmockkObject(ApiRepository)
    }

    @Test
    fun refreshesReminderWheneverMarkedStaleAndClearsPendingRefresh() = runTest {
        coEvery { ApiRepository.getMessage(MESSAGE_RESOURCE, any()) } returnsMany listOf(
            successResponse(200L),
            successResponse(300L),
        )
        messageController.refreshReminderIfNeeded(MESSAGE_UID, mailbox)
        coVerify(exactly = 0) { ApiRepository.getMessage(any(), any()) }

        markReminderForRefresh()
        messageController.refreshReminderIfNeeded(MESSAGE_UID, mailbox)
        assertReminderDate(200L)

        markReminderForRefresh()
        messageController.refreshReminderIfNeeded(MESSAGE_UID, mailbox)
        assertReminderDate(300L)
        coVerify(exactly = 2) { ApiRepository.getMessage(MESSAGE_RESOURCE, any()) }
    }

    @Test
    fun removesDirectRecipientsReminderAfterServerUpdateWithoutReceivingTheReply() = runTest {
        val recipientMailbox = Mailbox().apply { email = "recipient@example.test" }
        fun receivedMessage() = messageWithReminder(100L).apply {
            reminder?.uuid = null
            to.add(Recipient.createValidRecipient(recipientMailbox.email))
        }

        coEvery { ApiRepository.getMessage(MESSAGE_RESOURCE, any()) } returns ApiResponse(
            result = ApiResponseStatus.SUCCESS,
            data = receivedMessage().apply { reminder = null },
        )
        realm.writeBlocking {
            val message = MessageController.getMessageBlocking(MESSAGE_UID, this)!!
            message.reminder?.uuid = null
            message.to.add(Recipient.createValidRecipient(recipientMailbox.email))
            message.updateFlags(
                DefaultMessageFlags(
                    shortUid = "42",
                    isAnswered = false,
                    isFavorite = false,
                    isForwarded = false,
                    isMessageWithSendDelay = false,
                    isSeen = true,
                    isAcknowledged = false,
                    hasReminder = false,
                )
            )
        }
        val before = realm.query(Thread::class, "uid == $0", THREAD_UID).first().find()!!
        assertTrue(before.messages.single().shouldRefreshReminder)
        assertTrue(before.hasReminders)
        assertEquals(
            ReminderAudience.Recipient,
            ThreadReminders(before.messages, recipientMailbox.email, null).audienceFor(before.messages.single()),
        )

        messageController.refreshReminderIfNeeded(MESSAGE_UID, recipientMailbox)

        val after = realm.query(Thread::class, "uid == $0", THREAD_UID).first().find()!!
        val message = after.messages.single()
        assertNull(message.reminder)
        assertFalse(message.shouldRefreshReminder)
        assertFalse(after.hasReminders)
        assertNull(ThreadReminders(after.messages, recipientMailbox.email, null).audienceFor(message))
    }

    @Test
    fun repeatedRequestsDoNotDuplicateAnOngoingRefresh() = runTest {
        val response = CompletableDeferred<ApiResponse<Message>>()
        coEvery { ApiRepository.getMessage(MESSAGE_RESOURCE, any()) } coAnswers { response.await() }
        markReminderForRefresh()

        val firstRefresh = launch { messageController.refreshReminderIfNeeded(MESSAGE_UID, mailbox) }
        val secondRefresh = launch { messageController.refreshReminderIfNeeded(MESSAGE_UID, mailbox) }
        runCurrent()
        coVerify(exactly = 1) { ApiRepository.getMessage(MESSAGE_RESOURCE, any()) }

        response.complete(successResponse(200L))
        firstRefresh.join()
        secondRefresh.join()
        assertReminderDate(200L)
        coVerify(exactly = 1) { ApiRepository.getMessage(MESSAGE_RESOURCE, any()) }
    }

    @Test
    fun waitsForHeavyDataBeforeRefreshingReminder() = runTest {
        coEvery { ApiRepository.getMessage(MESSAGE_RESOURCE, any()) } returns successResponse(200L)
        realm.writeBlocking {
            MessageController.getMessageBlocking(MESSAGE_UID, this)!!.apply {
                shouldRefreshReminder = true
                areHeavyDataFetched = false
            }
        }
        messageController.refreshReminderIfNeeded(MESSAGE_UID, mailbox)
        coVerify(exactly = 0) { ApiRepository.getMessage(any(), any()) }

        realm.writeBlocking {
            MessageController.getMessageBlocking(MESSAGE_UID, this)!!.areHeavyDataFetched = true
        }
        messageController.refreshReminderIfNeeded(MESSAGE_UID, mailbox)
        assertReminderDate(200L)
        coVerify(exactly = 1) { ApiRepository.getMessage(MESSAGE_RESOURCE, any()) }
    }

    private fun markReminderForRefresh() {
        realm.writeBlocking {
            MessageController.getMessageBlocking(MESSAGE_UID, this)!!.shouldRefreshReminder = true
        }
    }

    private fun assertReminderDate(epochSeconds: Long) {
        val message = MessageController.getMessageBlocking(MESSAGE_UID, realm)!!
        assertEquals(epochSeconds, message.reminder?.date?.epochSeconds)
        assertFalse(message.shouldRefreshReminder)
    }

    private fun successResponse(epochSeconds: Long) = ApiResponse(
        result = ApiResponseStatus.SUCCESS,
        data = messageWithReminder(epochSeconds),
    )

    private fun messageWithReminder(epochSeconds: Long) = Message().apply {
        uid = MESSAGE_UID
        resource = MESSAGE_RESOURCE
        reminder = ReminderMessageInfo().apply {
            uuid = "reminder"
            date = RealmInstant.from(epochSeconds, 0)
        }
    }

    companion object {
        private const val MESSAGE_UID = "42@inbox"
        private const val THREAD_UID = "test-thread"
        private const val MESSAGE_RESOURCE = "/api/mail/test/folder/inbox/message/42"
    }
}
