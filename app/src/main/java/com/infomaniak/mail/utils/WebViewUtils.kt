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

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCustomDarkMode
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCustomStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getResizeScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getSetMargin

class WebViewUtils(context: Context) {

    private val customDarkMode by lazy { context.getCustomDarkMode() }
    private val setMargin by lazy { context.getSetMargin() }
    private val customStyle by lazy { context.getCustomStyle() }

    private val resizeScript by lazy { context.getResizeScript() }

    fun processHtmlForDisplay(html: String, isDisplayedInDarkMode: Boolean): String = with(HtmlFormatter(html)) {
        if (isDisplayedInDarkMode) registerCss(customDarkMode, DARK_BACKGROUND_STYLE_ID)
        registerCss(setMargin)
        registerCss(customStyle)
        registerMetaViewPort()
        registerScript(resizeScript)
        registerBodyEncapsulation()
        return@with inject()
    }

    companion object {

        private const val DARK_BACKGROUND_STYLE_ID = "dark_background_style"

        fun WebSettings.setupThreadWebViewSettings() {
            @SuppressLint("SetJavaScriptEnabled")
            javaScriptEnabled = true

            loadWithOverviewMode = true
            useWideViewPort = true

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        fun WebSettings.setupNewMessageWebViewSettings() {
            useWideViewPort = true
            loadWithOverviewMode = true
        }
    }
}
