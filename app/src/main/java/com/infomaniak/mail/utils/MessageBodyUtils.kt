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
package com.infomaniak.mail.utils

import android.content.Context
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.message.SubBody
import com.infomaniak.mail.utils.JsoupParserUtil.jsoupParseWithLog
import com.infomaniak.mail.utils.JsoupParserUtil.measureAndLogMemoryUsage
import com.infomaniak.mail.utils.PrintHeaderUtils.createPrintHeader
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import org.jsoup.nodes.Document

object MessageBodyUtils {

    const val INFOMANIAK_SIGNATURE_HTML_CLASS_NAME = "editorUserSignature"
    const val INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME = "ik_mail_quote"
    const val INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME = "forwardContentMessage"
    const val INFOMANIAK_BODY_HTML_ID = "ik-body"
    const val INFOMANIAK_SIGNATURE_HTML_ID = "ik-signature"
    const val INFOMANIAK_QUOTES_HTML_ID = "ik-quotes"

    private const val QUOTE_DETECTION_TIMEOUT = 1_500L

    // Arbitrary maximum length that a Message's body can reach without provoking
    // a crash when opened in a Thread with other equally long Messages.
    private const val MESSAGE_LENGTH_LIMIT = 104_448

    private val quoteDescriptors = arrayOf(
        "blockquote[type=cite]", // macOS and iOS mail client
        // The reply and forward #divRplyFwdMsg div only contains the header, the previous message body is written right next to
        // this div and can't be detected
        // "#divRplyFwdMsg", // Microsoft Outlook
        "#isForwardContent",
        "#isReplyContent",
        "#mailcontent:not(table)",
        "#origbody",
        "#oriMsgHtmlSeperator",
        "#reply139content",
        anyCssClassContaining("gmail_extra"), // Gmail
        anyCssClassContaining("gmail_quote"), // Gmail
        anyCssClassContaining(INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME), // That's us :3
        anyCssClassContaining("moz-cite-prefix"), // Mozilla Thunderbird
        anyCssClassContaining("protonmail_quote"), // Proton Mail
        anyCssClassContaining("yahoo_quoted"), // Yahoo! Mail
        anyCssClassContaining("zmail_extra"), // Zoho Mail
        "[name=\"quote\"]", // GMX
    )

    fun encapsulateQuotesWithInfomaniakClass(quotes: String): String {
        return """<div id=$INFOMANIAK_QUOTES_HTML_ID>$quotes</div>"""
    }

    suspend fun splitContentAndQuote(body: Body): SplitBody {

        val bodyContent = body.value
        if (body.type == Utils.TEXT_PLAIN) return SplitBody(bodyContent)

        return Utils.executeWithTimeoutOrDefault(
            timeout = QUOTE_DETECTION_TIMEOUT,
            defaultValue = SplitBody(bodyContent),
            block = {
                // Do not nest jsoupParseWithLog and measureAndLogMemoryUsage so logs are independent from one another
                val htmlDocument = jsoupParseWithLog(bodyContent)

                if (htmlDocument.text().length > MESSAGE_LENGTH_LIMIT) {
                    val content = bodyContent.substring(0, MESSAGE_LENGTH_LIMIT)
                    return@executeWithTimeoutOrDefault SplitBody(content)
                }

                val (content, quotes) = measureAndLogMemoryUsage(
                    tag = "Split signature and quote memory usage",
                    actionName = "splitting",
                ) { splitContentAndQuotes(htmlDocument = htmlDocument) }

                if (quotes.isEmpty() || quotes.all { it.isBlank() }) SplitBody(bodyContent) else SplitBody(content, bodyContent)
            },
            onTimeout = {
                // Timeout depends on the complexity of the processed html. A small mail has low chances of being complex.
                // A big mail could be simple or could be complex. A big mail doesn't imply that the mail will always be complex
                // but it can help.
                Sentry.captureMessage("Timeout reached while displaying a Message's body", SentryLevel.WARNING) { scope ->
                    scope.setExtra("body size", "${bodyContent.toByteArray().size} bytes")
                }
            },
        )
    }

    private fun CoroutineScope.splitContentAndQuotes(htmlDocument: Document): Pair<String, MutableList<String>> {
        val quotes = mutableListOf<String>()

        for (quoteDescriptor in quoteDescriptors) {
            ensureActive()

            htmlDocument.select(quoteDescriptor).forEach { foundQuote ->
                quotes.add(foundQuote.outerHtml())
                foundQuote.remove()
            }
        }

        return htmlDocument.outerHtml() to quotes
    }

    fun addPrintHeader(context: Context, message: Message, htmlDocument: Document) {
        htmlDocument.body().apply {
            attr("style", "margin: 40px")
            insertChildren(0, createPrintHeader(context, message))
        }
    }

    fun mergeSplitBodyAndSubBodies(body: String, subBodies: List<SubBody>): String {
        return body + formatSubBodiesContent(subBodies)
    }

    private fun formatSubBodiesContent(subBodies: List<SubBody>): String {
        var subBodiesContent = ""

        subBodies.forEach { subBody ->
            subBody.bodyValue?.let {
                if (subBodiesContent.isNotEmpty()) subBodiesContent += "<br/>"
                subBodiesContent += "<blockquote>${it}</blockquote>"
            }
        }

        return subBodiesContent
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
    ) {
        override fun equals(other: Any?): Boolean {
            return other === this || (other is SplitBody && other.content == content && other.quote == quote)
        }

        override fun hashCode(): Int = 31 * content.hashCode() + quote.hashCode()
    }
}
