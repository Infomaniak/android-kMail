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
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.annotations.Ignore

class EmojiReactionState constructor() : Reaction, EmbeddedRealmObject {
    override var emoji: String = ""
    var authors: RealmList<EmojiReactionAuthor> = realmListOf()
    override var hasReacted: Boolean = false
    var isSeen: Boolean = true

    @Ignore
    override val count: Int by authors::size

    constructor(emoji: String) : this() {
        this.emoji = emoji
    }

    fun addAuthor(newAuthor: EmojiReactionAuthor) {
        authors.add(newAuthor)
        if (hasReacted.not()) hasReacted = newAuthor.recipient?.isMe() == true
    }
}
