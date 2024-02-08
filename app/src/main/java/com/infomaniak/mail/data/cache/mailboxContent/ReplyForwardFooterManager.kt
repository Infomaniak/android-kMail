/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.ui.main.thread.MessageWebViewClient
import com.infomaniak.mail.utils.MailDateFormatUtils.formatForHeader
import com.infomaniak.mail.utils.MessageBodyUtils
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.toDate
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReplyForwardFooterManager @Inject constructor(private val appContext: Context) {

    fun createForwardFooter(message: Message, attachmentsToForward: List<Attachment>): String {
        val previousBody = getHtmlDocument(message)?.let { document ->
            document.processCids(
                message = message,
                associateDataToCid = { oldAttachment ->
                    val newAttachment = attachmentsToForward.find { it.originalContentId == oldAttachment.contentId }
                    newAttachment?.contentId
                },
                applyAssociatedDataToImage = { newContentId, imageElement ->
                    imageElement.attr(SRC_ATTRIBUTE, "${CID_PROTOCOL}$newContentId")
                },
            )

            document.outerHtml()
        } ?: ""

        val previousFullBody = computePreviousFullBody(previousBody, message)
        return assembleForwardHtmlFooter(message, previousFullBody)
    }

    fun createReplyFooter(message: Message): String {
        val date = message.date.toDate().formatForHeader()
        val from = message.fromName()
        val messageReplyHeader = appContext.getString(R.string.messageReplyHeader, date, from)

        val previousBody = getHtmlDocument(message)?.let { document ->
            document.processCids(
                message = message,
                associateDataToCid = Attachment::name,
                applyAssociatedDataToImage = { name, imageElement ->
                    imageElement.replaceWith(TextNode("<$name>"))
                },
            )

            document.outerHtml()
        } ?: ""

        val previousFullBody = computePreviousFullBody(previousBody, message)
        return assembleReplyHtmlFooter(messageReplyHeader, previousFullBody)
    }

    private fun Document.processCids(
        message: Message,
        associateDataToCid: (Attachment) -> String?,
        applyAssociatedDataToImage: (String, Element) -> Unit
    ) {
        val attachmentsMap = message.attachments.associate {
            it.contentId to associateDataToCid(it)
        }

        doOnHtmlImage { imageElement ->
            attachmentsMap[getCid(imageElement)]?.let { associatedData ->
                applyAssociatedDataToImage(associatedData, imageElement)
            }
        }
    }

    private fun Message.fromName(): String = sender?.quotedDisplayName() ?: appContext.getString(R.string.unknownRecipientTitle)

    private fun getHtmlDocument(message: Message): Document? {
        val html = message.body?.let { body ->
            when (body.type) {
                Utils.TEXT_PLAIN -> SharedUtils.createHtmlForPlainText(body.value)
                else -> body.value
            }
        }

        return html?.let(Jsoup::parse)
    }

    private fun Document.doOnHtmlImage(actionOnImage: (Element) -> Unit) {
        select(CID_IMAGE_CSS_QUERY).forEach { imageElement -> actionOnImage(imageElement) }
    }

    private fun getCid(imageElement: Element) = imageElement.attr(SRC_ATTRIBUTE).removePrefix(CID_PROTOCOL)

    private fun computePreviousFullBody(previousBody: String, message: Message): String {
        return message.body?.let { body ->
            MessageBodyUtils.mergeSplitBodyAndSubBodies(previousBody, body.subBodies, message.uid)
        } ?: previousBody
    }

    private fun assembleForwardHtmlFooter(
        message: Message,
        previousFullBody: String,
    ): String = with(appContext) {
        val messageForwardHeader = getString(R.string.messageForwardHeader)
        val fromTitle = getString(R.string.fromTitle)
        val dateTitle = getString(R.string.dateTitle)
        val subjectTitle = getString(R.string.subjectTitle)
        val toTitle = getString(R.string.toTitle)
        val ccTitle = getString(R.string.ccTitle)

        val forwardRoot = "<div class=\"${MessageBodyUtils.INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME}\" />"
        return parseAndWrapElementInNewDocument(forwardRoot).apply {
            addAndEscapeTextLine("---------- $messageForwardHeader ---------")
            addAndEscapeTextLine("$fromTitle ${message.fromName()}")
            addAndEscapeTextLine("$dateTitle ${message.date.toDate().formatForHeader()}")
            addAndEscapeTextLine("$subjectTitle ${message.subject}")
            addAndEscapeRecipientLine(toTitle, message.to)
            addAndEscapeRecipientLine(ccTitle, message.cc)
            addAndEscapeTextLine("")
            addAndEscapeTextLine("")

            addAlreadyEscapedBody(previousFullBody)
        }.outerHtml()
    }

    private fun assembleReplyHtmlFooter(messageReplyHeader: String, previousFullBody: String): String {
        val replyRoot = """<div class="${MessageBodyUtils.INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME}" />"""
        return parseAndWrapElementInNewDocument(replyRoot).apply {
            addAndEscapeTextLine(messageReplyHeader, endWithBr = false)
            addReplyBlockQuote {
                addAlreadyEscapedBody(previousFullBody)
            }
        }.outerHtml()
    }

    private fun parseAndWrapElementInNewDocument(elementHtml: String): Element {
        val doc = Jsoup.parseBodyFragment(elementHtml)
        return doc.body().firstElementChild()!!
    }

    private fun Element.addAndEscapeTextLine(content: String, endWithBr: Boolean = true) {
        appendElement("div").apply {
            text(content)
            if (endWithBr) appendElement("br")
        }
    }

    private fun Element.addAndEscapeRecipientLine(prefix: String, recipientList: List<Recipient>) {
        formatRecipientList(recipientList)?.let { recipients -> addAndEscapeTextLine("$prefix $recipients") }
    }

    private fun Element.addAlreadyEscapedBody(previousFullBody: String) {
        append(previousFullBody)
    }

    private fun Element.addReplyBlockQuote(addInnerElements: Element.() -> Unit) {
        val blockQuote = appendElement("blockquote")
        blockQuote.addInnerElements()
    }

    private fun formatRecipientList(recipientList: List<Recipient>): String? {
        return if (recipientList.isNotEmpty()) recipientList.joinToString { it.quotedDisplayName() } else null
    }

    companion object {
        private const val CID_PROTOCOL = "${MessageWebViewClient.CID_SCHEME}:"
        private const val SRC_ATTRIBUTE = "src"
        private const val CID_IMAGE_CSS_QUERY = "img[${SRC_ATTRIBUTE}^='${CID_PROTOCOL}']"
    }
}
