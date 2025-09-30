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
package com.infomaniak.mail.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.infomaniak.core.compose.basics.Typography
import com.infomaniak.core.compose.basics.bottomsheet.BottomSheetThemeDefaults
import com.infomaniak.core.compose.basics.bottomsheet.LocalBottomSheetTheme
import com.infomaniak.core.compose.materialthemefromxml.MaterialThemeFromXml
import com.infomaniak.mail.R

@Composable
fun MailTheme(content: @Composable () -> Unit) {
    MaterialThemeFromXml {
        val cornerSize = dimensionResource(R.dimen.bottomSheetCornerSize)

        val bottomSheetTheme = BottomSheetThemeDefaults.theme(
            shape = RoundedCornerShape(topStart = cornerSize, topEnd = cornerSize),
            dragHandleColor = colorResource(R.color.dragHandleColor),
            titleTextStyle = Typography.bodyMedium,
            titleColor = colorResource(R.color.primaryTextColor),
        )

        CompositionLocalProvider(
            LocalBottomSheetTheme provides bottomSheetTheme,
            content = content
        )
    }
}
