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
package com.infomaniak.mail.ui.newMessage.selectMailbox.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import coil3.ImageLoader
import com.infomaniak.core.avatar.components.Avatar
import com.infomaniak.core.avatar.getBackgroundColorResBasedOnId
import com.infomaniak.core.avatar.models.AvatarColors
import com.infomaniak.core.avatar.models.AvatarType
import com.infomaniak.core.avatar.models.AvatarUrlData
import com.infomaniak.core.coil.ImageLoaderProvider
import com.infomaniak.core.ui.compose.basics.Dimens
import com.infomaniak.core.ui.compose.margin.Margin
import com.infomaniak.core.ui.compose.preview.PreviewAllWindows
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.newMessage.selectMailbox.SelectMailboxViewModel.SelectedMailboxUi
import com.infomaniak.mail.ui.newMessage.selectMailbox.compose.previewparameter.SelectedMailboxPreviewParameter
import com.infomaniak.mail.ui.theme.MailTheme

@Composable
fun SelectedMailboxIndicator(
    modifier: Modifier = Modifier,
    selectedMailbox: SelectedMailboxUi
) {
    val context = LocalContext.current
    val unauthenticatedImageLoader = remember(context) { ImageLoaderProvider.newImageLoader(context) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeight)
                .clip(shape = RoundedCornerShape(Dimens.largeCornerRadius))
                .background(colorResource(R.color.informationBlockBackground))
                .padding(horizontal = Margin.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectedMailboxIndicatorContent(selectedMailbox, unauthenticatedImageLoader)
        }
    }
}

@Composable
private fun RowScope.SelectedMailboxIndicatorContent(
    selectedMailbox: SelectedMailboxUi,
    unauthenticatedImageLoader: ImageLoader,
) {
    val context = LocalContext.current

    Avatar(
        modifier = Modifier.size(Dimens.avatarSize),
        avatarType = AvatarType.getUrlOrInitials(
            avatarUrlData = selectedMailbox.avatarUrl?.let { AvatarUrlData(it, unauthenticatedImageLoader) },
            initials = selectedMailbox.initials,
            colors = AvatarColors(
                containerColor = Color(context.getBackgroundColorResBasedOnId(selectedMailbox.userId)),
                contentColor = colorResource(R.color.onColorfulBackground)
            )
        )
    )
    Text(
        modifier = Modifier.padding(horizontal = Margin.Mini),
        text = selectedMailbox.mailboxUi.emailIdn
    )
    Spacer(modifier = Modifier.weight(1f))
    Icon(
        painter = painterResource(R.drawable.ic_check_rounded),
        tint = colorResource(R.color.greenSuccess),
        contentDescription = null
    )
}

@PreviewAllWindows
@Composable
private fun Preview(@PreviewParameter(SelectedMailboxPreviewParameter::class) selectedMailbox: SelectedMailboxUi) {
    MailTheme {
        Surface {
            SelectedMailboxIndicator(selectedMailbox = selectedMailbox)
        }
    }
}
