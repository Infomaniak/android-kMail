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

import android.content.ClipDescription
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.signature.Signature.SignaturePosition
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery

object DraftController {

    //region Queries
    private fun MutableRealm?.getDraftsQuery(): RealmQuery<Draft> {
        return (this ?: RealmDatabase.mailboxContent()).query()
    }

    private fun MutableRealm?.getDraftQuery(key: String, value: String): RealmSingleQuery<Draft> {
        return (this ?: RealmDatabase.mailboxContent()).query<Draft>("$key = '$value'").first()
    }
    //endregion

    //region Get data
    fun getDrafts(realm: MutableRealm? = null): RealmResults<Draft> {
        return realm.getDraftsQuery().find()
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

    //region Open Draft
    fun MutableRealm.fetchDraft(draftResource: String, messageUid: String): String? {
        return ApiRepository.getDraft(draftResource).data?.also { draft ->
            upsertDraft(draft.initLocalValues(messageUid), this)
            MessageController.updateMessage(messageUid, this) {
                it.draftLocalUuid = draft.localUuid
            }
        }?.localUuid
    }

    fun MutableRealm.setSignature(draft: Draft) {

        draft.mimeType = ClipDescription.MIMETYPE_TEXT_HTML

        SignatureController.getDefaultSignature(this)?.let { defaultSignature ->

            draft.identityId = defaultSignature.id

            draft.from = realmListOf(Recipient().apply {
                this.email = defaultSignature.sender
                this.name = defaultSignature.fullName
            })

            draft.replyTo = realmListOf(Recipient().apply {
                this.email = defaultSignature.replyTo
                this.name = ""
            })

            val html = "<br/><br/><div class=\"editorUserSignature\">${defaultSignature.content}</div>"
            draft.body = when (defaultSignature.position) {
                SignaturePosition.AFTER_REPLY_MESSAGE -> draft.body + html
                else -> html + draft.body
            }
        }
    }

    fun MutableRealm.setPreviousMessage(draft: Draft, draftMode: Draft.DraftMode, previousMessageUid: String?) {
        previousMessageUid?.let { MessageController.getMessage(it, this) }?.let { previousMessage ->

            previousMessage.msgId.let {
                draft.inReplyTo = it
                draft.references = it
            }

            when (draftMode) {
                Draft.DraftMode.REPLY, Draft.DraftMode.REPLY_ALL -> draft.inReplyToUid = previousMessageUid
                Draft.DraftMode.FORWARD -> draft.forwardedUid = previousMessageUid
                Draft.DraftMode.NEW_MAIL -> Unit
            }

            draft.to = previousMessage.from
            if (draftMode == Draft.DraftMode.REPLY_ALL) draft.cc = previousMessage.to.union(previousMessage.cc).toRealmList()
            previousMessage.subject?.let { draft.subject = "Re: $it" }
        }
    }

    fun executeDraftAction(draft: Draft, mailboxUuid: String, realm: MutableRealm) {
        when (draft.action) {
            DraftAction.SAVE -> {
                val apiResponse = ApiRepository.saveDraft(mailboxUuid, draft)
                if (apiResponse.isSuccess()) with(apiResponse.data!!) {
                    updateDraft(draft.localUuid, realm) {
                        it.remoteUuid = draftRemoteUuid
                        it.messageUid = messageUid
                        it.action = null
                    }
                }
            }
            DraftAction.SEND -> {
                val apiResponse = ApiRepository.sendDraft(mailboxUuid, draft)
                if (apiResponse.isSuccess()) realm.delete(draft)
            }
            else -> Unit
        }
    }
    //endregion
}
