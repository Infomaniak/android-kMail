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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import okhttp3.Request

open class MessageWebViewClient(val context: Context, val attachments: List<Attachment>) : WebViewClient() {
    private val cidDictionary = attachments.associateBy { it.contentId }
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val scheme = request?.url?.scheme
        if (scheme != CID_SCHEME) return super.shouldInterceptRequest(view, request)

        val cid = request.url.schemeSpecificPart
        cidDictionary[cid]?.resource?.let { attachmentResource ->
            val resourceUrl = "${BuildConfig.MAIL_API}${attachmentResource}"
            val httpRequest = Request.Builder().url(resourceUrl).build()
            val response = HttpClient.okHttpClient.newCall(httpRequest).execute()
            return WebResourceResponse(
                null,
                response.header("content-encoding", "utf-8"),
                response.body!!.byteStream()
            )
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        request?.url?.let {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(it.toString())
            runCatching {
                context.startActivity(intent)
            }.onFailure {
                context.showToast(R.string.webViewCantHandleAction)
            }
        }
        return true
    }

    companion object {
        const val CID_SCHEME = "cid"
    }
}
