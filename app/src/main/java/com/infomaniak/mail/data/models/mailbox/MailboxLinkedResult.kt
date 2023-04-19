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
class MailboxLinkedResult(
    @SerialName("id")
    val mailboxId: Int,
    @SerialName("mail")
    val email: String,
    @SerialName("mail_idn")
    val emailIdn: String,
    @SerialName("mailbox")
    val mailboxName: String,
    @SerialName("has_valid_password")
    val hasValidPassword: Boolean,
    @SerialName("technical_right")
    val technicalRight: Boolean,
    @SerialName("is_limited")
    val isLimited: Boolean,
    @SerialName("is_valid")
    val isMailboxValid: Boolean,
    @SerialName("is_primary")
    val isPrimary: Boolean,
    val permission: String,
    val permissions: MailboxLinkedPermissions,
    @SerialName("product_id")
    val productId: Int,
    @SerialName("ksuite_customer_name")
    val kSuiteCustomerName: String?,
    val type: Int,
)
