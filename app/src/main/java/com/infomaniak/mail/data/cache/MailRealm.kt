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
import kotlinx.coroutines.flow.asStateFlow

object MailRealm {

    val mailboxInfo = Realm.open(RealmConfigurations.mailboxInfo)
    lateinit var mailboxContent: Realm

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

    fun fetchMailboxesFromApi(isInternetAvailable: Boolean): List<Mailbox> {
        // Get current data
        Log.d("API", "Mailboxes: Get current data")
        val mailboxesFromRealm = MailboxInfoController.getMailboxes()
        val mailboxesFromApi = ApiRepository.getMailboxes().data?.map { it.initLocalValues() } ?: emptyList()

        // Get outdated data
        Log.d("API", "Mailboxes: Get outdated data")
        val deletableMailboxes = if (isInternetAvailable) {
            mailboxesFromRealm.filter { fromRealm ->
                !mailboxesFromApi.any { fromApi -> fromApi.mailboxId == fromRealm.mailboxId }
            }
        } else {
            emptyList()
        }

        // Save new data
        Log.i("API", "Mailboxes: Save new data")
        mailboxesFromApi.forEach(MailboxInfoController::upsertMailbox)

        // Delete outdated data
        Log.e("API", "Mailboxes: Delete outdated data")
        deletableMailboxes.forEach {
            MailboxInfoController.deleteMailbox(it.objectId)
            Realm.deleteRealm(RealmConfigurations.mailboxContent(it.mailboxId))
        }

        return mailboxesFromApi
    }

    fun selectCurrentMailbox() {
        if (MailRealm::mailboxContent.isInitialized) mailboxContent.close()
        mailboxContent = Realm.open(RealmConfigurations.mailboxContent(AccountUtils.currentMailboxId))
    }

    /**
     * Configurations
     */
    @Suppress("FunctionName")
    private object RealmConfigurations {

        private const val MAILBOX_INFO_DB_NAME = "MailboxInfo.realm"
        private fun MAILBOX_CONTENT_DB_NAME(currentMailboxId: Int) = "${AccountUtils.currentUserId}-${currentMailboxId}.realm"

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
