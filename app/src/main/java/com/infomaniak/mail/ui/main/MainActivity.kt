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

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailboxController
import com.infomaniak.mail.data.cache.MailboxInfosController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.Realms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.startCalls).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                testRealm()
                // lightlyPopulateRealm()
                // fullyPopulateRealm()
                // callAllAPIs()
            }
        }
    }

    private fun testRealm() {

        // Get & save Test mailbox's infos
        val testMailboxInfos = ApiRepository.getMailboxes()
            .data
            ?.firstOrNull { it.email == "kevin.boulongne@ik.me" }
            ?.initLocalValues()
            ?: return
        MailboxInfosController.upsertMailboxInfos(testMailboxInfos)
        Log.i("Realm", "Upserted MailboxInfos: ${testMailboxInfos.email}")

        // Get Test mailbox
        val testMailbox = MailboxInfosController.getMailboxInfos().firstOrNull() ?: return

        // Select it
        AccountUtils.currentMailboxId = testMailbox.mailboxId
        Realms.selectCurrentMailbox()
        Log.e("Realm", "Switched to current mailbox: ${testMailbox.email}")

        // Get & save Inbox
        val folder = ApiRepository.getFolders(testMailbox).data?.firstOrNull { it.getRole() == FolderRole.INBOX } ?: return
        MailboxController.upsertFolder(folder)
        Log.i("Realm", "Upserted folder: ${folder.name}")

        // Get & save last thread
        val lastThread = ApiRepository.getThreads(testMailbox, folder).data?.threads?.firstOrNull()?.initLocalValues() ?: return
        MailboxController.upsertThread(lastThread)
        Log.i("Realm", "Upserted thread: ${lastThread.uid} - ${lastThread.date}")

        // folder.threads.addAll(threads)

        // Get last thread's messages
//        lastThread.messages.forEach { message ->
//
//            // Get all completed messages
//            ApiRepository.getMessage(message).data?.let { completedMessage ->
//
//                // Save all completed messages
//                completedMessage.fullyDownloaded = true
//                MailboxController.upsertMessage(completedMessage)
//                Log.d("Realm", "Upserted completed message: ${completedMessage.subject}")
//            }
//        }
    }

    private fun lightlyPopulateRealm() {

        // Save all mailboxes infos
        ApiRepository.getMailboxes().data?.forEach {
            val mailbox = it.initLocalValues()
            MailboxInfosController.upsertMailboxInfos(mailbox)
            Log.i("Realm", "Upserted MailboxInfos: ${mailbox.email}")
        }

        // Get the 1st mailbox
        MailboxInfosController.getMailboxInfos().firstOrNull()?.let { mailbox ->

            // Select it
            AccountUtils.currentMailboxId = mailbox.mailboxId
            Realms.selectCurrentMailbox()
            Log.e("Realm", "Switched to current mailbox: ${mailbox.email}")

            // Save all folders
            ApiRepository.getFolders(mailbox).data?.forEach { folder ->
                MailboxController.upsertFolder(folder)
                Log.i("Realm", "Upserted folder: ${folder.name}")
            }

            // Get the INBOX
            MailboxController.getFolderByRole(FolderRole.INBOX)?.let { folder ->
                Log.e("Realm", "Switched to folder: ${folder.name}")

                // Get all threads
                val threads = ApiRepository.getThreads(mailbox, folder).data?.threads

                // Save all threads
                threads?.forEach { thread ->
                    MailboxController.upsertThread(thread)
                    Log.i("Realm", "Upserted thread: ${thread.uid} - ${thread.date}")
                }

                // folder.threads.addAll(threads)

                // Get the 1st thread's messages
                threads?.firstOrNull()?.messages?.forEach { message ->

                    // Get all completed messages
                    ApiRepository.getMessage(message).data?.let { completedMessage ->

                        // Save all completed messages
                        completedMessage.fullyDownloaded = true
                        MailboxController.upsertMessage(completedMessage)
                        Log.d("Realm", "Upserted completed message: ${completedMessage.subject}")
                    }
                }
            }
        }
    }

    private fun fullyPopulateRealm() {
        ApiRepository.getMailboxes().data?.forEach {
            val mailbox = it.initLocalValues()
            MailboxInfosController.upsertMailboxInfos(mailbox)
            Log.i("Realm", "Upserted MailboxInfos: ${mailbox.email}")

            AccountUtils.currentMailboxId = mailbox.mailboxId
            Realms.selectCurrentMailbox()
            Log.e("Realm", "Switched to current mailbox: ${mailbox.email}")

            ApiRepository.getFolders(mailbox).data?.forEach { folder ->
                MailboxController.upsertFolder(folder)
                Log.e("Realm", "Upserted folder: ${folder.name}")

                ApiRepository.getThreads(mailbox, folder).data?.threads?.forEach { thread ->
                    MailboxController.upsertThread(thread)
                    Log.i("Realm", "Upserted thread: ${thread.uid}")

                    thread.messages.forEach { message ->
                        ApiRepository.getMessage(message).data?.let { completedMessage ->
                            completedMessage.fullyDownloaded = true
                            MailboxController.upsertMessage(completedMessage)
                            Log.i("Realm", "Upserted completed message: ${completedMessage.subject}")
                        }
                    }
                }
            }
        }
    }

    private fun callAllAPIs() {
        val getUser = ApiRepository.getUser()
        Log.i("API", "getUser: $getUser")

        val getAddressBooks = ApiRepository.getAddressBooks()
        Log.i("API", "getAddressBooks: $getAddressBooks")

        val getContacts = ApiRepository.getContacts()
        Log.i("API", "getContacts: $getContacts")

        val getMailboxes = ApiRepository.getMailboxes()
        Log.i("API", "getMailboxes: $getMailboxes")

        val mailbox = getMailboxes.data?.firstOrNull()!!
        val getFolders = ApiRepository.getFolders(mailbox)
        Log.i("API", "getFolders: $getFolders")

        val getQuotas = ApiRepository.getQuotas(mailbox)
        Log.i("API", "getQuotas: $getQuotas")

        val getSignature = ApiRepository.getSignatures(mailbox)
        Log.i("API", "getSignature: $getSignature")

        val inbox = getFolders.data?.find { it.getRole() == FolderRole.INBOX }!!
        val getInboxThreads = ApiRepository.getThreads(mailbox, inbox)
        Log.i("API", "getInboxThreads: $getInboxThreads")

        val message = getInboxThreads.data!!.threads.first().messages.first()
        val getMessage = ApiRepository.getMessage(message)
        Log.i("API", "getMessage: $getMessage")

        // val moveMessageResponse = ApiRepository.moveMessage(mailbox, arrayListOf(message), inbox.id)
        // Log.i("API", "moveMessageResponse: $moveMessageResponse")

        // val starMessageResponse = ApiRepository.starMessage(true, mailbox, arrayListOf(message.uid))
        // Log.i("API", "starMessageResponse: $starMessageResponse")

        val drafts = getFolders.data?.find { it.getRole() == FolderRole.DRAFT }!!
        val getDraftsThreads = ApiRepository.getThreads(mailbox, drafts)
        Log.i("API", "getDraftsThreads: $getDraftsThreads")

        val draftMessage = getDraftsThreads.data?.threads?.find { it.hasDrafts }?.messages?.find { it -> it.isDraft }
        draftMessage?.let {
            val draftUuid = draftMessage.draftResource.substringAfterLast('/')
            val getDraftFromUuid = ApiRepository.getDraft(mailbox, draftUuid)
            Log.i("API", "getDraftFromUuid: $getDraftFromUuid")
            val getDraftFromMessage = ApiRepository.getDraft(draftMessage)
            Log.i("API", "getDraftFromMessage: $getDraftFromMessage")

            // val deleteDraft = ApiRepository.deleteDraft(mailbox, draftUuid)
            // Log.i("API", "deleteDraft: $deleteDraft")
        }
    }
}
