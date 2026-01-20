/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infomaniak.core.ui.compose.basics.Typography
import com.infomaniak.core.ui.compose.bottomstickybuttonscaffolds.BottomStickyButtonScaffold
import com.infomaniak.core.ui.compose.margin.Margin
import com.infomaniak.core.ui.compose.preview.PreviewLargeWindow
import com.infomaniak.core.ui.compose.preview.PreviewSmallWindow
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.components.compose.ButtonType
import com.infomaniak.mail.ui.components.compose.LargeButton
import com.infomaniak.mail.ui.components.compose.MailTopAppBar
import com.infomaniak.mail.ui.components.compose.TopAppBarButtons
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.SelectedMailboxUi
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UiState
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UiState.DefaultScreen
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UiState.Error
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UiState.Loading
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UiState.SelectionScreen
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.UserMailboxesUi
import com.infomaniak.mail.ui.newMessage.selectMailbox.compose.AccountMailboxesDropdown
import com.infomaniak.mail.ui.newMessage.selectMailbox.compose.SelectedMailboxIndicator
import com.infomaniak.mail.ui.newMessage.selectMailbox.compose.previewparameter.SelectMailboxScreenDataPreview
import com.infomaniak.mail.ui.newMessage.selectMailbox.compose.previewparameter.SelectMailboxScreenPreviewParameter
import com.infomaniak.mail.ui.theme.MailTheme
import kotlinx.coroutines.launch
import com.infomaniak.core.common.R as RCore

@Composable
fun SelectMailboxScreen(
    viewModel: SelectMailboxViewModel,
    onNavigationTopbarClick: () -> Unit,
    onContinue: (SelectedMailboxUi) -> Unit
) {
    val usersWithMailboxes by viewModel.usersWithMailboxes.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    BackHandler(uiState is SelectionScreen) {
        viewModel.showSelectionScreen(false)
    }

    SelectMailboxScreen(
        usersWithMailboxes = usersWithMailboxes,
        uiState = { uiState },
        snackbarHostState = remember { SnackbarHostState() },
        onMailboxSelected = viewModel::selectMailbox,
        showSelectionScreen = viewModel::showSelectionScreen,
        onNavigationTopbarClick = onNavigationTopbarClick,
        onContinueWithMailbox = {
            scope.launch {
                viewModel.ensureMailboxIsFetched(it.userId, it.mailboxUi.mailboxId)
                onContinue(it)
            }
        }
    )
}

@Composable
private fun SelectMailboxScreen(
    usersWithMailboxes: List<UserMailboxesUi>,
    uiState: () -> UiState,
    snackbarHostState: SnackbarHostState,
    onMailboxSelected: (SelectedMailboxUi) -> Unit,
    showSelectionScreen: (Boolean) -> Unit,
    onNavigationTopbarClick: () -> Unit,
    onContinueWithMailbox: (SelectedMailboxUi) -> Unit
) {
    val selectedMailbox by remember { derivedStateOf { (uiState() as? SelectionScreen.Selected)?.mailboxUi } }

    BottomStickyButtonScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MailTopAppBar(
                navigationIcon = {
                    when (uiState()) {
                        is SelectionScreen -> TopAppBarButtons.Back(onNavigationTopbarClick)
                        is DefaultScreen, Loading, Error -> TopAppBarButtons.Close(onNavigationTopbarClick)
                    }
                }
            )
        },
        content = {
            Column {
                ScrollableContent(
                    uiState,
                    snackbarHostState,
                    usersWithMailboxes,
                    onMailboxSelected,
                    Modifier
                        .padding(horizontal = Margin.Medium)
                        .fillMaxWidth()
                        .weight(1f),
                )
                SelectedMailboxBottom(selectedMailbox)
            }
        },
        topButton = { modifier ->
            ContinueButton(modifier, uiState, onContinueWithMailbox)
        },
        bottomButton = { modifier ->
            DifferentAddressButton(modifier, uiState, showSelectionScreen)
        }
    )
}

@Composable
private fun Header() {
    Image(
        imageVector = ImageVector.vectorResource(R.drawable.illustration_mailbox_ellipsis_bubble),
        contentDescription = null
    )
    Spacer(Modifier.height(Margin.Medium))
    Text(
        style = Typography.h2,
        maxLines = 1,
        text = stringResource(R.string.composeMailboxCurrentTitle)
    )
}

@Composable
private fun ScrollableContent(
    uiState: () -> UiState,
    snackbarHostState: SnackbarHostState,
    usersWithMailboxes: List<UserMailboxesUi>,
    onMailboxSelected: (SelectedMailboxUi) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val uiState = uiState()
    val snackbarMessage = stringResource(RCore.string.anErrorHasOccurred)

    LazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Margin.Medium),
    ) {
        item { Header() }

        when (uiState) {
            is Loading -> Unit
            is Error -> scope.launch { snackbarHostState.showSnackbar(message = snackbarMessage) }
            is DefaultScreen -> {
                item { SelectedMailboxIndicator(modifier = Modifier.animateItem(), selectedMailbox = uiState.mailboxUi) }
            }
            is SelectionScreen -> {
                items(
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
private fun ContinueButton(
    modifier: Modifier,
    uiState: () -> UiState,
    onContinueWithMailbox: (SelectedMailboxUi) -> Unit,
) {
    val selectedMailbox by remember { derivedStateOf { (uiState() as? SelectMailboxViewModel.SelectedMailboxState)?.mailboxUi } }

    LargeButton(
        modifier = modifier,
        title = stringResource(R.string.buttonContinue),
        onClick = { selectedMailbox?.let { selectedMailboxUi -> onContinueWithMailbox(selectedMailboxUi) } },
        enabled = { uiState() is SelectMailboxViewModel.SelectedMailboxState },
        showIndeterminateProgress = { uiState() is DefaultScreen.FetchingNewMailbox },
    )
}

@Composable
private fun DifferentAddressButton(
    modifier: Modifier,
    uiState: () -> UiState,
    showSelectionScreen: (Boolean) -> Unit,
) {
    val uiState = uiState()
    AnimatedVisibility(
        modifier = modifier,
        visible = uiState is DefaultScreen,
    ) {
        LargeButton(
            title = stringResource(R.string.buttonSendWithDifferentAddress),
            style = ButtonType.Tertiary,
            onClick = { showSelectionScreen(true) },
            showIndeterminateProgress = { uiState is DefaultScreen.FetchingNewMailbox }
        )
    }
}

@Composable
@PreviewSmallWindow
@PreviewLargeWindow
private fun PreviewDefaultMailbox(
    @PreviewParameter(SelectMailboxScreenPreviewParameter::class) previewData: SelectMailboxScreenDataPreview
) {
    MailTheme {
        Surface(Modifier.fillMaxSize()) {
            SelectMailboxScreen(
                usersWithMailboxes = previewData.usersWithMailboxes,
                uiState = { previewData.uiState },
                snackbarHostState = remember { SnackbarHostState() },
                onMailboxSelected = {},
                showSelectionScreen = {},
                onNavigationTopbarClick = {},
                onContinueWithMailbox = {}
            )
        }
    }
}
