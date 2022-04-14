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
package com.infomaniak.mail.data.models

import com.google.gson.annotations.SerializedName

data class SignaturesResult(
    val signatures: ArrayList<Signature>,
    @SerializedName("default_signature_id")
    val defaultSignatureId: Int,
    @SerializedName("valid_emails")
    val validEmails: ArrayList<Email>,
    val position: String,
) {
    data class Signature(
        val id: Int,
        val name: String,
        @SerializedName("reply_to")
        val replyTo: String,
        @SerializedName("reply_to_idn")
        val replyToIdn: String,
        @SerializedName("reply_to_id")
        val replyToId: Int,
        @SerializedName("full_name")
        val fullName: String,
        val sender: String,
        @SerializedName("sender_idn")
        val senderIdn: String,
        @SerializedName("sender_id")
        val senderId: Int,
        val hash: String?,
        @SerializedName("is_default")
        val isDefault: Boolean,
        @SerializedName("service_mail_model_id")
        val serviceMailModelId: Int?,
        val position: String,
        @SerializedName("is_editable")
        val isEditable: Boolean,
        val content: String,
    )

    data class Email(
        val id: Int,
        val email: String,
        @SerializedName("email_idn")
        val emailIdn: String,
        @SerializedName("is_account")
        val isAccount: Boolean,
        @SerializedName("is_verified")
        val isVerified: Boolean,
        @SerializedName("is_removable")
        val isRemovable: Boolean,
    )
}