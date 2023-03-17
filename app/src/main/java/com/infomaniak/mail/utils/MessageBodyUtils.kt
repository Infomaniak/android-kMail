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
package com.infomaniak.mail.utils

import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.utils.Utils.TEXT_PLAIN
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

object MessageBodyUtils {

    const val INFOMANIAK_SIGNATURE_HTML_CLASS_NAME = "editorUserSignature"
    const val INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME = "ik_mail_quote"
    const val INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME = "forwardContentMessage"

    private const val blockquote = "blockquote"

    private val quoteDescriptors = arrayOf(
        "#divRplyFwdMsg", // Outlook
        "#isForwardContent",
        "#isReplyContent",
        "#mailcontent:not(table)",
        "#origbody",
        "#oriMsgHtmlSeperator",
        "#reply139content",
        ".gmail_extra",
        ".gmail_quote",
        ".${INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME}",
        ".moz-cite-prefix",
        ".protonmail_quote",
        ".yahoo_quoted",
        ".zmail_extra", // Zoho
        "[name=\"quote\"]", // GMX
        "blockquote[type=\"cite\"]",
    )

    fun splitBodyAndQuote(initialBody: Body): MessageBodyQuote {
        if (initialBody.type == TEXT_PLAIN) return MessageBodyQuote(initialBody.value, null)

        val htmlDocumentWithQuote = Jsoup.parse(initialBody.value)
        val htmlDocumentWithoutQuote = Jsoup.parse(initialBody.value)

        val blockquoteElement = findAndRemoveLastParentBlockquote(htmlDocumentWithoutQuote)
        val currentQuoteDescriptor = findFirstKnownParentQuoteDescriptor(htmlDocumentWithoutQuote).ifEmpty {
            if (blockquoteElement == null) "" else blockquote
        }

        val (body, quote) = splitBodyAndQuote(blockquoteElement, htmlDocumentWithQuote, currentQuoteDescriptor)
        return MessageBodyQuote(messageBody = if (quote.isNullOrBlank()) initialBody.value else body, quote = quote)
    }

    private fun findAndRemoveLastParentBlockquote(htmlDocumentWithoutQuote: Document): Element? {
        fun Document.selectLastParentBlockquote(): Element? {
            return selectFirst("$blockquote:not($blockquote $blockquote):last-of-type")
        }
        return htmlDocumentWithoutQuote.selectLastParentBlockquote()?.also { it.remove() }
    }

    private fun findFirstKnownParentQuoteDescriptor(htmlDocumentWithoutQuote: Document): String {
        var currentQuoteDescriptor = ""
        for (quoteDescriptor in quoteDescriptors) {
            val quotedContentElement = htmlDocumentWithoutQuote.selectElementAndFollowingSiblings(quoteDescriptor)
            if (quotedContentElement.isNotEmpty()) {
                quotedContentElement.remove()
                currentQuoteDescriptor = quoteDescriptor
            }
        }
        return currentQuoteDescriptor
    }

    private fun splitBodyAndQuote(
        blockquoteElement: Element?,
        htmlDocumentWithQuote: Document,
        currentQuoteDescriptor: String,
    ): Pair<String, String?> {
        return when {
            currentQuoteDescriptor == blockquote -> {
                for (quotedContentElement in htmlDocumentWithQuote.select(currentQuoteDescriptor)) {
                    if (quotedContentElement.toString() == blockquoteElement.toString()) {
                        quotedContentElement.remove()
                        break
                    }
                }
                htmlDocumentWithQuote.toString() to blockquoteElement.toString()
            }
            currentQuoteDescriptor.isNotEmpty() -> {
                val quotedContentElements = htmlDocumentWithQuote.selectElementAndFollowingSiblings(currentQuoteDescriptor)
                quotedContentElements.remove()
                htmlDocumentWithQuote.toString() to quotedContentElements.toString()
            }
            else -> {
                htmlDocumentWithQuote.toString() to null
            }
        }
    }

    private fun Document.selectElementAndFollowingSiblings(cssQuery: String): Elements {
        return select("$cssQuery, $cssQuery ~ *")
    }

    data class MessageBodyQuote(
        val messageBody: String,
        val quote: String?,
    )
}
