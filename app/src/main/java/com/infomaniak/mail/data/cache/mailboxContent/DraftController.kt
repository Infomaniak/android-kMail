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
import io.realm.kotlin.ext.isManaged
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

    fun manageDraftAutoSave(draft: Draft, mustSaveThread: Boolean) {
        RealmController.mailboxContent.writeBlocking {

            // Save Draft
            val latestDraft = (if (draft.isManaged()) getLatestDraftSync(draft.uuid) else draft) ?: return@writeBlocking
            latestDraft.apply {
                isModifiedOffline = true
                date = Date().toRealmInstant()
            }
            if (!latestDraft.isManaged()) copyToRealm(latestDraft, UpdatePolicy.ALL)

            // Save Message
            val draftMessage = Message.from(latestDraft)
            copyToRealm(draftMessage, UpdatePolicy.ALL)

            // Save Thread
            if (mustSaveThread) {
                val thread = Thread.from(draftMessage, getThreadByMessageUid(draft)?.uid)
                copyToRealm(thread, UpdatePolicy.ALL)

                // Save in Draft Folder
                insertInDraftFolderIfNeeded(thread)
            }
        }
    }

    fun removeDraft(uuid: String, messageUid: String) {
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

    /**
     * Utils
     */
    private fun getDraft(uuid: String): RealmSingleQuery<Draft> {
        return RealmController.mailboxContent.query<Draft>("${Draft::uuid.name} == '$uuid'").first()
    }

    private fun getThreadByMessageUid(draft: Draft): Thread? {
        val queryThread = "${Thread::messages.name}.${Message::uid.name} == '${draft.messageUid}'"

        return RealmController.mailboxContent.query<Thread>(queryThread).first().find()
    }

    private fun MutableRealm.insertInDraftFolderIfNeeded(thread: Thread) {
        thread.getDraftFolderIfNotExist()?.id?.let { getLatestFolderSync(it)?.threads?.add(thread) }
    }

    private fun Thread.getDraftFolderIfNotExist(): Folder? {
        return RealmController.mailboxContent.query<Folder>(
            "id == '${Folder.draftFolder?.id}' AND NONE ${Folder::threads.name}.${Thread::uid.name} == '${uid}'"
        ).first().find()
    }
}
