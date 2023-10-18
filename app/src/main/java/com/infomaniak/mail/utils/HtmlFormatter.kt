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
import com.google.android.material.R as RMaterial

class HtmlFormatter(private val html: String) {
    private val cssList = mutableListOf<Pair<String, String?>>()
    private val scripts = mutableListOf<String>()
    private var needsMetaViewport = false
    private var needsBodyEncapsulation = false

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

    fun inject(): String = with(HtmlSanitizer.getInstance().sanitize(Jsoup.parse(html))) {
        outputSettings().prettyPrint(true)
        head().apply {
            injectCss()
            injectMetaViewPort()
            injectScript()
        }
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

    private fun Element.encapsulateElementInDiv() {
        val bodyContent = childNodesCopy()
        empty()
        appendElement("div").id(KMAIL_MESSAGE_ID).appendChildren(bodyContent)
    }

    companion object {
        private const val PRIMARY_COLOR_CODE = "--kmail-primary-color"
        private const val KMAIL_MESSAGE_ID = "kmail-message-content"

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
