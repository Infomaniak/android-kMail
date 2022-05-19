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
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Suppress("ObjectPropertyName")
object MailRealm {

    private var _appSettings: Realm? = null
    private var _mailboxInfo: Realm? = null
    private var _mailboxContent: Realm? = null

    val appSettings: Realm
        get() = _appSettings
            ?: Realm.open(RealmConfigurations.appSettings).also { _appSettings = it }

    val mailboxInfo
        get() = _mailboxInfo
            ?: Realm.open(RealmConfigurations.mailboxInfo).also { _mailboxInfo = it }

    val mailboxContent
        get() = _mailboxContent
            ?: Realm.open(RealmConfigurations.mailboxContent(AccountUtils.currentMailboxId)).also { _mailboxContent = it }

    fun closeRealms() {
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

    // Current mailbox flow
    val mutableCurrentMailboxObjectIdFlow: MutableStateFlow<String?> = MutableStateFlow(null)
    val currentMailboxObjectIdFlow = mutableCurrentMailboxObjectIdFlow.asStateFlow()

    // Current folder flow
    val mutableCurrentFolderIdFlow: MutableStateFlow<String?> = MutableStateFlow(null)
    val currentFolderIdFlow = mutableCurrentFolderIdFlow.asStateFlow()

    // Current thread flow
    val mutableCurrentThreadUidFlow: MutableStateFlow<String?> = MutableStateFlow(null)
    val currentThreadUidFlow = mutableCurrentThreadUidFlow.asStateFlow()

    // Current message flow
    val mutableCurrentMessageUidFlow: MutableStateFlow<String?> = MutableStateFlow(null)
    val currentMessageUidFlow = mutableCurrentMessageUidFlow.asStateFlow()

    /**
     * Mailboxes
     */
    fun readMailboxesFromRealm(): List<Mailbox> = MailboxInfoController.getMailboxes()

    fun getMailboxConfiguration(mailboxId: Int): RealmConfiguration = RealmConfigurations.mailboxContent(mailboxId)

    /**
     * Configurations
     */
    @Suppress("FunctionName")
    private object RealmConfigurations {

        private const val APP_SETTINGS_DB_NAME = "AppSettings.realm"
        private const val MAILBOX_INFO_DB_NAME = "MailboxInfo.realm"
        private fun MAILBOX_CONTENT_DB_NAME(currentMailboxId: Int) = "${AccountUtils.currentUserId}-${currentMailboxId}.realm"

        val appSettings = RealmConfiguration
            .Builder(RealmSets.appSettings)
            .name(APP_SETTINGS_DB_NAME)
            .deleteRealmIfMigrationNeeded()
            .build()

        val mailboxInfo = RealmConfiguration
            .Builder(RealmSets.mailboxInfo)
            .name(MAILBOX_INFO_DB_NAME)
            .deleteRealmIfMigrationNeeded()
            .build()

        fun mailboxContent(currentMailboxId: Int) = RealmConfiguration
            .Builder(RealmSets.mailboxContent)
            .name(MAILBOX_CONTENT_DB_NAME(currentMailboxId))
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

            val others = setOf(
                AddressBook::class,
                Contact::class,
                Quotas::class,
                Signature::class,
                SignatureEmail::class,
                UserInfos::class,
                UserPreferences::class,
            )
        }
    }
}
