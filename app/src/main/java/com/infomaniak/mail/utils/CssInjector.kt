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
import androidx.annotation.ColorInt
import androidx.annotation.RawRes
import okhttp3.internal.toHexString
import org.jsoup.Jsoup

class CssInjector(val html: String) {

    private val cssList = mutableListOf<Pair<String, String?>>()

    fun registerCss(css: String, styleId: String? = null) {
        cssList.add(css to styleId)
    }

    fun inject(): String = with(Jsoup.parse(html)) {
        head().apply {
            cssList.forEach { (css, styleId) ->
                appendElement("style")
                    .attr("type", "text/css")
                    .appendText(css)
                    .also { element -> styleId?.let(element::id) }
            }
        }
        html()
    }

    companion object {
        private const val PRIMARY_COLOR_CODE = "\$primaryColor\$"

        fun Context.loadCss(@RawRes cssResId: Int, @ColorInt customColor: Int? = null): String {
            var css = readRawResource(cssResId)

            if (customColor != null) {
                val primaryColor = "#" + customColor.toHexString().substring(2 until 8)
                css = css.replace(PRIMARY_COLOR_CODE, primaryColor)
            }

            return css
        }
    }
}
