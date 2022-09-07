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
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.SharedFlow

object MailboxController {

    //region Get data
    private fun getAllMailboxes(realm: MutableRealm? = null): RealmResults<Mailbox> {
        return realm.getAllMailboxesQuery().find()
    }

    fun getAllMailboxesAsync(realm: MutableRealm? = null): SharedFlow<ResultsChange<Mailbox>> {
        return realm.getAllMailboxesQuery().asFlow().toSharedFlow()
    }

    private fun MutableRealm?.getAllMailboxesQuery(): RealmQuery<Mailbox> {
        return (this ?: RealmDatabase.mailboxInfos).query()
    }

    private fun getMailboxes(userId: Int, realm: MutableRealm? = null): RealmResults<Mailbox> {
        return realm.getMailboxesQuery(userId).find()
    }

    fun getMailboxesAsync(userId: Int, realm: MutableRealm? = null): SharedFlow<ResultsChange<Mailbox>> {
        return realm.getMailboxesQuery(userId).asFlow().toSharedFlow()
    }

    private fun MutableRealm?.getMailboxesQuery(userId: Int): RealmQuery<Mailbox> {
        return (this ?: RealmDatabase.mailboxInfos).query("${Mailbox::userId.name} == '$userId'")
    }

    fun getMailbox(objectId: String, realm: MutableRealm? = null): Mailbox? {
        return realm.getMailboxQuery(objectId).find()
    }

    private fun getMailboxAsync(objectId: String, realm: MutableRealm? = null): SharedFlow<SingleQueryChange<Mailbox>> {
        return realm.getMailboxQuery(objectId).asFlow().toSharedFlow()
    }

    private fun MutableRealm?.getMailboxQuery(objectId: String): RealmSingleQuery<Mailbox> {
        return (this ?: RealmDatabase.mailboxInfos).query<Mailbox>("${Mailbox::objectId.name} == '$objectId'").first()
    }

    fun getCurrentMailbox(): Mailbox? = with(RealmDatabase.mailboxInfos) {
        val checkUserId = "${Mailbox::userId.name} = '${AccountUtils.currentUserId}'"
        val checkMailboxId = "${Mailbox::mailboxId.name} = '${AccountUtils.currentMailboxId}'"
        val mailbox = query<Mailbox>("$checkUserId AND $checkMailboxId").first().find()
        return mailbox ?: query<Mailbox>(checkUserId).first().find()
    }
    //endregion

    //region Edit data
    fun update(apiMailboxes: List<Mailbox>) {

        // Get current data
        Log.d(RealmDatabase.TAG, "Mailboxes: Get current data")
        val realmMailboxes = getMailboxes(AccountUtils.currentUserId)

        // Get outdated data
        Log.d(RealmDatabase.TAG, "Mailboxes: Get outdated data")
        val deletableMailboxes = realmMailboxes.filter { realmMailbox ->
            apiMailboxes.none { apiMailbox -> apiMailbox.mailboxId == realmMailbox.mailboxId }
        }

        // Save new data
        Log.d(RealmDatabase.TAG, "Mailboxes: Save new data")
        upsertMailboxes(apiMailboxes)

        // Delete outdated data
        Log.d(RealmDatabase.TAG, "Mailboxes: Delete outdated data")
        val isCurrentMailboxDeleted = deletableMailboxes.any { it.mailboxId == AccountUtils.currentMailboxId }
        if (isCurrentMailboxDeleted) {
            RealmDatabase.closeMailboxContent()
            AccountUtils.currentMailboxId = AppSettings.DEFAULT_ID
        }
        deleteMailboxes(deletableMailboxes)
        deletableMailboxes.forEach { RealmDatabase.deleteMailboxContent(it.mailboxId) }

        if (isCurrentMailboxDeleted) AccountUtils.reloadApp()
    }

    fun upsertMailboxes(mailboxes: List<Mailbox>) {
        RealmDatabase.mailboxInfos.writeBlocking { mailboxes.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
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
