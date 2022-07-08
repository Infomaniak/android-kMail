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
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.data.models.signature.SignatureEmail
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.data.models.user.UserInfos
import com.infomaniak.mail.data.models.user.UserPreferences
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

@Suppress("ObjectPropertyName")
object MailRealm {

    private var _appSettings: Realm? = null
    private var _mailboxInfos: Realm? = null
    private var _mailboxContent: Realm? = null
    private var _userInfos: Realm? = null

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

    fun close() {
        closeContacts()
        closeMailboxContent()
        closeMailboxInfo()
        closeAppSettings()
    }

    private fun closeAppSettings() {
        _appSettings?.close()
        _appSettings = null
    }

    private fun closeMailboxInfo() {
        _mailboxInfos?.close()
        _mailboxInfos = null
    }

    fun closeMailboxContent() {
        _mailboxContent?.close()
        _mailboxContent = null
    }

    fun closeContacts() {
        _userInfos?.close()
        _userInfos = null
    }

    fun readUserPreferences(): UserPreferences = UserInfosController.getUserPreferences()

    fun readAddressBooks(): SharedFlow<ResultsChange<AddressBook>> = UserInfosController.getAddressBooks().toSharedFlow()

    fun readContacts(): SharedFlow<ResultsChange<Contact>> = UserInfosController.getContacts().toSharedFlow()

    fun getMailboxConfiguration(mailboxId: Int): RealmConfiguration = RealmConfigurations.mailboxContent(mailboxId)

    fun readMailboxes(): SharedFlow<ResultsChange<Mailbox>> {
        return MailboxInfosController.getMailboxesAsync(AccountUtils.currentUserId).toSharedFlow()
    }

    fun readFolders(): SharedFlow<ResultsChange<Folder>> = MailboxContentController.getFoldersAsync().toSharedFlow()

    fun readThreads(folder: Folder): List<Thread> = MailboxContentController.getFolderThreads(folder.id)

    fun readMessages(thread: Thread): List<Message> = thread.messages

    /**
     * Utils
     */
    private fun <T> Flow<T>.toSharedFlow(): SharedFlow<T> {
        return distinctUntilChanged().shareIn(
            scope = CoroutineScope(Dispatchers.IO),
            started = SharingStarted.Eagerly,
            replay = 1,
        )
    }

    /**
     * Configurations
     */
    @Suppress("FunctionName")
    private object RealmConfigurations {

        private const val APP_SETTINGS_DB_NAME = "AppSettings.realm"
        private val USER_INFOS_DB_NAME get() = "${AccountUtils.currentUserId}.realm"
        private const val MAILBOX_INFOS_DB_NAME = "MailboxInfos.realm"
        private fun MAILBOX_CONTENT_DB_NAME(mailboxId: Int) = "${AccountUtils.currentUserId}-${mailboxId}.realm"

        val appSettings =
            RealmConfiguration
                .Builder(RealmSets.appSettings)
                .name(APP_SETTINGS_DB_NAME)
                .deleteRealmIfMigrationNeeded()
                .build()

        val userInfos
            get() = RealmConfiguration
                .Builder(RealmSets.userInfos)
                .name(USER_INFOS_DB_NAME)
                .deleteRealmIfMigrationNeeded()
                .build()

        val mailboxInfos =
            RealmConfiguration
                .Builder(RealmSets.mailboxInfos)
                .name(MAILBOX_INFOS_DB_NAME)
                .deleteRealmIfMigrationNeeded()
                .build()

        fun mailboxContent(mailboxId: Int) =
            RealmConfiguration
                .Builder(RealmSets.mailboxContent)
                .name(MAILBOX_CONTENT_DB_NAME(mailboxId))
                .deleteRealmIfMigrationNeeded()
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
                Recipient::class,
                Body::class,
                Attachment::class,
            )

            val miscellaneous = setOf(
                Signature::class,
                SignatureEmail::class,
                UserInfos::class,
                UserPreferences::class,
            )
        }
    }
}
