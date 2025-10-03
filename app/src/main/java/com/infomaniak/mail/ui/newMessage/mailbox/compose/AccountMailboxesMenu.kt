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
package com.infomaniak.mail.ui.newMessage.mailbox.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.infomaniak.core.avatar.components.Avatar
import com.infomaniak.core.avatar.getBackgroundColorResBasedOnId
import com.infomaniak.core.avatar.models.AvatarColors
import com.infomaniak.core.avatar.models.AvatarType
import com.infomaniak.core.avatar.models.AvatarUrlData
import com.infomaniak.core.coil.ImageLoaderProvider
import com.infomaniak.core.compose.basics.Dimens
import com.infomaniak.core.compose.basics.Typography
import com.infomaniak.core.compose.margin.Margin
import com.infomaniak.core.compose.preview.PreviewAllWindows
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.newMessage.mailbox.UserMailboxesUi
import com.infomaniak.mail.ui.theme.MailTheme

@Composable
fun AccountMailboxesMenu(
    modifier: Modifier = Modifier,
    userWithMailboxes: UserMailboxesUi
) {
    val context = LocalContext.current
    val unauthenticatedImageLoader = remember(context) { ImageLoaderProvider.newImageLoader(context) }

    var rowSize by remember { mutableStateOf(Size.Unspecified) }

    val isDropDownExpanded = remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeight)
                .clip(shape = RoundedCornerShape(Dimens.largeCornerRadius))
                .border(
                    border = BorderStroke(1.dp, colorResource(R.color.dropdownBorderColor)),
                    shape = RoundedCornerShape(Dimens.largeCornerRadius)
                )
                .clickable {
                    isDropDownExpanded.value = true
                }
                .onGloballyPositioned { layoutCoordinates ->
                    rowSize = layoutCoordinates.size.toSize()
                }
                .padding(horizontal = Margin.Medium)

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
                    maxLines = 1
                )
                Text(
                    text = userWithMailboxes.userEmail,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_down),
                contentDescription = null
            )
        }
        DropdownMenu(
            modifier = Modifier
                .background(Color.White)
                .width(with(LocalDensity.current) { rowSize.width.toDp() }),
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
                            Text(
                                modifier = Modifier.padding(horizontal = Margin.Mini),
                                text = mailbox.email
                            )
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

@PreviewAllWindows
@Composable
private fun Preview(@PreviewParameter(SelectMailboxPreviewParameter::class) usersWithMailboxes: List<UserMailboxesUi>) {
    MailTheme {
        Surface {
            AccountMailboxesMenu(userWithMailboxes = usersWithMailboxes.first())
        }
    }
}
