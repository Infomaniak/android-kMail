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
package com.infomaniak.mail.utils

import com.infomaniak.mail.data.models.*
import com.infomaniak.mail.data.models.addressBooks.AddressBook
import com.infomaniak.mail.data.models.attachment.Attachment
import com.infomaniak.mail.data.models.attachment.AttachmentData
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signatures.Signature
import com.infomaniak.mail.data.models.signatures.SignatureEmail
import com.infomaniak.mail.data.models.threads.Thread
import com.infomaniak.mail.data.models.user.UserInfos
import com.infomaniak.mail.data.models.user.UserPreferences
import io.realm.Realm
import io.realm.RealmConfiguration

object Realms {

    val appSettings = Realm.open(RealmConfigurations.appSettings)
    val mailboxInfo = Realm.open(RealmConfigurations.mailboxInfo)
    lateinit var mailboxContent: Realm

    fun selectCurrentMailbox() {
        if (::mailboxContent.isInitialized) mailboxContent.close()
        mailboxContent = Realm.open(RealmConfigurations.mailboxContent())
    }

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
                Body::class,
                Attachment::class,
                AttachmentData::class,
                Recipient::class,
            )

            val others = setOf(
                AddressBook::class,
                Contact::class,
                Draft::class,
                Quotas::class,
                Signature::class,
                SignatureEmail::class,
                UserInfos::class,
                UserPreferences::class,
            )
        }
    }
}
