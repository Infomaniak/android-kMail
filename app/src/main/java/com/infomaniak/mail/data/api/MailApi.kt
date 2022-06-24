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
package com.infomaniak.mail.data.api

import android.util.Log
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.Mailbox
import io.realm.Realm

object MailApi {

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
            Realm.deleteRealm(MailRealm.getMailboxConfiguration(it.mailboxId))
        }

        return mailboxesFromApi
    }
}
