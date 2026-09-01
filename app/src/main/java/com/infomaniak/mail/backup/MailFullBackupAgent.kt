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
package com.infomaniak.mail.backup

import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import com.infomaniak.core.common.backup.FullBackup
import com.infomaniak.core.common.backup.FullBackupAgent
import com.infomaniak.core.common.backup.isDeviceToDeviceTransfer
import java.io.File

class MailFullBackupAgent : FullBackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        if (data.isDeviceToDeviceTransfer == true) {
            realmFiles(excludedDatabases = listOf("AppSettings")).forEach { fullBackupFile(it, data) }
        }
        super.onFullBackup(data)
    }

    override fun onRestoreFile(
        data: ParcelFileDescriptor,
        size: Long,
        destination: File,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        // Always restore the files, regardless of the data extraction rules.
        FullBackup.restoreFile(data, size, type, mode, mtime, destination)
    }

    private fun realmFiles(excludedDatabases: List<String>): List<File> {
        val realmExtensions = listOf(".realm", ".realm.lock")
        val realmFiles = filesDir.list()!!.asSequence()
            .filter { fileName ->
                realmExtensions.any { fileName.endsWith(it) && fileName.substringBefore(it) !in excludedDatabases }
            }.map { filesDir.resolve(it) }
        val realmManagementSuffix = ".realm.management"
        val realmManagementDirs = filesDir.list()!!.asSequence()
            .filter { fileName ->
                fileName.endsWith(realmManagementSuffix) && fileName.substringBefore(realmManagementSuffix) !in excludedDatabases
            }.map { filesDir.resolve(it) }
        return realmManagementDirs.flatMap { it.walk() }.plus(realmFiles).filter { file -> file.isFile }.toList()
    }
}
