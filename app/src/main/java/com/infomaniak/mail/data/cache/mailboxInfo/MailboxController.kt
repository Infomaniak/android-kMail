/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.MailboxLocalValues
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.di.MailboxInfoRealm
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.NotificationUtils.Companion.deleteMailNotificationChannel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MailboxController @Inject constructor(
    private val appContext: Context,
    @MailboxInfoRealm private val mailboxInfoRealm: Realm,
) {

    //region Get data
    fun getMailboxes(
        userId: Int? = null,
        exceptionMailboxIds: List<Int> = emptyList(),
    ): RealmResults<Mailbox> {
        return getMailboxes(userId, mailboxInfoRealm, exceptionMailboxIds)
    }

    fun getMailboxesCount(userId: Int): Flow<Long> = getMailboxesCountQuery(userId, mailboxInfoRealm).asFlow()

    fun getMailboxesAsync(userId: Int, exceptionMailboxIds: List<Int> = emptyList()): Flow<RealmResults<Mailbox>> {
        return getMailboxesQuery(userId, mailboxInfoRealm, exceptionMailboxIds).toMailboxesFlow()
    }

    fun getMailbox(objectId: String): Mailbox? {
        return getMailboxQuery(objectId, mailboxInfoRealm).find()
    }

    fun getMailbox(userId: Int, mailboxId: Int): Mailbox? {
        return getMailbox(userId, mailboxId, mailboxInfoRealm)
    }

    fun getMailboxWithFallback(userId: Int, mailboxId: Int): Mailbox? {
        return getMailboxWithFallback(userId, mailboxId, mailboxInfoRealm)
    }

    fun getFirstValidMailbox(userId: Int): Mailbox? {
        return getFirstValidMailbox(userId, mailboxInfoRealm)
    }

    fun getMailboxAsync(objectId: String): Flow<SingleQueryChange<Mailbox>> {
        return getMailboxQuery(objectId, mailboxInfoRealm).asFlow()
    }

    fun getMailboxAsync(userId: Int, mailboxId: Int): Flow<SingleQueryChange<Mailbox>> {
        return getMailboxQuery(userId, mailboxId, mailboxInfoRealm).asFlow()
    }

    fun getInvalidPasswordMailboxes(userId: Int): Flow<RealmResults<Mailbox>> {
        return getInvalidPasswordMailboxesQuery(userId, mailboxInfoRealm).toMailboxesFlow()
    }

    fun getLockedMailboxes(userId: Int): Flow<RealmResults<Mailbox>> {
        return getLockedMailboxesQuery(userId, mailboxInfoRealm).toMailboxesFlow()
    }

    private fun RealmQuery<Mailbox>.toMailboxesFlow() = asFlow().map { it.list }
    //endregion

    //region Edit data
    suspend fun updateMailboxes(remoteMailboxes: List<Mailbox>, userId: Int = AccountUtils.currentUserId) {

        val mailboxes = mailboxInfoRealm.write {
            return@write remoteMailboxes.map { remoteMailbox ->
                remoteMailbox.also {
                    it.initLocalValues(
                        userId = userId,
                        localValues = getMailbox(userId, remoteMailbox.mailboxId, realm = this)?.local,
                    )
                }
            }
        }

        update(mailboxes, userId)
    }

    private suspend fun update(remoteMailboxes: List<Mailbox>, userId: Int) {

        // Get current data
        SentryLog.d(RealmDatabase.TAG, "Mailboxes: Get current data")
        val localQuotasAndPermissions = getMailboxes(userId, mailboxInfoRealm).associate {
            it.objectId to (it.local.quotas to it.local.permissions)
        }

        mailboxInfoRealm.write {

            SentryLog.d(RealmDatabase.TAG, "Mailboxes: Save new data")
            upsertMailboxes(localQuotasAndPermissions, remoteMailboxes)

            SentryLog.d(RealmDatabase.TAG, "Mailboxes: Delete outdated data")
            deleteOutdatedData(remoteMailboxes, userId)
        }
    }

    private fun MutableRealm.upsertMailboxes(
        localQuotasAndPermissions: Map<String, Pair<Quotas?, MailboxPermissions?>>,
        remoteMailboxes: List<Mailbox>,
    ) {
        remoteMailboxes.forEach { remoteMailbox ->
            remoteMailbox.apply {
                local.quotas = localQuotasAndPermissions[objectId]?.first
                local.permissions = localQuotasAndPermissions[objectId]?.second
            }
            copyToRealm(remoteMailbox, UpdatePolicy.ALL)
        }
    }

    private fun MutableRealm.deleteOutdatedData(remoteMailboxes: List<Mailbox>, userId: Int) {
        val outdatedMailboxes = getMailboxes(userId, realm = this, remoteMailboxes.map { it.mailboxId })
        val isCurrentMailboxDeleted = outdatedMailboxes.any { it.mailboxId == AccountUtils.currentMailboxId }
        if (isCurrentMailboxDeleted) {
            RealmDatabase.closeMailboxContent()
            AccountUtils.currentMailboxId = AppSettings.DEFAULT_ID
        }
        outdatedMailboxes.forEach { RealmDatabase.deleteMailboxContent(it.mailboxId) }
        delete(outdatedMailboxes)
    }

    suspend fun updateMailbox(objectId: String, onUpdate: (Mailbox) -> Unit) {
        mailboxInfoRealm.write { getMailbox(objectId, realm = this)?.let(onUpdate) }
    }

    suspend fun deleteUserMailboxes(userId: Int) {
        mailboxInfoRealm.write {
            val mailboxes = getMailboxes(userId, realm = this)
            appContext.deleteMailNotificationChannel(mailboxes)
            delete(mailboxes)
        }
    }
    //endregion

    companion object {

        //region Queries
        private fun checkHasUserId(userId: Int): String {
            return "${Mailbox.localValuesPropertyName}.${MailboxLocalValues::userId.name} == '$userId'"
        }

        private val isMailboxLocked = "${Mailbox::isLocked.name} == true"
        private val hasValidPassword = "${Mailbox::isPasswordValid.name} == true"

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
            return realm.query("${checkHasUserId(userId)} AND $hasValidPassword AND (NOT $isMailboxLocked)")
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
            realm: TypedRealm,
            exceptionMailboxIds: List<Int> = emptyList(),
        ): RealmResults<Mailbox> {
            return getMailboxesQuery(userId, realm, exceptionMailboxIds).find()
        }

        fun getMailbox(objectId: String, realm: TypedRealm): Mailbox? {
            return getMailboxQuery(objectId, realm).find()
        }

        fun getMailbox(userId: Int, mailboxId: Int, realm: TypedRealm): Mailbox? {
            return getMailboxQuery(userId, mailboxId, realm).find()
        }

        fun getMailboxWithFallback(userId: Int, mailboxId: Int, realm: TypedRealm): Mailbox? {
            return getMailbox(userId, mailboxId, realm) ?: getMailboxesQuery(userId, realm).first().find()
        }

        fun getFirstValidMailbox(userId: Int, realm: TypedRealm): Mailbox? {
            return getValidMailboxesQuery(userId, realm).first().find()
        }
        //endregion
    }
}
