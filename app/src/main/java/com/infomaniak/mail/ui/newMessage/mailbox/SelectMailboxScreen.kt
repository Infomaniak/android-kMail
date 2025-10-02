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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infomaniak.core.avatar.components.Avatar
import com.infomaniak.core.avatar.getBackgroundColorResBasedOnId
import com.infomaniak.core.avatar.models.AvatarColors
import com.infomaniak.core.avatar.models.AvatarType
import com.infomaniak.core.avatar.models.AvatarUrlData
import com.infomaniak.core.coil.ImageLoaderProvider
import com.infomaniak.core.compose.basics.Dimens
import com.infomaniak.core.compose.basics.Typography
import com.infomaniak.core.compose.bottomstickybuttonscaffolds.BottomStickyButtonScaffold
import com.infomaniak.core.compose.margin.Margin
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.components.compose.ButtonType
import com.infomaniak.mail.ui.components.compose.LargeButton
import com.infomaniak.mail.ui.components.compose.MailTopAppBar
import com.infomaniak.mail.ui.components.compose.TopAppBarButtons
import com.infomaniak.mail.ui.newMessage.mailbox.compose.SelectMailboxPreviewParameter
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
                    SelectedMailbox(userWithMailbox)
                }
                usersWithMailboxes.forEach { userWithMailbox ->
                    AccountMailboxesMenu(userWithMailbox)
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
fun SelectedMailbox(mailboxSelected: UserMailboxesUi) {
    val context = LocalContext.current
    val unauthenticatedImageLoader = remember(context) { ImageLoaderProvider.newImageLoader(context) }

    Box(Modifier.padding(Margin.Medium)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeight)
                .clip(shape = RoundedCornerShape(Dimens.largeCornerRadius))
                .background(colorResource(R.color.informationBlockBackground))
                .padding(horizontal = Margin.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                modifier = Modifier.size(Dimens.avatarSize),
                avatarType = AvatarType.getUrlOrInitials(
                    avatarUrlData = mailboxSelected.avatarUrl?.let { AvatarUrlData(it, unauthenticatedImageLoader) },
                    initials = mailboxSelected.initials,
                    colors = AvatarColors(
                        containerColor = Color(context.getBackgroundColorResBasedOnId(mailboxSelected.userId)),
                        contentColor = if (isSystemInDarkTheme()) Color(0xFF333333) else Color.White
                    )
                )
            )
            Text(
                modifier = Modifier.padding(horizontal = Margin.Mini),
                text = mailboxSelected.mailboxes.first().email
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_check_rounded),
                tint = colorResource(R.color.greenSuccess),
                contentDescription = null
            )
        }

    }
}

@Composable
fun AccountMailboxesMenu(userWithMailboxes: UserMailboxesUi) {
    val context = LocalContext.current
    val unauthenticatedImageLoader = remember(context) { ImageLoaderProvider.newImageLoader(context) }

    val isDropDownExpanded = remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeight)
                .border(
                    border = BorderStroke(1.dp, colorResource(R.color.dropdownBorderColor)),
                    shape = RoundedCornerShape(Dimens.largeCornerRadius)
                )
                .padding(horizontal = Margin.Medium)
                .clickable {
                    isDropDownExpanded.value = true
                }
        ) {
            Avatar(
                modifier = Modifier.size(Dimens.avatarSize),
                avatarType = AvatarType.getUrlOrInitials(
                    avatarUrlData = userWithMailboxes.avatarUrl?.let { AvatarUrlData(it, unauthenticatedImageLoader) },
                    initials = userWithMailboxes.initials,
                    colors = AvatarColors(
                        containerColor = Color(context.getBackgroundColorResBasedOnId(userWithMailboxes.userId)),
                        contentColor = if (isSystemInDarkTheme()) Color(0xFF333333) else Color.White
                    )
                )
            )
            Column(
                modifier = Modifier.padding(horizontal = Margin.Mini)
            ) {
                Text(
                    text = userWithMailboxes.fullName,
                    style = Typography.bodyMedium,
                )
                Text(text = userWithMailboxes.userEmail)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_down),
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = isDropDownExpanded.value,
            onDismissRequest = {
                isDropDownExpanded.value = false
            }
        ) {
            userWithMailboxes.mailboxes.forEachIndexed { index, mailbox ->
                DropdownMenuItem(
                    text = {
                        Row {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_envelope),
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = null
                            )
                            Text(mailbox.email)
                        }
                    },
                    onClick = {
                        isDropDownExpanded.value = false
                    }
                )
            }
        }
    }
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
