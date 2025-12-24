/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.MailboxInfoRealm
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.NotificationUtils.Companion.deleteMailNotificationChannel
import com.infomaniak.mail.utils.extensions.findFirstSuspend
import com.infomaniak.mail.utils.extensions.findSuspend
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmScalarQuery
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MailboxController @Inject constructor(
    private val appContext: Context,
    private val notificationUtils: NotificationUtils,
    @MailboxInfoRealm private val mailboxInfoRealm: Realm,
) {

    //region Get data
    suspend fun getMailboxes(
        userId: Int? = null,
        exceptionMailboxIds: List<Int> = emptyList(),
    ): RealmResults<Mailbox> {
        return getMailboxes(userId, mailboxInfoRealm, exceptionMailboxIds)
    }

    fun getMailboxesCount(userId: Int): Flow<Long> = getMailboxesCountQuery(userId, mailboxInfoRealm).asFlow()

    fun getMailboxesAsync(userId: Int, exceptionMailboxIds: List<Int> = emptyList()): Flow<RealmResults<Mailbox>> {
        return getMailboxesQuery(userId, mailboxInfoRealm, exceptionMailboxIds).toMailboxesFlow()
    }

    suspend fun getMailbox(objectId: String): Mailbox? {
        return getMailboxQuery(objectId, mailboxInfoRealm).findSuspend()
    }

    suspend fun getMailbox(userId: Int, mailboxId: Int): Mailbox? {
        return getMailbox(userId, mailboxId, mailboxInfoRealm)
    }

    suspend fun getMailboxWithFallback(userId: Int, mailboxId: Int): Mailbox? {
        return getMailboxWithFallback(userId, mailboxId, mailboxInfoRealm)
    }

    suspend fun getFirstValidMailbox(userId: Int): Mailbox? {
        return getFirstValidMailbox(userId, mailboxInfoRealm)
    }

    fun getMailboxAsync(objectId: String): Flow<SingleQueryChange<Mailbox>> {
        return getMailboxQuery(objectId, mailboxInfoRealm).asFlow()
    }

    fun getMailboxAsync(userId: Int, mailboxId: Int): Flow<SingleQueryChange<Mailbox>> {
        return getMailboxQuery(userId, mailboxId, mailboxInfoRealm).asFlow()
    }

    fun getLockedMailboxes(userId: Int): Flow<RealmResults<Mailbox>> {
        return getLockedMailboxesQuery(userId, mailboxInfoRealm).toMailboxesFlow()
    }

    suspend fun getMyKSuiteMailboxCount(userId: Int): Long {
        return getMyKSuiteMailboxesQuery(userId, mailboxInfoRealm).count().findSuspend()
    }

    private fun RealmQuery<Mailbox>.toMailboxesFlow() = asFlow().map { it.list }
    //endregion

    //region Edit data
    suspend fun updateMailboxes(remoteMailboxes: List<Mailbox>, userId: Int = AccountUtils.currentUserId) {
        remoteMailboxes.forEach { mailbox ->
            notificationUtils.initMailNotificationChannel(mailbox)
        }

        mailboxInfoRealm.write {
            val remoteMailboxesIds = remoteMailboxes.map { remoteMailbox ->

                SentryLog.d(RealmDatabase.TAG, "Mailboxes: Get current data")
                val localMailbox = getMailboxBlocking(userId, remoteMailbox.mailboxId, realm = this)?.copyFromRealm()

                SentryLog.d(RealmDatabase.TAG, "Mailboxes: Save new data")
                remoteMailbox.initLocalValues(
                    userId = userId,
                    quotas = localMailbox?.quotas,
                    inboxUnreadCount = localMailbox?.unreadCountLocal,
                    permissions = localMailbox?.permissions,
                    signatures = localMailbox?.signatures,
                    featureFlags = localMailbox?._featureFlags,
                    externalMailFlagEnabled = localMailbox?.externalMailFlagEnabled,
                    trustedDomains = localMailbox?.trustedDomains,
                    sendersRestrictions = localMailbox?.sendersRestrictions,
                )
                copyToRealm(remoteMailbox, UpdatePolicy.ALL)

                return@map remoteMailbox.mailboxId
            }

            SentryLog.d(RealmDatabase.TAG, "Mailboxes: Delete outdated data")
            deleteOutdatedData(remoteMailboxesIds, userId)
        }
    }

    private fun MutableRealm.deleteOutdatedData(remoteMailboxesIds: List<Int>, userId: Int) {
        val outdatedMailboxes = getMailboxesBlocking(userId, realm = this, remoteMailboxesIds)
        val isCurrentMailboxDeleted = outdatedMailboxes.any { it.mailboxId == AccountUtils.currentMailboxId }
        if (isCurrentMailboxDeleted) {
            RealmDatabase.closeMailboxContent()
            AccountUtils.currentMailboxId = -2 // AppSettings.DEFAULT_ID
        }
        outdatedMailboxes.forEach { RealmDatabase.deleteMailboxContent(it.mailboxId) }
        delete(outdatedMailboxes)
    }

    suspend fun updateMailbox(objectId: String, onUpdate: (Mailbox) -> Unit) {
        mailboxInfoRealm.write { getMailboxBlocking(objectId, realm = this)?.let(onUpdate) }
    }

    suspend fun deleteUserMailboxes(userId: Int) {
        mailboxInfoRealm.write {
            val mailboxes = getMailboxesBlocking(userId, realm = this)
            appContext.deleteMailNotificationChannel(mailboxes)
            delete(mailboxes)
        }
    }
    //endregion

    companion object {

        //region Queries
        private fun checkHasUserId(userId: Int) = "${Mailbox::userId.name} == '$userId'"

        private val isLocked = "${Mailbox.isLockedPropertyName} == true"
        private val isKSuitePerso = "${Mailbox::isKSuitePerso.name} == true"

        private fun getMailboxesQuery(
            userId: Int? = null,
            realm: TypedRealm,
            exceptionMailboxIds: List<Int> = emptyList(),
        ): RealmQuery<Mailbox> {

            val query = if (userId == null) {
                realm.query()
            } else {
                realm.query<Mailbox>(checkHasUserId(userId))
            }

            val sortedQuery = query
                .sort(Mailbox::email.name, Sort.ASCENDING)
                .sort(Mailbox::isPrimary.name, Sort.DESCENDING)

            return if (exceptionMailboxIds.isEmpty()) {
                sortedQuery
            } else {
                sortedQuery.query("NOT ${Mailbox::mailboxId.name} IN $0", exceptionMailboxIds)
            }
        }

        private fun getValidMailboxesQuery(userId: Int, realm: TypedRealm): RealmQuery<Mailbox> {
            return realm.query("${checkHasUserId(userId)} AND NOT $isLocked")
        }

        private fun getMailboxesCountQuery(userId: Int, realm: TypedRealm): RealmScalarQuery<Long> {
            return getMailboxesQuery(userId, realm).count()
        }

        private fun getMailboxQuery(objectId: String, realm: TypedRealm): RealmSingleQuery<Mailbox> {
            return realm.query<Mailbox>("${Mailbox::objectId.name} == $0", objectId).first()
        }

        private fun getMailboxQuery(userId: Int, mailboxId: Int, realm: TypedRealm): RealmSingleQuery<Mailbox> {
            val checkMailboxId = "${Mailbox::mailboxId.name} == $0"
            return realm.query<Mailbox>("${checkHasUserId(userId)} AND $checkMailboxId", mailboxId).first()
        }

        private fun getLockedMailboxesQuery(userId: Int, realm: TypedRealm): RealmQuery<Mailbox> {
            return realm.query("${checkHasUserId(userId)} AND $isLocked")
        }

        private fun getMyKSuiteMailboxesQuery(userId: Int, realm: TypedRealm): RealmQuery<Mailbox> {
            return realm.query<Mailbox>("${checkHasUserId(userId)} AND $isKSuitePerso")
        }
        //endregion

        //region Get data
        suspend fun getMailboxes(
            userId: Int? = null,
            realm: TypedRealm,
            exceptionMailboxIds: List<Int> = emptyList(),
        ): RealmResults<Mailbox> {
            return getMailboxesQuery(userId, realm, exceptionMailboxIds).findSuspend()
        }

        fun getMailboxesBlocking(
            userId: Int? = null,
            realm: TypedRealm,
            exceptionMailboxIds: List<Int> = emptyList(),
        ): RealmResults<Mailbox> {
            return getMailboxesQuery(userId, realm, exceptionMailboxIds).find()
        }

        fun getMailboxBlocking(objectId: String, realm: TypedRealm): Mailbox? {
            return getMailboxQuery(objectId, realm).find()
        }

        suspend fun getMailbox(userId: Int, mailboxId: Int, realm: TypedRealm): Mailbox? {
            return getMailboxQuery(userId, mailboxId, realm).findSuspend()
        }

        private fun getMailboxBlocking(userId: Int, mailboxId: Int, realm: TypedRealm): Mailbox? {
            return getMailboxQuery(userId, mailboxId, realm).find()
        }

        suspend fun getMailboxWithFallback(userId: Int, mailboxId: Int, realm: TypedRealm): Mailbox? {
            return getMailbox(userId, mailboxId, realm) ?: getMailboxesQuery(userId, realm).findFirstSuspend()
        }

        suspend fun getFirstValidMailbox(userId: Int, realm: TypedRealm): Mailbox? {
            return getValidMailboxesQuery(userId, realm).findFirstSuspend()
        }
        //endregion
    }
}
