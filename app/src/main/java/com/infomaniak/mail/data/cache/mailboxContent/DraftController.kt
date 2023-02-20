/*
 * Infomaniak kMail - Android
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
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery

object DraftController {

    private const val PREFIX_REPLY = "Re: "
    private const val PREFIX_FORWARD = "Fw: "
    private const val REGEX_REPLY = "(re|ref|aw|rif|r):"
    private const val REGEX_FORWARD = "(fw|fwd|rv|wg|tr|i):"

    private inline val defaultRealm get() = RealmDatabase.mailboxContent()

    //region Queries
    private fun getDraftsQuery(query: String? = null, realm: TypedRealm): RealmQuery<Draft> = with(realm) {
        return@with query?.let { query(it) } ?: query()
    }

    private fun getOrphanDraftsQuery(realm: TypedRealm): RealmQuery<Draft> {
        return realm.query("${Draft::remoteUuid.name} == nil AND ${Draft.actionPropertyName} == nil")
    }

    private fun getDraftQuery(key: String, value: String, realm: TypedRealm): RealmSingleQuery<Draft> {
        return realm.query<Draft>("$key == '$value'").first()
    }

    private fun getDraftsWithActionsQuery(realm: TypedRealm): RealmQuery<Draft> {
        return getDraftsQuery("${Draft.actionPropertyName} != nil", realm)
    }
    //endregion

    //region Get data
    fun getOrphanDrafts(realm: TypedRealm): RealmResults<Draft> {
        return getOrphanDraftsQuery(realm).find()
    }

    fun getDraftsWithActions(realm: TypedRealm): RealmResults<Draft> {
        return getDraftsWithActionsQuery(realm).find()
    }

    fun getDraftsWithActionsCount(realm: TypedRealm = defaultRealm): Long {
        return getDraftsWithActionsQuery(realm).count().find()
    }

    fun getDraft(localUuid: String, realm: TypedRealm = defaultRealm): Draft? {
        return getDraftQuery(Draft::localUuid.name, localUuid, realm).find()
    }

    fun getDraftByMessageUid(messageUid: String, realm: TypedRealm = defaultRealm): Draft? {
        return getDraftQuery(Draft::messageUid.name, messageUid, realm).find()
    }
    //endregion

    //region Edit data
    fun upsertDraft(draft: Draft, realm: MutableRealm) {
        realm.copyToRealm(draft, UpdatePolicy.ALL)
    }

    fun updateDraft(localUuid: String, realm: MutableRealm? = null, onUpdate: (draft: Draft) -> Unit) {
        val block: (MutableRealm) -> Unit = { getDraft(localUuid, realm = it)?.let(onUpdate) }
        realm?.let(block) ?: defaultRealm.writeBlocking(block)
    }
    //endregion

    //region Open Draft
    fun Draft.setPreviousMessage(draftMode: DraftMode, previousMessage: Message) {

        inReplyTo = previousMessage.messageId

        val previousReferences = if (previousMessage.references == null) "" else "${previousMessage.references} "
        references = "${previousReferences}${previousMessage.messageId}"

        when (draftMode) {
            DraftMode.REPLY, DraftMode.REPLY_ALL -> inReplyToUid = previousMessage.uid
            DraftMode.FORWARD -> forwardedUid = previousMessage.uid
            DraftMode.NEW_MAIL -> Unit
        }

        val (to, cc) = previousMessage.getRecipientForReplyTo(draftMode == DraftMode.REPLY_ALL)
        this.to = to.toRealmList()
        this.cc = cc.toRealmList()

        subject = formatSubject(draftMode, previousMessage.subject ?: "")
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
}
