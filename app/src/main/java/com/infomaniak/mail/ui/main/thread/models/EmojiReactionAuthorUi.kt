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
package com.infomaniak.mail.ui.main.thread.models

import android.content.Context
import com.infomaniak.core.avatar.models.AvatarType
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.AvatarTypeUtils
import com.infomaniak.mail.utils.AvatarTypeUtils.fromUser
import com.infomaniak.mail.utils.extensions.MergedContactDictionary

sealed interface EmojiReactionAuthorUi {
    fun getName(context: Context): String
    fun getAvatarType(
        context: Context,
        mergedContactDictionary: MergedContactDictionary,
        isBimiEnabled: Boolean,
    ): AvatarType?

    data class Real(val correspondent: Correspondent, val bimi: Bimi?) : EmojiReactionAuthorUi {
        override fun getName(context: Context): String = correspondent.displayedName(context)

        override fun getAvatarType(
            context: Context,
            mergedContactDictionary: MergedContactDictionary,
            isBimiEnabled: Boolean,
        ): AvatarType? = AvatarTypeUtils.getAvatarType(correspondent, bimi, isBimiEnabled, mergedContactDictionary, context)
    }

    data object FakeMe : EmojiReactionAuthorUi {
        override fun getName(context: Context): String = context.getString(R.string.contactMe)

        override fun getAvatarType(
            context: Context,
            mergedContactDictionary: MergedContactDictionary,
            isBimiEnabled: Boolean,
        ): AvatarType = AvatarType.fromUser(AccountUtils.currentUser!!, context)
    }
}
