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

object ApiRoutes {

    fun resource(resource: String) = "$MAIL_API$resource"

//    fun addressBooks() = "$MAIL_API/api/pim/addressbook"

//    fun contacts() = "$MAIL_API/api/pim/contact/all?with=emails,details,others,contacted_times"

//    fun user() = "$MAIL_API/api/user"

//    fun signatures(hostingId: Int, mailboxName: String) =
//        "$MAIL_API/api/securedProxy/1/mail_hostings/$hostingId/mailboxes/$mailboxName/signatures"

    fun mailbox() = "$MAIL_API/api/mailbox"

    fun folders(uuid: String) = "/api/mail/$uuid/folder"

    fun folder(uuid: String, folderId: String) = "${folders(uuid)}/$folderId"

//    fun renameFolder(uuid: String, folderId: String) = "${folder(uuid, folderId)}/rename"

//    fun favoriteFolder(uuid: String, folderId: String, favorite: Boolean) =
//        "${folder(uuid, folderId)}/${if (favorite) "favorite" else "unfavorite"}"

//    fun readFolder(uuid: String, folderId: String) = "${folder(uuid, folderId)}/read"

//    fun flushFolder(uuid: String, folderId: String) = "${folder(uuid, folderId)}/flush"

    fun threads(uuid: String, folderId: String, filter: String?) =
        with("${folder(uuid, folderId)}/message?offset=0&thread=on") {
            if (filter != null) "$this&offset=$filter" else this
        }

    fun quotas(mailbox: String, productId: Int) = "${mailbox()}/quotas?mailbox=$mailbox&product_id=$productId"

//    fun moveMessage(uuid: String) = "/api/mail/$uuid/message/move"

//    fun draft(uuid: String) = "/api/mail/$uuid/draft"

//    fun draft(uuid: String, draftUuid: String) = "${draft(uuid)}/$draftUuid"

//    fun messageSeen(uuid: String) = "/api/mail/$uuid/message/seen"

//    fun messageUnseen(uuid: String) = "/api/mail/$uuid/message/unseen"

//    fun messageSafe(uuid: String) = "/api/mail/$uuid/message/safe"

//    fun createAttachment(uuid: String) = "${draft(uuid)}/attachment"

//    fun starMessage(uuid: String, star: Boolean) = "/api/mail/$uuid/message/${if (star) "star" else "unstar"}"

//    fun search(uuid: String, folderId: String, searchText: String) =
//        "${folder(uuid, folderId)}/message?offset=0&thread=on&scontains=$searchText&severywhere=1&sattachments=no"
}