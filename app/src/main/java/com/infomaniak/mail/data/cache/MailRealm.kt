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

import android.util.Log
import com.infomaniak.mail.data.api.ApiRepository
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

object MailRealm {

    val appSettings = Realm.open(RealmConfigurations.appSettings)
    val mailboxInfo = Realm.open(RealmConfigurations.mailboxInfo)
    lateinit var mailboxContent: Realm

    var mailboxes2: List<Mailbox> = emptyList()
    val currentMailboxFlow: MutableStateFlow<Mailbox?> = MutableStateFlow(null)
    // var currentMailbox: Mailbox? = null // TODO: Remove it (blocked by https://github.com/realm/realm-kotlin/issues/805)
    val currentFolderFlow: MutableStateFlow<Folder?> = MutableStateFlow(null) // TODO: Remove it (blocked by https://github.com/realm/realm-kotlin/issues/805)
    var currentThread: Thread? = null // TODO: Remove it (blocked by https://github.com/realm/realm-kotlin/issues/805)

    /**
     * Mailboxes
     */
    // fun getCachedMailboxes(): List<Mailbox> = MailboxInfoController.getMailboxes()

    fun getMailboxes(): List<Mailbox> {
        // Get current data
        Log.d("Realm", "getUpdatedMailboxes: Get current data")
        val mailboxesFromRealm = MailboxInfoController.getMailboxes()
        // TODO: Handle connectivity issues. If there is no Internet, all Realm Mailboxes will be deleted. We don't want that.
        val mailboxesFromAPI = ApiRepository.getMailboxes().data?.map { it.initLocalValues() } ?: emptyList()

        // Get outdated data
        Log.d("Realm", "getUpdatedMailboxes: Get outdated data")
        val deletableMailboxes = mailboxesFromRealm.filter { fromRealm ->
            !mailboxesFromAPI.any { fromApi -> fromApi.mailboxId == fromRealm.mailboxId }
        }

        // Delete outdated data
        Log.e("Realm", "getUpdatedMailboxes: Delete outdated data")
        deletableMailboxes.forEach {
            // TODO: Delete each Realm file in the same time, with `Realm.deleteRealm()`
            MailboxInfoController.deleteMailbox(it.objectId)
        }

        // Save new data
        Log.i("Realm", "getUpdatedMailboxes: Save new data")
        mailboxesFromAPI.forEach(MailboxInfoController::upsertMailbox)

        return mailboxesFromAPI
    }

    fun selectCurrentMailbox() {
        if (MailRealm::mailboxContent.isInitialized) mailboxContent.close()
        mailboxContent = Realm.open(RealmConfigurations.mailboxContent())
    }

    /**
     * Configurations
     */
    private object RealmConfigurations {

        private const val APP_SETTINGS_DB_NAME = "AppSettings.realm"
        private const val MAILBOX_INFO_DB_NAME = "MailboxInfo.realm"
        private val MAILBOX_CONTENT_DB_NAME = { "${AccountUtils.currentUserId}-${AccountUtils.currentMailboxId}.realm" }

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

        val mailboxContent = {
            RealmConfiguration
                .Builder(RealmSets.mailboxContent)
                .name(MAILBOX_CONTENT_DB_NAME())
                .deleteRealmIfMigrationNeeded()
                .build()
        }

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
