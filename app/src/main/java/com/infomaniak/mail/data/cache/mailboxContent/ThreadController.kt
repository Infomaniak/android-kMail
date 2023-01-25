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
package com.infomaniak.mail.data.cache.mailboxContent

import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.thread.Thread.ThreadFilter
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

object ThreadController {

    private val defaultRealm = RealmDatabase.mailboxContent()

    //region Queries
    private fun getThreadsQuery(realm: TypedRealm): RealmQuery<Thread> {
        return realm.query()
    }

    private fun getUnreadThreadsCountQuery(folderId: String, realm: TypedRealm): RealmScalarQuery<Long> {
        val byFolderId = "${Thread::folderId.name} == '$folderId'"
        val unseen = "${Thread::unseenMessagesCount.name} > 0"
        val query = "$byFolderId AND $unseen"
        return realm.query<Thread>(query).count()
    }

    private fun getThreadsQuery(
        folderId: String,
        filter: ThreadFilter = ThreadFilter.ALL,
        realm: TypedRealm,
    ): RealmQuery<Thread> {

        val byFolderId = "${Thread::folderId.name} == '$folderId'"
        val query = realm
            .query<Thread>(byFolderId)
            .sort(Thread::date.name, Sort.DESCENDING)

        return if (filter == ThreadFilter.ALL) {
            query
        } else {
            val withFilter = when (filter) {
                ThreadFilter.SEEN -> "${Thread::unseenMessagesCount.name} == 0"
                ThreadFilter.UNSEEN -> "${Thread::unseenMessagesCount.name} > 0"
                ThreadFilter.STARRED -> "${Thread::isFavorite.name} == true"
                ThreadFilter.ATTACHMENTS -> "${Thread::hasAttachments.name} == true"
                ThreadFilter.FOLDER -> TODO()
                else -> throw IllegalStateException("`${ThreadFilter::class.simpleName}` cannot be `${ThreadFilter.ALL.name}` here.")
            }
            query.query(withFilter)
        }
    }

    private fun getThreadQuery(uid: String, realm: TypedRealm): RealmSingleQuery<Thread> {
        return realm.query<Thread>("${Thread::uid.name} == '$uid'").first()
    }
    //endregion

    //region Get data
    fun getThreads(realm: TypedRealm): RealmResults<Thread> {
        return getThreadsQuery(realm).find()
    }

    fun getUnreadThreadsCount(folderId: String, realm: TypedRealm): Int {
        return getUnreadThreadsCountQuery(folderId, realm).find().toInt()
    }

    fun getThreadsAsync(
        folderId: String,
        filter: ThreadFilter = ThreadFilter.ALL,
        realm: TypedRealm = defaultRealm,
    ): Flow<ResultsChange<Thread>> {
        return getThreadsQuery(folderId, filter, realm).asFlow()
    }

    fun getThread(uid: String, realm: TypedRealm = defaultRealm): Thread? {
        return getThreadQuery(uid, realm).find()
    }

    fun getThreadAsync(uid: String, realm: TypedRealm = defaultRealm): Flow<SingleQueryChange<Thread>> {
        return getThreadQuery(uid, realm).asFlow()
    }
    //endregion

    //region Edit data
    fun MutableRealm.upsertThread(thread: Thread) {
        copyToRealm(thread, UpdatePolicy.ALL)
    }

    fun deleteThreads(folderId: String, realm: MutableRealm) {
        realm.delete(getThreadsQuery(folderId, realm = realm))
    }

    fun fetchIncompleteMessages(
        messages: List<Message>,
        mailbox: Mailbox,
        okHttpClient: OkHttpClient? = null,
        realm: Realm = defaultRealm,
    ) {

        val impactedFoldersIds = mutableSetOf<String>()

        realm.writeBlocking {
            messages.forEach { localMessage ->
                if (!localMessage.fullyDownloaded) {
                    with(ApiRepository.getMessage(localMessage.resource, okHttpClient)) {
                        if (isSuccess()) {
                            data?.also { remoteMessage ->

                                // If we've already got this Message's Draft beforehand, we need to save
                                // its `draftLocalUuid`, otherwise we'll lose the link between them.
                                val draftLocalUuid = if (remoteMessage.isDraft) {
                                    DraftController.getDraftByMessageUid(remoteMessage.uid, realm = this@writeBlocking)?.localUuid
                                } else {
                                    null
                                }

                                remoteMessage.initLocalValues(
                                    fullyDownloaded = true,
                                    messageIds = localMessage.messageIds,
                                    isSpam = localMessage.isSpam,
                                    date = localMessage.date,
                                    draftLocalUuid,
                                )

                                MessageController.upsertMessage(remoteMessage, realm = this@writeBlocking)
                            }
                        } else {
                            impactedFoldersIds.add(localMessage.folderId)
                        }
                    }
                }
            }
        }

        impactedFoldersIds.forEach { folderId ->
            FolderController.getFolder(folderId, realm)?.let { folder ->
                MessageController.fetchFolderMessages(mailbox, folder, okHttpClient, realm)
            }
        }
    }
    //endregion
}
