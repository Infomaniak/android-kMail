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
package com.infomaniak.mail.ui.newMessage.mailbox

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.infomaniak.core.compose.bottomstickybuttonscaffolds.BottomStickyButtonScaffold
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.components.compose.ButtonType
import com.infomaniak.mail.ui.components.compose.LargeButton
import com.infomaniak.mail.ui.theme.MailTheme

@Composable
fun SelectMailboxScreen() {
    val snackbarHostState = remember { SnackbarHostState() }

    BottomStickyButtonScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {

        },
        topButton = {
            LargeButton(
                modifier = it,
                title = stringResource(R.string.buttonContinue)
            ) {

            }
        },
        bottomButton = {
            LargeButton(
                modifier = it,
                title = stringResource(R.string.buttonContinue),
                style = ButtonType.Tertiary
            ) {

            }
        },
    ) {

    }
}

@Composable
@Preview(name = "(1) Light")
@Preview(name = "(2) Dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
private fun Preview() {
    MailTheme {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            SelectMailboxScreen()
        }
    }
}
