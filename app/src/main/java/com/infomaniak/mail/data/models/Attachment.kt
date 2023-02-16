/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.core.content.FileProvider
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.lib.core.utils.contains
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File

@Serializable
class Attachment : EmbeddedRealmObject {

    var uuid: String = ""
    @SerialName("part_id")
    var partId: String = ""
    @SerialName("mime_type")
    var mimeType: String = ""
    var encoding: String? = null
    var size: Long = 0L
    var name: String = ""
    @SerialName("disposition")
    private var _disposition: String? = null
    @SerialName("content_id")
    var contentId: String? = null
    var resource: String? = null
    @SerialName("drive_url")
    var driveUrl: String? = null

    //region Local data (Transient)
    @Transient
    var uploadLocalUri: String? = null
    //endregion

    val disposition: AttachmentDisposition?
        get() = enumValueOfOrNull<AttachmentDisposition>(_disposition)

    fun initLocalValues(name: String, size: Long, mimeType: String, uri: String) {
        this.name = name
        this.size = size
        this.mimeType = mimeType
        this.uploadLocalUri = uri
    }

    fun getFileTypeFromExtension(): AttachmentType = when (mimeType) {
        in Regex("application/(zip|rar|x-tar|.*compressed|.*archive)") -> AttachmentType.ARCHIVE
        in Regex("audio/") -> AttachmentType.AUDIO
        in Regex("image/") -> AttachmentType.IMAGE
        in Regex("/pdf") -> AttachmentType.PDF
        in Regex("spreadsheet|excel|comma-separated-values") -> AttachmentType.SPREADSHEET
        in Regex("document|text/plain|msword") -> AttachmentType.TEXT
        in Regex("video/") -> AttachmentType.VIDEO
        else -> AttachmentType.UNKNOWN
    }

    fun getCacheFile(
        context: Context,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        val cacheFolder = LocalStorageUtils.getAttachmentsCacheDir(context, userId, mailboxId)
        return File(cacheFolder, name)
    }

    fun getUploadLocalFile(
        context: Context, localDraftUuid: String,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        val cacheFolder = LocalStorageUtils.getAttachmentsUploadDir(context, localDraftUuid, userId, mailboxId)
        return File(cacheFolder, name)
    }

    fun openWithIntent(context: Context): Intent {
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.ATTACHMENTS_AUTHORITY), getCacheFile(context))
        return Intent().apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            setDataAndType(uri, mimeType)
        }
    }

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
    // suspend fun fetchAttachment(attachment: Attachment, cacheDir: File) {
    //
    //     fun downloadAttachmentData(fileUrl: String, okHttpClient: OkHttpClient): Response {
    //         val request = Request.Builder().url(fileUrl).headers(HttpUtils.getHeaders(contentType = null)).get().build()
    //         return okHttpClient.newBuilder().build().newCall(request).execute()
    //     }
    //
    //     fun saveAttachmentData(response: Response, outputFile: File, onFinish: (() -> Unit)) {
    //         Log.d("TAG", "Save remote data to ${outputFile.path}")
    //         BufferedInputStream(response.body?.byteStream()).use { input ->
    //             outputFile.outputStream().use { output ->
    //                 input.copyTo(output)
    //                 onFinish()
    //             }
    //         }
    //     }
    //
    //     val response = downloadAttachmentData(
    //         fileUrl = ApiRoutes.resource(attachment.resource),
    //         okHttpClient = KMailHttpClient.getHttpClient(AccountUtils.currentUserId),
    //     )
    //
    //     val file = File(cacheDir, "${attachment.uuid}_${attachment.name}")
    //
    //     saveAttachmentData(response, file) {
    //         attachment.localUri = file.toURI().toString()
    //         RealmDatabase.mailboxContent().writeBlocking { copyToRealm(attachment, UpdatePolicy.ALL) }
    //     }
    // }
}
