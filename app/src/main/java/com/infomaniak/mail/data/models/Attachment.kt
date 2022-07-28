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
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.KMailHttpClient
import io.realm.kotlin.UpdatePolicy
import androidx.annotation.DrawableRes
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.R
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.File

// @RealmClass(embedded = true) // TODO: https://github.com/realm/realm-kotlin/issues/551
@Serializable
class Attachment : RealmObject {
    @PrimaryKey // TODO: Remove `@PrimaryKey` when we have EmbeddedObjects
    var uuid: String = ""
    @SerialName("mime_type")
    var mimeType: String = ""
    var encoding: String = ""
    var size: Int = 0
    var name: String = ""
    private var disposition: String? = null
    @SerialName("content_id")
    var contentId: String? = null
    var resource: String = ""
    @SerialName("drive_url")
    var driveUrl: String? = null
    var localUri: String = ""
    var thumbnail: String = ""

    // TODO: Remove this method when we have EmbeddedObjects
    fun initLocalValues(position: Int, parentMessageUid: String) {
        uuid = "attachment_${position}_${parentMessageUid}"
    }

    fun getDisposition(): AttachmentDisposition? = enumValueOfOrNull<AttachmentDisposition>(disposition)

    override fun equals(other: Any?): Boolean = other is Attachment && other.uuid == uuid

    override fun hashCode(): Int = uuid.hashCode()

    enum class AttachmentDisposition {
        INLINE,
        ATTACHMENT,
    }

    enum class AttachmentType(@DrawableRes val icon: Int) {
        ARCHIVE(R.drawable.ic_file_zip),
        AUDIO(R.drawable.ic_file_audio),
        IMAGE(R.drawable.ic_file_image),
        PDF(R.drawable.ic_file_pdf),
        SPREADSHEET(R.drawable.ic_file_office_sheet),
        TEXT(R.drawable.ic_file_text),
        VIDEO(R.drawable.ic_file_video),
        UNKNOWN(R.drawable.ic_file_unknown),
        BOOK(R.drawable.ic_file_single_neutral_book),
        GRAPH(R.drawable.ic_file_office_graph),
    }

    // TODO: Use this, and move it elsewhere.
    suspend fun fetchAttachment(attachment: Attachment, cacheDir: File) {

        fun downloadAttachmentData(fileUrl: String, okHttpClient: OkHttpClient): Response {
            val request = Request.Builder().url(fileUrl).headers(HttpUtils.getHeaders(contentType = null)).get().build()
            return okHttpClient.newBuilder().build().newCall(request).execute()
        }

        fun saveAttachmentData(response: Response, outputFile: File, onFinish: (() -> Unit)) {
            Log.d("TAG", "Save remote data to ${outputFile.path}")
            BufferedInputStream(response.body?.byteStream()).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                    onFinish()
                }
            }
        }

        val response = downloadAttachmentData(
            fileUrl = ApiRoutes.resource(attachment.resource),
            okHttpClient = KMailHttpClient.getHttpClient(AccountUtils.currentUserId),
        )

        val file = File(cacheDir, "${attachment.uuid}_${attachment.name}")

        saveAttachmentData(response, file) {
            attachment.localUri = file.toURI().toString()
            RealmController.mailboxContent.writeBlocking { copyToRealm(attachment, UpdatePolicy.ALL) }
        }
    }
}
