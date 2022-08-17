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
package com.infomaniak.mail.data.cache.mailboxContent

import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.getLatestFolderSync
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.deleteLatestMessage
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.getLatestThreadSync
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.drafts.Draft
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.toRealmInstant
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow
import java.util.*

object DraftController {

    /**
     * Get data
     */
    fun getDraftSync(uuid: String): Draft? {
        return getDraft(uuid).find()
    }

    fun getDraftAsync(uuid: String): SharedFlow<SingleQueryChange<Draft>> {
        return getDraft(uuid).asFlow().toSharedFlow()
    }

    fun MutableRealm.getLatestDraftSync(uuid: String): Draft? {
        return getDraftSync(uuid)?.let(::findLatest)
    }

    /**
     * Edit data
     */
    fun upsertDraft(draft: Draft) {
        RealmController.mailboxContent.writeBlocking { copyToRealm(draft, UpdatePolicy.ALL) }
    }

    fun updateDraft(uuid: String, onUpdate: (draft: Draft) -> Draft): Draft? {
        return RealmController.mailboxContent.writeBlocking { getLatestDraftSync(uuid)?.let(onUpdate) }
    }

    fun saveDraftAndItsParents(draftUuid: String, isOnline: Boolean) {
        RealmController.mailboxContent.writeBlocking {

            // Save Draft
            val latestDraft = getLatestDraftSync(draftUuid) ?: return@writeBlocking
            latestDraft.apply {
                if (isOnline) {
                    isOffline = false
                    isModifiedOffline = false
                } else {
                    isModifiedOffline = true
                    date = Date().toRealmInstant()
                }
            }

            // Save Message
            val draftMessage = Message.from(latestDraft)
            copyToRealm(draftMessage, UpdatePolicy.ALL)

            // Save Thread
            val thread = Thread.from(draftMessage, getThreadByMessageUid(latestDraft.messageUid)?.uid)
            copyToRealm(thread, UpdatePolicy.ALL)

            // Save in Draft Folder
            insertInDraftFolderIfNeeded(thread)
        }
    }

    fun deleteDraftAndItsParents(uuid: String, messageUid: String) {
        RealmController.mailboxContent.writeBlocking {
            val threadsToRemove = getThreadsToRemoveByMessageUid(uuid, messageUid)
            deleteLatestMessage(messageUid.ifEmpty { uuid })
            threadsToRemove.forEach { thread -> thread.let { getLatestThreadSync(it.uid)?.let(::delete) } }
        }
    }

    private fun getThreadsToRemoveByMessageUid(uuid: String, messageUid: String): RealmResults<Thread> {
        val threadUidPropertyName = Thread::uid.name
        val messageUidPropertyName = "${Thread::messages.name}.${Message::uid.name}"
        val query = """
                ${Thread::parentFolderId.name} == '${Folder.draftFolder?.id}' AND (
                   $threadUidPropertyName == '$uuid' OR $threadUidPropertyName == '$messageUid' OR
                   $messageUidPropertyName == '$uuid' OR $messageUidPropertyName == '$messageUid'
                )
            """.trimIndent()

        return RealmController.mailboxContent.query<Thread>(query).find()
    }

    fun deleteDraft(uuid: String) {
        RealmController.mailboxContent.writeBlocking { getLatestDraftSync(uuid)?.let(::delete) }
    }

    /**
     * Utils
     */
    private fun getDraft(uuid: String): RealmSingleQuery<Draft> {
        return RealmController.mailboxContent.query<Draft>("${Draft::uuid.name} == '$uuid'").first()
    }

    private fun getThreadByMessageUid(messageUid: String): Thread? {
        val queryThread = "${Thread::messages.name}.${Message::uid.name} == '$messageUid'"

        return RealmController.mailboxContent.query<Thread>(queryThread).first().find()
    }

    private fun MutableRealm.insertInDraftFolderIfNeeded(thread: Thread) {
        getDraftFolderIfThreadIsOrphan(thread.uid)?.id?.let {
            getLatestFolderSync(it)?.threads?.add(thread)
        }
    }

    private fun getDraftFolderIfThreadIsOrphan(uid: String): Folder? {
        return RealmController.mailboxContent.query<Folder>(
            "id == '${Folder.draftFolder?.id}' AND NONE ${Folder::threads.name}.${Thread::uid.name} == '${uid}'"
        ).first().find()
    }
}
