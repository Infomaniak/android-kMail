/*
 * Infomaniak Mail - Android
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
@file:UseSerializers(RealmListSerializer::class)

package com.infomaniak.mail.data.models

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.RealmListSerializer
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.utils.Utils
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
import kotlin.math.max

@Serializable
class Folder : RealmObject {

    //region Remote data
    @PrimaryKey
    var id: String = ""
    var path: String = ""
    var name: String = ""
    @SerialName("role")
    private var _role: String? = null
    @SerialName("is_favorite")
    var isFavorite: Boolean = false
    @SerialName("unread_count")
    var unreadCountRemote: Int = 0
    var separator: String = ""
    var children: RealmList<Folder> = realmListOf()
    //endregion

    //region Local data (Transient)
    @Transient
    var lastUpdatedAt: RealmInstant? = null
    @Transient
    var cursor: String? = null
    @Transient
    var unreadCountLocal: Int = 0
    @Transient
    var threads: RealmList<Thread> = realmListOf()
    @Transient
    var messages: RealmList<Message> = realmListOf()
    // We start by downloading 1 page when 1st opening a Folder, before trying to download old Messages.
    // So when trying to get old Messages, we need to fetch 1 less page. Hence this computation.
    @Transient
    var remainingOldMessagesToFetch: Int = DEFAULT_REMAINING_OLD_MESSAGES_TO_FETCH
    @Transient
    var isHistoryComplete: Boolean = DEFAULT_IS_HISTORY_COMPLETE
    @Transient
    var isHidden: Boolean = false // For children only (a children Folder is hidden if its parent is collapsed)
    @Transient
    var isCollapsed: Boolean = false // For parents only (collapsing a parent Folder will hide its children)
    //endregion

    private val _parents by backlinks(Folder::children)
    val parent get() = _parents.singleOrNull()

    val role: FolderRole?
        get() = enumValueOfOrNull<FolderRole>(_role)

    val unreadCountDisplay: UnreadDisplay
        get() = UnreadDisplay(
            count = unreadCountLocal,
            shouldDisplayPastille = unreadCountLocal == 0 && unreadCountRemote > 0,
        )

    val canBeCollapsed: Boolean // For parents only (only a parent can be collapsed, its children will be hidden instead)
        get() = role == null && children.isNotEmpty() && isRoot

    val isRoot: Boolean
        get() = !path.contains(separator)

    fun initLocalValues(
        lastUpdatedAt: RealmInstant?,
        cursor: String?,
        unreadCount: Int,
        threads: RealmList<Thread>,
        messages: RealmList<Message>,
        remainingOldMessagesToFetch: Int,
        isHistoryComplete: Boolean,
        isHidden: Boolean,
        isCollapsed: Boolean,
    ) {
        this.lastUpdatedAt = lastUpdatedAt
        SentryDebug.addCursorBreadcrumb("initLocalValues", folder = this, cursor)
        this.cursor = cursor
        this.unreadCountLocal = unreadCount
        this.threads.addAll(threads)
        this.messages.addAll(messages)
        this.remainingOldMessagesToFetch = remainingOldMessagesToFetch
        this.isHistoryComplete = isHistoryComplete
        this.isHidden = isHidden
        this.isCollapsed = isCollapsed
    }

    fun getLocalizedName(context: Context): String {
        return role?.folderNameRes?.let(context::getString) ?: name
    }

    @DrawableRes
    fun getIcon(): Int {
        return role?.folderIconRes ?: if (isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder
    }

    override fun equals(other: Any?) = other === this || (other is Folder && other.id == id)

    override fun hashCode(): Int = id.hashCode()

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

        val DEFAULT_REMAINING_OLD_MESSAGES_TO_FETCH = max(Utils.NUMBER_OF_OLD_MESSAGES_TO_FETCH - Utils.PAGE_SIZE, 0)
        const val DEFAULT_IS_HISTORY_COMPLETE = false
    }
}
