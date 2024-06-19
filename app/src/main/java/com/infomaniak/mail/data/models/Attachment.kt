/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.annotation.DrawableRes
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.lib.core.utils.guessMimeType
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.AttachmentMimeTypeUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.Utils.MIMETYPE_UNKNOWN
import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File
import java.util.UUID

@Serializable
class Attachment : EmbeddedRealmObject {

    //region Remote data
    var uuid: String = ""
    @SerialName("mime_type")
    var mimeType: String = ""
    var size: Long = 0L
    var name: String = ""
    @SerialName("disposition")
    private var _disposition: String? = null
    @SerialName("content_id")
    var contentId: String? = null
    @SerialName("original_content_id")
    var originalContentId: String? = null
    var resource: String? = null
    @SerialName("drive_url")
    var driveUrl: String? = null
    //endregion

    //region Local data (Transient)
    @Transient
    var localUuid: String = UUID.randomUUID().toString()
    @Transient
    var uploadLocalUri: String? = null
    //endregion

    val isCalendarEvent: Boolean get() = AttachmentMimeTypeUtils.calendarMatches.contains(mimeType)

    val disposition: AttachmentDisposition?
        get() = enumValueOfOrNull<AttachmentDisposition>(_disposition)

    inline val downloadUrl get() = ApiRoutes.resource(resource!!)

    inline val safeMimeType get() = if (mimeType == MIMETYPE_UNKNOWN) name.guessMimeType() else mimeType

    fun initLocalValues(
        uri: String?,
        uuid: String? = null,
        name: String? = null,
        size: Long? = null,
        mimeType: String? = null,
    ): Attachment {

        this.uploadLocalUri = uri

        uuid?.let { localUuid = it }
        name?.let { this.name }
        size?.let { this.size = it }
        mimeType?.let { this.mimeType = it }

        return this
    }

    fun getFileTypeFromMimeType(): AttachmentType = AttachmentMimeTypeUtils.getFileTypeFromMimeType(safeMimeType)

    fun hasUsableCache(
        context: Context,
        file: File? = null,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): Boolean {
        val cachedFile = file ?: getCacheFile(context, userId, mailboxId)
        return cachedFile.length() > 0 && cachedFile.canRead()
    }

    fun isInlineCachedFile(context: Context): Boolean {
        return getCacheFile(context).exists() && disposition == AttachmentDisposition.INLINE
    }

    fun getCacheFile(
        context: Context,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File {
        val cacheFolder = LocalStorageUtils.getAttachmentsCacheDir(context, extractPathFromResource(), userId, mailboxId)
        return File(cacheFolder, name)
    }

    private fun extractPathFromResource(): String {
        return resource?.substringAfter("folder/")?.replace(Regex("(message|attachment)/"), "") ?: ""
    }

    fun getUploadLocalFile() = uploadLocalUri?.toUri()?.toFile()

    enum class AttachmentDisposition {
        INLINE,
        ATTACHMENT,
    }

    enum class AttachmentType(@DrawableRes val icon: Int) {
        ARCHIVE(R.drawable.ic_file_zip),
        AUDIO(R.drawable.ic_file_audio),
        CALENDAR(R.drawable.ic_file_calendar),
        CODE(R.drawable.ic_file_code),
        FONT(R.drawable.ic_file_font),
        IMAGE(R.drawable.ic_file_image),
        PDF(R.drawable.ic_file_pdf),
        POINTS(R.drawable.ic_file_office_graph),
        SPREADSHEET(R.drawable.ic_file_office_sheet),
        TEXT(R.drawable.ic_file_text),
        VCARD(R.drawable.ic_file_vcard),
        VIDEO(R.drawable.ic_file_video),
        UNKNOWN(R.drawable.ic_file_unknown),
    }
}
