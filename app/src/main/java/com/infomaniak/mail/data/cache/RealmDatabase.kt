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
package com.infomaniak.mail.data.cache

import android.content.Context
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.calendar.CalendarEvent
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.message.SubBody
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.internal.platform.WeakReference
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
    private var _mailboxContent: Realm? = null

    private var oldUserInfo = WeakReference<Realm?>(null)
    private var oldMailboxContent = WeakReference<Realm?>(null)
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

    val mailboxInfo get() = Realm.open(RealmConfig.mailboxInfo)

    val newMailboxContentInstance get() = newMailboxContentInstance(AccountUtils.currentUserId, AccountUtils.currentMailboxId)
    fun newMailboxContentInstance(userId: Int, mailboxId: Int) = Realm.open(RealmConfig.mailboxContent(mailboxId, userId))

    class MailboxContent {
        operator fun invoke() = runBlocking(Dispatchers.IO) {
            mailboxContentMutex.withLock {
                _mailboxContent ?: newMailboxContentInstance.also { _mailboxContent = it }
            }
        }
    }
    //endregion

    //region Close Realms
    fun closeOldRealms() {
        oldUserInfo.get()?.let {
            if (!it.isClosed()) it.close()
        }
        oldMailboxContent.get()?.let {
            if (!it.isClosed()) it.close()
        }
    }

    private fun closeUserInfo() {
        _userInfo?.close()
        resetUserInfo()
    }

    fun closeMailboxContent() {
        _mailboxContent?.close()
        resetMailboxContent()
    }
    //endregion

    //region Reset Realms
    fun backupPreviousRealms() {
        oldUserInfo = WeakReference(_userInfo)
        backupPreviousMailboxContent()
    }

    fun backupPreviousMailboxContent() {
        oldMailboxContent = WeakReference(_mailboxContent)
    }

    fun resetUserInfo() {
        _userInfo = null
    }

    fun resetMailboxContent() {
        _mailboxContent = null
    }
    //endregion

    //region Delete Realm
    fun deleteMailboxContent(mailboxId: Int, userId: Int = AccountUtils.currentUserId) {
        Realm.deleteRealm(RealmConfig.mailboxContent(mailboxId, userId))
    }

    fun removeUserData(context: Context, userId: Int) {
        closeMailboxContent()
        closeUserInfo()
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

        //region Configurations versions
        const val USER_INFO_SCHEMA_VERSION = 1L
        const val MAILBOX_INFO_SCHEMA_VERSION = 5L
        const val MAILBOX_CONTENT_SCHEMA_VERSION = 13L
        //endregion

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
            MailboxPermissions::class,
            Quotas::class,
        )
        val mailboxContentSet = setOf(
            Folder::class,
            Thread::class,
            Message::class,
            Draft::class,
            Recipient::class,
            Body::class,
            SubBody::class,
            Attachment::class,
            CalendarEventResponse::class,
            CalendarEvent::class,
            Attendee::class,
            Signature::class,
        )
        //endregion

        //region Configurations
        val appSettings = RealmConfiguration
            .Builder(appSettingsSet)
            .name(appSettingsDbName)
            .build()

        val userInfo
            get() = RealmConfiguration
                .Builder(userInfoSet)
                .name(userInfoDbName(AccountUtils.currentUserId))
                .schemaVersion(USER_INFO_SCHEMA_VERSION)
                .migration(USER_INFO_MIGRATION)
                .build()

        val mailboxInfo = RealmConfiguration
            .Builder(mailboxInfoSet)
            .name(mailboxInfoDbName)
            .schemaVersion(MAILBOX_INFO_SCHEMA_VERSION)
            .migration(MAILBOX_INFO_MIGRATION)
            .build()

        fun mailboxContent(mailboxId: Int, userId: Int) = RealmConfiguration
            .Builder(mailboxContentSet)
            .name(mailboxContentDbName(userId, mailboxId))
            .schemaVersion(MAILBOX_CONTENT_SCHEMA_VERSION)
            .migration(MAILBOX_CONTENT_MIGRATION)
            .build()
        //endregion
    }
}
