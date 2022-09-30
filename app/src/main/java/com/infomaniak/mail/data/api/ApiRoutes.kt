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
import com.infomaniak.mail.data.UiSettings.ThreadMode
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter

object ApiRoutes {

    fun resource(resource: String) = "$MAIL_API$resource"

    fun addressBooks() = "$MAIL_API/api/pim/addressbook"

    fun contacts() = "$MAIL_API/api/pim/contact/all?with=emails,details,others,contacted_times"

    fun user() = "$MAIL_API/api/user"

    fun signatures(hostingId: Int, mailboxName: String): String {
        return "$MAIL_API/api/securedProxy/1/mail_hostings/$hostingId/mailboxes/$mailboxName/signatures"
    }

    fun mailbox() = "$MAIL_API/api/mailbox?with=unseen"

    fun folders(uuid: String) = "$MAIL_API/api/mail/$uuid/folder"

    fun folder(uuid: String, folderId: String) = "${folders(uuid)}/$folderId"

    // fun renameFolder(uuid: String, folderId: String) = "${folder(uuid, folderId)}/rename"

    // fun favoriteFolder(uuid: String, folderId: String, favorite: Boolean) = "${folder(uuid, folderId)}/${if (favorite) "favorite" else "unfavorite"}"

    // fun readFolder(uuid: String, folderId: String) = "${folder(uuid, folderId)}/read"

    // fun flushFolder(uuid: String, folderId: String) = "${folder(uuid, folderId)}/flush"

    fun threads(
        uuid: String,
        folderId: String,
        threadMode: ThreadMode,
        offset: Int,
        filter: ThreadFilter,
        searchText: String? = null,
    ): String {
        val folder = folder(uuid, folderId)
        val message = "/message"
        val thread = "?thread=${threadMode.apiCallValue}"
        val page = "&offset=$offset"
        val urlSearch = searchText?.let { "&scontains=$it" } ?: ""
        val urlAttachment = if (filter == ThreadFilter.ATTACHMENTS) "&sattachments=yes" else ""
        val urlFilter = when (filter) {
            ThreadFilter.SEEN,
            ThreadFilter.STARRED,
            ThreadFilter.UNSEEN -> "&filters=${filter.name.lowercase()}"
            else -> ""
        }
        return "${folder}${message}${thread}${page}${urlSearch}${urlAttachment}${urlFilter}"
    }

    fun quotas(mailbox: String, hostingId: Int) = "$MAIL_API/api/mailbox/quotas?mailbox=$mailbox&product_id=$hostingId"

    fun moveMessage(uuid: String) = "$MAIL_API/api/mail/$uuid/message/move"

    fun deleteMessage(uuid: String) = "$MAIL_API/api/mail/$uuid/message/delete"

    fun draft(uuid: String) = "$MAIL_API/api/mail/$uuid/draft"

    fun draft(uuid: String, draftUuid: String) = "${draft(uuid)}/$draftUuid"

    fun messageSeen(uuid: String) = "$MAIL_API/api/mail/$uuid/message/seen"

    fun messageUnseen(uuid: String) = "$MAIL_API/api/mail/$uuid/message/unseen"

    // fun messageSafe(uuid: String) = "$MAIL_API/api/mail/$uuid/message/safe"

    // fun createAttachment(uuid: String) = "${draft(uuid)}/attachment"

    fun starMessage(uuid: String, star: Boolean) = "$MAIL_API/api/mail/$uuid/message/${if (star) "star" else "unstar"}"

    // fun search(uuid: String, folderId: String, searchText: String) = "${folder(uuid, folderId)}/message?offset=0&thread=on&scontains=$searchText&severywhere=1&sattachments=no"
}
