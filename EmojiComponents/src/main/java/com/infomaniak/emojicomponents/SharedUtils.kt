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
package com.infomaniak.emojicomponents

import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.infomaniak.emojicomponents.data.ReactionState

internal fun SnapshotStateMap<String, ReactionState>.updateWithEmoji(emoji: String) {
    if (this[emoji]?.hasReacted == true) return

    val oldCount = this[emoji]?.count ?: 0
    this[emoji] = object : ReactionState {
        override val count: Int = oldCount + 1
        override val hasReacted: Boolean = true
    }
}
