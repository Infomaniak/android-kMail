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
package com.infomaniak.mail.ui.login.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.google.android.material.color.DynamicColors
import com.infomaniak.core.ui.compose.margin.Margin
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.ui.theme.MailTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentColorSelector(
    selectedAccentColor: () -> AccentColor,
    onSelectAccentColor: (AccentColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedTabIndex = selectedAccentColor().introTabIndex

    PrimaryTabRow(
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        selectedTabIndex = selectedTabIndex,
        indicator = {
            Spacer(
                modifier
                    .fillMaxSize()
                    .tabIndicatorOffset(selectedTabIndex)
                    .background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(3.dp))
            )
        },
        containerColor = colorResource(R.color.elevatedBackground),
        divider = {},
    ) {
        AccentColorTab(
            accentColor = AccentColor.PINK,
            selectedTabIndex = selectedTabIndex,
            onClick = { onSelectAccentColor(AccentColor.PINK) },
        )
        AccentColorTab(
            accentColor = AccentColor.BLUE,
            selectedTabIndex = selectedTabIndex,
            onClick = { onSelectAccentColor(AccentColor.BLUE) },
        )
        if (DynamicColors.isDynamicColorAvailable()) {
            AccentColorTab(
                accentColor = AccentColor.SYSTEM,
                selectedTabIndex = selectedTabIndex,
                onClick = { onSelectAccentColor(AccentColor.SYSTEM) },
            )
        }
    }
}

@Composable
private fun AccentColorTab(accentColor: AccentColor, selectedTabIndex: Int, onClick: () -> Unit) {
    val isSelected = selectedTabIndex == accentColor.introTabIndex
    val textColor by animateColorAsState(getTextColor(isSelected))

    Tab(
        modifier = Modifier
            .zIndex(1f)
            .requiredHeight(40.dp),
        selected = isSelected,
        onClick = onClick,
    ) {
        Text(stringResource(accentColor.tabNameRes), color = textColor)
    }
}

@Composable
private fun getTextColor(isSelected: Boolean): Color = if (isSelected) {
    MaterialTheme.colorScheme.onPrimary
} else {
    colorResource(R.color.secondaryTextColor)
}


@Preview
@Composable
private fun Preview() {
    MailTheme {
        Surface {
            Box(Modifier.padding(Margin.Medium)) {
                var accentColor by remember { mutableStateOf(AccentColor.PINK) }
                val context = LocalContext.current
                MaterialTheme(
                    colorScheme = if (isSystemInDarkTheme()) {
                        darkColorScheme(primary = Color(accentColor.getPrimary(context)))
                    } else {
                        lightColorScheme(primary = Color(accentColor.getPrimary(context)))
                    }
                ) {
                    AccentColorSelector(
                        selectedAccentColor = { accentColor },
                        onSelectAccentColor = { accentColor = it }
                    )
                }
            }
        }
    }
}
