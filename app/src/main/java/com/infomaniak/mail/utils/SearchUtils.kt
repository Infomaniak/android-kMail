/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.util.Log
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import com.infomaniak.mail.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchUtils @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val folderController: FolderController,
    private val messageController: MessageController,
    private val threadController: ThreadController,
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
        mailboxContentRealm().writeBlocking {
            Log.i(TAG, "SearchUtils>deleteRealmSearchData: remove old search data")
            messageController.deleteSearchMessages(realm = this)
            threadController.deleteSearchThreads(realm = this)
            folderController.deleteSearchFolderData(realm = this)
        }
    }

    companion object {
        private val TAG = SearchUtils::class.simpleName

        fun List<Message>.convertToSearchThreads(): List<Thread> {
            return map { message ->
                message.toThread().apply {
                    uid = "search-${message.uid}"
                    isFromSearch = true
                    recomputeThread()
                }
            }
        }
    }
}
