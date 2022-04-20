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
import io.realm.annotations.RealmModule

object RealmModules {
    @RealmModule(
        classes = [
            AppSettings::class,
        ]
    )
    class AppSettingsModule

    @RealmModule(
        classes = [
            Mailbox::class,
        ]
    )
    class MailboxModule

    @RealmModule(
        classes = [
            Attachment::class,
            Folder::class,
            Message::class,
            Body::class,
            Recipient::class,
            Thread::class,
        ]
    )
    class MailsModule

    @RealmModule(
        classes = [
            AddressBook::class,
            AttachmentData::class,
            Contact::class,
            Draft::class,
            Quotas::class,
            Signature::class,
            SignatureEmail::class,
            UserInfos::class,
            UserPreferences::class,
        ]
    )
    class OtherModule
}