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
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.MailboxController
import com.infomaniak.mail.data.cache.MailboxInfosController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.Realms
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

class MainActivity : AppCompatActivity() {

    private val testRealmViewModel: TestRealmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navController = setupNavController()

//        findViewById<Button>(R.id.startCalls).setOnClickListener {
//            lifecycleScope.launch(Dispatchers.IO) {
////                lightlyPopulateRealm()
//                // fullyPopulateRealm()
//        callAllAPIs()
//
//            }
//        }

        navController.addOnDestinationChangedListener { _, dest, args -> onDestinationChanged(dest, args) }
    }

    private fun onDestinationChanged(destination: NavDestination, navigationArgs: Bundle?) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "Navigation"
            message = "Accessed to destination : ${destination.displayName}"
            level = SentryLevel.INFO
        })

        // TODO Matomo
//        with(destination) {
//            application.trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
//        }
    }

    private fun setupNavController(): NavController {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment
        return navHostFragment.navController.apply {
            if (currentDestination == null) navigate(graph.startDestinationId)
        }
    }


    private fun lightlyPopulateRealm() {

        // Save all mailboxes infos
        ApiRepository.getMailboxes().data?.forEach {
            val mailbox = it.initLocalValues()
            MailboxInfoController.upsertMailboxInfo(mailbox)
            Log.i("Realm", "Upserted MailboxInfo: ${mailbox.email}")
        }

        // Get the 1st mailbox
        MailboxInfoController.getMailboxInfos().firstOrNull()?.let { mailbox ->

            // Select it
            AccountUtils.currentMailboxId = mailbox.mailboxId
            Log.e("Realm", "Switched to current mailbox: ${mailbox.email}")

            // Save all folders
            ApiRepository.getFolders(mailbox).data?.forEach { folder ->
                MailboxContentController.upsertFolder(folder)
                Log.i("Realm", "Upserted folder: ${folder.name}")
            }

            MailboxController.getFolderByRole(Folder.FolderRole.INBOX)?.let { folder ->
                Log.e("Realm", "Switched to folder: ${folder.name}")

                // Get all threads
                val threads = ApiRepository.getThreads(mailbox, folder).data?.threads

                // Save all threads
                threads?.forEach { thread ->
                    MailboxContentController.upsertThread(thread)
                    Log.i("Realm", "Upserted thread: ${thread.uid} - ${thread.date}")
                }

                // folder.threads.addAll(threads)

                // Get the 1st thread's messages
                threads?.firstOrNull()?.messages?.forEach { message ->

                    // Get all completed messages
                    ApiRepository.getMessage(message).data?.let { completedMessage ->

                        // Save all completed messages
                        completedMessage.fullyDownloaded = true
                        MailboxContentController.upsertMessage(completedMessage)
                        Log.d("Realm", "Upserted completed message: ${completedMessage.subject}")
                    }
                }
            }
        }
    }

    private fun fullyPopulateRealm() {
        ApiRepository.getMailboxes().data?.forEach {
            val mailbox = it.initLocalValues()
            MailboxInfoController.upsertMailboxInfo(mailbox)
            Log.i("Realm", "Upserted MailboxInfo: ${mailbox.email}")

            AccountUtils.currentMailboxId = mailbox.mailboxId
            Log.e("Realm", "Switched to current mailbox: ${mailbox.email}")

            ApiRepository.getFolders(mailbox).data?.forEach { folder ->
                folder.initLocalValues()
                MailboxContentController.upsertFolder(folder)
                Log.e("Realm", "Upserted folder: ${folder.name}")

                ApiRepository.getThreads(mailbox, folder).data?.threads?.forEach { thread ->
                    MailboxContentController.upsertThread(thread)
                    Log.i("Realm", "Upserted thread: ${thread.uid}")

                    // thread.messages.forEach { message ->
                    //     ApiRepository.getMessage(message).data?.let { completedMessage ->
                    //         completedMessage.fullyDownloaded = true
                    //         MailboxContentController.upsertMessage(completedMessage)
                    //         Log.i("Realm", "Upserted completed message: ${completedMessage.subject}")
                    //     }
                    // }
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

        val inbox = getFolders.data?.find { it.getRole() == Folder.FolderRole.INBOX }!!
        val getInboxThreads = ApiRepository.getThreads(mailbox, inbox, null)
        Log.i("API", "getInboxThreads: $getInboxThreads")

        val message = getInboxThreads.data!!.threads.first().messages.first()
        val getMessage = ApiRepository.getMessage(message)
        Log.i("API", "getMessage: $getMessage")

        // val moveMessageResponse = ApiRepository.moveMessage(mailbox, arrayListOf(message), inbox.id)
        // Log.i("API", "moveMessageResponse: $moveMessageResponse")

        // val starMessageResponse = ApiRepository.starMessage(true, mailbox, arrayListOf(message.uid))
        // Log.i("API", "starMessageResponse: $starMessageResponse")

        val drafts = getFolders.data?.find { it.getRole() == Folder.FolderRole.DRAFT }!!
        val getDraftsThreads = ApiRepository.getThreads(mailbox, drafts, null)
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
