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

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview


@Composable
internal fun ReactionChip(
    emoji: String,
    reactionCount: () -> Int,
    selected: () -> Boolean,
    onClick: () -> Unit,
    colors: ReactionChipColors = ReactionChipDefaults.reactionChipColors(),
    shape: Shape = InputChipDefaults.shape,
) {
    InputChip(
        selected = selected(),
        onClick = onClick,
        leadingIcon = { Text(emoji) },
        label = { Text(reactionCount().toString()) },
        colors = InputChipDefaults.inputChipColors(
            containerColor = colors.containerColor,
            labelColor = colors.contentColor,
            selectedContainerColor = colors.containerColor,
            selectedLabelColor = colors.contentColor
        ),
        border = InputChipDefaults.inputChipBorder(
            enabled = true,
            selected = selected(),
            selectedBorderColor = colors.accentColor,
            borderColor = Color.Transparent,
        ),
        shape = shape,
    )
}

data class ReactionChipColors(
    val containerColor: Color,
    val contentColor: Color,
    val accentColor: Color,
)

object ReactionChipDefaults {
    @Composable
    fun reactionChipColors(): ReactionChipColors = ReactionChipColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        accentColor = MaterialTheme.colorScheme.primary
    )
}

@Preview
@Composable
private fun Preview() {
    Surface {
        Column {
            ReactionChip("\uD83D\uDCCC", { 5 }, { true }, {})
            ReactionChip("\uD83D\uDCCC", { 5 }, { false }, {})
        }
    }
}
