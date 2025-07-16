/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.message

import com.infomaniak.mail.data.models.correspondent.Recipient
import io.realm.kotlin.types.EmbeddedRealmObject

class EmojiReactionAuthor constructor() : EmbeddedRealmObject {
    var recipient: Recipient? = null
    var sourceMessageUid: String = ""

    constructor(recipient: Recipient, sourceMessageUid: String) : this() {
        this.recipient = recipient
        this.sourceMessageUid = sourceMessageUid
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmojiReactionAuthor

        if (recipient != other.recipient) return false
        if (sourceMessageUid != other.sourceMessageUid) return false

        return true
    }

    override fun hashCode(): Int {
        var result = recipient?.hashCode() ?: 0
        result = 31 * result + sourceMessageUid.hashCode()
        return result
    }
}
