/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.search.NamedFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.realmListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchUtils @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    @ApplicationContext private val appContext: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    fun searchFilters(query: String?, filters: Set<ThreadFilter>, resource: String?): String {

        val filtersQuery = StringBuilder("severywhere=${if (filters.contains(ThreadFilter.FOLDER)) "0" else "1"}")

        if (query?.isNotBlank() == true) filtersQuery.append("&scontains=$query")

        with(filters) {
            if (contains(ThreadFilter.ATTACHMENTS)) filtersQuery.append("&sattachments=yes")

            if (resource == null) when {
                contains(ThreadFilter.SEEN) -> filtersQuery.append("&filters=seen")
                contains(ThreadFilter.UNSEEN) -> filtersQuery.append("&filters=unseen")
                contains(ThreadFilter.STARRED) -> filtersQuery.append("&filters=starred")
                else -> Unit
            }
        }

        return filtersQuery.toString()
    }

    fun selectFilter(filter: ThreadFilter, selectedFilters: MutableSet<ThreadFilter>): MutableSet<ThreadFilter> {
        val filtersToRemove = when (filter) {
            ThreadFilter.SEEN -> setOf(ThreadFilter.UNSEEN, ThreadFilter.STARRED)
            ThreadFilter.UNSEEN -> setOf(ThreadFilter.SEEN, ThreadFilter.STARRED)
            ThreadFilter.STARRED -> setOf(ThreadFilter.SEEN, ThreadFilter.UNSEEN)
            else -> emptySet()
        }

        return selectedFilters.apply {
            removeAll(filtersToRemove)
            add(filter)
        }
    }

    suspend fun deleteRealmSearchData() = withContext(ioDispatcher) {
        mailboxContentRealm().write {
            SentryLog.i(TAG, "SearchUtils>deleteRealmSearchData: remove old search data")
            MessageController.deleteSearchMessages(realm = this)
            ThreadController.deleteSearchThreads(realm = this)
            FolderController.deleteSearchFolderData(realm = this)
        }
    }

    fun convertLocalMessagesToSearchThreads(searchMessages: List<Message>): List<Thread> {
        val cachedNamedFolders = mutableMapOf<String, NamedFolder>()
        return searchMessages.map { message ->
            message.toThread().apply {
                uid = "search-${message.uid}"
                isFromSearch = true
                recomputeThread()
                sharedThreadProcessing(appContext, cachedNamedFolders, realm = mailboxContentRealm())
            }
        }
    }

    /**
     * Initialize the search Threads obtained from the API.
     * - Format the remote Threads to make them compatible with the existing logic.
     * - Preserve old Messages data if it already exists locally.
     * - Handle duplicates using the existing logic.
     * @param remoteThreads The list of API Threads that need to be processed.
     * @param filterFolder The selected Folder on which we filter the Search.
     */
    suspend fun convertRemoteThreadsToSearchThreads(remoteThreads: List<Thread>, filterFolder: Folder?): List<Thread> {
        val cachedNamedFolders = mutableMapOf<String, NamedFolder>()
        return remoteThreads.map { remoteThread ->
            currentCoroutineContext().ensureActive()

            remoteThread.apply {
                isFromSearch = true
                setFolderId(filterFolder)
                keepOldMessagesData(filterFolder, mailboxContentRealm())
                sharedThreadProcessing(appContext, cachedNamedFolders, realm = mailboxContentRealm())
            }
        }
    }

    private fun Thread.setFolderId(filterFolder: Folder?) {
        this.folderId = if (messages.count() == 1) messages.single().folderId else filterFolder!!.id
    }

    private suspend fun Thread.keepOldMessagesData(filterFolder: Folder?, realm: Realm) {
        messages.forEach { remoteMessage: Message ->
            currentCoroutineContext().ensureActive()

            val localMessage = MessageController.getMessage(remoteMessage.uid, realm)
            if (localMessage == null) {
                // The Search only returns Messages from TRASH if we explicitly selected this folder,
                // which is the reason why we can compute the `isTrashed` value so loosely.
                remoteMessage.initLocalValues(
                    areHeavyDataFetched = false,
                    isTrashed = filterFolder?.role == FolderRole.TRASH,
                    messageIds = remoteMessage.computeMessageIds(),
                    draftLocalUuid = null,
                    isFromSearch = true,
                    isDeletedOnApi = false,
                    latestCalendarEventResponse = null,
                    swissTransferFiles = realmListOf(),
                )
            } else {
                remoteMessage.keepLocalValues(localMessage)
            }

            messagesIds += remoteMessage.messageIds

            // TODO: Remove this when the API returns the good value for [Message.hasAttachments]
            if (remoteMessage.hasAttachable) hasAttachable = true
        }
    }

    companion object {
        private val TAG = SearchUtils::class.java.simpleName
    }
}

/**
 * Thread processing that applies to both search threads from the api and from realm. Be careful, it relies on
 * [Thread.folderId] being set correctly.
 */
private fun Thread.sharedThreadProcessing(context: Context, cachedNamedFolders: MutableMap<String, NamedFolder>, realm: Realm) {
    setFolderName(cachedNamedFolders, realm, context)
}

private fun Thread.setFolderName(cachedNamedFolders: MutableMap<String, NamedFolder>, realm: Realm, context: Context) {
    val cachedNamedFolder = cachedNamedFolders[folderId]
        ?: FolderController.getFolder(folderId, realm)
            ?.let { NamedFolder.fromFolder(it, context) }
            ?.also { cachedNamedFolders[folderId] = it }

    // If the remote folder id is inbox and the thread is snoozed, instead of using its default name, change it to snooze
    val computedFolderName = cachedNamedFolder?.let {
        if (it is NamedFolder.Role && it.role == FolderRole.INBOX && isSnoozed()) {
            context.getString(FolderRole.SNOOZED.folderNameRes)
        } else {
            it.getName(context)
        }
    }

    computedFolderName?.let { folderName = it }
}
