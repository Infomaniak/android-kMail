/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import android.content.Context
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class Signature : RealmObject {

    //region Remote data
    @PrimaryKey
    var id: Int = 0
    var name: String = ""
    @SerialName("full_name")
    var senderName: String = ""
    @SerialName("is_default")
    var isDefault: Boolean = false
    var content: String = ""
    @SerialName("sender")
    var senderEmail: String = ""
    @SerialName("sender_idn")
    var senderEmailIdn: String = ""
    @SerialName("reply_to_id")
    var replyToId: Int = 0 /* Required for the update call, do not delete */
    @SerialName("sender_id")
    var senderId: Int = 0 /* Required for the update call, do not delete */
    //endregion

    //region Local data (Transient)
    @Transient
    var isDefaultReply: Boolean = false
    //endregion

    //region UI data (Transient & Ignore)
    @Transient
    @Ignore
    var isDummy: Boolean = false // The empty Signature to allow the User to not choose any Signature.
    //endregion

    companion object {

        fun getDummySignature(context: Context, isDefault: Boolean = false) = Signature().apply {
            id = Draft.NO_IDENTITY
            isDummy = true
            name = context.getString(R.string.selectSignatureNone)
            senderEmailIdn = AccountUtils.currentMailboxEmail!!
            this.isDefault = isDefault
        }
    }
}
