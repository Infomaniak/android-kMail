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
package com.infomaniak.mail.data.cache.mailboxInfo

import android.util.Log
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow

object MailboxController {

    //region Queries
    private fun checkHasUserId(userId: Int) = "${Mailbox::userId.name} = '$userId'"

    private fun MutableRealm?.getMailboxesQuery(): RealmQuery<Mailbox> {
        return (this ?: RealmDatabase.mailboxInfo()).query<Mailbox>().sort(Mailbox::unseenMessages.name, Sort.DESCENDING)
    }

    private fun MutableRealm?.getMailboxesQuery(userId: Int): RealmQuery<Mailbox> {
        return (this ?: RealmDatabase.mailboxInfo()).query<Mailbox>(checkHasUserId(userId))
            .sort(Mailbox::unseenMessages.name, Sort.DESCENDING)
    }

    private fun MutableRealm?.getMailboxesQuery(userId: Int, exceptionMailboxIds: List<Int>): RealmQuery<Mailbox> {
        val checkIsNotInExceptions = "NOT ${Mailbox::mailboxId.name} IN {${exceptionMailboxIds.joinToString { "\"$it\"" }}}"
        return (this ?: RealmDatabase.mailboxInfo()).query<Mailbox>(checkHasUserId(userId)).query(checkIsNotInExceptions)
    }

    private fun MutableRealm?.getMailboxQuery(objectId: String): RealmSingleQuery<Mailbox> {
        return (this ?: RealmDatabase.mailboxInfo()).query<Mailbox>("${Mailbox::objectId.name} = '$objectId'").first()
    }

    private fun MutableRealm?.getMailboxQuery(userId: Int, mailboxId: Int): RealmSingleQuery<Mailbox> {
        val checkMailboxId = "${Mailbox::mailboxId.name} = '$mailboxId'"
        return (this ?: RealmDatabase.mailboxInfo()).query<Mailbox>("${checkHasUserId(userId)} AND $checkMailboxId").first()
    }
    //endregion

    //region Get data
    fun getMailboxes(userId: Int, realm: MutableRealm? = null): RealmResults<Mailbox> {
        return realm.getMailboxesQuery(userId).find()
    }

    private fun getMailboxes(userId: Int, exceptionMailboxIds: List<Int>, realm: MutableRealm? = null): RealmResults<Mailbox> {
        return realm.getMailboxesQuery(userId, exceptionMailboxIds).find()
    }

    fun getMailboxesAsync(realm: MutableRealm? = null): Flow<ResultsChange<Mailbox>> {
        return realm.getMailboxesQuery().asFlow()
    }

    fun getMailboxesAsync(userId: Int, realm: MutableRealm? = null): Flow<ResultsChange<Mailbox>> {
        return realm.getMailboxesQuery(userId).asFlow()
    }

    fun getMailbox(objectId: String, realm: MutableRealm? = null): Mailbox? {
        return realm.getMailboxQuery(objectId).find()
    }

    fun getMailbox(userId: Int, mailboxId: Int, realm: MutableRealm? = null): Mailbox? {
        return realm.getMailboxQuery(userId, mailboxId).find() ?: realm.getMailboxesQuery(userId).first().find()
    }

    fun getMailboxAsync(objectId: String, realm: MutableRealm? = null): Flow<SingleQueryChange<Mailbox>> {
        return realm.getMailboxQuery(objectId).asFlow()
    }
    //endregion

    //region Edit data
    fun update(apiMailboxes: List<Mailbox>, userId: Int) {

        // Get current data
        Log.d(RealmDatabase.TAG, "Mailboxes: Get current data")
        val localQuotas = getMailboxes(userId).associate { it.objectId to it.quotas }

        val isCurrentMailboxDeleted = RealmDatabase.mailboxInfo().writeBlocking {

            Log.d(RealmDatabase.TAG, "Mailboxes: Save new data")
            upsertMailboxes(localQuotas, apiMailboxes)

            Log.d(RealmDatabase.TAG, "Mailboxes: Delete outdated data")
            return@writeBlocking deleteOutdatedData(apiMailboxes, userId)
        }

        if (isCurrentMailboxDeleted) AccountUtils.reloadApp()
    }

    private fun MutableRealm.upsertMailboxes(localQuotas: Map<String, Quotas?>, apiMailboxes: List<Mailbox>) {
        apiMailboxes.forEach { apiMailbox ->
            apiMailbox.quotas = localQuotas[apiMailbox.objectId]
            copyToRealm(apiMailbox, UpdatePolicy.ALL)
        }
    }

    private fun MutableRealm.deleteOutdatedData(apiMailboxes: List<Mailbox>, userId: Int): Boolean {
        val outdatedMailboxes = getMailboxes(userId, apiMailboxes.map { it.mailboxId }, this)
        val isCurrentMailboxDeleted = outdatedMailboxes.any { it.mailboxId == AccountUtils.currentMailboxId }
        if (isCurrentMailboxDeleted) {
            RealmDatabase.closeMailboxContent()
            AccountUtils.currentMailboxId = AppSettings.DEFAULT_ID
        }
        outdatedMailboxes.forEach { RealmDatabase.deleteMailboxContent(it.mailboxId) }
        delete(outdatedMailboxes)
        return isCurrentMailboxDeleted
    }

    fun updateMailbox(objectId: String, onUpdate: (mailbox: Mailbox) -> Unit) {
        RealmDatabase.mailboxInfo().writeBlocking { getMailbox(objectId, this)?.let(onUpdate) }
    }
    //endregion
}
