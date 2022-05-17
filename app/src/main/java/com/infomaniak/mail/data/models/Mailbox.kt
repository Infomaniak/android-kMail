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
package com.infomaniak.mail.data.models

import android.util.Log
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.utils.AccountUtils
import io.realm.MutableRealm.UpdatePolicy
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.toRealmList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Mailbox : RealmObject {
    var uuid: String = ""
    var email: String = ""
    @SerialName("email_idn")
    var emailIdn: String = ""
    var mailbox: String = ""
    @SerialName("real_mailbox")
    var realMailbox: String = ""
    @SerialName("link_id")
    var linkId: Int = 0
    @SerialName("mailbox_id")
    var mailboxId: Int = -1
    @SerialName("hosting_id")
    var hostingId: Int = 0
    @SerialName("is_primary")
    var isPrimary: Boolean = false
    @SerialName("password_status")
    var passwordStatus: String = ""
    @SerialName("is_password_valid")
    var isPasswordValid: Boolean = false
    @SerialName("is_valid")
    var isMailboxValid: Boolean = false
    @SerialName("is_locked")
    var isLocked: Boolean = false
    @SerialName("has_social_and_commercial_filtering")
    var hasSocialAndCommercialFiltering: Boolean = false
    @SerialName("show_config_modal")
    var showConfigModal: Boolean = false
    @SerialName("force_reset_password")
    var forceResetPassword: Boolean = false
    @SerialName("mda_version")
    var mdaVersion: String = ""
    @SerialName("is_limited")
    var isLimited: Boolean = false
    @SerialName("is_free")
    var isFree: Boolean = false
    @SerialName("daily_limit")
    var dailyLimit: Int = 0

    /**
     * Local
     */
    @PrimaryKey
    var objectId: String = ""
    var userId: Int = -1

    fun initLocalValues(): Mailbox {
        objectId = "${AccountUtils.currentUserId}_${mailboxId}"
        userId = AccountUtils.currentUserId

        return this
    }

    fun select() {
        if (MailRealm.currentMailboxObjectIdFlow.value != objectId) {
            AccountUtils.currentMailboxId = mailboxId
            MailRealm.mutableCurrentMailboxObjectIdFlow.value = objectId
        }
    }

    fun readFoldersFromRealm(): List<Folder> = MailboxContentController.getFolders()

    fun fetchFoldersFromApi(isInternetAvailable: Boolean): List<Folder> {

        // Get current data
        Log.d("API", "Folders: Get current data")
        val foldersFromRealm = MailboxContentController.getFolders()
        val foldersFromApi = ApiRepository.getFolders(uuid).data ?: emptyList()

        // Get outdated data
        Log.d("API", "Folders: Get outdated data")
        val deletableFolders = if (isInternetAvailable) {
            foldersFromRealm.filter { fromRealm ->
                !foldersFromApi.any { fromApi -> fromApi.id == fromRealm.id }
            }
        } else {
            emptyList()
        }
        val possiblyDeletableThreads = deletableFolders.flatMap { it.threads }
        val deletableMessages = possiblyDeletableThreads.flatMap { it.messages }.filter { message ->
            deletableFolders.any { folder -> folder.id == message.folderId }
        }
        val deletableThreads = possiblyDeletableThreads.filter { thread ->
            thread.messages.all { message -> deletableMessages.any { it.uid == message.uid } }
        }

        // Save new data
        Log.i("API", "Folders: Save new data")
        MailRealm.mailboxContent.writeBlocking {
            foldersFromApi.forEach { folderFromApi ->
                val folder = copyToRealm(folderFromApi, UpdatePolicy.ALL)
                foldersFromRealm.find { it.id == folderFromApi.id }?.threads
                    ?.mapNotNull(::findLatest)
                    ?.let { folder.threads = it.toRealmList() }
            }
        }

        // Delete outdated data
        Log.e("API", "Folders: Delete outdated data")
        deletableMessages.forEach { MailboxContentController.deleteMessage(it.uid) }
        deletableThreads.forEach { MailboxContentController.deleteThread(it.uid) }
        deletableFolders.forEach { MailboxContentController.deleteFolder(it.id) }

        return foldersFromApi
    }
}
