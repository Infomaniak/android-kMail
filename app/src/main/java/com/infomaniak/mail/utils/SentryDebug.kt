/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.os.Bundle
import android.util.Log
import androidx.navigation.NavController
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.message.Message
import io.realm.kotlin.TypedRealm
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

object SentryDebug {

    private var previousDestinationName: String = ""

    fun addNavigationBreadcrumb(name: String, arguments: Bundle?) {

        // This function comes from `io.sentry.android.navigation.SentryNavigationListener`
        fun Bundle?.refined(): Map<String, Any?> = this?.let { args ->
            args.keySet()
                .filter { it != NavController.KEY_DEEP_LINK_INTENT } // there's a lot of unrelated stuff
                .associateWith { args[it] }
        } ?: emptyMap()

        val newDestinationName = name.substringAfter("${BuildConfig.APPLICATION_ID}:id/")

        addInfoBreadcrumb(
            category = "Navigation",
            data = mapOf(
                "1_from" to previousDestinationName,
                "2_to" to newDestinationName,
                "3_args" to arguments.refined(),
            ),
        )

        previousDestinationName = newDestinationName
    }

    fun addUrlBreadcrumb(url: String) {
        addInfoBreadcrumb("API", url)
    }

    fun addNotificationBreadcrumb(notification: String) {
        addInfoBreadcrumb("Notification", notification)
    }

    fun addThreadsAlgoBreadcrumb(message: String, data: Map<String, Any>) {
        addInfoBreadcrumb("ThreadsAlgo", message, data)
    }

    private fun addInfoBreadcrumb(category: String, message: String? = null, data: Map<String, Any>? = null) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category
            this.message = message
            data?.let { it.forEach { (key, value) -> this.data[key] = value } }
            this.level = SentryLevel.INFO
        })
    }

    fun sendAlreadyExistingMessage(folder: Folder, existingMessage: Message, newMessage: Message) {

        Sentry.withScope { scope ->
            scope.level = SentryLevel.ERROR
            scope.setExtra("folder.id", folder.id)
            scope.setExtra("existingMessage.folderId", existingMessage.folderId)
            scope.setExtra("newMessage.folderId", newMessage.folderId)
            scope.setExtra("uid", existingMessage.uid)
            scope.setExtra("existingMessage.messageId", "${existingMessage.messageId}")
            scope.setExtra("newMessage.messageId", "${newMessage.messageId}")
            if (existingMessage.messageId == newMessage.messageId) {
                Sentry.captureMessage("Same message id")
            } else {
                Sentry.captureMessage("Same message uid")
            }
        }

        Log.d("FolderSingle", "Message's Folder list has more than one element.")
        Log.d("FolderSingle", "existingMessage.uid: ${existingMessage.uid}")
        Log.d("FolderSingle", "folder.id: ${folder.id} | folderName: ${folder.name}")
        Log.d("FolderSingle", "existingMessage.folderId: ${existingMessage.folderId}")
        Log.d("FolderSingle", "newMessage.folderId: ${newMessage.folderId}")
        Log.d("FolderSingle", "existingMessage.messageId: ${existingMessage.messageId}")
        Log.d("FolderSingle", "newMessage.messageId: ${newMessage.messageId}")
    }

    fun sendMissingMessages(
        sentUids: List<String>,
        receivedMessages: List<Message>,
        folder: Folder,
        newCursor: String,
    ) {
        if (receivedMessages.count() != sentUids.count()) {
            val receivedUids = mutableSetOf<String>().apply {
                receivedMessages.forEach { add(it.shortUid) }
            }
            val missingUids = sentUids.filterNot(receivedUids::contains)
            if (missingUids.isNotEmpty()) {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    scope.setExtra("missingMessages", "${missingUids.map { it.toLongUid(folder.id) }}")
                    scope.setExtra("previousCursor", "${folder.cursor}")
                    scope.setExtra("newCursor", newCursor)
                    Sentry.captureMessage("We tried to download some Messages, but they were nowhere to be found.")
                }
            }
        }
    }

    fun sendOrphanMessages(previousCursor: String?, folder: Folder) {
        val orphanMessages = folder.messages.filter { it.isOrphan() }
        if (orphanMessages.isNotEmpty()) {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("orphanMessages", "${orphanMessages.map { it.uid }}")
                scope.setExtra("previousCursor", "$previousCursor")
                scope.setExtra("newCursor", "${folder.cursor}")
                Sentry.captureMessage("We found some orphan Messages.")
            }
        }
    }

    fun sendOrphanThreads(previousCursor: String?, folder: Folder, realm: TypedRealm) {
        val orphanThreads = ThreadController.getOrphanThreads(realm)
        if (orphanThreads.isNotEmpty()) {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("orphanThreads", "${orphanThreads.map { it.uid }}")
                scope.setExtra("previousCursor", "$previousCursor")
                scope.setExtra("newCursor", "${folder.cursor}")
                Sentry.captureMessage("We found some orphan Threads.")
            }
        }
    }

    fun sendOrphanDrafts(realm: TypedRealm) {
        val orphanDrafts = DraftController.getOrphanDrafts(realm)
        if (orphanDrafts.isNotEmpty()) {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra(
                    "orphanDrafts", "${
                        orphanDrafts.map {
                            if (it.messageUid == null) {
                                "${Draft::date.name}: [${it.date}] | ${Draft::subject.name}: [${it.subject}]"
                            } else {
                                "${Draft::messageUid.name}: ${it.messageUid}"
                            }
                        }
                    }"
                )
                Sentry.captureMessage("We found some orphan Drafts.")
            }
        }
    }

    fun sendOverScrolledMessage(clientWidth: Int, scrollWidth: Int, messageUid: String) {
        Sentry.withScope { scope ->
            scope.level = SentryLevel.ERROR
            scope.setTag("messageUid", messageUid)
            scope.setExtra("clientWidth", "$clientWidth")
            scope.setExtra("scrollWidth", "$scrollWidth")
            Sentry.captureMessage("When resizing the mail with js, after zooming, it can still scroll.")
        }
    }

    fun sendJavaScriptError(errorName: String, errorMessage: String, errorStack: String, messageUid: String) {
        Sentry.withScope { scope ->
            scope.level = SentryLevel.ERROR
            scope.setTag("messageUid", messageUid)
            scope.setExtra("errorName", errorName)
            scope.setExtra("errorMessage", errorMessage)
            scope.setExtra("errorStack", errorStack)
            Sentry.captureMessage("JavaScript returned an error when displaying an email.")
        }
    }
}
