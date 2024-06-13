/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.AccountUtils
import java.io.File

interface Attachable {

    var size: Long
    var name: String
    var mimeType: String
    var resource: String?
    var localUuid: String

    val downloadUrl get() = ""

    val safeMimeType get() = ""

    fun getFileTypeFromMimeType(): AttachmentType

    fun hasUsableCache(
        context: Context,
        file: File? = null,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): Boolean

    fun isInlineCachedFile(context: Context): Boolean

    fun getCacheFile(
        context: Context,
        userId: Int = AccountUtils.currentUserId,
        mailboxId: Int = AccountUtils.currentMailboxId,
    ): File

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
