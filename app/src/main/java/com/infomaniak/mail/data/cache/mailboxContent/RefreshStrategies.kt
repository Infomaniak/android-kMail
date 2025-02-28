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
package com.infomaniak.mail.data.cache.mailboxContent

import android.content.Context
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive

interface RefreshStrategy {
    fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread>

    fun getMessageFromShortUid(shortUid: String, folderId: String, realm: TypedRealm): Message?

    /**
     * @return The list of impacted threads that have changed and need to be recomputed. The list of impacted threads will also be
     * used to determine what folders need to have their unread count updated. If an extra folder needs its unread count updated
     * but no thread has that extra folder as [Thread.folderId], you can define the extra folder you want inside
     * [extraFolderIdsThatNeedToRefreshUnreadOnDelete].
     */
    fun processDeletedMessage(
        scope: CoroutineScope,
        managedMessage: Message,
        context: Context,
        mailbox: Mailbox,
        realm: MutableRealm,
    ): Collection<Thread>

    fun extraFolderIdsThatNeedToRefreshUnreadOnDelete(realm: TypedRealm): List<String>
    fun processDeletedThread(thread: Thread, realm: MutableRealm)
    fun queryFolderThreadsOnDeletedUid(): Boolean

    /**
     * About the [impactedThreadsManaged]:
     *  This set will be updated throughout the whole process of handling added Messages.
     *  It represents all the Threads that will need to be recomputed to reflect the changes of the newly added Messages.
     *  We need to pass down a reference to the MutableSet to enable both addition and removal of Threads in it.
     */
    fun handleAddedMessages(
        scope: CoroutineScope,
        remoteMessage: Message,
        isConversationMode: Boolean,
        impactedThreadsManaged: MutableSet<Thread>,
        realm: MutableRealm,
    )
}

interface DefaultRefreshStrategy : RefreshStrategy {
    override fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread> {
        return ThreadController.getThreadsByFolderId(folderId, realm)
    }

    override fun getMessageFromShortUid(shortUid: String, folderId: String, realm: TypedRealm): Message? {
        return MessageController.getMessage(shortUid.toLongUid(folderId), realm)
    }

    override fun processDeletedMessage(
        scope: CoroutineScope,
        managedMessage: Message,
        context: Context,
        mailbox: Mailbox,
        realm: MutableRealm,
    ): Collection<Thread> = buildSet {
        /**
         * This list is reversed because we'll delete items while looping over it.
         * Doing so for managed Realm objects will lively update the list we're iterating through, making us skip the next item.
         * Looping in reverse enables us to not skip any item.
         */
        managedMessage.threads.asReversed().forEach { thread ->
            scope.ensureActive()

            val isSuccess = thread.messages.remove(managedMessage)
            if (isSuccess) add(thread)
        }

        MessageController.deleteMessage(context, mailbox, managedMessage, realm)
    }

    override fun extraFolderIdsThatNeedToRefreshUnreadOnDelete(realm: TypedRealm): List<String> = emptyList()

    override fun processDeletedThread(thread: Thread, realm: MutableRealm) {
        if (thread.getNumberOfMessagesInFolder() == 0) {
            realm.delete(thread)
        } else {
            thread.recomputeThread(realm)
        }
    }

    override fun queryFolderThreadsOnDeletedUid(): Boolean = false

    private fun String.toLongUid(folderId: String) = "${this}@${folderId}"
    private fun Thread.getNumberOfMessagesInFolder() = messages.count { message -> message.folderId == folderId }

    override fun handleAddedMessages(
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

        // Some Messages don't have references to all previous Messages of the Thread (ex: these from the iOS Mail app).
        // Because we are missing the links between Messages, it will create multiple Threads for the same Folder.
        // Hence, we need to find these duplicates.
        val isThereDuplicatedThreads = isThereDuplicatedThreads(remoteMessage.messageIds, existingThreads.count())

        // Create Thread in this Folder
        val thread = createNewThreadIfRequired(scope, remoteMessage, existingThreads, existingMessages)
        // Update Threads in other Folders
        addAllMessagesToAllThreads(scope, remoteMessage, existingThreads, existingMessages, impactedThreadsManaged)

        // Now that all other existing Threads are updated, we need to remove the duplicated Threads.
        if (isThereDuplicatedThreads) removeDuplicatedThreads(remoteMessage.messageIds, impactedThreadsManaged)

        return thread
    }

    private fun MutableRealm.isThereDuplicatedThreads(messageIds: RealmSet<String>, threadsCount: Int): Boolean {
        val foldersCount = ThreadController.getExistingThreadsFoldersCount(messageIds, realm = this)
        return foldersCount != threadsCount.toLong()
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

    private fun MutableRealm.addAllMessagesToAllThreads(
        scope: CoroutineScope,
        remoteMessage: Message,
        existingThreads: RealmResults<Thread>,
        existingMessages: Set<Message>,
        impactedThreadsManaged: MutableSet<Thread>,
    ) {
        if (existingThreads.isEmpty()) return

        val allExistingMessages = mutableSetOf<Message>().apply {
            addAll(existingMessages)
            add(remoteMessage)
        }

        existingThreads.forEach { thread ->
            scope.ensureActive()

            allExistingMessages.forEach { existingMessage ->
                scope.ensureActive()

                if (!thread.messages.contains(existingMessage)) {
                    thread.messagesIds += existingMessage.messageIds
                    thread.addMessageWithConditions(existingMessage, realm = this)
                }
            }

            impactedThreadsManaged += thread
        }
    }

    private fun MutableRealm.removeDuplicatedThreads(messageIds: RealmSet<String>, impactedThreadsManaged: MutableSet<Thread>) {

        // Create a map with all duplicated Threads of the same Thread in a list.
        val map = mutableMapOf<String, MutableList<Thread>>()
        ThreadController.getThreadsByMessageIds(messageIds, realm = this).forEach {
            map.getOrPut(it.folderId) { mutableListOf() }.add(it)
        }

        map.values.forEach { threads ->
            threads.forEachIndexed { index, thread ->
                if (index > 0) { // We want to keep only 1 duplicated Thread, so we skip the 1st one. (He's the chosen one!)
                    impactedThreadsManaged.remove(thread)
                    delete(thread) // Delete the other Threads. Sorry bro, you won't be missed.
                }
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
