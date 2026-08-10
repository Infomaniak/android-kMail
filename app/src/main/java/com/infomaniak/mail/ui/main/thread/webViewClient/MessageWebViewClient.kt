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
import com.infomaniak.mail.data.models.AttachmentDisposition
import com.infomaniak.mail.data.models.extensions.getCacheFile
import com.infomaniak.mail.data.models.extensions.getInlineCacheFile
import com.infomaniak.mail.data.models.extensions.hasUsableCache
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File

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
            val attachment = cidDictionary[cid] ?: return@runCatchingRealm emptyResource

            val cacheFile = getCacheFile(attachment)

            val data = if (attachment.hasUsableCache(context, cacheFile)) {
                cacheFile.inputStream()
            } else {
                val resource = attachment.resource ?: return super.shouldInterceptRequest(view, request)

                runCatching {
                    runBlocking { ApiRepository.downloadAttachment(resource) }
                }.getOrNull()?.body?.byteStream()?.readBytes()?.let { bytes ->
                    LocalStorageUtils.saveAttachmentToCacheDir(bytes.inputStream(), cacheFile)
                    bytes.inputStream()
                }
            }

            return WebResourceResponse(attachment.mimeType, Utils.UTF_8, data)
        }

        return if (shouldLoadResource(url)) {
            super.shouldInterceptRequest(view, request)
        } else {
            onBlockedResourcesDetected?.invoke()
            emptyResource
        }
    }.getOrDefault(super.shouldInterceptRequest(view, request))

    private fun getCacheFile(attachment: Attachment): File {
        return if (attachment.disposition == AttachmentDisposition.INLINE) {
            attachment.getInlineCacheFile(context).takeIf { it.exists() } ?: attachment.getCacheFile(context)
        } else {
            attachment.getCacheFile(context)
        }
    }

    private fun shouldLoadResource(url: Uri): Boolean {
        return _shouldLoadDistantResources
                || url.scheme.equals(DATA_SCHEME, ignoreCase = true)
                || url.isTrustedRemoteResource()
    }

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
