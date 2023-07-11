/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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

import android.content.Context
import android.util.Log
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.NotificationUtils.initMailNotificationChannel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmScalarQuery
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object MailboxController {

    private inline val defaultRealm get() = RealmDatabase.mailboxInfo()

    //region Queries
    private fun checkHasUserId(userId: Int) = "${Mailbox::userId.name} == '$userId'"
    private val isMailboxLocked = "${Mailbox::isLocked.name} == true"
    private val hasValidPassword = "${Mailbox::isPasswordValid.name} == true"

    private fun getMailboxesQuery(
        userId: Int? = null,
        exceptionMailboxIds: List<Int> = emptyList(),
        realm: TypedRealm = defaultRealm,
    ): RealmQuery<Mailbox> {

        val query = if (userId == null) {
            realm.query()
        } else {
            realm.query<Mailbox>(checkHasUserId(userId))
        }

        return if (exceptionMailboxIds.isEmpty()) {
            query
        } else {
            query.query("NOT ${Mailbox::mailboxId.name} IN $0", exceptionMailboxIds)
        }
    }

    private fun getValidMailboxesQuery(userId: Int, realm: TypedRealm): RealmQuery<Mailbox> {
        return realm.query("${checkHasUserId(userId)} AND $hasValidPassword AND (NOT $isMailboxLocked)")
    }

    private fun getMailboxesCountQuery(userId: Int): RealmScalarQuery<Long> {
        return getMailboxesQuery(userId).count()
    }

    private fun getMailboxQuery(objectId: String, realm: TypedRealm): RealmSingleQuery<Mailbox> {
        return realm.query<Mailbox>("${Mailbox::objectId.name} == '$objectId'").first()
    }

    private fun getMailboxQuery(userId: Int, mailboxId: Int, realm: TypedRealm): RealmSingleQuery<Mailbox> {
        val checkMailboxId = "${Mailbox::mailboxId.name} == '$mailboxId'"
        return realm.query<Mailbox>("${checkHasUserId(userId)} AND $checkMailboxId").first()
    }

    private fun getInvalidPasswordMailboxesQuery(userId: Int, realm: TypedRealm): RealmQuery<Mailbox> {
        return realm.query("${checkHasUserId(userId)} AND NOT ($hasValidPassword OR $isMailboxLocked)")
    }

    private fun getLockedMailboxesQuery(userId: Int, realm: TypedRealm): RealmQuery<Mailbox> {
        return realm.query("${checkHasUserId(userId)} AND $isMailboxLocked")
    }
    //endregion

    //region Get data
    fun getMailboxes(
        userId: Int? = null,
        exceptionMailboxIds: List<Int> = emptyList(),
        realm: TypedRealm = defaultRealm,
    ): RealmResults<Mailbox> {
        return getMailboxesQuery(userId, exceptionMailboxIds, realm).find()
    }

    fun getMailboxesCount(userId: Int): Long = getMailboxesCountQuery(userId).find()

    fun getMailboxesAsync(userId: Int, exceptionMailboxIds: List<Int> = emptyList()): Flow<RealmResults<Mailbox>> {
        return getMailboxesQuery(userId, exceptionMailboxIds).asFlow().map { it.list }
    }

    fun getMailbox(objectId: String, realm: TypedRealm = defaultRealm): Mailbox? {
        return getMailboxQuery(objectId, realm).find()
    }

    fun getMailbox(userId: Int, mailboxId: Int, realm: TypedRealm = defaultRealm): Mailbox? {
        return getMailboxQuery(userId, mailboxId, realm).find()
    }

    fun getMailboxWithFallback(userId: Int, mailboxId: Int, realm: TypedRealm = defaultRealm): Mailbox? {
        return getMailbox(userId, mailboxId, realm) ?: getMailboxesQuery(userId, realm = realm).first().find()
    }

    fun getFirstValidMailbox(userId: Int, realm: TypedRealm = defaultRealm): Mailbox? {
        return getValidMailboxesQuery(userId, realm).first().find()
    }

    fun getMailboxAsync(objectId: String): Flow<SingleQueryChange<Mailbox>> {
        return getMailboxQuery(objectId, defaultRealm).asFlow()
    }

    fun getInvalidPasswordMailboxes(userId: Int): Flow<RealmResults<Mailbox>> {
        return getInvalidPasswordMailboxesQuery(userId, defaultRealm).asFlow().map { it.list }
    }

    fun getLockedMailboxes(userId: Int): Flow<RealmResults<Mailbox>> {
        return getLockedMailboxesQuery(userId, defaultRealm).asFlow().map { it.list }
    }
    //endregion

    //region Edit data
    suspend fun updateMailboxes(
        context: Context,
        remoteMailboxes: List<Mailbox>,
        userId: Int = AccountUtils.currentUserId,
    ): Boolean {

        context.initMailNotificationChannel(remoteMailboxes)

        val mailboxes = defaultRealm.writeBlocking {
            return@writeBlocking remoteMailboxes.map { remoteMailbox ->
                remoteMailbox.also {
                    val localMailbox = getMailbox(userId, remoteMailbox.mailboxId, realm = this)
                    it.initLocalValues(
                        userId = userId,
                        quotas = localMailbox?.quotas,
                        inboxUnreadCount = localMailbox?.unreadCountLocal ?: 0,
                        permissions = localMailbox?.permissions,
                    )
                }
            }
        }

        return update(mailboxes, userId)
    }

    private suspend fun update(remoteMailboxes: List<Mailbox>, userId: Int): Boolean {

        // Get current data
        Log.d(RealmDatabase.TAG, "Mailboxes: Get current data")
        val localQuotasAndPermissions = getMailboxes(userId).associate { it.objectId to (it.quotas to it.permissions) }

        val isCurrentMailboxDeleted = defaultRealm.writeBlocking {

            Log.d(RealmDatabase.TAG, "Mailboxes: Save new data")
            upsertMailboxes(localQuotasAndPermissions, remoteMailboxes)

            Log.d(RealmDatabase.TAG, "Mailboxes: Delete outdated data")
            return@writeBlocking deleteOutdatedData(remoteMailboxes, userId)
        }

        return isCurrentMailboxDeleted.also { if (it) AccountUtils.reloadApp?.invoke() }
    }

    private fun MutableRealm.upsertMailboxes(
        localQuotasAndPermissions: Map<String, Pair<Quotas?, MailboxPermissions?>>,
        remoteMailboxes: List<Mailbox>,
    ) {
        remoteMailboxes.forEach { remoteMailbox ->
            remoteMailbox.apply {
                quotas = localQuotasAndPermissions[objectId]?.first
                permissions = localQuotasAndPermissions[objectId]?.second
            }
            copyToRealm(remoteMailbox, UpdatePolicy.ALL)
        }
    }

    private fun MutableRealm.deleteOutdatedData(remoteMailboxes: List<Mailbox>, userId: Int): Boolean {
        val outdatedMailboxes = getMailboxes(userId, remoteMailboxes.map { it.mailboxId }, realm = this)
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
        defaultRealm.writeBlocking { getMailbox(objectId, realm = this)?.let(onUpdate) }
    }
    //endregion
}
