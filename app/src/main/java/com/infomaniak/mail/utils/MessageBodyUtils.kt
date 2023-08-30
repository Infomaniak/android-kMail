/*
 * Infomaniak Mail - Android
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object MessageBodyUtils {

    const val INFOMANIAK_SIGNATURE_HTML_CLASS_NAME = "editorUserSignature"
    const val INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME = "ik_mail_quote"
    const val INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME = "forwardContentMessage"

    private const val BLOCKQUOTE = "blockquote"
    private const val QUOTE_DETECTION_SIZE_LIMIT = 1_000_000 // 1 Meg looks like a fine threshold

    private val quoteDescriptors = arrayOf(
        // Do not detect this quote as long as we can't detect siblings quotes or else a single reply will be missing among the
        // many replies of an Outlook reply "chain", which is worst than simply ignoring it
        // "#divRplyFwdMsg", // Outlook
        "#isForwardContent",
        "#isReplyContent",
        "#mailcontent:not(table)",
        "#origbody",
        "#oriMsgHtmlSeperator",
        "#reply139content",
        anyCssClassContaining("gmail_extra"),
        anyCssClassContaining("gmail_quote"),
        anyCssClassContaining(INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME),
        anyCssClassContaining("moz-cite-prefix"),
        anyCssClassContaining("protonmail_quote"),
        anyCssClassContaining("yahoo_quoted"),
        anyCssClassContaining("zmail_extra"), // Zoho
        "[name=\"quote\"]", // GMX
    )

    fun splitContentAndQuote(body: Body): SplitBody {

        if (body.type == Utils.TEXT_PLAIN) return SplitBody(body.value)

        // Heuristic to give up on mail too large for "perfect" preprocessing.
        if (body.value.toByteArray().size > QUOTE_DETECTION_SIZE_LIMIT) return SplitBody(body.value)

        // The original parsed html document in full
        val originalHtmlDocument = Jsoup.parse(body.value)
        // Initiated to the original document and it'll be processed by Jsoup to remove quotes.
        val htmlDocumentWithoutQuote = originalHtmlDocument.clone()

        // Find the last parent blockquote and delete it in htmlDocumentWithoutQuote
        val blockquoteElement = findAndRemoveLastParentBlockquote(htmlDocumentWithoutQuote)
        // Find the first known parent quote in the html and delete all known quotes descriptor
        val currentQuoteDescriptor = findFirstKnownParentQuoteDescriptor(htmlDocumentWithoutQuote).ifEmpty {
            if (blockquoteElement == null) "" else BLOCKQUOTE
        }

        val (content, quote) = splitContentAndQuote(originalHtmlDocument, currentQuoteDescriptor, blockquoteElement)
        return if (quote.isNullOrBlank()) SplitBody(body.value) else SplitBody(content, body.value)
    }

    private fun findAndRemoveLastParentBlockquote(htmlDocumentWithoutQuote: Document): Element? {
        fun Document.selectLastParentBlockquote(): Element? {
            return selectFirst("$BLOCKQUOTE:not($BLOCKQUOTE $BLOCKQUOTE):last-of-type")
        }

        return htmlDocumentWithoutQuote.selectLastParentBlockquote()?.also { it.remove() }
    }

    private fun findFirstKnownParentQuoteDescriptor(htmlDocumentWithoutQuote: Document): String {
        var currentQuoteDescriptor = ""
        for (quoteDescriptor in quoteDescriptors) {
            val quotedContentElement = htmlDocumentWithoutQuote.select(quoteDescriptor)
            if (quotedContentElement.isNotEmpty()) {
                quotedContentElement.remove()
                currentQuoteDescriptor = quoteDescriptor
            }
        }

        return currentQuoteDescriptor
    }

    private fun splitContentAndQuote(
        htmlDocumentWithQuote: Document,
        currentQuoteDescriptor: String,
        blockquoteElement: Element?,
    ): Pair<String, String?> {
        return when {
            currentQuoteDescriptor == BLOCKQUOTE -> {
                for (quotedContentElement in htmlDocumentWithQuote.select(currentQuoteDescriptor)) {
                    if (quotedContentElement.toString() == blockquoteElement.toString()) {
                        quotedContentElement.remove()
                        break
                    }
                }
                htmlDocumentWithQuote.toString() to blockquoteElement.toString()
            }
            currentQuoteDescriptor.isNotEmpty() -> {
                val quotedContentElements = htmlDocumentWithQuote.select(currentQuoteDescriptor)
                quotedContentElements.remove()
                htmlDocumentWithQuote.toString() to quotedContentElements.toString()
            }
            else -> {
                htmlDocumentWithQuote.toString() to null
            }
        }
    }

    //region Utils
    /**
     * Some Email clients rename CSS classes to prefix them.
     * We match all the CSS classes that contain the quote, in case this one has been renamed.
     * @return a new CSS query
     */
    private fun anyCssClassContaining(cssClass: String) = "[class*=$cssClass]"
    //endregion

    data class SplitBody(
        val content: String,
        val quote: String? = null,
    )
}
