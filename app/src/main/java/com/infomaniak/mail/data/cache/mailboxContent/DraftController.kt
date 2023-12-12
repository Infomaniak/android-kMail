/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.data.cache.mailboxContent

import com.infomaniak.lib.core.utils.contains
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.AccountUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import javax.inject.Inject

class DraftController @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val replyForwardHeaderManager: ReplyForwardHeaderManager,
) {

    //region Get data
    fun getDraftsWithActions(realm: TypedRealm): RealmResults<Draft> {
        return getDraftsWithActionsQuery(realm).find()
    }

    fun getDraftsWithActionsCount(): Long {
        return getDraftsWithActionsCount(mailboxContentRealm())
    }

    fun getDraft(localUuid: String): Draft? {
        return getDraft(localUuid, mailboxContentRealm())
    }

    fun getDraftByMessageUid(messageUid: String): Draft? {
        return getDraftByMessageUid(messageUid, mailboxContentRealm())
    }
    //endregion

    //region Edit data
    fun upsertDraft(draft: Draft, realm: MutableRealm) {
        realm.copyToRealm(draft, UpdatePolicy.ALL)
    }

    fun updateDraft(localUuid: String, realm: MutableRealm? = null, onUpdate: (draft: Draft) -> Unit) {
        val block: (MutableRealm) -> Unit = { getDraft(localUuid, realm = it)?.let(onUpdate) }
        realm?.let(block) ?: mailboxContentRealm().writeBlocking(block)
    }
    //endregion

    //region Open Draft
    fun setPreviousMessage(draft: Draft, draftMode: DraftMode, previousMessage: Message) {
        draft.inReplyTo = previousMessage.messageId

        val previousReferences = if (previousMessage.references == null) "" else "${previousMessage.references} "
        draft.references = "${previousReferences}${previousMessage.messageId}"

        draft.subject = formatSubject(draftMode, previousMessage.subject ?: "")

        when (draftMode) {
            DraftMode.REPLY, DraftMode.REPLY_ALL -> {
                draft.inReplyToUid = previousMessage.uid

                val (toList, ccList) = previousMessage.getRecipientsForReplyTo(draftMode == DraftMode.REPLY_ALL)
                draft.to = toList.toRealmList()
                draft.cc = ccList.toRealmList()

                draft.body += replyForwardHeaderManager.createReplyFooter(previousMessage)
            }
            DraftMode.FORWARD -> {
                draft.forwardedUid = previousMessage.uid

                val mailboxUuid = mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!!.uuid
                ApiRepository.attachmentsToForward(mailboxUuid, previousMessage).data?.attachments?.forEach { attachment ->
                    draft.attachments += attachment.apply {
                        resource = previousMessage.attachments.find { it.name == name }?.resource
                    }
                }

                draft.body += replyForwardHeaderManager.createForwardFooter(previousMessage, draft.attachments)
            }
            DraftMode.NEW_MAIL -> Unit
        }
    }

    fun fetchHeavyDataIfNeeded(message: Message, realm: Realm): Pair<Message, Boolean> {
        if (message.isFullyDownloaded()) return message to false

        val (deleted, failed) = ThreadController.fetchMessagesHeavyData(listOf(message), realm)
        val hasFailedFetching = deleted.isNotEmpty() || failed.isNotEmpty()
        return MessageController.getMessage(message.uid, realm)!! to hasFailedFetching
    }

    private fun formatSubject(draftMode: DraftMode, subject: String): String {

        fun String.isReply(): Boolean = this in Regex(REGEX_REPLY, RegexOption.IGNORE_CASE)
        fun String.isForward(): Boolean = this in Regex(REGEX_FORWARD, RegexOption.IGNORE_CASE)

        val prefix = when (draftMode) {
            DraftMode.REPLY, DraftMode.REPLY_ALL -> if (subject.isReply()) "" else PREFIX_REPLY
            DraftMode.FORWARD -> if (subject.isForward()) "" else PREFIX_FORWARD
            DraftMode.NEW_MAIL -> {
                throw IllegalStateException("`${DraftMode::class.simpleName}` cannot be `${DraftMode.NEW_MAIL.name}` here.")
            }
        }

        return prefix + subject
    }
    //endregion

    companion object {
        private const val PREFIX_REPLY = "Re: "
        private const val PREFIX_FORWARD = "Fw: "
        private const val REGEX_REPLY = "(re|ref|aw|rif|r):"
        private const val REGEX_FORWARD = "(fw|fwd|rv|wg|tr|i):"

        //region Queries
        private fun getDraftsQuery(query: String? = null, realm: TypedRealm): RealmQuery<Draft> = with(realm) {
            return@with query?.let(::query) ?: query()
        }

        private fun getOrphanDraftsQuery(realm: TypedRealm): RealmQuery<Draft> {
            return realm.query("${Draft::remoteUuid.name} == nil AND ${Draft.actionPropertyName} == nil")
        }

        private fun getDraftQuery(key: String, value: String, realm: TypedRealm): RealmSingleQuery<Draft> {
            return realm.query<Draft>("$key == $0", value).first()
        }

        private fun getDraftsWithActionsQuery(realm: TypedRealm): RealmQuery<Draft> {
            return getDraftsQuery("${Draft.actionPropertyName} != nil", realm)
        }
        //endregion

        //region Get data
        fun getDraft(localUuid: String, realm: TypedRealm): Draft? {
            return getDraftQuery(Draft::localUuid.name, localUuid, realm).find()
        }

        fun getDraftsWithActionsCount(realm: TypedRealm): Long {
            return getDraftsWithActionsQuery(realm).count().find()
        }

        fun getOrphanDrafts(realm: TypedRealm): RealmResults<Draft> {
            return getOrphanDraftsQuery(realm).find()
        }

        fun getDraftByMessageUid(messageUid: String, realm: TypedRealm): Draft? {
            return getDraftQuery(Draft::messageUid.name, messageUid, realm).find()
        }
        //endregion
    }
}
