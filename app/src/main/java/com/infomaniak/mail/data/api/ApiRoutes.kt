/*
 * Infomaniak ikMail - Android
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

import com.infomaniak.mail.BuildConfig.INFOMANIAK_API_V1
import com.infomaniak.mail.BuildConfig.MAIL_API
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.PaginationInfo
import com.infomaniak.mail.utils.Utils

object ApiRoutes {

    fun resource(resource: String) = "$MAIL_API$resource"

    fun ping() = "$MAIL_API/api/ping-with-auth"

    fun addressBooks() = "$MAIL_API/api/pim/addressbook"

    fun contact() = "$MAIL_API/api/pim/contact"

    fun contacts() = "${contact()}/all?with=emails,details,others,contacted_times&filters=has_email"

    fun mailbox() = "$MAIL_API/api/mailbox?with=unseen"

    fun permissions(linkId: Int, hostingId: Int): String {
        return "$MAIL_API/api/mailbox/permissions?user_mailbox_id=$linkId&product_id=$hostingId"
    }

    private fun apiMailbox(mailboxHostingId: Int, mailboxName: String): String {
        return "$INFOMANIAK_API_V1/mail_hostings/$mailboxHostingId/mailboxes/$mailboxName"
    }

    fun signatures(mailboxHostingId: Int, mailboxName: String) = "${apiMailbox(mailboxHostingId, mailboxName)}/signatures"

    fun signature(mailboxHostingId: Int, mailboxName: String, signatureId: Int): String {
        return "${signatures(mailboxHostingId, mailboxName)}/$signatureId"
    }

    fun manageMailboxes() = "$MAIL_API/api/securedProxy/profile/workspace/mailbox"

    fun manageMailbox(mailboxId: Int) = "${manageMailboxes()}/$mailboxId"

    fun updateMailboxPassword(mailboxId: Int): String {
        return "$MAIL_API/api/securedProxy/cache/invalidation/profile/workspace/mailbox/$mailboxId/update_password"
    }

    fun requestMailboxPassword(mailboxHostingId: Int, mailboxName: String): String {
        return "${apiMailbox(mailboxHostingId, mailboxName)}/ask_password"
    }

    fun backups(mailboxHostingId: Int, mailboxName: String) = "${apiMailbox(mailboxHostingId, mailboxName)}/backups"

    fun folders(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/folder"

    private fun folder(mailboxUuid: String, folderId: String) = "${folders(mailboxUuid)}/$folderId"

    fun flushFolder(mailboxUuid: String, folderId: String) = "${folder(mailboxUuid, folderId)}/flush"

    fun quotas(mailboxHostingId: Int, mailboxName: String): String {
        return "$MAIL_API/api/mailbox/quotas?mailbox=$mailboxName&product_id=$mailboxHostingId"
    }

    private fun messages(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/message"

    private fun message(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${folder(mailboxUuid, folderId)}/message/$shortUid"
    }

    fun moveMessages(mailboxUuid: String) = "${messages(mailboxUuid)}/move"

    fun deleteMessages(mailboxUuid: String) = "${messages(mailboxUuid)}/delete"

    fun messagesSeen(mailboxUuid: String) = "${messages(mailboxUuid)}/seen"

    fun messagesUnseen(mailboxUuid: String) = "${messages(mailboxUuid)}/unseen"

    fun starMessages(mailboxUuid: String, star: Boolean): String = "${messages(mailboxUuid)}/${if (star) "star" else "unstar"}"

    fun draft(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/draft"

    fun draft(mailboxUuid: String, remoteDraftUuid: String) = "${draft(mailboxUuid)}/$remoteDraftUuid"

    fun createAttachment(mailboxUuid: String) = "${draft(mailboxUuid)}/attachment"

    fun downloadAttachments(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${message(mailboxUuid, folderId, shortUid)}/attachmentsArchive"
    }

    fun attachmentToForward(mailboxUuid: String) = "${draft(mailboxUuid)}/attachmentsToForward"

    fun search(mailboxUuid: String, folderId: String, filters: String): String {
        return "${folder(mailboxUuid, folderId)}/message?thread=on&offset=0&$filters"
    }

    fun reportPhishing(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${message(mailboxUuid, folderId, shortUid)}/report"
    }

    fun blockUser(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${message(mailboxUuid, folderId, shortUid)}/blacklist"
    }

    fun getMessagesUids(mailboxUuid: String, folderId: String, info: PaginationInfo?): String {
        val endpoint = "${getMessages(mailboxUuid, folderId)}/messages-uids"
        val messages = "?messages=${Utils.PAGE_SIZE}"
        val offset = info?.offsetUid?.let { "&uid_offset=$it" } ?: ""
        val direction = info?.direction?.let { "&direction=$it" } ?: ""
        return "${endpoint}${messages}${offset}${direction}"
    }

    fun getMessagesUidsDelta(mailboxUuid: String, folderId: String, cursor: String): String {
        return "${getMessages(mailboxUuid, folderId)}/activities?signature=${cursor}"
    }

    fun getMessagesByUids(mailboxUuid: String, folderId: String, uids: List<Int>): String {
        return "${getMessages(mailboxUuid, folderId)}/messages?uids=${uids.joinToString(",")}"
    }

    private fun getMessages(mailboxUuid: String, folderId: String): String {
        return "$MAIL_API/api/mail/${mailboxUuid}/folder/${folderId}/mobile"
    }
}
