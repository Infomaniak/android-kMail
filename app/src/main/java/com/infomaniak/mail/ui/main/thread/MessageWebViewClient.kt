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
package com.infomaniak.mail.ui.main.thread

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import java.io.ByteArrayInputStream

class MessageWebViewClient(
    private val context: Context,
    private val cidDictionary: MutableMap<String, Attachment>,
    private val messageUid: String,
    private var shouldLoadDistantResources: Boolean,
    private val onBlockedResourcesDetected: () -> Unit,
) : WebViewClient() {

    // var blockedResourceCount = 0
    private val emptyResource by lazy { WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        Log.e("gibran", "shouldInterceptRequest - request?.url: ${request?.url}")
        
        if (request?.url?.scheme.equals(CID_SCHEME, ignoreCase = true)) {
            val cid = request!!.url.schemeSpecificPart
            cidDictionary[cid]?.let { attachment ->
                val cacheFile = attachment.getCacheFile(context)

                val data = if (attachment.hasUsableCache(context, cacheFile)) {
                    Log.d(TAG, "shouldInterceptRequest: load ${attachment.name} from local")
                    cacheFile.inputStream()
                } else {
                    Log.d(TAG, "shouldInterceptRequest: load ${attachment.name} from remote")
                    runCatching {
                        val resource = attachment.resource ?: return super.shouldInterceptRequest(view, request)
                        ApiRepository.downloadAttachment(resource)
                    }.getOrNull()?.body?.byteStream()?.readBytes()?.let {
                        LocalStorageUtils.saveCacheAttachment(it.inputStream(), cacheFile)
                        it.inputStream()
                    }
                }

                return WebResourceResponse(attachment.mimeType, Utils.UTF_8, data)
            }
        }

        Log.e("gibran", "shouldInterceptRequest: not a CID", );
        return if (shouldLoadDistantResources || request?.url?.scheme.equals(DATA_SCHEME, ignoreCase = true)) {
            Log.e("gibran", "shouldInterceptRequest: loading resource normally or data: detected", );
            super.shouldInterceptRequest(view, request)
        } else {
            Log.e("gibran", "shouldInterceptRequest: blocking resource", );
            onBlockedResourcesDetected()
            emptyResource
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        request?.url?.let {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(it.toString())
            }
            runCatching {
                context.startActivity(intent)
            }.onFailure {
                context.showToast(R.string.webViewCantHandleAction)
            }
        }
        return true
    }

    override fun onPageFinished(webView: WebView, url: String?) {
        webView.loadUrl("javascript:removeAllProperties(); normalizeMessageWidth(${webView.width.toDp()}, '$messageUid')")
        super.onPageFinished(webView, url)
    }

    fun unblockDistantResources() {
        shouldLoadDistantResources = true
    }

    companion object {
        val TAG = MessageWebViewClient::class.simpleName

        const val CID_SCHEME = "cid"
        const val DATA_SCHEME = "data"
    }
}
