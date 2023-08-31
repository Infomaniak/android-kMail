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

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCustomDarkMode
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCustomStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getFixStyleScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getImproveRenderingStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getJsBridgeScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getResizeScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getSignatureMarginStyle

class WebViewUtils(context: Context) {

    private val customDarkMode by lazy { context.getCustomDarkMode() }
    private val improveRenderingStyle by lazy { context.getImproveRenderingStyle() }
    private val customStyle by lazy { context.getCustomStyle() }
    private val signatureVerticalMargin by lazy { context.getSignatureMarginStyle() }

    private val resizeScript by lazy { context.getResizeScript() }
    private val fixStyleScript by lazy { context.getFixStyleScript() }
    private val jsBridgeScript by lazy { context.getJsBridgeScript() }

    fun processHtmlForDisplay(html: String, isDisplayedInDarkMode: Boolean): String = with(HtmlFormatter(html)) {
        addCommonDisplayContent(isDisplayedInDarkMode)
        return@with inject()
    }

    fun processSignatureHtmlForDisplay(html: String, isDisplayedInDarkMode: Boolean): String = with(HtmlFormatter(html)) {
        addCommonDisplayContent(isDisplayedInDarkMode)
        registerCss(signatureVerticalMargin)
        return@with inject()
    }

    private fun HtmlFormatter.addCommonDisplayContent(isDisplayedInDarkMode: Boolean) {
        if (isDisplayedInDarkMode) registerCss(customDarkMode, DARK_BACKGROUND_STYLE_ID)
        registerCss(improveRenderingStyle)
        registerCss(customStyle)
        registerMetaViewPort()
        registerScript(resizeScript)
        registerScript(fixStyleScript)
        registerScript(jsBridgeScript)
        registerBodyEncapsulation()
    }

    class JavascriptBridge {

        @JavascriptInterface
        fun reportOverScroll(clientWidth: Int, scrollWidth: Int, messageUid: String) {
            SentryDebug.sendOverScrolledMessage(clientWidth, scrollWidth, messageUid)
        }

        @JavascriptInterface
        fun reportError(
            errorName: String,
            errorMessage: String,
            errorStack: String,
            scriptFirstLine: String,
            messageUid: String,
        ) {
            val correctErrorStack = fixStackTraceLineNumber(errorStack, scriptFirstLine)
            SentryDebug.sendJavaScriptError(errorName, errorMessage, correctErrorStack, messageUid)
        }

        private fun fixStackTraceLineNumber(errorStack: String, scriptFirstLine: String): String {
            var correctErrorStack = errorStack
            val matches = "about:blank:([0-9]+):".toRegex().findAll(correctErrorStack)
            matches.forEach { match ->
                val lineNumber = match.groupValues[1]
                val newLineNumber = lineNumber.toInt() - scriptFirstLine.toInt() + 1
                correctErrorStack = correctErrorStack.replace(match.groupValues[0], "about:blank:$newLineNumber:")
            }
            return correctErrorStack
        }
    }

    companion object {
        private const val DARK_BACKGROUND_STYLE_ID = "dark_background_style"
        val jsBridge = JavascriptBridge() // TODO: Avoid excessive memory consumption with injection

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
            @SuppressLint("SetJavaScriptEnabled")
            javaScriptEnabled = true

            loadWithOverviewMode = true
            useWideViewPort = true
        }

        fun WebView.toggleWebViewTheme(isThemeTheSame: Boolean) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isThemeTheSame)
            }

            if (isThemeTheSame) addBackgroundJs() else removeBackgroundJs()
        }

        private fun WebView.addBackgroundJs() {
            val css = context.readRawResource(R.raw.custom_dark_mode)
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
