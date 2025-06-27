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
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


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
        colors = colors.inputChipColors,
        border = InputChipDefaults.inputChipBorder(
            enabled = true,
            selected = selected(),
            selectedBorderColor = colors.accentColor,
            borderColor = Color.Transparent,
            selectedBorderWidth = 1.dp,
        ),
        shape = shape,
    )
}

data class ReactionChipColors(
    val inputChipColors: SelectableChipColors,
    val accentColor: Color,
)

object ReactionChipDefaults {
    @Composable
    fun reactionChipColors(
        inputChipColors: SelectableChipColors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        accentColor: Color = MaterialTheme.colorScheme.primary,
    ): ReactionChipColors = ReactionChipColors(
        inputChipColors,
        accentColor,
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
