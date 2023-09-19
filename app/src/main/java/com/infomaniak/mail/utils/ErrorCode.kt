/*
 * Infomaniak Mail - Android
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
import com.infomaniak.lib.core.R as RCore

@Suppress("MemberVisibilityCanBePrivate")
object ErrorCode {

    //region Global
    const val INVALID_CREDENTIALS = "invalid_credentials"

    // Untranslated (Do not translate, we don't want to show this to the user)
    const val IDENTITY_NOT_FOUND = "identity__not_found"
    const val MESSAGE_NOT_FOUND = "mail__message_not_found"
    const val MESSAGE_ATTACHMENT_NOT_FOUND = "mail__message_attachment_not_found"
    const val VALIDATION_FAILED = "validation_failed"
    //endregion

    //region Mailbox
    const val MAILBOX_LOCKED = "mailbox_locked"
    const val ERROR_WHILE_LINKING_MAILBOX = "error_while_linking_mailbox"
    const val INVALID_MAILBOX_PASSWORD = "invalid_mailbox_password"
    //endregion

    //region Folder
    const val FOLDER_ALREADY_EXISTS = "folder__destination_already_exists"
    const val FOLDER_DOES_NOT_EXIST = "folder__not_exists"
    const val FOLDER_NAME_TOO_LONG = "folder__name_too_long"
    const val FOLDER_UNABLE_TO_CREATE = "folder__unable_to_create"
    const val FOLDER_UNABLE_TO_FLUSH = "folder__unable_to_flush"
    //endregion

    //region Draft
    const val DRAFT_ATTACHMENT_NOT_FOUND = "draft__attachment_not_found"
    const val DRAFT_DOES_NOT_EXIST = "draft__not_found"
    const val DRAFT_MESSAGE_NOT_FOUND = "draft__message_not_found"
    const val DRAFT_HAS_TOO_MANY_RECIPIENTS = "draft__to_many_recipients"
    const val DRAFT_MAX_ATTACHMENTS_SIZE_REACHED = "draft__max_attachments_size_reached"
    const val DRAFT_NEED_AT_LEAST_ONE_RECIPIENT = "draft__need_at_least_one_recipient_to_be_sent"
    const val DRAFT_ALREADY_SCHEDULED_OR_SENT = "draft__cannot_modify_scheduled_or_already_sent_message"

    // Untranslated (Do not translate, we don't want to show this to the user)
    const val DRAFT_CANNOT_CANCEL_NON_SCHEDULED_MESSAGE = "draft__cannot_cancel_non_scheduled_message"
    const val DRAFT_CANNOT_FORWARD_MORE_THAN_ONE_MESSAGE_INLINE = "draft__cannot_forward_more_than_one_message_inline"
    const val DRAFT_CANNOT_MOVE_SCHEDULED_MESSAGE = "draft__cannot_move_scheduled_message"
    //endregion

    //region Send
    const val SEND_FROM_REFUSED = "send__server_refused_from"
    const val SEND_RECIPIENTS_REFUSED = "send__server_refused_all_recipients"
    const val SEND_LIMIT_EXCEEDED = "send__server_rate_limit_exceeded"
    //endregion

    //region Attachments
    const val ATTACHMENT_NOT_VALID = "attachment__not_valid"
    const val ATTACHMENT_NOT_FOUND = "attachment__not_found"

    // Untranslated (Do not translate, we don't want to show this to the user)
    const val ATTACHMENT_MISSING_FILENAME_MIME = "attachment__missing_filename_or_mimetype"
    const val ATTACHMENT_UPLOAD_INCORRECT = "attachment__incorrect_disposition"
    const val ATTACHMENT_UPLOAD_CONTENT_ID_NOT_VALID = "attachment__content_id_not_valid"
    const val ATTACHMENT_ADD_FROM_DRIVE_FAIL = "attachment__add_attachment_from_drive_fail"
    const val ATTACHMENT_STORE_TO_DRIVE_FAIL = "attachment__store_to_drive_fail"
    //endregion

    //region Action
    const val MOVE_DESTINATION_NOT_FOUND = "mail__move_destination_folder_not_found"
    //endregion

    //region AI
    const val MAX_TOKEN_REACHED = "max_token_reached"
    //endregion

    val apiErrorCodes = listOf(

        // Global
        ApiErrorCode(INVALID_CREDENTIALS, R.string.errorInvalidCredentials),

        // Mailbox
        ApiErrorCode(MAILBOX_LOCKED, R.string.errorMailboxLocked),
        ApiErrorCode(ERROR_WHILE_LINKING_MAILBOX, R.string.errorAlreadyLinkedMailbox),
        ApiErrorCode(INVALID_MAILBOX_PASSWORD, R.string.errorInvalidMailboxPassword),

        // Folder
        ApiErrorCode(FOLDER_ALREADY_EXISTS, R.string.errorNewFolderAlreadyExists),
        ApiErrorCode(FOLDER_DOES_NOT_EXIST, R.string.errorFolderDoesNotExist),
        ApiErrorCode(FOLDER_NAME_TOO_LONG, R.string.errorNewFolderNameTooLong),
        ApiErrorCode(FOLDER_UNABLE_TO_CREATE, R.string.errorUnableToCreateFolder),
        ApiErrorCode(FOLDER_UNABLE_TO_FLUSH, R.string.errorUnableToFlushFolder),

        // Drafts
        ApiErrorCode(DRAFT_ATTACHMENT_NOT_FOUND, R.string.errorAttachmentNotFound), // Should we show this technical info ?
        ApiErrorCode(DRAFT_DOES_NOT_EXIST, R.string.errorDraftNotFound), // Should we show this technical info to the user ?
        ApiErrorCode(DRAFT_MESSAGE_NOT_FOUND, R.string.errorDraftNotFound), // Should we show this technical info to the user ?
        ApiErrorCode(DRAFT_HAS_TOO_MANY_RECIPIENTS, R.string.errorTooManyRecipients), // Useless until we handle local drafts
        ApiErrorCode(DRAFT_MAX_ATTACHMENTS_SIZE_REACHED, R.string.attachmentFileLimitReached),
        ApiErrorCode(DRAFT_NEED_AT_LEAST_ONE_RECIPIENT, R.string.errorAtLeastOneRecipient), // Useless until local drafts
        ApiErrorCode(DRAFT_ALREADY_SCHEDULED_OR_SENT, R.string.errorEditScheduledMessage),

        // Send
        ApiErrorCode(SEND_FROM_REFUSED, R.string.errorRefusedSender), // Useless until we handle local drafts
        ApiErrorCode(SEND_RECIPIENTS_REFUSED, R.string.errorRefusedRecipients), // Useless until we handle local drafts
        ApiErrorCode(SEND_LIMIT_EXCEEDED, R.string.errorSendLimitExceeded),

        // Attachments
        ApiErrorCode(ATTACHMENT_NOT_VALID, R.string.errorInvalidAttachment),
        ApiErrorCode(ATTACHMENT_NOT_FOUND, R.string.errorAttachmentNotFound),

        // Action
        ApiErrorCode(MOVE_DESTINATION_NOT_FOUND, R.string.errorMoveDestinationNotFound),
    )

    private val ignoredErrorCodesForDrafts = setOf(
        DRAFT_ALREADY_SCHEDULED_OR_SENT,
    )

    fun getTranslateResForDrafts(code: String?): Int? {
        return if (ignoredErrorCodesForDrafts.contains(code)) {
            null
        } else {
            apiErrorCodes.firstOrNull { it.code == code }?.translateRes ?: RCore.string.anErrorHasOccurred
        }
    }
}
