/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.MailDateFormatUtils.formatForHeader
import com.infomaniak.mail.utils.extensions.toDate
import org.jsoup.nodes.Element
import java.util.Date

object PrintHeaderUtils {

    fun createPrintHeader(context: Context, message: Message): Element {
        val elementsToInsert = mutableListOf<Element>()
        val rootHeaderDiv = Element("div")
        val firstSeparator = Element("hr").attr("color", "black")
        val secondSeparator = Element("hr").attr("color", "LightGray")

        val iconElement = Element("img")
            .attr("src", "file:///android_asset/icon_print_email.svg")
            .attr("width", "150")
        elementsToInsert.add(iconElement)
        elementsToInsert.add(firstSeparator)

        message.subject?.let { subject ->
            val subjectElement = Element("b").appendText(subject)
            elementsToInsert.add(subjectElement)
        }

        elementsToInsert.add(secondSeparator)

        val messageDetailsDiv = Element("div").attr("style", "margin-bottom: 40px; display: block")
        messageDetailsDiv.insertPrintRecipientField(context.getString(R.string.ccTitle), *message.cc.toTypedArray())
        messageDetailsDiv.insertPrintRecipientField(context.getString(R.string.toTitle), *message.to.toTypedArray())
        message.sender?.let { messageDetailsDiv.insertPrintRecipientField(context.getString(R.string.fromTitle), it) }

        messageDetailsDiv.insertPrintDateField(context.getString(R.string.dateTitle), message.date.toDate())

        elementsToInsert.add(messageDetailsDiv)

        rootHeaderDiv.attr("style", "margin-bottom: 40px")

        rootHeaderDiv.insertChildren(0, elementsToInsert)

        return rootHeaderDiv
    }

    private fun Element.insertPrintRecipientField(prefix: String, vararg recipients: Recipient) {
        if (recipients.isEmpty()) return

        val joinedRecipients = recipients.joinToString { recipient -> recipient.quotedDisplayName() }
        insertChildren(0, insertField(prefix).appendText(joinedRecipients))
    }

    private fun Element.insertPrintDateField(prefix: String, date: Date) {
        insertChildren(0, insertField(prefix).appendText(date.formatForHeader()))
    }

    private fun insertField(prefix: String) = with(Element("div")) {
        val fieldName = Element("b").appendText(prefix).attr("style", "margin-right: 10px")

        insertChildren(0, fieldName)
    }
}
