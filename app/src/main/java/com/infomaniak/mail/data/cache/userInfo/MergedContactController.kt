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
package com.infomaniak.mail.data.cache.userInfo

import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.mailbox.MailboxHostingStatus
import com.infomaniak.mail.di.UserInfoRealm
import com.infomaniak.mail.utils.extensions.update
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MergedContactController @Inject constructor(@UserInfoRealm private val userInfoRealm: Realm) {

    //region Queries
    private fun getMergedContactsQuery(): RealmQuery<MergedContact> {
        return userInfoRealm.query<MergedContact>()
            .sort(MergedContact::name.name)
            .sort(MergedContact::comesFromApi.name, Sort.DESCENDING)
    }

    private fun getMergedContactsByEmailQuery(email: String, realm: MutableRealm): RealmQuery<MergedContact> {
        return realm.query<MergedContact>("${MergedContact::email.name} == $0", email)
    }
    //endregion

    //region Get data
    fun getSortedMergedContacts(): RealmResults<MergedContact> {
        return getMergedContactsQuery().find()
    }

    fun getMergedContactsAsync(): Flow<ResultsChange<MergedContact>> {
        return getMergedContactsQuery().asFlow()
    }
    //endregion

    //region Edit data
    suspend fun update(mergedContacts: List<MergedContact>) {
        SentryLog.d(RealmDatabase.TAG, "MergedContacts: Save new data")
        userInfoRealm.update<MergedContact>(mergedContacts)
    }

    /**
     * Update merged contacts' encryption status and return the list of contact that cannot be encrypted
     *
     * @param encryptableMailboxesMap key : email of the merged contacts,
     * value : if the contact with this email can receive encrypted message
     *
     * @return A list of the uncryptable emails
     */
    suspend fun updateEncryptionStatus(encryptableMailboxesMap: List<MailboxHostingStatus>): List<String> {
        SentryLog.d(RealmDatabase.TAG, "MergedContacts: Save new data")
        val uncryptableEmailAddresses: MutableList<String> = mutableListOf()

        userInfoRealm.write {
            encryptableMailboxesMap.forEach { mailboxStatus ->
                getMergedContactsByEmailQuery(mailboxStatus.email, realm = this).find().forEach {
                    it.canBeEncrypted = mailboxStatus.isInfomaniakHosted
                }

                if (!mailboxStatus.isInfomaniakHosted) uncryptableEmailAddresses.add(mailboxStatus.email)
            }
        }

        return uncryptableEmailAddresses
    }
    //endregion
}
