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
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.ViewParent
import android.webkit.WebSettings
import android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
import android.webkit.WebView
import com.infomaniak.lib.richhtmleditor.looselyEscapeAsStringLiteralForJs
import com.infomaniak.mail.data.models.javascriptBridge.MessageDisplayJavascriptBridge
import com.infomaniak.mail.ui.newMessage.NewMessageFragment.Companion.MENTIONS_STYLE
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCustomDarkMode
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getCustomStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getFixStyleScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getImproveRenderingStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getMentionClickHandlerScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getMentionsStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getMessageDisplayJavascriptBridge
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getMessageDisplayStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getPrintMailStyle
import com.infomaniak.mail.utils.HtmlFormatter.Companion.getResizeScript
import com.infomaniak.mail.utils.HtmlFormatter.Companion.withMode
import com.infomaniak.mail.utils.extensions.enableAlgorithmicDarkening
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

class WebViewUtils(private val context: Context) {

    private val customDarkMode by lazy { context.getCustomDarkMode() }
    private val improveRenderingStyle by lazy { context.getImproveRenderingStyle() }
    private val customStyle by lazy { context.getCustomStyle() }
    private val messageDisplayStyle by lazy { context.getMessageDisplayStyle() }
    private val printMailStyle by lazy { context.getPrintMailStyle() }
    private val resizeScript by lazy { context.getResizeScript() }
    private val fixStyleScript by lazy { context.getFixStyleScript() }
    private val mentionClickHandlerScript by lazy { context.getMentionClickHandlerScript() }
    private val messageDisplayJsBridgeScript by lazy { context.getMessageDisplayJavascriptBridge() }

    fun processHtmlForPrint(
        html: String,
        printData: HtmlFormatter.PrintData,
        aliases: List<String>,
    ): String = with(HtmlFormatter(html)) {
        addCommonDisplayContent(isDisplayedInDarkMode = false, aliases)
        registerIsForPrint(printData)
        registerCss(printMailStyle)
        return@with inject()
    }

    fun processHtmlForDisplay(
        html: String,
        isDisplayedInDarkMode: Boolean,
        aliases: List<String>,
    ): String = with(HtmlFormatter(html)) {
        addCommonDisplayContent(isDisplayedInDarkMode, aliases)
        registerScript(mentionClickHandlerScript)
        return@with inject()
    }

    private fun HtmlFormatter.addCommonDisplayContent(isDisplayedInDarkMode: Boolean, aliases: List<String>) {
        registerScript(messageDisplayJsBridgeScript)
        registerCss(improveRenderingStyle)
        registerCss(customStyle)
        registerCss(messageDisplayStyle)
        registerCss(getDynamicMentionsStyle(context, aliases, isDisplayedInDarkMode))
        if (isDisplayedInDarkMode) registerCss(customDarkMode, DARK_BACKGROUND_STYLE_ID)
        registerMetaViewPort()
        registerScript(resizeScript)
        registerScript(fixStyleScript)
        registerBodyEncapsulation()
        registerBreakLongWords()
    }

    companion object {
        private const val DARK_BACKGROUND_STYLE_ID = "dark_background_style"

        lateinit var messageDisplayJsBridge: MessageDisplayJavascriptBridge // TODO: Avoid excessive memory consumption with injection

        fun initMessageDisplayJavascriptBridge(
            onWebViewFinishedLoading: () -> Unit,
            onMentionContactClicked: ((String, String?) -> Unit)? = null,
        ) {
            messageDisplayJsBridge = MessageDisplayJavascriptBridge(
                onWebViewFinishedLoading = onWebViewFinishedLoading,
                onMentionContactClicked = onMentionContactClicked,
            )
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

        fun getDynamicMentionsStyle(context: Context, aliases: List<String>, isDisplayedInDarkMode: Boolean): String {
            val uiMode = if (isDisplayedInDarkMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val contextWithTheme = context.withMode(uiMode)
            return contextWithTheme.getMentionsStyle(aliases)
        }

        fun WebView.toggleWebViewTheme(isThemeTheSame: Boolean, aliases: List<String>) {
            enableAlgorithmicDarkening(isThemeTheSame)
            if (isThemeTheSame) addBackgroundJs() else removeBackgroundJs()

            val mentionsCss = getDynamicMentionsStyle(context, aliases, isThemeTheSame)
            addCss(mentionsCss, MENTIONS_STYLE)
        }

        private fun WebView.addBackgroundJs() {
            val customDarkMode = context.getCustomDarkMode()
            addCss(customDarkMode, DARK_BACKGROUND_STYLE_ID)
        }

        fun WebView.addCss(css: String, id: String? = null) {
            val escapedStringLiteralId = id?.let { looselyEscapeAsStringLiteralForJs(it) }
            val removePreviousIdScript = getRemovePreviousElementByIdScript(escapedStringLiteralId)
            val setId = escapedStringLiteralId?.let { "style.id = ${it};" } ?: ""

            val escapedStringLiteralCss = looselyEscapeAsStringLiteralForJs(css)
            val addCssJs = """
                var style = document.createElement('style');
                style.textContent = $escapedStringLiteralCss;
                $setId
        
                document.head.appendChild(style);
                """.trimIndent()

            val code = removePreviousIdScript + "\n" + addCssJs

            evaluateJavascript(code, null)
        }

        private fun getRemovePreviousElementByIdScript(escapedId: String?): String = escapedId?.let {
            """
            var previousElement = document.getElementById($it)
            if (previousElement) previousElement.remove()
            """.trimIndent()
        } ?: ""

        private fun WebView.removeBackgroundJs() {
            val escapedId = looselyEscapeAsStringLiteralForJs(DARK_BACKGROUND_STYLE_ID)
            val removeBackgroundStyleScript = getRemovePreviousElementByIdScript(escapedId)
            evaluateJavascript(removeBackgroundStyleScript, null)
        }

        suspend fun WebView.evaluateJs(script: String): String = suspendCancellableCoroutine { continuation ->
            evaluateJavascript(script) {
                continuation.resume(it)
            }
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
