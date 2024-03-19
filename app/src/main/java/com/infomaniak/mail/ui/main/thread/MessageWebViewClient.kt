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
package com.infomaniak.mail.ui.main.thread

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.lib.core.utils.toDp
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import java.io.ByteArrayInputStream

class MessageWebViewClient(
    private val context: Context,
    private val cidDictionary: MutableMap<String, Attachment>,
    private val messageUid: String,
    private var shouldLoadDistantResources: Boolean,
    private val onBlockedResourcesDetected: (() -> Unit)? = null,
    private val navigateToNewMessageActivity: ((Uri) -> Unit)?,
    private val onPageFinished: (() -> Unit)? = null,
) : WebViewClient() {

    private val emptyResource by lazy { WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = runCatchingRealm {
        val url = request.url

        if (url?.scheme.equals(CID_SCHEME, ignoreCase = true)) {
            val cid = url.schemeSpecificPart
            return cidDictionary[cid]?.let { attachment ->
                val cacheFile = attachment.getCacheFile(context)

                val data = if (attachment.hasUsableCache(context, cacheFile)) {
                    cacheFile.inputStream()
                } else {
                    runCatching {
                        val resource = attachment.resource ?: return super.shouldInterceptRequest(view, request)
                        ApiRepository.downloadAttachment(resource)
                    }.getOrNull()?.body?.byteStream()?.readBytes()?.let {
                        LocalStorageUtils.saveAttachmentToCache(it.inputStream(), cacheFile)
                        it.inputStream()
                    }
                }

                WebResourceResponse(attachment.mimeType, Utils.UTF_8, data)
            } ?: emptyResource
        }

        val shouldLoadResource = shouldLoadDistantResources
                || url?.scheme.equals(DATA_SCHEME, ignoreCase = true)
                || trustedUrls.any { it.find(url.toString()) != null }

        return if (shouldLoadResource) {
            super.shouldInterceptRequest(view, request)
        } else {
            onBlockedResourcesDetected?.invoke()
            emptyResource
        }
    }.getOrDefault(super.shouldInterceptRequest(view, request))

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        request.url?.let { uri ->

            if (uri.scheme == "mailto") {
                navigateToNewMessageActivity?.invoke(uri)
                return true
            }

            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }.onFailure {
                context.showToast(R.string.startActivityCantHandleAction)
            }
        }
        return true
    }

    override fun onPageFinished(webView: WebView, url: String?) {
        runCatchingRealm {
            webView.loadUrl("javascript:removeAllProperties(); normalizeMessageWidth(${webView.width.toDp()}, '$messageUid')")
            onPageFinished?.invoke()
        }
    }

    fun unblockDistantResources() {
        shouldLoadDistantResources = true
    }

    companion object {
        val TAG = MessageWebViewClient::class.simpleName

        const val CID_SCHEME = "cid"
        const val DATA_SCHEME = "data"

        val trustedUrls = listOf(
            "https://.*?.infomaniak.com".toRegex(),
            "https://.*?.storage.infomaniak.com".toRegex(),
            "https://storage-master.infomaniak.ch".toRegex(),
            "http://infomaniak.statslive.info".toRegex(),
            "https://static.infomaniak.ch".toRegex(),
        )
    }
}
