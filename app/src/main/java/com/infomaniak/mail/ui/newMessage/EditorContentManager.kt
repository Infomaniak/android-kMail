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

import android.text.Html
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

    fun setContent(bodyContentPayload: BodyContentPayload) {
        when (bodyContentPayload.type) {
            BodyContentType.HTML -> setHtml(bodyContentPayload.content, bodyContentPayload.isSanitized!!)
            BodyContentType.TEXT_PLAIN_WITH_HTML -> setPlainTextAndInterpretHtml(bodyContentPayload.content)
            BodyContentType.TEXT_PLAIN_WITHOUT_HTML -> setPlainTextAndEscapeHtml(bodyContentPayload.content)
        }
    }

    private fun setHtml(html: String, isSanitized: Boolean) {
        val sanitizedHtml = if (isSanitized) html else html.sanitize()
        setSanitizedHtml(sanitizedHtml)
    }

    private fun setPlainTextAndInterpretHtml(text: String) = setSanitizedHtml(text.replaceNewLines().sanitize())

    private fun setPlainTextAndEscapeHtml(text: String) = setSanitizedHtml(text.escapeHtmlCharacters().replaceNewLines())

    private fun setSanitizedHtml(html: String) = editor.setHtml(html)

    private fun String.escapeHtmlCharacters(): String = Html.escapeHtml(this)

    private fun String.replaceNewLines(): String = replace(NEW_LINES_REGEX, "<br>")

    private fun String.sanitize(): String = HtmlSanitizer.getInstance().sanitize(Jsoup.parse(this)).html()

    companion object {
        val NEW_LINES_REGEX = "(\\r\\n|\\n)".toRegex()
    }
}
