/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
import android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
import android.webkit.WebView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.javascriptBridge.EditorJavascriptBridge
import com.infomaniak.mail.data.models.javascriptBridge.MessageDisplayJavascriptBridge
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCustomDarkMode
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCustomStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getFixStyleScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getImproveRenderingStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getMessageDisplayJavascriptBridge
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getMessageDisplayStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getPrintMailStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getResizeScript
import com.infomaniak.mail.utils.extensions.enableAlgorithmicDarkening
import com.infomaniak.mail.utils.extensions.loadCss

class WebViewUtils(context: Context) {

    private val customDarkMode by lazy { context.getCustomDarkMode() }
    private val improveRenderingStyle by lazy { context.getImproveRenderingStyle() }
    private val customStyle by lazy { context.getCustomStyle() }
    private val messageDisplayStyle by lazy { context.getMessageDisplayStyle() }
    private val printMailStyle by lazy { context.getPrintMailStyle() }

    private val resizeScript by lazy { context.getResizeScript() }
    private val fixStyleScript by lazy { context.getFixStyleScript() }
    private val messageDisplayJsBridgeScript by lazy { context.getMessageDisplayJavascriptBridge() }

    fun processHtmlForPrint(
        html: String,
        printData: HtmlFormatter.PrintData,
    ): String = with(HtmlFormatter(html)) {
        addCommonDisplayContent(isDisplayedInDarkMode = false)
        registerIsForPrint(printData)
        registerCss(printMailStyle)
        return@with inject()
    }

    fun processHtmlForDisplay(
        html: String,
        isDisplayedInDarkMode: Boolean,
    ): String = with(HtmlFormatter(html)) {
        addCommonDisplayContent(isDisplayedInDarkMode)
        return@with inject()
    }

    private fun HtmlFormatter.addCommonDisplayContent(isDisplayedInDarkMode: Boolean) {
        if (isDisplayedInDarkMode) registerCss(customDarkMode, DARK_BACKGROUND_STYLE_ID)
        registerCss(improveRenderingStyle)
        registerCss(customStyle)
        registerCss(messageDisplayStyle)
        registerMetaViewPort()
        registerScript(resizeScript)
        registerScript(fixStyleScript)
        registerScript(messageDisplayJsBridgeScript)
        registerBodyEncapsulation()
        registerBreakLongWords()
    }

    companion object {
        private const val DARK_BACKGROUND_STYLE_ID = "dark_background_style"

        lateinit var messageDisplayJsBridge: MessageDisplayJavascriptBridge // TODO: Avoid excessive memory consumption with injection
        lateinit var editorJsBridge: EditorJavascriptBridge

        fun initMessageDisplayJavascriptBridge(onWebViewFinishedLoading: () -> Unit) {
            messageDisplayJsBridge = MessageDisplayJavascriptBridge(onWebViewFinishedLoading = onWebViewFinishedLoading)
        }

        fun initEditorJsBridge(onInlineImagesDeleted: (List<String>) -> Unit) {
            editorJsBridge = EditorJavascriptBridge(onInlineImagesDeleted = onInlineImagesDeleted)
        }

        private fun WebSettings.setupCommonWebViewSettings() {
            @SuppressLint("SetJavaScriptEnabled")
            javaScriptEnabled = true
            cacheMode = LOAD_CACHE_ELSE_NETWORK
        }

        fun WebSettings.setupThreadWebViewSettings() {
            setupCommonWebViewSettings()

            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        fun WebSettings.setupNewMessageWebViewSettings() {
            setupCommonWebViewSettings()
        }

        fun WebView.toggleWebViewTheme(isThemeTheSame: Boolean) {
            enableAlgorithmicDarkening(isThemeTheSame)
            if (isThemeTheSame) addBackgroundJs() else removeBackgroundJs()
        }

        private fun WebView.addBackgroundJs() {
            val css = context.loadCss(R.raw.custom_dark_mode)
            evaluateJavascript(
                """ var style = document.createElement('style')
                document.head.appendChild(style)
                style.id = "$DARK_BACKGROUND_STYLE_ID"
                style.innerHTML = `$css`
            """.trimIndent(),
                null,
            )
        }

        private fun WebView.removeBackgroundJs() {
            val removeBackgroundStyleScript = "document.getElementById(\"$DARK_BACKGROUND_STYLE_ID\").remove()"
            evaluateJavascript(removeBackgroundStyleScript, null)
        }
    }
}
