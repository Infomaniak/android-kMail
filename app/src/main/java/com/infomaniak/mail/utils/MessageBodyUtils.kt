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

import android.content.Context
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.message.SubBody
import com.infomaniak.mail.extensions.formatNumericalDayMonthYearWithTime
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object MessageBodyUtils {

    const val INFOMANIAK_SIGNATURE_HTML_CLASS_NAME = "editorUserSignature"
    const val INFOMANIAK_REPLY_QUOTE_HTML_CLASS_NAME = "ik_mail_quote"
    const val INFOMANIAK_FORWARD_QUOTE_HTML_CLASS_NAME = "forwardContentMessage"

    private const val QUOTE_DETECTION_TIMEOUT = 1_500L

    private val quoteDescriptors = arrayOf(
        "blockquote[type=cite]", // macOS and iOS mail client
        "#divRplyFwdMsg", // Microsoft Outlook
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

    suspend fun splitContentAndQuote(body: Body): SplitBody {

        val bodyContent = body.value
        if (body.type == Utils.TEXT_PLAIN) return SplitBody(bodyContent)

        return Utils.executeWithTimeoutOrDefault(
            timeout = QUOTE_DETECTION_TIMEOUT,
            defaultValue = SplitBody(bodyContent),
            block = {
                val (content, quotes) = splitContentAndQuotes(htmlDocument = Jsoup.parse(bodyContent))
                if (quotes.isEmpty() || quotes.all { it.isBlank() }) SplitBody(bodyContent) else SplitBody(content, bodyContent)
            },
            onTimeout = {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.WARNING
                    scope.setExtra("body size", "${bodyContent.toByteArray().size} bytes")
                    scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                    Sentry.captureMessage("Timeout reached while displaying a Message's body")
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
        htmlDocument.body()
            .attr("style", "margin: 40px")
        htmlDocument.body().insertChildren(0, createPrintHeader(context, message))
    }

    private fun createPrintHeader(context: Context, message: Message): Element {
        val rootHeaderDiv = Element("div")
        val firstSeparator = Element("hr").attr("color", "black")
        val secondSeparator = Element("hr").attr("color", "LightGray")

        val iconElement = Element("img")
            .attr("src", "file:///android_asset/icon_print_email.svg")
            .attr("width", "150")
        rootHeaderDiv.insertChildren(0, iconElement)
        rootHeaderDiv.insertChildren(1, firstSeparator)

        message.subject?.let { subject ->
            val subjectElement = Element("b").appendText(subject)
            rootHeaderDiv.insertChildren(2, subjectElement)
        }

        rootHeaderDiv.insertChildren(3, secondSeparator)

        val messageDetailsDiv = Element("div").attr("style", "margin-bottom: 40px; display: block")
        messageDetailsDiv.insertPrintRecipientField(context.getString(R.string.ccTitle), *message.cc.toTypedArray())
        messageDetailsDiv.insertPrintRecipientField(context.getString(R.string.toTitle), * message.to.toTypedArray())
        message.sender?.let { messageDetailsDiv.insertPrintRecipientField(context.getString(R.string.fromTitle), it) }

        val formattedDate = message.date.toDate().formatNumericalDayMonthYearWithTime()
        messageDetailsDiv.insertPrintDateField(context.getString(R.string.dateTitle), formattedDate)

        rootHeaderDiv.insertChildren(4, messageDetailsDiv)

        rootHeaderDiv.attr("style", "margin-bottom: 40px")

        return rootHeaderDiv
    }

    private fun Element.insertPrintRecipientField(prefix: String, vararg recipients: Recipient) {
        if (recipients.isEmpty()) return

        insertChildren(
            0,
            insertField(prefix).appendText(
                recipients.joinToString { recipient -> recipient.quotedDisplay() }
            ),
        )
    }

    private fun Element.insertPrintDateField(prefix: String, date: String) {
        insertChildren(0, insertField(prefix).appendText(date))
    }

    private fun insertField(prefix: String) = with(Element("div")) {
        val fieldName = Element("b").appendText(prefix).attr("style", "margin-right: 10px")

        insertChildren(0, fieldName)
    }

    fun mergeSplitBodyAndSubBodies(body: String, subBodies: List<SubBody>, messageUid: String): String {
        return body + formatSubBodiesContent(subBodies, messageUid)
    }

    private fun formatSubBodiesContent(subBodies: List<SubBody>, messageUid: String): String {
        var subBodiesContent = ""

        subBodies.forEach { subBody ->
            subBody.bodyValue?.let {
                if (subBodiesContent.isNotEmpty()) subBodiesContent += "<br/>"
                subBodiesContent += "<blockquote>${it}</blockquote>"
            }
        }

        if (subBodiesContent.isNotEmpty()) SentryDebug.sendSubBodiesTrigger(messageUid)

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
