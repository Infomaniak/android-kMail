/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.content.Context
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
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
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.NotificationUtils.deleteMailNotificationChannel
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("ObjectPropertyName")
object RealmDatabase {

    val TAG: String = RealmDatabase::class.java.simpleName

    //region Realms
    private var _appSettings: Realm? = null
    private var _userInfo: Realm? = null
    private var _mailboxInfo: Realm? = null
    private var _mailboxContent: Realm? = null
    //endregion

    //region Realms' mutexes
    private val appSettingsMutex = Mutex()
    private val userInfoMutex = Mutex()
    private val mailboxInfoMutex = Mutex()
    private val mailboxContentMutex = Mutex()
    //endregion

    //region Open Realms
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

    val newMailboxInfoInstance get() = Realm.open(RealmConfig.mailboxInfo)
    fun mailboxInfo(): Realm = runBlocking(Dispatchers.IO) {
        mailboxInfoMutex.withLock {
            _mailboxInfo ?: newMailboxInfoInstance.also { _mailboxInfo = it }
        }
    }

    val newMailboxContentInstance get() = Realm.open(RealmConfig.mailboxContent(AccountUtils.currentMailboxId))
    fun newMailboxContentInstance(userId: Int, mailboxId: Int) = Realm.open(RealmConfig.mailboxContent(mailboxId, userId))
    fun mailboxContent(): Realm = runBlocking(Dispatchers.IO) {
        mailboxContentMutex.withLock {
            _mailboxContent ?: newMailboxContentInstance.also { _mailboxContent = it }
        }
    }
    //endregion

    //region Close Realms
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
    //endregion

    //region Delete Realm
    fun deleteMailboxContent(mailboxId: Int) {
        Realm.deleteRealm(RealmConfig.mailboxContent(mailboxId))
    }

    fun removeUserData(context: Context, userId: Int) {
        closeMailboxContent()
        closeUserInfo()
        mailboxInfo().writeBlocking {
            val mailboxes = MailboxController.getMailboxes(userId, realm = this)
            context.deleteMailNotificationChannel(mailboxes)
            delete(mailboxes)
        }
        deleteUserFiles(context, userId)
    }

    private fun deleteUserFiles(context: Context, userId: Int) {
        LocalStorageUtils.deleteUserData(context, userId)
        context.filesDir.listFiles()?.forEach { file ->
            val isMailboxContent = file.name.startsWith(RealmConfig.mailboxContentDbNamePrefix(userId))
            val isUserInfo = file.name.startsWith(RealmConfig.userInfoDbName(userId))
            if (isMailboxContent || isUserInfo) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
    }
    //endregion

    private object RealmConfig {

        //region Configurations names
        const val appSettingsDbName = "AppSettings.realm"
        const val mailboxInfoDbName = "MailboxInfo.realm"
        fun userInfoDbName(userId: Int) = "User-${userId}.realm"
        fun mailboxContentDbNamePrefix(userId: Int) = "Mailbox-${userId}-"
        fun mailboxContentDbName(userId: Int, mailboxId: Int) = "${mailboxContentDbNamePrefix(userId)}${mailboxId}.realm"
        //endregion

        //region Configurations sets
        val appSettingsSet = setOf(
            AppSettings::class,
        )
        val userInfoSet = setOf(
            AddressBook::class,
            MergedContact::class,
        )
        val mailboxInfoSet = setOf(
            Mailbox::class,
            Quotas::class,
        )
        val mailboxContentSet = setOf(
            Folder::class,
            Thread::class,
            Message::class,
            Draft::class,
            Recipient::class,
            Body::class,
            Attachment::class,
            Signature::class,
        )
        val miscellaneousSet = setOf(
            SignatureEmail::class,
        )
        //endregion

        //region Configurations
        val appSettings =
            RealmConfiguration
                .Builder(appSettingsSet)
                .name(appSettingsDbName)
                .deleteRealmIfMigrationNeeded() // TODO: Handle migration in production.
                .build()

        val userInfo
            get() = RealmConfiguration
                .Builder(userInfoSet)
                .name(userInfoDbName(AccountUtils.currentUserId))
                .deleteRealmIfMigrationNeeded() // TODO: Handle migration in production.
                .build()

        val mailboxInfo =
            RealmConfiguration
                .Builder(mailboxInfoSet)
                .name(mailboxInfoDbName)
                .deleteRealmIfMigrationNeeded() // TODO: Handle migration in production.
                .build()

        fun mailboxContent(mailboxId: Int, userId: Int = AccountUtils.currentUserId) =
            RealmConfiguration
                .Builder(mailboxContentSet)
                .name(mailboxContentDbName(userId, mailboxId))
                .deleteRealmIfMigrationNeeded() // TODO: Handle migration in production.
                .build()
        //endregion
    }
}
