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
    private var _mailboxInfo: Realm? = null
    private var _mailboxContent: Realm? = null
    private var _contacts: Realm? = null

    val appSettings: Realm
        get() = _appSettings
            ?: Realm.open(RealmConfigurations.appSettings).also { _appSettings = it }

    val mailboxInfo
        get() = _mailboxInfo
            ?: Realm.open(RealmConfigurations.mailboxInfo).also { _mailboxInfo = it }

    val mailboxContent
        get() = _mailboxContent
            ?: Realm.open(RealmConfigurations.mailboxContent(AccountUtils.currentMailboxId)).also { _mailboxContent = it }

    val contacts
        get() = _contacts
            ?: Realm.open(RealmConfigurations.contacts).also { _contacts = it }

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
        _mailboxInfo?.close()
        _mailboxInfo = null
    }

    fun closeMailboxContent() {
        _mailboxContent?.close()
        _mailboxContent = null
    }

    fun closeContacts() {
        _contacts?.close()
        _contacts = null
    }

    fun readContacts(): SharedFlow<ResultsChange<Contact>> = ContactsController.getContacts().toSharedFlow()

    fun getMailboxConfiguration(mailboxId: Int): RealmConfiguration = RealmConfigurations.mailboxContent(mailboxId)

    fun readMailboxes(): SharedFlow<ResultsChange<Mailbox>> {
        return MailboxInfoController.getMailboxesAsync(AccountUtils.currentUserId).toSharedFlow()
    }

    fun readFolders(): SharedFlow<ResultsChange<Folder>> {
        return MailboxContentController.getFoldersAsync().toSharedFlow()
    }

    fun readThreads(folder: Folder): List<Thread> {
        return MailboxContentController.getFolderThreads(folder.id)
    }

    fun readMessages(thread: Thread): List<Message> {
        return thread.messages
    }

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
        private const val MAILBOX_INFO_DB_NAME = "MailboxInfo.realm"
        private fun MAILBOX_CONTENT_DB_NAME(mailboxId: Int) = "${AccountUtils.currentUserId}-${mailboxId}.realm"
        private val CONTACTS_DB_NAME get() = "${AccountUtils.currentUserId}.realm"

        val appSettings =
            RealmConfiguration
                .Builder(RealmSets.appSettings)
                .name(APP_SETTINGS_DB_NAME)
                .deleteRealmIfMigrationNeeded()
                .build()

        val mailboxInfo =
            RealmConfiguration
                .Builder(RealmSets.mailboxInfo)
                .name(MAILBOX_INFO_DB_NAME)
                .deleteRealmIfMigrationNeeded()
                .build()

        fun mailboxContent(mailboxId: Int) =
            RealmConfiguration
                .Builder(RealmSets.mailboxContent)
                .name(MAILBOX_CONTENT_DB_NAME(mailboxId))
                .deleteRealmIfMigrationNeeded()
                .build()

        val contacts
            get() = RealmConfiguration
                .Builder(RealmSets.contacts)
                .name(CONTACTS_DB_NAME)
                .deleteRealmIfMigrationNeeded()
                .build()

        private object RealmSets {

            val appSettings = setOf(
                AppSettings::class,
            )

            val mailboxInfo = setOf(
                Mailbox::class,
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

            val contacts = setOf(
                AddressBook::class,
                Contact::class,
            )

            val miscellaneous = setOf(
                Quotas::class,
                Signature::class,
                SignatureEmail::class,
                UserInfos::class,
                UserPreferences::class,
            )
        }
    }
}
