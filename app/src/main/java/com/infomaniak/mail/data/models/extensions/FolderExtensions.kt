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
package com.infomaniak.mail.data.models.extensions

import android.content.Context
import androidx.annotation.DrawableRes
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.RefreshStrategy
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.defaultRefreshStrategy
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.UnreadDisplay
import io.realm.kotlin.TypedRealm

fun Folder.messagesBlocking(realm: TypedRealm): List<Message> = MessageController.getMessagesByFolderIdBlocking(id, realm)

@DrawableRes
fun Folder.getIcon(): Int {
    return role?.folderIconRes ?: if (isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder
}

val Folder.unreadCountDisplay: UnreadDisplay
    inline get() = UnreadDisplay(
        count = unreadCountLocal,
        shouldDisplayPastille = unreadCountLocal == 0 && unreadCountRemote > 0,
    )

val Folder.refreshStrategy: RefreshStrategy get() = role?.refreshStrategy ?: defaultRefreshStrategy

fun Folder.getLocalizedName(context: Context): String {
    return role?.folderNameRes?.let(context::getString) ?: name
}
