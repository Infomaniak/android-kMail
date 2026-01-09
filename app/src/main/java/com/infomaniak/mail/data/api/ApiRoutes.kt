/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import com.infomaniak.core.network.INFOMANIAK_API_V1
import com.infomaniak.core.common.utils.FORMAT_ISO_8601_WITH_TIMEZONE_SEPARATOR
import com.infomaniak.core.common.utils.format
import com.infomaniak.mail.MAIL_API
import com.infomaniak.mail.utils.Utils
import java.net.URLEncoder
import java.util.Date

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

    fun signature(mailboxHostingId: Int, mailboxName: String): String {
        return "${signatures(mailboxHostingId, mailboxName)}/set_defaults"
    }
    //endregion

    //region Personal Information Manager
    private fun pim(): String {
        return "$MAIL_API/api/pim"
    }

    fun addressBooks(): String {
        return "${pim()}/addressbook?with=categories,account_name"
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

    fun mailboxInfo(mailboxHostingId: Int, mailboxName: String): String {
        return "${securedProxy()}/1/mail_hostings/$mailboxHostingId/mailboxes/$mailboxName"
    }

    fun getSendersRestrictions(mailboxHostingId: Int, mailboxName: String): String {
        return "${mailboxInfo(mailboxHostingId, mailboxName)}?with=authorized_senders,blocked_senders"
    }

    fun externalMailInfo(mailboxHostingId: Int, mailboxName: String): String {
        return "${securedProxy()}/1/mail_hostings/$mailboxHostingId/mailboxes/$mailboxName/external_mail_flag"
    }

    fun isInfomaniakMailboxes(emails: Set<String>): String {
        val encodedEmails = emails.joinToString("&mailboxes[]=") { URLEncoder.encode(it, "UTF-8") }
        return "${securedProxy()}/1/mail_hostings/mailboxes/exist?mailboxes[]=$encodedEmails"
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
        return "${mailbox()}?with=aliases,unseen"
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
        return "${mailMailbox(mailboxUuid)}/folder?with=ik-static"
    }

    fun folder(mailboxUuid: String, folderId: String): String {
        return "${mailMailbox(mailboxUuid)}/folder/$folderId"
    }

    fun flushFolder(mailboxUuid: String, folderId: String): String {
        return "${folder(mailboxUuid, folderId)}/flush"
    }

    fun renameFolder(mailboxUuid: String, folderId: String): String {
        return "${folder(mailboxUuid, folderId)}/rename"
    }

    fun search(
        mailboxUuid: String,
        folderId: String,
        hasDisplayModeThread: Boolean,
        filters: String,
        resource: String?,
    ): String {
        return if (resource.isNullOrBlank()) {
            val threadMode = if (hasDisplayModeThread) "on" else "off"
            "${folder(mailboxUuid, folderId)}/message?thread=${threadMode}&offset=0&$filters&with=emoji_reactions_per_message"
        } else {
            "${resource(resource)}&$filters"
        }
    }
    //endregion

    //region Message from Folder/Message
    private fun message(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${folder(mailboxUuid, folderId)}/message/$shortUid"
    }

    fun downloadAttachments(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${message(mailboxUuid, folderId, shortUid)}/attachmentsArchive"
    }

    fun blockUser(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${message(mailboxUuid, folderId, shortUid)}/blacklist"
    }

    fun reportPhishing(mailboxUuid: String): String {
        return "${mailMailbox(mailboxUuid)}/message/report"
    }

    fun downloadMessage(mailboxUuid: String, folderId: String, shortUid: Int): String {
        return "${message(mailboxUuid, folderId, shortUid)}/download"
    }
    //endregion

    //region Messages from Folder/Mobile
    private fun getMessages(mailboxUuid: String, folderId: String): String {
        return "${folder(mailboxUuid, folderId)}/mobile"
    }

    fun getMessagesUidsDelta(mailboxUuid: String, folderId: String, cursor: String): String {
        return "${getMessages(mailboxUuid, folderId)}/activities?signature=$cursor"
    }

    fun getDateOrderedMessagesUids(mailboxUuid: String, folderId: String): String {
        val parameters = "?messages=${Utils.NUMBER_OF_OLD_UIDS_TO_FETCH}&direction=desc"
        return "${getMessages(mailboxUuid, folderId)}/date-ordered-messages-uids${parameters}"
    }

    fun getMessagesByUids(mailboxUuid: String, folderId: String, uids: List<Int>): String {
        return "${getMessages(mailboxUuid, folderId)}/messages?uids=${uids.joinToString(separator = ",")}"
    }
    //endregion

    //region Messages from Mailbox
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

    fun starMessages(mailboxUuid: String): String {
        return "${messages(mailboxUuid)}/star"
    }

    fun unstarMessages(mailboxUuid: String): String {
        return "${messages(mailboxUuid)}/unstar"
    }
    //endregion

    //region Draft from Mailbox
    fun draft(mailboxUuid: String): String {
        return "${mailMailbox(mailboxUuid)}/draft"
    }

    fun draft(mailboxUuid: String, remoteDraftUuid: String): String {
        return "${draft(mailboxUuid)}/$remoteDraftUuid"
    }

    fun rescheduleDraft(draftResource: String, scheduleDate: Date): String {
        val formatedDate = scheduleDate.format(FORMAT_ISO_8601_WITH_TIMEZONE_SEPARATOR)
        return "${MAIL_API}${draftResource}/schedule?schedule_date=${URLEncoder.encode(formatedDate, "UTF-8")}"
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

    //region SwissTransfer
    fun swissTransferContainer(containerUuid: String): String {
        return "$MAIL_API/api/swisstransfer/containers/$containerUuid"
    }

    fun swissTransferContainerDownloadUrl(containerUuid: String): String {
        return "${swissTransferContainer(containerUuid)}/files/download"
    }
    //endregion

    //region Snooze
    fun snooze(mailboxUuid: String): String {
        return "${mailMailbox(mailboxUuid)}/snoozes"
    }

    fun snoozeAction(mailboxUuid: String, snoozeUuid: String): String {
        return "${snooze(mailboxUuid)}/$snoozeUuid"
    }
    //endregion

    //region Unsubscribe list diffusion
    fun unsubscribe(messageResource: String): String {
        return "${resource(messageResource)}/unsubscribeFromList"
    }
    //endregion

    private fun mailboxUuidParameter(mailboxUuid: String): String {
        return "?mailbox_uuid=$mailboxUuid"
    }

    fun featureFlags(mailboxUuid: String): String {
        return "$MAIL_API/api/feature-flag/check${mailboxUuidParameter(mailboxUuid)}"
    }

    fun ping(): String {
        return "$MAIL_API/api/ping-with-auth"
    }

    fun resource(resource: String): String {
        return "$MAIL_API$resource"
    }

    fun bimi(bimi: String): String {
        return "$MAIL_API$bimi"
    }

    fun shareLink(mailboxUuid: String, folderId: String, mailId: Int): String {
        return "${message(mailboxUuid, folderId, mailId)}/share"
    }
}
