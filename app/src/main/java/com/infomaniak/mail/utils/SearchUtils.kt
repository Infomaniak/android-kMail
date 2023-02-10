/*
 * Infomaniak kMail - Android
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
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Thread
import com.infomaniak.mail.data.models.Thread.ThreadFilter
import com.infomaniak.mail.data.models.message.Message
import io.realm.kotlin.ext.toRealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SearchUtils {

    val TAG = SearchUtils::class.simpleName

    fun searchFilters(query: String?, filters: Set<ThreadFilter>): String {
        val filtersQuery = StringBuilder("severywhere=${if (filters.contains(ThreadFilter.FOLDER)) "0" else "1"}")
        if (!query.isNullOrBlank()) filtersQuery.append("&scontains=$query")

        with(filters) {
            when {
                contains(ThreadFilter.ATTACHMENTS) -> filtersQuery.append("&sattachments=yes")
                contains(ThreadFilter.SEEN) -> filtersQuery.append("&filters=seen")
                contains(ThreadFilter.UNSEEN) -> filtersQuery.append("&filters=unseen")
                contains(ThreadFilter.STARRED) -> filtersQuery.append("&filters=starred")
                else -> Unit
            }
        }
        return filtersQuery.toString()
    }

    fun selectFilter(filter: ThreadFilter, selectedFilters: MutableSet<ThreadFilter>?): MutableSet<ThreadFilter>? {
        val filters = when (filter) {
            ThreadFilter.SEEN -> {
                selectedFilters?.apply {
                    removeAll(arrayOf(ThreadFilter.UNSEEN, ThreadFilter.STARRED))
                }
            }
            ThreadFilter.UNSEEN -> {
                selectedFilters?.apply {
                    removeAll(arrayOf(ThreadFilter.SEEN, ThreadFilter.STARRED))
                }
            }
            ThreadFilter.STARRED -> {
                selectedFilters?.apply {
                    removeAll(arrayOf(ThreadFilter.SEEN, ThreadFilter.UNSEEN))
                }
            }
            else -> selectedFilters
        }

        return filters?.apply { add(filter) }
    }

    fun List<Message>.convertToSearchThreads(): List<Thread> {
        return this.map { message ->
            Thread().apply {
                this.uid = "search-${message.uid}"
                this.messages = listOf(message).toRealmList()
                this.unseenMessagesCount = 0
                this.from = message.from
                this.to = message.to
                this.date = message.date
                this.hasAttachments = message.hasAttachments
                this.isFavorite = message.isFavorite
                this.isAnswered = message.isAnswered
                this.isForwarded = message.isForwarded
                this.size = message.size
                this.subject = message.subject
            }
        }
    }

    suspend fun deleteRealmSearchData() = withContext(Dispatchers.IO) {
        RealmDatabase.mailboxContent().writeBlocking {
            Log.i(TAG, "SearchUtils>deleteRealmSearchData: remove old search data")
            MessageController.deleteSearchMessages(this)
            ThreadController.deleteSearchThreads(this)
        }
    }
}