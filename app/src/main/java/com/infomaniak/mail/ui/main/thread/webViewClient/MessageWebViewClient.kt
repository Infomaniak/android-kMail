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
package com.infomaniak.mail.ui.main.thread.webViewClient

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.extensions.getCacheFile
import com.infomaniak.mail.data.models.extensions.hasUsableCache
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

abstract class MessageWebViewClient(
    private val context: Context,
    private val cidDictionary: Map<String, Attachment>,
    private var _shouldLoadDistantResources: Boolean,
    private val onBlockedResourcesDetected: (() -> Unit)? = null,
) : WebViewClient() {
    val shouldLoadDistantResources get() = _shouldLoadDistantResources

    private val emptyResource by lazy { WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = runCatchingRealm {
        val url = request.url

        if (url?.scheme.equals(CID_SCHEME, ignoreCase = true)) {
            val cid = url.schemeSpecificPart
            return cidDictionary[cid]?.let { attachment ->
                val (cacheFile, hasUsableCache) = runBlocking {
                    val file = attachment.getCacheFile(context)
                    return@runBlocking file to attachment.hasUsableCache(context, file)
                }

                if (cacheFile == null) return@runCatchingRealm null

                val data = if (hasUsableCache) {
                    cacheFile.inputStream()
                } else {
                    runCatching {
                        val resource = attachment.resource ?: return super.shouldInterceptRequest(view, request)
                        runBlocking { ApiRepository.downloadAttachment(resource) }
                    }.getOrNull()?.body?.byteStream()?.readBytes()?.let {
                        LocalStorageUtils.saveAttachmentToCacheDir(it.inputStream(), cacheFile)
                        it.inputStream()
                    }
                }

                WebResourceResponse(attachment.mimeType, Utils.UTF_8, data)
            } ?: emptyResource
        }

        val shouldLoadResource = _shouldLoadDistantResources
                || url?.scheme.equals(DATA_SCHEME, ignoreCase = true)
                || url.isTrustedRemoteResource()

        return if (shouldLoadResource) {
            super.shouldInterceptRequest(view, request)
        } else {
            onBlockedResourcesDetected?.invoke()
            emptyResource
        }
    }.getOrDefault(super.shouldInterceptRequest(view, request))

    fun unblockDistantResources() {
        _shouldLoadDistantResources = true
    }

    companion object {
        val TAG = MessageWebViewClient::class.simpleName

        const val CID_SCHEME = "cid"
        const val DATA_SCHEME = "data"

        fun Uri.isTrustedRemoteResource(): Boolean {
            val normalizedHost = host?.lowercase() ?: return false
            if (scheme.equals("http", ignoreCase = true) && normalizedHost == "infomaniak.statslive.info") return true
            if (!scheme.equals("https", ignoreCase = true)) return false

            return normalizedHost == "static.infomaniak.ch" ||
                    normalizedHost == "storage-master.infomaniak.ch" ||
                    normalizedHost == "infomaniak.com" ||
                    normalizedHost.endsWith(".infomaniak.com") ||
                    normalizedHost == "storage.infomaniak.com" ||
                    normalizedHost.endsWith(".storage.infomaniak.com")
        }
    }
}
