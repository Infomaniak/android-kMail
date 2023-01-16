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
package com.infomaniak.mail.data.models.signature

import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Signature : RealmObject {
    var id: Int = 0
    var name: String = ""
    @SerialName("reply_to")
    var replyTo: String = ""
    @SerialName("reply_to_idn")
    var replyToIdn: String = ""
    @SerialName("reply_to_id")
    var replyToId: Int = 0
    @SerialName("full_name")
    var fullName: String = ""
    var sender: String = ""
    @SerialName("sender_idn")
    var senderIdn: String = ""
    @SerialName("sender_id")
    var senderId: Int = 0
    var hash: String? = null
    @SerialName("is_default")
    var isDefault: Boolean = false
    @SerialName("service_mail_model_id")
    var serviceMailModelId: Int? = null
    @SerialName("position")
    private var _position: String = ""
    @SerialName("is_editable")
    var isEditable: Boolean = false
    var content: String = ""

    val position: SignaturePosition?
        get() = enumValueOfOrNull<SignaturePosition>(_position)

    enum class SignaturePosition(val position: String) {
        BEFORE_REPLY_MESSAGE("top"),
        AFTER_REPLY_MESSAGE("bottom"),
    }
}
