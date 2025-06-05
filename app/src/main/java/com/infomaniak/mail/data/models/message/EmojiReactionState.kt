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

import com.infomaniak.emojicomponents.data.ReactionState
import io.realm.kotlin.types.EmbeddedRealmObject

class EmojiReactionState : ReactionState, EmbeddedRealmObject {
    override var count: Int = 0
    override var hasReacted: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmojiReactionState

        if (count != other.count) return false
        if (hasReacted != other.hasReacted) return false

        return true
    }

    override fun hashCode(): Int {
        var result = count
        result = 31 * result + hasReacted.hashCode()
        return result
    }
}
