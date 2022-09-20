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

import android.util.Log
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.toSharedFlow
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.SharedFlow

object MailboxController {

    private fun checkHasUserId(userId: Int) = "${Mailbox::userId.name} = '$userId'"

    //region Get data
    fun getAllMailboxesAsync(realm: MutableRealm? = null): SharedFlow<ResultsChange<Mailbox>> {
        return realm.getAllMailboxesQuery().asFlow().toSharedFlow()
    }

    private fun MutableRealm?.getAllMailboxesQuery(): RealmQuery<Mailbox> {
        return (this ?: RealmDatabase.mailboxInfos).query<Mailbox>().sort(Mailbox::unseenMessages.name, Sort.DESCENDING)
    }

    private fun getMailboxes(userId: Int, realm: MutableRealm? = null): RealmResults<Mailbox> {
        return realm.getMailboxesQuery(userId).find()
    }

    fun getMailboxesAsync(userId: Int, realm: MutableRealm? = null): SharedFlow<ResultsChange<Mailbox>> {
        return realm.getMailboxesQuery(userId).asFlow().toSharedFlow()
    }

    private fun MutableRealm?.getMailboxesQuery(userId: Int): RealmQuery<Mailbox> {
        return (this ?: RealmDatabase.mailboxInfos)
            .query<Mailbox>(checkHasUserId(userId))
            .sort(Mailbox::unseenMessages.name, Sort.DESCENDING)
    }

    private fun getMailboxes(userId: Int, exceptionMailboxIds: List<Int>, realm: MutableRealm? = null): RealmResults<Mailbox> {
        return realm.getMailboxesQuery(userId, exceptionMailboxIds).find()
    }

    private fun MutableRealm?.getMailboxesQuery(userId: Int, exceptionMailboxIds: List<Int>): RealmQuery<Mailbox> {
        val checkIsNotInExceptions = "NOT ${Mailbox::mailboxId.name} IN {${exceptionMailboxIds.joinToString { "\"$it\"" }}}"
        return (this ?: RealmDatabase.mailboxInfos).query<Mailbox>(checkHasUserId(userId)).query(checkIsNotInExceptions)
    }

    fun getMailbox(objectId: String, realm: MutableRealm? = null): Mailbox? {
        return realm.getMailboxQuery(objectId).find()
    }

    private fun MutableRealm?.getMailboxQuery(objectId: String): RealmSingleQuery<Mailbox> {
        return (this ?: RealmDatabase.mailboxInfos).query<Mailbox>("${Mailbox::objectId.name} = '$objectId'").first()
    }

    fun getMailbox(userId: Int, mailboxId: Int, realm: MutableRealm? = null): Mailbox? {
        return realm.getMailboxQuery(userId, mailboxId).find() ?: realm.getMailboxesQuery(userId).first().find()
    }

    private fun MutableRealm?.getMailboxQuery(userId: Int, mailboxId: Int): RealmSingleQuery<Mailbox> {
        val checkMailboxId = "${Mailbox::mailboxId.name} = '$mailboxId'"
        return (this ?: RealmDatabase.mailboxInfos).query<Mailbox>("${checkHasUserId(userId)} AND $checkMailboxId").first()
    }
    //endregion

    //region Edit data
    fun update(apiMailboxes: List<Mailbox>) {

        // Get outdated data
        Log.d(RealmDatabase.TAG, "Mailboxes: Get outdated data")
        val outdatedMailboxes = getMailboxes(AccountUtils.currentUserId, apiMailboxes.map { it.mailboxId })

        // Save new data
        Log.d(RealmDatabase.TAG, "Mailboxes: Save new data")
        upsertMailboxes(apiMailboxes)

        // Delete outdated data
        Log.d(RealmDatabase.TAG, "Mailboxes: Delete outdated data")
        val isCurrentMailboxDeleted = deleteOutdatedData(outdatedMailboxes)

        if (isCurrentMailboxDeleted) AccountUtils.reloadApp()
    }

    fun upsertMailboxes(mailboxes: List<Mailbox>) {
        RealmDatabase.mailboxInfos.writeBlocking { mailboxes.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
    }

    private fun deleteOutdatedData(outdatedMailboxes: List<Mailbox>): Boolean {
        val isCurrentMailboxDeleted = outdatedMailboxes.any { it.mailboxId == AccountUtils.currentMailboxId }
        if (isCurrentMailboxDeleted) {
            RealmDatabase.closeMailboxContent()
            AccountUtils.currentMailboxId = AppSettings.DEFAULT_ID
        }
        deleteMailboxes(outdatedMailboxes)
        outdatedMailboxes.forEach { RealmDatabase.deleteMailboxContent(it.mailboxId) }
        return isCurrentMailboxDeleted
    }

    fun updateMailbox(objectId: String, onUpdate: (mailbox: Mailbox) -> Unit) {
        RealmDatabase.mailboxInfos.writeBlocking { getMailbox(objectId, this)?.let(onUpdate) }
    }

    private fun deleteMailboxes(mailboxes: List<Mailbox>) {
        RealmDatabase.mailboxInfos.writeBlocking {
            mailboxes.forEach { getMailbox(it.objectId, this)?.let(::delete) }
        }
    }
    //endregion
}
