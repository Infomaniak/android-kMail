/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.extensions.findSuspend
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import javax.inject.Inject

class DraftController @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
) {

    //region Get data
    suspend fun getDraftsWithActions(realm: TypedRealm): RealmResults<Draft> {
        return getDraftsWithActionsQuery(realm).findSuspend()
    }

    suspend fun getDraftsWithActionsCount(): Long {
        return getDraftsWithActionsCount(mailboxContentRealm())
    }

    suspend fun getAllDrafts(realm: TypedRealm): RealmResults<Draft> {
        return getDraftsQuery(realm = realm).findSuspend()
    }

    fun getDraftBlocking(localUuid: String): Draft? {
        return getDraftBlocking(localUuid, mailboxContentRealm())
    }

    fun getDraftByMessageUid(messageUid: String): Draft? {
        return getDraftByMessageUidBlocking(messageUid, mailboxContentRealm())
    }
    //endregion

    //region Edit data
    suspend fun upsertDraft(draft: Draft) {
        mailboxContentRealm().write {
            upsertDraftBlocking(draft, realm = this)
        }
    }

    suspend fun deleteDraft(draft: Draft) {
        mailboxContentRealm().write {
            delete(getDraftQuery(Draft::localUuid.name, draft.localUuid, realm = this))
        }
    }
    //endregion

    //region Open Draft
    suspend fun fetchHeavyDataIfNeeded(message: Message): Pair<Message, Boolean> {
        return fetchHeavyDataIfNeeded(message, mailboxContentRealm())
    }
    //endregion

    companion object {

        //region Queries
        private fun getDraftsQuery(query: String? = null, realm: TypedRealm): RealmQuery<Draft> = with(realm) {
            return@with query?.let(::query) ?: query()
        }

        private fun getOrphanDraftsQuery(realm: TypedRealm): RealmQuery<Draft> {
            return realm.query("${Draft::remoteUuid.name} == nil AND ${Draft.actionPropertyName} == nil")
        }

        private fun getDraftQuery(key: String, value: String, realm: TypedRealm): RealmSingleQuery<Draft> {
            return realm.query<Draft>("$key == $0", value).first()
        }

        private fun getDraftsWithActionsQuery(realm: TypedRealm): RealmQuery<Draft> {
            return getDraftsQuery("${Draft.actionPropertyName} != nil", realm)
        }
        //endregion

        //region Get data
        fun getDraftBlocking(localUuid: String, realm: TypedRealm): Draft? {
            return getDraftQuery(Draft::localUuid.name, localUuid, realm).find()
        }

        suspend fun getDraft(localUuid: String, realm: TypedRealm): Draft? {
            return getDraftQuery(Draft::localUuid.name, localUuid, realm).findSuspend()
        }

        suspend fun getDraftsWithActionsCount(realm: TypedRealm): Long {
            return getDraftsWithActionsQuery(realm).count().findSuspend()
        }

        fun getOrphanDraftsBlocking(realm: TypedRealm): RealmResults<Draft> {
            return getOrphanDraftsQuery(realm).find()
        }

        fun getDraftByMessageUidBlocking(messageUid: String, realm: TypedRealm): Draft? {
            return getDraftQuery(Draft::messageUid.name, messageUid, realm).find()
        }
        //endregion

        //region Edit data
        fun upsertDraftBlocking(draft: Draft, realm: MutableRealm) {
            realm.copyToRealm(draft, UpdatePolicy.ALL)
        }

        fun updateDraftBlocking(localUuid: String, realm: MutableRealm, onUpdate: (Draft) -> Unit) {
            getDraftBlocking(localUuid, realm)?.let(onUpdate)
        }
        //endregion

        //region Open draft
        suspend fun fetchHeavyDataIfNeeded(message: Message, realm: Realm): Pair<Message, Boolean> {
            if (message.isFullyDownloaded()) return message to false

            val (deleted, failed) = ThreadController.fetchMessagesHeavyData(listOf(message), realm)
            val hasFailedFetching = deleted.isNotEmpty() || failed.isNotEmpty()
            return MessageController.getMessage(message.uid, realm)!! to hasFailedFetching
        }
        //endregion
    }
}
