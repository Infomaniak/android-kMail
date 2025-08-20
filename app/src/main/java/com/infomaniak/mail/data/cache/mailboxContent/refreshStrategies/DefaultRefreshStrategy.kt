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
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.ThreadRecomputations.recomputeThread
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive

val defaultRefreshStrategy = object : DefaultRefreshStrategy {}

interface DefaultRefreshStrategy : RefreshStrategy {
    override fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread> {
        return ThreadController.getThreadsByFolderId(folderId, realm)
    }

    override fun twinFolderRoles(): List<FolderRole> = emptyList()

    override fun shouldHideEmptyFolder(): Boolean = false

    override fun getMessageFromShortUid(shortUid: String, folderId: String, realm: TypedRealm): Message? {
        return MessageController.getMessageBlocking(shortUid.toLongUid(folderId), realm)
    }

    override fun processDeletedMessage(
        scope: CoroutineScope,
        managedMessage: Message,
        context: Context,
        mailbox: Mailbox,
        realm: MutableRealm,
    ) {
        MessageController.deleteMessage(context, mailbox, managedMessage, realm)
    }

    override fun addFolderToImpactedFolders(folderId: String, impactedFolders: ImpactedFolders) {
        impactedFolders += folderId
    }

    override fun processDeletedThread(thread: Thread, realm: MutableRealm) {
        if (thread.getNumberOfMessagesInFolder() == 0) {
            realm.delete(thread)
        } else {
            thread.recomputeThread(realm)
        }
    }

    override fun shouldQueryFolderThreadsOnDeletedUid(): Boolean = false

    private fun String.toLongUid(folderId: String) = "${this}@${folderId}"

    private fun Thread.getNumberOfMessagesInFolder() = messages.count { message -> message.folderId == folderId }

    override fun handleAddedMessage(
        scope: CoroutineScope,
        remoteMessage: Message,
        isConversationMode: Boolean,
        impactedThreadsManaged: MutableSet<Thread>,
        realm: MutableRealm,
    ) {
        val newThread = if (isConversationMode) {
            realm.handleAddedMessage(scope, remoteMessage, impactedThreadsManaged)
        } else {
            remoteMessage.toThread()
        }
        newThread?.let { impactedThreadsManaged += realm.putNewThreadInRealm(it) }
    }

    private fun MutableRealm.handleAddedMessage(
        scope: CoroutineScope,
        remoteMessage: Message,
        impactedThreadsManaged: MutableSet<Thread>,
    ): Thread? {

        // Other pre-existing Threads that will also require this Message and will provide the prior Messages for this new Thread.
        val existingThreads = ThreadController.getThreadsByMessageIds(remoteMessage.messageIds, realm = this)
        val existingMessages = getExistingMessages(existingThreads)

        val thread = createNewThreadIfRequired(scope, remoteMessage, existingThreads, existingMessages)
        updateExistingThreads(scope, remoteMessage, existingThreads, existingMessages, impactedThreadsManaged)

        return thread
    }

    private fun TypedRealm.createNewThreadIfRequired(
        scope: CoroutineScope,
        newMessage: Message,
        existingThreads: List<Thread>,
        existingMessages: Set<Message>,
    ): Thread? {
        var newThread: Thread? = null

        if (existingThreads.none { it.folderId == newMessage.folderId }) {

            newThread = newMessage.toThread()

            addPreviousMessagesToThread(scope, newThread, existingMessages)
        }

        return newThread
    }

    private fun MutableRealm.updateExistingThreads(
        scope: CoroutineScope,
        remoteMessage: Message,
        existingThreads: RealmResults<Thread>,
        existingMessages: Set<Message>,
        impactedThreadsManaged: MutableSet<Thread>,
    ) {
        // Update already existing Threads (i.e. in other Folders, or specific cases like Snoozed)
        impactedThreadsManaged += addAllMessagesToAllThreads(scope, remoteMessage, existingThreads, existingMessages)

        // Some Messages don't have references to all previous Messages of the Thread (ex: these from the iOS Mail app).
        // Because we are missing the links between Messages, it will create multiple Threads for the same Folder.
        // Hence, we need to find these duplicates, and remove them.
        val duplicatedThreads = identifyExtraDuplicatedThreads(remoteMessage.messageIds)
        // We need to make sure to remove the duplicated Threads even if they were previously added to `impactedThreadsManaged`.
        // Later on, this set will be used to recompute Threads, and Threads that are deleted from Realm will crash Realm if we try to access them.
        impactedThreadsManaged -= duplicatedThreads
        duplicatedThreads.forEach(::delete) // Delete the other Threads. Sorry bro, you won't be missed.
    }

    private fun MutableRealm.addAllMessagesToAllThreads(
        scope: CoroutineScope,
        remoteMessage: Message,
        existingThreads: RealmResults<Thread>,
        existingMessages: Set<Message>,
    ): Set<Thread> {

        if (existingThreads.isEmpty()) return emptySet()

        val allExistingMessages = buildSet {
            addAll(existingMessages)
            add(remoteMessage)
        }

        return buildSet {
            existingThreads.forEach { thread ->
                scope.ensureActive()

                allExistingMessages.forEach { existingMessage ->
                    scope.ensureActive()

                    if (!thread.messages.contains(existingMessage)) {
                        thread.messagesIds += existingMessage.messageIds
                        thread.addMessageWithConditions(existingMessage, realm = this@addAllMessagesToAllThreads)
                    }
                }

                add(thread)
            }
        }
    }

    private fun MutableRealm.identifyExtraDuplicatedThreads(messageIds: RealmSet<String>): Set<Thread> {

        // Create a map with all duplicated Threads of the same Thread in a list.
        val map = mutableMapOf<String, MutableList<Thread>>()
        ThreadController.getThreadsByMessageIds(messageIds, realm = this).forEach {
            map.getOrPut(it.folderId) { mutableListOf() }.add(it)
        }

        return buildSet {
            map.values.forEach { threads ->
                // We want to keep only 1 duplicated Thread, so we skip the 1st one. (He's the chosen one!)
                addAll(threads.subList(1, threads.count()))
            }
        }
    }

    private fun MutableRealm.putNewThreadInRealm(newThread: Thread): Thread {
        return ThreadController.upsertThread(newThread, realm = this)
    }

    private fun getExistingMessages(existingThreads: List<Thread>): Set<Message> {
        return existingThreads.flatMapTo(mutableSetOf()) { it.messages }
    }

    private fun TypedRealm.addPreviousMessagesToThread(
        scope: CoroutineScope,
        newThread: Thread,
        referenceMessages: Set<Message>,
    ) {
        referenceMessages.forEach { message ->
            scope.ensureActive()

            newThread.apply {
                messagesIds += message.computeMessageIds()
                addMessageWithConditions(message, realm = this@addPreviousMessagesToThread)
            }
        }
    }
}
