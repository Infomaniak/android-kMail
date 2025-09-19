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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infomaniak.core.compose.margin.Margin
import com.infomaniak.emojicomponents.R
import com.infomaniak.emojicomponents.icons.FaceSmileRoundPlus
import com.infomaniak.emojicomponents.icons.Icons

@Composable
internal fun AddReactionChip(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: () -> Boolean = { true },
    colors: AddReactionColors = AddReactionChipDefaults.addReactionColors(),
) {
    IconButton(
        modifier = modifier.size(38.dp),
        enabled = enabled(),
        onClick = onClick,
        content = {
            Icon(
                icon,
                stringResource(R.string.buttonAddReaction),
                modifier = Modifier.size(Margin.Large),
            )
        },
        colors = colors.buttonColors,
    )
}

data class AddReactionColors(val buttonColors: IconButtonColors)

object AddReactionChipDefaults {
    @Composable
    fun addReactionColors(
        iconButtonColors: IconButtonColors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ): AddReactionColors = AddReactionColors(iconButtonColors)
}

@Preview
@Composable
private fun AddReactionChipPreview() {
    Surface {
        Column {
            AddReactionChip(
                icon = Icons.FaceSmileRoundPlus,
                onClick = {},
            )
            AddReactionChip(
                icon = Icons.FaceSmileRoundPlus,
                onClick = {},
                enabled = { false }
            )
        }
    }
}
