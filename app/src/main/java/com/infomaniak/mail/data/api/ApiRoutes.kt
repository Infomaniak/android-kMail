/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

    //region API V1
    fun getCredentialsPassword(): String {
        return "$INFOMANIAK_API_V1/profile/password"
    }

    private fun mailboxV1(mailboxHostingId: Int, mailboxName: String): String {
        return "$INFOMANIAK_API_V1/mail_hostings/$mailboxHostingId/mailboxes/$mailboxName"
    }

    fun backups(mailboxHostingId: Int, mailboxName: String): String {
        return "${mailboxV1(mailboxHostingId, mailboxName)}/backups"
    }

    fun requestMailboxPassword(mailboxHostingId: Int, mailboxName: String): String {
        return "${mailboxV1(mailboxHostingId, mailboxName)}/ask_password"
    }

    fun signatures(mailboxHostingId: Int, mailboxName: String): String {
        return "${mailboxV1(mailboxHostingId, mailboxName)}/signatures"
    }

    fun signature(mailboxHostingId: Int, mailboxName: String, signatureId: Int): String {
        return "${signatures(mailboxHostingId, mailboxName)}/$signatureId"
    }
    //endregion

    //region Personal Information Manager
    private fun pim(): String {
        return "$MAIL_API/api/pim"
    }

    fun addressBooks(): String {
        return "${pim()}/addressbook"
    }

    fun contact(): String {
        return "${pim()}/contact"
    }

    fun contacts(): String {
        return "${contact()}/all?with=emails,details,others,contacted_times&filters=has_email"
    }
    //endregion

    //region Calendar
    fun infomaniakCalendarEventReply(calendarEventId: Int): String {
        return "${pim()}/event/$calendarEventId/reply"
    }

    fun calendarEvent(resource: String): String {
        return "${resource(resource)}?format=render"
    }

    fun icsCalendarEventReply(resource: String): String {
        return "${resource(resource)}/reply"
    }
    //endregion

    //region Secured Proxy
    private fun securedProxy(): String {
        return "$MAIL_API/api/securedProxy"
    }

    fun updateMailboxPassword(mailboxId: Int): String {
        return "${securedProxy()}/cache/invalidation/profile/workspace/mailbox/$mailboxId/update_password"
    }

    fun manageMailboxes(): String {
        return "${securedProxy()}/profile/workspace/mailbox"
    }

    fun manageMailbox(mailboxId: Int): String {
        return "${manageMailboxes()}/$mailboxId"
    }
    //endregion

    //region Mailbox
    private fun mailbox(): String {
        return "$MAIL_API/api/mailbox"
    }

    fun mailboxes(): String {
        return "${mailbox()}?with=aliases,external_mail_flag_enabled,unseen"
    }

    fun permissions(linkId: Int, mailboxHostingId: Int): String {
        return "${mailbox()}/permissions?user_mailbox_id=$linkId&product_id=$mailboxHostingId"
    }

    fun quotas(mailboxHostingId: Int, mailboxName: String): String {
        return "${mailbox()}/quotas?mailbox=$mailboxName&product_id=$mailboxHostingId"
    }
    //endregion

    //region Folder
    private fun mailMailbox(mailboxUuid: String): String {
        return "$MAIL_API/api/mail/$mailboxUuid"
    }

    fun folders(mailboxUuid: String): String {
        return "${mailMailbox(mailboxUuid)}/folder"
    }

    private fun folder(mailboxUuid: String, folderId: String): String {
        return "${folders(mailboxUuid)}/$folderId"
    }

    fun flushFolder(mailboxUuid: String, folderId: String): String {
        return "${folder(mailboxUuid, folderId)}/flush"
    }

    fun search(mailboxUuid: String, folderId: String, filters: String): String {
        return "${folder(mailboxUuid, folderId)}/message?thread=on&offset=0&$filters"
    }
    //endregion

    //region Messages from Folder/Message
    private fun message(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${folder(mailboxUuid, folderId)}/message/$shortUid"
    }

    fun downloadAttachments(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${message(mailboxUuid, folderId, shortUid)}/attachmentsArchive"
    }

    fun blockUser(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${message(mailboxUuid, folderId, shortUid)}/blacklist"
    }

    fun reportPhishing(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${message(mailboxUuid, folderId, shortUid)}/report"
    }
    //endregion

    //region Message from Folder/Mobile
    private fun getMessages(mailboxUuid: String, folderId: String): String {
        return "${folder(mailboxUuid, folderId)}/mobile"
    }

    fun getMessagesUidsDelta(mailboxUuid: String, folderId: String, cursor: String): String {
        return "${getMessages(mailboxUuid, folderId)}/activities?signature=$cursor"
    }

    fun getMessagesUids(mailboxUuid: String, folderId: String, info: PaginationInfo?): String {
        val endpoint = "${getMessages(mailboxUuid, folderId)}/messages-uids?messages=${Utils.PAGE_SIZE}"
        val offset = info?.offsetUid?.let { "&uid_offset=$it" } ?: ""
        val direction = info?.direction?.let { "&direction=$it" } ?: ""

        return "${endpoint}${offset}${direction}"
    }

    fun getMessagesByUids(mailboxUuid: String, folderId: String, uids: List<Int>): String {
        return "${getMessages(mailboxUuid, folderId)}/messages?uids=${uids.joinToString(",")}"
    }
    //endregion

    //region Message from Mailbox
    private fun messages(mailboxUuid: String): String {
        return "${mailMailbox(mailboxUuid)}/message"
    }

    fun deleteMessages(mailboxUuid: String): String {
        return "${messages(mailboxUuid)}/delete"
    }

    fun moveMessages(mailboxUuid: String): String {
        return "${messages(mailboxUuid)}/move"
    }

    fun messagesSeen(mailboxUuid: String): String {
        return "${messages(mailboxUuid)}/seen"
    }

    fun messagesUnseen(mailboxUuid: String): String {
        return "${messages(mailboxUuid)}/unseen"
    }

    fun starMessages(mailboxUuid: String, star: Boolean): String {
        return "${messages(mailboxUuid)}/${if (star) "star" else "unstar"}"
    }
    //endregion

    //region Draft from Mailbox
    fun draft(mailboxUuid: String): String {
        return "${mailMailbox(mailboxUuid)}/draft"
    }

    fun draft(mailboxUuid: String, remoteDraftUuid: String): String {
        return "${draft(mailboxUuid)}/$remoteDraftUuid"
    }

    fun createAttachment(mailboxUuid: String): String {
        return "${draft(mailboxUuid)}/attachment"
    }

    fun attachmentToForward(mailboxUuid: String): String {
        return "${draft(mailboxUuid)}/attachmentsToForward"
    }
    //endregion

    //region AI
    private fun ai(): String {
        return "$MAIL_API/api/ai"
    }

    fun aiConversation(mailboxUuid: String): String {
        return "${ai()}${mailboxUuidParameter(mailboxUuid)}"
    }

    fun aiShortcutWithContext(contextId: String, action: String, mailboxUuid: String): String {
        return "${ai()}/mobile/$contextId/${action}${mailboxUuidParameter(mailboxUuid)}"
    }

    fun aiShortcutNoContext(action: String, mailboxUuid: String): String {
        return "${ai()}/mobile/${action}${mailboxUuidParameter(mailboxUuid)}"
    }
    //endregion

    private fun mailboxUuidParameter(mailboxUuid: String): String {
        return "?mailbox_uuid=$mailboxUuid"
    }

    fun featureFlag(featureName: String, mailboxUuid: String): String {
        return "$MAIL_API/api/feature-flag/check/${featureName}${mailboxUuidParameter(mailboxUuid)}"
    }

    fun ping(): String {
        return "$MAIL_API/api/ping-with-auth"
    }

    fun resource(resource: String): String {
        return "$MAIL_API$resource"
    }
}
