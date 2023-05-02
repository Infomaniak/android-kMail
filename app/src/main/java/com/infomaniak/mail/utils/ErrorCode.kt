/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import com.infomaniak.lib.core.utils.ApiErrorCode
import com.infomaniak.mail.R

@Suppress("MemberVisibilityCanBePrivate")
object ErrorCode {

    // TODO
    const val ACCESS_DENIED = "access_denied"
    const val VALIDATION_FAILED = "validation_failed"

    // Global
    const val MAILBOX_LOCKED = "mailbox_locked"

    // Folder
    const val FOLDER_ALREADY_EXISTS = "folder__destination_already_exists"
    const val FOLDER_DOES_NOT_EXIST = "folder__not_exists"

    // Draft
    const val DRAFT_DOES_NOT_EXIST = "draft__not_found"
    const val DRAFT_MESSAGE_NOT_FOUND = "draft__message_not_found"
    const val DRAFT_HAS_TOO_MANY_RECIPIENTS = "draft__to_many_recipients"
    const val DRAFT_NEED_AT_LEAST_ONE_RECIPIENT = "draft__need_at_least_one_recipient_to_be_sent"
    const val DRAFT_ALREADY_SCHEDULED_OR_SENT = "draft__cannot_modify_scheduled_or_already_sent_message"

    // Identity
    const val IDENTITY_NOT_FOUND = "identity__not_found"

    // Send
    const val SEND_RECIPIENTS_REFUSED = "send__server_refused_all_recipients"
    const val SEND_LIMIT_EXCEEDED = "send__server_rate_limit_exceeded"

    val apiErrorCodes = listOf(
        // ApiErrorCode(ACCESS_DENIED, R.string.),
        // ApiErrorCode(VALIDATION_FAILED, R.string.),
        ApiErrorCode(MAILBOX_LOCKED, R.string.errorMailboxLocked),
        ApiErrorCode(FOLDER_ALREADY_EXISTS, R.string.errorNewFolderAlreadyExists),
        // ApiErrorCode(FOLDER_DOES_NOT_EXIST, R.string.),
        // ApiErrorCode(DRAFT_DOES_NOT_EXIST, R.string.),
        // ApiErrorCode(DRAFT_MESSAGE_NOT_FOUND, R.string.),
        ApiErrorCode(DRAFT_HAS_TOO_MANY_RECIPIENTS, R.string.errorTooManyRecipients),
        ApiErrorCode(DRAFT_NEED_AT_LEAST_ONE_RECIPIENT, R.string.errorAtLeastOneRecipient),
        ApiErrorCode(DRAFT_ALREADY_SCHEDULED_OR_SENT, R.string.errorEditScheduledMessage),
        // ApiErrorCode(IDENTITY_NOT_FOUND, R.string.),
        ApiErrorCode(SEND_RECIPIENTS_REFUSED, R.string.errorRefusedRecipients),
        ApiErrorCode(SEND_LIMIT_EXCEEDED, R.string.errorSendLimitExceeded),
    )

    fun getTranslateRes(code: String) = apiErrorCodes.first { it.code == code }.translateRes
}
