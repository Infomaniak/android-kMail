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
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.Utils
import okhttp3.Request
import java.io.File

class MessageWebViewClient(
    private val context: Context,
    private val cidDictionary: MutableMap<String, Attachment>
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {

        if (request?.url?.scheme == CID_SCHEME) {
            val cid = request.url.schemeSpecificPart
            cidDictionary[cid]?.let { attachment ->
                val cacheFile = attachment.getCacheFile(context)
                if (!attachment.hasUsableCache(context, cacheFile)) {
                    Log.d(TAG, "shouldInterceptRequest: cache ${attachment.name} with ${attachment.size}")
                    saveAttachmentToCache(attachment.resource!!, cacheFile)
                }
                Log.i(TAG, "shouldInterceptRequest: load attachment ${attachment.name} from cache with ${cacheFile.length()}")
                return WebResourceResponse(attachment.mimeType, Utils.UTF_8, cacheFile.inputStream())
            }
        }

        return super.shouldInterceptRequest(view, request)
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

    private fun saveAttachmentToCache(resource: String, cacheFile: File) {
        val resourceUrl = "${BuildConfig.MAIL_API}${resource}"
        val httpRequest = Request.Builder().url(resourceUrl).build()
        val response = HttpClient.okHttpClient.newCall(httpRequest).execute()
        if (response.isSuccessful) {
            LocalStorageUtils.saveCacheAttachment(resource, response.body!!.byteStream(), cacheFile)
        }
    }

    private companion object {
        val TAG = MessageWebViewClient::class.simpleName
        const val CID_SCHEME = "cid"
    }
}
