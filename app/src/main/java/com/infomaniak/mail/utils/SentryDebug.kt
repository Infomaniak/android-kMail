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
import android.util.Log
import androidx.navigation.NavController
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
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

    fun addDraftsBreadcrumbs(drafts: List<Draft>, step: String) {
        drafts.forEach { addDraftBreadcrumbs(it, step) }
    }

    fun addDraftBreadcrumbs(draft: Draft, step: String) = with(draft) {

        var count = 1
        val data = mutableMapOf<String, Any>()

        fun Int.countPadding(): String = toString().padStart(length = 2, '0')

        fun addData(category: String, key: String = "", value: String) {
            data[count.countPadding() + "." + (category + key).padStart(length = 20)] = value
            count++
        }

        fun addDraftData(key: String, value: String) {
            addData(category = "draft", key = " - $key", value = value)
        }

        fun addAttachmentsData(key: String, value: String) {
            addData(category = "attachments", key = " - $key", value = value)
        }

        fun addAttachmentData(index: Int, value: String) {
            addData(category = "attachment #${(index + 1).countPadding()}", value = value)
        }

        addData(category = "step", value = step)
        addData(category = "email", value = AccountUtils.currentMailboxEmail.toString())

        addDraftData(key = "localUuid", value = localUuid)
        addDraftData(key = "remoteUuid", value = remoteUuid.toString())
        addDraftData(key = "messageUid", value = messageUid.toString())
        addDraftData(key = "action", value = action?.name.toString())

        val draftMode = when {
            inReplyToUid != null -> "REPLY or REPLY_ALL"
            forwardedUid != null -> "FORWARD"
            else -> "NEW_MAIL"
        }
        addDraftData(key = "mode", value = draftMode)

        addAttachmentsData(key = "count", value = attachments.count().toString())

        attachments.forEachIndexed { index, it ->
            addAttachmentData(
                index = index,
                value = "localUuid: ${it.localUuid} | uuid: ${it.uuid} | uploadLocalUri: ${it.uploadLocalUri}",
            )
            addAttachmentData(
                index = index,
                value = "uploadStatus: ${it.uploadStatus?.name} | size: ${it.size}",
            )
        }

        val category = "Attachments_Situation"
        Log.i(category, data.map { "${it.key}: ${it.value}" }.joinToString(separator = "\n"))
        addInfoBreadcrumb(category = category, data = data)
    }

    private fun addInfoBreadcrumb(category: String, message: String? = null, data: Map<String, Any>? = null) {
        Breadcrumb().apply {
            this.category = category
            this.message = message
            data?.let { it.forEach { (key, value) -> this.setData(key, value) } }
            this.level = SentryLevel.INFO
        }.also(Sentry::addBreadcrumb)
    }
    //endregion

    //region Send Sentry
    // TODO: Added the 04/09/23. It's not supposed to be possible, but we never know…
    //  If this doesn't trigger after a certain amount of time, you can remove it.
    //
    //  Also added in ThreadListAdapter & ThreadController the 04/06/24.
    fun sendEmptyThread(thread: Thread, message: String, realm: TypedRealm) = with(thread) {

        val messageFromThreadUid = MessageController.getMessage(uid, realm)

        Sentry.captureMessage(message, SentryLevel.ERROR) { scope ->
            scope.setExtra("01. currentUserId", "${AccountUtils.currentUserId}")
            scope.setExtra("02. currentMailboxEmail", AccountUtils.currentMailboxEmail.toString())
            scope.setExtra("03. folderId", folderId)
            scope.setExtra("04. folder.id", folder.id)
            scope.setExtra("05. folder.role", folder.role?.name.toString())
            scope.setExtra("06. thread.uid", uid)
            scope.setExtra("07. thread.messages.count", "${messages.count()}")
            scope.setExtra("08. thread.duplicates.count", "${duplicates.count()}")
            scope.setExtra("09. thread.isFromSearch", "$isFromSearch")
            scope.setExtra("10. thread.hasDrafts", "$hasDrafts")
            scope.setExtra("11. message.uid", "${messageFromThreadUid?.uid}")
            scope.setExtra("12. message.folderId", "${messageFromThreadUid?.folderId}")
            scope.setExtra("13. message.folder.id", "${messageFromThreadUid?.folder?.id}")
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
        Sentry.captureMessage("Failed Notif : $reason", sentryLevel) { scope ->
            scope.setExtra("userId", "${userId?.toString()}")
            scope.setExtra("currentUserId", "[${AccountUtils.currentUserId}]")
            scope.setExtra("mailboxId", "${mailboxId?.toString()}")
            scope.setExtra("mailbox.email", "[${mailbox?.email}]")
            scope.setExtra("currentMailboxEmail", "[${AccountUtils.currentMailboxEmail}]")
            scope.setExtra("messageUid", "$messageUid")
            throwable?.let { scope.setExtra("throwable", it.stackTraceToString()) }
        }
    }

    fun sendMessageInWrongFolder(remoteMessage: Message, folder: Folder, realm: TypedRealm) {
        val localMessage = MessageController.getMessage(remoteMessage.uid, realm)
        Sentry.captureMessage(
            "Message is in wrong Folder (related to 'Message has multiple parent folders')",
            SentryLevel.ERROR,
        ) { scope ->
            scope.setExtra("localMessage folderId", "${localMessage?.folderId}")
            scope.setExtra("remoteMessage folderId", remoteMessage.folderId)
            scope.setExtra("remoteMessageUid", remoteMessage.uid)
            scope.setExtra("folderRole", "${folder.role?.name}")
            scope.setExtra("folderId", folder.id)
        }
    }

    fun sendOrphanMessages(previousCursor: String?, folder: Folder, realm: TypedRealm): List<Message> {
        val orphanMessages = folder.messages(realm).filter { it.isOrphan() }
        if (orphanMessages.isNotEmpty()) {
            Sentry.captureMessage("We found some orphan Messages.", SentryLevel.ERROR) { scope ->
                scope.setExtra("orphanMessages", "${orphanMessages.map { it.uid }}")
                scope.setExtra("number of Messages", "${orphanMessages.count()}")
                scope.setExtra("previousCursor", "$previousCursor")
                scope.setExtra("newCursor", "${folder.cursor}")
                scope.setExtra("folder", folder.displayForSentry())
            }
        }
        return orphanMessages
    }

    fun sendOrphanThreads(previousCursor: String?, folder: Folder, realm: TypedRealm): RealmResults<Thread> {
        val orphanThreads = ThreadController.getOrphanThreads(realm)
        if (orphanThreads.isNotEmpty()) {
            Sentry.captureMessage("We found some orphan Threads.", SentryLevel.ERROR) { scope ->
                scope.setExtra("orphanThreads", "${orphanThreads.map { it.uid }}")
                scope.setExtra("number of Threads", "${orphanThreads.count()}")
                scope.setExtra("number of Messages", "${orphanThreads.map { it.messages.count() }}")
                scope.setExtra("previousCursor", "$previousCursor")
                scope.setExtra("newCursor", "${folder.cursor}")
                scope.setExtra("folder", folder.displayForSentry())
            }
        }
        return orphanThreads
    }

    fun sendOrphanDrafts(orphans: List<Draft>) {
        if (orphans.isNotEmpty()) {
            Sentry.captureMessage("We found some orphan Drafts.", SentryLevel.ERROR) { scope ->
                scope.setExtra(
                    "orphanDrafts",
                    orphans.joinToString {
                        if (it.messageUid == null) {
                            "${Draft::localUuid.name}: [${it.localUuid}]"
                        } else {
                            "${Draft::messageUid.name}: ${it.messageUid}"
                        }
                    },
                )
            }
        }
    }

    fun sendOverScrolledMessage(clientWidth: Int, scrollWidth: Int, messageUid: String) {
        Sentry.captureMessage("When resizing the mail with js, after zooming, it can still scroll.", SentryLevel.ERROR) { scope ->
            scope.setTag("messageUid", messageUid)
            scope.setExtra("clientWidth", "$clientWidth")
            scope.setExtra("scrollWidth", "$scrollWidth")
        }
    }

    fun sendJavaScriptError(errorName: String, errorMessage: String, errorStack: String, messageUid: String) {
        Sentry.captureMessage("JavaScript returned an error when displaying an email.", SentryLevel.ERROR) { scope ->
            scope.setTag("messageUid", messageUid)
            scope.setExtra("errorName", errorName)
            scope.setExtra("errorMessage", errorMessage)
            scope.setExtra("errorStack", errorStack)
        }
    }

    fun sendSubBodiesTrigger(messageUid: String) {
        Sentry.captureMessage("Received an email with SubBodies!!") { scope ->
            scope.setExtra("email", "${AccountUtils.currentMailboxEmail}")
            scope.setExtra("messageUid", messageUid)
        }
    }

    fun sendCredentialsIssue(infomaniakLogin: String?, infomaniakPassword: String) {
        Sentry.captureMessage("Credentials issue when trying to auto-sync user", SentryLevel.ERROR) { scope ->
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
        }
    }

    fun sendWebViewVersionName(webViewPackageName: String?, webViewVersionName: String?, majorVersion: Int) {
        Sentry.captureMessage(
            "WebView version name might be null on some devices. Checking that the version name is ok.",
        ) { scope ->
            scope.setTag("webViewPackageName", "$webViewPackageName")
            scope.setTag("webViewVersionName", "$webViewVersionName")
            scope.setTag("majorVersion", "$majorVersion")
        }
    }
    //endregion

    //region Utils
    fun Folder.displayForSentry() = role?.name ?: id
    //endregion
}
