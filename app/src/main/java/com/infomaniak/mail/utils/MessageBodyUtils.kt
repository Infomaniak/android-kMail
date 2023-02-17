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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object MessageBodyUtils {

    private const val blockquote = "blockquote"

    private val quoteDescriptors = listOf(
        ".ik_mail_quote",
        ".gmail_extra",
        ".gmail_quote",
        ".yahoo_quoted",
    )

    fun splitBodyAndQuote(messageBody: String): MessageBodyQuote {
        val htmlDocumentWithQuote = Jsoup.parse(messageBody)
        val htmlDocumentWithoutQuote = Jsoup.parse(messageBody)
        val htmlQuotes: MutableList<Element> = mutableListOf()

        handleBlockQuote(htmlDocumentWithoutQuote, htmlQuotes)
        val currentQuoteDescriptor = handleQuoteDescriptors(htmlDocumentWithoutQuote, htmlQuotes).ifEmpty { blockquote }

        val (body, quote) = splitBodyAndQuote(htmlQuotes, htmlDocumentWithQuote, currentQuoteDescriptor)
        return MessageBodyQuote(messageBody = body, quote = quote)
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

    private fun handleBlockQuote(htmlDocumentWithoutQuote: Document, htmlQuotes: MutableList<Element>) {
        var quotedContentElements = htmlDocumentWithoutQuote.selectFirst(blockquote)

        while (quotedContentElements != null) {
            htmlQuotes.add(quotedContentElements)
            quotedContentElements.remove()
            quotedContentElements = htmlDocumentWithoutQuote.selectFirst(blockquote)
        }
    }

    private fun splitBodyAndQuote(
        htmlQuotes: MutableList<Element>,
        htmlDocumentWithQuote: Document,
        currentQuoteDescriptor: String
    ): Pair<String, String?> {
        return htmlQuotes.lastOrNull()?.let {
            val quotedContentElements = htmlDocumentWithQuote.select(currentQuoteDescriptor)
            if (currentQuoteDescriptor == blockquote) {
                for (quoteElement in quotedContentElements) {
                    if (quoteElement.toString() == it.toString()) {
                        quoteElement.remove()
                        break
                    }
                }
                htmlDocumentWithQuote.toString() to it.toString()
            } else {
                val firstQuotedContent = quotedContentElements.first()
                firstQuotedContent?.remove()
                htmlDocumentWithQuote.toString() to firstQuotedContent.toString()
            }

            htmlDocumentWithQuote.toString() to it.toString()
        } ?: (htmlDocumentWithQuote.toString() to null)
    }

    data class MessageBodyQuote(
        val messageBody: String,
        val quote: String?,
    )
}