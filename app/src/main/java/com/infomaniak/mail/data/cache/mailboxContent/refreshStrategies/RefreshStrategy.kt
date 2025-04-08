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
package com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies

import android.content.Context
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import kotlinx.coroutines.CoroutineScope

interface RefreshStrategy {
    fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread>

    /**
     * The list of other folder roles that need to query their threads again when the current folder has its threads queried.
     *
     * Some folders such as INBOX and Snooze require to query again the other folder's threads as well. For example, if a
     * message uid is returned as "added" or "deleted" in the snooze folder, it should disappear or appear from inbox as well.
     */
    fun twinFolderRoles(): List<FolderRole>

    fun shouldHideEmptyFolder(): Boolean

    fun getMessageFromShortUid(shortUid: String, folderId: String, realm: TypedRealm): Message?

    fun processDeletedMessage(
        scope: CoroutineScope,
        managedMessage: Message,
        context: Context,
        mailbox: Mailbox,
        realm: MutableRealm,
    )

    /**
     * If an extra folder needs its unread count updated but no thread has that extra folder as [Thread.folderId], you can add the
     * extra folder inside this method as they will be inserted inside the list of impacted folders.
     */
    fun addFolderToImpactedFolders(folderId: String, impactedFolders: ImpactedFolders)

    fun processDeletedThread(thread: Thread, realm: MutableRealm)

    fun shouldQueryFolderThreadsOnDeletedUid(): Boolean

    /**
     * About the [impactedThreadsManaged]:
     *  This set will be updated throughout the whole process of handling added Messages.
     *  It represents all the Threads that will need to be recomputed to reflect the changes of the newly added Messages.
     *  We need to pass down a reference to the MutableSet to enable both addition and removal of Threads in it.
     */
    fun handleAddedMessage(
        scope: CoroutineScope,
        remoteMessage: Message,
        isConversationMode: Boolean,
        impactedThreadsManaged: MutableSet<Thread>,
        realm: MutableRealm,
    )
}
