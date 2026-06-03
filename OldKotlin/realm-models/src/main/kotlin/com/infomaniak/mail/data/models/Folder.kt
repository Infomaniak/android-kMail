/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
@file:UseSerializers(RealmListKSerializer::class)
@file:OptIn(InternalModelProperties::class)

package com.infomaniak.mail.data.models

import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.core.common.extensions.enumValueOfOrNull
import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.query.Sort
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlin.reflect.KProperty1

@Serializable
class Folder : RealmObject, Cloneable, TreeStructure<Folder> {

    //region Remote data
    @PrimaryKey
    var id: String = ""
    var path: String = ""
    var name: String = ""
    @InternalModelProperties
    @SerialName("role")
    var _role: String? = null
    @SerialName("is_favorite")
    var isFavorite: Boolean = false
    @SerialName("unread_count")
    var unreadCountRemote: Int = 0
    var separator: String = ""
    override var children = realmListOf<Folder>()
    //endregion

    //region Local data (Transient)

    // ------------- !IMPORTANT! -------------
    // Every field that is added in this Transient region should be declared in
    // `initLocalValue()` too to avoid loosing data when updating from the API.

    @Transient
    var lastUpdatedAt: RealmInstant? = null
    @Transient
    var cursor: String? = null
    @Transient
    var unreadCountLocal: Int = 0
    @Transient
    var threads = realmListOf<Thread>()

    /**
     * List of old Messages UIDs of this Folder that we need to fetch.
     * When first opening the Folder, we get the full list of UIDs, and we store it.
     * Then, we'll be able to go through it as we want to fetch the old Messages.
     */
    @Transient
    var oldMessagesUidsToFetch = realmListOf<Int>()
    @Transient
    var newMessagesUidsToFetch = realmListOf<Int>()
    @Transient
    var remainingOldMessagesToFetch: Int = NUMBER_OF_OLD_MESSAGES_TO_FETCH

    @Transient
    var isCollapsed: Boolean = false // For parents only (collapsing a parent Folder will hide its children)
    @Transient
    var roleOrder: Int = role?.order ?: CUSTOM_FOLDER_ROLE_ORDER
    @Transient
    var sortedName: String = name
    @Transient
    var isDisplayed: Boolean = true // Used to hide folders on specific conditions (i.e. ScheduledDrafts folder when its empty)
    //endregion

    private val _parents by backlinks(Folder::children)
    val parent get() = _parents.singleOrNull()

    val role: FolderRole?
        get() = enumValueOfOrNull<FolderRole>(_role)

    val isRoot: Boolean
        inline get() = !path.contains(separator)

    val folderSort get() = role?.folderSort ?: FolderSort.Default

    fun initLocalValues(
        lastUpdatedAt: RealmInstant?,
        cursor: String?,
        unreadCount: Int,
        threads: RealmList<Thread>,
        oldMessagesUidsToFetch: RealmList<Int>,
        newMessagesUidsToFetch: RealmList<Int>,
        remainingOldMessagesToFetch: Int,
        isDisplayed: Boolean,
        isCollapsed: Boolean,
        sortedName: String
    ) {
        this.lastUpdatedAt = lastUpdatedAt
        this.cursor = cursor
        this.unreadCountLocal = unreadCount
        this.threads.addAll(threads)
        this.oldMessagesUidsToFetch.addAll(oldMessagesUidsToFetch)
        this.newMessagesUidsToFetch.addAll(newMessagesUidsToFetch)
        this.remainingOldMessagesToFetch = remainingOldMessagesToFetch
        this.isDisplayed = isDisplayed
        this.isCollapsed = isCollapsed

        this.sortedName = sortedName
    }

    override fun equals(other: Any?) = other === this || (other is Folder && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    class FolderSort private constructor(
        val sortOrder: Sort = Sort.DESCENDING,
        sortBy: KProperty1<*, *> = Thread::internalDate,
    ) {
        val sortBy = sortBy.name

        companion object {
            val Default = FolderSort()
            val Snooze = FolderSort(Sort.ASCENDING, Thread::snoozeEndDate)
            val Scheduled = FolderSort(Sort.ASCENDING, Thread::displayDate)
        }
    }

    companion object {
        val rolePropertyName = Folder::_role.name
        val parentsPropertyName = Folder::_parents.name

        const val NUMBER_OF_OLD_MESSAGES_TO_FETCH = 500 // Number of Messages we want to fetch when 1st opening a Folder

        const val DUMMY_FOLDER_ID = "eJzz9HPyjwAABGYBgQ--" // Fun fact: It's actually the INBOX folder id. But nobody cares.
        const val CUSTOM_FOLDER_ROLE_ORDER = 0

    }
}
