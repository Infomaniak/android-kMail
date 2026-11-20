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
package com.infomaniak.mail.data.models.extensions

import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.extensions.getStartAndEndOfPlusEmail
import io.realm.kotlin.types.RealmInstant

enum class ReminderAudience { Sender, Recipient, ScheduledDraft }

internal class ThreadReminders(messages: List<Message>, mailboxEmail: String?, aliases: List<String>?) {

    private val mailboxAddresses = (aliases.orEmpty() + listOfNotNull(mailboxEmail))
        .mapNotNull { it.normalizedAddress() }
        .toSet()
    private val plusAddressPatterns = mailboxAddresses.map { it.getStartAndEndOfPlusEmail() }

    private val latestSendByAddress = buildMap<String, RealmInstant> {
        messages.forEach { message ->
            if (message.isDraft || message.isScheduledDraft || message.isMessageWithSendDelay || message.isDeletedOnApi ||
                message.isReaction || message.isReminder
            ) return@forEach

            val address = message.sender?.email?.addressKey() ?: return@forEach
            val previousDate = get(address)
            if (previousDate == null || message.internalDate > previousDate) put(address, message.internalDate)
        }
    }

    fun audienceFor(message: Message): ReminderAudience? {
        val reminder = message.reminder ?: return null

        if (message.isHiddenReminder || message.isDeletedOnApi) return null

        if (message.isScheduledDraft) return ReminderAudience.ScheduledDraft

        if (isAnswered(message)) return null

        return when {
            reminder.uuid != null -> ReminderAudience.Sender
            message.to.any { it.email.isMailboxAddress() } -> ReminderAudience.Recipient
            else -> null
        }
    }

    fun hasVisibleReminder(message: Message): Boolean {
        if (message.reminder != null) return audienceFor(message) != null

        // The server's pending flag can identify a sent reminder before its details have been downloaded.
        return message.shouldRefreshReminder && !message.isHiddenReminder && !message.isDeletedOnApi &&
                message.sender?.email?.isMailboxAddress() == true && !isAnswered(message)
    }

    private fun isAnswered(message: Message): Boolean = message.to.any { recipient ->
        val lastSent = recipient.email.addressKey()?.let(latestSendByAddress::get)
        lastSent != null && lastSent > message.internalDate
    }

    private fun String.addressKey(): String? {
        val address = normalizedAddress() ?: return null

        return if (address.isMailboxAddress()) MAILBOX_ADDRESS_KEY else address
    }

    private fun String.isMailboxAddress(): Boolean {
        val address = normalizedAddress() ?: return false

        return address in mailboxAddresses || plusAddressPatterns.any { (start, end) ->
            address.startsWith(start) && address.endsWith(end)
        }
    }

    private fun String.normalizedAddress(): String? = trim().lowercase().takeIf { it.isNotEmpty() }

    private companion object {
        const val MAILBOX_ADDRESS_KEY = ""
    }
}
