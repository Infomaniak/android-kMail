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
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.data.models.signature.SignatureEmail
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("ObjectPropertyName")
object RealmDatabase {

    val TAG: String = RealmDatabase::class.java.simpleName

    private var _appSettings: Realm? = null
    private var _userInfo: Realm? = null
    private var _mailboxInfo: Realm? = null
    private var _mailboxContent: Realm? = null

    private val appSettingsMutex = Mutex()
    private val userInfoMutex = Mutex()
    private val mailboxInfoMutex = Mutex()
    private val mailboxContentMutex = Mutex()

    fun appSettings(): Realm = runBlocking(Dispatchers.IO) {
        appSettingsMutex.withLock {
            _appSettings ?: Realm.open(RealmConfig.appSettings).also { _appSettings = it }
        }
    }

    fun userInfo(): Realm = runBlocking(Dispatchers.IO) {
        userInfoMutex.withLock {
            _userInfo ?: Realm.open(RealmConfig.userInfo).also { _userInfo = it }
        }
    }

    fun mailboxInfo(): Realm = runBlocking(Dispatchers.IO) {
        mailboxInfoMutex.withLock {
            _mailboxInfo ?: Realm.open(RealmConfig.mailboxInfo).also { _mailboxInfo = it }
        }
    }

    fun mailboxContent(): Realm = runBlocking(Dispatchers.IO) {
        mailboxContentMutex.withLock {
            _mailboxContent ?: Realm.open(RealmConfig.mailboxContent(AccountUtils.currentMailboxId)).also { _mailboxContent = it }
        }
    }

    inline fun <reified T : RealmObject> Realm.update(items: List<RealmObject>) {
        writeBlocking {
            delete(query<T>())
            copyListToRealm(items)
        }
    }

    // TODO: There is currently no way to insert multiple objects in one call (https://github.com/realm/realm-kotlin/issues/938)
    fun MutableRealm.copyListToRealm(items: List<RealmObject>, alsoCopyManagedItems: Boolean = true) {
        items.forEach { if (alsoCopyManagedItems || !it.isManaged()) copyToRealm(it, UpdatePolicy.ALL) }
    }

    fun deleteMailboxContent(mailboxId: Int) {
        Realm.deleteRealm(RealmConfig.mailboxContent(mailboxId))
    }

    fun close() {
        closeMailboxContent()
        closeMailboxInfo()
        closeUserInfo()
        closeAppSettings()
    }

    private fun closeAppSettings() {
        _appSettings?.close()
        _appSettings = null
    }

    fun closeUserInfo() {
        _userInfo?.close()
        _userInfo = null
    }

    private fun closeMailboxInfo() {
        _mailboxInfo?.close()
        _mailboxInfo = null
    }

    fun closeMailboxContent() {
        _mailboxContent?.close()
        _mailboxContent = null
    }

    private object RealmConfig {

        private const val appSettingsDbName = "AppSettings.realm"
        private val userInfoDbName get() = "User-${AccountUtils.currentUserId}.realm"
        private const val mailboxInfoDbName = "MailboxInfo.realm"
        private fun mailboxContentDbName(mailboxId: Int) = "Mailbox-${AccountUtils.currentUserId}-${mailboxId}.realm"

        val appSettings =
            RealmConfiguration
                .Builder(RealmSets.appSettings)
                .name(appSettingsDbName)
                .deleteRealmIfMigrationNeeded() // TODO: Do we want to keep this in production?
                .build()

        val userInfo
            get() = RealmConfiguration
                .Builder(RealmSets.userInfo)
                .name(userInfoDbName)
                .deleteRealmIfMigrationNeeded() // TODO: Do we want to keep this in production?
                .build()

        val mailboxInfo =
            RealmConfiguration
                .Builder(RealmSets.mailboxInfo)
                .name(mailboxInfoDbName)
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

            val userInfo = setOf(
                AddressBook::class,
                MergedContact::class,
            )

            val mailboxInfo = setOf(
                Mailbox::class,
                Quotas::class,
            )

            val mailboxContent = setOf(
                Folder::class,
                Thread::class,
                Message::class,
                Draft::class,
                Recipient::class,
                Body::class,
                Attachment::class,
                Signature::class,
            )

            val miscellaneous = setOf(
                SignatureEmail::class,
            )
        }
    }
}
