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
package com.infomaniak.mail.data.cache.mailboxContent

import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.ApiController.json
import com.infomaniak.lib.core.utils.contains
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.getMessage
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.MainViewModel
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.serialization.encodeToString

object DraftController {

    private const val PREFIX_REPLY = "Re: "
    private const val PREFIX_FORWARD = "Fw: "
    private const val REGEX_REPLY = "(re|ref|aw|rif|r):"
    private const val REGEX_FORWARD = "(fw|fwd|rv|wg|tr|i):"

    //region Queries
    private fun MutableRealm?.getDraftsQuery(query: String? = null): RealmQuery<Draft> {
        return with(this ?: RealmDatabase.mailboxContent()) {
            query?.let { query(it) } ?: query()
        }
    }

    private fun MutableRealm?.getDraftQuery(key: String, value: String): RealmSingleQuery<Draft> {
        return (this ?: RealmDatabase.mailboxContent()).query<Draft>("$key = '$value'").first()
    }

    private fun getDraftsWithActionsQuery(realm: MutableRealm? = null): RealmQuery<Draft> {
        return realm.getDraftsQuery("${Draft.actionPropertyName} != nil")
    }
    //endregion

    //region Get data
    private fun getDrafts(realm: MutableRealm): RealmResults<Draft> {
        return realm.getDraftsQuery().find()
    }

    fun getDraftsWithActions(realm: MutableRealm): RealmResults<Draft> {
        return getDraftsWithActionsQuery(realm).find()
    }

    fun getDraftsWithActionsCount(): Long {
        return getDraftsWithActionsQuery().count().find()
    }

    fun getDraft(localUuid: String, realm: MutableRealm? = null): Draft? {
        return realm.getDraftQuery(Draft::localUuid.name, localUuid).find()
    }

    fun getDraftByMessageUid(messageUid: String, realm: MutableRealm? = null): Draft? {
        return realm.getDraftQuery(Draft::messageUid.name, messageUid).find()
    }
    //endregion

    //region Edit data
    fun upsertDraft(draft: Draft, realm: MutableRealm? = null) {
        val block: (MutableRealm) -> Unit = { it.copyToRealm(draft, UpdatePolicy.ALL) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    fun updateDraft(localUuid: String, realm: MutableRealm? = null, onUpdate: (draft: Draft) -> Unit) {
        val block: (MutableRealm) -> Unit = { getDraft(localUuid, it)?.let(onUpdate) }
        realm?.let(block) ?: RealmDatabase.mailboxContent().writeBlocking(block)
    }

    fun cleanOrphans(threads: List<Thread>, realm: MutableRealm) {
        // TODO: Refactor with LinkingObjects when it's available (https://github.com/realm/realm-kotlin/pull/1021)
        val messagesUids = threads.flatMap { it.messages }.map { it.uid }
        val drafts = getDrafts(realm)
        drafts.reversed().forEach {
            if (!messagesUids.contains(it.messageUid)) realm.delete(it)
        }
    }
    //endregion

    fun deleteDraft(message: Message) {
        val mailboxObjectId = MainViewModel.currentMailboxObjectId.value ?: return
        val mailboxUuid = MailboxController.getMailbox(mailboxObjectId)?.uuid ?: return
        with(ApiRepository.deleteMessages(mailboxUuid, listOf(message.uid))) {
            if (isSuccess()) MessageController.deleteMessage(message.uid)
        }
    }

    //region Open Draft
    fun MutableRealm.fetchDraft(draftResource: String, messageUid: String): String? {
        return ApiRepository.getDraft(draftResource).data?.also { draft ->
            draft.initLocalValues(messageUid)
            upsertDraft(draft, this@fetchDraft)
            getMessage(messageUid, this@fetchDraft)?.draftLocalUuid = draft.localUuid
        }?.localUuid
    }

    fun setPreviousMessage(draft: Draft, draftMode: DraftMode, previousMessage: Message) {
        previousMessage.msgId.let {
            draft.inReplyTo = it
            draft.references = it
        }

        when (draftMode) {
            DraftMode.REPLY, DraftMode.REPLY_ALL -> draft.inReplyToUid = previousMessage.uid
            DraftMode.FORWARD -> draft.forwardedUid = previousMessage.uid
            DraftMode.NEW_MAIL -> Unit
        }

        draft.to = previousMessage.from
        if (draftMode == DraftMode.REPLY_ALL) draft.cc = previousMessage.to.union(previousMessage.cc).toRealmList()

        draft.subject = formatSubject(draftMode, previousMessage.subject ?: "")
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

    private inline fun <reified T> ApiResponse<T>.manageUploadErrors() {
        throw error?.exception ?: Exception(data?.let { json.encodeToString(it) })
    }

    fun executeDraftAction(draft: Draft, mailboxUuid: String, realm: MutableRealm) {

        when (draft.action) {
            DraftAction.SAVE -> with(ApiRepository.saveDraft(mailboxUuid, draft)) {
                if (isSuccess()) {
                    updateDraft(draft.localUuid, realm) {
                        it.remoteUuid = data?.draftRemoteUuid
                        it.messageUid = data?.messageUid
                        it.action = null
                    }
                } else manageUploadErrors()
            }
            DraftAction.SEND -> with(ApiRepository.sendDraft(mailboxUuid, draft)) {
                if (isSuccess()) realm.delete(draft) else manageUploadErrors()
            }
            else -> Unit
        }
    }
    //endregion
}
