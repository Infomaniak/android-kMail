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
package com.infomaniak.mail.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewBottomSheetSeparatorBinding
import splitties.systemservices.layoutInflater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailBottomSheetScaffold(
    isVisible: () -> Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable (ColumnScope.() -> Unit),
) {
    if (isVisible()) {
        val bottomSheetCornerSize = dimensionResource(R.dimen.bottomSheetCornerSize)
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = onDismissRequest,
            shape = RoundedCornerShape(topStart = bottomSheetCornerSize, topEnd = bottomSheetCornerSize),
            containerColor = colorResource(R.color.bottomSheetBackgroundColor),
            dragHandle = {
                AndroidView(
                    factory = { ViewBottomSheetSeparatorBinding.inflate(it.layoutInflater).root },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(R.dimen.bottomSheetDragHandleHeight)),
                ) { /* No-op */ }
            },
            content = {
                title?.let {
                    Text(
                        it,
                        style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                content()
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottomSheetBottomPadding)))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun Preview() {
    MaterialTheme {
        Surface {
            MailBottomSheetScaffold(
                isVisible = { true },
                onDismissRequest = {},
                title = "This bottom sheet's title"
            ) {
                Text("Hello world")
            }
        }
    }
}
