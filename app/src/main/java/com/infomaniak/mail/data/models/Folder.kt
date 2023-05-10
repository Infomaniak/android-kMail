/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers

@Serializable
class Folder : RealmObject {

    //region API data
    @PrimaryKey
    var id: String = ""
    var path: String = ""
    var name: String = ""
    @SerialName("role")
    private var _role: String? = null
    @SerialName("is_favorite")
    var isFavorite: Boolean = false
    var separator: String = ""
    var children: RealmList<Folder> = realmListOf()
    //endregion

    //region Local data (Transient)
    @Transient
    var lastUpdatedAt: RealmInstant? = null
    @Transient
    var cursor: String? = null
    @Transient
    var unreadCount: Int = 0
    @Transient
    var threads: RealmList<Thread> = realmListOf()
    @Transient
    var messages: RealmList<Message> = realmListOf()
    @Transient
    var shouldGetHistory: Boolean = true
    @Transient
    var isHistoryComplete: Boolean = false
    // TODO: Remove this before going into production
    @Transient
    @Suppress("PropertyName")
    var _forceClearAllDatabasesBecauseWeTryToFixOrphans: String = "fake_variable_used_to_automatically_clean_Realm_DB"
    //endregion

    private val _parents by backlinks(Folder::children)
    val parent get() = _parents.singleOrNull()

    val role: FolderRole?
        get() = enumValueOfOrNull<FolderRole>(_role)

    fun initLocalValues(
        lastUpdatedAt: RealmInstant?,
        cursor: String?,
        unreadCount: Int,
        threads: RealmList<Thread>,
        messages: RealmList<Message>,
    ) {
        this.lastUpdatedAt = lastUpdatedAt
        this.cursor = cursor
        this.unreadCount = unreadCount
        this.threads.addAll(threads)
        this.messages.addAll(messages)
    }

    fun getLocalizedName(context: Context): String {
        return role?.folderNameRes?.let(context::getString) ?: name
    }

    @DrawableRes
    fun getIcon(): Int {
        return role?.folderIconRes ?: if (isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder
    }

    enum class FolderRole(
        @StringRes val folderNameRes: Int,
        @DrawableRes val folderIconRes: Int,
        val order: Int,
        val matomoValue: String,
    ) {
        INBOX(R.string.inboxFolder, R.drawable.ic_drawer_inbox, 0, "inboxFolder"),
        DRAFT(R.string.draftFolder, R.drawable.ic_draft, 4, "draftFolder"),
        SENT(R.string.sentFolder, R.drawable.ic_sent_messages, 3, "sentFolder"),
        SPAM(R.string.spamFolder, R.drawable.ic_spam, 5, "spamFolder"),
        TRASH(R.string.trashFolder, R.drawable.ic_bin, 6, "trashFolder"),
        ARCHIVE(R.string.archiveFolder, R.drawable.ic_archive_folder, 7, "archiveFolder"),
        COMMERCIAL(R.string.commercialFolder, R.drawable.ic_promotions, 1, "commercialFolder"),
        SOCIALNETWORKS(R.string.socialNetworksFolder, R.drawable.ic_social_media, 2, "socialNetworksFolder"),
    }

    companion object {
        val rolePropertyName = Folder::_role.name
        val parentsPropertyName = Folder::_parents.name
    }
}
