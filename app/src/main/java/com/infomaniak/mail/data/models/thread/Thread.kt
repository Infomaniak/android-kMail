/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
@file:UseSerializers(RealmListKSerializer::class, RealmInstantSerializer::class)

package com.infomaniak.mail.data.models.thread

import com.infomaniak.core.utils.apiEnum
import com.infomaniak.mail.MatomoMail.SEARCH_FOLDER_FILTER_NAME
import com.infomaniak.mail.data.api.RealmInstantSerializer
import com.infomaniak.mail.data.api.SnoozeUuidSerializer
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.RefreshStrategy
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.ui.main.folder.ThreadListDateDisplay
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.extensions.toRealmInstant
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.*
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.util.Date

@Serializable
class Thread : RealmObject {

    //region Remote data
    @PrimaryKey
    var uid: String = ""
    var messages = realmListOf<Message>()
    // This value should always be provided because messages always have at least an internalDate. Because of this, the initial value is meaningless
    @SerialName("internal_date")
    var internalDate: RealmInstant = Date().toRealmInstant()
    @SerialName("date")
    var displayDate: RealmInstant = internalDate
    @SerialName("unseen_messages")
    var unseenMessagesCount: Int = 0
    var from = realmListOf<Recipient>()
    var to = realmListOf<Recipient>()
    var subject: String? = null
    @SerialName("has_drafts")
    var hasDrafts: Boolean = false
    @SerialName("flagged")
    var isFavorite: Boolean = false
    @SerialName("answered")
    var isAnswered: Boolean = false
    @SerialName("forwarded")
    var isForwarded: Boolean = false
    @SerialName("snooze_state")
    private var _snoozeState: String? = null
    @SerialName("snooze_end_date")
    var snoozeEndDate: RealmInstant? = null
    @SerialName("snooze_action")
    @Serializable(with = SnoozeUuidSerializer::class)
    var snoozeUuid: String? = null
    //endregion

    //region Local data (Transient)
    @Transient
    var folderId: String = ""
    @Transient
    var folderName: String = ""
    @Transient
    var duplicates = realmListOf<Message>()
    @Transient
    var messagesIds = realmSetOf<String>()
    @Transient
    var isFromSearch: Boolean = false
    @Transient
    var hasAttachable: Boolean = false
    // Has been moved (archived, spammed, deleted, moved) but API call hasn't been done yet.
    // It's only used to filter locally the Threads' list.
    @Transient
    var isLocallyMovedOut: Boolean = false
    @Transient
    var numberOfScheduledDrafts: Int = 0
    //endregion

    @Ignore
    var snoozeState: SnoozeState? by apiEnum(::_snoozeState)
        private set

    // TODO: Put this back in `private` when the Threads parental issues are fixed
    val _folders by backlinks(Folder::threads)

    // TODO: Remove this `runCatching / getOrElse` when the Threads parental issues are fixed
    val folder
        get() = runCatching {
            _folders.single()
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

            return@getOrElse _folders.firstOrNull { uid.contains(it.id) } ?: _folders.first()
        }

    val isOnlyOneDraft get() = messages.count() == 1 && hasDrafts

    fun addMessageWithConditions(newMessage: Message, realm: TypedRealm) {

        val shouldAddMessage = when (FolderController.getFolder(folderId, realm)?.role) {
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

    fun recomputeThread(realm: MutableRealm? = null) {
        val lastCurrentFolderMessage = messages.lastOrNull { it.folderId == folderId }
        val lastMessage = if (isFromSearch) {
            // In the search, some threads (such as threads from the snooze folder) won't have any messages with the same folderId
            // as the thread folderId. This is an expected behavior and we don't want to delete it in this case. We just need to
            // fallback on the last message of the thread.
            lastCurrentFolderMessage ?: messages.lastOrNull()
        } else {
            lastCurrentFolderMessage
        }

        if (lastMessage == null) {
            // Delete Thread if empty. Do not rely on this deletion code being part of the method's logic, it's a temporary fix. If
            // threads should be deleted, then they need to be deleted outside this method.
            if (isManaged()) realm?.delete(this)
            return
        }

        resetThread()

        updateThread(lastMessage)

        // Remove duplicates in Recipients lists
        val unmanagedFrom = if (from.getRealm<Realm>() == null) from else from.copyFromRealm()
        val unmanagedTo = if (to.getRealm<Realm>() == null) to else to.copyFromRealm()
        from = unmanagedFrom.distinct().toRealmList()
        to = unmanagedTo.distinct().toRealmList()
    }

    private fun resetThread() {
        unseenMessagesCount = 0
        from = realmListOf()
        to = realmListOf()
        hasDrafts = false
        isFavorite = false
        isAnswered = false
        isForwarded = false
        hasAttachable = false
        numberOfScheduledDrafts = 0
        snoozeState = null
        snoozeEndDate = null
        snoozeUuid = null
    }

    private fun updateThread(lastMessage: Message) {

        fun Thread.updateSnoozeStatesBasedOn(message: Message) {
            message.snoozeState?.let {
                snoozeState = it
                snoozeEndDate = message.snoozeEndDate
                snoozeUuid = message.snoozeUuid
            }
        }

        messages.sortBy { it.internalDate }

        messages.forEach { message ->
            messagesIds += message.messageIds
            if (!message.isSeen) unseenMessagesCount++
            from += message.from
            to += message.to
            if (message.isDraft) hasDrafts = true
            if (message.isFavorite) isFavorite = true
            if (message.isAnswered) {
                isAnswered = true
                isForwarded = false
            }
            if (message.isForwarded) {
                isForwarded = true
                isAnswered = false
            }
            if (message.hasAttachable) hasAttachable = true
            if (message.isScheduledDraft) numberOfScheduledDrafts++

            updateSnoozeStatesBasedOn(message)
        }

        /**
         * Only needed for snooze because they rely on duplicates to compute the correct state of every thread. Tightly linked
         * with [RefreshStrategy.alsoRecomputeDuplicatedThreads].
         */
        duplicates.forEach(::updateSnoozeStatesBasedOn)

        displayDate = lastMessage.displayDate
        internalDate = lastMessage.internalDate
        subject = messages.first().subject
    }

    fun computeAvatarRecipient(): Pair<Recipient?, Bimi?> = runCatching {

        val message = messages.lastOrNull {
            it.folderRole != FolderRole.SENT &&
                    it.folderRole != FolderRole.DRAFT &&
                    it.folderRole != FolderRole.SCHEDULED_DRAFTS
        } ?: messages.last()

        val recipients = when (message.folderRole) {
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

    fun computeDisplayedRecipients(): RealmList<Recipient> = when (folder.role) {
        FolderRole.SENT, FolderRole.DRAFT, FolderRole.SCHEDULED_DRAFTS -> to
        else -> from
    }

    fun computePreview(): String {
        val message = if (folder.role == FolderRole.SENT) {
            messages.lastOrNull { it.folderId == folderId } ?: messages.last()
        } else {
            messages.last()
        }

        return message.preview
    }

    /**
     * Keep the snooze state condition of [Thread.computeThreadListDateDisplay] the same as
     * the condition used in [ThreadController.getThreadsWithSnoozeFilterQuery].
     * As in, check that [Thread.snoozeEndDate] and [Thread.snoozeUuid] are not null.
     */
    fun computeThreadListDateDisplay(folderRole: FolderRole?) = when {
        numberOfScheduledDrafts > 0 && folderRole == FolderRole.SCHEDULED_DRAFTS -> ThreadListDateDisplay.Scheduled
        snoozeState != null && snoozeEndDate != null && snoozeUuid != null -> ThreadListDateDisplay.Snoozed
        else -> ThreadListDateDisplay.Default
    }

    override fun equals(other: Any?) = other === this || (other is Thread && other.uid == uid)

    override fun hashCode(): Int = uid.hashCode()

    enum class ThreadFilter(val matomoValue: String) {
        ALL(SEARCH_FOLDER_FILTER_NAME),
        SEEN("readFilter"),
        UNSEEN("unreadFilter"),
        STARRED("favoriteFilter"),
        ATTACHMENTS("attachmentFilter"),
        FOLDER(SEARCH_FOLDER_FILTER_NAME),
    }

    companion object {
        const val FORMAT_DAY_OF_THE_WEEK = "EEE"
    }
}
