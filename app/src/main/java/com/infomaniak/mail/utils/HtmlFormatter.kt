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
package com.infomaniak.mail.utils

import android.content.Context
import androidx.annotation.RawRes
import com.infomaniak.mail.R
import okhttp3.internal.toHexString
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.google.android.material.R as RMaterial

class HtmlFormatter(private val html: String) {

    private val cssList = mutableListOf<Pair<String, String?>>()
    private var needsMetaViewport = false

    fun registerCss(css: String, styleId: String? = null) {
        cssList.add(css to styleId)
    }

    fun registerMetaViewPort() {
        needsMetaViewport = true
    }

    fun inject(): String = with(Jsoup.parse(html)) {
        head().apply {
            injectCss()
            injectMetaViewPort()
        }
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

    companion object {
        private const val PRIMARY_COLOR_CODE = "--kmail-primary-color"

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

        private fun formatCssVariable(variableName: String, color: Int): String {
            val formattedColor = colorToHexRepresentation(color)
            return "$variableName: $formattedColor;\n"
        }

        private fun colorToHexRepresentation(color: Int) = "#" + color.toHexString().substring(2 until 8)

        fun Context.getCustomDarkMode(): String = loadCss(R.raw.custom_dark_mode)

        fun Context.getSetMargin(): String = loadCss(R.raw.set_margin_around_mail)

        fun Context.getCustomStyle(): String = loadCss(
            R.raw.custom_style,
            listOf(PRIMARY_COLOR_CODE to getAttributeColor(RMaterial.attr.colorPrimary)),
        )
    }
}
