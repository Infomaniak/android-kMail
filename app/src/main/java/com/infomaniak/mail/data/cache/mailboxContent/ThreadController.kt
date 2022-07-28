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
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow

object ThreadController {

    /**
     * Get data
     */
    fun getThreadSync(uid: String): Thread? {
        return getThread(uid).find()
    }

    fun getThreadAsync(uid: String): SharedFlow<SingleQueryChange<Thread>> {
        return getThread(uid).asFlow().toSharedFlow()
    }

    fun MutableRealm.getLatestThreadSync(uid: String): Thread? {
        return getThreadSync(uid)?.let(::findLatest)
    }

    /**
     * Edit data
     */
    fun MutableRealm.deleteLatestThread(uid: String) {
        getLatestThreadSync(uid)?.let(::delete)
    }

    /**
     * Utils
     */
    private fun getThread(uid: String): RealmSingleQuery<Thread> {
        return RealmController.mailboxContent.query<Thread>("${Thread::uid.name} == '$uid'").first()
    }

    /**
     * TODO?
     */
    // fun deleteThreads(threads: List<Thread>) {
    //     MailRealm.mailboxContent.writeBlocking { threads.forEach { getLatestThread(it.uid)?.let(::delete) } }
    // }

    // fun upsertThread(thread: Thread) {
    //     MailRealm.mailboxContent.writeBlocking { copyToRealm(thread, UpdatePolicy.ALL) }
    // }

    // fun upsertLatestThread(uid: String) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestThread(uid)?.let { copyToRealm(it, UpdatePolicy.ALL) } }
    // }

    // fun updateThread(uid: String, onUpdate: (thread: Thread) -> Unit) {
    //     MailRealm.mailboxContent.writeBlocking { getLatestThread(uid)?.let(onUpdate) }
    // }

    // fun getLatestThread(uid: String): Thread? = RealmController.mailboxContent.writeBlocking { getLatestThread(uid) }

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    // fun getDeletableThreads(folder: Folder, threadsToKeep: List<Thread>): RealmResults<Thread> {
    //     val threadsIds = threadsToKeep.map { it.uid }
    //     val query = threadsIds.joinToString(
    //         prefix = "NOT (${Thread::uid.name} == '",
    //         separator = "' OR ${Thread::uid.name} == '",
    //         postfix = "')"
    //     )
    //     return MailRealm.mailboxContent.query<Thread>(query).find()
    // }
}
