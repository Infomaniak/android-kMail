/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.utils.attachment

import android.content.Context
import android.content.Intent
import com.infomaniak.core.legacy.utils.hasSupportedApplications
import com.infomaniak.mail.data.cache.mailboxContent.AttachmentController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.extensions.getCacheFile
import com.infomaniak.mail.data.models.extensions.getUploadLocalFile
import com.infomaniak.mail.data.models.extensions.hasUsableCache
import com.infomaniak.mail.data.models.extensions.isInlineCachedFile
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.AttachmentExt
import com.infomaniak.mail.utils.extensions.AttachmentExt.getIntentOrGoToAppStore
import com.infomaniak.mail.utils.extensions.AttachmentExt.openWithIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface AttachmentOperations {
    suspend fun hasSupportedApp(attachment: Attachment): Boolean
    suspend fun isCached(attachment: Attachment): Boolean
    suspend fun getOpenIntent(attachment: Attachment): Intent?
    suspend fun download(attachment: Attachment): Boolean
    suspend fun deleteIncompleteCache(attachment: Attachment)
}

class DefaultAttachmentOperations @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentController: AttachmentController,
) : AttachmentOperations {

    override suspend fun hasSupportedApp(attachment: Attachment): Boolean {
        return attachment.openWithIntent(context)?.hasSupportedApplications(context) == true
    }

    override suspend fun isCached(attachment: Attachment): Boolean {
        return attachment.hasUsableCache(context, attachment.getUploadLocalFile()) || attachment.isInlineCachedFile(context)
    }

    override suspend fun getOpenIntent(attachment: Attachment): Intent? {
        return attachment.getIntentOrGoToAppStore(context, AttachmentExt.AttachmentIntentType.OPEN_WITH)
    }

    override suspend fun download(attachment: Attachment): Boolean {
        val localAttachment = attachmentController.getAttachment(attachment.localUuid)
        return LocalStorageUtils.downloadThenSaveAttachmentToCacheDir(context, localAttachment)
    }

    override suspend fun deleteIncompleteCache(attachment: Attachment) {
        runCatchingRealm {
            attachment.getCacheFile(context)?.apply { if (exists()) delete() }
        }
    }
}
