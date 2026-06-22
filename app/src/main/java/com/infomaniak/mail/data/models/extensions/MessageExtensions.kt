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
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.FolderRole
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.getMessages.DefaultMessageFlags
import com.infomaniak.mail.data.models.getMessages.SnoozeMessageFlags
import com.infomaniak.mail.data.models.message.FormatedPreview
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.extensions.toRealmInstant
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.TypedRealmObject
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlin.random.Random

val Message.folder: Folder
    get() = run {

        // TODO: Remove the whole content of this `run` and replace it with this commented code when the parental issue is fixed
        // val sameFolderThread = threads.singleOrNull { it.folderId == folderId }
        // val searchFolderThread = threads.first { it.folderId == FolderController.SEARCH_FOLDER_ID }
        // return@run (sameFolderThread ?: searchFolderThread).folder

        var correctFolder: Folder?
        var reason: String?

        computeFolderAndReason(folderId).let {
            correctFolder = it.first
            reason = it.second
        }

        if (correctFolder == null) {
            computeFolderAndReason(FolderController.SEARCH_FOLDER_ID).let {
                correctFolder = it.first
                reason = it.second
            }
        }

        if (correctFolder == null) {
            // TODO: As of 20/05/2025, this event is taking the Sentry server down because it's emitting way too often.
            //  So we temporarily set it to emit in only 2% of cases, while we are working on a fix.
            if (Random.nextInt(0, 100) < 2) {
                Sentry.captureMessage(
                    "Message doesn't have a parent Thread from its own Folder, it should not be possible",
                    SentryLevel.ERROR,
                ) { scope ->
                    scope.setTag("issueType", reason ?: "null") // The `null` value is supposedly impossible
                    scope.setExtra("threadsUid", threads.joinToString { it.uid })
                    scope.setExtra("threadsCount", "${threads.count()}")
                    scope.setExtra(
                        "threadsFolder",
                        "${threads.map { "role:[${it.folder.role?.name}] (folderId:[${it.folderId}] | folder.id:[${it.folder.id}])" }}",
                    )
                    scope.setExtra("messageUid", uid)
                    scope.setExtra("folderId", folderId)
                    scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                }
            }

            correctFolder = (threads.firstOrNull { it.folderId == folderId } ?: threads.first()).folder
        }

        return@run correctFolder
    }

fun Message.isInSpamFolder() = folder.role == FolderRole.SPAM

fun Message.computeFolderAndReason(folderId: String): Pair<Folder?, String?> {

    var correctFolder: Folder? = null
    var reason: String? = null

    val list = threads.filter { it.folderId == folderId }
    val count = list.count()

    when {
        count < 1 -> reason = "no parent Thread from correct Folder"
        count > 1 -> reason = "multiple same parent Threads"
        else -> correctFolder = list.first().folder
    }

    return correctFolder to reason
}

val Message.calendarAttachment: Attachment? get() = if (isDraft) null else attachments.firstOrNull(Attachment::isCalendarEvent)

fun Message.getFormattedPreview(context: Context, totalUnseenReactionOnLastEmoji: Int = 0): FormatedPreview = when {
    isEncrypted -> FormatedPreview.Encryption(context.getString(R.string.encryptedMessageHeader))
    isReaction && totalUnseenReactionOnLastEmoji > 1 -> FormatedPreview.Reaction(
        context.resources.getQuantityString(
            R.plurals.previewMultiReaction,
            (totalUnseenReactionOnLastEmoji - 1),
            emojiReaction,
            from.firstOrNull()?.name.orEmpty(),
            (totalUnseenReactionOnLastEmoji - 1)
        )
    )
    isReaction -> FormatedPreview.Reaction(context.getString(R.string.previewReaction, from.first().name, emojiReaction))
    preview.isBlank() -> FormatedPreview.Empty(context.getString(R.string.noBodyDescription))
    else -> FormatedPreview.Body(preview.trim())
}

fun Message.updateFlags(flags: DefaultMessageFlags) {
    isSeen = flags.isSeen
    isFavorite = flags.isFavorite
    isAnswered = flags.isAnswered
    isForwarded = flags.isForwarded
    isScheduledMessage = flags.isScheduledMessage

    if (flags.isSeen && snoozeState == SnoozeState.Unsnoozed) {
        snoozeState = null
        snoozeEndDate = null
        snoozeUuid = null
    }
}

fun Message.updateSnoozeFlags(flags: SnoozeMessageFlags) {
    snoozeEndDate = flags.snoozeEndDate.toRealmInstant()
}

fun Message.getRecipientsForReplyTo(replyAll: Boolean = false): Pair<List<Recipient>, List<Recipient>> {

    fun cleanedFrom() = from.detachedFromRealm(this).filterNot { it.isMe() }

    val cleanedTo = to.detachedFromRealm(this).filterNot { it.isMe() }
    val cleanedCc = cc.detachedFromRealm(this).filterNot { it.isMe() }

    var to = replyTo.detachedFromRealm(this).filterNot { it.isMe() }.ifEmpty { cleanedFrom() }
    var cc = emptyList<Recipient>()

    if (to.isEmpty()) {
        to = cleanedTo
    } else if (replyAll) {
        cc = cleanedTo
    }

    if (to.isEmpty()) {
        to = cleanedCc
    } else if (replyAll) {
        cc = cc + cleanedCc
    }

    if (to.isEmpty()) to = from.detachedFromRealm(this)

    return to to cc
}

private inline fun <reified T : TypedRealmObject> RealmList<T>.detachedFromRealm(
    message: Message,
    depth: UInt = UInt.MIN_VALUE
): List<T> {
    return if (message.isManaged()) copyFromRealm(depth) else this
}
