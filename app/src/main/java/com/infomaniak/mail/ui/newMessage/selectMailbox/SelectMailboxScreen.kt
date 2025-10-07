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
package com.infomaniak.mail.ui.newMessage.selectMailbox

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.SelectedMailboxUi
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UserMailboxesUi
import com.infomaniak.mail.ui.newMessage.selectMailbox.compose.AccountMailboxesDropdown
import com.infomaniak.mail.ui.newMessage.selectMailbox.compose.SelectedMailboxIndicator
import com.infomaniak.mail.ui.newMessage.selectMailbox.compose.previewparameter.SelectMailboxScreenDataPreview
import com.infomaniak.mail.ui.newMessage.selectMailbox.compose.previewparameter.SelectMailboxScreenPreviewParameter
import com.infomaniak.mail.ui.theme.MailTheme

@Composable
fun SelectMailboxScreen(
    viewModel: SelectMailboxViewModel,
    onNavigationTopbarClick: () -> Unit,
    onContinue: (SelectedMailboxUi) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val usersWithMailboxes by viewModel.usersWithMailboxes.collectAsStateWithLifecycle()
    val userWithMailboxSelected by viewModel.selectedMailbox.collectAsStateWithLifecycle()
    val selectingAnotherUser = remember { mutableStateOf(false) }

    SelectMailboxScreen(
        usersWithMailboxes = usersWithMailboxes,
        selectedMailbox = userWithMailboxSelected,
        selectingAnotherUser = selectingAnotherUser,
        snackbarHostState = snackbarHostState,
        onMailboxSelected = {
            viewModel.selectMailbox(it)
        },
        onNavigationTopbarClick = onNavigationTopbarClick,
        onContinue = onContinue
    )
}

@Composable
fun SelectMailboxScreen(
    usersWithMailboxes: List<UserMailboxesUi>,
    selectedMailbox: SelectedMailboxUi?,
    selectingAnotherUser: MutableState<Boolean>,
    snackbarHostState: SnackbarHostState? = null,
    onMailboxSelected: (SelectedMailboxUi?) -> Unit,
    onNavigationTopbarClick: () -> Unit,
    onContinue: (SelectedMailboxUi) -> Unit
) {
    val bottomButton: (@Composable (Modifier) -> Unit)? = { modifier ->
        LargeButton(
            modifier = modifier.padding(horizontal = Margin.Medium),
            title = stringResource(R.string.buttonSendWithDifferentAddress),
            style = ButtonType.Tertiary,
            onClick = {
                selectingAnotherUser.value = true
                onMailboxSelected(null)
            }
        )
    }

    BottomStickyButtonScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MailTopAppBar(
                navigationIcon = {
                    TopAppBarButtons.Close(onNavigationTopbarClick)
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
                if (!selectingAnotherUser.value) {
                    selectedMailbox?.let {
                        SelectedMailboxIndicator(
                            modifier = Modifier.padding(Margin.Medium),
                            selectedMailbox = it
                        )
                    }
                } else {
                    Column {
                        LazyColumn(
                            modifier = Modifier.padding(Margin.Medium),
                            verticalArrangement = Arrangement.spacedBy(Margin.Mini),
                        ) {
                            items(usersWithMailboxes) { userWithMailboxes ->
                                AccountMailboxesDropdown(
                                    userWithMailboxes = userWithMailboxes,
                                    onClickMailbox = { mailbox ->
                                        onMailboxSelected(mailbox)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        selectedMailbox?.let {
                            SelectedMailboxIndicator(
                                modifier = Modifier.padding(Margin.Medium),
                                selectedMailbox = it
                            )
                        }
                    }
                }
            }
        },
        topButton = {
            LargeButton(
                modifier = it.padding(horizontal = Margin.Medium),
                title = stringResource(R.string.buttonContinue),
                enabled = { selectedMailbox != null },
                onClick = {
                    selectedMailbox?.let { onContinue(selectedMailbox) }
                }
            )
        },
        bottomButton = bottomButton.takeIf{ !selectingAnotherUser.value }
    )
}

@Composable
@Preview(name = "(1) Light")
@Preview(name = "(2) Dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
private fun PreviewDefaultMailbox(
    @PreviewParameter(SelectMailboxScreenPreviewParameter::class) previewData: SelectMailboxScreenDataPreview
) {
    val selectingAnotherUser = remember { mutableStateOf(previewData.selectingAnotherUser) }

    MailTheme {
        Surface(Modifier.fillMaxSize()) {
            SelectMailboxScreen(
                usersWithMailboxes = previewData.usersWithMailboxes,
                selectedMailbox = previewData.selectedMailboxUi,
                selectingAnotherUser = selectingAnotherUser,
                onMailboxSelected = {},
                onNavigationTopbarClick = {},
                onContinue = {}
            )
        }
    }
}
