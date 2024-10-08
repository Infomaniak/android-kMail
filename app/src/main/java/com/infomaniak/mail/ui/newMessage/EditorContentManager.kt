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
import com.infomaniak.mail.utils.JsoupParserUtil.jsoupParseWithLog
import dagger.hilt.android.scopes.FragmentScoped
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
        .sanitize(jsoupParseWithLog(this))
        .apply { outputSettings().prettyPrint(false) }
        .getHtmlWithoutDocumentWrapping()

    // Jsoup wraps parsed html inside an <html> and <body> tag. This gives us a wrapped form of the html content. While the editor
    // can handle this wrapped HTML without issues, it will also output the HTML in this wrapped form if given one as input.
    // If the HTML received from the API is unwrapped, the sanitization process will wrap it, leading to failed comparisons due to
    // this wrapping, during draft snapshot comparisons, even when the actual content hasn't changed.
    // This method checks if the HTML is wrapped with an <html> tag containing exactly one empty <head> and one <body> tag.
    // If this wrapping is detected, the method unwraps the HTML and returns only the content within the <body> tag.
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
