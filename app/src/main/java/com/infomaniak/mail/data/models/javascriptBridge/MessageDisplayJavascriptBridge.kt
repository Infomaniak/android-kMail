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
package com.infomaniak.mail.data.models.javascriptBridge

import android.webkit.JavascriptInterface
import com.infomaniak.mail.utils.SentryDebug

class MessageDisplayJavascriptBridge(
    private val onWebViewFinishedLoading: () -> Unit,
) {

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

    @JavascriptInterface
    fun webviewFinishedLoading() {
        onWebViewFinishedLoading()
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
