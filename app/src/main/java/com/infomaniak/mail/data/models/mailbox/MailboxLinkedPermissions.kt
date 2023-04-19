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
package com.infomaniak.mail.data.models.mailbox

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MailboxLinkedPermissions(
    @SerialName("manage_filters")
    val manageFilters: Boolean,
    @SerialName("manage_security")
    val manageSecurity: Boolean,
    @SerialName("manage_aliases")
    val manageAliases: Boolean,
    @SerialName("manage_redirections")
    val manageRedirections: Boolean,
    @SerialName("manage_signatures")
    val manageSignatures: Boolean,
    @SerialName("manage_auto_reply")
    val manageAutoReply: Boolean,
    @SerialName("change_password")
    val managePassword: Boolean,
    @SerialName("configure_mail_folders")
    val configureMailFolders: Boolean,
    @SerialName("manage_chat")
    val manageChat: Boolean,
    @SerialName("restore_emails")
    val restoreEmails: Boolean,
    @SerialName("manage_rules")
    val manageRules: Boolean,
    @SerialName("access_logs")
    val accessLogs: Boolean,
)

