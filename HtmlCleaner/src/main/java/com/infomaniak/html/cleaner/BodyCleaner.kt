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

/**
 * This file comes from https://github.com/thundernest/k-9/tree/main/app/html-cleaner
 */
package com.infomaniak.html.cleaner

import org.jsoup.nodes.Document
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

internal class BodyCleaner {
    private val cleaner: Cleaner

    init {
        val allowList = Safelist.relaxed()
            .addTags("font", "hr", "ins", "del", "center", "map", "area", "title", "tt", "kbd", "samp", "var", "button")
            .addAttributes("font", "color", "face", "size")
            .addAttributes(
                "body",
                "id",
                "class",
                "dir",
                "lang",
                "style",
                "alink",
                "background",
                "bgcolor",
                "link",
                "text",
                "vlink"
            )
            .addAttributes("a", "name")
            .addAttributes("div", "align")
            .addAttributes(
                "table",
                "align",
                "background",
                "bgcolor",
                "border",
                "cellpadding",
                "cellspacing",
                "width",
            )
            .addAttributes("tr", "align", "background", "bgcolor", "valign")
            .addAttributes(
                "th",
                "align", "background", "bgcolor", "colspan", "headers", "height", "nowrap", "rowspan", "scope",
                "sorted", "valign", "width",
            )
            .addAttributes(
                "td",
                "align", "background", "bgcolor", "colspan", "headers", "height", "nowrap", "rowspan", "scope",
                "valign", "width",
            )
            .addAttributes("map", "name")
            .addAttributes("area", "shape", "coords", "href", "alt")
            .addProtocols("area", "href", "http", "https")
            .addAttributes("img", "usemap")
            .addAttributes(":all", "class", "style", "id", "dir")
            .addProtocols("img", "src", "http", "https", "cid", "data")
            // Allow all URI schemes in links. Removing all protocols makes the list of protocols empty which means allow all protocols
            .removeProtocols("a", "href", "ftp", "http", "https", "mailto")

        cleaner = Cleaner(allowList)
    }

    fun clean(dirtyDocument: Document): Document {
        val cleanedDocument = cleaner.clean(dirtyDocument)
        copyDocumentType(dirtyDocument, cleanedDocument)
        return cleanedDocument
    }

    private fun copyDocumentType(dirtyDocument: Document, cleanedDocument: Document) {
        dirtyDocument.documentType()?.let { documentType ->
            cleanedDocument.insertChildren(0, documentType)
        }
    }
}
