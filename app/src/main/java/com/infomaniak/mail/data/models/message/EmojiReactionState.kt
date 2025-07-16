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

import com.infomaniak.emojicomponents.data.Reaction
import com.infomaniak.mail.data.models.correspondent.Recipient
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.annotations.Ignore

class EmojiReactionState constructor() : Reaction, EmbeddedRealmObject {
    override var emoji: String = ""
    var authors: RealmList<Recipient> = realmListOf()
    override var hasReacted: Boolean = false

    @Ignore
    override val count: Int by authors::size

    constructor(emoji: String) : this() {
        this.emoji = emoji
    }

    fun addAuthor(newAuthor: Recipient) {
        authors.add(newAuthor)
        hasReacted = hasReacted || newAuthor.isMe()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmojiReactionState

        if (hasReacted != other.hasReacted) return false
        if (emoji != other.emoji) return false
        if (authors != other.authors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hasReacted.hashCode()
        result = 31 * result + emoji.hashCode()
        result = 31 * result + authors.hashCode()
        return result
    }
}
