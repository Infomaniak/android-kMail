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
package com.infomaniak.mail.ui.components.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.infomaniak.core.compose.basics.Typography
import com.infomaniak.core.compose.preview.PreviewAllWindows
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.theme.MailTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailTopAppBar(
    title: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.primary
        ),
        title = { Text(text = title, style = Typography.h2) },
        navigationIcon = navigationIcon,
        actions = actions
    )
}

@Composable
fun TopAppBarButton(
    icon: ImageVector,
    @StringRes contentDescResId: Int,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(imageVector = icon, contentDescription = stringResource(contentDescResId))
    }
}

object TopAppBarButtons {
    @Composable
    fun Back(onClick: () -> Unit) = TopAppBarButton(
        icon = ImageVector.vectorResource(R.drawable.ic_chevron_left),
        contentDescResId = R.string.contentDescriptionButtonBack,
        onClick = onClick,
    )

    @Composable
    fun Close(onClick: () -> Unit) = TopAppBarButton(
        icon = ImageVector.vectorResource(R.drawable.ic_close_big),
        contentDescResId = R.string.buttonClose,
        onClick = onClick,
    )
}

@PreviewAllWindows
@Composable
private fun BrandTopAppBarPreview() {
    MailTheme {
        MailTopAppBar(
            title = "Title",
            navigationIcon = {
                TopAppBarButtons.Close {  }
            }
        )
    }
}
