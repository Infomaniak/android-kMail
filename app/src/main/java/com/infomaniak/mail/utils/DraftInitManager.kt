/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import android.content.Context
import com.infomaniak.lib.core.utils.contains
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.ReplyForwardFooterManager
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.AttachmentUploadStatus
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.utils.DraftInitManager.SignatureScore.EXACT_MATCH
import com.infomaniak.mail.utils.DraftInitManager.SignatureScore.EXACT_MATCH_AND_IS_DEFAULT
import com.infomaniak.mail.utils.DraftInitManager.SignatureScore.NO_MATCH
import com.infomaniak.mail.utils.DraftInitManager.SignatureScore.ONLY_EMAIL_MATCH
import com.infomaniak.mail.utils.DraftInitManager.SignatureScore.ONLY_EMAIL_MATCH_AND_IS_DEFAULT
import com.infomaniak.mail.utils.extensions.getDefault
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import javax.inject.Inject

class DraftInitManager @Inject constructor(
    private val mailboxController: MailboxController,
    private val replyForwardFooterManager: ReplyForwardFooterManager,
    @ApplicationContext private val appContext: Context,
) {
    suspend fun Draft.setPreviousMessage(draftMode: DraftMode, previousMessage: Message) {
        inReplyTo = previousMessage.messageId

        val previousReferences = if (previousMessage.references == null) "" else "${previousMessage.references} "
        references = "${previousReferences}${previousMessage.messageId}"

        subject = formatSubject(draftMode, previousMessage.subject)

        when (draftMode) {
            DraftMode.REPLY, DraftMode.REPLY_ALL -> {
                inReplyToUid = previousMessage.uid

                val (toList, ccList) = previousMessage.getRecipientsForReplyTo(replyAll = draftMode == DraftMode.REPLY_ALL)
                to = toList.toRealmList()
                cc = ccList.toRealmList()
            }
            DraftMode.FORWARD -> {
                forwardedUid = previousMessage.uid

                val mailboxUuid = mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!!.uuid
                ApiRepository.attachmentsToForward(mailboxUuid, previousMessage).data?.attachments?.forEach { attachment ->
                    attachments += attachment.apply {
                        resource = previousMessage.attachments.find { it.name == name }?.resource
                        setUploadStatus(AttachmentUploadStatus.UPLOADED)
                    }
                    SentryDebug.addDraftBreadcrumbs(this, step = "set previousMessage when reply/replyAll/Forward")
                }
            }
            DraftMode.NEW_MAIL -> Unit
        }
    }

    /**
     * @param attachments is only needed when the draft mode is [DraftMode.FORWARD]
     */
    fun createQuote(draftMode: DraftMode, previousMessage: Message, attachments: List<Attachment>): String? {
        return when (draftMode) {
            DraftMode.REPLY, DraftMode.REPLY_ALL -> replyForwardFooterManager.createReplyFooter(previousMessage)
            DraftMode.FORWARD -> replyForwardFooterManager.createForwardFooter(previousMessage, attachments)
            DraftMode.NEW_MAIL -> null
        }
    }

    fun chooseSignature(
        currentMailboxEmail: String,
        signatures: List<Signature>,
        draftMode: DraftMode,
        previousMessage: Message?,
    ): Signature {
        val defaultSignature = signatures.getDefault(draftMode)
        val shouldPreselectSignature = draftMode == DraftMode.REPLY || draftMode == DraftMode.REPLY_ALL
        return if (shouldPreselectSignature) {
            // TODO: Why is the most fitting signature not first before defaultSignature?
            defaultSignature ?: guessMostFittingSignature(previousMessage!!, signatures)
        } else {
            defaultSignature
        } ?: Signature.getDummySignature(appContext, email = currentMailboxEmail, isDefault = true)
    }

    fun Draft.setSignatureIdentity(signature: Signature) {
        identityId = signature.id.toString()
    }

    private fun guessMostFittingSignature(message: Message, signatures: List<Signature>): Signature? {

        val signatureEmailsMap = signatures.groupBy { it.senderEmail.lowercase() }

        return findSignatureInRecipients(message.to, signatureEmailsMap)
            ?: findSignatureInRecipients(message.from, signatureEmailsMap)
            ?: findSignatureInRecipients(message.cc, signatureEmailsMap)
    }

    private fun findSignatureInRecipients(
        recipients: RealmList<Recipient>,
        signatureEmailsMap: Map<String, List<Signature>>,
    ): Signature? {

        val matchingEmailRecipients = recipients.filter { it.email.lowercase() in signatureEmailsMap }
        if (matchingEmailRecipients.isEmpty()) return null // If no Recipient represents us, go to next Recipients

        var bestScore = NO_MATCH
        var bestSignature: Signature? = null
        matchingEmailRecipients.forEach { recipient ->
            val signatures = signatureEmailsMap[recipient.email.lowercase()] ?: return@forEach
            val (score, signature) = computeScore(recipient, signatures)
            when (score) {
                EXACT_MATCH_AND_IS_DEFAULT -> return signature
                else -> {
                    if (score.strictlyGreaterThan(bestScore)) {
                        bestScore = score
                        bestSignature = signature
                    }
                }
            }
        }

        return bestSignature
    }

    /**
     * Only pass in Signatures that have the same email address as the Recipient
     */
    private fun computeScore(recipient: Recipient, signatures: List<Signature>): Pair<SignatureScore, Signature> {
        var bestScore: SignatureScore = NO_MATCH
        var bestSignature: Signature? = null

        signatures.forEach { signature ->
            when (val score = computeScore(recipient, signature)) {
                EXACT_MATCH_AND_IS_DEFAULT -> return score to signature
                else -> if (score.strictlyGreaterThan(bestScore)) {
                    bestScore = score
                    bestSignature = signature
                }
            }
        }

        return bestScore to bestSignature!!
    }

    /**
     * Only pass in a Signature that has the same email address as the Recipient
     */
    private fun computeScore(recipient: Recipient, signature: Signature): SignatureScore {
        val isExactMatch = recipient.name == signature.senderName
        val isDefault = signature.isDefault

        val score = when {
            isExactMatch && isDefault -> EXACT_MATCH_AND_IS_DEFAULT
            isExactMatch -> EXACT_MATCH
            isDefault -> ONLY_EMAIL_MATCH_AND_IS_DEFAULT
            else -> ONLY_EMAIL_MATCH
        }

        return score
    }

    private fun formatSubject(draftMode: DraftMode, previousSubject: String?): String {

        fun String.isReply(): Boolean = this in Regex(REGEX_REPLY, RegexOption.IGNORE_CASE)
        fun String.isForward(): Boolean = this in Regex(REGEX_FORWARD, RegexOption.IGNORE_CASE)

        val subject = previousSubject ?: ""

        val prefix = when (draftMode) {
            DraftMode.REPLY, DraftMode.REPLY_ALL -> if (subject.isReply()) "" else PREFIX_REPLY
            DraftMode.FORWARD -> if (subject.isForward()) "" else PREFIX_FORWARD
            DraftMode.NEW_MAIL -> error("`${DraftMode::class.simpleName}` cannot be `${DraftMode.NEW_MAIL.name}` here.")
        }

        return prefix + subject
    }

    private enum class SignatureScore(private val weight: Int) {
        EXACT_MATCH_AND_IS_DEFAULT(4),
        EXACT_MATCH(3),
        ONLY_EMAIL_MATCH_AND_IS_DEFAULT(2),
        ONLY_EMAIL_MATCH(1),
        NO_MATCH(0);

        fun strictlyGreaterThan(other: SignatureScore): Boolean = weight > other.weight
    }

    companion object {
        private const val PREFIX_REPLY = "Re: "
        private const val PREFIX_FORWARD = "Fw: "
        private const val REGEX_REPLY = "(re|ref|aw|rif|r):"
        private const val REGEX_FORWARD = "(fw|fwd|rv|wg|tr|i):"
    }
}
