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
package com.infomaniak.mail.data.cache

import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Duplicate
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.data.models.signature.SignatureEmail
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.user.UserInfo
import com.infomaniak.mail.data.models.user.UserPreferences
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject

@Suppress("ObjectPropertyName")
object RealmDatabase {

    val TAG: String = RealmDatabase::class.java.simpleName

    private var _appSettings: Realm? = null
    private var _userInfos: Realm? = null
    private var _mailboxInfos: Realm? = null
    private var _mailboxContent: Realm? = null

    val appSettings: Realm
        get() = _appSettings
            ?: Realm.open(RealmConfigurations.appSettings).also { _appSettings = it }

    val userInfos
        get() = _userInfos
            ?: Realm.open(RealmConfigurations.userInfos).also { _userInfos = it }

    val mailboxInfos
        get() = _mailboxInfos
            ?: Realm.open(RealmConfigurations.mailboxInfos).also { _mailboxInfos = it }

    val mailboxContent
        get() = _mailboxContent
            ?: Realm.open(RealmConfigurations.mailboxContent(AccountUtils.currentMailboxId)).also { _mailboxContent = it }

    inline fun <reified T : RealmObject> Realm.update(items: List<RealmObject>) {
        writeBlocking {
            delete(query<T>())
            copyListToRealm(items)
        }
    }

    // TODO: There is currently no way to insert multiple objects in one call (https://github.com/realm/realm-kotlin/issues/938)
    fun MutableRealm.copyListToRealm(items: List<RealmObject>) {
        items.forEach { copyToRealm(it, UpdatePolicy.ALL) }
    }

    fun deleteMailboxContent(mailboxId: Int) {
        Realm.deleteRealm(RealmConfigurations.mailboxContent(mailboxId))
    }

    fun close() {
        closeMailboxContent()
        closeMailboxInfos()
        closeUserInfos()
        closeAppSettings()
    }

    private fun closeAppSettings() {
        _appSettings?.close()
        _appSettings = null
    }

    fun closeUserInfos() {
        _userInfos?.close()
        _userInfos = null
    }

    private fun closeMailboxInfos() {
        _mailboxInfos?.close()
        _mailboxInfos = null
    }

    fun closeMailboxContent() {
        _mailboxContent?.close()
        _mailboxContent = null
    }

    private object RealmConfigurations {

        private const val appSettingsDbName = "AppSettings.realm"
        private val userInfosDbName get() = "User-${AccountUtils.currentUserId}.realm"
        private const val mailboxInfosDbName = "MailboxInfos.realm"
        private fun mailboxContentDbName(mailboxId: Int) = "Mailbox-${AccountUtils.currentUserId}-${mailboxId}.realm"

        val appSettings =
            RealmConfiguration
                .Builder(RealmSets.appSettings)
                .name(appSettingsDbName)
                .deleteRealmIfMigrationNeeded() // TODO: Do we want to keep this in production?
                .build()

        val userInfos
            get() = RealmConfiguration
                .Builder(RealmSets.userInfos)
                .name(userInfosDbName)
                .deleteRealmIfMigrationNeeded() // TODO: Do we want to keep this in production?
                .build()

        val mailboxInfos =
            RealmConfiguration
                .Builder(RealmSets.mailboxInfos)
                .name(mailboxInfosDbName)
                .deleteRealmIfMigrationNeeded() // TODO: Do we want to keep this in production?
                .build()

        fun mailboxContent(mailboxId: Int) =
            RealmConfiguration
                .Builder(RealmSets.mailboxContent)
                .name(mailboxContentDbName(mailboxId))
                .deleteRealmIfMigrationNeeded() // TODO: Do we want to keep this in production?
                .build()

        private object RealmSets {

            val appSettings = setOf(
                AppSettings::class,
            )

            val userInfos = setOf(
                UserPreferences::class,
                AddressBook::class,
                Contact::class,
            )

            val mailboxInfos = setOf(
                Mailbox::class,
                Quotas::class,
            )

            val mailboxContent = setOf(
                Folder::class,
                Thread::class,
                Message::class,
                Draft::class,
                Duplicate::class,
                Recipient::class,
                Body::class,
                Attachment::class,
            )

            val miscellaneous = setOf(
                Signature::class,
                SignatureEmail::class,
                UserInfo::class,
            )
        }
    }
}
