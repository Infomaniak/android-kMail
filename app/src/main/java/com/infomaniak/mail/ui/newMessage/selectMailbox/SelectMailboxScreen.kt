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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infomaniak.core.compose.basics.Typography
import com.infomaniak.core.compose.bottomstickybuttonscaffolds.BottomStickyButtonScaffold
import com.infomaniak.core.compose.margin.Margin
import com.infomaniak.core.compose.preview.PreviewLargeWindow
import com.infomaniak.core.compose.preview.PreviewSmallWindow
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.components.compose.ButtonType
import com.infomaniak.mail.ui.components.compose.LargeButton
import com.infomaniak.mail.ui.components.compose.MailTopAppBar
import com.infomaniak.mail.ui.components.compose.TopAppBarButtons
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.SelectedMailboxUi
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UiState
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SelectMailboxScreenContent(
        usersWithMailboxes = usersWithMailboxes,
        uiState = { uiState },
        snackbarHostState = snackbarHostState,
        onMailboxSelected = viewModel::selectMailbox,
        onChooseAnotherMailbox = viewModel::chooseAnotherMailbox,
        onNavigationTopbarClick = onNavigationTopbarClick,
        onContinueWithMailbox = onContinue
    )
}

@Composable
fun SelectMailboxScreenContent(
    usersWithMailboxes: List<UserMailboxesUi>,
    uiState: () -> UiState,
    snackbarHostState: SnackbarHostState? = null,
    onMailboxSelected: (SelectedMailboxUi?) -> Unit,
    onChooseAnotherMailbox: (Boolean) -> Unit,
    onNavigationTopbarClick: () -> Unit,
    onContinueWithMailbox: (SelectedMailboxUi) -> Unit
) {
    val selectedMailbox by remember { derivedStateOf { (uiState() as? UiState.SelectMailbox)?.selectedMailbox?.value } }

    BackHandler(
        enabled = uiState() is UiState.SelectMailbox
    ) {
        onChooseAnotherMailbox(false)
        onMailboxSelected(null)
    }

    BottomStickyButtonScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MailTopAppBar(
                navigationIcon = {
                    if (uiState() is UiState.SelectMailbox) {
                        TopAppBarButtons.Back(onNavigationTopbarClick)
                    } else {
                        TopAppBarButtons.Close(onNavigationTopbarClick)
                    }
                }
            )
        },
        content = {
            Column {
                ScrollableContent(
                    uiState,
                    usersWithMailboxes,
                    onMailboxSelected
                )
                SelectedMailboxBottom(selectedMailbox)
            }
        },
        topButton = { modifier ->
            TopButton(modifier, uiState, onContinueWithMailbox)
        },
        bottomButton = { modifier ->
            BottomButton(modifier, uiState, onMailboxSelected, onChooseAnotherMailbox)
        }
    )
}

@Composable
private fun Header() {
    Image(
        imageVector = ImageVector.vectorResource(R.drawable.illustration_mailbox_ellipsis_bubble),
        contentDescription = null
    )
    Text(
        style = Typography.h2,
        maxLines = 1,
        text = stringResource(R.string.composeMailboxCurrentTitle)
    )
}

@Composable
private fun ColumnScope.ScrollableContent(
    uiState: () -> UiState,
    usersWithMailboxes: List<UserMailboxesUi>,
    onMailboxSelected: (SelectedMailboxUi?) -> Unit
) {
    uiState().let { uiState ->
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = Margin.Medium)
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Margin.Medium),
        ) {
            item {
                Header()
            }
            when (uiState) {
                is UiState.Loading -> {}
                is UiState.DefaultMailbox -> item {
                    SelectedMailboxIndicator(
                        modifier = Modifier.animateItem(),
                        selectedMailbox = uiState.defaultMailbox
                    )
                }
                is UiState.SelectMailbox -> items(
                    items = usersWithMailboxes,
                    key = { it.userId }
                ) { userWithMailboxes ->
                    AccountMailboxesDropdown(
                        modifier = Modifier.animateItem(),
                        userWithMailboxes = userWithMailboxes,
                        onClickMailbox = { mailbox ->
                            onMailboxSelected(mailbox)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedMailboxBottom(selectedMailbox: SelectedMailboxUi?) {
    AnimatedContent(
        targetState = selectedMailbox,
        transitionSpec = { (fadeIn()).togetherWith(fadeOut()) }
    ) { mailbox ->
        if (mailbox != null) {
            SelectedMailboxIndicator(
                modifier = Modifier
                    .padding(horizontal = Margin.Medium)
                    .padding(top = Margin.Medium),
                selectedMailbox = mailbox
            )
        }
    }
}

@Composable
private fun TopButton(
    modifier: Modifier,
    uiState: () -> UiState,
    onContinueWithMailbox: (SelectedMailboxUi) -> Unit
) {
    val selectedMailbox by remember { derivedStateOf { (uiState() as? UiState.SelectMailbox)?.selectedMailbox?.value } }

    LargeButton(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.buttonContinue),
        enabled = { uiState() !is UiState.SelectMailbox || (uiState() as UiState.SelectMailbox).selectedMailbox.value != null }
    ) {
        selectedMailbox?.let { selectedMailboxUi ->
            onContinueWithMailbox(selectedMailboxUi)
        }
    }
}

@Composable
private fun BottomButton(
    modifier: Modifier,
    uiState: () -> UiState,
    onMailboxSelected: (SelectedMailboxUi?) -> Unit,
    onChooseAnotherMailbox: (Boolean) -> Unit,
) {
    AnimatedVisibility(
        modifier = modifier.fillMaxWidth(),
        visible = uiState() is UiState.DefaultMailbox
    ) {
        LargeButton(
            title = stringResource(R.string.buttonSendWithDifferentAddress),
            style = ButtonType.Tertiary,
        ) {
            onChooseAnotherMailbox(true)
            onMailboxSelected(null)
        }
    }
}

@Composable
@PreviewSmallWindow
@PreviewLargeWindow
private fun PreviewDefaultMailbox(
    @PreviewParameter(SelectMailboxScreenPreviewParameter::class) previewData: SelectMailboxScreenDataPreview
) {
    val uiState by previewData.uiState.collectAsStateWithLifecycle()

    MailTheme {
        Surface(Modifier.fillMaxSize()) {
            SelectMailboxScreenContent(
                usersWithMailboxes = previewData.usersWithMailboxes,
                uiState = { uiState },
                onMailboxSelected = {},
                onChooseAnotherMailbox = {},
                onNavigationTopbarClick = {},
                onContinueWithMailbox = {}
            )
        }
    }
}
