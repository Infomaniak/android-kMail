/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import com.infomaniak.mail.data.LocalSettings.ThreadMode
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter

object ApiRoutes {

    fun resource(resource: String) = "$MAIL_API$resource"

    fun addressBooks() = "$MAIL_API/api/pim/addressbook"

    fun contacts() = "$MAIL_API/api/pim/contact/all?with=emails,details,others,contacted_times"

    fun signatures(mailboxHostingId: Int, mailboxName: String): String {
        return "$MAIL_API/api/securedProxy/1/mail_hostings/$mailboxHostingId/mailboxes/$mailboxName/signatures"
    }

    fun mailbox() = "$MAIL_API/api/mailbox?with=unseen"

    fun folders(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/folder"

    fun folder(mailboxUuid: String, folderId: String) = "${folders(mailboxUuid)}/$folderId"

    // fun renameFolder(mailboxUuid: String, folderId: String) = "${folder(mailboxUuid, folderId)}/rename"

    // fun favoriteFolder(mailboxUuid: String, folderId: String, favorite: Boolean): String {
    //     return "${folder(mailboxUuid, folderId)}/${if (favorite) "favorite" else "unfavorite"}"
    // }

    // fun readFolder(mailboxUuid: String, folderId: String) = "${folder(mailboxUuid, folderId)}/read"

    // fun flushFolder(mailboxUuid: String, folderId: String) = "${folder(mailboxUuid, folderId)}/flush"

    fun threads(
        mailboxUuid: String,
        folderId: String,
        threadMode: ThreadMode,
        offset: Int,
        filter: ThreadFilter,
        searchText: String? = null,
    ): String {

        val folder = folder(mailboxUuid, folderId)

        val message = "/message"

        val page = "?offset=$offset"

        val isDraftFolder = threadMode == ThreadMode.THREADS && FolderController.getFolder(folderId)?.role == FolderRole.DRAFT
        val thread = "&thread=${(if (isDraftFolder) ThreadMode.MESSAGES else threadMode).apiCallValue}"

        val urlSearch = searchText?.let { "&scontains=$it" } ?: ""

        val urlAttachment = if (filter == ThreadFilter.ATTACHMENTS) "&sattachments=yes" else ""

        val urlFilter = when (filter) {
            ThreadFilter.SEEN,
            ThreadFilter.STARRED,
            ThreadFilter.UNSEEN -> "&filters=${filter.name.lowercase()}"
            else -> ""
        }

        return "${folder}${message}${page}${thread}${urlSearch}${urlAttachment}${urlFilter}"
    }

    fun quotas(mailboxHostingId: Int, mailboxName: String): String {
        return "$MAIL_API/api/mailbox/quotas?mailbox=$mailboxName&product_id=$mailboxHostingId"
    }

    fun moveMessage(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/message/move"

    fun deleteMessage(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/message/delete"

    fun draft(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/draft"

    fun draft(mailboxUuid: String, draftRemoteUuid: String) = "${draft(mailboxUuid)}/$draftRemoteUuid"

    fun messageSeen(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/message/seen"

    fun messageUnseen(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/message/unseen"

    // fun messageSafe(mailboxUuid: String) = "$MAIL_API/api/mail/$mailboxUuid/message/safe"

    // fun createAttachment(mailboxUuid: String) = "${draft(mailboxUuid)}/attachment"

    fun starMessage(mailboxUuid: String, star: Boolean): String {
        return "$MAIL_API/api/mail/$mailboxUuid/message/${if (star) "star" else "unstar"}"
    }

    // fun search(mailboxUuid: String, folderId: String, searchText: String): String {
    //     return "${folder(mailboxUuid, folderId)}/message?offset=0&thread=on&scontains=$searchText&severywhere=1&sattachments=no"
    // }
}
