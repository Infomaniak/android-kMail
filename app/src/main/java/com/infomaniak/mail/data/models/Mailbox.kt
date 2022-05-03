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
import com.google.gson.annotations.SerializedName
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.utils.AccountUtils
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

class Mailbox : RealmObject {
    var uuid: String = ""
    var email: String = ""

    @SerializedName("email_idn")
    var emailIdn: String = ""
    var mailbox: String = ""

    @SerializedName("real_mailbox")
    var realMailbox: String = ""

    @SerializedName("link_id")
    var linkId: Int = 0

    @SerializedName("mailbox_id")
    var mailboxId: Int = -1

    @SerializedName("hosting_id")
    var hostingId: Int = 0

    @SerializedName("is_primary")
    var isPrimary: Boolean = false

    @SerializedName("password_status")
    var passwordStatus: String = ""

    @SerializedName("is_password_valid")
    var isPasswordValid: Boolean = false

    @SerializedName("is_valid")
    var isMailboxValid: Boolean = false

    @SerializedName("is_locked")
    var isLocked: Boolean = false

    @SerializedName("has_social_and_commercial_filtering")
    var hasSocialAndCommercialFiltering: Boolean = false

    @SerializedName("show_config_modal")
    var showConfigModal: Boolean = false

    @SerializedName("force_reset_password")
    var forceResetPassword: Boolean = false

    @SerializedName("mda_version")
    var mdaVersion: String = ""

    @SerializedName("is_limited")
    var isLimited: Boolean = false

    @SerializedName("is_free")
    var isFree: Boolean = false

    @SerializedName("daily_limit")
    var dailyLimit: Int = 0

    /**
     * Local
     */
    var userId: Int = -1

    @PrimaryKey
    var objectId: String = ""

    fun initLocalValues(): Mailbox {
        userId = AccountUtils.currentUserId
        objectId = "${mailboxId}_${AccountUtils.currentUserId}"

        return this
    }

    fun getFolders(): List<Folder> {

        AccountUtils.currentMailboxId = mailboxId

        // Get current data
        Log.d("Realm", "getUpdatedFolders: Get current data")
        val folderFromRealm = MailboxContentController.getFolders()
        // TODO: Handle connectivity issues. If there is no Internet, all Realm Folders will be deleted. We don't want that.
        val foldersFromAPI = ApiRepository.getFolders(this).data ?: emptyList()

        // Get outdated data
        Log.d("Realm", "getUpdatedFolders: Get outdated data")
        val deletableFolders = folderFromRealm.filter { fromRealm ->
            !foldersFromAPI.any { fromApi -> fromApi.id == fromRealm.id }
        }
        val possiblyDeletableThreads = deletableFolders.flatMap { it.threads }
        val deletableMessages = possiblyDeletableThreads.flatMap { it.messages }.filter { message ->
            deletableFolders.any { folder -> folder.id == message.folderId }
        }
        val deletableThreads = possiblyDeletableThreads.filter { thread ->
            thread.messages.all { message -> deletableMessages.any { it.uid == message.uid } }
        }

        // Delete outdated data
        Log.e("Realm", "getUpdatedFolders: Delete outdated data")
        deletableMessages.forEach { MailboxContentController.deleteMessage(it.uid) }
        deletableThreads.forEach { MailboxContentController.deleteThread(it.uid) }
        deletableFolders.forEach { MailboxContentController.deleteFolder(it.id) }

        // Save new data
        Log.i("Realm", "getUpdatedFolders: Save new data")
        foldersFromAPI.forEach(MailboxContentController::upsertFolder)

        MailRealm.currentMailboxFlow.value = this

        return foldersFromAPI
    }
}
