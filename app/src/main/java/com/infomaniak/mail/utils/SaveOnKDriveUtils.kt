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
package com.infomaniak.mail.utils

import android.content.Context
import android.content.pm.PackageManager
import io.sentry.Sentry

object SaveOnKDriveUtils {
    const val DRIVE_PACKAGE = "com.infomaniak.drive"
    const val SAVE_EXTERNAL_ACTIVITY_CLASS = "com.infomaniak.drive.ui.SaveExternalFilesActivity"

    fun canSaveOnKDrive(context: Context) = runCatching {
        val packageInfo = context.packageManager.getPackageInfo(DRIVE_PACKAGE, PackageManager.GET_ACTIVITIES)
        packageInfo.activities.any { it.name == SAVE_EXTERNAL_ACTIVITY_CLASS }
    }.getOrElse {
        Sentry.captureException(it)
        false
    }
}
