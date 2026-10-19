/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.html.cleaner

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlSanitizerTest {

    private val htmlSanitizer = HtmlSanitizer()

    private fun sanitize(html: String): Document = htmlSanitizer.sanitize(Jsoup.parse(html))

    /**
     * Mutation XSS: the sanitizer used to keep `title` from inside a dropped `<svg>`. On re-parse, a browser
     * tokenizes `title` as RCDATA, so an inner `</title>` closed it early and the rest became live HTML:
     * `<svg><title><style></title><img src=x onerror="alert(1)">...`
     */
    @Test
    fun `svg title mutation xss is neutralized`() {
        val payload = "<svg><title><style></title><img src=x onerror=\"alert(1)\"></style></title></svg>"

        val reparsed = Jsoup.parse(sanitize(payload).html())

        assertFalse(reparsed.select("title").isNotEmpty())
        assertFalse(reparsed.select("img").isNotEmpty())
        assertFalse(reparsed.select("script").isNotEmpty())
        assertTrue(reparsed.allElements.none { element ->
            element.attributes().any { it.key.startsWith("on", ignoreCase = true) }
        })
    }

    @Test
    fun `title tags are removed`() {
        val cleaned = sanitize("<html><head><title>Subject</title></head><body><title>Another</title><p>Body</p></body></html>")

        assertFalse(cleaned.select("title").isNotEmpty())
        assertTrue(cleaned.body().select("p").isNotEmpty())
    }

    @Test
    fun `direct event handlers are stripped`() {
        val cleaned = sanitize(
            "<p>hello</p><img src=\"https://example.com/image.png\" onerror=\"alert(1)\" onload=\"alert(2)\">"
        )

        assertTrue(cleaned.select("img[src=https://example.com/image.png]").isNotEmpty())
        assertFalse(cleaned.select("img[onerror]").isNotEmpty())
        assertFalse(cleaned.select("img[onload]").isNotEmpty())
    }

    @Test
    fun `sanitization reaches a fixpoint`() {
        val payload = "<svg><title><style></title><img src=x onerror=\"alert(1)\"></style></title></svg>"

        val cleaned = sanitize(payload)
        val resanitized = htmlSanitizer.sanitize(Jsoup.parse(cleaned.html()))

        assertEquals(cleaned.html(), resanitized.html())
    }

    /**
     * Intermediate re-serialization must not alter content: pretty-printing inserts whitespace that becomes real
     * text on re-parse.
     */
    @Test
    fun `whitespace is preserved through sanitization`() {
        val html = "<div style=\"white-space: pre-wrap\">A  B\nC<span>D</span></div>"

        val cleaned = sanitize(html)
        val div = cleaned.body().selectFirst("div")!!

        assertEquals("A  B\nC", div.textNodes().first().wholeText)
        assertEquals("D", div.selectFirst("span")!!.text())
    }

    @Test
    fun `legitimate email content is preserved`() {
        val cleaned = sanitize(
            """
            <html>
              <head><style>h1 { color: red; }</style></head>
              <body>
                <style>p { color: blue; }</style>
                <a href="https://infomaniak.com">link</a>
                <a href="mailto:support@infomaniak.com">mail</a>
                <a href="tel:+41220000000">phone</a>
                <a href="geo:46.2,6.1">map</a>
                <a href="javascript:alert(1)">evil</a>
                <a href="https://infomaniak.com" onclick="alert(1)">evil link</a>
                <img src="https://infomaniak.com/image.png">
                <a href="https://infomaniak.com" data-ik-mention-ref="john.doe">John Doe</a>
              </body>
            </html>
            """.trimIndent()
        )

        assertTrue(cleaned.body().select("style").isNotEmpty())
        assertTrue(cleaned.head().select("style").isNotEmpty())
        assertTrue(cleaned.select("a[href=https://infomaniak.com]").isNotEmpty())
        assertTrue(cleaned.select("a[href^=mailto]").isNotEmpty())
        assertTrue(cleaned.select("a[href^=tel]").isNotEmpty())
        assertTrue(cleaned.select("a[href^=geo]").isNotEmpty())
        assertTrue(cleaned.select("img[src]").isNotEmpty())

        // javascript: hrefs are not allowed, event handlers are stripped
        assertFalse(cleaned.select("a[href^=javascript]").isNotEmpty())
        assertFalse(cleaned.select("a[onclick]").isNotEmpty())

        // Mention attributes survive, and contenteditable="false" is re-added on mention anchors
        val mention = cleaned.select("a[data-ik-mention-ref]").first()
        assertTrue(mention != null)
        assertEquals("false", mention!!.attr("contenteditable"))
    }
}
