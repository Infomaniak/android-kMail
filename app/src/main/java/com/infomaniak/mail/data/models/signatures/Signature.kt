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
package com.infomaniak.mail.data.models.signatures

import com.google.gson.annotations.SerializedName
import io.realm.RealmObject

open class Signature(
    var id: Int = 0,
    var name: String = "",
    @SerializedName("reply_to")
    var replyTo: String = "",
    @SerializedName("reply_to_idn")
    var replyToIdn: String = "",
    @SerializedName("reply_to_id")
    var replyToId: Int = 0,
    @SerializedName("full_name")
    var fullName: String = "",
    var sender: String = "",
    @SerializedName("sender_idn")
    var senderIdn: String = "",
    @SerializedName("sender_id")
    var senderId: Int = 0,
    var hash: String = "",
    @SerializedName("is_default")
    var isDefault: Boolean = false,
    @SerializedName("service_mail_model_id")
    var serviceMailModelId: Int = 0,
    var position: String = "",
    @SerializedName("is_editable")
    var isEditable: Boolean = false,
    var content: String = "",
) : RealmObject()