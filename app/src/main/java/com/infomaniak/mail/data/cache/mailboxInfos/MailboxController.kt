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
package com.infomaniak.mail.data.cache.mailboxInfos

import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow

object MailboxController {

    /**
     * Get data
     */
    fun getMailboxesSync(userId: Int): RealmResults<Mailbox> {
        return getMailboxes(userId).find()
    }

    fun getMailboxesAsync(userId: Int): SharedFlow<ResultsChange<Mailbox>> {
        return getMailboxes(userId).asFlow().toSharedFlow()
    }

    fun getMailboxSync(objectId: String): Mailbox? {
        return getMailbox(objectId).find()
    }

    fun getMailboxAsync(objectId: String): SharedFlow<SingleQueryChange<Mailbox>> {
        return getMailbox(objectId).asFlow().toSharedFlow()
    }

    /**
     * Edit data
     */
    fun upsertMailboxes(mailboxes: List<Mailbox>) {
        RealmController.mailboxInfos.writeBlocking { mailboxes.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    fun deleteMailboxes(mailboxes: List<Mailbox>) {
        RealmController.mailboxInfos.writeBlocking { mailboxes.forEach { getLatestMailbox(it.objectId)?.let(::delete) } }
    }

    /**
     * Utils
     */
    private fun getMailboxes(userId: Int): RealmQuery<Mailbox> {
        return RealmController.mailboxInfos.query("${Mailbox::userId.name} == '$userId'")
    }

    private fun getMailbox(objectId: String): RealmSingleQuery<Mailbox> {
        return RealmController.mailboxInfos.query<Mailbox>("${Mailbox::objectId.name} == '$objectId'").first()
    }

    private fun MutableRealm.getLatestMailbox(objectId: String): Mailbox? {
        return getMailboxSync(objectId)?.let(::findLatest)
    }

    /**
     * TODO?
     */
    // fun upsertMailbox(mailbox: Mailbox) {
    //     RealmController.mailboxInfos.writeBlocking { copyToRealm(mailbox, UpdatePolicy.ALL) }
    // }

    // fun deleteMailbox(id: String) {
    //     RealmController.mailboxInfos.writeBlocking { getLatestMailbox(id)?.let(::delete) }
    // }

    // fun selectMailboxByEmail(email: String) {
    //     currentMailbox = RealmController.mailboxInfos.query<Mailbox>("${Mailbox::email.name} == '$email'").first().find()
    //     currentMailbox?.let { AccountUtils.currentMailboxId = it.mailboxId } ?: throw MailboxNotFoundException(email)
    // }

    // fun getMailboxInfoByEmail(email: String): Mailbox? {
    //     return RealmController.mailboxInfos.query<Mailbox>("${Mailbox::email.name} == '$email'").first().find()
    // }

    // private fun updateMailboxInfo(id: String, onUpdate: (mailbox: Mailbox) -> Unit) {
    //     RealmController.mailboxInfos.writeBlocking { getLatestMailboxInfoById(id)?.let(onUpdate) }
    // }

    // private fun MutableRealm.removeMailboxInfoIfAlreadyExisting(mailbox: Mailbox) {
    //     getMailboxInfoByObjectId(mailbox.mailboxId)?.let { findLatest(it)?.let(::delete) }
    // }

    // TODO: RealmKotlin doesn't fully support `IN` for now.
    // TODO: Workaround: https://github.com/realm/realm-js/issues/2781#issuecomment-607213640
    // fun getDeletableMailboxes(mailboxesToKeep: List<Mailbox>): RealmResults<Mailbox> {
    //     val objectIds = mailboxesToKeep.map { it.objectId }
    //     val query = objectIds.joinToString(
    //         prefix = "NOT (${Mailbox::objectId.name} == '",
    //         separator = "' OR ${Mailbox::objectId.name} == '",
    //         postfix = "')"
    //     )
    //     return RealmController.mailboxInfos.query<Mailbox>(query).find()
    // }

    // class MailboxNotFoundException(email: String) : Exception("Mailbox [$email] not found")
}
