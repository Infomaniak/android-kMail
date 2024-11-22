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
package com.infomaniak.mail.ui.main.thread.actions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.goToPlayStore
import com.infomaniak.lib.core.utils.setBackNavigationResult
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.KDriveUtils.DRIVE_PACKAGE
import com.infomaniak.mail.utils.KDriveUtils.SAVE_EXTERNAL_ACTIVITY_CLASS
import com.infomaniak.mail.utils.KDriveUtils.canSaveOnKDrive
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadThreadsProgressDialog : DownloadProgressDialog() {
    private val downloadThreadsViewModel: DownloadThreadsViewModel by viewModels()
    private val navigationArgs: DownloadThreadsProgressDialogArgs by navArgs()
    override val dialogTitle: String? by lazy { getDialogTitleFromArgs() }
    override val dialogIconDrawableRes: Int? by lazy { null }

    override fun download() {
        downloadThreadsViewModel.downloadThreads(mainViewModel.currentMailbox.value).observe(this) { threadUris ->
            if (threadUris == null) {
                popBackStackWithError()
            } else {
                ArrayList(threadUris).openKDriveOrPlayStore(requireContext())?.let { openKDriveIntent ->
                    setBackNavigationResult(DOWNLOAD_THREADS_RESULT, openKDriveIntent)
                } ?: run { findNavController().popBackStack() }
            }
        }
    }

    private fun getDialogTitleFromArgs() = if (navigationArgs.messageUids.size == 1) {
        navigationArgs.nameFirstMessage
    } else {
        requireContext().resources.getQuantityString(R.plurals.downloadingEmailsTitle, 1, navigationArgs.messageUids.size)
    }

    private fun ArrayList<Uri>.openKDriveOrPlayStore(context: Context): Intent? {
        return if (canSaveOnKDrive(context)) {
            saveToDriveIntent()
        } else {
            context.goToPlayStore(DRIVE_PACKAGE)
            null
        }
    }

    private fun ArrayList<Uri>.saveToDriveIntent(): Intent {
        return Intent().apply {
            component = ComponentName(DRIVE_PACKAGE, SAVE_EXTERNAL_ACTIVITY_CLASS)
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, this@saveToDriveIntent)
        }
    }

    companion object {
        const val DOWNLOAD_THREADS_RESULT = "download_threads_result"
    }
}
