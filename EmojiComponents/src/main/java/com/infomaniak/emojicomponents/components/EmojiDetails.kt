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
package com.infomaniak.emojicomponents.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infomaniak.core.compose.margin.Margin

@Composable
fun EmojiDetails(details: () -> SnapshotStateMap<String, String>) {
    LazyColumn {
        items(items = details().toList(), key = { it.first }) { (emoji, name) ->
            Row(
                Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(Margin.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar() // TODO
                Text(name)
                Spacer(modifier = Modifier.weight(1f))
                Text(emoji)
            }
        }
    }
}

@Composable
fun UserAvatar() {
    Surface(modifier = Modifier.size(24.dp), shape = CircleShape, color = Color.Gray) { }
}

@Preview
@Composable
private fun Preview() {
    val details = remember { mutableStateMapOf("hi" to "hello", "hey" to "nice") }
    Surface {
        EmojiDetails({ details })
    }
}
