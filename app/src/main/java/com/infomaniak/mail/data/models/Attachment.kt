/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.data.models

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.KMailHttpClient
import io.realm.MutableRealm.UpdatePolicy
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.use
import java.io.BufferedInputStream
import java.io.File

// @RealmClass(embedded = true) // TODO: https://github.com/realm/realm-kotlin/issues/551
class Attachment : RealmObject {
    @PrimaryKey // TODO: Remove `@PrimaryKey` when we have EmbeddedObjects
    var uuid: String = ""

    @SerializedName("part_id")
    var partId: String = ""

    @SerializedName("mime_type")
    var mimeType: String = ""
    var encoding: String = ""
    var size: Int = 0
    var name: String = ""

    @SerializedName("disposition")
    private var disposition: String? = null

    @SerializedName("content_id")
    var contentId: String = ""
    var resource: String = ""

    @SerializedName("drive_url")
    var driveUrl: String = ""
    var localUri: String = ""
    var thumbnail: String = ""

    // TODO: Remove this method when we have EmbeddedObjects
    fun initLocalValues(position: Int, parentMessageUid: String): Attachment {
        uuid = "attachment_${position}_${parentMessageUid}"

        return this
    }

    suspend fun getAttachmentData(cacheDir: File) {
        val response = downloadAttachmentData(
            fileUrl = ApiRoutes.resource(resource),
            okHttpClient = KMailHttpClient.getHttpClient(AccountUtils.currentUserId),
        )

        val file = File(cacheDir, "${uuid}_${name}")

        saveAttachmentData(response, file) {
            localUri = file.toURI().toString()
            MailRealm.mailboxContent.writeBlocking { copyToRealm(this@Attachment, UpdatePolicy.ALL) }
        }
    }

    private fun downloadAttachmentData(fileUrl: String, okHttpClient: OkHttpClient = HttpClient.okHttpClient): Response {
        val request = Request.Builder().url(fileUrl).headers(HttpUtils.getHeaders(contentType = null)).get().build()
        return okHttpClient.newBuilder().build().newCall(request).execute()
    }

    private fun saveAttachmentData(response: Response, outputFile: File, onFinish: (() -> Unit)) {
        Log.d("TAG", "save remote data to ${outputFile.path}")
        BufferedInputStream(response.body?.byteStream()).use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
                onFinish()
            }
        }
    }

    fun getDisposition(): AttachmentDisposition? = when (disposition) {
        AttachmentDisposition.INLINE.name -> AttachmentDisposition.INLINE
        AttachmentDisposition.ATTACHMENT.name -> AttachmentDisposition.ATTACHMENT
        else -> null
    }

    enum class AttachmentDisposition {
        INLINE,
        ATTACHMENT,
    }
}
