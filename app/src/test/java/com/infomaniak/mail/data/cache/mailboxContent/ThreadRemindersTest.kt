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

import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.ThreadRecomputations.recomputeThread
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.defaultRefreshStrategy
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.FolderRole
import com.infomaniak.mail.data.models.InternalModelProperties
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.extensions.ReminderAudience
import com.infomaniak.mail.data.models.extensions.ThreadReminders
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.message.ReminderMessageInfo
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.dataset.DummyMailboxContent
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.MessageDiffCallback
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.NotifyType
import com.infomaniak.mail.ui.main.thread.models.MessageUi
import com.infomaniak.mail.ui.main.thread.models.MessageUi.UnsubscribeState
import com.infomaniak.mail.utils.ThreadListUtils
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadRemindersTest {

    @Test
    fun showsReminderOnlyToTheCreatorAndDirectRecipients() {
        val received = reminder(10, FIRST, SECOND).apply {
            cc.add(recipient(COPY))
            bcc.add(recipient(BLIND_COPY))
        }
        val sent = reminder(10, FIRST, SECOND).apply { reminder?.uuid = "reminder-id" }

        assertEquals(ReminderAudience.Sender, ThreadReminders(listOf(sent), SENDER, null).audienceFor(sent))
        assertEquals(ReminderAudience.Recipient, ThreadReminders(listOf(received), FIRST, null).audienceFor(received))
        assertTrue(ThreadListUtils.hasReminders(listOf(sent), SENDER, null))
        assertTrue(ThreadListUtils.hasReminders(listOf(received), FIRST, null))
        for (mailbox in listOf(COPY, BLIND_COPY)) {
            assertNull(ThreadReminders(listOf(received), mailbox, null).audienceFor(received))
            assertFalse(ThreadListUtils.hasReminders(listOf(received), mailbox, null))
        }
    }

    @Test
    fun onlyARecentSentMessageFromADirectRecipientClosesTheReminder() {
        val original = reminder(10, FIRST, SECOND)
        val messages = listOf(
            original,
            message(9, FIRST),
            message(10, FIRST),
            message(11, COPY),
            message(12, SENDER),
            message(13, FIRST).apply { isDraft = true },
            message(14, FIRST).apply { isScheduledDraft = true },
            message(15, FIRST).apply { isMessageWithSendDelay = true },
            message(16, FIRST).apply { isDeletedOnApi = true },
            message(17, FIRST).apply { isReminder = true },
        )

        assertTrue(ThreadListUtils.hasReminders(messages, FIRST, null))
        assertEquals(ReminderAudience.Recipient, ThreadReminders(messages, FIRST, null).audienceFor(original))

        val answered = messages + message(18, SECOND.uppercase())
        assertNull(ThreadReminders(answered, FIRST, null).audienceFor(original))
        assertFalse(ThreadListUtils.hasReminders(answered, FIRST, null))
        assertFalse(ThreadListUtils.hasReminders(answered, SECOND, null))
        original.reminder?.uuid = "reminder-id"
        assertNull(ThreadReminders(answered, SENDER, null).audienceFor(original))
        assertFalse(ThreadListUtils.hasReminders(answered, SENDER, null))
    }

    @Test
    fun recognizesMailboxAliasesWithoutEquatingUnrelatedAddresses() {
        val original = reminder(10, "alternate@example.test")
        val messages = listOf(original, message(11, FIRST))

        assertFalse(ThreadListUtils.hasReminders(messages, FIRST, listOf("alternate@example.test")))
        assertTrue(ThreadListUtils.hasReminders(messages, "alternate@example.test", null))
    }

    @Test
    fun keepsListIconIfAnotherReminderStillApplies() {
        val firstReminder = reminder(10, FIRST)
        val anotherReminder = reminder(12, FIRST)
        val messages = listOf(firstReminder, message(11, FIRST), anotherReminder)
        val reminders = ThreadReminders(messages, FIRST, null)

        assertNull(reminders.audienceFor(firstReminder))
        assertEquals(ReminderAudience.Recipient, reminders.audienceFor(anotherReminder))
        assertTrue(ThreadListUtils.hasReminders(messages, FIRST, null))
    }

    @Test
    fun keepsScheduledReminderAndPendingSenderIcon() {
        val scheduled = reminder(10, FIRST).apply { isScheduledDraft = true }
        assertEquals(ReminderAudience.ScheduledDraft, ThreadReminders(listOf(scheduled), SENDER, null).audienceFor(scheduled))

        val pending = reminder(10, FIRST).apply {
            reminder = null
            shouldRefreshReminder = true
        }
        assertTrue(ThreadListUtils.hasReminders(listOf(pending), SENDER, null))
        assertFalse(ThreadListUtils.hasReminders(listOf(pending, message(11, FIRST)), SENDER, null))
    }

    @Test
    fun removingTheReminderRebindsItsMessageEvenWithAnotherUiChange() {
        val original = MessageUi(
            message = reminder(10, FIRST),
            reminderAudience = ReminderAudience.Recipient,
            emojiReactionsState = emptyMap(),
            isReactionsFeatureAvailable = false,
            unsubscribeState = null,
            acknowledgeState = null,
        )
        val answered = original.copy(reminderAudience = null)
        val callback = MessageDiffCallback()

        assertFalse(callback.areContentsTheSame(original, answered))
        assertEquals(NotifyType.ReminderRebind, callback.getChangePayload(original, answered))
        assertNull(callback.getChangePayload(original, answered.copy(unsubscribeState = UnsubscribeState.InProgress)))
    }

    @OptIn(InternalModelProperties::class)
    @Test
    fun syncingSentReplyUpdatesTheAlreadyObservedInboxThread() = runBlocking {
        val mailboxContent = DummyMailboxContent()
        val realm = mailboxContent()
        try {
            val original = reminder(10, FIRST, SECOND).apply {
                uid = "1@inbox"
                folderId = "inbox"
                messageId = "<original@example.test>"
                messageIds.addAll(computeMessageIds())
            }
            val inboxThread = original.toThread().apply { recomputeThread(aliases = null, mailboxEmail = FIRST) }
            realm.write {
                copyToRealm(Folder().apply {
                    id = "inbox"
                    _role = FolderRole.INBOX.name
                    threads.add(inboxThread)
                })
                copyToRealm(Folder().apply {
                    id = "drafts"
                    _role = FolderRole.DRAFT.name
                })
                copyToRealm(Folder().apply {
                    id = "sent"
                    _role = FolderRole.SENT.name
                })
            }

            val updates = Channel<Thread>(Channel.UNLIMITED)
            val observer = launch {
                ThreadController(mailboxContent).getThreadAsync(inboxThread.uid)
                    .map { it.obj }
                    .filterNotNull()
                    .collect { updates.send(it) }
            }

            try {
                assertTrue(withTimeout(5_000) { updates.receive() }.hasReminders)

                fun reply(time: Long, folder: String) = message(time, FIRST).apply {
                    uid = "2@$folder"
                    folderId = folder
                    messageId = "<reply@example.test>"
                    inReplyTo = "<original@example.test>"
                    messageIds.addAll(computeMessageIds())
                }

                suspend fun syncMessage(message: Message) {
                    realm.write {
                        val impactedThreads = mutableSetOf<Thread>()
                        defaultRefreshStrategy.handleAddedMessage(this@runBlocking, message, true, impactedThreads, this)
                        impactedThreads.forEach { it.recomputeThread(realm = this, aliases = null, mailboxEmail = FIRST) }
                    }
                }

                syncMessage(reply(11, "drafts").apply {
                    isDraft = true
                    isMessageWithSendDelay = true
                })
                val threadWithPendingReply = withTimeout(5_000) { updates.receive() }
                assertEquals(2, threadWithPendingReply.messages.size)
                assertTrue(threadWithPendingReply.hasReminders)

                syncMessage(reply(12, "sent"))
                val updatedThread = withTimeout(5_000) { updates.receive() }
                assertEquals(2, updatedThread.messages.size)
                assertFalse(updatedThread.hasReminders)
                val updatedOriginal = updatedThread.messages.first()
                assertTrue(updatedOriginal.reminder != null)
                assertNull(ThreadReminders(updatedThread.messages, FIRST, null).audienceFor(updatedOriginal))
            } finally {
                observer.cancel()
            }
        } finally {
            realm.close()
        }
    }

    private fun recipient(address: String) = Recipient.createValidRecipient(address)

    private fun message(time: Long, sender: String): Message {
        val date = Json.encodeToString(RealmInstantSerializer, RealmInstant.from(time, 0))
        return Json.decodeFromString<Message>("""{"internal_date":$date}""").apply {
            from.add(recipient(sender))
        }
    }

    private fun reminder(time: Long, vararg recipients: String) = message(time, SENDER).apply {
        to.addAll(recipients.map(::recipient))
        reminder = ReminderMessageInfo().apply { date = RealmInstant.from(time + 100, 0) }
    }

    private companion object {
        const val SENDER = "sender@example.test"
        const val FIRST = "first@example.test"
        const val SECOND = "second@example.test"
        const val COPY = "copy@example.test"
        const val BLIND_COPY = "blind-copy@example.test"
    }
}
