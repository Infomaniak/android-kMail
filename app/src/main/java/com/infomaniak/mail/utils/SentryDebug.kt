/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
import androidx.navigation.NavController
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.extensions.toLongUid
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.migration.AutomaticSchemaMigration.MigrationContext
import io.realm.kotlin.query.RealmResults
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

object SentryDebug {

    //region Add Breadcrumb
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

    fun addUrlBreadcrumb(url: String, requestContextId: String) {
        addInfoBreadcrumb(
            category = "API",
            data = mapOf(
                "url" to url,
                "requestContextId" to requestContextId,
            ),
        )
    }

    fun addNotificationBreadcrumb(notification: String) {
        addInfoBreadcrumb("Notification", notification)
    }

    fun addThreadsAlgoBreadcrumb(message: String, data: Map<String, Any>) {
        addInfoBreadcrumb("ThreadsAlgo", message, data)
    }

    fun addCursorBreadcrumb(message: String, folder: Folder, cursor: String?) {
        addInfoBreadcrumb(
            category = "Cursor",
            message = message,
            data = mapOf(
                "folderName" to folder.name,
                "previousFolderCursor" to "${folder.cursor}",
                "newFolderCursor" to "$cursor",
            ),
        )
    }

    fun addMigrationBreadcrumb(migrationContext: MigrationContext) = with(migrationContext) {
        addInfoBreadcrumb(
            category = "Migration",
            data = mapOf(
                "realmName" to oldRealm.configuration.name,
                "oldVersion" to oldRealm.version().version,
                "newVersion" to newRealm.version().version,
            ),
        )
    }

    private fun addInfoBreadcrumb(category: String, message: String? = null, data: Map<String, Any>? = null) {
        Breadcrumb().apply {
            this.category = category
            this.message = message
            data?.let { it.forEach { (key, value) -> this.data[key] = value } }
            this.level = SentryLevel.INFO
        }.also(Sentry::addBreadcrumb)
    }
    //endregion

    //region Send Sentry
    // TODO: Added the 04/09/23. It's not supposed to be possible, but we never know…
    //  If this doesn't trigger after a certain amount of time, you can remove it.
    //
    //  Also added in ThreadListAdapter the 31/05/24.
    fun sendEmptyThread(thread: Thread, message: String) = with(thread) {
        Sentry.withScope { scope ->
            scope.setExtra("currentUserId", "${AccountUtils.currentUserId}")
            scope.setExtra("currentMailboxEmail", AccountUtils.currentMailboxEmail.toString())
            scope.setExtra("folderId", folderId)
            scope.setExtra("folder.id", folder.id)
            scope.setExtra("folder.role", folder.role?.name.toString())
            scope.setExtra("uid", uid)
            scope.setExtra("messages.count", "${messages.count()}")
            scope.setExtra("duplicates.count", "${duplicates.count()}")
            scope.setExtra("isFromSearch", "$isFromSearch")
            scope.setExtra("hasDrafts", "$hasDrafts")
            Sentry.captureMessage(message, SentryLevel.ERROR)
        }
    }

    fun sendFailedNotification(
        reason: String,
        sentryLevel: SentryLevel,
        userId: Int? = null,
        mailboxId: Int? = null,
        messageUid: String? = null,
        mailbox: Mailbox? = null,
        throwable: Throwable? = null,
    ) {
        Sentry.withScope { scope ->

            scope.level = sentryLevel

            scope.setTag("reason", reason)
            scope.setExtra("userId", "${userId?.toString()}")
            scope.setExtra("currentUserId", "[${AccountUtils.currentUserId}]")
            scope.setExtra("mailboxId", "${mailboxId?.toString()}")
            scope.setExtra("mailbox.email", "[${mailbox?.email}]")
            scope.setExtra("currentMailboxEmail", "[${AccountUtils.currentMailboxEmail}]")
            scope.setExtra("messageUid", "$messageUid")

            val message = "We received a Notification, but we failed to show it"

            throwable?.let {
                scope.setExtra("message", message)
                Sentry.captureException(it)
            } ?: run {
                Sentry.captureMessage(message)
            }
        }
    }

    fun sendMissingMessages(
        sentUids: List<Int>,
        receivedMessages: List<Message>,
        folder: Folder,
        newCursor: String,
    ) {
        if (receivedMessages.count() != sentUids.count()) {
            val receivedUids = mutableSetOf<Int>().apply {
                receivedMessages.forEach { add(it.shortUid) }
            }
            val missingUids = sentUids.filterNot(receivedUids::contains)
            if (missingUids.isNotEmpty()) {
                Sentry.withScope { scope ->
                    scope.setExtra("1. newCursor", newCursor)
                    scope.setExtra("2. previousCursor", "${folder.cursor}")
                    scope.setExtra("3. input", "${sentUids.map { it }}")
                    scope.setExtra("4. output", "${receivedMessages.map { it.shortUid }}")
                    scope.setExtra("5. missing", "${missingUids.map { it.toString().toLongUid(folder.id) }}")
                    Sentry.captureMessage(
                        "We tried to download some Messages, but they were nowhere to be found.",
                        SentryLevel.ERROR,
                    )
                }
            }
        }
    }

    fun sendOrphanMessages(previousCursor: String?, folder: Folder): List<Message> {
        val orphanMessages = folder.messages.filter { it.isOrphan() }
        if (orphanMessages.isNotEmpty()) {
            Sentry.withScope { scope ->
                scope.setExtra("orphanMessages", "${orphanMessages.map { it.uid }}")
                scope.setExtra("number of Messages", "${orphanMessages.count()}")
                scope.setExtra("previousCursor", "$previousCursor")
                scope.setExtra("newCursor", "${folder.cursor}")
                scope.setExtra("folder", folder.displayForSentry())
                Sentry.captureMessage("We found some orphan Messages.", SentryLevel.ERROR)
            }
        }
        return orphanMessages
    }

    fun sendOrphanThreads(previousCursor: String?, folder: Folder, realm: TypedRealm): RealmResults<Thread> {
        val orphanThreads = ThreadController.getOrphanThreads(realm)
        if (orphanThreads.isNotEmpty()) {
            Sentry.withScope { scope ->
                scope.setExtra("orphanThreads", "${orphanThreads.map { it.uid }}")
                scope.setExtra("number of Threads", "${orphanThreads.count()}")
                scope.setExtra("number of Messages", "${orphanThreads.map { it.messages.count() }}")
                scope.setExtra("previousCursor", "$previousCursor")
                scope.setExtra("newCursor", "${folder.cursor}")
                scope.setExtra("folder", folder.displayForSentry())
                Sentry.captureMessage("We found some orphan Threads.", SentryLevel.ERROR)
            }
        }
        return orphanThreads
    }

    fun sendOrphanDrafts(realm: TypedRealm) {
        val orphanDrafts = DraftController.getOrphanDrafts(realm)
        if (orphanDrafts.isNotEmpty()) {
            Sentry.withScope { scope ->
                scope.setExtra(
                    "orphanDrafts",
                    orphanDrafts.joinToString {
                        if (it.messageUid == null) {
                            "${Draft::localUuid.name}: [${it.localUuid}]"
                        } else {
                            "${Draft::messageUid.name}: ${it.messageUid}"
                        }
                    },
                )
                Sentry.captureMessage("We found some orphan Drafts.", SentryLevel.ERROR)
            }
        }
    }

    fun sendOverScrolledMessage(clientWidth: Int, scrollWidth: Int, messageUid: String) {
        Sentry.withScope { scope ->
            scope.setTag("messageUid", messageUid)
            scope.setExtra("clientWidth", "$clientWidth")
            scope.setExtra("scrollWidth", "$scrollWidth")
            Sentry.captureMessage("When resizing the mail with js, after zooming, it can still scroll.", SentryLevel.ERROR)
        }
    }

    fun sendJavaScriptError(errorName: String, errorMessage: String, errorStack: String, messageUid: String) {
        Sentry.withScope { scope ->
            scope.setTag("messageUid", messageUid)
            scope.setExtra("errorName", errorName)
            scope.setExtra("errorMessage", errorMessage)
            scope.setExtra("errorStack", errorStack)
            Sentry.captureMessage("JavaScript returned an error when displaying an email.", SentryLevel.ERROR)
        }
    }

    fun sendSubBodiesTrigger(messageUid: String) {
        Sentry.withScope { scope ->
            scope.setExtra("email", "${AccountUtils.currentMailboxEmail}")
            scope.setExtra("messageUid", messageUid)
            Sentry.captureMessage("Received an email with SubBodies!!", SentryLevel.INFO)
        }
    }

    fun sendCredentialsIssue(infomaniakLogin: String?, infomaniakPassword: String) {
        Sentry.withScope { scope ->
            scope.setExtra("email", "${AccountUtils.currentUser?.email}")
            val loginStatus = when {
                infomaniakLogin == null -> "is null"
                infomaniakLogin.isEmpty() -> "is empty"
                infomaniakLogin.isBlank() -> "is blank"
                else -> "is ok"
            }
            scope.setExtra("infomaniakLogin status", loginStatus)
            val passwordStatus = when {
                infomaniakPassword.isEmpty() -> "is empty"
                infomaniakPassword.isBlank() -> "is blank"
                else -> "is ok"
            }
            scope.setExtra("infomaniakPassword status", passwordStatus)
            Sentry.captureMessage("Credentials issue when trying to auto-sync user", SentryLevel.ERROR)
        }
    }
    //endregion

    //region Utils
    fun Folder.displayForSentry() = role?.name ?: id
    //endregion
}
