/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment

object AttachmentIntentUtils {
    private const val DRIVE_PACKAGE = "com.infomaniak.drive"
    private const val DRIVE_CLASS = "com.infomaniak.drive.ui.SaveExternalFilesActivity"

    const val DOWNLOAD_ATTACHMENT_RESULT = "download_attachment_result"

    fun Attachment.saveToDriveIntent(context: Context): Intent {
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.ATTACHMENTS_AUTHORITY), getCacheFile(context))
        return Intent().apply {
            component = ComponentName(DRIVE_PACKAGE, DRIVE_CLASS)
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            setDataAndType(uri, safeMimeType)
        }
    }

    fun Attachment.openWithIntent(context: Context): Intent {
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.ATTACHMENTS_AUTHORITY), getCacheFile(context))
        return Intent().apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            setDataAndType(uri, safeMimeType)
        }
    }

    enum class AttachmentIntent {
        OPEN_WITH, SAVE_TO_DRIVE
    }
}
