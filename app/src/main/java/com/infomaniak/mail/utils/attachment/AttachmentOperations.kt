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
import com.infomaniak.core.common.extensions.goToAppStore
import com.infomaniak.core.legacy.utils.hasSupportedApplications
import com.infomaniak.mail.data.cache.mailboxContent.AttachmentController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.extensions.hasFileForOpening
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.LocalStorageUtils
import com.infomaniak.mail.utils.SaveOnKDriveUtils
import com.infomaniak.mail.utils.SaveOnKDriveUtils.DRIVE_PACKAGE
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType.OPEN_WITH
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType.SAVE_TO_DRIVE
import com.infomaniak.mail.utils.extensions.AttachmentExt.openWithIntent
import com.infomaniak.mail.utils.extensions.AttachmentExt.saveToDriveIntent
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

interface AttachmentOperations {
    suspend fun getAttachment(localUuid: String): Attachment?
    suspend fun hasSupportedApp(attachment: Attachment, mailbox: Mailbox? = null): Boolean
    suspend fun isCached(attachment: Attachment, mailbox: Mailbox? = null): Boolean
    suspend fun getOpenIntent(attachment: Attachment, intentType: AttachmentIntentType = OPEN_WITH, mailbox: Mailbox? = null): Intent?
    suspend fun download(attachment: Attachment, mailbox: Mailbox? = null): Boolean
    fun canSaveOnKDrive(): Boolean
    fun openDriveAppStore()
}

class DefaultAttachmentOperations @Inject constructor(
    @ActivityContext private val context: Context,
    private val attachmentController: AttachmentController,
) : AttachmentOperations {

    override suspend fun getAttachment(localUuid: String): Attachment? {
        return runCatchingRealm { attachmentController.getAttachment(localUuid) }.getOrNull()
    }

    override suspend fun hasSupportedApp(attachment: Attachment, mailbox: Mailbox?): Boolean {
        return attachment.openWithIntent(context, mailbox)?.hasSupportedApplications(context) == true
    }

    override suspend fun isCached(attachment: Attachment, mailbox: Mailbox?): Boolean {
        return attachment.hasFileForOpening(context, mailbox)
    }

    override suspend fun getOpenIntent(attachment: Attachment, intentType: AttachmentIntentType, mailbox: Mailbox?): Intent? {
        return when (intentType) {
            OPEN_WITH -> attachment.openWithIntent(context, mailbox)
            SAVE_TO_DRIVE -> attachment.saveToDriveIntent(context, mailbox)
        }
    }

    override suspend fun download(attachment: Attachment, mailbox: Mailbox?): Boolean {
        return LocalStorageUtils.downloadThenSaveAttachmentToCacheDir(context, attachment, mailbox)
    }

    override fun canSaveOnKDrive(): Boolean = SaveOnKDriveUtils.canSaveOnKDrive(context)

    override fun openDriveAppStore() = context.goToAppStore(DRIVE_PACKAGE)
}
