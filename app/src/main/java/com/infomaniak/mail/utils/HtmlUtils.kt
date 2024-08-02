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

import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.ui.main.thread.MessageWebViewClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object HtmlUtils {

    const val CID_PROTOCOL = "${MessageWebViewClient.CID_SCHEME}:"
    const val SRC_ATTRIBUTE = "src"
    private const val CID_IMAGE_CSS_QUERY = "img[$SRC_ATTRIBUTE^='$CID_PROTOCOL']"

    fun <T> Document.processCids(
        attachments: List<Attachment>,
        associateDataToCid: (Attachment) -> T?,
        onCidImageFound: (T, Element) -> Unit
    ) {
        val attachmentsMap = attachments.associate {
            it.contentId to associateDataToCid(it)
        }

        doOnHtmlImage { imageElement ->
            attachmentsMap[getCid(imageElement)]?.let { associatedData ->
                onCidImageFound(associatedData, imageElement)
            }
        }
    }

    private fun Document.doOnHtmlImage(actionOnImage: (Element) -> Unit) {
        select(CID_IMAGE_CSS_QUERY).forEach { imageElement -> actionOnImage(imageElement) }
    }

    private fun getCid(imageElement: Element) = imageElement.attr(SRC_ATTRIBUTE).removePrefix(CID_PROTOCOL)
}
