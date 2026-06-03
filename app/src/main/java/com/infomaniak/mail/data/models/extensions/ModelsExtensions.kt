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
@file:OptIn(InternalModelProperties::class)

package com.infomaniak.mail.data.models.extensions

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.infomaniak.core.common.FormatterFileSize.formatShortFileSize
import com.infomaniak.core.common.utils.enumValueOfOrNull
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.guessMimeType
import com.infomaniak.core.network.api.ApiController
import com.infomaniak.core.network.utils.ErrorCodeTranslated
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.RefreshStrategy
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.defaultRefreshStrategy
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.inboxRefreshStrategy
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.scheduledDraftRefreshStrategy
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.snoozeRefreshStrategy
import com.infomaniak.mail.data.models.Attachable
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.AttachmentDisposition
import com.infomaniak.mail.data.models.AttachmentType
import com.infomaniak.mail.data.models.AttachmentUploadStatus
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.FolderRole
import com.infomaniak.mail.data.models.InternalModelProperties
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.SwissTransferFile
import com.infomaniak.mail.data.models.calendar.AttendanceState
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse.AttachmentEventMethod
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.DraftAction
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.EmojiReactionAuthor
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionFolderNotAllowedDraft
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionFolderNotAllowedScheduledDraft
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionFolderNotAllowedSpam
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionFolderNotAllowedTrash
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionMaxRecipient
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionMessageInReplyToEncrypted
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionMessageInReplyToNotAllowed
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionMessageInReplyToNotValid
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionRecipientNotAllowed
import com.infomaniak.mail.data.models.message.EmojiReactionState
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.AttachableMimeTypeUtils
import com.infomaniak.mail.utils.ExternalUtils.ExternalData
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.getDefault
import com.infomaniak.mail.utils.extensions.isEmail
import com.infomaniak.mail.utils.extensions.isUserIn
import io.realm.kotlin.TypedRealm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File

///////////////////////////////////////////////////////////////////////////////////////////////////
// Attendee
///////////////////////////////////////////////////////////////////////////////////////////////////

val Attendee.state get() = AttendanceState.entries.firstOrNull { it.apiValue == _state } ?: AttendanceState.NEEDS_ACTION

fun Attendee.manuallyOverrideAttendanceState(newAttendanceState: AttendanceState) {
    _state = newAttendanceState.apiValue
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Recipient
///////////////////////////////////////////////////////////////////////////////////////////////////

// Computes if the Recipient is external, according to the required conditions.
// Does not tell anything about how to display the Recipient chip when composing a new Message.
fun Recipient.isExternal(externalData: ExternalData): Boolean = with(externalData) {
    val isUnknownContact = email !in emailDictionary
    val isAlias = email in aliases
    val isUntrustedDomain = email.isEmail() && trustedDomains.none(email::endsWith)
    val isMailerDaemon = """mailer-daemon@(?:.+\.)?infomaniak\.ch""".toRegex(RegexOption.IGNORE_CASE).matches(email)

    return@with isUnknownContact && !isAlias && isUntrustedDomain && !isMailerDaemon
}

fun Recipient.Companion.createValidRecipientOrNull(
    email: String,
    name: String? = null,
    hasExternalProvider: Boolean? = null
): Recipient? {
    if (!email.isEmail()) return null
    return createValidRecipient(
        syntacticallyValidEmail = email,
        name = name,
        hasExternalProvider = hasExternalProvider
    )
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Attachable
///////////////////////////////////////////////////////////////////////////////////////////////////

val Attachable.downloadUrl get() = ApiRoutes.resource(resource!!)

val Attachable.safeMimeType get() = if (mimeType == Utils.MIMETYPE_UNKNOWN) name.guessMimeType() else mimeType

fun Attachable.getFileTypeFromMimeType(): AttachmentType = AttachableMimeTypeUtils.getFileTypeFromMimeType(safeMimeType)

fun Attachable.hasUsableCache(
    context: Context,
    file: File? = null,
    userId: Int = AccountUtils.currentUserId,
    mailboxId: Int = AccountUtils.currentMailboxId,
): Boolean = when (this) {
    is Attachment -> {
        val cachedFile = file ?: getCacheFile(context, userId, mailboxId)
        cachedFile.length() > 0 && cachedFile.canRead()
    }
    is SwissTransferFile -> false
}

fun Attachable.isInlineCachedFile(context: Context): Boolean = when (this) {
    is Attachment -> getCacheFile(context).exists() && disposition == AttachmentDisposition.INLINE
    is SwissTransferFile -> false
}

fun Attachable.getCacheFile(
    context: Context,
    userId: Int = AccountUtils.currentUserId,
    mailboxId: Int = AccountUtils.currentMailboxId,
): File = when (this) {
    is Attachment -> {
        val cacheFolder = LocalStorageUtils.getAttachmentsCacheDir(context, extractPathFromResource(), userId, mailboxId)
        File(cacheFolder, name)
    }
    is SwissTransferFile -> File("")
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Attachment
///////////////////////////////////////////////////////////////////////////////////////////////////

val Attachment.isCalendarEvent: Boolean get() = AttachableMimeTypeUtils.calendarMatches.contains(mimeType)

fun Attachment.setUploadStatus(attachmentUploadStatus: AttachmentUploadStatus, draft: Draft? = null, step: String = "") {
    draft?.let { SentryDebug.addDraftBreadcrumbs(it, step) }
    _uploadStatus = attachmentUploadStatus.name
}

/**
 * After uploading an Attachment, we replace the local version with the remote one.
 * The remote one doesn't know about local data, so we have to backup them.
 */
fun Attachment.backupLocalData(oldAttachment: Attachment, draft: Draft) {
    localUuid = oldAttachment.localUuid
    uploadLocalUri = oldAttachment.uploadLocalUri
    setUploadStatus(AttachmentUploadStatus.UPLOADED, draft, "backupLocalData -> setUploadStatus")
}

fun Attachment.getUploadLocalFile() = uploadLocalUri?.toUri()?.toFile()

private fun Attachment.extractPathFromResource(): String {
    return resource?.substringAfter("folder/")?.replace(Regex("(message|attachment)/"), "") ?: ""
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Signature
///////////////////////////////////////////////////////////////////////////////////////////////////

fun Signature.Companion.getDummySignature(
    context: Context,
    email: String = AccountUtils.currentMailboxEmail!!,
    isDefault: Boolean = false,
) = Signature().apply {
    id = Draft.NO_IDENTITY
    isDummy = true
    name = context.getString(R.string.selectSignatureNone)
    senderEmailIdn = email
    this.isDefault = isDefault
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Quotas
///////////////////////////////////////////////////////////////////////////////////////////////////

fun Quotas.getText(context: Context): String {

    val usedSize = context.formatShortFileSize(size)
    val maxSize = maxStorage?.let { context.formatShortFileSize(it) }

    return context.getString(R.string.menuDrawerMailboxStorage, usedSize, maxSize)
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Mailbox
///////////////////////////////////////////////////////////////////////////////////////////////////

inline val Mailbox.kSuite: KSuite?
    get() = when {
        // For KSuite Pro tiers, only Free & Standard are relevant in kMail, all Pro paid tiers got the same functionalities
        isKSuitePro && isKSuiteProFree -> KSuite.Pro.Free
        isKSuitePro && !isKSuiteProFree -> KSuite.Pro.Standard
        isKSuitePerso && isLimited -> KSuite.Perso.Free
        isKSuitePerso && !isLimited -> KSuite.Perso.Plus
        isPartOfStarterPack -> KSuite.StarterPack
        else -> null // It's an older offer, but it checks out.
    }

fun Mailbox.notificationsIsDisabled(notificationManagerCompat: NotificationManagerCompat): Boolean = with(notificationManagerCompat) {
    val isGroupBlocked = getNotificationChannelGroupCompat(channelGroupId)?.isBlocked == true
    val isChannelBlocked = getNotificationChannelCompat(channelId)?.importance == NotificationManagerCompat.IMPORTANCE_NONE
    return@with !areNotificationsEnabled() || isGroupBlocked || isChannelBlocked
}

val Mailbox.unreadCountDisplay: UnreadDisplay
    get() = UnreadDisplay(
        count = unreadCountLocal,
        shouldDisplayPastille = unreadCountLocal == 0 && unreadCountRemote > 0,
    )

fun Mailbox.getDefaultSignatureWithFallback(): Signature {
    return signatures.getDefault() ?: signatures.first()
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// Draft
///////////////////////////////////////////////////////////////////////////////////////////////////

var Draft.action
    get() = enumValueOfOrNull<DraftAction>(_action)
    set(value) {
        _action = value?.apiCallValue
    }

fun Draft.getJsonRequestBody(): MutableMap<String, JsonElement> {
    return draftJson.encodeToJsonElement(this).jsonObject.toMutableMap().apply {
        this[Draft::attachments.name] = JsonArray(attachments.map { JsonPrimitive(it.uuid) })
    }
}

private val draftJson = Json(ApiController.json) { encodeDefaults = true }

///////////////////////////////////////////////////////////////////////////////////////////////////
// Folder
///////////////////////////////////////////////////////////////////////////////////////////////////

fun Folder.messagesBlocking(realm: TypedRealm): List<Message> = MessageController.getMessagesByFolderIdBlocking(id, realm)

@DrawableRes
fun Folder.getIcon(): Int {
    return role?.folderIconRes ?: if (isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder
}

val Folder.unreadCountDisplay: UnreadDisplay
    inline get() = UnreadDisplay(
        count = unreadCountLocal,
        shouldDisplayPastille = unreadCountLocal == 0 && unreadCountRemote > 0,
    )

val Folder.refreshStrategy: RefreshStrategy get() = role?.refreshStrategy ?: defaultRefreshStrategy

fun Folder.getLocalizedName(context: Context): String {
    return role?.folderNameRes?.let(context::getString) ?: name
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// EmojiReactionNotAllowedReason
///////////////////////////////////////////////////////////////////////////////////////////////////

fun EmojiReactionNotAllowedReason.getTranslateRes(): Int = when (this) {
    EmojiReactionFolderNotAllowedDraft -> R.string.errorEmojiReactionFolderNotAllowedDraft
    EmojiReactionFolderNotAllowedScheduledDraft -> R.string.errorEmojiReactionFolderNotAllowedScheduledDraft
    EmojiReactionFolderNotAllowedSpam -> R.string.errorEmojiReactionFolderNotAllowedSpam
    EmojiReactionFolderNotAllowedTrash -> R.string.errorEmojiReactionFolderNotAllowedTrash
    EmojiReactionMessageInReplyToNotAllowed -> R.string.errorEmojiReactionMessageInReplyToNotAllowed
    EmojiReactionMessageInReplyToNotValid -> R.string.errorEmojiReactionMessageInReplyToNotValid
    EmojiReactionMessageInReplyToEncrypted -> R.string.errorEmojiReactionMessageInReplyEncrypted
    EmojiReactionMaxRecipient -> R.string.errorEmojiReactionMaxRecipient
    EmojiReactionRecipientNotAllowed -> R.string.errorEmojiReactionRecipientNotAllowed
}

fun EmojiReactionNotAllowedReason.asErrorCodeTranslated(): ErrorCodeTranslated = object : ErrorCodeTranslated {
    override val code: String = "emoji_reaction__$apiValue"
    override val translateRes: Int = getTranslateRes()
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// CalendarEventResponse
///////////////////////////////////////////////////////////////////////////////////////////////////

fun CalendarEventResponse.isReplyAuthorized(): Boolean {
    return (attachmentEventMethod == null || attachmentEventMethod == AttachmentEventMethod.REQUEST)
            && !isCanceled
            && calendarEvent?.attendees?.isUserIn() == true
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// EmojiReactionState
///////////////////////////////////////////////////////////////////////////////////////////////////

fun EmojiReactionState.addAuthor(newAuthor: EmojiReactionAuthor) {
    authors.add(newAuthor)
    if (hasReacted.not()) hasReacted = newAuthor.recipient?.isMe() == true
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// FolderRole
///////////////////////////////////////////////////////////////////////////////////////////////////

val FolderRole.folderNameRes: Int
    @StringRes
    get() = when (this) {
        FolderRole.INBOX -> R.string.inboxFolder
        FolderRole.COMMERCIAL -> R.string.commercialFolder
        FolderRole.SOCIALNETWORKS -> R.string.socialNetworksFolder
        FolderRole.SENT -> R.string.sentFolder
        FolderRole.SNOOZED -> R.string.snoozedFolder
        FolderRole.SCHEDULED_DRAFTS -> R.string.scheduledMessagesFolder
        FolderRole.DRAFT -> R.string.draftFolder
        FolderRole.SPAM -> R.string.spamFolder
        FolderRole.TRASH -> R.string.trashFolder
        FolderRole.ARCHIVE -> R.string.archiveFolder
    }

val FolderRole.folderIconRes: Int
    @DrawableRes
    get() = when (this) {
        FolderRole.INBOX -> R.drawable.ic_drawer_inbox
        FolderRole.COMMERCIAL -> R.drawable.ic_promotions
        FolderRole.SOCIALNETWORKS -> R.drawable.ic_social_media
        FolderRole.SENT -> R.drawable.ic_send
        FolderRole.SNOOZED -> R.drawable.ic_alarm_clock
        FolderRole.SCHEDULED_DRAFTS -> R.drawable.ic_schedule_send
        FolderRole.DRAFT -> R.drawable.ic_draft
        FolderRole.SPAM -> R.drawable.ic_spam
        FolderRole.TRASH -> R.drawable.ic_bin
        FolderRole.ARCHIVE -> R.drawable.ic_archive_folder
    }

val FolderRole.matomoName: MatomoMail.MatomoName
    get() = when (this) {
        FolderRole.INBOX -> MatomoMail.MatomoName.InboxFolder
        FolderRole.COMMERCIAL -> MatomoMail.MatomoName.CommercialFolder
        FolderRole.SOCIALNETWORKS -> MatomoMail.MatomoName.SocialNetworksFolder
        FolderRole.SENT -> MatomoMail.MatomoName.SentFolder
        FolderRole.SNOOZED -> MatomoMail.MatomoName.SnoozedFolder
        FolderRole.SCHEDULED_DRAFTS -> MatomoMail.MatomoName.ScheduledDraftsFolder
        FolderRole.DRAFT -> MatomoMail.MatomoName.DraftFolder
        FolderRole.SPAM -> MatomoMail.MatomoName.SpamFolder
        FolderRole.TRASH -> MatomoMail.MatomoName.TrashFolder
        FolderRole.ARCHIVE -> MatomoMail.MatomoName.ArchiveFolder
    }

val FolderRole.refreshStrategy: RefreshStrategy
    get() = when (this) {
        FolderRole.INBOX -> inboxRefreshStrategy
        FolderRole.COMMERCIAL -> defaultRefreshStrategy
        FolderRole.SOCIALNETWORKS -> defaultRefreshStrategy
        FolderRole.SENT -> defaultRefreshStrategy
        FolderRole.SNOOZED -> snoozeRefreshStrategy
        FolderRole.SCHEDULED_DRAFTS -> scheduledDraftRefreshStrategy
        FolderRole.DRAFT -> defaultRefreshStrategy
        FolderRole.SPAM -> defaultRefreshStrategy
        FolderRole.TRASH -> defaultRefreshStrategy
        FolderRole.ARCHIVE -> defaultRefreshStrategy
    }
