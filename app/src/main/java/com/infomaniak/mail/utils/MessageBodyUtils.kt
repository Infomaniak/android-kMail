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
        val htmlQuotes = mutableListOf<Element>()

        handleBlockQuote(htmlDocumentWithoutQuote, htmlQuotes)
        val currentQuoteDescriptor = handleQuoteDescriptors(htmlDocumentWithoutQuote, htmlQuotes).ifEmpty { blockquote }

        val (body, quote) = splitBodyAndQuote(htmlQuotes, htmlDocumentWithQuote, currentQuoteDescriptor)
        return MessageBodyQuote(messageBody = if (quote.isNullOrBlank()) initialBody.value else body, quote = quote)
    }

    private fun handleBlockQuote(htmlDocumentWithoutQuote: Document, htmlQuotes: MutableList<Element>) {
        var quotedContentElements = htmlDocumentWithoutQuote.selectFirst(blockquote)

        while (quotedContentElements != null) {
            htmlQuotes.add(quotedContentElements)
            quotedContentElements.remove()
            quotedContentElements = htmlDocumentWithoutQuote.selectFirst(blockquote)
        }
    }

    private fun handleQuoteDescriptors(htmlDocumentWithoutQuote: Document, htmlQuotes: MutableList<Element>): String {
        var currentQuoteDescriptor = ""
        for (quoteDescriptor in quoteDescriptors) {
            val quotedContentElement = htmlDocumentWithoutQuote.select(quoteDescriptor).first()
            if (quotedContentElement != null) {
                htmlQuotes.add(quotedContentElement)
                quotedContentElement.remove()
                currentQuoteDescriptor = quoteDescriptor
                break
            }
        }
        return currentQuoteDescriptor
    }

    private fun splitBodyAndQuote(
        htmlQuotes: MutableList<Element>,
        htmlDocumentWithQuote: Document,
        currentQuoteDescriptor: String,
    ): Pair<String, String?> {
        return htmlQuotes.lastOrNull()?.let { htmlQuote ->
            val quotedContentElements = htmlDocumentWithQuote.select(currentQuoteDescriptor)
            if (currentQuoteDescriptor == blockquote) {
                for (quoteElement in quotedContentElements) {
                    if (quoteElement.toString() == htmlQuote.toString()) {
                        quoteElement.remove()
                        break
                    }
                }
                htmlDocumentWithQuote.toString() to htmlQuote.toString()
            } else {
                val firstQuotedContent = quotedContentElements.first()
                firstQuotedContent?.remove()
                htmlDocumentWithQuote.toString() to firstQuotedContent.toString()
            }
        } ?: (htmlDocumentWithQuote.toString() to null)
    }

    data class MessageBodyQuote(
        val messageBody: String,
        val quote: String?,
    )
}
