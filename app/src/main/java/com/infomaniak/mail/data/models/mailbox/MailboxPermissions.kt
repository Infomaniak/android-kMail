/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.mailbox

import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MailboxPermissions : EmbeddedRealmObject {

    @SerialName("can_manage_filters")
    var canManageFilters: Boolean = false
    @SerialName("can_change_password")
    var canChangePassword: Boolean = false
    @SerialName("can_manage_signatures")
    var canManageSignatures: Boolean = false
    @SerialName("can_configure_mail_folders")
    var canConfigureMailFolders: Boolean = false
    @SerialName("can_restore_emails")
    var canRestoreEmails: Boolean = false

    companion object
}
