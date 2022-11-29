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

import android.content.Context
import android.util.Log
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.infomaniak.lib.core.BuildConfig
import com.infomaniak.lib.core.auth.TokenAuthenticator
import com.infomaniak.lib.core.auth.TokenInterceptor
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.NotificationUtils.initMailNotificationChannel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

object MailboxController {

    //region Queries
    private fun checkHasUserId(userId: Int) = "${Mailbox::userId.name} == '$userId'"

    private fun getMailboxesQuery(realm: TypedRealm? = null): RealmQuery<Mailbox> {
        return (realm ?: RealmDatabase.mailboxInfo()).query<Mailbox>().sort(Mailbox::unseenMessages.name, Sort.DESCENDING)
    }

    private fun getMailboxesQuery(userId: Int, realm: TypedRealm? = null): RealmQuery<Mailbox> {
        return (realm ?: RealmDatabase.mailboxInfo()).query<Mailbox>(checkHasUserId(userId))
            .sort(Mailbox::unseenMessages.name, Sort.DESCENDING)
    }

    private fun getMailboxesQuery(userId: Int, exceptionMailboxIds: List<Int>, realm: TypedRealm? = null): RealmQuery<Mailbox> {
        val checkIsNotInExceptions = "NOT ${Mailbox::mailboxId.name} IN {${exceptionMailboxIds.joinToString { "\"$it\"" }}}"
        return (realm ?: RealmDatabase.mailboxInfo()).query<Mailbox>(checkHasUserId(userId)).query(checkIsNotInExceptions)
    }

    private fun getMailboxQuery(objectId: String, realm: TypedRealm? = null): RealmSingleQuery<Mailbox> {
        return (realm ?: RealmDatabase.mailboxInfo()).query<Mailbox>("${Mailbox::objectId.name} == '$objectId'").first()
    }

    private fun getMailboxQuery(userId: Int, mailboxId: Int, realm: TypedRealm? = null): RealmSingleQuery<Mailbox> {
        val checkMailboxId = "${Mailbox::mailboxId.name} == '$mailboxId'"
        return (realm ?: RealmDatabase.mailboxInfo()).query<Mailbox>("${checkHasUserId(userId)} AND $checkMailboxId").first()
    }
    //endregion

    //region Get data
    fun getMailboxes(userId: Int, realm: TypedRealm? = null): RealmResults<Mailbox> {
        return getMailboxesQuery(userId, realm).find()
    }

    private fun getMailboxes(userId: Int, exceptionMailboxIds: List<Int>, realm: TypedRealm? = null): RealmResults<Mailbox> {
        return getMailboxesQuery(userId, exceptionMailboxIds, realm).find()
    }

    fun getMailboxesAsync(realm: TypedRealm? = null): Flow<ResultsChange<Mailbox>> {
        return getMailboxesQuery(realm).asFlow()
    }

    fun getMailboxesAsync(userId: Int, realm: TypedRealm? = null): Flow<ResultsChange<Mailbox>> {
        return getMailboxesQuery(userId, realm).asFlow()
    }

    fun getMailbox(objectId: String, realm: TypedRealm? = null): Mailbox? {
        return getMailboxQuery(objectId, realm).find()
    }

    fun getMailbox(userId: Int, mailboxId: Int, realm: TypedRealm? = null): Mailbox? {
        return getMailboxQuery(userId, mailboxId, realm).find() ?: getMailboxesQuery(userId, realm).first().find()
    }

    fun getMailboxAsync(objectId: String, realm: TypedRealm? = null): Flow<SingleQueryChange<Mailbox>> {
        return getMailboxQuery(objectId, realm).asFlow()
    }

    fun getCurrentMailbox(): Mailbox? {
        return MainViewModel.currentMailboxObjectId.value?.let(::getMailbox)
    }

    fun getCurrentMailboxUuid(): String? {
        return getCurrentMailbox()?.uuid
    }
    //endregion

    //region Edit data
    fun updateMailboxes(context: Context, user: User? = null) {
        (if (user != null) ApiRepository.getMailboxes(createOkHttpClientForSpecificUser(user))
        else ApiRepository.getMailboxes())
            .data?.let { mailboxes ->

                context.initMailNotificationChannel(mailboxes)

                val userId = user?.id ?: AccountUtils.currentUserId

                val remoteMailboxes = RealmDatabase.mailboxInfo().writeBlocking {
                    mailboxes.map {
                        val mailboxObjectId = it.createObjectId(userId)
                        val unseenMessages = getMailbox(mailboxObjectId, realm = this)?.unseenMessages ?: 0
                        it.initLocalValues(userId, unseenMessages)
                    }
                }

                update(remoteMailboxes, userId)
            }
    }

    private fun createOkHttpClientForSpecificUser(user: User): OkHttpClient {

        val tokenInterceptorListener = object : TokenInterceptorListener {
            override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
                AccountUtils.setUserToken(user, apiToken)
            }

            override suspend fun onRefreshTokenError() {
                // TODO?
            }

            override suspend fun getApiToken(): ApiToken = user.apiToken
        }

        return OkHttpClient.Builder()
            .apply {
                if (BuildConfig.DEBUG) addNetworkInterceptor(StethoInterceptor())
                addInterceptor(TokenInterceptor(tokenInterceptorListener))
                authenticator(TokenAuthenticator(tokenInterceptorListener))
            }.build()
    }

    private fun update(remoteMailboxes: List<Mailbox>, userId: Int) {

        // Get current data
        Log.d(RealmDatabase.TAG, "Mailboxes: Get current data")
        val localQuotas = getMailboxes(userId).associate { it.objectId to it.quotas }

        val isCurrentMailboxDeleted = RealmDatabase.mailboxInfo().writeBlocking {

            Log.d(RealmDatabase.TAG, "Mailboxes: Save new data")
            upsertMailboxes(localQuotas, remoteMailboxes)

            Log.d(RealmDatabase.TAG, "Mailboxes: Delete outdated data")
            return@writeBlocking deleteOutdatedData(remoteMailboxes, userId)
        }

        if (isCurrentMailboxDeleted) AccountUtils.reloadApp()
    }

    private fun MutableRealm.upsertMailboxes(localQuotas: Map<String, Quotas?>, remoteMailboxes: List<Mailbox>) {
        remoteMailboxes.forEach { remoteMailbox ->
            remoteMailbox.quotas = localQuotas[remoteMailbox.objectId]
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
        RealmDatabase.mailboxInfo().writeBlocking { getMailbox(objectId, realm = this)?.let(onUpdate) }
    }
    //endregion
}
