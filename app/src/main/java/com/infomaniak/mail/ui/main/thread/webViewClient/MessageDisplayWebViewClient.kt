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
package com.infomaniak.mail.ui.main.thread.webViewClient

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import com.infomaniak.core.ui.view.toDp
import com.infomaniak.lib.richhtmleditor.looselyEscapeAsStringLiteralForJs
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.WebViewVersionUtils
import io.sentry.Sentry
import io.sentry.SentryLevel

class MessageDisplayWebViewClient(
    private val context: Context,
    cidDictionary: MutableMap<String, Attachment>,
    private val messageUid: String,
    private var shouldLoadDistantResources: Boolean,
    onBlockedResourcesDetected: (() -> Unit)? = null,
    navigateToNewMessageActivity: ((Uri) -> Unit)?,
    onPageFinished: (() -> Unit)? = null,
) : MessageWebViewClient(
    context,
    cidDictionary,
    shouldLoadDistantResources,
    onBlockedResourcesDetected,
    navigateToNewMessageActivity,
    onPageFinished,
) {
    override fun onPageFinished(webView: WebView, url: String?) {
        runCatchingRealm {
            val widthInDp = webView.width.toDp(webView)
            if (widthInDp <= 0) {
                val versionData = WebViewVersionUtils.getWebViewVersionData(context)

                Sentry.captureMessage("Zero width webview detected onPageFinished which prevents message width's normalization") { scope ->
                    scope.level = SentryLevel.WARNING
                    scope.setExtra("width", webView.width.toString())
                    scope.setExtra("measuredWidth", webView.measuredWidth.toString())
                    scope.setExtra("height", webView.height.toString())
                    scope.setExtra("measuredHeight", webView.measuredHeight.toString())
                    scope.setTag(
                        "webview version",
                        "${versionData?.webViewPackageName}: ${versionData?.versionName} - ${versionData?.majorVersion}"
                    )
                    scope.setTag("visibility", webView.visibility.toString())
                    scope.setTag("messageUid", messageUid)
                    scope.setTag("shouldLoadDistantResources", shouldLoadDistantResources.toString())
                }
            }
            val escapedMessageUid = looselyEscapeAsStringLiteralForJs(messageUid)
            webView.loadUrl("javascript:removeAllProperties(); normalizeMessageWidth($widthInDp, '$escapedMessageUid')")
            super.onPageFinished(webView, url)
        }
    }
}
