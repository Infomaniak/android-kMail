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
package com.infomaniak.mail.utils

import android.content.Context
import androidx.compose.ui.graphics.Color
import coil3.imageLoader
import com.infomaniak.core.avatar.getBackgroundColorResBasedOnId
import com.infomaniak.core.avatar.models.AvatarColors
import com.infomaniak.core.avatar.models.AvatarType
import com.infomaniak.core.avatar.models.AvatarUrlData
import com.infomaniak.core.coil.ImageLoaderProvider.simpleImageLoader
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.utils.extensions.MergedContactDictionary

object AvatarTypeUtils {
    fun AvatarType.Companion.fromUser(user: User, context: Context): AvatarType.WithInitials = getUrlOrInitials(
        avatarUrlData = user.avatar?.let { AvatarUrlData(it, context.simpleImageLoader) },
        initials = user.getInitials(),
        colors = AvatarColors(
            containerColor = Color(context.getBackgroundColorResBasedOnId(user.id, R.array.AvatarColors)),
            contentColor = context.getContentColor(),
        ),
    )

    private fun AvatarType.WithInitials.Initials.Companion.fromCorrespondent(
        correspondent: Correspondent,
        context: Context,
    ): AvatarType.WithInitials.Initials = AvatarType.WithInitials.Initials(
        initials = correspondent.initials,
        colors = context.correspondentAvatarColors(correspondent),
    )

    private fun AvatarType.WithInitials.Url.Companion.fromCorrespondent(
        avatarUrlData: AvatarUrlData,
        correspondent: Correspondent,
        context: Context,
    ): AvatarType.WithInitials.Url = AvatarType.WithInitials.Url(
        url = avatarUrlData.url,
        imageLoader = avatarUrlData.imageLoader,
        initials = correspondent.initials,
        colors = context.correspondentAvatarColors(correspondent),
    )

    private fun AvatarType.Companion.getUrlOrInitialsFromCorrespondent(
        avatarUrlData: AvatarUrlData?,
        correspondent: Correspondent,
        context: Context,
    ): AvatarType.WithInitials = getUrlOrInitials(
        avatarUrlData = avatarUrlData,
        initials = correspondent.initials,
        colors = context.correspondentAvatarColors(correspondent),
    )

    private fun Context.getContentColor() = Color(getColor(R.color.onColorfulBackground))

    private fun Context.getContainerColor(correspondent: Correspondent) = Color(
        getBackgroundColorResBasedOnId(correspondent.email.hashCode(), R.array.AvatarColors)
    )

    fun Context.correspondentAvatarColors(correspondent: Correspondent): AvatarColors {
        return AvatarColors(getContainerColor(correspondent), getContentColor())
    }

    fun getAvatarType(
        correspondent: Correspondent?,
        bimi: Bimi?,
        isBimiEnabled: Boolean,
        contacts: MergedContactDictionary,
        context: Context,
    ): AvatarType? {
        val avatarDisplayType = getAvatarDisplayType(correspondent, bimi, isBimiEnabled, contacts)
        return when (avatarDisplayType) {
            AvatarDisplayType.UNKNOWN_CORRESPONDENT -> AvatarType.DrawableResource(R.drawable.ic_unknown_user_avatar)
            AvatarDisplayType.USER_AVATAR -> AccountUtils.currentUser?.let { user -> AvatarType.fromUser(user, context) }
            AvatarDisplayType.CUSTOM_AVATAR -> {
                val avatarUrlData = searchInMergedContact(correspondent!!, contacts)?.avatar?.let {
                    AvatarUrlData(it, context.imageLoader)
                }
                AvatarType.getUrlOrInitialsFromCorrespondent(avatarUrlData, correspondent, context)
            }
            AvatarDisplayType.INITIALS -> AvatarType.WithInitials.Initials.fromCorrespondent(correspondent!!, context)
            AvatarDisplayType.BIMI -> {
                val avatarUrlData = AvatarUrlData(ApiRoutes.bimi(bimi!!.svgContentUrl!!), context.imageLoader)
                AvatarType.WithInitials.Url.fromCorrespondent(avatarUrlData, correspondent!!, context)
            }
        }
    }

    private fun getAvatarDisplayType(
        correspondent: Correspondent?,
        bimi: Bimi?,
        isBimiEnabled: Boolean,
        contacts: MergedContactDictionary,
    ): AvatarDisplayType = when {
        correspondent == null -> AvatarDisplayType.UNKNOWN_CORRESPONDENT
        correspondent.shouldDisplayUserAvatar() -> AvatarDisplayType.USER_AVATAR
        correspondent.hasMergedContactAvatar(contacts) -> AvatarDisplayType.CUSTOM_AVATAR
        bimi?.isDisplayable(isBimiEnabled) == true -> AvatarDisplayType.BIMI
        else -> AvatarDisplayType.INITIALS
    }

    private fun Correspondent.hasMergedContactAvatar(contacts: MergedContactDictionary): Boolean {
        return searchInMergedContact(correspondent = this, contacts)?.avatar != null
    }

    private fun searchInMergedContact(correspondent: Correspondent, contacts: MergedContactDictionary): MergedContact? {
        val recipientsForEmail = contacts[correspondent.email]
        return recipientsForEmail?.getOrElse(correspondent.name) { recipientsForEmail.entries.elementAt(0).value }
    }

    private enum class AvatarDisplayType {
        UNKNOWN_CORRESPONDENT,
        CUSTOM_AVATAR,
        USER_AVATAR,
        BIMI,
        INITIALS,
    }
}
