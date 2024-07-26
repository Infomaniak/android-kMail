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
import org.jsoup.nodes.Document
import javax.inject.Inject

@FragmentScoped
class EditorContentManager @Inject constructor() {

    fun setContent(editor: RichHtmlEditorWebView, bodyContentPayload: BodyContentPayload) = with(editor) {
        when (bodyContentPayload.type) {
            BodyContentType.HTML_SANITIZED -> setSanitizedHtml(bodyContentPayload.content)
            BodyContentType.HTML_UNSANITIZED -> setUnsanitizedHtml(bodyContentPayload.content)
            BodyContentType.TEXT_PLAIN_WITH_HTML -> setPlainTextAndInterpretHtml(bodyContentPayload.content)
            BodyContentType.TEXT_PLAIN_WITHOUT_HTML -> setPlainTextAndEscapeHtml(bodyContentPayload.content)
        }
    }

    private fun RichHtmlEditorWebView.setSanitizedHtml(html: String) = setHtml(html)

    private fun RichHtmlEditorWebView.setUnsanitizedHtml(html: String) = setSanitizedHtml(html.sanitize())

    private fun RichHtmlEditorWebView.setPlainTextAndInterpretHtml(text: String) {
        setSanitizedHtml(text.replaceNewLines().sanitize())
    }

    private fun RichHtmlEditorWebView.setPlainTextAndEscapeHtml(text: String) {
        setSanitizedHtml(text.escapeHtmlCharacters().replaceNewLines())
    }

    private fun String.escapeHtmlCharacters(): String = Html.escapeHtml(this)

    private fun String.replaceNewLines(): String = replace(NEW_LINES_REGEX, "<br>")

    private fun String.sanitize(): String = HtmlSanitizer.getInstance()
        .sanitize(Jsoup.parse(this))
        .apply { outputSettings().prettyPrint(false) }
        .getHtmlWithoutDocumentWrapping()

    private fun Document.getHtmlWithoutDocumentWrapping(): String {
        val html = root().firstElementChild() ?: return html()
        val nodeSize = html.childNodeSize()
        val elements = html.children()

        val canRemoveDocumentWrapping = nodeSize == 2
                && elements.count() == 2
                && elements[0].tagName().uppercase() == "HEAD"
                && elements[0].childNodeSize() == 0
                && elements[1].tagName().uppercase() == "BODY"

        return if (canRemoveDocumentWrapping) body().html() else html()
    }

    companion object {
        private val NEW_LINES_REGEX = "(\\r\\n|\\n)".toRegex()
    }
}
