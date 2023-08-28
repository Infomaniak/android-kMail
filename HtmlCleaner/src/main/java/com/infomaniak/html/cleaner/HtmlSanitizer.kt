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

/**
 * This file comes from https://github.com/thundernest/k-9/tree/main/app/html-cleaner
 */
package com.infomaniak.html.cleaner

import org.jsoup.nodes.Document

class HtmlSanitizer {
    private val headCleaner = HeadCleaner()
    private val bodyCleaner = BodyCleaner()

    fun sanitize(dirtyDocument: Document): Document {
        val cleanedDocument = bodyCleaner.clean(dirtyDocument)
        headCleaner.clean(dirtyDocument, cleanedDocument)
        return cleanedDocument
    }

    companion object {
        @Volatile
        private var INSTANCE: HtmlSanitizer? = null

        fun getInstance(): HtmlSanitizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return it }
                HtmlSanitizer().also { INSTANCE = it }
            }
        }
    }
}
