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
package com.infomaniak.mail.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.threads.Thread
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.Realms
import io.realm.MutableRealm
import io.realm.toRealmList

class TestRealmViewModel : ViewModel() {

    fun testRealm() {

        // Get & save Test mailbox's infos
        val testMailboxInfo = ApiRepository.getMailboxes()
            .data
            ?.firstOrNull { it.email == "kevin.boulongne@ik.me" }
            ?.initLocalValues()
            ?: return
        MailboxInfoController.upsertMailboxInfo(testMailboxInfo)
        Log.i("Realm", "Upserted MailboxInfo: ${testMailboxInfo.email}")

        // Get Test mailbox
        val testMailbox = MailboxInfoController.getMailboxInfos().firstOrNull() ?: return

        // Select it
        AccountUtils.currentMailboxId = testMailbox.mailboxId
        Log.e("Realm", "Switched to current mailbox: ${testMailbox.email}")

        // Get & save Inbox
        ApiRepository.getFolders(testMailbox).data?.forEach { folder ->

            ApiRepository.getThreads(testMailbox, folder).data?.threads
                ?.also { threads -> folder.threads = threads.toRealmList() }
                ?.forEach { thread ->
                    // MailboxContentController.upsertThread(thread)
                    // Log.i("Realm", "Upserted thread: ${thread.uid}")
                }

            MailboxContentController.upsertFolder(folder)
            Log.e("Realm", "Upserted folder: ${folder.name}")
        }
    }

    fun testRealm2() {


        Log.e("TOTO", "ApiRepository.getMailboxes")
        val mailboxInfosFromAPI = ApiRepository.getMailboxes().data?.map { it.initLocalValues() } ?: emptyList()
        Log.e("TOTO", "mailboxInfosFromAPI.forEach.copyToRealm")
        Realms.mailboxInfo.writeBlocking { mailboxInfosFromAPI.forEach { copyToRealm(it, MutableRealm.UpdatePolicy.ALL) } }


        Log.e("TOTO", "MailboxInfoController.getMailboxInfoByEmail")
        val mailbox = MailboxInfoController.getMailboxInfoByEmail("kevin.boulongne@ik.me") ?: return
        Log.e("TOTO", "AccountUtils.currentMailboxId = mailbox.mailboxId")
        AccountUtils.currentMailboxId = mailbox.mailboxId
        Log.e("TOTO", "ApiRepository.getFolders")
        val foldersFromAPI = ApiRepository.getFolders(mailbox).data ?: emptyList()
        Realms.mailboxContent.writeBlocking {
            Log.e("TOTO", "foldersFromAPI.forEach.copyToRealm")
            foldersFromAPI.forEach { copyToRealm(it, MutableRealm.UpdatePolicy.ALL) }
        }


        Log.e("TOTO", "MailboxContentController.getFolderByRole(FolderRole.INBOX)")
        val folder = MailboxContentController.getFolderByRole(Folder.FolderRole.INBOX) ?: return
        Log.e("TOTO", "ApiRepository.getThreads")
        val threadsFromApi = ApiRepository.getThreads(mailbox, folder).data?.threads ?: emptyList()
        Log.e("TOTO", "MailboxContentController.updateFolder.threads = threadsFromApi")
        MailboxContentController.updateFolder(folder.id) { it.threads = threadsFromApi.toRealmList() }


        // Update Messages
        // MailboxContentController.getFolders().forEach { folder -> folder.threads.forEach { updateMessages(mailbox, it) } }
        // Get current data
        // Get outdated data
        // Delete outdated data
        // Save new data


        Realms.mailboxContent.close()
        Realms.mailboxInfo.close()
        Realms.appSettings.close()
    }

    fun updateRealm() {
        // Update MailboxInfos
        updateMailboxInfos()

        // Update Folders
        val mailbox = MailboxInfoController.getMailboxInfoByEmail("kevin.boulongne@ik.me") ?: return
        AccountUtils.currentMailboxId = mailbox.mailboxId
        updateFolders(mailbox)

        // Update Threads
        val folder = MailboxContentController.getFolderByRole(Folder.FolderRole.INBOX) ?: return
        updateThreads(mailbox, folder)

        // Update Messages
        // MailboxContentController.getFolders().forEach { folder -> folder.threads.forEach { updateMessages(mailbox, it) } }
    }

    private fun updateMailboxInfos() {
        // Get current data
        Log.e("TOTO", "updateMailboxInfos: Get current data")
        val mailboxInfosFromRealm = MailboxInfoController.getMailboxInfos()
        val mailboxInfosFromAPI = ApiRepository.getMailboxes().data?.map { it.initLocalValues() } ?: emptyList()

        // Get outdated data
        Log.e("TOTO", "updateMailboxInfos: Get outdated data")
        val mailboxInfosDeletable = mailboxInfosFromRealm.subtract(mailboxInfosFromAPI)

        // Delete outdated data
        Log.e("TOTO", "updateMailboxInfos: Delete outdated data")
        mailboxInfosDeletable.forEach { MailboxInfoController.removeMailboxInfo(it.objectId) }

        // Save new data
        Log.e("TOTO", "updateMailboxInfos: Save new data")
        mailboxInfosFromAPI.forEach(MailboxInfoController::upsertMailboxInfo)
    }

//    fun testRealm1() {
//
//        // Get & save Test mailbox's infos
//        val testMailboxInfo = ApiRepository.getMailboxes()
//            .data
//            ?.firstOrNull { it.email == "kevin.boulongne@ik.me" }
//            ?.initLocalValues()
//            ?: return
//        MailboxInfoController.upsertMailboxInfo(testMailboxInfo)
//        Log.i("Realm", "Upserted MailboxInfo: ${testMailboxInfo.email}")
//
//        // Get Test mailbox
//        val testMailbox = MailboxInfoController.getMailboxInfos().firstOrNull() ?: return
//
//        // Select it
//        AccountUtils.currentMailboxId = testMailbox.mailboxId
//        Log.e("Realm", "Switched to current mailbox: ${testMailbox.email}")
//
//        // Get & save Inbox
//        ApiRepository.getFolders(testMailbox).data?.forEach { folder ->
//
//            ApiRepository.getThreads(testMailbox, folder).data?.threads
//                ?.also { threads -> folder.threads = threads.toRealmList() }
//                ?.forEach { thread ->
//                    // MailboxContentController.upsertThread(thread)
//                    // Log.i("Realm", "Upserted thread: ${thread.uid}")
//                }
//
//            MailboxContentController.upsertFolder(folder)
//            Log.e("Realm", "Upserted folder: ${folder.name}")
//        }
//    }

//    fun testRealm2() {
//
//
//        Log.e("Realm", "ApiRepository.getMailboxes")
//        val mailboxInfosFromAPI = ApiRepository.getMailboxes().data?.map { it.initLocalValues() } ?: emptyList()
//        Log.e("Realm", "mailboxInfosFromAPI.forEach.copyToRealm")
//        MailRealm.mailboxInfo.writeBlocking { mailboxInfosFromAPI.forEach { copyToRealm(it, UpdatePolicy.ALL) } }
//
//
//        Log.e("Realm", "MailboxInfoController.getMailboxInfoByEmail")
//        val mailbox = MailboxInfoController.getMailboxInfoByEmail("kevin.boulongne@ik.me") ?: return
//        Log.e("Realm", "AccountUtils.currentMailboxId = mailbox.mailboxId")
//        AccountUtils.currentMailboxId = mailbox.mailboxId
//        Log.e("Realm", "ApiRepository.getFolders")
//        val foldersFromAPI = ApiRepository.getFolders(mailbox).data ?: emptyList()
//        MailRealm.mailboxContent.writeBlocking {
//            Log.e("Realm", "foldersFromAPI.forEach.copyToRealm")
//            foldersFromAPI.forEach { copyToRealm(it, UpdatePolicy.ALL) }
//        }
//
//
//        Log.e("Realm", "MailboxContentController.getFolderByRole(FolderRole.INBOX)")
//        val folder = MailboxContentController.getFolderByRole(FolderRole.INBOX) ?: return
//        Log.e("Realm", "ApiRepository.getThreads")
//        val threadsFromApi = ApiRepository.getThreads(mailbox, folder).data?.threads ?: emptyList()
//        Log.e("Realm", "MailboxContentController.updateFolder.threads = threadsFromApi")
//        MailboxContentController.updateFolder(folder.id) { it.threads = threadsFromApi.toRealmList() }
//
//
//        // Update Messages
//        // MailboxContentController.getFolders().forEach { folder -> folder.threads.forEach { updateMessages(mailbox, it) } }
//        // Get current data
//        // Get outdated data
//        // Delete outdated data
//        // Save new data
//
//
//        MailRealm.mailboxContent.close()
//        MailRealm.mailboxInfo.close()
//        MailRealm.appSettings.close()
//    }

//    fun updateRealm() {
//        // Update MailboxInfos
//        updateMailboxInfos()
//
//        // Select Mailbox
//        MailboxInfoController.selectMailboxByEmail("kevin.boulongne@ik.me")
//        val mailbox = MailRealm.currentMailbox ?: return
//
//        // Update Folders
//        val folder = updateFolders(mailbox) ?: return
//
//        // Update Threads
//        // val folder = MailboxContentController.getFolderByRole(Folder.FolderRole.INBOX) ?: return
//        updateThreads(mailbox, folder)
//
//        // Update Messages
//        val thread = folder.threads.firstOrNull() ?: return
//        updateMessages(mailbox, thread)
//    }

//    private fun updateMailboxInfos() {
//        // Get current data
//        Log.d("Realm", "updateMailboxInfos: Get current data")
//        // val mailboxInfosFromRealm = MailboxInfoController.getMailboxInfos()
//        val mailboxInfosFromAPI = ApiRepository.getMailboxes().data?.map { it.initLocalValues() } ?: emptyList()
//
//        // Get outdated data
//        Log.d("Realm", "updateMailboxInfos: Get outdated data")
//        // val mailboxInfosDeletable = mailboxInfosFromRealm.subtract(mailboxInfosFromAPI)
//
//        // Delete outdated data
//        Log.e("Realm", "updateMailboxInfos: Delete outdated data")
//        // mailboxInfosDeletable.forEach { MailboxInfoController.removeMailboxInfo(it.objectId) }
//
//        // Save new data
//        Log.i("Realm", "updateMailboxInfos: Save new data")
//        mailboxInfosFromAPI.forEach(MailboxInfoController::upsertMailboxInfo)
//    }

//    private fun updateFolders(mailbox: Mailbox): Folder? {
//        // Get current data
//        Log.d("Realm", "updateFolders: Get current data")
//        // val foldersFromRealm = MailboxContentController.getFolders()
//        val foldersFromAPI = ApiRepository.getFolders(mailbox).data ?: emptyList()
//
//        // Get outdated data
//        Log.d("Realm", "updateFolders: Get outdated data")
//        // val foldersDeletable = foldersFromRealm.subtract(foldersFromAPI)
//        // val threadsDeletable = foldersDeletable.flatMap { it.threads }
//        // val foldersIds = foldersDeletable.map { it.id }
//        // val messagesDeletable = threadsDeletable.flatMap { thread -> thread.messages.filter { foldersIds.contains(it.folderId) } }
//
//        // Delete outdated data
//        Log.e("Realm", "updateFolders: Delete outdated data")
//        // messagesDeletable.forEach { MailboxContentController.removeMessage(it.uid) }
//        // threadsDeletable.forEach { MailboxContentController.removeThread(it.uid) }
//        // foldersDeletable.forEach { MailboxContentController.removeFolder(it.id) }
//
//        // Save new data
//        Log.i("Realm", "updateFolders: Save new data")
//        // foldersFromAPI.forEach(MailboxContentController::upsertFolder)
//        var inbox: Folder? = null
//        MailRealm.mailboxContent.writeBlocking {
//            foldersFromAPI.forEach {
//                if (it.getRole() == FolderRole.INBOX) inbox = it
//                copyToRealm(it)
//            }
//        }
//
//        return inbox
//    }

//    private fun updateThreads(mailbox: Mailbox, folder: Folder) {
//        // Get current data
//        Log.d("Realm", "updateThreads: Get current data")
//        // val threadsFromRealm = folder.threads
//        val threadsFromApi = ApiRepository.getThreads(mailbox, folder).data?.threads ?: emptyList()
//
//        // Get outdated data
//        Log.d("Realm", "updateThreads: Get outdated data")
//        // val threadsDeletable = threadsFromRealm.subtract(threadsFromApi)
//        // val messagesDeletable = threadsDeletable.flatMap { thread -> thread.messages.filter { it.folderId == folder.id } }
//
//        // Delete outdated data
//        Log.e("Realm", "updateThreads: Delete outdated data")
//        // messagesDeletable.forEach { MailboxContentController.removeMessage(it.uid) }
//        // threadsDeletable.forEach { MailboxContentController.removeThread(it.uid) }
//
//        // Save new data
//        Log.i("Realm", "updateThreads: Save new data")
//        // MailboxContentController.updateFolder(folder.id) { it.threads = threadsFromApi.toRealmList() }
//        folder.threads = threadsFromApi.toRealmList()
//        MailRealm.mailboxContent.writeBlocking { copyToRealm(folder, UpdatePolicy.ALL) }
//        Log.e("Realm", "updateThreads1: $folder")
//        Log.e("Realm", "updateThreads2: $threadsFromApi")
//        // Realms.mailboxContent.writeBlocking { folder.threads = threadsFromApi.toRealmList() }
//        // Realms.mailboxContent.writeBlocking { findLatest(folder)?.let { it.threads = threadsFromApi.toRealmList() } }
//    }

//    private fun updateMessages(mailbox: Mailbox, thread: Thread) {
//        // Get current data
//        Log.d("Realm", "updateMessages: Get current data")
//
//        // Get outdated data
//        Log.d("Realm", "updateMessages: Get outdated data")
//
//        // Delete outdated data
//        Log.e("Realm", "updateMessages: Delete outdated data")
//
//        // Save new data
//        Log.i("Realm", "updateMessages: Save new data")
//    }

//    fun lightlyPopulateRealm() {
//
//        // Save all mailboxes infos
//        ApiRepository.getMailboxes().data?.forEach {
//            val mailbox = it.initLocalValues()
//            MailboxInfoController.upsertMailboxInfo(mailbox)
//            Log.i("Realm", "Upserted MailboxInfo: ${mailbox.email}")
//        }
//
//        // Get the 1st mailbox
//        MailboxInfoController.getMailboxInfos().firstOrNull()?.let { mailbox ->
//
//            // Select it
//            AccountUtils.currentMailboxId = mailbox.mailboxId
//            Log.e("Realm", "Switched to current mailbox: ${mailbox.email}")
//
//            // Save all folders
//            ApiRepository.getFolders(mailbox).data?.forEach { folder ->
//
//                if (folder.getRole() == FolderRole.INBOX) {
//                    Log.e("Realm", "Switched to folder: ${folder.name}")
//
//                    // Get all threads
//                    val threads = ApiRepository.getThreads(mailbox, folder).data?.threads
//
//                    // Save all threads
//                    // threads?.forEach { thread ->
//                    //     MailboxContentController.upsertThread(thread)
//                    //     Log.i("Realm", "Upserted thread: ${thread.uid} - ${thread.date}")
//                    // }
//
//                    threads?.toRealmList()?.let { folder.threads = it }
//                    // threads?.toRealmList()?.let { newThreads ->
//                    //     MailboxContentController.updateFolder(folder.id) {
//                    //         it.threads = newThreads
//                    //     }
//                    // }
//
//                    // Get the 1st thread's messages
////                    threads?.firstOrNull()?.messages?.forEach { message ->
////
////                        // Get all completed messages
////                        ApiRepository.getMessage(message).data?.let { completedMessage ->
////
////                            // Save all completed messages
////                            completedMessage.fullyDownloaded = true
////                            MailboxContentController.upsertMessage(completedMessage)
////                            Log.d("Realm", "Upserted completed message: ${completedMessage.subject}")
////                        }
////                    }
//                }
//
//                MailboxContentController.upsertFolder(folder)
//                Log.i("Realm", "Upserted folder: ${folder.name}")
//            }
//
////            // Get the INBOX
////            MailboxContentController.getFolderByRole(FolderRole.INBOX)?.let { folder ->
////                Log.e("Realm", "Switched to folder: ${folder.name}")
////
////                // Get all threads
////                val threads = ApiRepository.getThreads(mailbox, folder).data?.threads
////
////                // Save all threads
////                // threads?.forEach { thread ->
////                //     MailboxContentController.upsertThread(thread)
////                //     Log.i("Realm", "Upserted thread: ${thread.uid} - ${thread.date}")
////                // }
////
////                threads?.toRealmList()?.let { newThreads ->
////                    MailboxContentController.updateFolder(folder.id) {
////                        it.threads = newThreads
////                    }
////                }
////
////                // Get the 1st thread's messages
////                threads?.firstOrNull()?.messages?.forEach { message ->
////
////                    // Get all completed messages
////                    ApiRepository.getMessage(message).data?.let { completedMessage ->
////
////                        // Save all completed messages
////                        completedMessage.fullyDownloaded = true
////                        MailboxContentController.upsertMessage(completedMessage)
////                        Log.d("Realm", "Upserted completed message: ${completedMessage.subject}")
////                    }
////                }
////            }
//        }
//    }

//    fun fullyPopulateRealm() {
//        ApiRepository.getMailboxes().data?.forEach {
//            val mailbox = it.initLocalValues()
//            MailboxInfoController.upsertMailboxInfo(mailbox)
//            Log.i("Realm", "Upserted MailboxInfo: ${mailbox.email}")
//
//            AccountUtils.currentMailboxId = mailbox.mailboxId
//            Log.e("Realm", "Switched to current mailbox: ${mailbox.email}")
//
//            ApiRepository.getFolders(mailbox).data?.forEach { folder ->
//
//                ApiRepository.getThreads(mailbox, folder).data?.threads
//                    ?.also { threads -> folder.threads = threads.toRealmList() }
//                    ?.forEach { thread ->
//                        MailboxContentController.upsertThread(thread)
//                        Log.i("Realm", "Upserted thread: ${thread.uid}")
//
//                        // thread.messages.forEach { message ->
//                        //     ApiRepository.getMessage(message).data?.let { completedMessage ->
//                        //         completedMessage.fullyDownloaded = true
//                        //         MailboxContentController.upsertMessage(completedMessage)
//                        //         Log.i("Realm", "Upserted completed message: ${completedMessage.subject}")
//                        //     }
//                        // }
//                    }
//
//                MailboxContentController.upsertFolder(folder)
//                Log.e("Realm", "Upserted folder: ${folder.name}")
//            }
//        }
//    }

//    fun callAllAPIs() {
//        val getUser = ApiRepository.getUser()
//        Log.i("API", "getUser: $getUser")
//
//        val getAddressBooks = ApiRepository.getAddressBooks()
//        Log.i("API", "getAddressBooks: $getAddressBooks")
//
//        val getContacts = ApiRepository.getContacts()
//        Log.i("API", "getContacts: $getContacts")
//
//        val getMailboxes = ApiRepository.getMailboxes()
//        Log.i("API", "getMailboxes: $getMailboxes")
//
//        val mailbox = getMailboxes.data?.firstOrNull()!!
//        val getFolders = ApiRepository.getFolders(mailbox)
//        Log.i("API", "getFolders: $getFolders")
//
//        val getQuotas = ApiRepository.getQuotas(mailbox)
//        Log.i("API", "getQuotas: $getQuotas")
//
//        val getSignature = ApiRepository.getSignatures(mailbox)
//        Log.i("API", "getSignature: $getSignature")
//
//        val inbox = getFolders.data?.find { it.getRole() == FolderRole.INBOX }!!
//        val getInboxThreads = ApiRepository.getThreads(mailbox, inbox)
//        Log.i("API", "getInboxThreads: $getInboxThreads")
//
//        val message = getInboxThreads.data?.threads?.first()?.messages?.first()!!
//        val getMessage = ApiRepository.getMessage(message)
//        Log.i("API", "getMessage: $getMessage")
//
//        // val moveMessageResponse = ApiRepository.moveMessage(mailbox, arrayListOf(message), inbox.id)
//        // Log.i("API", "moveMessageResponse: $moveMessageResponse")
//
//        // val starMessageResponse = ApiRepository.starMessage(true, mailbox, arrayListOf(message.uid))
//        // Log.i("API", "starMessageResponse: $starMessageResponse")
//
//        val drafts = getFolders.data?.find { it.getRole() == FolderRole.DRAFT }!!
//        val getDraftsThreads = ApiRepository.getThreads(mailbox, drafts)
//        Log.i("API", "getDraftsThreads: $getDraftsThreads")
//
//        val draftMessage = getDraftsThreads.data?.threads?.find { it.hasDrafts }?.messages?.find { it -> it.isDraft }
//        draftMessage?.let {
//            val draftUuid = draftMessage.draftResource.substringAfterLast('/')
//            val getDraftFromUuid = ApiRepository.getDraft(mailbox, draftUuid)
//            Log.i("API", "getDraftFromUuid: $getDraftFromUuid")
//            val getDraftFromMessage = ApiRepository.getDraft(draftMessage)
//            Log.i("API", "getDraftFromMessage: $getDraftFromMessage")
//
//            // val deleteDraft = ApiRepository.deleteDraft(mailbox, draftUuid)
//            // Log.i("API", "deleteDraft: $deleteDraft")
//        }
//    }
}
