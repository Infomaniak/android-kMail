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
package com.infomaniak.mail.data.models.thread

import android.content.Context
import androidx.annotation.IdRes
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.isSmallerThanDays
import com.infomaniak.mail.utils.toDate
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.PrimaryKey

class Thread : RealmObject {

    @PrimaryKey
    var uid: String = ""
    var folderId: String = ""
    var messages: RealmList<Message> = realmListOf()
    var duplicates: RealmList<Message> = realmListOf()
    var unseenMessagesCount: Int = 0
    var from: RealmList<Recipient> = realmListOf()
    var to: RealmList<Recipient> = realmListOf()
    var date: RealmInstant = RealmInstant.MAX
    var size: Int = 0
    var hasAttachments: Boolean = false
    var hasDrafts: Boolean = false
    var isFavorite: Boolean = false
    var answered: Boolean = false
    var forwarded: Boolean = false
    var scheduled: Boolean = false
    var messagesIds: RealmSet<String> = realmSetOf()

    fun addFirstMessage(message: Message) {
        messagesIds += message.messageIds
        messages.add(message)
    }

    fun addMessageWithConditions(newMessage: Message, realm: TypedRealm) {
        messagesIds += newMessage.messageIds

        val folderRole = FolderController.getFolder(folderId, realm)?.role

        val isInTrash = newMessage.isInTrash(realm)

        // If the Message is deleted, but we are not in the Trash: ignore it, just leave.
        if (folderRole != FolderRole.TRASH && isInTrash) return

        val shouldAddMessage = when (folderRole) {
            FolderRole.DRAFT -> newMessage.isDraft // Only add draft Messages in Draft folder
            FolderRole.TRASH -> isInTrash // Only add deleted Messages in Trash folder
            else -> true
        }

        if (shouldAddMessage) {
            val twinMessage = messages.firstOrNull { it.messageId == newMessage.messageId }
            if (twinMessage != null) {
                addDuplicatedMessage(twinMessage, newMessage)
            } else {
                messages.add(newMessage)
            }
        }
    }

    private fun addDuplicatedMessage(twinMessage: Message, newMessage: Message) {
        val isTwinTheRealMessage = twinMessage.folderId == folderId
        if (isTwinTheRealMessage) {
            duplicates.add(newMessage)
        } else {
            messages.remove(twinMessage)
            duplicates.add(twinMessage)
            messages.add(newMessage)
        }
    }

    fun recomputeThread(realm: MutableRealm) {

        // Delete Thread if empty
        if (messages.none { it.folderId == folderId }) {
            realm.delete(this)
            return
        }

        resetThread()

        updateThread()

        // Remove duplicates in Recipients lists
        from = from.toRecipientsList().distinct().toRealmList()
        to = to.toRecipientsList().distinct().toRealmList()
    }

    private fun resetThread() {
        // TODO: Remove this `sortBy`, and get the Messages in the right order via Realm query (but before, fix the `Thread.date`)
        messages.sortBy { it.date }
        unseenMessagesCount = 0
        from = realmListOf()
        to = realmListOf()
        size = 0
        hasAttachments = false
        hasDrafts = false
        isFavorite = false
        answered = false
        forwarded = false
        scheduled = false
    }

    private fun updateThread() {
        messages.forEach { message ->
            messagesIds += message.messageIds
            if (!message.seen) unseenMessagesCount++
            from += message.from
            to += message.to
            size += message.size
            if (message.hasAttachments) hasAttachments = true
            if (message.isDraft) hasDrafts = true
            if (message.isFavorite) isFavorite = true
            if (message.answered) answered = true
            if (message.forwarded) forwarded = true
            if (message.scheduled) scheduled = true
        }
        date = messages.findLast { it.folderId == folderId }?.date!!
    }

    fun formatDate(context: Context): String = with(date.toDate()) {
        when {
            isToday() -> format(FORMAT_DATE_HOUR_MINUTE)
            isYesterday() -> context.getString(R.string.messageDetailsYesterday)
            isSmallerThanDays(6) -> format(FORMAT_DAY_OF_THE_WEEK)
            isThisYear() -> format(FORMAT_DATE_SHORT_DAY_ONE_CHAR)
            else -> format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR)
        }
    }

    fun isOnlyOneDraft(): Boolean = hasDrafts && messages.count() == 1

    fun getFormattedSubject(context: Context) = messages.first().getFormattedSubject(context)

    // TODO: Replace this with a RealmList sub query (blocked by https://github.com/realm/realm-kotlin/issues/1037)
    fun getMessageDuplicates(messageId: String): List<Message> = duplicates.filter { it.messageId == messageId }

    // TODO: Replace this with a RealmList sub query (blocked by https://github.com/realm/realm-kotlin/issues/1037)
    fun getMessageDuplicatesUids(messageId: String): List<String> = getMessageDuplicates(messageId).map { it.uid }

    private fun RealmList<Recipient>.toRecipientsList(): List<Recipient> {
        return map { Recipient().initLocalValues(it.email, it.name) }
    }

    enum class ThreadFilter(@IdRes val filterNameRes: Int) {
        ALL(R.string.searchAllMessages),
        SEEN(R.string.searchFilterRead),
        UNSEEN(R.string.searchFilterUnread),
        STARRED(R.string.favoritesFolder),
        ATTACHMENTS(R.string.searchFilterAttachment),
        FOLDER(R.string.searchFilterFolder),
    }

    companion object {
        const val FORMAT_DAY_OF_THE_WEEK = "EEE"
    }
}
