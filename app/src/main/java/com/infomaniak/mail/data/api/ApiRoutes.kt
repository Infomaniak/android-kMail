/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.data.api

import com.infomaniak.mail.BuildConfig.MAIL_API

object ApiRoutes {

    private const val MAX_NUMBER_OF_MESSAGES_TO_FETCH: Int = 500

    fun resource(resource: String) = "$MAIL_API$resource"

    fun addressBooks() = "$MAIL_API/api/pim/addressbook"

    fun contact() = "$MAIL_API/api/pim/contact"

    fun contacts() = "${contact()}/all?with=emails,details,others,contacted_times"

    fun signatures(mailboxHostingId: Int, mailboxName: String): String {
        return "$MAIL_API/api/securedProxy/1/mail_hostings/$mailboxHostingId/mailboxes/$mailboxName/signatures"
    }

    fun mailbox() = "$MAIL_API/api/mailbox"

    fun folders(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/folder"

    private fun folder(mailboxUuid: String, folderId: String) = "${folders(mailboxUuid)}/$folderId"

    fun flushFolder(mailboxUuid: String, folderId: String) = "${folder(mailboxUuid, folderId)}/flush"

    fun quotas(mailboxHostingId: Int, mailboxName: String): String {
        return "$MAIL_API/api/mailbox/quotas?mailbox=$mailboxName&product_id=$mailboxHostingId"
    }

    private fun messages(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/message"

    private fun message(mailboxUuid: String, folderId: String, shortUid: String): String {
        return "${folder(mailboxUuid, folderId)}/message/$shortUid"
    }

    fun moveMessages(mailboxUuid: String) = "${messages(mailboxUuid)}/move"

    fun deleteMessages(mailboxUuid: String) = "${messages(mailboxUuid)}/delete"

    fun messagesSeen(mailboxUuid: String) = "${messages(mailboxUuid)}/seen"

    fun messagesUnseen(mailboxUuid: String) = "${messages(mailboxUuid)}/unseen"

    fun starMessages(mailboxUuid: String, star: Boolean): String = "${messages(mailboxUuid)}/${if (star) "star" else "unstar"}"

    fun draft(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/draft"

    fun draft(mailboxUuid: String, draftRemoteUuid: String) = "${draft(mailboxUuid)}/$draftRemoteUuid"

    fun createAttachment(mailboxUuid: String) = "${draft(mailboxUuid)}/attachment"

    fun downloadAttachments(mailboxUuid: String, folderId: String, shortUid: String): String {
        return "${message(mailboxUuid, folderId, shortUid)}/attachmentsArchive"
    }

    fun attachmentToForward(mailboxUuid: String) = "${draft(mailboxUuid)}/attachmentsToForward"

    fun search(mailboxUuid: String, folderId: String, filters: String): String {
        return "${folder(mailboxUuid, folderId)}/message?thread=on&offset=0&$filters"
    }

    fun reportPhishing(mailboxUuid: String, folderId: String, shortUid: String): String {
        return "${message(mailboxUuid, folderId, shortUid)}/report"
    }

    fun blockUser(mailboxUuid: String, folderId: String, shortUid: String): String {
        return "${message(mailboxUuid, folderId, shortUid)}/blacklist"
    }

    fun getMessagesUids(mailboxUuid: String, folderId: String): String {
        return "${getMessages(mailboxUuid, folderId)}/messages_uids?messages=${MAX_NUMBER_OF_MESSAGES_TO_FETCH}"
    }

    fun getMessagesUidsDelta(mailboxUuid: String, folderId: String, cursor: String): String {
        return "${getMessages(mailboxUuid, folderId)}/activities?signature=${cursor}"
    }

    fun getMessagesByUids(mailboxUuid: String, folderId: String, messagesUids: List<String>): String {
        return "${getMessages(mailboxUuid, folderId)}/messages?uids=${messagesUids.joinToString(",")}"
    }

    private fun getMessages(mailboxUuid: String, folderId: String): String {
        return "$MAIL_API/api/mail/${mailboxUuid}/folder/${folderId}/mobile"
    }
}
