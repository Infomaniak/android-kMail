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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infomaniak.core.compose.basics.Typography
import com.infomaniak.core.compose.bottomstickybuttonscaffolds.BottomStickyButtonScaffold
import com.infomaniak.core.compose.margin.Margin
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.components.compose.ButtonType
import com.infomaniak.mail.ui.components.compose.LargeButton
import com.infomaniak.mail.ui.components.compose.MailTopAppBar
import com.infomaniak.mail.ui.components.compose.TopAppBarButtons
import com.infomaniak.mail.ui.newMessage.mailbox.compose.AccountMailboxesMenu
import com.infomaniak.mail.ui.newMessage.mailbox.compose.SelectMailboxPreviewParameter
import com.infomaniak.mail.ui.newMessage.mailbox.compose.SelectedMailbox
import com.infomaniak.mail.ui.theme.MailTheme

@Composable
fun SelectMailboxScreen(viewModel: SelectMailboxViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val usersWithMailboxes by viewModel.usersWithMailboxes.collectAsStateWithLifecycle()
    val userWithMailboxSelected by viewModel.userWithMailboxSelected.collectAsStateWithLifecycle()

    SelectMailboxScreen(
        usersWithMailboxes = usersWithMailboxes,
        userWithMailboxSelected = userWithMailboxSelected,
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun SelectMailboxScreen(
    usersWithMailboxes: List<UserMailboxesUi>,
    userWithMailboxSelected: UserMailboxesUi?,
    snackbarHostState: SnackbarHostState? = null,
) {
    BottomStickyButtonScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MailTopAppBar(
                navigationIcon = {
                    TopAppBarButtons.Close {
                        // TODO: Close NewMessageActivity and go to threadlist
                    }
                }
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .padding(Margin.Medium)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Margin.Medium)
            ) {
                Image(
                    imageVector = ImageVector.vectorResource(R.drawable.illustration_mailbox_ellipsis_bubble),
                    contentDescription = null
                )
                Text(
                    style = Typography.h2,
                    maxLines = 1,
                    text = stringResource(R.string.composeMailboxCurrentTitle)
                )
                userWithMailboxSelected?.let { userWithMailbox ->
                    SelectedMailbox(
                        modifier = Modifier.padding(Margin.Medium),
                        mailboxSelected =  userWithMailbox
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Margin.Mini),
                ) {
                    items(usersWithMailboxes) { userWithMailboxes ->
                        AccountMailboxesMenu(
                            modifier = Modifier.padding(horizontal = Margin.Medium),
                            userWithMailboxes = userWithMailboxes
                        )
                    }
                }
            }
        },
        topButton = {
            LargeButton(
                modifier = it.padding(horizontal = Margin.Medium),
                title = stringResource(R.string.buttonContinue)
            ) {
                // TODO: Open newMessageFragment
            }
        },
        bottomButton = {
            LargeButton(
                modifier = it.padding(horizontal = Margin.Medium),
                title = stringResource(R.string.buttonSendWithDifferentAddress),
                modifier = it,
                onClick = {
                    // TODO: Open screen choose account and mailbox
                },
                style = ButtonType.Tertiary
            )
        },
    )
}

@Composable
@Preview(name = "(1) Light")
@Preview(name = "(2) Dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
private fun Preview(@PreviewParameter(SelectMailboxPreviewParameter::class) usersWithMailboxes: List<UserMailboxesUi>) {
    MailTheme {
        Surface(Modifier.fillMaxSize()) {
            SelectMailboxScreen(
                usersWithMailboxes = usersWithMailboxes,
                userWithMailboxSelected = usersWithMailboxes.first()
            )
        }
    }
}
