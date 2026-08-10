/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.extensions

import android.content.Context
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.isUnsnoozed
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.main.folder.ThreadListDateDisplay
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.FeatureAvailability
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.types.RealmList
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlin.random.Random

// TODO: Remove this `runCatching / getOrElse` when the Threads parental issues are fixed
val Thread.folder
    get() = runCatching {
        // The only situation were we can have more than 1 parent folder is if the parent folders
        // are 2 with this exact situation : 1x any real folder and 1x the Search folder.
        if (_folders.count() == 2) {
            _folders.single { it.id != FolderController.SEARCH_FOLDER_ID }
        } else {
            _folders.single()
        }
    }.getOrElse { exception ->
        val reason = if (_folders.isEmpty()) {
            "no parents" // Thread has 0 parent folders
        } else {
            val allFoldersAreSearch = _folders.all { it.id == FolderController.SEARCH_FOLDER_ID }
            val allFoldersAreTheSame = _folders.all { it.id == _folders.firstOrNull()?.id }
            when {
                allFoldersAreSearch -> "multiple SEARCH folder" // Thread has multiple times the Search folder as parent
                allFoldersAreTheSame -> "multiple same parent" // Thread has multiple times the same parent folder
                else -> "multiple parents" // Thread has multiple parent folders
            }
        }
        // TODO: As of 20/05/2025, this event is taking the Sentry server down because it's emitting way too often.
        //  So we temporarily set it to emit in only 2% of cases, while we are working on a fix.
        if (Random.nextInt(0, 100) < 2) {
            Sentry.captureMessage(
                "Thread doesn't have a unique parent Folder, it should not be possible",
                SentryLevel.ERROR,
            ) { scope ->
                scope.setTag("issueType", reason)
                scope.setTag("foldersId", _folders.joinToString { it.id })
                scope.setTag("foldersCount", "${_folders.count()}")
                scope.setExtra("folders_", "${_folders.map { "role:[${it.role?.name}] (id:[${it.id}])" }}")
                scope.setExtra("foldersCount_", "${_folders.count()}")
                scope.setExtra("threadUid", uid)
                scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                scope.setExtra("exception", exception.message.toString())
            }
        }

        return@getOrElse _folders.firstOrNull { uid.contains(it.id) } ?: _folders.first()
    }

fun Thread.getDisplayedMessages(featureFlags: Mailbox.FeatureFlagSet?, localSettings: LocalSettings): RealmList<Message> {
    return if (FeatureAvailability.isReactionsAvailable(featureFlags, localSettings)) messagesWithContent else messages
}

fun Thread.addMessageWithConditions(newMessage: Message, realm: TypedRealm) {

    val shouldAddMessage = when (FolderController.getFolderBlocking(folderId, realm)?.role) {
        FolderRole.DRAFT -> newMessage.isDraft // In Draft folder: only add draft Messages.
        FolderRole.SCHEDULED_DRAFTS -> newMessage.isScheduledDraft // In ScheduledDrafts folder: only add scheduled Messages.
        FolderRole.TRASH -> newMessage.isTrashed // In Trash folder: only add deleted Messages.
        else -> !newMessage.isTrashed // In other folders: only add non-deleted Messages.
    }

    if (shouldAddMessage) {
        val twinMessage = messages.firstOrNull { it.messageId == newMessage.messageId }
        if (twinMessage == null) {
            messages.add(newMessage)
        } else {
            addDuplicatedMessage(twinMessage, newMessage)
        }
    }
}

private fun Thread.addDuplicatedMessage(twinMessage: Message, newMessage: Message) {
    val isTwinTheRealMessage = twinMessage.folderId == folderId
    if (isTwinTheRealMessage) {
        duplicates.add(newMessage)
    } else {
        messages.remove(twinMessage)
        duplicates.add(twinMessage)
        messages.add(newMessage)
    }
}

fun Thread.containsOnlyScheduledDrafts(featureFlags: Mailbox.FeatureFlagSet?, localSettings: LocalSettings): Boolean {
    return getDisplayedMessages(featureFlags, localSettings).count() == numberOfScheduledDrafts
}

fun Thread.computeThreadListDateDisplay(featureFlags: Mailbox.FeatureFlagSet?, localSettings: LocalSettings) = when {
    containsOnlyScheduledDrafts(featureFlags, localSettings) -> ThreadListDateDisplay.Scheduled
    isSnoozed() -> ThreadListDateDisplay.Snoozed
    isUnsnoozed() -> ThreadListDateDisplay.Unsnoozed
    else -> ThreadListDateDisplay.Default
}

fun Thread.computePreview(context: Context): String {
    val message = if (folder.role == FolderRole.SENT) {
        messages.lastOrNull { it.folderId == folderId } ?: messages.last()
    } else {
        messages.last()
    }

    return message.getFormattedPreview(context).content
}

fun Thread.computeAvatarRecipient(): Pair<Recipient?, Bimi?> = runCatching {
    val message = messages.lastOrNull {
        it.folder.role != FolderRole.SENT &&
                it.folder.role != FolderRole.DRAFT &&
                it.folder.role != FolderRole.SCHEDULED_DRAFTS
    } ?: messages.last()

    val recipients = when (message.folder.role) {
        FolderRole.SENT, FolderRole.DRAFT, FolderRole.SCHEDULED_DRAFTS -> message.to
        else -> message.from
    }

    return@runCatching recipients.firstOrNull() to message.bimi

}.getOrElse { throwable ->
    Sentry.captureException(throwable) { scope ->
        scope.setExtra("thread.folder.role", folder.role?.name.toString())
        scope.setExtra("thread.folder.id", folder.id)
        scope.setExtra("thread.folderId", folderId)
        scope.setExtra("thread.uid", uid)
        scope.setExtra("thread.messages.count", "${messages.count()}")
        scope.setExtra("thread.duplicates.count", "${duplicates.count()}")
        scope.setExtra("thread.isFromSearch", "$isFromSearch")
        scope.setExtra("thread.hasDrafts", "$hasDrafts")
    }

    return@getOrElse null to null
}

fun Thread.computeDisplayedRecipients(): RealmList<Recipient> = when (folder.role) {
    FolderRole.SENT, FolderRole.DRAFT, FolderRole.SCHEDULED_DRAFTS -> to
    else -> from
}
