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

class HtmlProcessor(private val htmlHeadProvider: HtmlHeadProvider) {
    private val htmlSanitizer = HtmlSanitizer()

    fun processForDisplay(html: String): String {
        return htmlSanitizer.sanitize(html).html()
    }

    // private fun Document.addCustomHeadContents() = apply {
    //     head().append(htmlHeadProvider.headHtml)
    // }
    //
    // private fun Document.toCompactString(): String {
    //     outputSettings()
    //         .prettyPrint(false)
    //         .indentAmount(0)
    //
    //     return html()
    // }
}
