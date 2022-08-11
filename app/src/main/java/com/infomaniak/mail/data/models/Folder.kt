/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
@file:UseSerializers(RealmListSerializer::class, RealmInstantSerializer::class)

package com.infomaniak.mail.data.models

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
class Folder : RealmObject {
    @PrimaryKey
    var id: String = ""
    var path: String = ""
    var name: String = ""
    @Suppress("PropertyName")
    @SerialName("role")
    var _role: String? = null
    @SerialName("unread_count")
    var unreadCount: Int = 0
    @SerialName("total_count")
    var totalCount: Int = 0
    @SerialName("is_fake")
    var isFake: Boolean = false
    @SerialName("is_collapsed")
    var isCollapsed: Boolean = false
    @SerialName("is_favorite")
    var isFavorite: Boolean = false
    var separator: String = ""
    var children: RealmList<Folder> = realmListOf()

    /**
     * Local
     */
    var threads: RealmList<Thread> = realmListOf()
    var parentLink: Folder? = null
    var lastUpdatedAt: RealmInstant? = null

    val isDraftFolder get() = role == FolderRole.DRAFT

    val role: FolderRole?
        get() = enumValueOfOrNull<FolderRole>(_role)

    fun initLocalValues(threads: RealmList<Thread>, parentLink: Folder?, lastUpdatedAt: RealmInstant?) {
        this.threads = threads
        this.parentLink = parentLink
        this.lastUpdatedAt = lastUpdatedAt
    }

    fun getLocalizedName(context: Context): String {
        return enumValueOfOrNull<FolderRole>(_role)?.folderNameRes?.let(context::getString) ?: name
    }

    fun getUnreadCountOrNull(): String? = if (unreadCount > 0) unreadCount.toString() else null

    enum class FolderRole(@StringRes val folderNameRes: Int, @DrawableRes val folderIconRes: Int, val order: Int) {
        INBOX(R.string.inboxFolder, R.drawable.ic_drawer_mailbox, 0),
        DRAFT(R.string.draftFolder, R.drawable.ic_edit_draft, 4),
        SENT(R.string.sentFolder, R.drawable.ic_sent_messages, 3),
        SPAM(R.string.spamFolder, R.drawable.ic_spam, 5),
        TRASH(R.string.trashFolder, R.drawable.ic_bin, 6),
        ARCHIVE(R.string.archiveFolder, R.drawable.ic_archive_folder, 7),
        COMMERCIAL(R.string.commercialFolder, R.drawable.ic_promotions, 1),
        SOCIALNETWORKS(R.string.socialNetworksFolder, R.drawable.ic_social_media, 2),
    }

    companion object {
        const val API_DRAFT_FOLDER_NAME = "Drafts"

        inline val draftFolder: Folder? get() = FolderController.getFoldersSync().find { it.role == FolderRole.DRAFT }
    }
}
