/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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
import android.view.MotionEvent
import android.view.ViewParent
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
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getJsBridgeScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getMessageDisplayStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getPrintMailStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getResizeScript
import com.infomaniak.mail.utils.extensions.enableAlgorithmicDarkening
import com.infomaniak.mail.utils.extensions.loadCss
import kotlin.math.abs

class WebViewUtils(context: Context) {

    private val customDarkMode by lazy { context.getCustomDarkMode() }
    private val improveRenderingStyle by lazy { context.getImproveRenderingStyle() }
    private val customStyle by lazy { context.getCustomStyle() }
    private val messageDisplayStyle by lazy { context.getMessageDisplayStyle() }
    private val printMailStyle by lazy { context.getPrintMailStyle() }

    private val resizeScript by lazy { context.getResizeScript() }
    private val fixStyleScript by lazy { context.getFixStyleScript() }
    private val jsBridgeScript by lazy { context.getJsBridgeScript() }

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
        registerScript(jsBridgeScript)
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

        fun initEditorJsBridge(onImagesDeletedFromQuotes: (List<String>) -> Unit) {
            editorJsBridge = EditorJavascriptBridge(onImagesDeletedFromQuotes = onImagesDeletedFromQuotes)
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

        /**
         * Lets the WebView take the zoom gestures and lets the WebView handle scrolls that start horizontally while letting the
         * parent handle scrolls that start vertically. This is not perfect but the best solution is way more complex.
         */
        fun WebView.configureOnTouchListener(touchSlop: Int) {
            var startX = 0f
            var startY = 0f
            var isHorizontalGesture: Boolean? = null

            setOnTouchListener { view, event ->
                if (event.pointerCount > 1) {
                    // In the case of a multitouch always let the webview handle the zoom
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    return@setOnTouchListener false // Event not consumed. Let the webview handle the event
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                        isHorizontalGesture = null
                        // Take control so we can decide if we need to keep it or give it up
                        view.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isHorizontalGesture == null) {
                            isHorizontalGesture = computeIsHorizontalGesture(event, startX, startY, touchSlop)
                        }

                        isHorizontalGesture?.let(view.parent::requestDisallowInterceptHorizontalTouchEvent)
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        view.parent.requestDisallowInterceptTouchEvent(false)
                        isHorizontalGesture = null

                        if (event.action == MotionEvent.ACTION_UP) view.performClick()
                    }
                }

                false // Event not consumed. Let the webview handle the event
            }
        }
    }
}

private fun computeIsHorizontalGesture(event: MotionEvent, startX: Float, startY: Float, touchSlop: Int): Boolean? {
    val dx = abs(event.x - startX)
    val dy = abs(event.y - startY)

    return if (dx > touchSlop || dy > touchSlop) dx > dy else null
}

fun ViewParent.requestDisallowInterceptHorizontalTouchEvent(isHorizontalGesture: Boolean) {
    if (isHorizontalGesture) {
        // Horizontal -> WebView keeps control
        requestDisallowInterceptTouchEvent(true)
    } else {
        // Vertical -> Parent takes over
        requestDisallowInterceptTouchEvent(false)
    }
}
