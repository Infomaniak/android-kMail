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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.infomaniak.core.compose.margin.Margin
import com.infomaniak.emojicomponents.data.ReactionState
import com.infomaniak.emojicomponents.icons.FaceSmileRoundPlus
import com.infomaniak.emojicomponents.icons.Icons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiReactions(
    reactions: () -> Map<String, ReactionState>,
    onEmojiClicked: (String) -> Unit,
    onAddReactionClick: () -> Unit,
    modifier: Modifier = Modifier,
    addReactionIcon: ImageVector = EmojiReactionsDefaults.addReactionIcon,
    isAddReactionEnabled: () -> Boolean = { true },
    colors: EmojiReactionsColors = EmojiReactionsDefaults.colors(),
    shape: Shape = InputChipDefaults.shape,
) {
    FlowRow(
        modifier,
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Margin.Mini),
    ) {
        reactions().forEach { (emoji, state) ->
            ReactionChip(
                emoji = emoji,
                reactionCount = { state.count },
                selected = { state.hasReacted },
                onClick = { onEmojiClicked(emoji) },
                colors = colors.reactionChip,
                shape = shape,
            )
        }
        AddReactionChip(
            addReactionIcon,
            onClick = onAddReactionClick,
            enabled = isAddReactionEnabled,
            colors = colors.addReaction,
        )
    }
}

data class EmojiReactionsColors(
    val reactionChip: ReactionChipColors,
    val addReaction: AddReactionColors,
)

object EmojiReactionsDefaults {
    val addReactionIcon = Icons.FaceSmileRoundPlus

    @Composable
    fun colors(
        reactionChipColors: ReactionChipColors = ReactionChipDefaults.reactionChipColors(),
        addReactionColors: AddReactionColors = AddReactionChipDefaults.addReactionColors(),
    ): EmojiReactionsColors = EmojiReactionsColors(
        reactionChipColors,
        addReactionColors,
    )
}

@Preview
@Composable
private fun EmojiReactionsPreview() {
    class State(override val count: Int, override val hasReacted: Boolean) : ReactionState

    fun SnapshotStateMap<String, ReactionState>.updateWithEmoji(emoji: String) {
        if (this[emoji]?.hasReacted == true) return

        val oldCount = this[emoji]?.count ?: 0
        this[emoji] = object : ReactionState {
            override val count: Int = oldCount + 1
            override val hasReacted: Boolean = true
        }
    }

    val reactions: SnapshotStateMap<String, ReactionState> = remember {
        mutableStateMapOf(
            "✅" to State(3, false),
            "\uD83D\uDCCC" to State(1, false),
            "\uD83D\uDE00" to State(5, true),
            "\uD83D\uDE0D" to State(3, false),
            "\uD83E\uDEE5" to State(2, true),
        )
    }

    Surface {
        EmojiReactions(
            reactions = { reactions },
            onEmojiClicked = { emoji -> reactions.updateWithEmoji(emoji) },
            onAddReactionClick = {},
        )
    }
}
