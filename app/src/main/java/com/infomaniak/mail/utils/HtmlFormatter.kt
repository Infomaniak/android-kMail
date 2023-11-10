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
package com.infomaniak.mail.utils

import android.content.Context
import androidx.annotation.RawRes
import com.infomaniak.html.cleaner.HtmlSanitizer
import com.infomaniak.mail.R
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import com.google.android.material.R as RMaterial

class HtmlFormatter(private val html: String) {
    private val cssList = mutableListOf<Pair<String, String?>>()
    private val scripts = mutableListOf<String>()
    private var needsMetaViewport = false
    private var needsBodyEncapsulation = false
    private var breakLongWords = false

    fun registerCss(css: String, styleId: String? = null) {
        cssList.add(css to styleId)
    }

    fun registerMetaViewPort() {
        needsMetaViewport = true
    }

    fun registerScript(script: String) {
        scripts.add(script)
    }

    fun registerBodyEncapsulation() {
        needsBodyEncapsulation = true
    }

    fun registerBreakLongWords() {
        breakLongWords = true
    }

    fun inject(): String = with(HtmlSanitizer.getInstance().sanitize(Jsoup.parse(html))) {
        outputSettings().prettyPrint(true)
        head().apply {
            injectCss()
            injectMetaViewPort()
            injectScript()
        }

        if (breakLongWords) body().breakLongStrings()

        if (needsBodyEncapsulation) body().encapsulateElementInDiv()
        html()
    }

    private fun Element.injectCss() {
        cssList.forEach { (css, styleId) ->
            appendElement("style")
                .attr("type", "text/css")
                .appendText(css)
                .also { element -> styleId?.let(element::id) }
        }
    }

    private fun Element.injectMetaViewPort() {
        if (needsMetaViewport) {
            appendElement("meta")
                .attr("name", "viewport")
                .attr("content", "width=device-width")
        }
    }

    private fun Element.injectScript() {
        scripts.forEach { script ->
            appendElement("script")
                .text(script)
        }
    }

    // When displaying emails that have very long strings that are hard to break (mostly URLs), sometimes using
    // 'overflow-wrap: break-word' doesn't help softwrapping the line. This results in very wide emails that need to be zoomed out
    // excessively. To fix this issue, we add zero width spaces in optimal places to help this css attribute wrap correctly
    private fun Node.breakLongStrings() {
        for (child in childNodes()) {
            if (child is TextNode) {
                if (child.isBlank) continue

                val text = child.text()
                if (text.length <= 30) continue

                child.text(breakString(text))
            } else {
                child.breakLongStrings()
            }
        }
    }

    private fun breakString(text: String): String {
        var counter = 0
        var lastCharIsBreakable = false
        val stringBuilder = StringBuilder(OPTIMAL_STRING_LENGTH)

        for (char in text) {
            if (++counter == BREAK_LIMIT) {
                counter = 0
                stringBuilder.append(char)
                stringBuilder.append(ZERO_WIDTH_SPACE)
                continue
            }

            when (char) {
                in DETECT_BUT_DO_NOT_BREAK -> {
                    stringBuilder.append(char)
                    counter = 0
                }
                in BREAK_CHARACTERS -> {
                    stringBuilder.append(char)
                    lastCharIsBreakable = true
                }
                else -> {
                    if (lastCharIsBreakable) stringBuilder.append(ZERO_WIDTH_SPACE)
                    stringBuilder.append(char)
                    lastCharIsBreakable = false
                }
            }
        }

        return stringBuilder.toString()
    }

    private fun Element.encapsulateElementInDiv() {
        val bodyContent = childNodesCopy()
        empty()
        appendElement("div").id(KMAIL_MESSAGE_ID).appendChildren(bodyContent)
    }

    companion object {
        private const val PRIMARY_COLOR_CODE = "--kmail-primary-color"
        private const val KMAIL_MESSAGE_ID = "kmail-message-content"

        private const val ZERO_WIDTH_SPACE = 0x200B.toChar()
        private const val BREAK_LIMIT = 30
        // Across a few handpicked representative emails, average text node length for text nodes bigger than 30 characters seems
        // to be centered between 60 and 120
        private const val OPTIMAL_STRING_LENGTH = 120
        private val DETECT_BUT_DO_NOT_BREAK = setOf(' ', ZERO_WIDTH_SPACE) // ZWSP could already be present in the original html
        private val BREAK_CHARACTERS = setOf(':', '/', '~', '.', ',', '-', '_', '?', '#', '%', '=', '&')

        private fun Context.loadCss(@RawRes cssResId: Int, customColors: List<Pair<String, Int>> = emptyList()): String {
            var css = readRawResource(cssResId)

            if (customColors.isNotEmpty()) {
                var header = ":root {\n"
                customColors.forEach { (variableName, color) ->
                    header += formatCssVariable(variableName, color)
                }
                header += "}\n\n"

                css = header + css
            }

            return css
        }

        private fun Context.loadScript(
            @RawRes scriptResId: Int,
            customVariablesDeclaration: List<Pair<String, Any>> = emptyList(),
        ): String {
            var script = readRawResource(scriptResId)
            customVariablesDeclaration.forEach { (variableName, value) ->
                val variableDeclaration = "const $variableName = ${formatValueForJs(value)};"
                script = "${variableDeclaration}\n${script}"
            }
            return script
        }

        private fun formatValueForJs(value: Any): String {
            return when (value) {
                is String -> "'$value'"
                is Int -> value.toString()
                else -> throw NotImplementedError()
            }
        }

        private fun formatCssVariable(variableName: String, color: Int): String {
            val formattedColor = Utils.colorToHexRepresentation(color)
            return "$variableName: $formattedColor;\n"
        }

        fun Context.getCustomDarkMode(): String = loadCss(R.raw.custom_dark_mode)

        fun Context.getImproveRenderingStyle(): String = loadCss(R.raw.improve_rendering)

        fun Context.getCustomStyle(): String = loadCss(
            R.raw.style,
            listOf(PRIMARY_COLOR_CODE to getAttributeColor(RMaterial.attr.colorPrimary)),
        )

        fun Context.getSignatureMarginStyle(): String = loadCss(R.raw.signature_margins)

        fun Context.getResizeScript(): String = loadScript(
            R.raw.munge_email,
            listOf("MESSAGE_SELECTOR" to "#$KMAIL_MESSAGE_ID")
        )

        fun Context.getFixStyleScript(): String {
            return loadScript(R.raw.fix_email_style)
        }

        fun Context.getJsBridgeScript(): String {
            return loadScript(R.raw.javascript_bridge)
        }
    }
}
