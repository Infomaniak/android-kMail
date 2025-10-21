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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeCompilerApi
import androidx.compose.runtime.derivedStateOf
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
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.*
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
                AnimatedContent(uiState()) { uiState ->
                    when (uiState) {
                        is UiState.Loading -> {}
                        is UiState.DefaultMailbox -> SelectedMailboxIndicator(
                            modifier = Modifier.padding(Margin.Medium),
                            selectedMailbox = uiState.defaultMailbox
                        )
                        is UiState.SelectMailbox -> AccountMailboxesList(
                            usersWithMailboxes,
                            onMailboxSelected
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                AnimatedContent(
                    targetState = selectedMailbox,
                    transitionSpec = { (slideInHorizontally() + fadeIn()).togetherWith(slideOutHorizontally() + fadeOut()) }
                ) { mailbox ->
                    if (mailbox != null) {
                        SelectedMailboxIndicator(
                            modifier = Modifier.padding(Margin.Medium),
                            selectedMailbox = mailbox
                        )
                    }
                }
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
private fun AccountMailboxesList(
    usersWithMailboxes: List<UserMailboxesUi>,
    onMailboxSelected: (SelectedMailboxUi?) -> Unit
) {
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
}

@Composable
private fun TopButton(
    modifier: Modifier,
    uiState: () -> UiState,
    onContinueWithMailbox: (SelectedMailboxUi) -> Unit
) {
    val selectedMailbox by remember { derivedStateOf { (uiState() as? UiState.SelectMailbox)?.selectedMailbox?.value } }

    LargeButton(
        modifier = modifier.padding(horizontal = Margin.Medium),
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Margin.Medium),
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
@Preview(name = "(1) Light")
@Preview(name = "(2) Dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
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
