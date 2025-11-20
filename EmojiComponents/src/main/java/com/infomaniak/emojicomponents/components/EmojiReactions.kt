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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.infomaniak.core.ui.compose.margin.Margin
import com.infomaniak.emojicomponents.data.Reaction
import com.infomaniak.emojicomponents.icons.FaceSmileRoundPlus
import com.infomaniak.emojicomponents.icons.Icons

@Composable
fun EmojiReactions(
    reactions: () -> List<Reaction>,
    onEmojiClicked: (String) -> Unit,
    onAddReactionClick: () -> Unit,
    modifier: Modifier = Modifier,
    addReactionIcon: ImageVector = EmojiReactionsDefaults.addReactionIcon,
    isAddReactionEnabled: () -> Boolean = { true },
    colors: EmojiReactionsColors = EmojiReactionsDefaults.colors(),
    shape: Shape = InputChipDefaults.shape,
    onLongPress: ((String) -> Unit)? = null,
) {
    FlowRow(
        modifier,
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Margin.Mini),
    ) {
        reactions().forEach { reaction ->
            ReactionChip(
                emoji = reaction.emoji,
                reactionCount = { reaction.count },
                selected = { reaction.hasReacted },
                onClick = { onEmojiClicked(reaction.emoji) },
                colors = colors.reactionChip,
                shape = shape,
                onLongPress = { onLongPress?.invoke(reaction.emoji) },
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
    class State(override val emoji: String, override val count: Int, override val hasReacted: Boolean) : Reaction

    fun SnapshotStateList<Reaction>.updateWithEmoji(emoji: String) {
        val shouldUpdate = find { it.emoji == emoji }?.hasReacted != true
        if (shouldUpdate) {
            val index = indexOfFirst { it.emoji == emoji }
            val oldReaction = get(index)
            set(
                index,
                object : Reaction {
                    override val emoji: String = oldReaction.emoji
                    override val count: Int = oldReaction.count + 1
                    override val hasReacted: Boolean = true
                }
            )
        }
    }

    val reactions: SnapshotStateList<Reaction> = remember {
        mutableStateListOf(
            State("✅", 3, false),
            State("\uD83D\uDCCC", 1, false),
            State("\uD83D\uDE00", 5, true),
            State("\uD83D\uDE0D", 3, false),
            State("\uD83E\uDEE5", 2, true),
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
