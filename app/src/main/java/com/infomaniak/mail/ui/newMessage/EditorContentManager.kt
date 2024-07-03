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
package com.infomaniak.mail.ui.newMessage

import com.infomaniak.html.cleaner.HtmlSanitizer
import com.infomaniak.lib.richhtmleditor.RichHtmlEditorWebView
import dagger.hilt.android.scopes.FragmentScoped
import org.jsoup.Jsoup
import javax.inject.Inject

@FragmentScoped
class EditorContentManager @Inject constructor() {
    private lateinit var editor: RichHtmlEditorWebView

    fun initValues(editor: RichHtmlEditorWebView) {
        this.editor = editor
    }

    fun setHtml(htmlPayload: HtmlPayload) = with(htmlPayload) {
        if (isSanitized) setSanitizedHtml(html) else setUnsanitizedHtml(html)
    }

    private fun setUnsanitizedHtml(html: String) {
        setSanitizedHtml(HtmlSanitizer.getInstance().sanitize(Jsoup.parse(html)).html())
    }

    private fun setSanitizedHtml(html: String) {
        editor.setHtml(html)
    }

    data class HtmlPayload(val html: String, val isSanitized: Boolean)
}
