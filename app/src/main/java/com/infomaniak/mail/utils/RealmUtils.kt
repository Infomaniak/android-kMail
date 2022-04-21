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
import com.infomaniak.mail.data.models.threads.ThreadMail
import com.infomaniak.mail.data.models.user.UserInfos
import com.infomaniak.mail.data.models.user.UserPreferences
import io.realm.Realm
import io.realm.RealmConfiguration

object Realms {
    val appSettings = Realm.open(RealmConfigurations.appSettings)
    val mailboxInfos = Realm.open(RealmConfigurations.mailboxInfos)
    val mailbox by lazy { Realm.open(RealmConfigurations.mailbox) } // TODO: Handle when the user changes of mailbox.
}

@Suppress("FunctionName")
private object RealmConfigurations {

    private const val APP_SETTINGS_DB_NAME = "AppSettings.realm"
    private const val MAILBOX_INFOS_DB_NAME = "MailboxInfos.realm"
    private fun MAILBOX_DB_NAME(usrId: Int, mailboxId: Int) = "$usrId-$mailboxId.realm"

    val appSettings = RealmConfiguration
        .Builder(RealmSets.appSettings)
        .name(APP_SETTINGS_DB_NAME)
        .deleteRealmIfMigrationNeeded()
        .build()

    val mailboxInfos = RealmConfiguration
        .Builder(RealmSets.mailboxInfos)
        .name(MAILBOX_INFOS_DB_NAME)
        .deleteRealmIfMigrationNeeded()
        .build()

    val mailbox by lazy {
        RealmConfiguration
            .Builder(RealmSets.mailbox)
            .name(MAILBOX_DB_NAME(AccountUtils.currentUserId, AccountUtils.currentMailboxId))
            .deleteRealmIfMigrationNeeded()
            .build()
    }
}

private object RealmSets {

    val appSettings = setOf(
        AppSettings::class,
    )

    val mailboxInfos = setOf(
        Mailbox::class,
    )

    val mailbox = setOf(
        Attachment::class,
        Folder::class,
        Message::class,
        Body::class,
        Recipient::class,
        ThreadMail::class,
    )

    val others = setOf(
        AddressBook::class,
        AttachmentData::class,
        Contact::class,
        Draft::class,
        Quotas::class,
        Signature::class,
        SignatureEmail::class,
        UserInfos::class,
        UserPreferences::class,
    )
}