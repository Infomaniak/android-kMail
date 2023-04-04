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

import android.content.Context
import com.infomaniak.lib.core.utils.contains
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController.fetchIncompleteMessages
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.ui.main.thread.MessageWebViewClient.Companion.CID_SCHEME
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.MessageBodyUtils
import com.infomaniak.mail.utils.toDate
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

object DraftController {

    private inline val defaultRealm get() = RealmDatabase.mailboxContent()

    private const val PREFIX_REPLY = "Re: "
    private const val PREFIX_FORWARD = "Fw: "
    private const val REGEX_REPLY = "(re|ref|aw|rif|r):"
    private const val REGEX_FORWARD = "(fw|fwd|rv|wg|tr|i):"

    private const val CID_PROTOCOL = "$CID_SCHEME:"
    private const val SRC_ATTRIBUTE = "src"
    private const val CID_IMAGE_CSS_QUERY = "img[$SRC_ATTRIBUTE^='$CID_PROTOCOL']"

    //region Queries
    private fun getDraftsQuery(query: String? = null, realm: TypedRealm): RealmQuery<Draft> = with(realm) {
        return@with query?.let(::query) ?: query()
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
    fun Draft.setPreviousMessage(draftMode: DraftMode, message: Message, context: Context, realm: MutableRealm): Boolean {

        var isSuccess = true

        val previousMessage = if (message.isFullyDownloaded) {
            message
        } else {
            isSuccess = realm.fetchIncompleteMessages(listOf(message)).isEmpty()
            MessageController.getMessage(message.uid, realm)!!
        }

        inReplyTo = previousMessage.messageId

        val previousReferences = if (previousMessage.references == null) "" else "${previousMessage.references} "
        references = "${previousReferences}${previousMessage.messageId}"

        subject = formatSubject(draftMode, previousMessage.subject ?: "")

        when (draftMode) {
            DraftMode.REPLY, DraftMode.REPLY_ALL -> {
                inReplyToUid = previousMessage.uid

                val (toList, ccList) = previousMessage.getRecipientsForReplyTo(draftMode == DraftMode.REPLY_ALL)
                to = toList.toRealmList()
                cc = ccList.toRealmList()

                body += context.replyQuote(previousMessage)
            }
            DraftMode.FORWARD -> {
                forwardedUid = previousMessage.uid

                val mailboxUuid = MailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!!.uuid
                ApiRepository.attachmentsToForward(mailboxUuid, previousMessage).data?.attachments
                    ?.map { attachment ->
                        attachment.apply {
                            resource = previousMessage.attachments.find { it.name == attachment.name }?.resource
                        }
                    }?.let {
                        attachments += it
                    }

                body += context.forwardQuote(previousMessage)
            }
            DraftMode.NEW_MAIL -> Unit
        }

        return isSuccess
    }

    private fun Context.replyQuote(message: Message): String {

        val date = message.date.toDate()
        val from = message.fromName(context = this)
        val messageReplyHeader = getString(R.string.messageReplyHeader, date, from)

        val previousBody = message.body?.value?.let(Jsoup::parse)?.let { document ->
            val attachmentsMap = message.attachments.associate { it.contentId to it.name }
            document.select(CID_IMAGE_CSS_QUERY).forEach { element ->
                val cid = element.attr(SRC_ATTRIBUTE).removePrefix(CID_PROTOCOL)
                attachmentsMap[cid]?.let { name ->
                    element.replaceWith(TextNode("<${name}>"))
                }
            }
            return@let document.outerHtml()
        } ?: ""

        return """
            <div id=\"answerContentMessage\" class="${MessageBodyUtils.INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME}" >
            <div>$messageReplyHeader</div>
            <blockquote class=\"ws-ng-quote\">
            $previousBody
            </blockquote>
        </div>
        """.trimIndent()
    }

    private fun Context.forwardQuote(message: Message): String {

        val messageForwardHeader = getString(R.string.messageForwardHeader)
        val fromTitle = getString(R.string.fromTitle)
        val dateTitle = getString(R.string.dateTitle)
        val subjectTitle = getString(R.string.subjectTitle)
        val toTitle = getString(R.string.toTitle)
        val ccTitle = getString(R.string.ccTitle)
        val previousBody = message.body?.value ?: ""

        val ccList = if (message.cc.isNotEmpty()) {
            "<div>$ccTitle ${message.cc.joinToString { it.quotedDisplay() }}<br></div>"
        } else {
            ""
        }

        return """
            <div class="${MessageBodyUtils.INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME}">
            <div>---------- $messageForwardHeader ---------<br></div>
            <div>$fromTitle ${message.fromName(context = this)}<br></div>
            <div>$dateTitle ${message.date.toDate()}<br></div>
            <div>$subjectTitle ${message.subject}<br></div>
            <div>$toTitle ${message.to.joinToString { it.quotedDisplay() }}<br></div>
            $ccList
            <div><br></div>
            <div><br></div>
            $previousBody
            </div>
        """.trimIndent()
    }

    private fun Message.fromName(context: Context): String {
        return from.firstOrNull()?.quotedDisplay() ?: context.getString(R.string.unknownRecipientTitle)
    }

    private fun Recipient.quotedDisplay(): String = "${("$name ").ifBlank { "" }}&lt;$email&gt;"

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
