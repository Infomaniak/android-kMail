/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.contactcard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.auth.models.user.Card
import com.infomaniak.core.ui.compose.contactcard.ContactCardDefaults
import com.infomaniak.core.ui.compose.contactcard.ContactCardScreen
import com.infomaniak.core.ui.compose.contactcard.ContactCardTopBarState
import com.infomaniak.core.ui.compose.contactcard.ContactCardViewModel
import com.infomaniak.core.ui.compose.contactcard.R
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.components.compose.MailTopAppBar
import com.infomaniak.mail.ui.components.compose.MailTopAppBarTitle
import com.infomaniak.mail.ui.components.compose.TopAppBarButton
import com.infomaniak.mail.ui.components.compose.TopAppBarButtons
import com.infomaniak.mail.ui.theme.LocalMailThemeColors
import com.infomaniak.mail.ui.theme.MailTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.infomaniak.core.common.R as RCore
import com.infomaniak.mail.R as RMail

@AndroidEntryPoint
class ContactCardFragment : Fragment() {

    private val contactCardViewModel: ContactCardViewModel by viewModels()

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        descriptionDialog.bindAlertToLifecycle(viewLifecycleOwner)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MailTheme {
                    val mailColors = LocalMailThemeColors.current
                    ContactCardScreen(
                        onBack = { findNavController().popBackStack() },
                        onShare = ::shareCard,
                        viewModel = contactCardViewModel,
                        confirmDelete = ::confirmDelete,
                        confirmValidationError = ::confirmValidationError,
                        topBar = { state -> MailContactCardTopBar(state) },
                        colors = ContactCardDefaults.colors(
                            waveBackground = mailColors.onboardingSecondaryBackground,
                            background = mailColors.backgroundHeaderColor,
                        ),
                    )
                }
            }
        }
    }

    private fun shareCard(card: Card) {
        contactCardViewModel.shareCardData(card) { file ->
            val authority = "${requireContext().packageName}.core.contactcard.provider"
            val uri = FileProvider.getUriForFile(requireContext(), authority, file)
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/vcard"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, getString(R.string.contactCardShareChooserTitle)))
        }
    }

    private fun confirmDelete(onConfirmed: () -> Unit) {
        descriptionDialog.show(
            title = getString(R.string.deleteAlertTitle),
            description = getString(R.string.deleteAlertDescription),
            displayLoader = false,
            positiveButtonText = RMail.string.actionDelete,
            onPositiveButtonClicked = { onConfirmed() },
        )
    }

    private fun confirmValidationError() {
        descriptionDialog.show(
            title = getString(R.string.alertTitle),
            description = getString(R.string.alertDescription),
            displayLoader = false,
            positiveButtonText = android.R.string.ok,
            onPositiveButtonClicked = { },
        )
    }
}

@Composable
private fun MailContactCardTopBar(state: ContactCardTopBarState) {
    when (state) {
        is ContactCardTopBarState.Editor -> MailContactCardTopBarEditor(state)
        is ContactCardTopBarState.Preview -> MailContactCardTopBarPreview(state)
        is ContactCardTopBarState.Default -> MailContactCardTopBarDefault(state)
        is ContactCardTopBarState.Onboarding -> MailContactCardTopBarOnboarding(state)
    }
}

@Composable
private fun MailContactCardTopBarEditor(state: ContactCardTopBarState.Editor) {
    MailTopAppBar(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        title = { MailTopAppBarTitle(stringResource(R.string.contactCardTitle)) },
        navigationIcon = {
            TextButton(onClick = state.onCancel) {
                Text(
                    text = stringResource(RCore.string.buttonCancel),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        actions = {
            TextButton(onClick = state.onSave) {
                Text(
                    text = stringResource(RCore.string.buttonSave),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun MailContactCardTopBarPreview(state: ContactCardTopBarState.Preview) {
    MailTopAppBar(
        title = { MailTopAppBarTitle(stringResource(R.string.contactCardTitle)) },
        navigationIcon = { TopAppBarButtons.Close(onClick = state.onClose) },
        actions = {
            TopAppBarButton(
                icon = ImageVector.vectorResource(RMail.drawable.ic_param_dots),
                contentDescResId = R.string.buttonMore,
                onClick = state.onMore,
            )
        },
    )
}

@Composable
private fun MailContactCardTopBarDefault(state: ContactCardTopBarState.Default) {
    MailTopAppBar(
        title = { MailTopAppBarTitle(stringResource(R.string.contactCardTitle)) },
        navigationIcon = { TopAppBarButtons.Back(onClick = state.onBack) },
    )
}

@Composable
private fun MailContactCardTopBarOnboarding(state: ContactCardTopBarState.Onboarding) {
    MailTopAppBar(
        navigationIcon = { TopAppBarButtons.Back(onClick = state.onBack) },
    )
}

@Preview(showBackground = true)
@Composable
private fun MailContactCardTopBarEditorPreview() {
    MailTheme {
        MailContactCardTopBarEditor(
            ContactCardTopBarState.Editor(
                onCancel = {},
                onSave = {},
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MailContactCardTopBarPreviewPreview() {
    MailTheme {
        MailContactCardTopBarPreview(
            ContactCardTopBarState.Preview(
                onClose = {},
                onMore = {},
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MailContactCardTopBarDefaultPreview() {
    MailTheme {
        MailContactCardTopBarDefault(
            ContactCardTopBarState.Default(
                onBack = {},
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MailContactCardTopBarOnboardingPreview() {
    MailTheme {
        MailContactCardTopBarOnboarding(
            ContactCardTopBarState.Onboarding(
                onBack = {},
            ),
        )
    }
}
